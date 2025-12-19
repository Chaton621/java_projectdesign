package com.library.server.model;

import java.time.LocalDateTime;

/**
 * 借阅记录实体类
 */
public class BorrowRecord {
    private Long id;
    private Long userId;
    private Long bookId;
    private LocalDateTime borrowTime;
    private LocalDateTime dueTime;
    private LocalDateTime returnTime;
    private String status;  // BORROWED, RETURNED, OVERDUE
    private Double fineAmount;  // 该记录的罚款金额
    private LocalDateTime createdAt;
    
    public BorrowRecord() {
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Long getBookId() {
        return bookId;
    }
    
    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }
    
    public LocalDateTime getBorrowTime() {
        return borrowTime;
    }
    
    public void setBorrowTime(LocalDateTime borrowTime) {
        this.borrowTime = borrowTime;
    }
    
    public LocalDateTime getDueTime() {
        return dueTime;
    }
    
    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }
    
    public LocalDateTime getReturnTime() {
        return returnTime;
    }
    
    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public boolean isOverdue() {
        return "OVERDUE".equals(status) || 
               ("BORROWED".equals(status) && dueTime != null && dueTime.isBefore(LocalDateTime.now()));
    }
    
    public Double getFineAmount() {
        return fineAmount != null ? fineAmount : 0.0;
    }
    
    public void setFineAmount(Double fineAmount) {
        this.fineAmount = fineAmount;
    }
}












