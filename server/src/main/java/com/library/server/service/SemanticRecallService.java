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
import com.library.server.dao.EmbeddingDao.SimilarBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 语义召回服务
 * 基于Embedding的语义相似度推荐
 */
public class SemanticRecallService {
    private static final Logger logger = LoggerFactory.getLogger(SemanticRecallService.class);
    
    // 默认参数
    private static final int DEFAULT_USER_PROFILE_K = 5;  // 用户画像使用最近K本借阅书
    private static final int DEFAULT_TOP_N = 10;  // 默认推荐数量
    
    private final EmbeddingDao embeddingDao = new EmbeddingDao();
    private final BookDao bookDao = new BookDao();
    private final BorrowRecordDao recordDao = new BorrowRecordDao();
    
    /**
     * 语义召回推荐
     */
    public Response recommend(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            int topN = request.getPayload() != null && request.getPayload().has("topN") ?
                    request.getPayload().get("topN").asInt() : DEFAULT_TOP_N;
            int userProfileK = request.getPayload() != null && request.getPayload().has("userProfileK") ?
                    request.getPayload().get("userProfileK").asInt() : DEFAULT_USER_PROFILE_K;
            
            logger.info("语义召回推荐: userId={}, topN={}, userProfileK={}", userId, topN, userProfileK);
            
            // 构建用户画像向量
            float[] userProfileVector = buildUserProfile(userId, userProfileK);
            
            if (userProfileVector == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("无法构建用户画像，请先借阅一些图书"));
            }
            
            // 查询相似图书
            List<SimilarBook> similarBooks = embeddingDao.querySimilarBooks(userProfileVector, topN * 2);
            
            if (similarBooks.isEmpty()) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("没有找到语义相似的图书"));
            }
            
            // 获取用户已借过的书
            Set<Long> borrowedBooks = getUserBorrowedBooks(userId);
            
            // 获取用户最近借阅的K本书（用于生成推荐理由）
            List<Long> recentBookIds = getRecentBorrowedBooks(userId, userProfileK);
            
            // 过滤并构建响应
            ArrayNode bookArray = JsonUtil.getObjectMapper().createArrayNode();
            int count = 0;
            
            for (SimilarBook similarBook : similarBooks) {
                if (count >= topN) break;
                
                // 排除已借过的书
                if (borrowedBooks.contains(similarBook.bookId)) {
                    continue;
                }
                
                Book book = bookDao.findById(similarBook.bookId);
                if (book == null || book.getAvailableCount() <= 0) {
                    continue;
                }
                
                ObjectNode bookNode = JsonUtil.createObjectNode();
                bookNode.put("bookId", similarBook.bookId);
                bookNode.put("title", book.getTitle());
                bookNode.put("author", book.getAuthor());
                bookNode.put("category", book.getCategory());
                bookNode.put("publisher", book.getPublisher());
                bookNode.put("description", book.getDescription());
                bookNode.put("availableCount", book.getAvailableCount());
                bookNode.put("similarity", similarBook.similarity);
                
                // 生成推荐理由
                String reason = generateReason(recentBookIds, book);
                bookNode.put("reason", reason);
                
                bookArray.add(bookNode);
                count++;
            }
            
            if (bookArray.size() == 0) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("没有找到合适的推荐图书"));
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("books", bookArray);
            data.put("total", bookArray.size());
            
            logger.info("语义召回完成: userId={}, 推荐{}本书", userId, bookArray.size());
            return Response.success(requestId, "推荐成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("语义召回失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 构建用户画像向量
     * 最近K本借阅书的embedding平均
     */
    private float[] buildUserProfile(Long userId, int k) {
        // 获取用户最近借阅的K本书
        List<Long> recentBookIds = getRecentBorrowedBooks(userId, k);
        
        if (recentBookIds.isEmpty()) {
            // 冷启动：使用热门书的平均
            logger.info("用户无借阅历史，使用热门书构建画像");
            return buildProfileFromTrendingBooks(k);
        }
        
        // 获取这些书的embedding并计算平均
        List<float[]> embeddings = new ArrayList<>();
        for (Long bookId : recentBookIds) {
            float[] embedding = embeddingDao.getEmbedding(bookId);
            if (embedding != null) {
                embeddings.add(embedding);
            }
        }
        
        if (embeddings.isEmpty()) {
            // 如果这些书都没有embedding，使用热门书
            logger.info("用户借阅的书没有embedding，使用热门书构建画像");
            return buildProfileFromTrendingBooks(k);
        }
        
        // 计算平均向量
        return averageVectors(embeddings);
    }
    
    /**
     * 从热门书构建画像向量
     */
    private float[] buildProfileFromTrendingBooks(int k) {
        // 获取热门图书（简化实现，实际可以使用TrendingService）
        List<Book> trendingBooks = bookDao.searchBooks(null, null, k, 0);
        
        List<float[]> embeddings = new ArrayList<>();
        for (Book book : trendingBooks) {
            float[] embedding = embeddingDao.getEmbedding(book.getId());
            if (embedding != null) {
                embeddings.add(embedding);
            }
        }
        
        if (embeddings.isEmpty()) {
            return null;  // 无法构建画像
        }
        
        return averageVectors(embeddings);
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
        
        // 归一化
        for (int i = 0; i < dimension; i++) {
            average[i] /= vectors.size();
        }
        
        return average;
    }
    
    /**
     * 获取用户最近借阅的K本书
     */
    private List<Long> getRecentBorrowedBooks(Long userId, int k) {
        try {
            List<BorrowRecord> records = recordDao.findByUserId(userId);
            return records.stream()
                    .sorted((a, b) -> b.getBorrowTime().compareTo(a.getBorrowTime()))
                    .limit(k)
                    .map(BorrowRecord::getBookId)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取用户借阅记录失败: userId={}", userId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取用户已借过的书
     */
    private Set<Long> getUserBorrowedBooks(Long userId) {
        try {
            List<BorrowRecord> records = recordDao.findByUserId(userId);
            return records.stream()
                    .map(BorrowRecord::getBookId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            logger.error("获取用户借阅记录失败: userId={}", userId, e);
            return java.util.Collections.emptySet();
        }
    }
    
    /**
     * 生成推荐理由
     */
    private String generateReason(List<Long> recentBookIds, Book targetBook) {
        if (recentBookIds.isEmpty()) {
            return "基于热门图书的语义相似度推荐";
        }
        
        // 使用第一本最近借阅的书作为理由
        Long recentBookId = recentBookIds.get(0);
        Book recentBook = bookDao.findById(recentBookId);
        
        if (recentBook != null) {
            return String.format("与你最近借阅的《%s》语义相近", recentBook.getTitle());
        }
        
        return "基于你借阅历史的语义相似度推荐";
    }
}

