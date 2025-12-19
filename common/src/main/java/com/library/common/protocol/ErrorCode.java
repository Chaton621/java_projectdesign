package com.library.common.protocol;

/**
 * 错误码枚举
 * 定义系统所有错误类型
 */
public enum ErrorCode {
    // 认证相关
    AUTH_FAILED(401, "认证失败"),
    FORBIDDEN(403, "权限不足"),
    TOKEN_EXPIRED(401, "Token已过期"),
    TOKEN_INVALID(401, "Token无效"),
    
    // 资源相关
    NOT_FOUND(404, "资源不存在"),
    ALREADY_EXISTS(409, "资源已存在"),
    
    // 业务逻辑
    NO_STOCK(400, "库存不足"),
    BOOK_ALREADY_BORROWED(400, "图书已被借出"),
    BOOK_NOT_BORROWED(400, "该图书未被借出"),
    USER_FROZEN(403, "用户已被冻结"),
    OVERDUE_BOOK(400, "存在逾期图书，无法继续借阅"),
    
    // 数据验证
    VALIDATION_ERROR(400, "数据验证失败"),
    INVALID_PARAMETER(400, "参数无效"),
    
    // 系统错误
    SERVER_ERROR(500, "服务器内部错误"),
    DATABASE_ERROR(500, "数据库错误"),
    NETWORK_ERROR(500, "网络错误"),
    NOT_IMPLEMENTED(501, "功能未实现"),
    
    // 未知错误
    UNKNOWN_ERROR(500, "未知错误");
    
    private final int httpStatus;
    private final String message;
    
    ErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
    
    public String getMessage() {
        return message;
    }
}

