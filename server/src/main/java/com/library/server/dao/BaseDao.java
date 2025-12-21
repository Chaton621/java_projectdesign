package com.library.server.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 基础DAO类
 * 提供通用的数据库操作方法
 */
public abstract class BaseDao {
    protected static final Logger logger = LoggerFactory.getLogger(BaseDao.class);
    protected final DataSource dataSource;
    
    protected BaseDao() {
        this.dataSource = DataSourceProvider.getDataSource();
    }
    
    /**
     * 获取数据库连接
     */
    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    /**
     * 关闭资源（Connection）
     */
    protected void close(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error("关闭Connection失败", e);
            }
        }
    }
    
    /**
     * 关闭资源（PreparedStatement）
     */
    protected void close(PreparedStatement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                logger.error("关闭PreparedStatement失败", e);
            }
        }
    }
    
    /**
     * 关闭资源（ResultSet）
     */
    protected void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                logger.error("关闭ResultSet失败", e);
            }
        }
    }
    
    /**
     * 关闭所有资源
     */
    protected void close(Connection conn, PreparedStatement stmt, ResultSet rs) {
        close(rs);
        close(stmt);
        close(conn);
    }
    
    /**
     * 关闭Connection和PreparedStatement
     */
    protected void close(Connection conn, PreparedStatement stmt) {
        close(stmt);
        close(conn);
    }
    
    /**
     * 回滚事务
     */
    protected void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                logger.error("回滚事务失败", e);
            }
        }
    }
}















