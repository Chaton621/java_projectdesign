package com.library.server.util;

import com.library.server.dao.BorrowRecordDao;
import com.library.server.dao.UserDao;
import com.library.server.model.Book;
import com.library.server.model.BorrowRecord;
import com.library.server.model.User;
import com.library.server.dao.BookDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建演示用户账户工具
 * 创建huang和wen两个用户，包含借阅记录和欠费
 */
public class CreateDemoUsers {
    private static final Logger logger = LoggerFactory.getLogger(CreateDemoUsers.class);
    
    public static void main(String[] args) {
        logger.info("开始创建演示用户账户...");
        
        // 生成密码哈希
        String passwordHash = PasswordUtil.hashPassword("12345");
        logger.info("密码哈希（密码: 12345）: {}", passwordHash);
        logger.info("");
        logger.info("请将以下哈希值更新到SQL脚本中：");
        logger.info("huang和wen的password_hash: {}", passwordHash);
        logger.info("");
        
        UserDao userDao = new UserDao();
        BookDao bookDao = new BookDao();
        BorrowRecordDao recordDao = new BorrowRecordDao();
        
        try {
            // 创建或获取huang用户
            User huang = userDao.findByUsername("huang");
            if (huang == null) {
                huang = new User("huang", passwordHash, "USER", "ACTIVE");
                Long huangId = userDao.insertUser(huang);
                huang.setId(huangId);
                logger.info("创建huang用户成功: id={}", huangId);
            } else {
                logger.info("huang用户已存在: id={}", huang.getId());
            }
            
            // 创建或获取wen用户
            User wen = userDao.findByUsername("wen");
            if (wen == null) {
                wen = new User("wen", passwordHash, "USER", "ACTIVE");
                Long wenId = userDao.insertUser(wen);
                wen.setId(wenId);
                logger.info("创建wen用户成功: id={}", wenId);
            } else {
                logger.info("wen用户已存在: id={}", wen.getId());
            }
            
            // 获取一些图书用于借阅
            List<Book> books = bookDao.searchBooks(null, null, 50, 0);
            if (books.size() < 25) {
                logger.warn("图书数量不足，请先导入图书数据");
                return;
            }
            
            logger.info("找到{}本图书，开始创建借阅记录...", books.size());
            
            // 删除这两个用户的旧借阅记录（使用SQL直接删除）
            try (Connection conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection()) {
                java.sql.PreparedStatement stmt = conn.prepareStatement("DELETE FROM borrow_records WHERE user_id IN (?, ?)");
                stmt.setLong(1, huang.getId());
                stmt.setLong(2, wen.getId());
                int deleted = stmt.executeUpdate();
                logger.info("已删除{}条旧借阅记录", deleted);
                
                // 恢复这些图书的可用数量
                stmt = conn.prepareStatement(
                    "UPDATE books SET available_count = LEAST(available_count + 1, total_count) " +
                    "WHERE id IN (SELECT DISTINCT book_id FROM borrow_records WHERE user_id IN (?, ?) AND status = 'BORROWED')"
                );
                stmt.setLong(1, huang.getId());
                stmt.setLong(2, wen.getId());
                stmt.executeUpdate();
            }
            
            // 创建huang的借阅记录
            double huangFine = createBorrowRecords(huang.getId(), "huang", books, recordDao, bookDao);
            
            // 创建wen的借阅记录
            double wenFine = createBorrowRecords(wen.getId(), "wen", books, recordDao, bookDao);
            
            // 更新用户欠费金额（已归还记录的欠费）
            try (Connection conn = com.library.server.dao.DataSourceProvider.getDataSource().getConnection()) {
                userDao.updateFineAmount(conn, huang.getId(), huangFine);
                userDao.updateFineAmount(conn, wen.getId(), wenFine);
            }
            
            logger.info("");
            logger.info("演示用户创建完成！");
            logger.info("用户名: huang, 密码: 12345, 欠费: {}元", huangFine);
            logger.info("用户名: wen, 密码: 12345, 欠费: {}元", wenFine);
            logger.info("");
            logger.info("注意：当前逾期记录的欠费金额会在系统运行时自动计算更新");
            
        } catch (Exception e) {
            logger.error("创建演示用户失败", e);
        }
    }
    
    private static double createBorrowRecords(Long userId, String username, List<Book> books, 
                                           BorrowRecordDao recordDao, BookDao bookDao) {
        LocalDateTime now = LocalDateTime.now();
        int bookIndex = 0;
        double totalFine = 0.0;
        
        try {
            // 历史借阅记录（已归还，无欠费）- 5条
            for (int i = 0; i < 5 && bookIndex < books.size(); i++) {
                BorrowRecord record = new BorrowRecord();
                record.setUserId(userId);
                record.setBookId(books.get(bookIndex).getId());
                record.setBorrowTime(now.minusDays(90 - i * 5));
                record.setDueTime(now.minusDays(60 - i * 5));
                record.setReturnTime(now.minusDays(60 - i * 5));
                record.setStatus("RETURNED");
                record.setFineAmount(0.0);
                record.setCreatedAt(now.minusDays(90 - i * 5));
                recordDao.insertBorrowRecord(null, record);
                bookIndex++;
            }
            
            // 逾期借阅记录（已归还，有欠费）- 2条
            // 逾期5天
            if (bookIndex < books.size()) {
                BorrowRecord record = new BorrowRecord();
                record.setUserId(userId);
                record.setBookId(books.get(bookIndex).getId());
                record.setBorrowTime(now.minusDays(65));
                record.setDueTime(now.minusDays(35));
                record.setReturnTime(now.minusDays(30));
                record.setStatus("RETURNED");
                record.setFineAmount(5.0); // 5天 * 1.0元/天
                record.setCreatedAt(now.minusDays(65));
                recordDao.insertBorrowRecord(null, record);
                totalFine += 5.0;
                bookIndex++;
            }
            
            // 逾期15天
            if (bookIndex < books.size()) {
                BorrowRecord record = new BorrowRecord();
                record.setUserId(userId);
                record.setBookId(books.get(bookIndex).getId());
                record.setBorrowTime(now.minusDays(60));
                record.setDueTime(now.minusDays(30));
                record.setReturnTime(now.minusDays(15));
                record.setStatus("RETURNED");
                record.setFineAmount(23.0); // 7 * 1.0 + 8 * 2.0 = 23.0
                record.setCreatedAt(now.minusDays(60));
                recordDao.insertBorrowRecord(null, record);
                totalFine += 23.0;
                bookIndex++;
            }
            
            // 当前借阅记录（未归还，未逾期）- 2条
            for (int i = 0; i < 2 && bookIndex < books.size(); i++) {
                BorrowRecord record = new BorrowRecord();
                record.setUserId(userId);
                record.setBookId(books.get(bookIndex).getId());
                record.setBorrowTime(now.minusDays(20 - i * 5));
                record.setDueTime(now.plusDays(10 + i * 5));
                record.setReturnTime(null);
                record.setStatus("BORROWED");
                record.setFineAmount(0.0);
                record.setCreatedAt(now.minusDays(20 - i * 5));
                recordDao.insertBorrowRecord(null, record);
                bookDao.updateBookStock(null, books.get(bookIndex).getId(), -1);
                bookIndex++;
            }
            
            // 当前逾期借阅记录（未归还，已逾期）- 2条
            // 逾期8天
            if (bookIndex < books.size()) {
                BorrowRecord record = new BorrowRecord();
                record.setUserId(userId);
                record.setBookId(books.get(bookIndex).getId());
                record.setBorrowTime(now.minusDays(38));
                record.setDueTime(now.minusDays(8));
                record.setReturnTime(null);
                record.setStatus("BORROWED");
                record.setFineAmount(0.0);
                record.setCreatedAt(now.minusDays(38));
                recordDao.insertBorrowRecord(null, record);
                bookDao.updateBookStock(null, books.get(bookIndex).getId(), -1);
                bookIndex++;
            }
            
            // 逾期35天
            if (bookIndex < books.size()) {
                BorrowRecord record = new BorrowRecord();
                record.setUserId(userId);
                record.setBookId(books.get(bookIndex).getId());
                record.setBorrowTime(now.minusDays(65));
                record.setDueTime(now.minusDays(35));
                record.setReturnTime(null);
                record.setStatus("BORROWED");
                record.setFineAmount(0.0);
                record.setCreatedAt(now.minusDays(65));
                recordDao.insertBorrowRecord(null, record);
                bookDao.updateBookStock(null, books.get(bookIndex).getId(), -1);
                bookIndex++;
            }
            
            logger.info("{}的借阅记录创建完成，已归还记录的欠费: {}元", username, totalFine);
            return totalFine;
            
        } catch (Exception e) {
            logger.error("创建{}的借阅记录失败", username, e);
            return totalFine;
        }
    }
}





