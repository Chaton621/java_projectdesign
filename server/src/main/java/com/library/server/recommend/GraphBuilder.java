package com.library.server.recommend;

import com.library.server.dao.BorrowRecordDao;
import com.library.server.model.BorrowRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 图构建器
 * 从借阅记录构建User-Book二部图
 */
public class GraphBuilder {
    private static final Logger logger = LoggerFactory.getLogger(GraphBuilder.class);
    
    private final double lambda;  // 时间衰减参数
    private final double behaviorWeight;  // 行为权重
    
    public GraphBuilder(double lambda, double behaviorWeight) {
        this.lambda = lambda;
        this.behaviorWeight = behaviorWeight;
    }
    
    /**
     * 构建用户子图
     * 包含目标用户及其借阅的图书，以及借阅相同图书的其他用户及其借阅的图书
     */
    public Graph buildUserSubgraph(Long userId) {
        Graph graph = new Graph();
        BorrowRecordDao recordDao = new BorrowRecordDao();
        
        // 1. 添加目标用户节点
        String userIdStr = "user:" + userId;
        graph.addNode(userIdStr, GraphNode.NodeType.USER);
        
        // 2. 获取目标用户的借阅记录
        List<BorrowRecord> userRecords = recordDao.findByUserId(userId);
        if (userRecords.isEmpty()) {
            logger.warn("用户没有借阅记录: userId={}", userId);
            return graph;
        }
        
        // 3. 添加目标用户借阅的图书节点和边
        Set<Long> userBookIds = new HashSet<>();
        for (BorrowRecord record : userRecords) {
            Long bookId = record.getBookId();
            userBookIds.add(bookId);
            String bookIdStr = "book:" + bookId;
            graph.addNode(bookIdStr, GraphNode.NodeType.BOOK);
            
            // 计算边权重（考虑时间衰减）
            double weight = calculateWeight(record.getBorrowTime());
            graph.addEdge(userIdStr, bookIdStr, weight);
        }
        
        // 4. 找到借阅了相同图书的其他用户
        Set<Long> processedUserIds = new HashSet<>();
        processedUserIds.add(userId);
        
        for (Long bookId : userBookIds) {
            // 查询借阅了同一本书的其他用户
            List<BorrowRecord> coBorrowRecords = recordDao.findByBookId(bookId);
            
            for (BorrowRecord coRecord : coBorrowRecords) {
                Long otherUserId = coRecord.getUserId();
                
                // 跳过目标用户自己
                if (otherUserId.equals(userId) || processedUserIds.contains(otherUserId)) {
                    continue;
                }
                
                processedUserIds.add(otherUserId);
                
                // 添加其他用户节点
                String otherUserIdStr = "user:" + otherUserId;
                graph.addNode(otherUserIdStr, GraphNode.NodeType.USER);
                
                // 添加从图书到其他用户的边
                String bookIdStr = "book:" + bookId;
                double weight = calculateWeight(coRecord.getBorrowTime());
                graph.addEdge(bookIdStr, otherUserIdStr, weight);
                
                // 添加其他用户借阅的其他图书（扩展子图）
                List<BorrowRecord> otherUserRecords = recordDao.findByUserId(otherUserId);
                for (BorrowRecord otherRecord : otherUserRecords) {
                    String otherBookIdStr = "book:" + otherRecord.getBookId();
                    graph.addNode(otherBookIdStr, GraphNode.NodeType.BOOK);
                    
                    double otherWeight = calculateWeight(otherRecord.getBorrowTime());
                    graph.addEdge(otherUserIdStr, otherBookIdStr, otherWeight);
                }
            }
        }
        
        logger.info("构建用户子图完成: userId={}, nodes={}", userId, graph.getNodeCount());
        return graph;
    }
    
    /**
     * 计算边权重
     * 使用指数衰减：weight = behaviorWeight * exp(-lambda * days)
     */
    private double calculateWeight(LocalDateTime borrowTime) {
        long days = ChronoUnit.DAYS.between(borrowTime, LocalDateTime.now());
        return behaviorWeight * Math.exp(-lambda * days);
    }
}
