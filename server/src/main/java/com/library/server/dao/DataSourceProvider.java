package com.library.server.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.util.Properties;

/**
 * 数据源提供者
 * 使用HikariCP连接池管理PostgreSQL连接
 */
public class DataSourceProvider {
    private static final Logger logger = LoggerFactory.getLogger(DataSourceProvider.class);
    private static volatile HikariDataSource dataSource;
    
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/library_db";
    private static final String DEFAULT_USERNAME = "postgres";
    private static final String DEFAULT_PASSWORD = "postgres";
    private static final int DEFAULT_MIN_POOL_SIZE = 5;
    private static final int DEFAULT_MAX_POOL_SIZE = 20;
    private static final long DEFAULT_CONNECTION_TIMEOUT = 30000;
    private static final long DEFAULT_IDLE_TIMEOUT = 600000;
    private static final long DEFAULT_MAX_LIFETIME = 1800000;
    
    /**
     * 获取数据源（单例模式）
     */
    public static DataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DataSourceProvider.class) {
                if (dataSource == null) {
                    dataSource = createDataSource();
                }
            }
        }
        return dataSource;
    }
    
    /**
     * 创建数据源
     */
    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        Properties props = loadProperties();
        
        String url = props.getProperty("db.url", DEFAULT_URL);
        String username = props.getProperty("db.username", DEFAULT_USERNAME);
        String password = props.getProperty("db.password", DEFAULT_PASSWORD);
        
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMinimumIdle(Integer.parseInt(props.getProperty("db.pool.size.minimum", 
                String.valueOf(DEFAULT_MIN_POOL_SIZE))));
        config.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.size.maximum", 
                String.valueOf(DEFAULT_MAX_POOL_SIZE))));
        config.setConnectionTimeout(Long.parseLong(props.getProperty("db.pool.connection.timeout", 
                String.valueOf(DEFAULT_CONNECTION_TIMEOUT))));
        config.setIdleTimeout(Long.parseLong(props.getProperty("db.pool.idle.timeout", 
                String.valueOf(DEFAULT_IDLE_TIMEOUT))));
        config.setMaxLifetime(Long.parseLong(props.getProperty("db.pool.max.lifetime", 
                String.valueOf(DEFAULT_MAX_LIFETIME))));
        config.setPoolName("LibraryHikariPool");
        config.setConnectionTestQuery("SELECT 1");
        
        logger.info("初始化HikariCP连接池: url={}, username={}, poolSize=[{}, {}]", 
                url, username, config.getMinimumIdle(), config.getMaximumPoolSize());
        
        return new HikariDataSource(config);
    }
    
    /**
     * 加载配置文件
     */
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = DataSourceProvider.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
                logger.info("成功加载application.properties配置文件");
            } else {
                logger.warn("未找到application.properties，使用默认配置");
            }
        } catch (Exception e) {
            logger.warn("加载application.properties失败，使用默认配置", e);
        }
        return props;
    }
    
    /**
     * 关闭数据源（优雅关闭）
     */
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("HikariCP连接池已关闭");
        }
    }
}














