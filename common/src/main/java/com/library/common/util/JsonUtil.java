package com.library.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON工具类
 * 基于Jackson的序列化/反序列化工具
 * 支持newline-delimited JSON (NDJSON)格式
 */
public class JsonUtil {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 将对象序列化为JSON字符串
     */
    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("序列化JSON失败", e);
            throw new RuntimeException("JSON序列化失败", e);
        }
    }
    
    /**
     * 将JSON字符串反序列化为对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("反序列化JSON失败: {}", json, e);
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }
    
    /**
     * 将JSON字符串解析为JsonNode
     */
    public static JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            logger.error("解析JSON失败: {}", json, e);
            throw new RuntimeException("JSON解析失败", e);
        }
    }
    
    /**
     * 将JsonNode转换为Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new HashMap<>();
        }
        try {
            return (Map<String, Object>) objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            logger.error("JsonNode转Map失败", e);
            return new HashMap<>();
        }
    }
    
    /**
     * 将对象转换为JsonNode
     */
    public static JsonNode toJsonNode(Object obj) {
        return objectMapper.valueToTree(obj);
    }
    
    /**
     * 创建空的ObjectNode
     */
    public static ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }
    
    /**
     * 编码：将对象序列化为NDJSON格式（一行一个JSON）
     * 添加换行符，便于Socket传输
     */
    public static String encode(Object obj) {
        return toJson(obj) + "\n";
    }
    
    /**
     * 解码：将NDJSON格式的字符串解析为对象
     * 去除换行符后解析
     * 如果解析失败，返回null而不是抛出异常
     */
    public static <T> T decode(String jsonLine, Class<T> clazz) {
        if (jsonLine == null || jsonLine.trim().isEmpty()) {
            logger.warn("解码失败: JSON字符串为空");
            return null;
        }
        // 去除首尾空白字符（包括换行符）
        String trimmed = jsonLine.trim();
        try {
            return objectMapper.readValue(trimmed, clazz);
        } catch (JsonProcessingException e) {
            logger.error("反序列化JSON失败: 前100字符={}, 错误={}", 
                    trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed, 
                    e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("解码JSON时发生未预期异常: {}", trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed, e);
            return null;
        }
    }
    
    /**
     * 获取ObjectMapper实例（用于高级操作）
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}















