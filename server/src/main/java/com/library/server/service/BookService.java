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
            
            // 更新字段（如果提供）
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
}
