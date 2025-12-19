package com.library.common.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.library.common.util.JsonUtil;

import java.util.Objects;

public class Request {
    @JsonProperty("requestId")
    private String requestId;
    
    @JsonProperty("opCode")
    private OpCode opCode;
    
    @JsonProperty("token")
    private String token;
    
    @JsonProperty("payload")
    private JsonNode payload;
    
    public Request() {
    }
    
    @JsonCreator
    public Request(@JsonProperty("requestId") String requestId,
                   @JsonProperty("opCode") OpCode opCode,
                   @JsonProperty("token") String token,
                   @JsonProperty("payload") JsonNode payload) {
        this.requestId = requestId;
        this.opCode = opCode;
        this.token = token;
        this.payload = payload;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public OpCode getOpCode() {
        return opCode;
    }
    
    public void setOpCode(OpCode opCode) {
        this.opCode = opCode;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public JsonNode getPayload() {
        return payload;
    }
    
    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
    
    public String getPayloadString(String key) {
        if (payload == null || !payload.has(key)) {
            return null;
        }
        JsonNode node = payload.get(key);
        return node.isTextual() ? node.asText() : null;
    }
    
    public Long getPayloadLong(String key) {
        if (payload == null || !payload.has(key)) {
            return null;
        }
        JsonNode node = payload.get(key);
        return node.isNumber() ? node.asLong() : null;
    }
    
    public Integer getPayloadInt(String key) {
        if (payload == null || !payload.has(key)) {
            return null;
        }
        JsonNode node = payload.get(key);
        return node.isNumber() ? node.asInt() : null;
    }
    
    public Integer getPayloadInt(String key, int defaultValue) {
        Integer value = getPayloadInt(key);
        return value != null ? value : defaultValue;
    }
    
    public Double getPayloadDouble(String key) {
        if (payload == null || !payload.has(key)) {
            return null;
        }
        JsonNode node = payload.get(key);
        return node.isNumber() ? node.asDouble() : null;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request request = (Request) o;
        return Objects.equals(requestId, request.requestId) &&
               opCode == request.opCode &&
               Objects.equals(token, request.token) &&
               Objects.equals(payload, request.payload);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(requestId, opCode, token, payload);
    }
    
    @Override
    public String toString() {
        return "Request{" +
               "requestId='" + requestId + '\'' +
               ", opCode=" + opCode +
               ", token='" + (token != null ? "***" : null) + '\'' +
               ", payload=" + payload +
               '}';
    }
}
