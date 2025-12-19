package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.BookDao;
import com.library.server.dao.BorrowRecordDao;
import com.library.server.dao.UserDao;
import com.library.server.model.Book;
import com.library.server.model.BorrowRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import com.library.server.service.FineService;
import java.util.List;

/**
 * 借阅服务
 */
public class BorrowService {
    private static final Logger logger = LoggerFactory.getLogger(BorrowService.class);
    private final BorrowRecordDao recordDao = new BorrowRecordDao();
    private final BookDao bookDao = new BookDao();
    private final UserDao userDao = new UserDao();
    
    /**
     * 借书
     * 借书设置 due_time = borrow_time + 30天
     */
    public Response borrowBook(Request request, Long userId) {
        String requestId = request.getRequestId();
        Connection conn = null;
        
        try {
            com.library.server.model.User user = userDao.findById(userId);
            if (user != null && user.isAdmin()) {
                return Response.error(requestId, ErrorCode.FORBIDDEN, 
                        JsonUtil.toJsonNode("管理员账户不能借书"));
            }
            
            Long bookId = request.getPayloadLong("bookId");
            if (bookId == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                        JsonUtil.toJsonNode("图书ID不能为空"));
            }
            
            if (recordDao.hasOverdueBooks(userId)) {
                return Response.error(requestId, ErrorCode.OVERDUE_BOOK, 
                        JsonUtil.toJsonNode("存在逾期图书，无法继续借阅"));
            }
            
            Book book = bookDao.findById(bookId);
            if (book == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("图书不存在"));
            }
            
            conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection();
            conn.setAutoCommit(false);
            
            boolean stockUpdated = bookDao.updateBookStock(conn, bookId, -1);
            if (!stockUpdated) {
                conn.rollback();
                return Response.error(requestId, ErrorCode.NO_STOCK, 
                        JsonUtil.toJsonNode("库存不足"));
            }
            
            BorrowRecord record = new BorrowRecord();
            record.setUserId(userId);
            record.setBookId(bookId);
            record.setBorrowTime(LocalDateTime.now());
            record.setDueTime(LocalDateTime.now().plusDays(30));
            record.setStatus("BORROWED");
            record.setCreatedAt(LocalDateTime.now());
            
            Long recordId = recordDao.insertBorrowRecord(conn, record);
            
            conn.commit();
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("recordId", recordId);
            data.put("bookId", bookId);
            data.put("bookTitle", book.getTitle());
            data.put("borrowTime", record.getBorrowTime().toString());
            data.put("dueTime", record.getDueTime().toString());
            
            logger.info("借书成功: userId={}, bookId={}, recordId={}", userId, bookId, recordId);
            return Response.success(requestId, "借书成功", JsonUtil.toJsonNode(data));
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("回滚事务失败", ex);
                }
            }
            logger.error("借书失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("回滚事务失败", ex);
                }
            }
            logger.error("借书失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("关闭连接失败", e);
                }
            }
        }
    }
    
    /**
     * 还书
     * 返回时若逾期则在响应里返回 overdueDays
     */
    public Response returnBook(Request request, Long userId) {
        String requestId = request.getRequestId();
        Connection conn = null;
        
        try {
            Long recordId = request.getPayloadLong("recordId");
            if (recordId == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                        JsonUtil.toJsonNode("借阅记录ID不能为空"));
            }
            
            // 查找借阅记录
            BorrowRecord record = recordDao.findById(recordId);
            if (record == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                        JsonUtil.toJsonNode("借阅记录不存在"));
            }
            
            // 检查是否是该用户的记录
            if (!record.getUserId().equals(userId)) {
                return Response.error(requestId, ErrorCode.FORBIDDEN, 
                        JsonUtil.toJsonNode("无权操作此借阅记录"));
            }
            
            // 检查是否已归还
            if ("RETURNED".equals(record.getStatus())) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                        JsonUtil.toJsonNode("该图书已经归还"));
            }
            
            long overdueDays = FineService.calculateOverdueDays(record.getDueTime());
            double fineAmount = FineService.calculateFine(overdueDays);
            
            conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection();
            conn.setAutoCommit(false);
            
            recordDao.markReturned(conn, recordId, fineAmount);
            
            if (fineAmount > 0) {
                UserDao userDao = new UserDao();
                userDao.addFineAmount(conn, userId, fineAmount);
            }
            
            bookDao.updateBookStock(conn, record.getBookId(), 1);
            
            conn.commit();
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("recordId", recordId);
            data.put("bookId", record.getBookId());
            data.put("returnTime", LocalDateTime.now().toString());
            data.put("overdueDays", overdueDays);
            data.put("fineAmount", fineAmount);
            
            if (overdueDays > 0) {
                data.put("message", String.format("归还成功，逾期%d天，需缴纳罚款%.2f元", overdueDays, fineAmount));
            } else {
                data.put("message", "归还成功");
            }
            
            logger.info("还书成功: userId={}, recordId={}, overdueDays={}", 
                    userId, recordId, overdueDays);
            return Response.success(requestId, "还书成功", JsonUtil.toJsonNode(data));
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("回滚事务失败", ex);
                }
            }
            logger.error("还书失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("回滚事务失败", ex);
                }
            }
            logger.error("还书失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("关闭连接失败", e);
                }
            }
        }
    }
    
    /**
     * 我的借阅记录
     */
    public Response getMyRecords(Request request, Long userId) {
        String requestId = request.getRequestId();
        
        try {
            String status = request.getPayloadString("status");  // 可选：BORROWED, RETURNED, OVERDUE
            
            List<BorrowRecord> records;
            if (status != null && !status.isEmpty()) {
                records = recordDao.findByUserIdAndStatus(userId, status);
            } else {
                records = recordDao.findByUserId(userId);
            }
            
            ArrayNode recordArray = JsonUtil.getObjectMapper().createArrayNode();
            for (BorrowRecord record : records) {
                ObjectNode recordNode = JsonUtil.createObjectNode();
                recordNode.put("id", record.getId());
                recordNode.put("bookId", record.getBookId());
                recordNode.put("borrowTime", record.getBorrowTime().toString());
                recordNode.put("dueTime", record.getDueTime().toString());
                if (record.getReturnTime() != null) {
                    recordNode.put("returnTime", record.getReturnTime().toString());
                }
                recordNode.put("status", record.getStatus());
                recordNode.put("fineAmount", record.getFineAmount());
                
                if (record.getStatus().equals("BORROWED") || record.getStatus().equals("OVERDUE")) {
                    long overdueDays = FineService.calculateOverdueDays(record.getDueTime());
                    recordNode.put("overdueDays", overdueDays);
                    if (overdueDays > 0) {
                        double currentFine = FineService.calculateFine(overdueDays);
                        recordNode.put("currentFine", currentFine);
                    }
                }
                
                Book book = bookDao.findById(record.getBookId());
                if (book != null) {
                    recordNode.put("bookTitle", book.getTitle());
                    recordNode.put("bookAuthor", book.getAuthor());
                }
                
                recordArray.add(recordNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("records", recordArray);
            data.put("total", records.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("查询借阅记录失败: userId={}", userId, e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 查看所有借阅记录（管理员操作）
     */
    public Response getAllRecords(Request request) {
        String requestId = request.getRequestId();
        
        try {
            String status = request.getPayloadString("status");  // 可选：BORROWED, RETURNED, OVERDUE
            Integer limit = request.getPayloadInt("limit");
            Integer offset = request.getPayloadInt("offset");
            
            if (limit == null || limit <= 0) limit = 100;
            if (offset == null || offset < 0) offset = 0;
            
            List<BorrowRecord> records;
            if (status != null && !status.isEmpty()) {
                records = recordDao.findAllByStatus(status, limit, offset);
            } else {
                records = recordDao.findAll(limit, offset);
            }
            
            ArrayNode recordArray = JsonUtil.getObjectMapper().createArrayNode();
            for (BorrowRecord record : records) {
                ObjectNode recordNode = JsonUtil.createObjectNode();
                recordNode.put("id", record.getId());
                recordNode.put("userId", record.getUserId());
                recordNode.put("bookId", record.getBookId());
                recordNode.put("borrowTime", record.getBorrowTime().toString());
                recordNode.put("dueTime", record.getDueTime().toString());
                if (record.getReturnTime() != null) {
                    recordNode.put("returnTime", record.getReturnTime().toString());
                }
                recordNode.put("status", record.getStatus());
                
                // 获取图书信息
                Book book = bookDao.findById(record.getBookId());
                if (book != null) {
                    recordNode.put("bookTitle", book.getTitle());
                    recordNode.put("bookAuthor", book.getAuthor());
                }
                
                // 获取用户信息
                com.library.server.model.User user = userDao.findById(record.getUserId());
                if (user != null) {
                    recordNode.put("username", user.getUsername());
                }
                
                recordArray.add(recordNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("records", recordArray);
            data.put("total", records.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
            
        } catch (Exception e) {
            logger.error("查询所有借阅记录失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
}

