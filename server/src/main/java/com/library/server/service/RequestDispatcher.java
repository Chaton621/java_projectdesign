package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.OpCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 请求分发器
 * 按OpCode路由到对应的Service方法，统一异常捕获与日志
 */
public class RequestDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(RequestDispatcher.class);
    
    private final TokenService tokenService;
    private final Map<OpCode, Function<Request, Response>> handlers;
    
    private final UserService userService;
    private final BookService bookService;
    private final TrendingService trendingService;
    private final BorrowService borrowService;
    private final FineQueryService fineQueryService;
    private final FineRateConfigService fineRateConfigService;
    private final StatisticsService statisticsService;
    private final RecommendService recommendService;
    private final ChatService chatService;
    private final UserRecommendationService userRecommendationService;
    
    public RequestDispatcher(TokenService tokenService) {
        this.tokenService = tokenService;
        this.userService = new UserService();
        this.bookService = new BookService();
        this.trendingService = new TrendingService();
        this.borrowService = new BorrowService();
        this.fineQueryService = new FineQueryService();
        this.fineRateConfigService = new FineRateConfigService();
        this.statisticsService = new StatisticsService();
        this.recommendService = new RecommendService();
        this.chatService = new ChatService();
        this.userRecommendationService = new UserRecommendationService();
        this.handlers = new HashMap<>();
        registerHandlers();
    }
    
    /**
     * 注册所有处理器
     */
    private void registerHandlers() {
        handlers.put(OpCode.REGISTER, this::handleRegister);
        handlers.put(OpCode.LOGIN, this::handleLogin);
        handlers.put(OpCode.SEARCH_BOOK, this::handleSearchBook);
        handlers.put(OpCode.TRENDING, this::handleTrending);
        handlers.put(OpCode.BORROW_BOOK, this::handleBorrowBook);
        handlers.put(OpCode.RETURN_BOOK, this::handleReturnBook);
        handlers.put(OpCode.MY_RECORDS, this::handleMyRecords);
        handlers.put(OpCode.RECOMMEND, this::handleRecommend);
        handlers.put(OpCode.GET_USER_FINE, this::handleGetUserFine);
        handlers.put(OpCode.ADMIN_ADD_BOOK, this::handleAdminAddBook);
        handlers.put(OpCode.ADMIN_UPDATE_BOOK, this::handleAdminUpdateBook);
        handlers.put(OpCode.ADMIN_DELETE_BOOK, this::handleAdminDeleteBook);
        handlers.put(OpCode.ADMIN_IMPORT_BOOKS, this::handleAdminImportBooks);
        handlers.put(OpCode.ADMIN_USER_FREEZE, this::handleAdminUserFreeze);
        handlers.put(OpCode.ADMIN_USER_UNFREEZE, this::handleAdminUserUnfreeze);
        handlers.put(OpCode.ADMIN_ALL_RECORDS, this::handleAdminAllRecords);
        handlers.put(OpCode.ADMIN_LIST_USERS, this::handleAdminListUsers);
        handlers.put(OpCode.ADMIN_ALL_USERS_FINE, this::handleAdminAllUsersFine);
        handlers.put(OpCode.ADMIN_SEND_REMINDER, this::handleAdminSendReminder);
        handlers.put(OpCode.ADMIN_GET_FINE_RATE_CONFIG, this::handleAdminGetFineRateConfig);
        handlers.put(OpCode.ADMIN_UPDATE_FINE_RATE_CONFIG, this::handleAdminUpdateFineRateConfig);
        handlers.put(OpCode.ADMIN_ADD_FINE_RATE_CONFIG, this::handleAdminAddFineRateConfig);
        handlers.put(OpCode.ADMIN_DELETE_FINE_RATE_CONFIG, this::handleAdminDeleteFineRateConfig);
        handlers.put(OpCode.ADMIN_STATISTICS, this::handleAdminStatistics);
        handlers.put(OpCode.SEND_MESSAGE, this::handleSendMessage);
        handlers.put(OpCode.GET_CONVERSATION, this::handleGetConversation);
        handlers.put(OpCode.GET_RECENT_CONVERSATIONS, this::handleGetRecentConversations);
        handlers.put(OpCode.GET_UNREAD_COUNT, this::handleGetUnreadCount);
        handlers.put(OpCode.RECOMMEND_USERS, this::handleRecommendUsers);
        handlers.put(OpCode.SEARCH_USERS, this::handleSearchUsers);
    }
    
    /**
     * 分发请求
     */
    public Response dispatch(Request request) {
        if (request == null || request.getOpCode() == null) {
            return Response.error(request != null ? request.getRequestId() : "unknown", 
                ErrorCode.INVALID_PARAMETER, "请求格式错误");
        }
        
        logger.debug("分发请求: opCode={}, requestId={}", request.getOpCode(), request.getRequestId());
        
        try {
            Function<Request, Response> handler = handlers.get(request.getOpCode());
            if (handler == null) {
                logger.warn("未找到处理器: opCode={}", request.getOpCode());
                return Response.error(request.getRequestId(), ErrorCode.INVALID_PARAMETER, 
                    "不支持的操作: " + request.getOpCode());
            }
            
            return handler.apply(request);
        } catch (Exception e) {
            logger.error("处理请求异常: opCode={}, requestId={}", 
                request.getOpCode(), request.getRequestId(), e);
            return Response.error(request.getRequestId(), ErrorCode.SERVER_ERROR, 
                "服务器内部错误: " + e.getMessage());
        }
    }
    
    /**
     * 验证Token（如果需要）
     */
    private boolean validateToken(Request request, boolean requireAdmin) {
        String token = request.getToken();
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        if (requireAdmin) {
            return tokenService.isAdmin(token);
        } else {
            return tokenService.validateToken(token) != null;
        }
    }
    
    private Response handleRegister(Request request) {
        logger.info("处理注册请求: requestId={}", request.getRequestId());
        return userService.register(request);
    }
    
    private Response handleLogin(Request request) {
        logger.info("处理登录请求: requestId={}", request.getRequestId());
        return userService.login(request, tokenService);
    }
    
    private Response handleSearchBook(Request request) {
        logger.info("处理搜索图书请求: requestId={}", request.getRequestId());
        return bookService.searchBooks(request);
    }
    
    private Response handleTrending(Request request) {
        logger.info("处理热门图书请求: requestId={}", request.getRequestId());
        return trendingService.getTrending(request);
    }
    
    private Response handleBorrowBook(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理借书请求: requestId={}, userId={}", request.getRequestId(), userId);
        return borrowService.borrowBook(request, userId);
    }
    
    private Response handleReturnBook(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理还书请求: requestId={}, userId={}", request.getRequestId(), userId);
        return borrowService.returnBook(request, userId);
    }
    
    private Response handleMyRecords(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理我的记录请求: requestId={}, userId={}", request.getRequestId(), userId);
        return borrowService.getMyRecords(request, userId);
    }
    
    private Response handleRecommend(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理推荐请求: requestId={}, userId={}", request.getRequestId(), userId);
        return recommendService.recommend(request, userId);
    }
    
    private Response handleAdminAddBook(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员添加图书请求: requestId={}", request.getRequestId());
        return bookService.addBook(request);
    }
    
    private Response handleAdminUpdateBook(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员更新图书请求: requestId={}", request.getRequestId());
        return bookService.updateBook(request);
    }
    
    private Response handleAdminDeleteBook(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员删除图书请求: requestId={}", request.getRequestId());
        return bookService.deleteBook(request);
    }
    
    private Response handleAdminImportBooks(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员导入图书请求: requestId={}", request.getRequestId());
        return Response.error(request.getRequestId(), ErrorCode.SERVER_ERROR, "NOT_IMPLEMENTED");
    }
    
    private Response handleAdminUserFreeze(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员冻结用户请求: requestId={}", request.getRequestId());
        return userService.freezeUser(request);
    }
    
    private Response handleAdminUserUnfreeze(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员解冻用户请求: requestId={}", request.getRequestId());
        return userService.unfreezeUser(request);
    }
    
    private Response handleGetUserFine(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理查询用户欠费请求: requestId={}, userId={}", request.getRequestId(), userId);
        return fineQueryService.getUserFine(request, userId);
    }
    
    private Response handleAdminAllRecords(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员查询所有借阅记录请求: requestId={}", request.getRequestId());
        return borrowService.getAllRecords(request);
    }
    
    private Response handleAdminListUsers(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员列出所有用户请求: requestId={}", request.getRequestId());
        return userService.listAllUsers(request);
    }
    
    private Response handleAdminAllUsersFine(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员查询所有用户欠费请求: requestId={}", request.getRequestId());
        return fineQueryService.getAllUsersFine(request);
    }
    
    private Response handleAdminSendReminder(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员发送提醒请求: requestId={}", request.getRequestId());
        return fineQueryService.sendReminder(request);
    }
    
    private Response handleAdminGetFineRateConfig(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员获取梯度价格配置请求: requestId={}", request.getRequestId());
        return fineRateConfigService.getAllConfigs(request);
    }
    
    private Response handleAdminUpdateFineRateConfig(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员更新梯度价格配置请求: requestId={}", request.getRequestId());
        return fineRateConfigService.updateConfig(request);
    }
    
    private Response handleAdminAddFineRateConfig(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员添加梯度价格配置请求: requestId={}", request.getRequestId());
        return fineRateConfigService.addConfig(request);
    }
    
    private Response handleAdminDeleteFineRateConfig(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员删除梯度价格配置请求: requestId={}", request.getRequestId());
        return fineRateConfigService.deleteConfig(request);
    }
    
    private Response handleAdminStatistics(Request request) {
        if (!validateToken(request, true)) {
            return Response.error(request.getRequestId(), ErrorCode.FORBIDDEN);
        }
        logger.info("处理管理员统计请求: requestId={}", request.getRequestId());
        return statisticsService.getStatistics(request);
    }
    
    private Response handleSendMessage(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理发送消息请求: requestId={}, userId={}", request.getRequestId(), userId);
        return chatService.sendMessage(request, userId);
    }
    
    private Response handleGetConversation(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理获取对话请求: requestId={}, userId={}", request.getRequestId(), userId);
        return chatService.getConversation(request, userId);
    }
    
    private Response handleGetRecentConversations(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理获取最近对话请求: requestId={}, userId={}", request.getRequestId(), userId);
        return chatService.getRecentConversations(request, userId);
    }
    
    private Response handleGetUnreadCount(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理获取未读消息数请求: requestId={}, userId={}", request.getRequestId(), userId);
        return chatService.getUnreadCount(request, userId);
    }
    
    private Response handleRecommendUsers(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        Long userId = tokenService.validateToken(request.getToken());
        if (userId == null) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理推荐用户请求: requestId={}, userId={}", request.getRequestId(), userId);
        return userRecommendationService.recommendUsers(request, userId);
    }
    
    private Response handleSearchUsers(Request request) {
        if (!validateToken(request, false)) {
            return Response.error(request.getRequestId(), ErrorCode.AUTH_FAILED);
        }
        logger.info("处理搜索用户请求: requestId={}", request.getRequestId());
        return userRecommendationService.searchUsers(request);
    }
}

