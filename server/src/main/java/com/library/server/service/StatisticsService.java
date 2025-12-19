package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class StatisticsService {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsService.class);

    public Response getStatistics(Request request) {
        String requestId = request.getRequestId();
        logger.info("开始获取统计数据: requestId={}", requestId);

        try {
            ObjectNode data = JsonUtil.createObjectNode();

            // 获取借阅总数
            int totalBorrowed = getTotalBorrowedCount();
            data.put("totalBorrowed", totalBorrowed);
            logger.debug("借阅总数: {}", totalBorrowed);

            // 获取分类统计
            List<CategoryStat> categoryStats = getCategoryStatistics();
            ArrayNode categoryArray = JsonUtil.getObjectMapper().createArrayNode();
            int totalCategories = getAllCategoriesCount();  // 统计所有图书的分类数量
            for (CategoryStat stat : categoryStats) {
                ObjectNode statNode = JsonUtil.createObjectNode();
                statNode.put("category", stat.category != null ? stat.category : "未知");
                statNode.put("count", stat.count);
                categoryArray.add(statNode);
            }
            data.set("categoryStats", categoryArray);
            data.put("totalCategories", totalCategories);
            logger.debug("分类统计: 总数={}, 分类数={}", categoryStats.size(), totalCategories);

            // 获取趋势数据
            List<TrendData> trendData = getTrendData(7);
            ArrayNode trendArray = JsonUtil.getObjectMapper().createArrayNode();
            for (TrendData trend : trendData) {
                ObjectNode trendNode = JsonUtil.createObjectNode();
                trendNode.put("date", trend.date);
                trendNode.put("count", trend.count);
                trendArray.add(trendNode);
            }
            data.set("trendData", trendArray);
            logger.debug("趋势数据: 天数={}", trendData.size());

            logger.info("获取统计数据成功: requestId={}, totalBorrowed={}, totalCategories={}", 
                    requestId, totalBorrowed, totalCategories);
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));

        } catch (Exception e) {
            logger.error("获取统计数据失败: requestId={}", requestId, e);
            // 即使出错也返回一个包含默认值的响应，避免客户端显示空白
            try {
                ObjectNode errorData = JsonUtil.createObjectNode();
                errorData.put("totalBorrowed", 0);
                errorData.put("totalCategories", 0);
                errorData.set("categoryStats", JsonUtil.getObjectMapper().createArrayNode());
                errorData.set("trendData", JsonUtil.getObjectMapper().createArrayNode());
                return Response.success(requestId, "查询成功（部分数据可能不完整）", JsonUtil.toJsonNode(errorData));
            } catch (Exception ex) {
                logger.error("创建错误响应失败", ex);
                return Response.error(requestId, ErrorCode.SERVER_ERROR, "获取统计数据失败: " + e.getMessage());
            }
        }
    }

    private int getTotalBorrowedCount() {
        String sql = "SELECT COUNT(*) FROM borrow_records br " +
                     "INNER JOIN users u ON br.user_id = u.id " +
                     "WHERE br.status = 'BORROWED' AND u.role != 'ADMIN'";
        try (Connection conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("查询借阅总数失败", e);
        }
        return 0;
    }

    private List<CategoryStat> getCategoryStatistics() {
        List<CategoryStat> stats = new ArrayList<>();
        String sql = "SELECT COALESCE(b.category, '未知') as category, COUNT(*) as count " +
                     "FROM borrow_records br " +
                     "INNER JOIN books b ON br.book_id = b.id " +
                     "INNER JOIN users u ON br.user_id = u.id " +
                     "WHERE u.role != 'ADMIN' " +
                     "GROUP BY b.category " +
                     "ORDER BY count DESC";
        try (Connection conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                CategoryStat stat = new CategoryStat();
                String category = rs.getString("category");
                stat.category = (category != null && !category.isEmpty()) ? category : "未知";
                stat.count = rs.getInt("count");
                if (stat.count > 0) {
                    stats.add(stat);
                }
            }
            logger.debug("查询到 {} 个分类的统计信息", stats.size());
        } catch (SQLException e) {
            logger.error("查询分类统计失败", e);
        }
        return stats;
    }
    
    /**
     * 获取所有图书的分类数量（不重复）
     */
    private int getAllCategoriesCount() {
        String sql = "SELECT COUNT(DISTINCT category) FROM books WHERE category IS NOT NULL AND category != ''";
        try (Connection conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("查询所有分类数量失败", e);
        }
        return 0;
    }

    private List<TrendData> getTrendData(int days) {
        List<TrendData> trendData = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.format(formatter);
            int count = getBorrowCountForDate(date);
            TrendData trend = new TrendData();
            trend.date = dateStr;
            trend.count = count;
            trendData.add(trend);
        }

        return trendData;
    }

    private int getBorrowCountForDate(LocalDate date) {
        String sql = "SELECT COUNT(*) FROM borrow_records br " +
                     "INNER JOIN users u ON br.user_id = u.id " +
                     "WHERE DATE(br.borrow_time) = ? AND u.role != 'ADMIN'";
        try (Connection conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("查询日期借阅数量失败: date=" + date, e);
        }
        return 0;
    }

    private static class CategoryStat {
        String category;
        int count;
    }

    private static class TrendData {
        String date;
        int count;
    }
}





