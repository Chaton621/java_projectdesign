package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.BorrowRecordDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * 综合推荐服务（AI增强版）
 * 结合图推荐（GraphRecommendService）、语义召回（SemanticRecallService）和AI推荐（AIRecommendService）
 * 提供更全面、更智能的推荐结果
 */
public class RecommendService {
    private static final Logger logger = LoggerFactory.getLogger(RecommendService.class);
    
    private final GraphRecommendService graphRecommendService;
    private final SemanticRecallService semanticRecallService;
    private final AIRecommendService aiRecommendService;
    private final BorrowRecordDao borrowRecordDao = new BorrowRecordDao();
    
    // 默认权重：图推荐40%，语义召回30%，AI推荐30%
    private static final double DEFAULT_GRAPH_WEIGHT = 0.4;
    private static final double DEFAULT_SEMANTIC_WEIGHT = 0.3;
    private static final double DEFAULT_AI_WEIGHT = 0.3;
    
    // 借阅人数/次数权重：新用户20%，老用户10%
    private static final double POPULARITY_WEIGHT_NEW_USER = 0.2;
    private static final double POPULARITY_WEIGHT_OLD_USER = 0.1;
    
    public RecommendService() {
        this.graphRecommendService = new GraphRecommendService();
        this.semanticRecallService = new SemanticRecallService();
        this.aiRecommendService = new AIRecommendService();
    }
    
    /**
     * 综合推荐（AI增强版）
     * 结合图推荐、语义召回和AI推荐的结果，使用三路融合策略
     */
    public Response recommend(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            double graphWeight = request.getPayload() != null && request.getPayload().has("graphWeight") ?
                    request.getPayload().get("graphWeight").asDouble() : DEFAULT_GRAPH_WEIGHT;
            double semanticWeight = request.getPayload() != null && request.getPayload().has("semanticWeight") ?
                    request.getPayload().get("semanticWeight").asDouble() : DEFAULT_SEMANTIC_WEIGHT;
            double aiWeight = request.getPayload() != null && request.getPayload().has("aiWeight") ?
                    request.getPayload().get("aiWeight").asDouble() : DEFAULT_AI_WEIGHT;
            int topN = request.getPayload() != null && request.getPayload().has("topN") ?
                    request.getPayload().get("topN").asInt() : 20;
            
            // 归一化权重
            double totalWeight = graphWeight + semanticWeight + aiWeight;
            if (totalWeight > 0) {
                graphWeight /= totalWeight;
                semanticWeight /= totalWeight;
                aiWeight /= totalWeight;
            }
            
            logger.info("AI增强综合推荐: userId={}, graphWeight={}, semanticWeight={}, aiWeight={}, topN={}", 
                    userId, graphWeight, semanticWeight, aiWeight, topN);
            
            Map<Long, RecommendationScore> bookScores = new HashMap<>();
            Response graphResponse = graphRecommendService.recommend(request, userId);
            int graphBookCount = 0;
            
            if (graphResponse.isSuccess() && graphResponse.getData() != null) {
                JsonNode booksNode = graphResponse.getData().get("books");
                if (booksNode != null && booksNode.isArray()) {
                    for (JsonNode bookNode : booksNode) {
                        Long bookId = bookNode.has("bookId") ? bookNode.get("bookId").asLong() : null;
                        if (bookId != null) {
                            double score = bookNode.has("score") ? bookNode.get("score").asDouble() : 0.0;
                            String reason = bookNode.has("reason") ? bookNode.get("reason").asText() : "图推荐";
                            
                            RecommendationScore recScore = bookScores.getOrDefault(bookId, new RecommendationScore(bookId));
                            double normalizedScore = Math.min(score, 1.0);
                            recScore.addGraphScore(normalizedScore * graphWeight);
                            recScore.setReason(reason);
                            recScore.setBookData(bookNode);
                            bookScores.put(bookId, recScore);
                            graphBookCount++;
                            
                            logger.debug("图推荐: bookId={}, score={}, normalized={}, weighted={}", 
                                    bookId, score, normalizedScore, normalizedScore * graphWeight);
                        }
                    }
                }
                logger.info("图推荐成功: 返回{}本书", graphBookCount);
            } else {
                logger.warn("图推荐失败: {}", graphResponse != null ? graphResponse.getMessage() : "未知错误");
            }
            
            Response semanticResponse = semanticRecallService.recommend(request, userId);
            int semanticBookCount = 0;
            
            if (semanticResponse.isSuccess() && semanticResponse.getData() != null) {
                JsonNode booksNode = semanticResponse.getData().get("books");
                if (booksNode != null && booksNode.isArray()) {
                    for (JsonNode bookNode : booksNode) {
                        Long bookId = bookNode.has("bookId") ? bookNode.get("bookId").asLong() : null;
                        if (bookId != null) {
                            double similarity = bookNode.has("similarity") ? bookNode.get("similarity").asDouble() : 0.0;
                            String reason = bookNode.has("reason") ? bookNode.get("reason").asText() : "语义推荐";
                            
                            RecommendationScore recScore = bookScores.getOrDefault(bookId, new RecommendationScore(bookId));
                            double normalizedSimilarity = Math.min(Math.max(similarity, 0.0), 1.0);
                            recScore.addSemanticScore(normalizedSimilarity * semanticWeight);
                            if (recScore.getReason() == null || recScore.getReason().isEmpty()) {
                                recScore.setReason(reason);
                            }
                            if (recScore.getBookData() == null) {
                                recScore.setBookData(bookNode);
                            }
                            bookScores.put(bookId, recScore);
                            semanticBookCount++;
                            
                            logger.debug("语义召回: bookId={}, similarity={}, normalized={}, weighted={}", 
                                    bookId, similarity, normalizedSimilarity, normalizedSimilarity * semanticWeight);
                        }
                    }
                }
                logger.info("语义召回成功: 返回{}本书", semanticBookCount);
            } else {
                logger.warn("语义召回失败: {}", semanticResponse != null ? semanticResponse.getMessage() : "未知错误");
            }
            
            // 3. AI推荐
            Response aiResponse = aiRecommendService.recommend(request, userId);
            int aiBookCount = 0;
            
            if (aiResponse.isSuccess() && aiResponse.getData() != null) {
                JsonNode booksNode = aiResponse.getData().get("books");
                if (booksNode != null && booksNode.isArray()) {
                    for (JsonNode bookNode : booksNode) {
                        Long bookId = bookNode.has("bookId") ? bookNode.get("bookId").asLong() : null;
                        if (bookId != null) {
                            double score = bookNode.has("score") ? bookNode.get("score").asDouble() : 0.0;
                            String reason = bookNode.has("reason") ? bookNode.get("reason").asText() : "AI推荐";
                            
                            RecommendationScore recScore = bookScores.getOrDefault(bookId, new RecommendationScore(bookId));
                            double normalizedScore = Math.min(Math.max(score, 0.0), 1.0);
                            recScore.addAIScore(normalizedScore * aiWeight);
                            
                            // AI推荐理由优先级更高（更个性化）
                            if (bookNode.has("aiEnhanced") && bookNode.get("aiEnhanced").asBoolean()) {
                                recScore.setReason(reason);
                            } else if (recScore.getReason() == null || recScore.getReason().isEmpty()) {
                                recScore.setReason(reason);
                            }
                            
                            if (recScore.getBookData() == null) {
                                recScore.setBookData(bookNode);
                            }
                            bookScores.put(bookId, recScore);
                            aiBookCount++;
                            
                            logger.debug("AI推荐: bookId={}, score={}, normalized={}, weighted={}", 
                                    bookId, score, normalizedScore, normalizedScore * aiWeight);
                        }
                    }
                }
                logger.info("AI推荐成功: 返回{}本书", aiBookCount);
            } else {
                logger.warn("AI推荐失败: {}", aiResponse != null ? aiResponse.getMessage() : "未知错误");
            }
            
            // 检查是否为新用户（无借阅记录）
            boolean isNewUser = borrowRecordDao.findByUserId(userId).isEmpty();
            double popularityWeight = isNewUser ? POPULARITY_WEIGHT_NEW_USER : POPULARITY_WEIGHT_OLD_USER;
            
            if (isNewUser) {
                logger.info("检测到新用户（无借阅记录），增加借阅次数权重: userId={}, weight={}", userId, popularityWeight);
            }
            
            // 获取所有候选图书的借阅次数
            List<Long> bookIds = new ArrayList<>(bookScores.keySet());
            Map<Long, Integer> borrowCounts = borrowRecordDao.getBorrowCountsByBookIds(bookIds);
            
            // 计算最大借阅次数（用于归一化）
            int maxBorrowCount = borrowCounts.values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(1);
            
            // 为每本书添加借阅次数权重
            for (Map.Entry<Long, Integer> entry : borrowCounts.entrySet()) {
                Long bookId = entry.getKey();
                int borrowCount = entry.getValue();
                
                RecommendationScore recScore = bookScores.get(bookId);
                if (recScore != null) {
                    // 归一化借阅次数到[0, 1]
                    double normalizedBorrowCount = maxBorrowCount > 0 ? 
                            (double) borrowCount / maxBorrowCount : 0.0;
                    
                    // 添加借阅次数权重分数
                    double popularityScore = normalizedBorrowCount * popularityWeight;
                    recScore.addPopularityScore(popularityScore);
                    
                    logger.debug("借阅次数权重: bookId={}, borrowCount={}, normalized={}, weight={}, score={}", 
                            bookId, borrowCount, normalizedBorrowCount, popularityWeight, popularityScore);
                }
            }
            
            if (bookScores.isEmpty()) {
                if (!graphResponse.isSuccess() && !semanticResponse.isSuccess() && !aiResponse.isSuccess()) {
                    // 新用户时，返回热门图书
                    if (isNewUser) {
                        return getPopularBooksForNewUser(requestId, topN);
                    }
                    return Response.error(requestId, ErrorCode.NOT_FOUND, 
                            JsonUtil.toJsonNode("无法生成推荐，请先借阅一些图书"));
                } else if (!graphResponse.isSuccess() && !semanticResponse.isSuccess()) {
                    return aiResponse;  // 至少AI推荐成功
                } else if (!graphResponse.isSuccess() && !aiResponse.isSuccess()) {
                    return semanticResponse;  // 至少语义召回成功
                } else if (!semanticResponse.isSuccess() && !aiResponse.isSuccess()) {
                    return graphResponse;  // 至少图推荐成功
                }
            }
            
            List<RecommendationScore> sortedScores = new ArrayList<>(bookScores.values());
            sortedScores.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
            
            int count = Math.min(topN, sortedScores.size());
            ArrayNode bookArray = JsonUtil.getObjectMapper().createArrayNode();
            
            double maxScore = count > 0 ? sortedScores.get(0).getTotalScore() : 1.0;
            double minScore = count > 0 ? sortedScores.get(count - 1).getTotalScore() : 0.0;
            double scoreRange = maxScore - minScore;
            
            for (int i = 0; i < count; i++) {
                RecommendationScore recScore = sortedScores.get(i);
                JsonNode bookData = recScore.getBookData();
                
                if (bookData != null) {
                    double totalScore = recScore.getTotalScore();
                    
                    double normalizedScore;
                    if (scoreRange > 0.001) {
                        normalizedScore = ((totalScore - minScore) / scoreRange) * 10.0;
                    } else if (totalScore > 0.001) {
                        normalizedScore = totalScore * 10.0;
                    } else {
                        normalizedScore = 0.1;
                    }
                    
                    normalizedScore = Math.max(0.0, Math.min(10.0, normalizedScore));
                    
                    ObjectNode bookNode = JsonUtil.createObjectNode();
                    bookNode.put("bookId", recScore.getBookId());
                    bookNode.put("title", bookData.has("title") ? bookData.get("title").asText() : "");
                    bookNode.put("author", bookData.has("author") ? bookData.get("author").asText() : "");
                    bookNode.put("category", bookData.has("category") ? bookData.get("category").asText() : "");
                    bookNode.put("publisher", bookData.has("publisher") ? bookData.get("publisher").asText() : "");
                    bookNode.put("description", bookData.has("description") ? bookData.get("description").asText() : "");
                    bookNode.put("availableCount", bookData.has("availableCount") ? bookData.get("availableCount").asInt() : 0);
                    bookNode.put("score", normalizedScore);
                    bookNode.put("reason", recScore.getReason());
                    
                    logger.debug("推荐图书: bookId={}, 原始分数={}, 规范化分数={}", 
                            recScore.getBookId(), String.format("%.6f", totalScore), String.format("%.2f", normalizedScore));
                    
                    bookArray.add(bookNode);
                }
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("books", bookArray);
            data.put("total", bookArray.size());
            data.put("graphCount", graphBookCount);
            data.put("semanticCount", semanticBookCount);
            data.put("aiCount", aiBookCount);
            data.put("fusionStrategy", "graph+semantic+ai");
            
            logger.info("AI增强综合推荐完成: userId={}, 推荐{}本书 (图:{} 语义:{} AI:{}), 分数范围=[{}, {}], 规范化到[0, 10]", 
                    userId, bookArray.size(), graphBookCount, semanticBookCount, aiBookCount,
                    String.format("%.3f", minScore), String.format("%.3f", maxScore));
            return Response.success(requestId, "AI增强推荐成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("综合推荐失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 为新用户获取热门图书（按借阅次数排序）
     */
    private Response getPopularBooksForNewUser(String requestId, int topN) {
        try {
            List<Long> allBookIds = new ArrayList<>();
            com.library.server.dao.BookDao bookDao = new com.library.server.dao.BookDao();
            List<com.library.server.model.Book> allBooks = bookDao.searchBooks(null, null, topN * 3, 0);
            
            for (com.library.server.model.Book book : allBooks) {
                if (book.getAvailableCount() > 0) {
                    allBookIds.add(book.getId());
                }
            }
            
            if (allBookIds.isEmpty()) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("没有可推荐的图书"));
            }
            
            // 获取借阅次数
            Map<Long, Integer> borrowCounts = borrowRecordDao.getBorrowCountsByBookIds(allBookIds);
            
            // 按借阅次数排序
            List<Map.Entry<Long, Integer>> sortedEntries = new ArrayList<>(borrowCounts.entrySet());
            sortedEntries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            
            // 取前topN本
            ArrayNode bookArray = JsonUtil.getObjectMapper().createArrayNode();
            int count = Math.min(topN, sortedEntries.size());
            
            for (int i = 0; i < count; i++) {
                Long bookId = sortedEntries.get(i).getKey();
                com.library.server.model.Book book = bookDao.findById(bookId);
                
                if (book != null && book.getAvailableCount() > 0) {
                    ObjectNode bookNode = JsonUtil.createObjectNode();
                    bookNode.put("bookId", book.getId());
                    bookNode.put("title", book.getTitle());
                    bookNode.put("author", book.getAuthor());
                    bookNode.put("category", book.getCategory());
                    bookNode.put("publisher", book.getPublisher());
                    bookNode.put("description", book.getDescription());
                    bookNode.put("availableCount", book.getAvailableCount());
                    bookNode.put("score", 10.0 - (i * 0.5));  // 分数递减
                    bookNode.put("reason", "借阅人数多，热门推荐");
                    
                    bookArray.add(bookNode);
                }
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("books", bookArray);
            data.put("total", bookArray.size());
            data.put("fusionStrategy", "popularity");
            
            logger.info("新用户热门推荐: 推荐{}本书", bookArray.size());
            return Response.success(requestId, "热门推荐成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("新用户热门推荐失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 推荐分数类（支持三路融合+借阅次数权重）
     */
    private static class RecommendationScore {
        private final Long bookId;
        private double graphScore = 0.0;
        private double semanticScore = 0.0;
        private double aiScore = 0.0;
        private double popularityScore = 0.0;
        private String reason;
        private JsonNode bookData;
        
        public RecommendationScore(Long bookId) {
            this.bookId = bookId;
        }
        
        public void addGraphScore(double score) {
            this.graphScore += score;
        }
        
        public void addSemanticScore(double score) {
            this.semanticScore += score;
        }
        
        public void addAIScore(double score) {
            this.aiScore += score;
        }
        
        public void addPopularityScore(double score) {
            this.popularityScore += score;
        }
        
        public double getTotalScore() {
            return graphScore + semanticScore + aiScore + popularityScore;
        }
        
        public Long getBookId() { return bookId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public JsonNode getBookData() { return bookData; }
        public void setBookData(JsonNode bookData) { this.bookData = bookData; }
    }
}
