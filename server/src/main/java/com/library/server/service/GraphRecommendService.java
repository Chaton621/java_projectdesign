package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.BookDao;
import com.library.server.model.Book;
import com.library.server.recommend.Graph;
import com.library.server.recommend.GraphBuilder;
import com.library.server.recommend.PPRRecommender;
import com.library.server.recommend.RecommendationExplanation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 图推荐服务
 * 基于Personalized PageRank的协同过滤推荐
 */
public class GraphRecommendService {
    private static final Logger logger = LoggerFactory.getLogger(GraphRecommendService.class);
    
    // 默认参数
    private static final double DEFAULT_LAMBDA = 0.05;  // 时间衰减参数
    private static final double DEFAULT_BEHAVIOR_WEIGHT = 1.0;  // 行为权重
    private static final double DEFAULT_RESTART_PROBABILITY = 0.15;  // 重启概率
    private static final int DEFAULT_MAX_ITERATIONS = 30;  // 最大迭代次数
    private static final int DEFAULT_TOP_N = 10;  // 默认推荐数量
    
    private final BookDao bookDao = new BookDao();
    
    /**
     * 生成图推荐
     */
    public Response recommend(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            // 获取参数
            double lambda = request.getPayload() != null && request.getPayload().has("lambda") ?
                    request.getPayload().get("lambda").asDouble() : DEFAULT_LAMBDA;
            double behaviorWeight = request.getPayload() != null && request.getPayload().has("behaviorWeight") ?
                    request.getPayload().get("behaviorWeight").asDouble() : DEFAULT_BEHAVIOR_WEIGHT;
            double restartProb = request.getPayload() != null && request.getPayload().has("restartProbability") ?
                    request.getPayload().get("restartProbability").asDouble() : DEFAULT_RESTART_PROBABILITY;
            int maxIter = request.getPayload() != null && request.getPayload().has("maxIterations") ?
                    request.getPayload().get("maxIterations").asInt() : DEFAULT_MAX_ITERATIONS;
            int topN = request.getPayload() != null && request.getPayload().has("topN") ?
                    request.getPayload().get("topN").asInt() : DEFAULT_TOP_N;
            
            logger.info("生成图推荐: userId={}, lambda={}, topN={}", userId, lambda, topN);
            
            // 构建子图
            GraphBuilder graphBuilder = new GraphBuilder(lambda, behaviorWeight);
            Graph graph = graphBuilder.buildUserSubgraph(userId);
            
            if (graph.getNodeCount() == 0) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("用户没有借阅历史，无法生成推荐"));
            }
            
            // 执行PPR推荐
            PPRRecommender recommender = new PPRRecommender(restartProb, maxIter);
            List<RecommendationExplanation> recommendations = recommender.recommend(userId, graph, topN);
            
            if (recommendations.isEmpty()) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("没有找到合适的推荐图书"));
            }
            
            // 构建响应
            ArrayNode bookArray = JsonUtil.getObjectMapper().createArrayNode();
            for (RecommendationExplanation explanation : recommendations) {
                Book book = bookDao.findById(explanation.getBookId());
                if (book == null) {
                    continue;
                }
                
                ObjectNode bookNode = JsonUtil.createObjectNode();
                bookNode.put("bookId", explanation.getBookId());
                bookNode.put("title", book.getTitle());
                bookNode.put("author", book.getAuthor());
                bookNode.put("category", book.getCategory());
                bookNode.put("publisher", book.getPublisher());
                bookNode.put("description", book.getDescription());
                bookNode.put("availableCount", book.getAvailableCount());
                bookNode.put("score", explanation.getScore());
                bookNode.put("reason", explanation.getMainReason());
                
                // 添加详细路径
                ArrayNode pathsArray = JsonUtil.getObjectMapper().createArrayNode();
                for (RecommendationExplanation.ExplanationPath path : explanation.getPaths()) {
                    ObjectNode pathNode = JsonUtil.createObjectNode();
                    pathNode.put("type", path.getType().name());
                    pathNode.put("contribution", path.getContribution());
                    if (path.getSourceBookId() != null) {
                        pathNode.put("sourceBookId", path.getSourceBookId());
                        pathNode.put("sourceBookTitle", path.getSourceBookTitle());
                    }
                    pathNode.put("targetBookId", path.getTargetBookId());
                    pathNode.put("targetBookTitle", path.getTargetBookTitle());
                    pathsArray.add(pathNode);
                }
                bookNode.set("paths", pathsArray);
                
                bookArray.add(bookNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("books", bookArray);
            data.put("total", recommendations.size());
            
            logger.info("图推荐完成: userId={}, 推荐{}本书", userId, recommendations.size());
            return Response.success(requestId, "推荐成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("生成图推荐失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
}



















