package com.library.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 生成用户密码哈希工具
 * 用于生成reset_and_generate_users.sql中需要的密码哈希
 */
public class GenerateUserPasswords {
    private static final Logger logger = LoggerFactory.getLogger(GenerateUserPasswords.class);
    
    public static void main(String[] args) {
        logger.info("生成用户密码哈希...");
        logger.info("密码: 12345");
        logger.info("");
        
        // 生成密码哈希
        String passwordHash = PasswordUtil.hashPassword("12345");
        
        logger.info("========================================");
        logger.info("密码哈希（密码: 12345）:");
        logger.info("{}", passwordHash);
        logger.info("========================================");
        logger.info("");
        logger.info("请将以下哈希值更新到 database/reset_and_generate_users.sql 中：");
        logger.info("替换 v_password_hash 变量的值");
        logger.info("");
        logger.info("或者直接使用以下SQL更新所有新用户的密码哈希：");
        logger.info("UPDATE users SET password_hash = '{}' WHERE username LIKE 'user%';", passwordHash);
    }
}
