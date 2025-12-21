package com.library.server;

import com.library.server.net.SocketServer;
import com.library.server.util.AdminInitializer;
import com.library.server.util.FineRateConfigInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);
    
    private static final int DEFAULT_PORT = 9090;
    private static final int DEFAULT_THREAD_POOL_SIZE = 20;
    
    public static void main(String[] args) {
        Properties props = loadProperties();
        int port = Integer.parseInt(props.getProperty("server.port", String.valueOf(DEFAULT_PORT)));
        int threadPoolSize = Integer.parseInt(props.getProperty("server.threadPoolSize", String.valueOf(DEFAULT_THREAD_POOL_SIZE)));
        
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            threadPoolSize = Integer.parseInt(args[1]);
        }
        
        AdminInitializer.initialize();
        FineRateConfigInitializer.initialize();
        
        SocketServer server = new SocketServer(port, threadPoolSize);
        
        try {
            server.start();
            logger.info("服务器启动成功: port={}, threadPoolSize={}", port, threadPoolSize);
            logger.info("按Ctrl+C停止服务器");
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("收到关闭信号，正在关闭服务器...");
                server.stop();
            }));
            
            while (server.isRunning()) {
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            logger.error("服务器启动失败", e);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.info("服务器被中断");
            server.stop();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 加载配置文件
     */
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = ServerMain.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
                logger.debug("成功加载application.properties配置文件");
            } else {
                logger.warn("未找到application.properties，使用默认配置");
            }
        } catch (Exception e) {
            logger.warn("加载application.properties失败，使用默认配置", e);
        }
        return props;
    }
}
