package com.library.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * 密码工具类
 * 使用PBKDF2算法进行密码哈希（Java标准库，无需额外依赖）
 */
public class PasswordUtil {
    private static final Logger logger = LoggerFactory.getLogger(PasswordUtil.class);
    
    // PBKDF2参数
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100000;  // 迭代次数
    private static final int KEY_LENGTH = 256;      // 密钥长度（位）
    private static final int SALT_LENGTH = 16;      // 盐长度（字节）
    
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * 生成密码哈希
     * 返回格式：iterations:salt:hash（Base64编码）
     */
    public static String hashPassword(String password) {
        try {
            // 生成随机盐
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            
            // 生成哈希
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            
            // 编码为Base64字符串
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(hash);
            
            // 返回格式：iterations:salt:hash
            return ITERATIONS + ":" + saltBase64 + ":" + hashBase64;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("密码哈希失败", e);
            throw new RuntimeException("密码哈希失败", e);
        }
    }
    
    /**
     * 验证密码
     */
    public static boolean verifyPassword(String password, String storedHash) {
        try {
            // 解析存储的哈希
            String[] parts = storedHash.split(":");
            if (parts.length != 3) {
                logger.warn("密码哈希格式无效");
                return false;
            }
            
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] storedHashBytes = Base64.getDecoder().decode(parts[2]);
            
            // 使用相同的参数计算密码哈希
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] computedHash = factory.generateSecret(spec).getEncoded();
            
            // 比较哈希值（使用常量时间比较，防止时序攻击）
            return constantTimeEquals(storedHashBytes, computedHash);
        } catch (Exception e) {
            logger.error("密码验证失败", e);
            return false;
        }
    }
    
    /**
     * 常量时间比较两个字节数组（防止时序攻击）
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
