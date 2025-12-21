package com.library.server.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 罚款梯度价格配置数据访问对象
 */
public class FineRateConfigDao extends BaseDao {
    private static final Logger logger = LoggerFactory.getLogger(FineRateConfigDao.class);
    
    /**
     * 罚款梯度配置
     */
    public static class FineRateConfig {
        private Long id;
        private Integer dayRangeStart;
        private Integer dayRangeEnd;
        private Double ratePerDay;
        private String description;
        private Integer displayOrder;
        
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getDayRangeStart() { return dayRangeStart; }
        public void setDayRangeStart(Integer dayRangeStart) { this.dayRangeStart = dayRangeStart; }
        public Integer getDayRangeEnd() { return dayRangeEnd; }
        public void setDayRangeEnd(Integer dayRangeEnd) { this.dayRangeEnd = dayRangeEnd; }
        public Double getRatePerDay() { return ratePerDay; }
        public void setRatePerDay(Double ratePerDay) { this.ratePerDay = ratePerDay; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    }
    
    /**
     * 获取所有配置（按显示顺序排序）
     */
    public List<FineRateConfig> findAll() {
        String sql = "SELECT id, day_range_start, day_range_end, rate_per_day, description, display_order " +
                     "FROM fine_rate_config ORDER BY display_order ASC, day_range_start ASC";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<FineRateConfig> configs = new ArrayList<>();
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                FineRateConfig config = new FineRateConfig();
                config.setId(rs.getLong("id"));
                config.setDayRangeStart(rs.getInt("day_range_start"));
                Integer dayRangeEnd = rs.getObject("day_range_end", Integer.class);
                config.setDayRangeEnd(dayRangeEnd);
                config.setRatePerDay(rs.getDouble("rate_per_day"));
                config.setDescription(rs.getString("description"));
                config.setDisplayOrder(rs.getInt("display_order"));
                configs.add(config);
            }
            
            return configs;
        } catch (SQLException e) {
            logger.error("查询罚款梯度配置失败", e);
            throw new RuntimeException("查询罚款梯度配置失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 根据ID查找配置
     */
    public FineRateConfig findById(Long id) {
        String sql = "SELECT id, day_range_start, day_range_end, rate_per_day, description, display_order " +
                     "FROM fine_rate_config WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, id);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                FineRateConfig config = new FineRateConfig();
                config.setId(rs.getLong("id"));
                config.setDayRangeStart(rs.getInt("day_range_start"));
                Integer dayRangeEnd = rs.getObject("day_range_end", Integer.class);
                config.setDayRangeEnd(dayRangeEnd);
                config.setRatePerDay(rs.getDouble("rate_per_day"));
                config.setDescription(rs.getString("description"));
                config.setDisplayOrder(rs.getInt("display_order"));
                return config;
            }
            return null;
        } catch (SQLException e) {
            logger.error("查找罚款梯度配置失败: id={}", id, e);
            throw new RuntimeException("查找罚款梯度配置失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 更新配置
     */
    public boolean update(FineRateConfig config) {
        String sql = "UPDATE fine_rate_config SET day_range_start = ?, day_range_end = ?, " +
                     "rate_per_day = ?, description = ?, display_order = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, config.getDayRangeStart());
            if (config.getDayRangeEnd() != null) {
                stmt.setInt(2, config.getDayRangeEnd());
            } else {
                stmt.setObject(2, null);
            }
            stmt.setDouble(3, config.getRatePerDay());
            stmt.setString(4, config.getDescription());
            stmt.setInt(5, config.getDisplayOrder());
            stmt.setLong(6, config.getId());
            
            int rows = stmt.executeUpdate();
            logger.info("更新罚款梯度配置: id={}, rows={}", config.getId(), rows);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("更新罚款梯度配置失败: id={}, error={}", config.getId(), e.getMessage(), e);
            // 检查是否是唯一性约束冲突
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("UNIQUE") || errorMsg.contains("unique") || 
                errorMsg.contains("duplicate key") || errorMsg.contains("违反唯一约束"))) {
                throw new RuntimeException("天数范围与其他配置冲突，请选择不同的范围", e);
            }
            throw new RuntimeException("更新罚款梯度配置失败: " + (errorMsg != null ? errorMsg : e.getClass().getSimpleName()), e);
        } finally {
            close(conn, stmt, null);
        }
    }
    
    /**
     * 插入新配置
     */
    public Long insert(FineRateConfig config) {
        String sql = "INSERT INTO fine_rate_config (day_range_start, day_range_end, rate_per_day, description, display_order) " +
                     "VALUES (?, ?, ?, ?, ?) RETURNING id";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, config.getDayRangeStart());
            if (config.getDayRangeEnd() != null) {
                stmt.setInt(2, config.getDayRangeEnd());
            } else {
                stmt.setObject(2, null);
            }
            stmt.setDouble(3, config.getRatePerDay());
            stmt.setString(4, config.getDescription());
            stmt.setInt(5, config.getDisplayOrder());
            
            rs = stmt.executeQuery();
            if (rs.next()) {
                Long id = rs.getLong("id");
                logger.info("插入罚款梯度配置成功: id={}", id);
                return id;
            }
            throw new RuntimeException("插入罚款梯度配置失败：未返回ID");
        } catch (SQLException e) {
            logger.error("插入罚款梯度配置失败", e);
            throw new RuntimeException("插入罚款梯度配置失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 删除配置
     */
    public boolean delete(Long id) {
        String sql = "DELETE FROM fine_rate_config WHERE id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, id);
            
            int rows = stmt.executeUpdate();
            logger.info("删除罚款梯度配置: id={}, rows={}", id, rows);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("删除罚款梯度配置失败: id={}", id, e);
            throw new RuntimeException("删除罚款梯度配置失败", e);
        } finally {
            close(conn, stmt, null);
        }
    }
}






