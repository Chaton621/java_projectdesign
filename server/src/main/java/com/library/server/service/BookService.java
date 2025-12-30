package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.BookDao;
import com.library.server.model.Book;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.io.StringReader;
import java.io.BufferedReader;

/**
 * 图书服务
 */
public class BookService {
    private static final Logger logger = LoggerFactory.getLogger(BookService.class);
    private final BookDao bookDao = new BookDao();
    
    /**
     * 搜索图书
     */
    public Response searchBooks(Request request) {
        String requestId = request.getRequestId();
        
        try {
            String keyword = request.getPayloadString("keyword");
            String category = request.getPayloadString("category");
            int limit = request.getPayloadInt("limit", 20);
            int offset = request.getPayloadInt("offset", 0);
            
            List<Book> books = bookDao.searchBooks(keyword, category, limit, offset);
            
            ArrayNode booksArray = JsonUtil.getObjectMapper().createArrayNode();
            for (Book book : books) {
                ObjectNode bookNode = JsonUtil.getObjectMapper().createObjectNode();
                bookNode.put("id", book.getId());
                bookNode.put("isbn", book.getIsbn());
                bookNode.put("title", book.getTitle());
                bookNode.put("author", book.getAuthor());
                bookNode.put("category", book.getCategory());
                bookNode.put("publisher", book.getPublisher());
                bookNode.put("description", book.getDescription());
                bookNode.put("coverImagePath", book.getCoverImagePath());
                bookNode.put("totalCount", book.getTotalCount());
                bookNode.put("availableCount", book.getAvailableCount());
                if (book.getCreatedAt() != null) {
                    bookNode.put("createdAt", book.getCreatedAt().toString());
                }
                booksArray.add(bookNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("books", booksArray);
            data.put("total", books.size());
            
            return Response.success(requestId, data);
        } catch (Exception e) {
            logger.error("搜索图书失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR, 
                "搜索图书失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加图书
     */
    public Response addBook(Request request) {
        String requestId = request.getRequestId();
        
        try {
            String isbn = request.getPayloadString("isbn");
            String title = request.getPayloadString("title");
            String author = request.getPayloadString("author");
            String category = request.getPayloadString("category");
            String publisher = request.getPayloadString("publisher");
            String description = request.getPayloadString("description");
            Integer totalCount = request.getPayloadInt("totalCount");
            
            if (isbn == null || title == null || author == null || category == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    "必填字段不能为空");
            }
            
            if (totalCount == null || totalCount < 0) {
                totalCount = 0;
            }
            
            Book book = new Book();
            book.setIsbn(isbn);
            book.setTitle(title);
            book.setAuthor(author);
            book.setCategory(category);
            book.setPublisher(publisher);
            book.setDescription(description);
            book.setTotalCount(totalCount);
            book.setAvailableCount(totalCount);
            book.setCreatedAt(LocalDateTime.now());
            
            Long bookId = bookDao.insertBook(book);
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("bookId", bookId);
            
            return Response.success(requestId, "添加图书成功", data);
        } catch (Exception e) {
            logger.error("添加图书失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR, 
                "添加图书失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新图书
     */
    public Response updateBook(Request request) {
        String requestId = request.getRequestId();
        
        try {
            Long bookId = request.getPayloadLong("bookId");
            if (bookId == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    "图书ID不能为空");
            }
            
            Book existingBook = bookDao.findById(bookId);
            if (existingBook == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, "图书不存在");
            }
            
            if (request.getPayloadString("isbn") != null) {
                existingBook.setIsbn(request.getPayloadString("isbn"));
            }
            if (request.getPayloadString("title") != null) {
                existingBook.setTitle(request.getPayloadString("title"));
            }
            if (request.getPayloadString("author") != null) {
                existingBook.setAuthor(request.getPayloadString("author"));
            }
            if (request.getPayloadString("category") != null) {
                existingBook.setCategory(request.getPayloadString("category"));
            }
            if (request.getPayloadString("publisher") != null) {
                existingBook.setPublisher(request.getPayloadString("publisher"));
            }
            if (request.getPayloadString("description") != null) {
                existingBook.setDescription(request.getPayloadString("description"));
            }
            if (request.getPayloadInt("totalCount") != null) {
                int newTotalCount = request.getPayloadInt("totalCount");
                int delta = newTotalCount - existingBook.getTotalCount();
                existingBook.setTotalCount(newTotalCount);
                existingBook.setAvailableCount(existingBook.getAvailableCount() + delta);
            }
            
            boolean updated = bookDao.updateBook(existingBook);
            
            if (updated) {
                return Response.success(requestId, "更新图书成功", null);
            } else {
                return Response.error(requestId, ErrorCode.SERVER_ERROR, "更新图书失败");
            }
        } catch (Exception e) {
            logger.error("更新图书失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR, 
                "更新图书失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除图书
     */
    public Response deleteBook(Request request) {
        String requestId = request.getRequestId();
        
        try {
            Long bookId = request.getPayloadLong("bookId");
            if (bookId == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    "图书ID不能为空");
            }
            
            Book book = bookDao.findById(bookId);
            if (book == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, "图书不存在");
            }
            
            boolean deleted = bookDao.deleteBook(bookId);
            
            if (deleted) {
                return Response.success(requestId, "删除图书成功", null);
            } else {
                return Response.error(requestId, ErrorCode.SERVER_ERROR, "删除图书失败");
            }
        } catch (Exception e) {
            logger.error("删除图书失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR, 
                "删除图书失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量导入图书
     * 支持CSV/TXT/EXCEL格式
     * 格式：isbn,title,author,category,publisher,description,totalCount
     */
    public Response importBooks(Request request) {
        String requestId = request.getRequestId();
        
        try {
            String content = request.getPayloadString("content");
            
            if (content == null || content.trim().isEmpty()) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    "文件内容不能为空");
            }
            
            // 解析CSV格式的内容（无论是CSV、TXT还是EXCEL转换后的内容）
            List<Book> books = parseCsvContent(content);
            
            int successCount = 0;
            int failCount = 0;
            List<String> errorMessages = new ArrayList<>();
            
            // 批量插入图书
            for (Book book : books) {
                try {
                    // 验证必填字段
                    if (book.getIsbn() == null || book.getIsbn().trim().isEmpty() ||
                        book.getTitle() == null || book.getTitle().trim().isEmpty() ||
                        book.getAuthor() == null || book.getAuthor().trim().isEmpty() ||
                        book.getCategory() == null || book.getCategory().trim().isEmpty()) {
                        failCount++;
                        errorMessages.add(String.format("ISBN %s: 必填字段不能为空", 
                            book.getIsbn() != null ? book.getIsbn() : "未知"));
                        continue;
                    }
                    
                    // 检查ISBN是否已存在
                    Book existingBook = bookDao.findByIsbn(book.getIsbn());
                    if (existingBook != null) {
                        // 如果已存在，更新图书信息
                        existingBook.setTitle(book.getTitle());
                        existingBook.setAuthor(book.getAuthor());
                        existingBook.setCategory(book.getCategory());
                        existingBook.setPublisher(book.getPublisher());
                        existingBook.setDescription(book.getDescription());
                        
                        // 更新总数，同时更新可用数
                        int delta = book.getTotalCount() - existingBook.getTotalCount();
                        existingBook.setTotalCount(book.getTotalCount());
                        existingBook.setAvailableCount(existingBook.getAvailableCount() + delta);
                        
                        bookDao.updateBook(existingBook);
                        successCount++;
                    } else {
                        // 新图书，插入
                        book.setAvailableCount(book.getTotalCount());
                        book.setCreatedAt(LocalDateTime.now());
                        bookDao.insertBook(book);
                        successCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    errorMessages.add(String.format("ISBN %s: %s", 
                        book.getIsbn() != null ? book.getIsbn() : "未知", e.getMessage()));
                    logger.warn("导入图书失败: ISBN={}, error={}", book.getIsbn(), e.getMessage());
                }
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("successCount", successCount);
            data.put("failCount", failCount);
            data.put("totalCount", books.size());
            
            String message = String.format("导入完成：成功%d本，失败%d本", successCount, failCount);
            if (failCount > 0 && errorMessages.size() > 0) {
                // 只返回前10个错误信息，避免响应过大
                int maxErrors = Math.min(10, errorMessages.size());
                ArrayNode errorsArray = JsonUtil.getObjectMapper().createArrayNode();
                for (int i = 0; i < maxErrors; i++) {
                    errorsArray.add(errorMessages.get(i));
                }
                data.set("errors", errorsArray);
                if (errorMessages.size() > maxErrors) {
                    message += String.format("（显示前%d个错误）", maxErrors);
                }
            }
            
            return Response.success(requestId, message, data);
        } catch (Exception e) {
            logger.error("批量导入图书失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR, 
                "批量导入图书失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析CSV格式的内容
     * 格式：isbn,title,author,category,publisher,description,totalCount
     */
    private List<Book> parseCsvContent(String content) throws Exception {
        List<Book> books = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // 跳过空行
                if (line.isEmpty()) {
                    continue;
                }
                
                // 跳过表头行（如果第一行包含"isbn"等关键字）
                if (lineNumber == 1 && line.toLowerCase().contains("isbn")) {
                    continue;
                }
                
                // 解析CSV行（简单处理，不支持引号内的逗号）
                String[] parts = line.split(",");
                if (parts.length < 4) {
                    // 至少需要isbn,title,author,category
                    continue;
                }
                
                Book book = new Book();
                book.setIsbn(parts[0].trim());
                book.setTitle(parts.length > 1 ? parts[1].trim() : "");
                book.setAuthor(parts.length > 2 ? parts[2].trim() : "");
                book.setCategory(parts.length > 3 ? parts[3].trim() : "");
                book.setPublisher(parts.length > 4 ? parts[4].trim() : "");
                book.setDescription(parts.length > 5 ? parts[5].trim() : "");
                
                // 解析totalCount
                int totalCount = 0;
                if (parts.length > 6) {
                    try {
                        totalCount = Integer.parseInt(parts[6].trim());
                    } catch (NumberFormatException e) {
                        totalCount = 0;
                    }
                }
                book.setTotalCount(totalCount);
                
                books.add(book);
            }
        }
        
        return books;
    }
}
