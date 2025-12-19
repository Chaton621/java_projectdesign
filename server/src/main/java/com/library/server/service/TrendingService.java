package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.BookDao;
import com.library.server.dao.BorrowRecordDao;
import com.library.server.model.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 热门榜单服务
 * 按借阅记录统计TopN，或读缓存表
 */
public class TrendingService {
    private static final Logger logger = LoggerFactory.getLogger(TrendingService.class);
    private final BookDao bookDao = new BookDao();
    
    /**
     * 获取热门图书
     */
    public Response getTrending(Request request) {
        String requestId = request.getRequestId();
        
        try {
            Integer topN = request.getPayloadInt("topN");
            if (topN == null || topN <= 0) topN = 10;
            if (topN > 100) topN = 100;
            
            List<TrendingBook> trendingBooks = getTrendingBooks(topN, 30);
            
            ArrayNode bookArray = JsonUtil.getObjectMapper().createArrayNode();
            for (TrendingBook tb : trendingBooks) {
                ObjectNode bookNode = JsonUtil.createObjectNode();
                bookNode.put("bookId", tb.bookId);
                bookNode.put("borrowCount", tb.borrowCount);
                
                Book book = bookDao.findById(tb.bookId);
                if (book != null) {
                    bookNode.put("title", book.getTitle());
                    bookNode.put("author", book.getAuthor());
                    bookNode.put("category", book.getCategory());
                    bookNode.put("availableCount", book.getAvailableCount());
                }
                
                bookArray.add(bookNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("books", bookArray);
            data.put("total", trendingBooks.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("查询热门图书失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 获取热门图书（按借阅记录统计）
     */
    private List<TrendingBook> getTrendingBooks(int topN, int days) {
        List<TrendingBook> trendingBooks = new ArrayList<>();
        String sql = "SELECT book_id, COUNT(*) as borrow_count " +
                     "FROM borrow_records " +
                     "WHERE borrow_time >= CURRENT_DATE - INTERVAL '? days' " +
                     "GROUP BY book_id " +
                     "ORDER BY borrow_count DESC " +
                     "LIMIT ?";
        
        sql = String.format(
            "SELECT br.book_id, COUNT(*) as borrow_count " +
            "FROM borrow_records br " +
            "INNER JOIN users u ON br.user_id = u.id " +
            "WHERE br.borrow_time >= CURRENT_DATE - INTERVAL '%d days' " +
            "AND u.role != 'ADMIN' " +
            "GROUP BY br.book_id " +
            "ORDER BY borrow_count DESC " +
            "LIMIT %d",
            days, topN
        );
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                TrendingBook tb = new TrendingBook();
                tb.bookId = rs.getLong("book_id");
                tb.borrowCount = rs.getInt("borrow_count");
                trendingBooks.add(tb);
            }
            
            return trendingBooks;
        } catch (SQLException e) {
            logger.error("查询热门图书失败", e);
            throw new RuntimeException("查询热门图书失败", e);
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (SQLException e) {}
            }
            if (stmt != null) {
                try { stmt.close(); } catch (SQLException e) {}
            }
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) {}
            }
        }
    }
    
    /**
     * 热门图书数据类
     */
    private static class TrendingBook {
        Long bookId;
        Integer borrowCount;
    }
}

