package com.library.client.view;

import com.library.client.ClientApp;
import com.library.client.model.Session;
import com.library.client.net.SocketClient;
import com.library.common.protocol.OpCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 读者首页 - 推荐系统视图
 */
public class ReaderDashboardView {
    private final ClientApp app;
    private final SocketClient client;
    private final UserHomeView userHomeView;
    private final Session session;
    private MediaPlayer mediaPlayer;
    private Label timeLabel;
    private Label welcomeLabel;
    private Label fineLabel;
    private ScheduledExecutorService scheduler;
    private FlowPane recommendationCardsPane;
    private ObservableList<RecommendationItem> recommendations;
    
    public ReaderDashboardView(ClientApp app, SocketClient client, UserHomeView userHomeView, Session session) {
        this.app = app;
        this.client = client;
        this.userHomeView = userHomeView;
        this.session = session;
    }
    
    public Scene createScene() {
        StackPane backgroundPane = new StackPane();
        
        boolean videoLoaded = false;
        try {
            URL videoUrl = getClass().getResource("/images/user-background.mp4");
            if (videoUrl != null) {
                Media media = new Media(videoUrl.toExternalForm());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setAutoPlay(true);
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                mediaPlayer.setMute(true);
                
                MediaView mediaView = new MediaView(mediaPlayer);
                mediaView.setFitWidth(1200);
                mediaView.setFitHeight(800);
                mediaView.setPreserveRatio(true);
                
                backgroundPane.getChildren().add(0, mediaView);
                backgroundPane.widthProperty().addListener((obs, oldVal, newVal) -> {
                    mediaView.setFitWidth(newVal.doubleValue());
                });
                backgroundPane.heightProperty().addListener((obs, oldVal, newVal) -> {
                    mediaView.setFitHeight(newVal.doubleValue());
                });
                
                videoLoaded = true;
            }
        } catch (Exception e) {
            System.err.println("无法加载视频背景: " + e.getMessage());
        }
        
        if (!videoLoaded) {
            backgroundPane.getStyleClass().add("main-container");
        }
        
        BorderPane root = new BorderPane();
        
        // 顶部栏
        HBox topBox = new HBox(15);
        topBox.setPadding(new Insets(20, 30, 20, 30));
        topBox.getStyleClass().add("top-bar");
        topBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("图书馆管理系统 - 读者首页");
        titleLabel.getStyleClass().add("top-title");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        
        HBox rightBox = new HBox(15);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.setSpacing(15);
        
        String username = session.getUsername();
        Label userLabel = new Label("读者: " + username);
        userLabel.getStyleClass().add("top-user-info");
        
        fineLabel = new Label("欠费: 加载中...");
        fineLabel.getStyleClass().add("top-user-info");
        loadFineInfo();
        
        timeLabel = new Label();
        timeLabel.getStyleClass().add("top-user-info");
        updateTime();
        
        Button logoutButton = new Button("登出");
        logoutButton.getStyleClass().add("logout-button");
        logoutButton.setOnAction(e -> {
            session.logout();
            app.showLoginView();
        });
        
        rightBox.getChildren().addAll(userLabel, fineLabel, timeLabel, logoutButton);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        topBox.getChildren().addAll(titleLabel, spacer, rightBox);
        root.setTop(topBox);
        
        // 中间内容 - 推荐系统
        VBox centerBox = new VBox(20);
        centerBox.setPadding(new Insets(30, 50, 30, 50));
        centerBox.setAlignment(Pos.TOP_CENTER);
        
        // 欢迎信息
        welcomeLabel = new Label();
        welcomeLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        welcomeLabel.setTextFill(Color.WHITE);
        updateWelcomeMessage();
        
        // 推荐图书标题
        Label recommendTitleLabel = new Label("推荐图书");
        recommendTitleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 28));
        recommendTitleLabel.setTextFill(Color.WHITE);
        
        // 推荐卡片容器 - 改进布局
        recommendationCardsPane = new FlowPane(25, 25);
        recommendationCardsPane.setAlignment(Pos.CENTER);
        recommendationCardsPane.setPadding(new Insets(30, 40, 30, 40));
        recommendationCardsPane.setPrefWrapLength(1150);
        recommendationCardsPane.setStyle("-fx-background-color: transparent;");
        
        ScrollPane cardsScrollPane = new ScrollPane(recommendationCardsPane);
        cardsScrollPane.setFitToWidth(true);
        cardsScrollPane.setFitToHeight(true);
        cardsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        cardsScrollPane.setPadding(new Insets(10));
        
        centerBox.getChildren().addAll(welcomeLabel, recommendTitleLabel, cardsScrollPane);
        
        VBox.setVgrow(cardsScrollPane, Priority.ALWAYS);
        
        root.setCenter(centerBox);
        
        // 底部按钮
        HBox bottomButtonBox = new HBox(30);
        bottomButtonBox.setAlignment(Pos.CENTER);
        bottomButtonBox.setPadding(new Insets(20, 0, 30, 0));
        
        Button enterSystemButton = new Button("进入图书系统");
        enterSystemButton.getStyleClass().add("action-button");
        enterSystemButton.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        enterSystemButton.setPrefSize(200, 50);
        enterSystemButton.setStyle("-fx-background-color: #667eea; -fx-text-fill: white; -fx-background-radius: 10;");
        enterSystemButton.setOnAction(e -> {
            cleanup();
            Scene userScene = userHomeView.createScene();
            app.getPrimaryStage().setScene(userScene);
        });
        
        Button chatButton = new Button("聊天交友");
        chatButton.getStyleClass().add("action-button");
        chatButton.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        chatButton.setPrefSize(200, 50);
        chatButton.setStyle("-fx-background-color: #764ba2; -fx-text-fill: white; -fx-background-radius: 10;");
        chatButton.setOnAction(e -> {
            cleanup();
            app.showChatView();
        });
        
        bottomButtonBox.getChildren().addAll(enterSystemButton, chatButton);
        root.setBottom(bottomButtonBox);
        
        backgroundPane.getChildren().add(root);
        
        Scene scene = new Scene(backgroundPane, 1200, 800);
        
        scene.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null && backgroundPane.getChildren().size() > 0) {
                MediaView mediaView = (MediaView) backgroundPane.getChildren().get(0);
                mediaView.setFitWidth(newVal.doubleValue());
            }
        });
        scene.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null && backgroundPane.getChildren().size() > 0) {
                MediaView mediaView = (MediaView) backgroundPane.getChildren().get(0);
                mediaView.setFitHeight(newVal.doubleValue());
            }
        });
        
        try {
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("无法加载CSS样式: " + e.getMessage());
        }
        
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::updateTime, 0, 1, TimeUnit.MINUTES);
        
        Platform.runLater(() -> {
            updateTime();
            loadRecommendations();
        });
        
        // 每分钟更新欢迎信息
        scheduler.scheduleAtFixedRate(this::updateWelcomeMessage, 0, 1, TimeUnit.MINUTES);
        
        return scene;
    }
    
    private void updateTime() {
        LocalDateTime now = LocalDateTime.now();
        String timeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        Platform.runLater(() -> {
            if (timeLabel != null) {
                timeLabel.setText(timeStr);
            }
            updateWelcomeMessage();
        });
    }
    
    private void updateWelcomeMessage() {
        if (welcomeLabel != null && session != null) {
            LocalDateTime now = LocalDateTime.now();
            String timeStr = now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分"));
            String username = session.getUsername();
            welcomeLabel.setText("你好,读者 " + username + ",现在是 " + timeStr);
        }
    }
    
    private void loadFineInfo() {
        new Thread(() -> {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.GET_USER_FINE);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode());
                
                Response response = client.send(request);
                
                Platform.runLater(() -> {
                    if (response.isSuccess() && response.getData() != null) {
                        double totalOwed = response.getData().has("totalOwed") ? 
                                response.getData().get("totalOwed").asDouble() : 0.0;
                        if (fineLabel != null) {
                            fineLabel.setText(String.format("欠费: %.2f元", totalOwed));
                        }
                    } else {
                        if (fineLabel != null) {
                            fineLabel.setText("欠费: 加载失败");
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (fineLabel != null) {
                        fineLabel.setText("欠费: 加载失败");
                    }
                });
            }
        }).start();
    }
    
    private void loadRecommendations() {
        new Thread(() -> {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.RECOMMEND);
                request.setToken(session.getToken());
                ObjectNode payload = JsonUtil.createObjectNode();
                payload.put("topN", 20);
                request.setPayload(payload);
                
                Response response = client.send(request);
                
                Platform.runLater(() -> {
                    if (response.isSuccess() && response.getData() != null) {
                        JsonNode booksNode = response.getData().get("books");
                        recommendations = FXCollections.observableArrayList();
                        
                        if (booksNode != null && booksNode.isArray()) {
                            for (JsonNode bookNode : booksNode) {
                                RecommendationItem item = new RecommendationItem();
                                item.setBookId(bookNode.has("bookId") ? bookNode.get("bookId").asLong() : 0);
                                item.setTitle(bookNode.has("title") ? bookNode.get("title").asText() : "");
                                item.setAuthor(bookNode.has("author") ? bookNode.get("author").asText() : "");
                                item.setCategory(bookNode.has("category") ? bookNode.get("category").asText() : "");
                                item.setAvailableCount(bookNode.has("availableCount") ? bookNode.get("availableCount").asInt() : 0);
                                item.setReason(bookNode.has("reason") ? bookNode.get("reason").asText() : "系统推荐");
                                item.setScore(bookNode.has("score") ? bookNode.get("score").asDouble() : 0.0);
                                recommendations.add(item);
                            }
                        }
                        
                        updateRecommendationCards();
                    } else {
                        recommendationCardsPane.getChildren().clear();
                        Label errorLabel = new Label("加载推荐失败: " + (response != null ? response.getMessage() : "未知错误"));
                        errorLabel.setTextFill(Color.WHITE);
                        recommendationCardsPane.getChildren().add(errorLabel);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    recommendationCardsPane.getChildren().clear();
                    Label errorLabel = new Label("加载推荐失败: " + e.getMessage());
                    errorLabel.setTextFill(Color.WHITE);
                    recommendationCardsPane.getChildren().add(errorLabel);
                });
            }
        }).start();
    }
    
    private void updateRecommendationCards() {
        recommendationCardsPane.getChildren().clear();
        
        if (recommendations == null || recommendations.isEmpty()) {
            Label emptyLabel = new Label("暂无推荐图书");
            emptyLabel.setTextFill(Color.WHITE);
            emptyLabel.setFont(Font.font("Microsoft YaHei", 16));
            recommendationCardsPane.getChildren().add(emptyLabel);
            return;
        }
        
        for (RecommendationItem item : recommendations) {
            VBox card = createBookCard(item);
            recommendationCardsPane.getChildren().add(card);
        }
    }
    
    private VBox createBookCard(RecommendationItem item) {
        // 主卡片容器
        VBox card = new VBox(0);
        card.setPrefWidth(220);
        card.setPrefHeight(380);
        card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); " +
                     "-fx-background-radius: 20; " +
                     "-fx-border-color: rgba(102, 126, 234, 0.3); " +
                     "-fx-border-width: 2; " +
                     "-fx-border-radius: 20;");
        
        // 添加高级阴影效果
        DropShadow shadow = new DropShadow();
        shadow.setRadius(20);
        shadow.setOffsetX(0);
        shadow.setOffsetY(8);
        shadow.setColor(Color.color(0, 0, 0, 0.25));
        card.setEffect(shadow);
        
        // 鼠标悬停效果
        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: rgba(255, 255, 255, 1.0); " +
                         "-fx-background-radius: 20; " +
                         "-fx-border-color: rgba(102, 126, 234, 0.6); " +
                         "-fx-border-width: 2; " +
                         "-fx-border-radius: 20;");
            DropShadow hoverShadow = new DropShadow();
            hoverShadow.setRadius(25);
            hoverShadow.setOffsetX(0);
            hoverShadow.setOffsetY(10);
            hoverShadow.setColor(Color.color(102.0/255, 126.0/255, 234.0/255, 0.4));
            card.setEffect(hoverShadow);
            card.setTranslateY(-5);
        });
        
        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); " +
                         "-fx-background-radius: 20; " +
                         "-fx-border-color: rgba(102, 126, 234, 0.3); " +
                         "-fx-border-width: 2; " +
                         "-fx-border-radius: 20;");
            card.setEffect(shadow);
            card.setTranslateY(0);
        });
        
        // 封面区域（带渐变背景）
        StackPane coverPane = new StackPane();
        coverPane.setPrefHeight(260);
        coverPane.setStyle("-fx-background-radius: 20 20 0 0;");
        
        // 渐变背景 - 使用纯色（JavaFX Region不支持CSS渐变，使用纯色代替）
        String gradientColor = getCategoryColor(item.getCategory());
        Region coverBackground = new Region();
        coverBackground.setStyle("-fx-background-color: " + gradientColor + "; " +
                               "-fx-background-radius: 18 18 0 0;");
        coverBackground.setPrefHeight(260);
        
        // 封面内容区域
        VBox coverContent = new VBox(8);
        coverContent.setAlignment(Pos.CENTER);
        coverContent.setPadding(new Insets(20));
        
        // 书籍图标（使用文字代替）
        Label bookIcon = new Label("📚");
        bookIcon.setFont(Font.font(60));
        bookIcon.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.9);");
        
        // 书名（在封面上显示）
        Label titleOnCover = new Label(item.getTitle());
        titleOnCover.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        titleOnCover.setTextFill(Color.WHITE);
        titleOnCover.setWrapText(true);
        titleOnCover.setTextAlignment(TextAlignment.CENTER);
        titleOnCover.setMaxWidth(180);
        titleOnCover.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 3, 0, 0, 1);");
        
        coverContent.getChildren().addAll(bookIcon, titleOnCover);
        
        coverPane.getChildren().addAll(coverBackground, coverContent);
        
        // 添加Tooltip到封面区域（显示推荐理由）
        Tooltip tooltip = new Tooltip("推荐理由：\n" + item.getReason());
        tooltip.setStyle("-fx-font-size: 13px; " +
                        "-fx-font-family: 'Microsoft YaHei'; " +
                        "-fx-background-color: rgba(50, 50, 50, 0.95); " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 10px; " +
                        "-fx-background-radius: 8;");
        tooltip.setShowDelay(Duration.millis(300));
        tooltip.setHideDelay(Duration.millis(100));
        Tooltip.install(coverPane, tooltip);
        
        // 信息区域
        VBox infoPane = new VBox(8);
        infoPane.setPadding(new Insets(15));
        infoPane.setStyle("-fx-background-color: rgba(255, 255, 255, 1.0); " +
                         "-fx-background-radius: 0 0 18 18;");
        
        // 书名（信息区域）
        Label titleLabel = new Label(item.getTitle());
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(190);
        titleLabel.setStyle("-fx-text-fill: #2c3e50;");
        
        // 作者
        Label authorLabel = new Label(item.getAuthor());
        authorLabel.setFont(Font.font("Microsoft YaHei", 13));
        authorLabel.setStyle("-fx-text-fill: #7f8c8d;");
        authorLabel.setWrapText(true);
        authorLabel.setMaxWidth(190);
        
        // 分类和推荐度
        HBox metaBox = new HBox(8);
        metaBox.setAlignment(Pos.CENTER_LEFT);
        
        // 分类标签
        Label categoryLabel = new Label(item.getCategory());
        categoryLabel.setFont(Font.font("Microsoft YaHei", 11));
        categoryLabel.setStyle("-fx-background-color: " + gradientColor + "; " +
                             "-fx-text-fill: white; " +
                             "-fx-background-radius: 12; " +
                             "-fx-padding: 4 12 4 12;");
        
        // 推荐度标签（范围0-10）
        Double score = item.getScore();
        if (score == null || score <= 0) {
            score = 0.0;
        }
        Label scoreLabel = new Label(String.format("推荐度: %.1f/10", score));
        scoreLabel.setFont(Font.font("Microsoft YaHei", 10));
        // 根据推荐度设置颜色：高推荐度（>=7）用绿色，中等（>=4）用橙色，低推荐度用灰色
        if (score >= 7.0) {
            scoreLabel.setStyle("-fx-text-fill: #27ae60;"); // 绿色
        } else if (score >= 4.0) {
            scoreLabel.setStyle("-fx-text-fill: #f39c12;"); // 橙色
        } else {
            scoreLabel.setStyle("-fx-text-fill: #95a5a6;"); // 灰色
        }
        
        metaBox.getChildren().addAll(categoryLabel, scoreLabel);
        
        // 借阅按钮
        Button borrowButton = new Button(item.getAvailableCount() > 0 ? "立即借阅" : "暂无库存");
        borrowButton.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        borrowButton.setPrefWidth(190);
        borrowButton.setPrefHeight(40);
        if (item.getAvailableCount() > 0) {
            borrowButton.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2); " +
                                 "-fx-text-fill: white; " +
                                 "-fx-background-radius: 10; " +
                                 "-fx-cursor: hand;");
            borrowButton.setOnAction(e -> borrowBook(item));
            
            // 按钮悬停效果
            borrowButton.setOnMouseEntered(e -> {
                borrowButton.setStyle("-fx-background-color: linear-gradient(to right, #5568d3, #6a3f8f); " +
                                     "-fx-text-fill: white; " +
                                     "-fx-background-radius: 10; " +
                                     "-fx-cursor: hand; " +
                                     "-fx-effect: dropshadow(three-pass-box, rgba(102, 126, 234, 0.5), 8, 0, 0, 0);");
            });
            borrowButton.setOnMouseExited(e -> {
                borrowButton.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2); " +
                                     "-fx-text-fill: white; " +
                                     "-fx-background-radius: 10; " +
                                     "-fx-cursor: hand;");
            });
        } else {
            borrowButton.setStyle("-fx-background-color: #ecf0f1; " +
                                 "-fx-text-fill: #95a5a6; " +
                                 "-fx-background-radius: 10;");
            borrowButton.setDisable(true);
        }
        
        infoPane.getChildren().addAll(titleLabel, authorLabel, metaBox, borrowButton);
        
        card.getChildren().addAll(coverPane, infoPane);
        
        return card;
    }
    
    private String getCategoryColor(String category) {
        if (category == null) return "#667eea";
        switch (category) {
            case "心理学": return "#2ecc71";
            case "历史": return "#3498db";
            case "文学": return "#e74c3c";
            case "科幻小说": return "#9b59b6";
            case "计算机": return "#667eea";
            case "经济管理": return "#f39c12";
            case "艺术": return "#e91e63";
            case "教育": return "#00bcd4";
            default: return "#667eea";
        }
    }
    
    private void borrowBook(RecommendationItem item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认借阅");
        confirm.setHeaderText("确定要借阅这本图书吗？");
        confirm.setContentText("书名: " + item.getTitle());
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.BORROW_BOOK);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode()
                        .put("bookId", item.getBookId()));
                
                Response response = client.send(request);
                if (response.isSuccess()) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("借阅成功");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("图书借阅成功！");
                    successAlert.showAndWait();
                    
                    loadRecommendations();
                    loadFineInfo();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("借阅失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("借阅失败: " + response.getMessage());
                    errorAlert.showAndWait();
                }
            } catch (Exception e) {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("借阅失败");
                errorAlert.setHeaderText(null);
                errorAlert.setContentText("借阅失败: " + e.getMessage());
                errorAlert.showAndWait();
            }
        }
    }
    
    public void cleanup() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }
    
    // 推荐数据模型
    public static class RecommendationItem {
        private Long bookId;
        private String title;
        private String author;
        private String category;
        private Integer availableCount;
        private String reason;
        private Double score;
        
        public Long getBookId() { return bookId; }
        public void setBookId(Long bookId) { this.bookId = bookId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public Integer getAvailableCount() { return availableCount; }
        public void setAvailableCount(Integer availableCount) { this.availableCount = availableCount; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }
}

