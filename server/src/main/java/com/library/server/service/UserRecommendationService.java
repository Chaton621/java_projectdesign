package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.BorrowRecordDao;
import com.library.server.dao.DataSourceProvider;
import com.library.server.dao.UserDao;
import com.library.server.model.User;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class UserRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(UserRecommendationService.class);
    private final UserDao userDao = new UserDao();
    private final BorrowRecordDao borrowRecordDao = new BorrowRecordDao();
    
    public Response recommendUsers(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            int topN = request.getPayloadInt("topN", 10);
            
            List<User> allUsers = userDao.findAllUsers();
            Map<Long, Set<Long>> userBooks = new HashMap<>();
            
            for (User user : allUsers) {
                if (user.getId().equals(userId) || user.isAdmin()) {
                    continue;
                }
                
                List<com.library.server.model.BorrowRecord> records = borrowRecordDao.findByUserId(user.getId());
                Set<Long> bookIds = new HashSet<>();
                for (com.library.server.model.BorrowRecord record : records) {
                    bookIds.add(record.getBookId());
                }
                userBooks.put(user.getId(), bookIds);
            }
            
            List<com.library.server.model.BorrowRecord> myRecords = borrowRecordDao.findByUserId(userId);
            Set<Long> myBooks = new HashSet<>();
            for (com.library.server.model.BorrowRecord record : myRecords) {
                myBooks.add(record.getBookId());
            }
            
            List<UserSimilarity> similarities = new ArrayList<>();
            for (Map.Entry<Long, Set<Long>> entry : userBooks.entrySet()) {
                Long otherUserId = entry.getKey();
                Set<Long> otherBooks = entry.getValue();
                
                Set<Long> intersection = new HashSet<>(myBooks);
                intersection.retainAll(otherBooks);
                
                Set<Long> union = new HashSet<>(myBooks);
                union.addAll(otherBooks);
                
                double similarity = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
                
                User otherUser = userDao.findById(otherUserId);
                if (otherUser != null) {
                    similarities.add(new UserSimilarity(otherUser, similarity, intersection.size()));
                }
            }
            
            similarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            
            ArrayNode userArray = JsonUtil.getObjectMapper().createArrayNode();
            int count = 0;
            for (UserSimilarity us : similarities) {
                if (count >= topN) break;
                
                ObjectNode userNode = JsonUtil.createObjectNode();
                userNode.put("userId", us.user.getId());
                userNode.put("username", us.user.getUsername());
                userNode.put("similarity", us.similarity);
                userNode.put("commonBooks", us.commonBooks);
                userArray.add(userNode);
                count++;
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("users", userArray);
            data.put("total", userArray.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("推荐用户失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    public Response searchUsers(Request request) {
        String requestId = request.getRequestId();
        
        try {
            String keyword = request.getPayloadString("keyword");
            if (keyword == null || keyword.trim().isEmpty()) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("搜索关键词不能为空"));
            }
            
            String sql = "SELECT id, username, role, status, fine_amount, created_at " +
                         "FROM users " +
                         "WHERE username ILIKE ? AND role = 'USER' " +
                         "ORDER BY username LIMIT 20";
            
            List<User> users = new ArrayList<>();
            try (Connection conn = DataSourceProvider.getDataSource().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "%" + keyword.trim() + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        User user = new User();
                        user.setId(rs.getLong("id"));
                        user.setUsername(rs.getString("username"));
                        user.setRole(rs.getString("role"));
                        user.setStatus(rs.getString("status"));
                        if (rs.getObject("fine_amount") != null) {
                            user.setFineAmount(rs.getDouble("fine_amount"));
                        }
                        users.add(user);
                    }
                }
            }
            
            ArrayNode userArray = JsonUtil.getObjectMapper().createArrayNode();
            for (User user : users) {
                ObjectNode userNode = JsonUtil.createObjectNode();
                userNode.put("userId", user.getId());
                userNode.put("username", user.getUsername());
                userNode.put("status", user.getStatus());
                userArray.add(userNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("users", userArray);
            data.put("total", users.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("搜索用户失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    private static class UserSimilarity {
        User user;
        double similarity;
        int commonBooks;
        
        UserSimilarity(User user, double similarity, int commonBooks) {
            this.user = user;
            this.similarity = similarity;
            this.commonBooks = commonBooks;
        }
    }
}





