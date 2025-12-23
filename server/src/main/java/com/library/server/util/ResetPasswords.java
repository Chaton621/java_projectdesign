package com.library.server.util;

import com.library.server.dao.UserDao;
import com.library.server.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

/**
 * 重置密码工具
 * 用于重置管理员和演示用户的密码
 */
public class ResetPasswords {
    private static final Logger logger = LoggerFactory.getLogger(ResetPasswords.class);
    
    public static void main(String[] args) {
        logger.info("开始重置密码...");
        
        UserDao userDao = new UserDao();
        
        try {
            // 重置管理员密码
            User admin = userDao.findByUsername("admin");
            if (admin != null) {
                String adminPasswordHash = PasswordUtil.hashPassword("admin123");
                try (Connection conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection()) {
                    java.sql.PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE users SET password_hash = ? WHERE username = 'admin'"
                    );
                    stmt.setString(1, adminPasswordHash);
                    int rows = stmt.executeUpdate();
                    if (rows > 0) {
                        logger.info("管理员密码已重置: username=admin, password=admin123");
                    } else {
                        logger.warn("管理员密码重置失败");
                    }
                }
            } else {
                logger.warn("管理员账户不存在");
            }
            
            // 重置或创建huang用户
            User huang = userDao.findByUsername("huang");
            String huangPasswordHash = PasswordUtil.hashPassword("12345");
            if (huang != null) {
                try (Connection conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection()) {
                    java.sql.PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE users SET password_hash = ? WHERE username = 'huang'"
                    );
                    stmt.setString(1, huangPasswordHash);
                    int rows = stmt.executeUpdate();
                    if (rows > 0) {
                        logger.info("huang用户密码已重置: username=huang, password=12345");
                    }
                }
            } else {
                // 创建huang用户
                User newHuang = new User("huang", huangPasswordHash, "USER", "ACTIVE");
                Long huangId = userDao.insertUser(newHuang);
                logger.info("创建huang用户成功: id={}, username=huang, password=12345", huangId);
            }
            
            // 重置或创建wen用户
            User wen = userDao.findByUsername("wen");
            String wenPasswordHash = PasswordUtil.hashPassword("12345");
            if (wen != null) {
                try (Connection conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection()) {
                    java.sql.PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE users SET password_hash = ? WHERE username = 'wen'"
                    );
                    stmt.setString(1, wenPasswordHash);
                    int rows = stmt.executeUpdate();
                    if (rows > 0) {
                        logger.info("wen用户密码已重置: username=wen, password=12345");
                    }
                }
            } else {
                // 创建wen用户
                User newWen = new User("wen", wenPasswordHash, "USER", "ACTIVE");
                Long wenId = userDao.insertUser(newWen);
                logger.info("创建wen用户成功: id={}, username=wen, password=12345", wenId);
            }
            
            logger.info("");
            logger.info("密码重置完成！");
            logger.info("管理员: username=admin, password=admin123");
            logger.info("演示用户1: username=huang, password=12345");
            logger.info("演示用户2: username=wen, password=12345");
            
        } catch (Exception e) {
            logger.error("重置密码失败", e);
        }
    }
}









