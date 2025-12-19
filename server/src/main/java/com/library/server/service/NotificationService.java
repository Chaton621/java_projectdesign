package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.DataSourceProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    private static final Map<Long, String> pendingNotifications = new ConcurrentHashMap<>();
    
    public static void addNotification(Long userId, String message) {
        pendingNotifications.put(userId, message);
        logger.info("添加通知: userId={}, message={}", userId, message.substring(0, Math.min(50, message.length())));
    }
    
    public static String getAndClearNotification(Long userId) {
        return pendingNotifications.remove(userId);
    }
    
    public static boolean hasNotification(Long userId) {
        return pendingNotifications.containsKey(userId);
    }
}





