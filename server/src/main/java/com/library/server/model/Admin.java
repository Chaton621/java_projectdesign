package com.library.server.model;

/**
 * 管理员类
 * User类的子类，代表管理员角色
 */
public class Admin extends User {
    
    public Admin() {
        super();
        this.setRole("ADMIN");
    }
    
    public Admin(String username, String passwordHash, String status) {
        super(username, passwordHash, "ADMIN", status);
    }
    
    /**
     * 从User对象创建Admin
     */
    public static Admin fromUser(User user) {
        if (user == null) {
            return null;
        }
        Admin admin = new Admin();
        admin.setId(user.getId());
        admin.setUsername(user.getUsername());
        admin.setPasswordHash(user.getPasswordHash());
        admin.setRole("ADMIN");
        admin.setStatus(user.getStatus());
        admin.setCreatedAt(user.getCreatedAt());
        return admin;
    }
    
    /**
     * 检查是否是管理员
     */
    @Override
    public boolean isAdmin() {
        return true;
    }
}










