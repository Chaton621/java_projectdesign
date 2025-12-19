package com.library.server.net;

import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.service.RequestDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * 客户端连接处理器
 * 每个客户端连接对应一个ClientHandler实例
 * 在独立线程中运行，处理该客户端的请求
 */
public class ClientHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    
    private final Socket clientSocket;
    private final RequestDispatcher dispatcher;
    private BufferedReader reader;
    private PrintWriter writer;
    
    public ClientHandler(Socket clientSocket, RequestDispatcher dispatcher) {
        this.clientSocket = clientSocket;
        this.dispatcher = dispatcher;
    }
    
    /**
     * 处理客户端连接
     * 读取请求，处理，发送响应
     */
    public void run() {
        String clientAddr = clientSocket.getRemoteSocketAddress().toString();
        logger.info("开始处理客户端连接: {}", clientAddr);
        
        try {
            // 创建输入输出流
            reader = new BufferedReader(new InputStreamReader(
                clientSocket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(clientSocket.getOutputStream(), true);
            
            // 持续读取请求
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;  // 跳过空行
                }
                
                try {
                    // 解析请求
                    Request request = JsonUtil.decode(line, Request.class);
                    if (request == null) {
                        logger.warn("无法解析请求: {}", line.length() > 100 ? 
                            line.substring(0, 100) : line);
                        continue;
                    }
                    
                    logger.debug("收到请求: requestId={}, opCode={}", 
                        request.getRequestId(), request.getOpCode());
                    
                    // 处理请求
                    Response response = dispatcher.dispatch(request);
                    
                    // 发送响应
                    String responseJson = JsonUtil.encode(response);
                    writer.println(responseJson);
                    writer.flush();
                    
                    logger.debug("发送响应: requestId={}, success={}", 
                        request.getRequestId(), response.isSuccess());
                    
                } catch (Exception e) {
                    logger.error("处理请求异常: {}", line.length() > 100 ? 
                        line.substring(0, 100) : line, e);
                    
                    // 发送错误响应
                    try {
                        Response errorResponse = Response.error(
                            "unknown", 
                            com.library.common.protocol.ErrorCode.SERVER_ERROR,
                            "服务器处理请求时发生异常: " + e.getMessage()
                        );
                        String errorJson = JsonUtil.encode(errorResponse);
                        writer.println(errorJson);
                        writer.flush();
                    } catch (Exception ex) {
                        logger.error("发送错误响应失败", ex);
                    }
                }
            }
            
        } catch (IOException e) {
            if (clientSocket.isClosed()) {
                logger.info("客户端连接已关闭: {}", clientAddr);
            } else {
                logger.error("读取客户端数据失败: {}", clientAddr, e);
            }
        } catch (Exception e) {
            logger.error("处理客户端连接时发生未预期异常: {}", clientAddr, e);
        } finally {
            close();
            logger.info("客户端连接处理完成: {}", clientAddr);
        }
    }
    
    /**
     * 关闭连接和资源
     */
    private void close() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            logger.error("关闭BufferedReader失败", e);
        }
        
        if (writer != null) {
            writer.close();
        }
        
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.error("关闭Socket失败", e);
        }
    }
}
