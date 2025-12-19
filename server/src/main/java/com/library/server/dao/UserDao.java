package com.library.server.dao;

import com.library.server.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据访问对象
 */
public class UserDao extends BaseDao {
    private static final Logger logger = LoggerFactory.getLogger(UserDao.class);
    
    /**
     * 根据ID查找用户
     */
    public User findById(Long id) {
        String sql = "SELECT id, username, password_hash, role, status, fine_amount, created_at " +
                     "FROM users WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, id);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
            return null;
        } catch (SQLException e) {
            logger.error("查找用户失败: id={}", id, e);
            throw new RuntimeException("查找用户失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 根据用户名查找用户
     */
    public User findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, role, status, fine_amount, created_at " +
                     "FROM users WHERE username = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
            return null;
        } catch (SQLException e) {
            logger.error("查找用户失败: username={}", username, e);
            throw new RuntimeException("查找用户失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 插入新用户
     */
    public Long insertUser(User user) {
        String sql = "INSERT INTO users (username, password_hash, role, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?) RETURNING id";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getRole());
            stmt.setString(4, user.getStatus());
            stmt.setTimestamp(5, Timestamp.valueOf(user.getCreatedAt()));
            
            rs = stmt.executeQuery();
            if (rs.next()) {
                Long id = rs.getLong("id");
                logger.info("插入用户成功: id={}, username={}", id, user.getUsername());
                return id;
            }
            throw new RuntimeException("插入用户失败：未返回ID");
        } catch (SQLException e) {
            logger.error("插入用户失败: username={}", user.getUsername(), e);
            throw new RuntimeException("插入用户失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 更新用户状态
     */
    public boolean updateStatus(Long userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setLong(2, userId);
            
            int rows = stmt.executeUpdate();
            logger.info("更新用户状态: userId={}, status={}, affectedRows={}", userId, status, rows);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("更新用户状态失败: userId={}, status={}", userId, status, e);
            throw new RuntimeException("更新用户状态失败", e);
        } finally {
            close(conn, stmt);
        }
    }
    
    /**
     * 查找所有用户
     */
    public List<User> findAllUsers() {
        String sql = "SELECT id, username, password_hash, role, status, fine_amount, created_at FROM users";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<User> users = new ArrayList<>();
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
            return users;
        } catch (SQLException e) {
            logger.error("查找所有用户失败", e);
            throw new RuntimeException("查找所有用户失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 将ResultSet映射为User对象
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(rs.getString("role"));
        user.setStatus(rs.getString("status"));
        if (rs.getObject("fine_amount") != null) {
            user.setFineAmount(rs.getDouble("fine_amount"));
        }
        Timestamp timestamp = rs.getTimestamp("created_at");
        user.setCreatedAt(timestamp != null ? timestamp.toLocalDateTime() : null);
        return user;
    }
    
    public boolean updateFineAmount(Connection conn, Long userId, double fineAmount) throws SQLException {
        String sql = "UPDATE users SET fine_amount = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, fineAmount);
            stmt.setLong(2, userId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        }
    }
    
    public boolean addFineAmount(Connection conn, Long userId, double amount) throws SQLException {
        String sql = "UPDATE users SET fine_amount = COALESCE(fine_amount, 0) + ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setLong(2, userId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        }
    }
}
