package com.library.server.model;

/**
 * 普通用户类
 * User类的子类，代表普通用户角色
 */
public class NormalUser extends User {
    
    public NormalUser() {
        super();
        this.setRole("USER");
    }
    
    public NormalUser(String username, String passwordHash, String status) {
        super(username, passwordHash, "USER", status);
    }
    
    /**
     * 从User对象创建NormalUser
     */
    public static NormalUser fromUser(User user) {
        if (user == null) {
            return null;
        }
        NormalUser normalUser = new NormalUser();
        normalUser.setId(user.getId());
        normalUser.setUsername(user.getUsername());
        normalUser.setPasswordHash(user.getPasswordHash());
        normalUser.setRole("USER");
        normalUser.setStatus(user.getStatus());
        normalUser.setCreatedAt(user.getCreatedAt());
        return normalUser;
    }
    
    /**
     * 检查是否是普通用户
     */
    @Override
    public boolean isAdmin() {
        return false;
    }
}










