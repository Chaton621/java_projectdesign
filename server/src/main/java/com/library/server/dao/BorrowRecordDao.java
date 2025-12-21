package com.library.server.dao;

import com.library.server.model.BorrowRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 借阅记录数据访问对象
 */
public class BorrowRecordDao extends BaseDao {
    private static final Logger logger = LoggerFactory.getLogger(BorrowRecordDao.class);
    
    /**
     * 根据ID查找借阅记录
     */
    public BorrowRecord findById(Long id) {
        String sql = "SELECT id, user_id, book_id, borrow_time, due_time, return_time, " +
                     "status, fine_amount, created_at FROM borrow_records WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, id);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToRecord(rs);
            }
            return null;
        } catch (SQLException e) {
            logger.error("查找借阅记录失败: id={}", id, e);
            throw new RuntimeException("查找借阅记录失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 根据用户ID和状态查找借阅记录
     */
    public List<BorrowRecord> findByUserIdAndStatus(Long userId, String status) {
        String sql = "SELECT id, user_id, book_id, borrow_time, due_time, return_time, " +
                     "status, fine_amount, created_at FROM borrow_records WHERE user_id = ? AND status = ? " +
                     "ORDER BY borrow_time DESC";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, userId);
            stmt.setString(2, status);
            rs = stmt.executeQuery();
            
            List<BorrowRecord> records = new ArrayList<>();
            while (rs.next()) {
                records.add(mapResultSetToRecord(rs));
            }
            return records;
        } catch (SQLException e) {
            logger.error("查找借阅记录失败: userId={}, status={}", userId, status, e);
            throw new RuntimeException("查找借阅记录失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 根据用户ID查找所有借阅记录
     */
    public List<BorrowRecord> findByUserId(Long userId) {
        String sql = "SELECT id, user_id, book_id, borrow_time, due_time, return_time, " +
                     "status, fine_amount, created_at FROM borrow_records WHERE user_id = ? " +
                     "ORDER BY borrow_time DESC";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, userId);
            rs = stmt.executeQuery();
            
            List<BorrowRecord> records = new ArrayList<>();
            while (rs.next()) {
                records.add(mapResultSetToRecord(rs));
            }
            return records;
        } catch (SQLException e) {
            logger.error("查找借阅记录失败: userId={}", userId, e);
            throw new RuntimeException("查找借阅记录失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 根据图书ID查找所有借阅记录
     */
    public List<BorrowRecord> findByBookId(Long bookId) {
        String sql = "SELECT id, user_id, book_id, borrow_time, due_time, return_time, " +
                     "status, fine_amount, created_at FROM borrow_records WHERE book_id = ? " +
                     "ORDER BY borrow_time DESC";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, bookId);
            rs = stmt.executeQuery();
            
            List<BorrowRecord> records = new ArrayList<>();
            while (rs.next()) {
                records.add(mapResultSetToRecord(rs));
            }
            return records;
        } catch (SQLException e) {
            logger.error("查找借阅记录失败: bookId={}", bookId, e);
            throw new RuntimeException("查找借阅记录失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 插入借阅记录（事务中调用）
     */
    public Long insertBorrowRecord(Connection conn, BorrowRecord record) throws SQLException {
        String sql = "INSERT INTO borrow_records (user_id, book_id, borrow_time, due_time, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, record.getUserId());
            stmt.setLong(2, record.getBookId());
            stmt.setTimestamp(3, Timestamp.valueOf(record.getBorrowTime()));
            stmt.setTimestamp(4, Timestamp.valueOf(record.getDueTime()));
            stmt.setString(5, record.getStatus());
            stmt.setTimestamp(6, Timestamp.valueOf(
                record.getCreatedAt() != null ? record.getCreatedAt() : LocalDateTime.now()));
            
            rs = stmt.executeQuery();
            if (rs.next()) {
                Long id = rs.getLong("id");
                logger.info("插入借阅记录成功: id={}, userId={}, bookId={}", 
                    id, record.getUserId(), record.getBookId());
                return id;
            }
            throw new RuntimeException("插入借阅记录失败：未返回ID");
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }
    
    /**
     * 标记为已归还（事务中调用）
     */
    public boolean markReturned(Connection conn, Long recordId, Double fineAmount) throws SQLException {
        String sql = "UPDATE borrow_records SET return_time = ?, status = ?, fine_amount = ? WHERE id = ?";
        
        PreparedStatement stmt = null;
        
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(2, "RETURNED");
            stmt.setObject(3, fineAmount);
            stmt.setLong(4, recordId);
            
            int rows = stmt.executeUpdate();
            logger.info("标记归还成功: recordId={}, fineAmount={}, affectedRows={}", recordId, fineAmount, rows);
            return rows > 0;
        } finally {
            if (stmt != null) stmt.close();
        }
    }
    
    public boolean markReturned(Connection conn, Long recordId) throws SQLException {
        return markReturned(conn, recordId, null);
    }
    
    /**
     * 检查用户是否有逾期图书
     */
    public boolean hasOverdueBooks(Long userId) {
        String sql = "SELECT COUNT(*) FROM borrow_records " +
                     "WHERE user_id = ? AND status IN ('BORROWED', 'OVERDUE') " +
                     "AND due_time < CURRENT_TIMESTAMP";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, userId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        } catch (SQLException e) {
            logger.error("检查逾期图书失败: userId={}", userId, e);
            throw new RuntimeException("检查逾期图书失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 插入借阅记录（独立调用，自己管理连接）
     */
    public Long insertRecord(BorrowRecord record) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            Long id = insertBorrowRecord(conn, record);
            conn.commit();
            return id;
        } catch (SQLException e) {
            if (conn != null) {
                rollback(conn);
            }
            logger.error("插入借阅记录失败", e);
            throw new RuntimeException("插入借阅记录失败", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.error("恢复自动提交失败", e);
                }
            }
            close(conn);
        }
    }
    
    /**
     * 查找所有借阅记录（管理员操作）
     * 排除管理员账户的借阅记录
     */
    public List<BorrowRecord> findAll(int limit, int offset) {
        String sql = "SELECT br.id, br.user_id, br.book_id, br.borrow_time, br.due_time, br.return_time, " +
                     "br.status, br.fine_amount, br.created_at " +
                     "FROM borrow_records br " +
                     "INNER JOIN users u ON br.user_id = u.id " +
                     "WHERE u.role != 'ADMIN' " +
                     "ORDER BY br.borrow_time DESC LIMIT ? OFFSET ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            rs = stmt.executeQuery();
            
            List<BorrowRecord> records = new ArrayList<>();
            while (rs.next()) {
                records.add(mapResultSetToRecord(rs));
            }
            return records;
        } catch (SQLException e) {
            logger.error("查找所有借阅记录失败", e);
            throw new RuntimeException("查找所有借阅记录失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 根据状态查找所有借阅记录（管理员操作）
     * 排除管理员账户的借阅记录
     */
    public List<BorrowRecord> findAllByStatus(String status, int limit, int offset) {
        String sql = "SELECT br.id, br.user_id, br.book_id, br.borrow_time, br.due_time, br.return_time, " +
                     "br.status, br.fine_amount, br.created_at " +
                     "FROM borrow_records br " +
                     "INNER JOIN users u ON br.user_id = u.id " +
                     "WHERE br.status = ? AND u.role != 'ADMIN' " +
                     "ORDER BY br.borrow_time DESC LIMIT ? OFFSET ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            rs = stmt.executeQuery();
            
            List<BorrowRecord> records = new ArrayList<>();
            while (rs.next()) {
                records.add(mapResultSetToRecord(rs));
            }
            return records;
        } catch (SQLException e) {
            logger.error("根据状态查找借阅记录失败: status={}", status, e);
            throw new RuntimeException("根据状态查找借阅记录失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 将ResultSet映射为BorrowRecord对象
     */
    private BorrowRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        BorrowRecord record = new BorrowRecord();
        record.setId(rs.getLong("id"));
        record.setUserId(rs.getLong("user_id"));
        record.setBookId(rs.getLong("book_id"));
        Timestamp borrowTime = rs.getTimestamp("borrow_time");
        record.setBorrowTime(borrowTime != null ? borrowTime.toLocalDateTime() : null);
        Timestamp dueTime = rs.getTimestamp("due_time");
        record.setDueTime(dueTime != null ? dueTime.toLocalDateTime() : null);
        Timestamp returnTime = rs.getTimestamp("return_time");
        record.setReturnTime(returnTime != null ? returnTime.toLocalDateTime() : null);
        record.setStatus(rs.getString("status"));
        if (rs.getObject("fine_amount") != null) {
            record.setFineAmount(rs.getDouble("fine_amount"));
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        record.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return record;
    }
    
    public List<BorrowRecord> findOverdueRecordsByUserId(Long userId) {
        String sql = "SELECT id, user_id, book_id, borrow_time, due_time, return_time, " +
                     "status, fine_amount, created_at FROM borrow_records " +
                     "WHERE user_id = ? AND status IN ('BORROWED', 'OVERDUE') " +
                     "AND due_time < CURRENT_TIMESTAMP " +
                     "ORDER BY due_time ASC";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, userId);
            rs = stmt.executeQuery();
            
            List<BorrowRecord> records = new ArrayList<>();
            while (rs.next()) {
                records.add(mapResultSetToRecord(rs));
            }
            return records;
        } catch (SQLException e) {
            logger.error("查找逾期记录失败: userId={}", userId, e);
            throw new RuntimeException("查找逾期记录失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 获取图书的借阅次数
     */
    public int getBorrowCountByBookId(Long bookId) {
        String sql = "SELECT COUNT(*) FROM borrow_records WHERE book_id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, bookId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.error("获取图书借阅次数失败: bookId={}", bookId, e);
            return 0;
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 批量获取图书的借阅次数
     */
    public Map<Long, Integer> getBorrowCountsByBookIds(List<Long> bookIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (bookIds == null || bookIds.isEmpty()) {
            return result;
        }
        
        String placeholders = String.join(",", Collections.nCopies(bookIds.size(), "?"));
        String sql = String.format(
            "SELECT book_id, COUNT(*) as borrow_count " +
            "FROM borrow_records " +
            "WHERE book_id IN (%s) " +
            "GROUP BY book_id",
            placeholders
        );
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            for (int i = 0; i < bookIds.size(); i++) {
                stmt.setLong(i + 1, bookIds.get(i));
            }
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Long bookId = rs.getLong("book_id");
                int count = rs.getInt("borrow_count");
                result.put(bookId, count);
            }
            
            // 为没有借阅记录的图书设置0
            for (Long bookId : bookIds) {
                if (!result.containsKey(bookId)) {
                    result.put(bookId, 0);
                }
            }
            
            return result;
        } catch (SQLException e) {
            logger.error("批量获取图书借阅次数失败", e);
            // 返回默认值0
            for (Long bookId : bookIds) {
                result.put(bookId, 0);
            }
            return result;
        } finally {
            close(conn, stmt, rs);
        }
    }
}














