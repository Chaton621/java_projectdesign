package com.library.common.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.library.common.util.JsonUtil;

import java.util.Map;
import java.util.Objects;

/**
 * 响应对象
 * 服务端返回给客户端的响应格式
 */
public class Response {
    @JsonProperty("requestId")
    private String requestId;
    
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("code")
    private String code;  // 错误码（成功时为null或"SUCCESS"）
    
    @JsonProperty("message")
    private String message;  // 错误消息（成功时为null或"操作成功"）
    
    @JsonProperty("data")
    private JsonNode data;
    
    // 默认构造函数（Jackson反序列化需要）
    public Response() {
    }
    
    @JsonCreator
    public Response(@JsonProperty("requestId") String requestId,
                    @JsonProperty("success") boolean success,
                    @JsonProperty("code") String code,
                    @JsonProperty("message") String message,
                    @JsonProperty("data") JsonNode data) {
        this.requestId = requestId;
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    /**
     * 创建成功响应
     */
    public static Response success(String requestId, JsonNode data) {
        return new Response(requestId, true, "SUCCESS", "操作成功", data);
    }
    
    /**
     * 创建成功响应（无数据）
     */
    public static Response success(String requestId) {
        return new Response(requestId, true, "SUCCESS", "操作成功", null);
    }
    
    /**
     * 创建成功响应（带消息）
     */
    public static Response success(String requestId, String message, JsonNode data) {
        return new Response(requestId, true, "SUCCESS", message, data);
    }
    
    /**
     * 创建失败响应
     */
    public static Response error(String requestId, ErrorCode errorCode) {
        return new Response(requestId, false, errorCode.name(), errorCode.getMessage(), null);
    }
    
    /**
     * 创建失败响应（带自定义消息）
     */
    public static Response error(String requestId, ErrorCode errorCode, String customMessage) {
        return new Response(requestId, false, errorCode.name(), customMessage, null);
    }
    
    /**
     * 创建失败响应（带数据）
     */
    public static Response error(String requestId, ErrorCode errorCode, JsonNode data) {
        return new Response(requestId, false, errorCode.name(), errorCode.getMessage(), data);
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public JsonNode getData() {
        return data;
    }
    
    public void setData(JsonNode data) {
        this.data = data;
    }
    
    /**
     * 获取data中的字符串值
     */
    public String getDataString(String key) {
        if (data == null || !data.has(key)) {
            return null;
        }
        JsonNode node = data.get(key);
        return node.isTextual() ? node.asText() : null;
    }
    
    /**
     * 获取data中的整数值
     */
    public Integer getDataInt(String key) {
        if (data == null || !data.has(key)) {
            return null;
        }
        JsonNode node = data.get(key);
        return node.isNumber() ? node.asInt() : null;
    }
    
    /**
     * 获取data中的长整数值
     */
    public Long getDataLong(String key) {
        if (data == null || !data.has(key)) {
            return null;
        }
        JsonNode node = data.get(key);
        return node.isNumber() ? node.asLong() : null;
    }
    
    /**
     * 将data转换为Map（便于使用）
     */
    public Map<String, Object> getDataAsMap() {
        if (data == null) {
            return null;
        }
        return JsonUtil.jsonNodeToMap(data);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Response response = (Response) o;
        return success == response.success &&
               Objects.equals(requestId, response.requestId) &&
               Objects.equals(code, response.code) &&
               Objects.equals(message, response.message) &&
               Objects.equals(data, response.data);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(requestId, success, code, message, data);
    }
    
    @Override
    public String toString() {
        return "Response{" +
               "requestId='" + requestId + '\'' +
               ", success=" + success +
               ", code='" + code + '\'' +
               ", message='" + message + '\'' +
               ", data=" + data +
               '}';
    }
}




