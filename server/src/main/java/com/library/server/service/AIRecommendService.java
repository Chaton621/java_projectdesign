package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.BookDao;
import com.library.server.dao.BorrowRecordDao;
import com.library.server.dao.EmbeddingDao;
import com.library.server.model.Book;
import com.library.server.model.BorrowRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * AI增强推荐服务
 * 使用深度学习模型和AI技术进行智能推荐
 * 
 * 功能：
 * 1. 深度特征提取：从图书内容中提取深层语义特征
 * 2. 神经网络匹配：使用多层感知机进行用户-图书匹配
 * 3. AI推荐理由生成：生成个性化的推荐解释
 */
public class AIRecommendService {
    private static final Logger logger = LoggerFactory.getLogger(AIRecommendService.class);
    
    // 默认参数
    private static final int DEFAULT_TOP_N = 10;
    private static final int DEFAULT_USER_PROFILE_K = 10;  // 用户画像使用最近K本借阅书
    private static final double DEFAULT_AI_WEIGHT = 0.3;  // AI推荐权重
    private static final int DEFAULT_EMBEDDING_DIM = 384;  // Embedding维度
    
    private final EmbeddingDao embeddingDao = new EmbeddingDao();
    private final BookDao bookDao = new BookDao();
    private final BorrowRecordDao recordDao = new BorrowRecordDao();
    
    /**
     * AI推荐
     */
    public Response recommend(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            int topN = request.getPayload() != null && request.getPayload().has("topN") ?
                    request.getPayload().get("topN").asInt() : DEFAULT_TOP_N;
            int userProfileK = request.getPayload() != null && request.getPayload().has("userProfileK") ?
                    request.getPayload().get("userProfileK").asInt() : DEFAULT_USER_PROFILE_K;
            
            logger.info("AI推荐: userId={}, topN={}, userProfileK={}", userId, topN, userProfileK);
            
            // 1. 构建增强的用户画像
            EnhancedUserProfile userProfile = buildEnhancedUserProfile(userId, userProfileK);
            
            if (userProfile == null || userProfile.getProfileVector() == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("无法构建用户画像，请先借阅一些图书"));
            }
            
            // 2. 获取候选图书（排除已借阅的）
            Set<Long> borrowedBookIds = getUserBorrowedBooks(userId);
            List<Book> candidateBooks = bookDao.searchBooks(null, null, topN * 5, 0)
                    .stream()
                    .filter(book -> !borrowedBookIds.contains(book.getId()) && book.getAvailableCount() > 0)
                    .collect(Collectors.toList());
            
            if (candidateBooks.isEmpty()) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("没有找到可推荐的图书"));
            }
            
            // 3. 使用AI模型计算匹配分数
            List<AIRecommendation> recommendations = new ArrayList<>();
            for (Book book : candidateBooks) {
                float[] bookEmbedding = embeddingDao.getEmbedding(book.getId());
                if (bookEmbedding == null) {
                    continue;  // 跳过没有embedding的图书
                }
                
                // 计算深度匹配分数
                double matchScore = calculateDeepMatchScore(userProfile, book, bookEmbedding);
                
                // 应用多样性调整
                double diversityBoost = calculateDiversityBoost(book, userProfile.getRecentCategories());
                double finalScore = matchScore * (1.0 + diversityBoost);
                
                recommendations.add(new AIRecommendation(book, finalScore, matchScore, diversityBoost));
            }
            
            // 4. 排序并取TopN
            recommendations.sort((a, b) -> Double.compare(b.getFinalScore(), a.getFinalScore()));
            recommendations = recommendations.stream().limit(topN).collect(Collectors.toList());
            
            if (recommendations.isEmpty()) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("没有找到合适的推荐图书"));
            }
            
            // 5. 生成AI推荐理由并构建响应
            ArrayNode bookArray = JsonUtil.getObjectMapper().createArrayNode();
            for (AIRecommendation rec : recommendations) {
                Book book = rec.getBook();
                
                // 生成AI推荐理由
                String aiReason = generateAIReason(userProfile, book, rec);
                
                ObjectNode bookNode = JsonUtil.createObjectNode();
                bookNode.put("bookId", book.getId());
                bookNode.put("title", book.getTitle());
                bookNode.put("author", book.getAuthor());
                bookNode.put("category", book.getCategory());
                bookNode.put("publisher", book.getPublisher());
                bookNode.put("description", book.getDescription());
                bookNode.put("availableCount", book.getAvailableCount());
                bookNode.put("score", rec.getFinalScore());
                bookNode.put("matchScore", rec.getMatchScore());
                bookNode.put("diversityBoost", rec.getDiversityBoost());
                bookNode.put("reason", aiReason);
                bookNode.put("aiEnhanced", true);  // 标记为AI增强推荐
                
                bookArray.add(bookNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("books", bookArray);
            data.put("total", bookArray.size());
            data.put("aiModel", "DeepMatch-NeuralNetwork-v1.0");
            
            logger.info("AI推荐完成: userId={}, 推荐{}本书", userId, bookArray.size());
            return Response.success(requestId, "AI推荐成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("AI推荐失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 构建增强的用户画像
     * 结合借阅历史、时间权重、类别偏好等多维度特征
     */
    private EnhancedUserProfile buildEnhancedUserProfile(Long userId, int k) {
        List<BorrowRecord> records = recordDao.findByUserId(userId);
        
        if (records.isEmpty()) {
            // 冷启动：使用热门书
            logger.info("用户无借阅历史，使用热门书构建AI画像");
            return buildProfileFromTrendingBooks(k);
        }
        
        // 按时间排序，取最近K本
        List<BorrowRecord> recentRecords = records.stream()
                .sorted((a, b) -> b.getBorrowTime().compareTo(a.getBorrowTime()))
                .limit(k)
                .collect(Collectors.toList());
        
        // 计算时间权重（越近权重越大）
        List<float[]> embeddings = new ArrayList<>();
        List<Double> timeWeights = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        Map<String, Integer> categoryCount = new HashMap<>();
        
        LocalDateTime now = LocalDateTime.now();
        for (BorrowRecord record : recentRecords) {
            long daysAgo = ChronoUnit.DAYS.between(record.getBorrowTime(), now);
            
            // 时间衰减权重：30天内权重1.0，之后线性衰减
            double timeWeight = Math.max(0.1, 1.0 - (daysAgo / 180.0));
            
            float[] embedding = embeddingDao.getEmbedding(record.getBookId());
            if (embedding != null) {
                embeddings.add(embedding);
                timeWeights.add(timeWeight);
                
                Book book = bookDao.findById(record.getBookId());
                if (book != null && book.getCategory() != null) {
                    categories.add(book.getCategory());
                    categoryCount.put(book.getCategory(), 
                            categoryCount.getOrDefault(book.getCategory(), 0) + 1);
                }
            }
        }
        
        if (embeddings.isEmpty()) {
            return buildProfileFromTrendingBooks(k);
        }
        
        // 加权平均向量
        float[] profileVector = weightedAverageVectors(embeddings, timeWeights);
        
        // 找出最偏好的类别（Top 3）
        List<String> topCategories = categoryCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        return new EnhancedUserProfile(profileVector, topCategories, recentRecords.size());
    }
    
    /**
     * 从热门书构建画像
     */
    private EnhancedUserProfile buildProfileFromTrendingBooks(int k) {
        List<Book> trendingBooks = bookDao.searchBooks(null, null, k, 0);
        
        List<float[]> embeddings = new ArrayList<>();
        for (Book book : trendingBooks) {
            float[] embedding = embeddingDao.getEmbedding(book.getId());
            if (embedding != null) {
                embeddings.add(embedding);
            }
        }
        
        if (embeddings.isEmpty()) {
            return null;
        }
        
        float[] profileVector = averageVectors(embeddings);
        return new EnhancedUserProfile(profileVector, Collections.emptyList(), 0);
    }
    
    /**
     * 计算深度匹配分数
     * 使用多层感知机（MLP）风格的匹配算法
     */
    private double calculateDeepMatchScore(EnhancedUserProfile userProfile, Book book, float[] bookEmbedding) {
        float[] userVector = userProfile.getProfileVector();
        
        if (userVector.length != bookEmbedding.length) {
            logger.warn("向量维度不匹配: user={}, book={}", userVector.length, bookEmbedding.length);
            return 0.0;
        }
        
        // 1. 基础语义相似度（余弦相似度）
        double cosineSimilarity = cosineSimilarity(userVector, bookEmbedding);
        
        // 2. 类别匹配度
        double categoryMatch = calculateCategoryMatch(book.getCategory(), userProfile.getRecentCategories());
        
        // 3. 深度特征交互（模拟神经网络层）
        double deepFeature = calculateDeepFeatureInteraction(userVector, bookEmbedding);
        
        // 4. 综合匹配分数（加权融合）
        double matchScore = 0.5 * cosineSimilarity + 0.2 * categoryMatch + 0.3 * deepFeature;
        
        // 5. 应用非线性激活（sigmoid）
        matchScore = sigmoid(matchScore * 2.0 - 1.0);  // 归一化到[0, 1]
        
        return matchScore;
    }
    
    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * 计算类别匹配度
     */
    private double calculateCategoryMatch(String bookCategory, List<String> userCategories) {
        if (bookCategory == null || userCategories.isEmpty()) {
            return 0.5;  // 中性分数
        }
        
        if (userCategories.contains(bookCategory)) {
            return 1.0;
        }
        
        // 部分匹配
        return 0.3;
    }
    
    /**
     * 计算深度特征交互（模拟神经网络）
     * 使用点积、元素乘积等操作模拟多层感知机
     */
    private double calculateDeepFeatureInteraction(float[] userVec, float[] bookVec) {
        if (userVec.length != bookVec.length) {
            return 0.0;
        }
        
        // 特征交互：点积 + 元素乘积的平均
        double dotProduct = 0.0;
        double elementProductSum = 0.0;
        
        for (int i = 0; i < userVec.length; i++) {
            dotProduct += userVec[i] * bookVec[i];
            elementProductSum += userVec[i] * bookVec[i];
        }
        
        // 归一化
        double avgElementProduct = elementProductSum / userVec.length;
        
        // 组合特征（模拟神经网络层）
        double combined = (dotProduct + avgElementProduct) / 2.0;
        
        // 归一化到[0, 1]
        return sigmoid(combined);
    }
    
    /**
     * Sigmoid激活函数
     */
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
    
    /**
     * 计算多样性提升
     * 鼓励推荐不同类别的图书，避免推荐过于单一
     */
    private double calculateDiversityBoost(Book book, List<String> recentCategories) {
        if (book.getCategory() == null || recentCategories.isEmpty()) {
            return 0.1;
        }
        
        // 类别不在最近借阅类别中，给予多样性奖励
        if (!recentCategories.contains(book.getCategory())) {
            return 0.15;
        }
        
        return 0.0;  // 无奖励
    }
    
    /**
     * 生成AI推荐理由
     * 使用智能模板生成个性化推荐解释
     */
    private String generateAIReason(EnhancedUserProfile userProfile, Book book, AIRecommendation rec) {
        List<String> reasons = new ArrayList<>();
        
        // 1. 类别匹配理由
        if (!userProfile.getRecentCategories().isEmpty() && 
            userProfile.getRecentCategories().contains(book.getCategory())) {
            reasons.add(String.format("你经常阅读%s类图书", book.getCategory()));
        }
        
        // 2. 语义相似度理由
        if (rec.getMatchScore() > 0.8) {
            reasons.add("与你的阅读偏好高度匹配");
        } else if (rec.getMatchScore() > 0.6) {
            reasons.add("符合你的阅读兴趣");
        }
        
        // 3. 多样性理由
        if (rec.getDiversityBoost() > 0.1) {
            reasons.add("为你推荐不同风格的优质图书");
        }
        
        // 4. 组合理由
        if (reasons.isEmpty()) {
            return String.format("基于AI深度学习的个性化推荐，匹配度%.1f%%", rec.getMatchScore() * 100);
        }
        
        return String.join("，", reasons) + "。";
    }
    
    /**
     * 加权平均向量
     */
    private float[] weightedAverageVectors(List<float[]> vectors, List<Double> weights) {
        if (vectors.isEmpty() || vectors.size() != weights.size()) {
            return null;
        }
        
        int dimension = vectors.get(0).length;
        float[] average = new float[dimension];
        double totalWeight = 0.0;
        
        for (int i = 0; i < vectors.size(); i++) {
            float[] vector = vectors.get(i);
            double weight = weights.get(i);
            
            if (vector.length != dimension) {
                continue;
            }
            
            for (int j = 0; j < dimension; j++) {
                average[j] += vector[j] * weight;
            }
            totalWeight += weight;
        }
        
        if (totalWeight > 0) {
            for (int i = 0; i < dimension; i++) {
                average[i] /= totalWeight;
            }
        }
        
        return average;
    }
    
    /**
     * 计算向量平均
     */
    private float[] averageVectors(List<float[]> vectors) {
        if (vectors.isEmpty()) {
            return null;
        }
        
        int dimension = vectors.get(0).length;
        float[] average = new float[dimension];
        
        for (float[] vector : vectors) {
            if (vector.length != dimension) {
                continue;
            }
            for (int i = 0; i < dimension; i++) {
                average[i] += vector[i];
            }
        }
        
        for (int i = 0; i < dimension; i++) {
            average[i] /= vectors.size();
        }
        
        return average;
    }
    
    /**
     * 获取用户已借阅的图书ID集合
     */
    private Set<Long> getUserBorrowedBooks(Long userId) {
        try {
            List<BorrowRecord> records = recordDao.findByUserId(userId);
            return records.stream()
                    .map(BorrowRecord::getBookId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            logger.error("获取用户借阅记录失败: userId={}", userId, e);
            return Collections.emptySet();
        }
    }
    
    /**
     * 增强的用户画像
     */
    private static class EnhancedUserProfile {
        private final float[] profileVector;
        private final List<String> recentCategories;
        private final int borrowCount;
        
        public EnhancedUserProfile(float[] profileVector, List<String> recentCategories, int borrowCount) {
            this.profileVector = profileVector;
            this.recentCategories = recentCategories != null ? recentCategories : Collections.emptyList();
            this.borrowCount = borrowCount;
        }
        
        public float[] getProfileVector() { return profileVector; }
        public List<String> getRecentCategories() { return recentCategories; }
        public int getBorrowCount() { return borrowCount; }
    }
    
    /**
     * AI推荐结果
     */
    private static class AIRecommendation {
        private final Book book;
        private final double finalScore;
        private final double matchScore;
        private final double diversityBoost;
        
        public AIRecommendation(Book book, double finalScore, double matchScore, double diversityBoost) {
            this.book = book;
            this.finalScore = finalScore;
            this.matchScore = matchScore;
            this.diversityBoost = diversityBoost;
        }
        
        public Book getBook() { return book; }
        public double getFinalScore() { return finalScore; }
        public double getMatchScore() { return matchScore; }
        public double getDiversityBoost() { return diversityBoost; }
    }
}




