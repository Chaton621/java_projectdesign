package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.UserDao;
import com.library.server.dao.BorrowRecordDao;
import com.library.server.model.User;
import com.library.server.model.BorrowRecord;
import com.library.server.service.FineService;
import com.library.server.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * 用户服务
 */
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserDao userDao = new UserDao();
    private final BorrowRecordDao recordDao = new BorrowRecordDao();
    
    /**
     * 用户注册
     */
    public Response register(Request request) {
        String requestId = request.getRequestId();
        
        try {
            String username = request.getPayloadString("username");
            String password = request.getPayloadString("password");
            String role = request.getPayloadString("role");
            
            if (username == null || username.trim().isEmpty() || 
                password == null || password.isEmpty()) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                        JsonUtil.toJsonNode("用户名和密码不能为空"));
            }
            
            if (userDao.findByUsername(username) != null) {
                return Response.error(requestId, ErrorCode.ALREADY_EXISTS, 
                        JsonUtil.toJsonNode("用户名已存在"));
            }
            
            String passwordHash = PasswordUtil.hashPassword(password);
            User user = new User(username, passwordHash, 
                    role != null ? role : "USER", "ACTIVE");
            Long userId = userDao.insertUser(user);
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("userId", userId);
            data.put("username", username);
            
            logger.info("用户注册成功: userId={}, username={}", userId, username);
            return Response.success(requestId, "注册成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("用户注册失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 用户登录
     */
    public Response login(Request request, TokenService tokenService) {
        String requestId = request.getRequestId();
        
        try {
            String username = request.getPayloadString("username");
            String password = request.getPayloadString("password");
            
            if (username == null || password == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                        JsonUtil.toJsonNode("用户名和密码不能为空"));
            }
            
            User user = userDao.findByUsername(username);
            if (user == null) {
                return Response.error(requestId, ErrorCode.AUTH_FAILED, 
                        JsonUtil.toJsonNode("用户名或密码错误"));
            }
            
            // 检查用户状态
            if (!user.isActive()) {
                return Response.error(requestId, ErrorCode.USER_FROZEN);
            }
            
            // 验证密码
            if (!PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
                return Response.error(requestId, ErrorCode.AUTH_FAILED, 
                        JsonUtil.toJsonNode("用户名或密码错误"));
            }
            
            // 生成Token
            String token = tokenService.generateToken(user);
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("token", token);
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("role", user.getRole());
            
            logger.info("用户登录成功: userId={}, username={}", user.getId(), username);
            return Response.success(requestId, "登录成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("用户登录失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 冻结用户（管理员操作）
     */
    public Response freezeUser(Request request) {
        String requestId = request.getRequestId();
        
        try {
            Long userId = request.getPayloadLong("userId");
            if (userId == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                        JsonUtil.toJsonNode("用户ID不能为空"));
            }
            
            User user = userDao.findById(userId);
            if (user == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("用户不存在"));
            }
            
            if (user.isAdmin()) {
                return Response.error(requestId, ErrorCode.FORBIDDEN, 
                        JsonUtil.toJsonNode("不能冻结管理员账户"));
            }
            
            boolean success = userDao.updateStatus(userId, "FROZEN");
            if (!success) {
                return Response.error(requestId, ErrorCode.SERVER_ERROR, 
                        JsonUtil.toJsonNode("冻结用户失败"));
            }
            
            logger.info("冻结用户成功: userId={}, username={}", userId, user.getUsername());
            return Response.success(requestId, "冻结用户成功", null);
            
        } catch (Exception e) {
            logger.error("冻结用户失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 解冻用户（管理员操作）
     */
    public Response unfreezeUser(Request request) {
        String requestId = request.getRequestId();
        
        try {
            Long userId = request.getPayloadLong("userId");
            if (userId == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                        JsonUtil.toJsonNode("用户ID不能为空"));
            }
            
            User user = userDao.findById(userId);
            if (user == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("用户不存在"));
            }
            
            boolean success = userDao.updateStatus(userId, "ACTIVE");
            if (!success) {
                return Response.error(requestId, ErrorCode.SERVER_ERROR, 
                        JsonUtil.toJsonNode("解冻用户失败"));
            }
            
            logger.info("解冻用户成功: userId={}, username={}", userId, user.getUsername());
            return Response.success(requestId, "解冻用户成功", null);
            
        } catch (Exception e) {
            logger.error("解冻用户失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 列出所有用户（管理员操作）
     * 包含用户的欠费信息（包括已记录的欠费和当前逾期的欠费）
     */
    public Response listAllUsers(Request request) {
        String requestId = request.getRequestId();
        
        try {
            List<User> users = userDao.findAllUsers();
            
            ArrayNode userArray = JsonUtil.getObjectMapper().createArrayNode();
            for (User user : users) {
                ObjectNode userNode = JsonUtil.createObjectNode();
                userNode.put("id", user.getId());
                userNode.put("username", user.getUsername());
                userNode.put("role", user.getRole());
                userNode.put("status", user.getStatus());
                
                // 计算总欠费：已记录的欠费 + 当前逾期的欠费
                double totalFine = user.getFineAmount(); // 已记录的欠费
                List<BorrowRecord> overdueRecords = recordDao.findOverdueRecordsByUserId(user.getId());
                
                double currentOverdueFine = 0.0;
                for (BorrowRecord record : overdueRecords) {
                    long overdueDays = FineService.calculateOverdueDays(record.getDueTime());
                    currentOverdueFine += FineService.calculateFine(overdueDays);
                }
                
                double totalOwed = totalFine + currentOverdueFine;
                
                // 返回总欠费（包括当前逾期的欠费）
                userNode.put("fineAmount", totalOwed);
                userNode.put("totalFine", totalFine); // 已记录的欠费
                userNode.put("currentOverdueFine", currentOverdueFine); // 当前逾期的欠费
                
                if (user.getCreatedAt() != null) {
                    userNode.put("createdAt", user.getCreatedAt().toString());
                }
                userArray.add(userNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("users", userArray);
            data.put("total", users.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("列出所有用户失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
}


















