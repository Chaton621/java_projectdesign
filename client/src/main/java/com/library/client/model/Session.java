package com.library.client.model;

/**
 * 会话管理
 * 保存token和当前用户信息
 * 每个客户端实例拥有独立的Session
 */
public class Session {
    private String token;
    private Long userId;
    private String username;
    private String role;  // USER, ADMIN
    
    public Session() {
    }
    
    /**
     * 登录
     */
    public void login(String token, Long userId, String username, String role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.role = role;
    }
    
    /**
     * 登出
     */
    public void logout() {
        this.token = null;
        this.userId = null;
        this.username = null;
        this.role = null;
    }
    
    /**
     * 检查是否已登录
     */
    public boolean isLoggedIn() {
        return token != null && !token.isEmpty();
    }
    
    /**
     * 检查是否是管理员
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
    
    public String getToken() {
        return token;
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
}












