package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * 综合推荐服务
 * 结合图推荐（GraphRecommendService）和语义召回（SemanticRecallService）
 * 提供更全面的推荐结果
 */
public class RecommendService {
    private static final Logger logger = LoggerFactory.getLogger(RecommendService.class);
    
    private final GraphRecommendService graphRecommendService;
    private final SemanticRecallService semanticRecallService;
    
    // 默认权重：图推荐和语义召回的权重
    private static final double DEFAULT_GRAPH_WEIGHT = 0.6;  // 图推荐权重
    private static final double DEFAULT_SEMANTIC_WEIGHT = 0.4;  // 语义召回权重
    
    public RecommendService() {
        this.graphRecommendService = new GraphRecommendService();
        this.semanticRecallService = new SemanticRecallService();
    }
    
    /**
     * 综合推荐
     * 结合图推荐和语义召回的结果
     */
    public Response recommend(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            // 获取参数
            double graphWeight = request.getPayload() != null && request.getPayload().has("graphWeight") ?
                    request.getPayload().get("graphWeight").asDouble() : DEFAULT_GRAPH_WEIGHT;
            double semanticWeight = request.getPayload() != null && request.getPayload().has("semanticWeight") ?
                    request.getPayload().get("semanticWeight").asDouble() : DEFAULT_SEMANTIC_WEIGHT;
            int topN = request.getPayload() != null && request.getPayload().has("topN") ?
                    request.getPayload().get("topN").asInt() : 20;
            
            logger.info("综合推荐: userId={}, graphWeight={}, semanticWeight={}, topN={}", 
                    userId, graphWeight, semanticWeight, topN);
            
            // 1. 尝试图推荐
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
                            // 图推荐分数通常在0-1之间，乘以权重后范围是0-0.6
                            double normalizedScore = Math.min(score, 1.0); // 确保不超过1.0
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
            
            // 2. 尝试语义召回
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
                            // 语义相似度通常在0-1之间，乘以权重后范围是0-0.4
                            double normalizedSimilarity = Math.min(Math.max(similarity, 0.0), 1.0); // 确保在0-1之间
                            recScore.addSemanticScore(normalizedSimilarity * semanticWeight);
                            // 如果图推荐没有理由，使用语义推荐的理由
                            if (recScore.getReason() == null || recScore.getReason().isEmpty()) {
                                recScore.setReason(reason);
                            }
                            // 如果图推荐没有数据，使用语义推荐的数据
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
            
            // 3. 如果两种方法都失败，返回错误
            if (bookScores.isEmpty()) {
                // 优先返回图推荐的错误信息（因为图推荐通常更可靠）
                if (!graphResponse.isSuccess()) {
                    return graphResponse;
                } else if (!semanticResponse.isSuccess()) {
                    return semanticResponse;
                } else {
                    return Response.error(requestId, ErrorCode.NOT_FOUND, 
                            JsonUtil.toJsonNode("无法生成推荐，请先借阅一些图书"));
                }
            }
            
            // 4. 按综合分数排序
            List<RecommendationScore> sortedScores = new ArrayList<>(bookScores.values());
            sortedScores.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
            
            // 5. 取前topN个并规范化推荐度
            int count = Math.min(topN, sortedScores.size());
            ArrayNode bookArray = JsonUtil.getObjectMapper().createArrayNode();
            
            // 找到最大分数用于归一化（可选：将分数映射到0-10范围）
            double maxScore = count > 0 ? sortedScores.get(0).getTotalScore() : 1.0;
            double minScore = count > 0 ? sortedScores.get(count - 1).getTotalScore() : 0.0;
            double scoreRange = maxScore - minScore;
            
            for (int i = 0; i < count; i++) {
                RecommendationScore recScore = sortedScores.get(i);
                JsonNode bookData = recScore.getBookData();
                
                if (bookData != null) {
                    double totalScore = recScore.getTotalScore();
                    
                    // 规范化推荐度到0-10范围，使其更易读
                    // 如果所有分数都很小（接近0），则按比例放大到0-10
                    double normalizedScore;
                    if (scoreRange > 0.001) {
                        // 有分数差异，归一化到0-10
                        normalizedScore = ((totalScore - minScore) / scoreRange) * 10.0;
                    } else if (totalScore > 0.001) {
                        // 分数很小但非零，直接放大
                        normalizedScore = totalScore * 10.0;
                    } else {
                        // 分数为0或接近0，给一个最小显示值
                        normalizedScore = 0.1;
                    }
                    
                    // 确保在0-10范围内
                    normalizedScore = Math.max(0.0, Math.min(10.0, normalizedScore));
                    
                    ObjectNode bookNode = JsonUtil.createObjectNode();
                    bookNode.put("bookId", recScore.getBookId());
                    bookNode.put("title", bookData.has("title") ? bookData.get("title").asText() : "");
                    bookNode.put("author", bookData.has("author") ? bookData.get("author").asText() : "");
                    bookNode.put("category", bookData.has("category") ? bookData.get("category").asText() : "");
                    bookNode.put("publisher", bookData.has("publisher") ? bookData.get("publisher").asText() : "");
                    bookNode.put("description", bookData.has("description") ? bookData.get("description").asText() : "");
                    bookNode.put("availableCount", bookData.has("availableCount") ? bookData.get("availableCount").asInt() : 0);
                    bookNode.put("score", normalizedScore); // 使用规范化后的分数（0-10范围）
                    bookNode.put("reason", recScore.getReason());
                    
                    logger.debug("推荐图书: bookId={}, 原始分数={}, 规范化分数={}", 
                            recScore.getBookId(), String.format("%.6f", totalScore), String.format("%.2f", normalizedScore));
                    
                    bookArray.add(bookNode);
                }
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("books", bookArray);
            data.put("total", bookArray.size());
            
            logger.info("综合推荐完成: userId={}, 推荐{}本书, 分数范围=[{}, {}], 规范化到[0, 10]", 
                    userId, bookArray.size(), String.format("%.3f", minScore), String.format("%.3f", maxScore));
            return Response.success(requestId, "推荐成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("综合推荐失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 推荐分数类
     */
    private static class RecommendationScore {
        private final Long bookId;
        private double graphScore = 0.0;
        private double semanticScore = 0.0;
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
        
        public double getTotalScore() {
            return graphScore + semanticScore;
        }
        
        public Long getBookId() { return bookId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public JsonNode getBookData() { return bookData; }
        public void setBookData(JsonNode bookData) { this.bookData = bookData; }
    }
}
