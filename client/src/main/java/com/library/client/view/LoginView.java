package com.library.client.view;

import com.library.client.ClientApp;
import com.library.client.model.Session;
import com.library.client.net.SocketClient;
import com.library.common.protocol.OpCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * 登录/注册视图
 */
public class LoginView {
    private final ClientApp app;
    private final SocketClient client;
    private final Session session;
    private TextField usernameField;
    private PasswordField passwordField;
    private Label statusLabel;
    
    public LoginView(ClientApp app, SocketClient client, Session session) {
        this.app = app;
        this.client = client;
        this.session = session;
    }
    
    public Scene createScene() {
        // 主容器 - 使用StackPane以支持图片背景
        StackPane root = new StackPane();
        
        // 尝试加载背景图片
        try {
            Image bgImage = new Image(getClass().getResourceAsStream("/images/login-background.jpg"));
            if (bgImage != null) {
                BackgroundImage backgroundImage = new BackgroundImage(
                    bgImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    new BackgroundSize(100, 100, true, true, true, true)
                );
                root.setBackground(new Background(backgroundImage));
            } else {
                // 使用渐变背景
                root.setStyle("-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");
            }
        } catch (Exception e) {
            // 加载失败时使用渐变背景
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");
        }
        
        // 登录面板容器
        VBox loginPanel = new VBox(25);
        loginPanel.setAlignment(Pos.CENTER);
        loginPanel.setPadding(new Insets(50, 60, 50, 60));
        loginPanel.getStyleClass().add("login-panel");
        loginPanel.setMaxWidth(450);
        
        // Logo/标题区域
        VBox titleBox = new VBox(15);
        titleBox.setAlignment(Pos.CENTER);
        
        // 尝试加载Logo
        ImageView logoView = null;
        try {
            Image logo = new Image(getClass().getResourceAsStream("/images/library-logo.png"));
            if (logo != null) {
                logoView = new ImageView(logo);
                logoView.setFitWidth(120);
                logoView.setFitHeight(120);
                logoView.setPreserveRatio(true);
            }
        } catch (Exception e) {
            // Logo不存在，使用文字标题
        }
        
        Text title = new Text("图书馆管理系统");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 32));
        title.setFill(Color.web("#667eea"));
        
        if (logoView != null) {
            titleBox.getChildren().addAll(logoView, title);
        } else {
            titleBox.getChildren().add(title);
        }
        
        // 登录表单
        VBox form = new VBox(20);
        form.setAlignment(Pos.CENTER);
        form.getStyleClass().add("login-form");
        
        VBox usernameBox = new VBox(8);
        Label usernameLabel = new Label("用户名");
        usernameLabel.getStyleClass().add("login-label");
        usernameField = new TextField();
        usernameField.setPromptText("请输入用户名");
        usernameField.getStyleClass().add("login-field");
        usernameBox.getChildren().addAll(usernameLabel, usernameField);
        
        VBox passwordBox = new VBox(8);
        Label passwordLabel = new Label("密码");
        passwordLabel.getStyleClass().add("login-label");
        passwordField = new PasswordField();
        passwordField.setPromptText("请输入密码");
        passwordField.getStyleClass().add("login-field");
        passwordBox.getChildren().addAll(passwordLabel, passwordField);
        
        form.getChildren().addAll(usernameBox, passwordBox);
        
        // 按钮区域
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        Button loginButton = new Button("登录");
        loginButton.getStyleClass().add("login-button");
        loginButton.setOnAction(e -> handleLogin());
        
        Button registerButton = new Button("注册");
        registerButton.getStyleClass().add("register-button");
        registerButton.setOnAction(e -> handleRegister());
        
        buttonBox.getChildren().addAll(loginButton, registerButton);
        
        // 状态标签
        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setMaxWidth(350);
        statusLabel.setWrapText(true);
        statusLabel.setAlignment(Pos.CENTER);
        
        // 组装面板
        loginPanel.getChildren().addAll(titleBox, form, buttonBox, statusLabel);
        
        // 将面板添加到根容器
        root.getChildren().add(loginPanel);
        
        // 回车键登录
        passwordField.setOnAction(e -> handleLogin());
        usernameField.setOnAction(e -> passwordField.requestFocus());
        
        Scene scene = new Scene(root, 500, 600);
        
        // 加载CSS样式
        try {
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("无法加载CSS样式: " + e.getMessage());
        }
        
        return scene;
    }
    
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("用户名和密码不能为空");
            }
            return;
        }
        
        // 禁用按钮，防止重复提交
        javafx.application.Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText("正在登录...");
            }
        });
        
        // 在后台线程执行网络请求
        new Thread(() -> {
            try {
                // 构建登录请求
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.LOGIN);
                request.setPayload(JsonUtil.createObjectNode()
                        .put("username", username)
                        .put("password", password));
                
                // 发送请求
                Response response = client.send(request);
                
                // 在JavaFX线程中更新UI
                javafx.application.Platform.runLater(() -> {
                    if (response != null && response.isSuccess() && response.getData() != null) {
                        String token = response.getDataString("token");
                        Long userId = response.getDataLong("userId");
                        String role = response.getDataString("role");
                        
                        // 保存会话
                        session.login(token, userId, username, role);
                        
                        // 跳转到主界面
                        app.showHomeView();
                    } else {
                        if (statusLabel != null) {
                            String errorMsg = response != null ? response.getMessage() : "网络错误或服务器无响应";
                            statusLabel.setText("登录失败: " + errorMsg);
                        }
                    }
                });
            } catch (Exception e) {
                // 在JavaFX线程中更新UI
                javafx.application.Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("登录失败: " + e.getMessage());
                    }
                });
            }
        }).start();
    }
    
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("用户名和密码不能为空");
            }
            return;
        }
        
        // 在后台线程执行网络请求
        new Thread(() -> {
            try {
                // 构建注册请求
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.REGISTER);
                request.setPayload(JsonUtil.createObjectNode()
                        .put("username", username)
                        .put("password", password));
                
                // 发送请求
                Response response = client.send(request);
                
                // 在JavaFX线程中更新UI
                javafx.application.Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        if (statusLabel != null) {
                            statusLabel.setText("注册成功，请登录");
                        }
                        passwordField.clear();
                    } else {
                        if (statusLabel != null) {
                            statusLabel.setText("注册失败: " + response.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                // 在JavaFX线程中更新UI
                javafx.application.Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("注册失败: " + e.getMessage());
                    }
                });
            }
        }).start();
    }
    
}




