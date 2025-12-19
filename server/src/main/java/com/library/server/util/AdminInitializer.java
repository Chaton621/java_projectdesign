package com.library.server.util;

import com.library.server.dao.UserDao;
import com.library.server.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 管理员初始化工具
 * 首次启动时插入默认管理员账户
 */
public class AdminInitializer {
    private static final Logger logger = LoggerFactory.getLogger(AdminInitializer.class);
    
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    
    /**
     * 初始化管理员账户
     * 如果admin用户不存在，则创建
     */
    public static void initializeAdmin() {
        UserDao userDao = new UserDao();
        
        try {
            // 检查admin用户是否已存在
            User existingAdmin = userDao.findByUsername(DEFAULT_ADMIN_USERNAME);
            if (existingAdmin != null) {
                logger.info("管理员账户已存在: {}", DEFAULT_ADMIN_USERNAME);
                return;
            }
            
            // 创建管理员账户
            String passwordHash = PasswordUtil.hashPassword(DEFAULT_ADMIN_PASSWORD);
            User admin = new User(DEFAULT_ADMIN_USERNAME, passwordHash, "ADMIN", "ACTIVE");
            Long adminId = userDao.insertUser(admin);
            
            logger.info("管理员账户创建成功: id={}, username={}, password={}", 
                    adminId, DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
            logger.warn("请尽快修改默认管理员密码！");
            
        } catch (Exception e) {
            logger.error("初始化管理员账户失败", e);
        }
    }
    
    /**
     * 通过SQL初始化管理员（备用方案）
     * 可以在数据库初始化脚本中直接执行
     */
    public static String getAdminInitSQL() {
        String passwordHash = PasswordUtil.hashPassword(DEFAULT_ADMIN_PASSWORD);
        return String.format(
            "INSERT INTO users (username, password_hash, role, status, created_at) " +
            "VALUES ('%s', '%s', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP) " +
            "ON CONFLICT (username) DO NOTHING;",
            DEFAULT_ADMIN_USERNAME, passwordHash
        );
    }
    
    /**
     * 初始化方法（供ServerMain调用）
     */
    public static void initialize() {
        initializeAdmin();
    }
}








