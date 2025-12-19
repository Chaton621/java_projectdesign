package com.library.server.service;

import com.library.server.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Token服务
 * 简单实现：UUID + 内存会话表 + 过期时间
 */
public class TokenService {
    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);
    
    // Token过期时间（小时）
    private static final long TOKEN_EXPIRE_HOURS = 24;
    
    // 会话存储：token -> SessionInfo
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    
    // 定时清理过期token
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "TokenCleanup");
            t.setDaemon(true);
            return t;
        }
    );
    
    public TokenService() {
        // 每小时清理一次过期token
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredTokens, 1, 1, TimeUnit.HOURS);
        logger.info("TokenService初始化完成，token过期时间: {}小时", TOKEN_EXPIRE_HOURS);
    }
    
    /**
     * 生成Token
     */
    public String generateToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expireTime = LocalDateTime.now().plusHours(TOKEN_EXPIRE_HOURS);
        
        SessionInfo session = new SessionInfo(user.getId(), user.getUsername(), 
                user.getRole(), expireTime);
        sessions.put(token, session);
        
        logger.info("生成Token: userId={}, username={}, expireTime={}", 
                user.getId(), user.getUsername(), expireTime);
        return token;
    }
    
    /**
     * 验证Token并返回用户ID
     */
    public Long validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        
        SessionInfo session = sessions.get(token);
        if (session == null) {
            logger.debug("Token不存在: {}", token);
            return null;
        }
        
        if (session.isExpired()) {
            sessions.remove(token);
            logger.debug("Token已过期: {}", token);
            return null;
        }
        
        // 更新最后访问时间
        session.updateLastAccess();
        return session.getUserId();
    }
    
    /**
     * 获取会话信息
     */
    public SessionInfo getSession(String token) {
        SessionInfo session = sessions.get(token);
        if (session != null && !session.isExpired()) {
            session.updateLastAccess();
            return session;
        }
        if (session != null) {
            sessions.remove(token);
        }
        return null;
    }
    
    /**
     * 使Token失效
     */
    public void invalidateToken(String token) {
        SessionInfo removed = sessions.remove(token);
        if (removed != null) {
            logger.info("Token已失效: userId={}", removed.getUserId());
        }
    }
    
    /**
     * 检查Token是否为管理员
     */
    public boolean isAdmin(String token) {
        SessionInfo session = getSession(token);
        return session != null && "ADMIN".equals(session.getRole());
    }
    
    /**
     * 清理过期token
     */
    private void cleanupExpiredTokens() {
        int beforeSize = sessions.size();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int afterSize = sessions.size();
        int removed = beforeSize - afterSize;
        if (removed > 0) {
            logger.debug("清理过期token: {}个", removed);
        }
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        sessions.clear();
        logger.info("TokenService已关闭");
    }
    
    /**
     * 会话信息
     */
    public static class SessionInfo {
        private final Long userId;
        private final String username;
        private final String role;
        private final LocalDateTime expireTime;
        private LocalDateTime lastAccess;
        
        public SessionInfo(Long userId, String username, String role, LocalDateTime expireTime) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.expireTime = expireTime;
            this.lastAccess = LocalDateTime.now();
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expireTime);
        }
        
        public void updateLastAccess() {
            this.lastAccess = LocalDateTime.now();
        }
        
        public Long getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getRole() {
            return role;
        }
        
        public LocalDateTime getExpireTime() {
            return expireTime;
        }
        
        public LocalDateTime getLastAccess() {
            return lastAccess;
        }
    }
}
