package com.library.server.dao;

import com.library.server.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessageDao extends BaseDao {
    private static final Logger logger = LoggerFactory.getLogger(MessageDao.class);
    
    public Long insertMessage(Message message) {
        String sql = "INSERT INTO messages (sender_id, receiver_id, content, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?) RETURNING id";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, message.getSenderId());
            stmt.setLong(2, message.getReceiverId());
            stmt.setString(3, message.getContent());
            stmt.setString(4, message.getStatus());
            stmt.setTimestamp(5, Timestamp.valueOf(
                message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now()));
            
            rs = stmt.executeQuery();
            if (rs.next()) {
                Long id = rs.getLong("id");
                logger.info("插入消息成功: id={}, senderId={}, receiverId={}", 
                    id, message.getSenderId(), message.getReceiverId());
                return id;
            }
            throw new RuntimeException("插入消息失败：未返回ID");
        } catch (SQLException e) {
            logger.error("插入消息失败", e);
            throw new RuntimeException("插入消息失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    public List<Message> getConversation(Long userId1, Long userId2, int limit, int offset) {
        String sql = "SELECT id, sender_id, receiver_id, content, status, created_at " +
                     "FROM messages " +
                     "WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) " +
                     "ORDER BY created_at DESC LIMIT ? OFFSET ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, userId1);
            stmt.setLong(2, userId2);
            stmt.setLong(3, userId2);
            stmt.setLong(4, userId1);
            stmt.setInt(5, limit);
            stmt.setInt(6, offset);
            rs = stmt.executeQuery();
            
            List<Message> messages = new ArrayList<>();
            while (rs.next()) {
                messages.add(mapResultSetToMessage(rs));
            }
            return messages;
        } catch (SQLException e) {
            logger.error("查询对话失败: userId1={}, userId2={}", userId1, userId2, e);
            throw new RuntimeException("查询对话失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    public List<Message> getRecentConversations(Long userId) {
        String sql = "SELECT DISTINCT ON (other_user_id) " +
                     "id, sender_id, receiver_id, content, status, created_at, " +
                     "CASE WHEN sender_id = ? THEN receiver_id ELSE sender_id END as other_user_id " +
                     "FROM messages " +
                     "WHERE sender_id = ? OR receiver_id = ? " +
                     "ORDER BY other_user_id, created_at DESC";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            stmt.setLong(3, userId);
            rs = stmt.executeQuery();
            
            List<Message> messages = new ArrayList<>();
            while (rs.next()) {
                messages.add(mapResultSetToMessage(rs));
            }
            return messages;
        } catch (SQLException e) {
            logger.warn("使用DISTINCT ON失败，尝试备用查询: {}", e.getMessage());
            return getRecentConversationsFallback(userId);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    private List<Message> getRecentConversationsFallback(Long userId) {
        String sql = "SELECT id, sender_id, receiver_id, content, status, created_at " +
                     "FROM messages " +
                     "WHERE sender_id = ? OR receiver_id = ? " +
                     "ORDER BY created_at DESC LIMIT 50";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            rs = stmt.executeQuery();
            
            Map<Long, Message> latestMessages = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                Message msg = mapResultSetToMessage(rs);
                Long otherUserId = msg.getSenderId().equals(userId) ? msg.getReceiverId() : msg.getSenderId();
                if (!latestMessages.containsKey(otherUserId)) {
                    latestMessages.put(otherUserId, msg);
                }
            }
            return new ArrayList<>(latestMessages.values());
        } catch (SQLException e) {
            logger.error("查询最近对话失败: userId={}", userId, e);
            throw new RuntimeException("查询最近对话失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    public int getUnreadCount(Long userId) {
        String sql = "SELECT COUNT(*) FROM messages WHERE receiver_id = ? AND status = 'UNREAD'";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, userId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.error("查询未读消息数失败: userId={}", userId, e);
            throw new RuntimeException("查询未读消息数失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    public boolean markAsRead(Long messageId, Long userId) {
        String sql = "UPDATE messages SET status = 'READ' " +
                     "WHERE id = ? AND receiver_id = ? AND status = 'UNREAD'";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, messageId);
            stmt.setLong(2, userId);
            
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("标记消息已读失败: messageId={}, userId={}", messageId, userId, e);
            throw new RuntimeException("标记消息已读失败", e);
        } finally {
            close(conn, stmt);
        }
    }
    
    public boolean markConversationAsRead(Long userId1, Long userId2) {
        String sql = "UPDATE messages SET status = 'READ' " +
                     "WHERE receiver_id = ? AND sender_id = ? AND status = 'UNREAD'";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, userId1);
            stmt.setLong(2, userId2);
            
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("标记对话已读失败: userId1={}, userId2={}", userId1, userId2, e);
            throw new RuntimeException("标记对话已读失败", e);
        } finally {
            close(conn, stmt);
        }
    }
    
    private Message mapResultSetToMessage(ResultSet rs) throws SQLException {
        Message message = new Message();
        message.setId(rs.getLong("id"));
        message.setSenderId(rs.getLong("sender_id"));
        message.setReceiverId(rs.getLong("receiver_id"));
        message.setContent(rs.getString("content"));
        message.setStatus(rs.getString("status"));
        Timestamp timestamp = rs.getTimestamp("created_at");
        message.setCreatedAt(timestamp != null ? timestamp.toLocalDateTime() : null);
        return message;
    }
}





