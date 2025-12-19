package com.library.client.net;

import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Socket客户端
 * 用于与服务器通信
 */
public class SocketClient {
    private static final Logger logger = LoggerFactory.getLogger(SocketClient.class);
    
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final Lock lock = new ReentrantLock();
    private volatile boolean connected = false;
    
    public SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * 连接到服务器
     */
    public void connect() throws IOException {
        lock.lock();
        try {
            if (connected && socket != null && !socket.isClosed()) {
                logger.debug("已经连接到服务器");
                return;
            }
            
            logger.info("正在连接到服务器: {}:{}", host, port);
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(socket.getOutputStream(), true);
            connected = true;
            logger.info("成功连接到服务器: {}:{}", host, port);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 发送请求并接收响应
     */
    public Response send(Request request) {
        lock.lock();
        try {
            // 确保已连接
            if (!connected || socket == null || socket.isClosed()) {
                connect();
            }
            
            // 序列化请求
            String requestJson = JsonUtil.encode(request);
            
            // 发送请求
            writer.println(requestJson);
            writer.flush();
            
            logger.debug("发送请求: requestId={}, opCode={}", 
                request.getRequestId(), request.getOpCode());
            
            // 读取响应
            String responseLine = reader.readLine();
            if (responseLine == null) {
                throw new IOException("服务器关闭了连接");
            }
            
            // 反序列化响应
            Response response = JsonUtil.decode(responseLine, Response.class);
            if (response == null) {
                throw new IOException("无法解析服务器响应: " + 
                    (responseLine.length() > 100 ? responseLine.substring(0, 100) : responseLine));
            }
            
            logger.debug("收到响应: requestId={}, success={}", 
                response.getRequestId(), response.isSuccess());
            
            return response;
            
        } catch (IOException e) {
            logger.error("发送请求失败", e);
            connected = false;
            // 尝试重新连接
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ex) {
                logger.error("关闭连接失败", ex);
            }
            throw new RuntimeException("网络通信失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        lock.lock();
        try {
            connected = false;
            
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.error("关闭BufferedReader失败", e);
                }
            }
            
            if (writer != null) {
                writer.close();
            }
            
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                    logger.info("已关闭与服务器的连接");
                } catch (IOException e) {
                    logger.error("关闭Socket失败", e);
                }
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}
