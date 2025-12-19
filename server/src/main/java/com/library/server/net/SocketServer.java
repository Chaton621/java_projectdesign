package com.library.server.net;

import com.library.server.dao.DataSourceProvider;
import com.library.server.service.OverdueScheduler;
import com.library.server.service.RequestDispatcher;
import com.library.server.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketServer {
    private static final Logger logger = LoggerFactory.getLogger(SocketServer.class);
    
    private final int port;
    private final int threadPoolSize;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private final TokenService tokenService;
    private final RequestDispatcher dispatcher;
    private final OverdueScheduler overdueScheduler;
    
    public SocketServer(int port, int threadPoolSize) {
        this.port = port;
        this.threadPoolSize = threadPoolSize;
        this.tokenService = new TokenService();
        this.dispatcher = new RequestDispatcher(tokenService);
        this.overdueScheduler = new OverdueScheduler();
    }
    
    public void start() throws IOException {
        if (running.get()) {
            logger.warn("服务器已在运行");
            return;
        }
        
        serverSocket = new ServerSocket(port);
        executorService = Executors.newFixedThreadPool(threadPoolSize);
        running.set(true);
        
        overdueScheduler.start();
        logger.info("Socket服务器启动: port={}, threadPoolSize={}", port, threadPoolSize);
        
        Thread acceptThread = new Thread(this::acceptConnections, "ServerAcceptThread");
        acceptThread.setDaemon(false);
        acceptThread.start();
    }
    
    private void acceptConnections() {
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("接受新连接: {}", clientSocket.getRemoteSocketAddress());
                
                try {
                    final String clientAddr = clientSocket.getRemoteSocketAddress().toString();
                    ClientHandler handler = new ClientHandler(clientSocket, dispatcher);
                    
                    executorService.submit(() -> {
                        try {
                            logger.info("线程池开始执行ClientHandler: {}", clientAddr);
                            handler.run();
                            logger.info("ClientHandler执行完成: {}", clientAddr);
                        } catch (Throwable e) {
                            logger.error("ClientHandler执行异常: {}", clientAddr, e);
                        }
                    });
                    logger.info("ClientHandler已提交到线程池，等待执行: {}, 线程池大小: {}", 
                            clientAddr, ((java.util.concurrent.ThreadPoolExecutor) executorService).getPoolSize());
                } catch (Exception e) {
                    logger.error("创建或提交ClientHandler失败: {}", clientSocket.getRemoteSocketAddress(), e);
                    try {
                        clientSocket.close();
                    } catch (IOException ioException) {
                        logger.error("关闭连接失败", ioException);
                    }
                }
                
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("接受连接失败", e);
                } else {
                    logger.info("服务器已关闭，停止接受连接");
                }
            }
        }
    }
    
    public void stop() {
        if (!running.get()) {
            logger.warn("服务器未运行");
            return;
        }
        
        logger.info("开始关闭服务器...");
        running.set(false);
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                logger.info("ServerSocket已关闭");
            } catch (IOException e) {
                logger.error("关闭ServerSocket失败", e);
            }
        }
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("线程池未在30秒内关闭，强制关闭");
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.error("线程池强制关闭失败");
                    }
                }
                logger.info("线程池已关闭");
            } catch (InterruptedException e) {
                logger.error("等待线程池关闭被中断", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (overdueScheduler != null) {
            overdueScheduler.stop();
        }
        
        if (tokenService != null) {
            tokenService.shutdown();
        }
        
        DataSourceProvider.close();
        
        logger.info("服务器已关闭");
    }
    
    public boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }
    
    public int getPort() {
        return port;
    }
}
