package com.library.server.dao;

import com.library.server.model.Book;
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
 * 图书数据访问对象
 */
public class BookDao extends BaseDao {
    private static final Logger logger = LoggerFactory.getLogger(BookDao.class);
    
    /**
     * 根据ID查找图书
     */
    public Book findById(Long id) {
        String sql = "SELECT id, isbn, title, author, category, publisher, description, " +
                     "cover_image_path, total_count, available_count, created_at " +
                     "FROM books WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, id);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToBook(rs);
            }
            return null;
        } catch (SQLException e) {
            logger.error("查找图书失败: id={}", id, e);
            throw new RuntimeException("查找图书失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 搜索图书
     * @param keyword 关键词（标题、作者、ISBN）
     * @param category 分类
     * @param limit 限制数量
     * @param offset 偏移量
     */
    public List<Book> searchBooks(String keyword, String category, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, isbn, title, author, category, publisher, description, " +
            "cover_image_path, total_count, available_count, created_at " +
            "FROM books WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (title ILIKE ? OR author ILIKE ? OR isbn ILIKE ?)");
            String pattern = "%" + keyword + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        
        if (category != null && !category.trim().isEmpty()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<Book> books = new ArrayList<>();
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                books.add(mapResultSetToBook(rs));
            }
            return books;
        } catch (SQLException e) {
            logger.error("搜索图书失败: keyword={}, category={}", keyword, category, e);
            throw new RuntimeException("搜索图书失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 更新图书库存
     * @param conn 数据库连接（如果为null则自己创建连接并管理事务）
     * @param bookId 图书ID
     * @param delta 库存变化量（正数增加，负数减少）
     * @return 是否更新成功
     */
    public boolean updateBookStock(Connection conn, Long bookId, int delta) {
        if (conn == null) {
            Connection ownConn = null;
            try {
                ownConn = getConnection();
                ownConn.setAutoCommit(false);
                boolean result = updateBookStockInternal(ownConn, bookId, delta);
                ownConn.commit();
                return result;
            } catch (SQLException e) {
                if (ownConn != null) {
                    rollback(ownConn);
                }
                logger.error("更新图书库存失败: bookId={}, delta={}", bookId, delta, e);
                throw new RuntimeException("更新图书库存失败", e);
            } finally {
                if (ownConn != null) {
                    try {
                        ownConn.setAutoCommit(true);
                    } catch (SQLException e) {
                        logger.error("恢复自动提交失败", e);
                    }
                }
                close(ownConn);
            }
        }
        
        try {
            return updateBookStockInternal(conn, bookId, delta);
        } catch (SQLException e) {
            logger.error("更新图书库存失败: bookId={}, delta={}", bookId, delta, e);
            throw new RuntimeException("更新图书库存失败", e);
        }
    }
    
    /**
     * 内部方法：执行实际的库存更新操作
     */
    private boolean updateBookStockInternal(Connection conn, Long bookId, int delta) throws SQLException {
        String sql = "UPDATE books SET available_count = available_count + ? " +
                     "WHERE id = ? AND available_count + ? >= 0 AND available_count + ? <= total_count";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, delta);
            stmt.setLong(2, bookId);
            stmt.setInt(3, delta);
            stmt.setInt(4, delta);
            
            int rows = stmt.executeUpdate();
            logger.info("更新图书库存: bookId={}, delta={}, affectedRows={}", bookId, delta, rows);
            return rows > 0;
        }
    }
    
    /**
     * 插入新图书
     */
    public Long insertBook(Book book) {
        String sql = "INSERT INTO books (isbn, title, author, category, publisher, description, " +
                     "cover_image_path, total_count, available_count, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, book.getIsbn());
            stmt.setString(2, book.getTitle());
            stmt.setString(3, book.getAuthor());
            stmt.setString(4, book.getCategory());
            stmt.setString(5, book.getPublisher());
            stmt.setString(6, book.getDescription());
            stmt.setString(7, book.getCoverImagePath());
            stmt.setInt(8, book.getTotalCount());
            stmt.setInt(9, book.getAvailableCount());
            stmt.setTimestamp(10, Timestamp.valueOf(
                book.getCreatedAt() != null ? book.getCreatedAt() : LocalDateTime.now()));
            
            rs = stmt.executeQuery();
            if (rs.next()) {
                Long id = rs.getLong("id");
                logger.info("插入图书成功: id={}, title={}", id, book.getTitle());
                return id;
            }
            throw new RuntimeException("插入图书失败：未返回ID");
        } catch (SQLException e) {
            logger.error("插入图书失败: title={}", book.getTitle(), e);
            throw new RuntimeException("插入图书失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 更新图书信息
     */
    public boolean updateBook(Book book) {
        String sql = "UPDATE books SET isbn = ?, title = ?, author = ?, category = ?, " +
                     "publisher = ?, description = ?, cover_image_path = ?, " +
                     "total_count = ?, available_count = ? WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, book.getIsbn());
            stmt.setString(2, book.getTitle());
            stmt.setString(3, book.getAuthor());
            stmt.setString(4, book.getCategory());
            stmt.setString(5, book.getPublisher());
            stmt.setString(6, book.getDescription());
            stmt.setString(7, book.getCoverImagePath());
            stmt.setInt(8, book.getTotalCount());
            stmt.setInt(9, book.getAvailableCount());
            stmt.setLong(10, book.getId());
            
            int rows = stmt.executeUpdate();
            logger.info("更新图书: id={}, title={}, affectedRows={}", 
                book.getId(), book.getTitle(), rows);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("更新图书失败: id={}", book.getId(), e);
            throw new RuntimeException("更新图书失败", e);
        } finally {
            close(conn, stmt);
        }
    }
    
    /**
     * 删除图书
     */
    public boolean deleteBook(Long bookId) {
        String sql = "DELETE FROM books WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, bookId);
            
            int rows = stmt.executeUpdate();
            logger.info("删除图书: id={}, affectedRows={}", bookId, rows);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("删除图书失败: id={}", bookId, e);
            throw new RuntimeException("删除图书失败", e);
        } finally {
            close(conn, stmt);
        }
    }
    
    /**
     * 将ResultSet映射为Book对象
     */
    private Book mapResultSetToBook(ResultSet rs) throws SQLException {
        Book book = new Book();
        book.setId(rs.getLong("id"));
        book.setIsbn(rs.getString("isbn"));
        book.setTitle(rs.getString("title"));
        book.setAuthor(rs.getString("author"));
        book.setCategory(rs.getString("category"));
        book.setPublisher(rs.getString("publisher"));
        book.setDescription(rs.getString("description"));
        book.setCoverImagePath(rs.getString("cover_image_path"));
        book.setTotalCount(rs.getInt("total_count"));
        book.setAvailableCount(rs.getInt("available_count"));
        Timestamp timestamp = rs.getTimestamp("created_at");
        book.setCreatedAt(timestamp != null ? timestamp.toLocalDateTime() : null);
        return book;
    }
    
}
