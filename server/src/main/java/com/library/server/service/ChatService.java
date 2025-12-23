package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.MessageDao;
import com.library.server.dao.UserDao;
import com.library.server.model.Message;
import com.library.server.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 聊天服务
 */
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    
    private final MessageDao messageDao = new MessageDao();
    private final UserDao userDao = new UserDao();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * 发送消息
     */
    public Response sendMessage(Request request, Long senderId) {
        String requestId = request.getRequestId();
        
        try {
            Long receiverId = request.getPayloadLong("receiverId");
            String content = request.getPayloadString("content");
            
            if (receiverId == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("接收者ID不能为空"));
            }
            
            if (content == null || content.trim().isEmpty()) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("消息内容不能为空"));
            }
            
            User receiver = userDao.findById(receiverId);
            if (receiver == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                    JsonUtil.toJsonNode("接收者不存在"));
            }
            
            Message message = new Message();
            message.setSenderId(senderId);
            message.setReceiverId(receiverId);
            message.setContent(content.trim());
            message.setStatus("UNREAD");
            message.setCreatedAt(LocalDateTime.now());
            
            Long messageId = messageDao.insertMessage(message);
            
            logger.info("发送消息成功: messageId={}, senderId={}, receiverId={}", 
                messageId, senderId, receiverId);
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("messageId", messageId);
            data.put("createdAt", message.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return Response.success(requestId, "消息发送成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("发送消息失败: senderId={}", senderId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 获取对话消息
     */
    public Response getConversation(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            Long otherUserId = request.getPayloadLong("otherUserId");
            int limit = request.getPayloadInt("limit", 50);
            int offset = request.getPayloadInt("offset", 0);
            
            if (otherUserId == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("对方用户ID不能为空"));
            }
            
            List<Message> messages = messageDao.getConversation(userId, otherUserId, limit, offset);
            messageDao.markConversationAsRead(userId, otherUserId);
            
            User otherUser = userDao.findById(otherUserId);
            if (otherUser == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                    JsonUtil.toJsonNode("对方用户不存在"));
            }
            
            ArrayNode messageArray = JsonUtil.getObjectMapper().createArrayNode();
            for (Message msg : messages) {
                ObjectNode msgNode = JsonUtil.createObjectNode();
                msgNode.put("id", msg.getId());
                msgNode.put("senderId", msg.getSenderId());
                msgNode.put("receiverId", msg.getReceiverId());
                msgNode.put("content", msg.getContent());
                msgNode.put("status", msg.getStatus());
                msgNode.put("isMine", msg.getSenderId().equals(userId));
                msgNode.put("time", msg.getCreatedAt() != null ? 
                    msg.getCreatedAt().format(TIME_FORMATTER) : "");
                msgNode.put("createdAt", msg.getCreatedAt() != null ? 
                    msg.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                messageArray.add(msgNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("messages", messageArray);
            data.put("otherUserId", otherUserId);
            data.put("otherUsername", otherUser.getUsername());
            data.put("total", messages.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("获取对话失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 获取最近对话列表
     */
    public Response getRecentConversations(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            List<Message> messages = messageDao.getRecentConversations(userId);
            
            Map<Long, Message> latestMessages = new LinkedHashMap<>();
            Map<Long, User> userMap = new HashMap<>();
            
            for (Message msg : messages) {
                Long otherUserId = msg.getSenderId().equals(userId) ? 
                    msg.getReceiverId() : msg.getSenderId();
                
                if (!latestMessages.containsKey(otherUserId)) {
                    latestMessages.put(otherUserId, msg);
                    User otherUser = userDao.findById(otherUserId);
                    if (otherUser != null) {
                        userMap.put(otherUserId, otherUser);
                    }
                }
            }
            
            ArrayNode conversationArray = JsonUtil.getObjectMapper().createArrayNode();
            for (Map.Entry<Long, Message> entry : latestMessages.entrySet()) {
                Long otherUserId = entry.getKey();
                Message msg = entry.getValue();
                User otherUser = userMap.get(otherUserId);
                
                if (otherUser == null) continue;
                
                ObjectNode convNode = JsonUtil.createObjectNode();
                convNode.put("otherUserId", otherUserId);
                convNode.put("otherUsername", otherUser.getUsername());
                convNode.put("lastMessage", msg.getContent());
                convNode.put("lastMessageTime", msg.getCreatedAt() != null ? 
                    msg.getCreatedAt().format(TIME_FORMATTER) : "");
                convNode.put("isUnread", msg.getStatus().equals("UNREAD") && 
                    msg.getReceiverId().equals(userId));
                convNode.put("createdAt", msg.getCreatedAt() != null ? 
                    msg.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                conversationArray.add(convNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("conversations", conversationArray);
            data.put("total", conversationArray.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("获取最近对话失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 获取未读消息数
     */
    public Response getUnreadCount(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            int count = messageDao.getUnreadCount(userId);
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("unreadCount", count);
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("获取未读消息数失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
}




