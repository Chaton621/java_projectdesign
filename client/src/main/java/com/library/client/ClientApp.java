package com.library.client;

import com.library.client.model.Session;
import com.library.client.net.SocketClient;
import com.library.client.view.AdminDashboardView;
import com.library.client.view.AdminHomeView;
import com.library.client.view.ChatView;
import com.library.client.view.LoginView;
import com.library.client.view.ReaderDashboardView;
import com.library.client.view.UserHomeView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(ClientApp.class);
    
    private Stage primaryStage;
    private SocketClient client;
    private Session session;
    private LoginView loginView;
    private UserHomeView userHomeView;
    private AdminHomeView adminHomeView;
    private AdminDashboardView adminDashboardView;
    private ReaderDashboardView readerDashboardView;
    private ChatView chatView;
    
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("图书馆管理系统");
        
        session = new Session();
        
        String host = getParameters().getNamed().getOrDefault("host", "localhost");
        int port = Integer.parseInt(getParameters().getNamed().getOrDefault("port", "9090"));
        client = new SocketClient(host, port);
        
        loginView = new LoginView(this, client, session);
        userHomeView = new UserHomeView(this, client, session);
        adminHomeView = new AdminHomeView(this, client, session);
        adminDashboardView = new AdminDashboardView(this, client, adminHomeView, session);
        readerDashboardView = new ReaderDashboardView(this, client, userHomeView, session);
        chatView = new ChatView(this, client, session);
        
        showLoginView();
        
        primaryStage.setOnCloseRequest(e -> {
            if (readerDashboardView != null) {
                readerDashboardView.cleanup();
            }
            if (adminDashboardView != null) {
                adminDashboardView.cleanup();
            }
            if (adminHomeView != null) {
                adminHomeView.cleanup();
            }
            if (userHomeView != null) {
                userHomeView.cleanup();
            }
            if (client != null) {
                client.close();
            }
        });
    }
    
    public void showLoginView() {
        Scene scene = loginView.createScene();
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    public void showHomeView() {
        if (session.isAdmin()) {
            if (adminDashboardView != null) {
                adminDashboardView.cleanup();
            }
            if (adminHomeView != null) {
                adminHomeView.cleanup();
            }
            Scene scene = adminDashboardView.createScene();
            primaryStage.setScene(scene);
        } else {
            if (readerDashboardView != null) {
                readerDashboardView.cleanup();
            }
            if (userHomeView != null) {
                userHomeView.cleanup();
            }
            Scene scene = readerDashboardView.createScene();
            primaryStage.setScene(scene);
        }
        primaryStage.show();
    }
    
    public Stage getPrimaryStage() {
        return primaryStage;
    }
    
    public Session getSession() {
        return session;
    }
    
    public void showChatView() {
        if (chatView != null) {
            Scene scene = chatView.createScene();
            primaryStage.setScene(scene);
            primaryStage.show();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}









