package com.library.server.recommend;

import com.library.server.dao.BookDao;
import com.library.server.dao.BorrowRecordDao;
import com.library.server.model.Book;
import com.library.server.model.BorrowRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Personalized PageRank推荐器
 * 基于图的随机游走算法进行推荐
 */
public class PPRRecommender {
    private static final Logger logger = LoggerFactory.getLogger(PPRRecommender.class);
    
    private final double restartProbability;  // 重启概率（通常0.15）
    private final int maxIterations;  // 最大迭代次数
    
    public PPRRecommender(double restartProbability, int maxIterations) {
        this.restartProbability = restartProbability;
        this.maxIterations = maxIterations;
    }
    
    /**
     * 执行PPR推荐
     * @param userId 目标用户ID
     * @param graph 用户子图
     * @param topN 返回前N个推荐
     * @return 推荐列表（带解释）
     */
    public List<RecommendationExplanation> recommend(Long userId, Graph graph, int topN) {
        String userIdStr = "user:" + userId;
        
        if (!graph.containsNode(userIdStr)) {
            logger.warn("用户节点不存在: userId={}", userId);
            return Collections.emptyList();
        }
        
        // 1. 初始化PageRank向量（从目标用户开始）
        Map<String, Double> pprScores = new HashMap<>();
        pprScores.put(userIdStr, 1.0);
        
        // 2. 迭代计算PPR
        for (int iter = 0; iter < maxIterations; iter++) {
            Map<String, Double> newScores = new HashMap<>();
            
            // 初始化新分数（重启概率部分）
            for (String nodeId : graph.getAllNodeIds()) {
                newScores.put(nodeId, nodeId.equals(userIdStr) ? restartProbability : 0.0);
            }
            
            // 传播分数
            for (Map.Entry<String, Double> entry : pprScores.entrySet()) {
                String nodeId = entry.getKey();
                Double score = entry.getValue();
                
                GraphNode node = graph.getNode(nodeId);
                if (node == null) continue;
                
                Map<String, Double> edges = node.getEdges();
                if (edges.isEmpty()) continue;
                
                // 归一化边权重
                double totalWeight = node.getTotalEdgeWeight();
                if (totalWeight == 0) continue;
                
                // 传播到邻居节点
                double propagateScore = score * (1 - restartProbability);
                for (Map.Entry<String, Double> edge : edges.entrySet()) {
                    String neighborId = edge.getKey();
                    double edgeWeight = edge.getValue();
                    double normalizedWeight = edgeWeight / totalWeight;
                    
                    newScores.put(neighborId, 
                        newScores.getOrDefault(neighborId, 0.0) + propagateScore * normalizedWeight);
                }
            }
            
            // 检查收敛
            double diff = calculateDifference(pprScores, newScores);
            pprScores = newScores;
            
            if (diff < 1e-6) {
                logger.debug("PPR收敛于迭代{}: diff={}", iter + 1, diff);
                break;
            }
        }
        
        // 3. 提取图书节点分数（排除用户节点和已借阅的图书）
        Set<Long> borrowedBookIds = getBorrowedBookIds(userId);
        Map<Long, Double> bookScores = new HashMap<>();
        
        for (Map.Entry<String, Double> entry : pprScores.entrySet()) {
            String nodeId = entry.getKey();
            if (nodeId.startsWith("book:")) {
                try {
                    Long bookId = Long.parseLong(nodeId.substring(5));
                    // 排除已借阅的图书
                    if (!borrowedBookIds.contains(bookId)) {
                        bookScores.put(bookId, entry.getValue());
                    }
                } catch (NumberFormatException e) {
                    logger.warn("无法解析图书ID: {}", nodeId);
                }
            }
        }
        
        // 4. 排序并取前N个
        List<Map.Entry<Long, Double>> sortedBooks = bookScores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(topN)
            .collect(Collectors.toList());
        
        // 5. 构建推荐解释
        BookDao bookDao = new BookDao();
        List<RecommendationExplanation> recommendations = new ArrayList<>();
        
        for (Map.Entry<Long, Double> entry : sortedBooks) {
            Long bookId = entry.getKey();
            Double score = entry.getValue();
            
            Book book = bookDao.findById(bookId);
            if (book == null) continue;
            
            RecommendationExplanation explanation = new RecommendationExplanation(bookId, score);
            
            // 生成推荐路径解释
            generateExplanationPaths(userId, bookId, graph, explanation);
            
            recommendations.add(explanation);
        }
        
        logger.info("PPR推荐完成: userId={}, 推荐{}本书", userId, recommendations.size());
        return recommendations;
    }
    
    /**
     * 计算两次迭代的差异
     */
    private double calculateDifference(Map<String, Double> oldScores, Map<String, Double> newScores) {
        double diff = 0.0;
        Set<String> allNodes = new HashSet<>(oldScores.keySet());
        allNodes.addAll(newScores.keySet());
        
        for (String nodeId : allNodes) {
            double oldScore = oldScores.getOrDefault(nodeId, 0.0);
            double newScore = newScores.getOrDefault(nodeId, 0.0);
            diff += Math.abs(newScore - oldScore);
        }
        
        return diff;
    }
    
    /**
     * 获取用户已借阅的图书ID集合
     */
    private Set<Long> getBorrowedBookIds(Long userId) {
        BorrowRecordDao recordDao = new BorrowRecordDao();
        List<BorrowRecord> records = recordDao.findByUserId(userId);
        return records.stream()
            .map(BorrowRecord::getBookId)
            .collect(Collectors.toSet());
    }
    
    /**
     * 生成推荐路径解释
     */
    private void generateExplanationPaths(Long userId, Long targetBookId, Graph graph, 
                                         RecommendationExplanation explanation) {
        String userIdStr = "user:" + userId;
        String targetBookIdStr = "book:" + targetBookId;
        
        BookDao bookDao = new BookDao();
        Book targetBook = bookDao.findById(targetBookId);
        if (targetBook == null) return;
        
        // 查找从用户到目标图书的路径
        GraphNode userNode = graph.getNode(userIdStr);
        if (userNode == null) return;
        
        // 查找用户借阅的图书（这些图书可能连接到目标图书）
        for (String neighborId : userNode.getEdges().keySet()) {
            if (neighborId.startsWith("book:")) {
                try {
                    Long sourceBookId = Long.parseLong(neighborId.substring(5));
                    GraphNode sourceBookNode = graph.getNode(neighborId);
                    
                    if (sourceBookNode != null) {
                        // 检查这本书是否连接到目标图书
                        for (String nextNeighborId : sourceBookNode.getEdges().keySet()) {
                            if (nextNeighborId.startsWith("user:") && !nextNeighborId.equals(userIdStr)) {
                                // 找到另一个用户，检查这个用户是否借阅了目标图书
                                GraphNode otherUserNode = graph.getNode(nextNeighborId);
                                if (otherUserNode != null) {
                                    for (String finalBookId : otherUserNode.getEdges().keySet()) {
                                        if (finalBookId.equals(targetBookIdStr)) {
                                            // 找到共借路径：User -> Book1 -> User2 -> TargetBook
                                            Book sourceBook = bookDao.findById(sourceBookId);
                                            if (sourceBook != null) {
                                                double contribution = calculatePathContribution(
                                                    userNode.getEdges().get(neighborId),
                                                    sourceBookNode.getEdges().get(nextNeighborId),
                                                    otherUserNode.getEdges().get(targetBookIdStr)
                                                );
                                                
                                                RecommendationExplanation.ExplanationPath path = 
                                                    new RecommendationExplanation.ExplanationPath(
                                                        RecommendationExplanation.ExplanationPath.PathType.CO_BORROWED,
                                                        targetBookId,
                                                        targetBook.getTitle(),
                                                        contribution
                                                    );
                                                path.setSourceBookId(sourceBookId);
                                                path.setSourceBookTitle(sourceBook.getTitle());
                                                explanation.addPath(path);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }
        
        // 如果还没有路径，添加一个通用路径
        if (explanation.getPaths().isEmpty()) {
            RecommendationExplanation.ExplanationPath path = 
                new RecommendationExplanation.ExplanationPath(
                    RecommendationExplanation.ExplanationPath.PathType.SIMILAR_USER,
                    targetBookId,
                    targetBook.getTitle(),
                    explanation.getScore()
                );
            explanation.addPath(path);
        }
    }
    
    /**
     * 计算路径贡献度
     */
    private double calculatePathContribution(double weight1, double weight2, double weight3) {
        return (weight1 + weight2 + weight3) / 3.0;
    }
}
