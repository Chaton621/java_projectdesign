package com.library.server.util;

import com.library.server.dao.FineRateConfigDao;
import com.library.server.dao.DataSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;

/**
 * 欠费价格梯度配置初始化器
 * 在服务器启动时自动初始化默认的欠费价格梯度配置
 */
public class FineRateConfigInitializer {
    private static final Logger logger = LoggerFactory.getLogger(FineRateConfigInitializer.class);
    
    /**
     * 初始化默认的欠费价格梯度配置
     */
    public static void initialize() {
        try {
            // 首先确保表存在
            ensureTableExists();
            
            FineRateConfigDao configDao = new FineRateConfigDao();
            
            // 检查是否已有配置
            List<FineRateConfigDao.FineRateConfig> existingConfigs = configDao.findAll();
            
            if (!existingConfigs.isEmpty()) {
                logger.info("欠费价格梯度配置已存在，跳过初始化。当前配置数量: {}", existingConfigs.size());
                // 打印现有配置信息
                for (FineRateConfigDao.FineRateConfig config : existingConfigs) {
                    logger.info("  梯度{}: 第{}-{}天, 每天{}元 - {}", 
                            config.getDisplayOrder(),
                            config.getDayRangeStart(),
                            config.getDayRangeEnd() != null ? config.getDayRangeEnd() : "无上限",
                            config.getRatePerDay(),
                            config.getDescription());
                }
                return;
            }
            
            // 创建默认配置
            logger.info("开始初始化欠费价格梯度配置...");
            
            // 梯度1: 前7天，每天0.1元
            FineRateConfigDao.FineRateConfig config1 = new FineRateConfigDao.FineRateConfig();
            config1.setDayRangeStart(1);
            config1.setDayRangeEnd(7);
            config1.setRatePerDay(0.1);
            config1.setDescription("前7天每天0.1元");
            config1.setDisplayOrder(1);
            Long id1 = configDao.insert(config1);
            logger.info("创建梯度配置1: id={}, 第1-7天, 每天{}元", id1, config1.getRatePerDay());
            
            // 梯度2: 第8-30天，每天0.2元
            FineRateConfigDao.FineRateConfig config2 = new FineRateConfigDao.FineRateConfig();
            config2.setDayRangeStart(8);
            config2.setDayRangeEnd(30);
            config2.setRatePerDay(0.2);
            config2.setDescription("第8-30天每天0.2元");
            config2.setDisplayOrder(2);
            Long id2 = configDao.insert(config2);
            logger.info("创建梯度配置2: id={}, 第8-30天, 每天{}元", id2, config2.getRatePerDay());
            
            // 梯度3: 31天以上，每天0.3元
            FineRateConfigDao.FineRateConfig config3 = new FineRateConfigDao.FineRateConfig();
            config3.setDayRangeStart(31);
            config3.setDayRangeEnd(null); // NULL 表示无上限
            config3.setRatePerDay(0.3);
            config3.setDescription("31天以上每天0.3元");
            config3.setDisplayOrder(3);
            Long id3 = configDao.insert(config3);
            logger.info("创建梯度配置3: id={}, 第31天以上, 每天{}元", id3, config3.getRatePerDay());
            
            logger.info("欠费价格梯度配置初始化完成！共创建{}个梯度配置", 3);
            
        } catch (Exception e) {
            logger.error("初始化欠费价格梯度配置失败", e);
            // 不抛出异常，避免影响服务器启动
        }
    }
    
    /**
     * 确保 fine_rate_config 表存在
     */
    private static void ensureTableExists() {
        Connection conn = null;
        try {
            DataSource dataSource = DataSourceProvider.getDataSource();
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "fine_rate_config", null);
            
            if (!tables.next()) {
                // 表不存在，创建表
                logger.info("fine_rate_config 表不存在，正在创建...");
                createTable(conn);
                logger.info("fine_rate_config 表创建成功");
            } else {
                logger.debug("fine_rate_config 表已存在");
            }
        } catch (Exception e) {
            logger.error("检查或创建 fine_rate_config 表失败", e);
            throw new RuntimeException("初始化数据库表失败", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception e) {
                    logger.warn("关闭连接失败", e);
                }
            }
        }
    }
    
    /**
     * 创建 fine_rate_config 表
     */
    private static void createTable(Connection conn) throws Exception {
        String createTableSql = 
            "CREATE TABLE IF NOT EXISTS fine_rate_config (" +
            "    id BIGSERIAL PRIMARY KEY," +
            "    day_range_start INTEGER NOT NULL," +
            "    day_range_end INTEGER," +
            "    rate_per_day DOUBLE PRECISION NOT NULL CHECK (rate_per_day >= 0)," +
            "    description VARCHAR(200)," +
            "    display_order INTEGER NOT NULL DEFAULT 0," +
            "    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "    UNIQUE(day_range_start, day_range_end)" +
            ")";
        
        String createIndexSql = 
            "CREATE INDEX IF NOT EXISTS idx_fine_rate_config_order ON fine_rate_config(display_order)";
        
        try (var stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(createIndexSql);
        }
    }
    
    /**
     * 重置为默认配置（删除所有现有配置并重新创建）
     * 注意：此方法会删除所有现有配置，请谨慎使用
     */
    public static void resetToDefaults() {
        FineRateConfigDao configDao = new FineRateConfigDao();
        
        try {
            logger.warn("开始重置欠费价格梯度配置为默认值...");
            
            // 获取所有现有配置并删除
            List<FineRateConfigDao.FineRateConfig> existingConfigs = configDao.findAll();
            for (FineRateConfigDao.FineRateConfig config : existingConfigs) {
                configDao.delete(config.getId());
                logger.info("删除现有配置: id={}", config.getId());
            }
            
            // 重新初始化
            initialize();
            
            logger.info("欠费价格梯度配置已重置为默认值");
            
        } catch (Exception e) {
            logger.error("重置欠费价格梯度配置失败", e);
            throw new RuntimeException("重置欠费价格梯度配置失败", e);
        }
    }
}





