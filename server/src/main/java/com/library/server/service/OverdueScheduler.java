package com.library.server.service;

import com.library.server.dao.BorrowRecordDao;
import com.library.server.dao.DataSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 逾期扫描调度器
 * 每天扫描未归还且due_time < now的记录，标记为OVERDUE
 */
public class OverdueScheduler {
    private static final Logger logger = LoggerFactory.getLogger(OverdueScheduler.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "OverdueScheduler");
                t.setDaemon(true);
                return t;
            }
    );
    
    private final BorrowRecordDao recordDao = new BorrowRecordDao();
    private volatile boolean running = false;
    
    /**
     * 启动调度器（每天凌晨2点执行）
     */
    public void start() {
        if (running) {
            logger.warn("逾期扫描调度器已在运行");
            return;
        }
        
        running = true;
        // 计算到下一个凌晨2点的延迟
        long initialDelay = calculateDelayToNext2AM();
        
        // 每天执行一次
        scheduler.scheduleAtFixedRate(this::scanOverdueRecords, 
                initialDelay, 24, TimeUnit.HOURS);
        
        logger.info("逾期扫描调度器已启动，将在每天凌晨2点执行");
    }
    
    /**
     * 停止调度器
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("逾期扫描调度器已停止");
    }
    
    /**
     * 扫描逾期记录
     */
    private void scanOverdueRecords() {
        logger.info("开始扫描逾期记录...");
        
        Connection conn = null;
        PreparedStatement updateStmt = null;
        PreparedStatement countStmt = null;
        ResultSet rs = null;
        
        try {
            conn = DataSourceProvider.getDataSource().getConnection();
            conn.setAutoCommit(false);
            countStmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM borrow_records " +
                "WHERE status = 'BORROWED' " +
                "AND due_time < CURRENT_TIMESTAMP " +
                "AND return_time IS NULL"
            );
            rs = countStmt.executeQuery();
            int count = 0;
            if (rs.next()) {
                count = rs.getInt(1);
            }
            rs.close();
            countStmt.close();
            
            if (count == 0) {
                logger.info("没有逾期记录需要更新");
                return;
            }
            
            // 更新逾期记录
            updateStmt = conn.prepareStatement(
                "UPDATE borrow_records " +
                "SET status = 'OVERDUE' " +
                "WHERE status = 'BORROWED' " +
                "AND due_time < CURRENT_TIMESTAMP " +
                "AND return_time IS NULL"
            );
            
            int updated = updateStmt.executeUpdate();
            conn.commit();
            
            logger.info("逾期扫描完成: 更新了{}条记录", updated);
            
            // 记录详细信息（可选）
            if (updated > 0) {
                logOverdueDetails(conn);
            }
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("回滚事务失败", ex);
                }
            }
            logger.error("扫描逾期记录失败", e);
        } catch (Exception e) {
            logger.error("扫描逾期记录异常", e);
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (SQLException e) {}
            }
            if (updateStmt != null) {
                try { updateStmt.close(); } catch (SQLException e) {}
            }
            if (countStmt != null) {
                try { countStmt.close(); } catch (SQLException e) {}
            }
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("关闭连接失败", e);
                }
            }
        }
    }
    
    /**
     * 记录逾期详情
     */
    private void logOverdueDetails(Connection conn) {
        String sql = "SELECT br.id, br.user_id, br.book_id, br.due_time, " +
                     "EXTRACT(DAY FROM (CURRENT_TIMESTAMP - br.due_time)) as overdue_days " +
                     "FROM borrow_records br " +
                     "WHERE br.status = 'OVERDUE' " +
                     "AND br.return_time IS NULL " +
                     "ORDER BY overdue_days DESC " +
                     "LIMIT 10";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            logger.info("逾期记录详情（Top 10）:");
            while (rs.next()) {
                logger.info("  记录ID: {}, 用户ID: {}, 图书ID: {}, 逾期天数: {}", 
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getLong("book_id"),
                        rs.getInt("overdue_days"));
            }
        } catch (SQLException e) {
            logger.warn("记录逾期详情失败", e);
        }
    }
    
    /**
     * 计算到下一个凌晨2点的延迟（秒）
     */
    private long calculateDelayToNext2AM() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime next2AM = now.withHour(2).withMinute(0).withSecond(0).withNano(0);
        
        if (now.isAfter(next2AM)) {
            next2AM = next2AM.plusDays(1);
        }
        
        return java.time.Duration.between(now, next2AM).getSeconds();
    }
}

