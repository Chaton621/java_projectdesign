package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.BorrowRecordDao;
import com.library.server.dao.UserDao;
import com.library.server.model.BorrowRecord;
import com.library.server.model.User;
import com.library.server.service.NotificationService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class FineQueryService {
    private static final Logger logger = LoggerFactory.getLogger(FineQueryService.class);
    private final UserDao userDao = new UserDao();
    private final BorrowRecordDao recordDao = new BorrowRecordDao();
    
    public Response getUserFine(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            User user = userDao.findById(userId);
            if (user == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                    JsonUtil.toJsonNode("用户不存在"));
            }
            
            // 安全地获取欠费金额，避免null
            Double fineAmount = user.getFineAmount();
            double totalFine = fineAmount != null ? fineAmount : 0.0;
            
            List<BorrowRecord> overdueRecords = recordDao.findOverdueRecordsByUserId(userId);
            
            double currentOverdueFine = 0.0;
            ArrayNode overdueArray = JsonUtil.getObjectMapper().createArrayNode();
            
            for (BorrowRecord record : overdueRecords) {
                try {
                    if (record.getDueTime() == null) {
                        logger.warn("借阅记录dueTime为null: recordId={}", record.getId());
                        continue;
                    }
                    long overdueDays = FineService.calculateOverdueDays(record.getDueTime());
                    double fine = FineService.calculateFine(overdueDays);
                    currentOverdueFine += fine;
                    
                    ObjectNode recordNode = JsonUtil.createObjectNode();
                    recordNode.put("recordId", record.getId());
                    recordNode.put("bookId", record.getBookId());
                    recordNode.put("overdueDays", overdueDays);
                    recordNode.put("fineAmount", fine);
                    recordNode.put("dueTime", record.getDueTime().toString());
                    overdueArray.add(recordNode);
                } catch (Exception e) {
                    logger.warn("计算逾期罚款失败: recordId={}", record.getId(), e);
                    // 继续处理下一条记录
                }
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("totalFine", totalFine);
            data.put("currentOverdueFine", currentOverdueFine);
            data.put("totalOwed", totalFine + currentOverdueFine);
            data.set("overdueRecords", overdueArray);
            
            String notification = NotificationService.getAndClearNotification(userId);
            if (notification != null) {
                data.put("notification", notification);
            }
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("查询用户欠费失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    public Response getAllUsersFine(Request request) {
        String requestId = request.getRequestId();
        
        try {
            List<User> users = userDao.findAllUsers();
            ArrayNode userArray = JsonUtil.getObjectMapper().createArrayNode();
            
            for (User user : users) {
                if (user.isAdmin()) {
                    continue;
                }
                
                double totalFine = user.getFineAmount();
                List<BorrowRecord> overdueRecords = recordDao.findOverdueRecordsByUserId(user.getId());
                
                double currentOverdueFine = 0.0;
                for (BorrowRecord record : overdueRecords) {
                    long overdueDays = FineService.calculateOverdueDays(record.getDueTime());
                    currentOverdueFine += FineService.calculateFine(overdueDays);
                }
                
                double totalOwed = totalFine + currentOverdueFine;
                
                if (totalOwed > 0) {
                    ObjectNode userNode = JsonUtil.createObjectNode();
                    userNode.put("userId", user.getId());
                    userNode.put("username", user.getUsername());
                    userNode.put("totalFine", totalFine);
                    userNode.put("currentOverdueFine", currentOverdueFine);
                    userNode.put("totalOwed", totalOwed);
                    userNode.put("overdueCount", overdueRecords.size());
                    userArray.add(userNode);
                }
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("users", userArray);
            data.put("total", userArray.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("查询所有用户欠费失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    public Response sendReminder(Request request) {
        String requestId = request.getRequestId();
        
        try {
            Long userId = request.getPayloadLong("userId");
            String message = request.getPayloadString("message");
            
            if (userId == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("用户ID不能为空"));
            }
            
            User user = userDao.findById(userId);
            if (user == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                    JsonUtil.toJsonNode("用户不存在"));
            }
            
            double totalFine = user.getFineAmount();
            List<BorrowRecord> overdueRecords = recordDao.findOverdueRecordsByUserId(userId);
            
            double currentOverdueFine = 0.0;
            for (BorrowRecord record : overdueRecords) {
                long overdueDays = FineService.calculateOverdueDays(record.getDueTime());
                currentOverdueFine += FineService.calculateFine(overdueDays);
            }
            
            double totalOwed = totalFine + currentOverdueFine;
            
            if (message == null || message.isEmpty()) {
                StringBuilder defaultMessage = new StringBuilder();
                defaultMessage.append("尊敬的读者 ").append(user.getUsername()).append("，您好！\n\n");
                defaultMessage.append("您目前有逾期未还的图书，请尽快归还。\n");
                defaultMessage.append("逾期图书数量：").append(overdueRecords.size()).append("本\n");
                if (totalOwed > 0) {
                    defaultMessage.append("当前欠费总额：").append(String.format("%.2f", totalOwed)).append("元\n");
                    defaultMessage.append("（其中已产生罚款：").append(String.format("%.2f", totalFine)).append("元，");
                    defaultMessage.append("当前逾期预计罚款：").append(String.format("%.2f", currentOverdueFine)).append("元）\n");
                }
                defaultMessage.append("\n请尽快到图书馆办理还书和缴费手续，感谢您的配合！");
                message = defaultMessage.toString();
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("userId", userId);
            data.put("username", user.getUsername());
            data.put("message", message);
            data.put("totalOwed", totalOwed);
            data.put("overdueCount", overdueRecords.size());
            
            com.library.server.service.NotificationService.addNotification(userId, message);
            
            logger.info("发送提醒: userId={}, username={}, totalOwed={}", 
                userId, user.getUsername(), totalOwed);
            
            return Response.success(requestId, "提醒已发送", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("发送提醒失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
}











