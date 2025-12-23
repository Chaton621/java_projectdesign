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
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Interpolator;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;

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
    private VBox loadingPane;
    private ProgressIndicator loadingIndicator;
    private ScrollPane cardsScrollPane;
    private SequentialTransition stepsAnimation;  // 保存步骤动画引用，用于停止
    
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
        
        fineLabel = new Label("欠费: 0.00元");
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
        
        // 加载动画容器
        loadingPane = new VBox(20);
        loadingPane.setAlignment(Pos.CENTER);
        loadingPane.setPadding(new Insets(50));
        // 添加半透明背景，确保在视频背景上可见
        loadingPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); " +
                            "-fx-background-radius: 15;");
        
        // 加载指示器
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        loadingIndicator.setPrefSize(60, 60);
        loadingIndicator.setStyle("-fx-progress-color: #667eea;");
        
        // 加载文字
        Label loadingLabel = new Label("AI正在分析你的阅读偏好...");
        loadingLabel.setFont(Font.font("Microsoft YaHei", 18));
        loadingLabel.setTextFill(Color.WHITE);
        
        // 推荐步骤展示
        VBox stepsBox = new VBox(15);
        stepsBox.setAlignment(Pos.CENTER);
        stepsBox.setPadding(new Insets(20, 0, 0, 0));
        
        Label step1 = new Label("🔍 图推荐算法：发现相似用户...");
        Label step2 = new Label("📚 语义分析：匹配图书内容...");
        Label step3 = new Label("🤖 AI智能融合：生成个性化推荐...");
        
        for (Label step : new Label[]{step1, step2, step3}) {
            step.setFont(Font.font("Microsoft YaHei", 14));
            step.setTextFill(Color.WHITE);
            step.setOpacity(0.3);  // 初始透明度更低
        }
        
        stepsBox.getChildren().addAll(step1, step2, step3);
        loadingPane.getChildren().addAll(loadingIndicator, loadingLabel, stepsBox);
        
        // 为步骤文字添加闪烁动画效果（时间再增加一倍）
        stepsAnimation = new SequentialTransition();
        for (int i = 0; i < 3; i++) {
            Label step = (Label) stepsBox.getChildren().get(i);
            FadeTransition stepFade = new FadeTransition(Duration.millis(1600), step);  // 从800ms增加到1600ms
            stepFade.setFromValue(0.3);
            stepFade.setToValue(1.0);
            stepFade.setAutoReverse(true);
            stepFade.setCycleCount(2);
            stepsAnimation.getChildren().add(stepFade);
        }
        stepsAnimation.setCycleCount(SequentialTransition.INDEFINITE);
        stepsAnimation.play();  // 持续播放步骤动画
        
        // 使用StackPane来切换加载和内容
        StackPane contentPane = new StackPane();
        
        cardsScrollPane = new ScrollPane(recommendationCardsPane);
        cardsScrollPane.setFitToWidth(true);
        cardsScrollPane.setFitToHeight(true);
        cardsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        cardsScrollPane.setPadding(new Insets(10));
        cardsScrollPane.setVisible(false);
        
        // 先添加卡片容器（底层）
        contentPane.getChildren().add(cardsScrollPane);
        // 后添加加载动画（上层，确保可见）
        contentPane.getChildren().add(loadingPane);
        
        centerBox.getChildren().addAll(welcomeLabel, recommendTitleLabel, contentPane);
        
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
        loadFineInfoWithRetry(3);
    }
    
    private void loadFineInfoWithRetry(int maxRetries) {
        new Thread(() -> {
            int retryCount = 0;
            boolean success = false;
            
            while (retryCount < maxRetries && !success) {
                try {
                    Request request = new Request();
                    request.setRequestId(java.util.UUID.randomUUID().toString());
                    request.setOpCode(OpCode.GET_USER_FINE);
                    request.setToken(session.getToken());
                    request.setPayload(JsonUtil.createObjectNode());
                    
                    Response response = client.send(request);
                    
                    if (response != null && response.isSuccess() && response.getData() != null) {
                        double totalOwedValue = 0.0;
                        try {
                            if (response.getData().has("totalOwed")) {
                                totalOwedValue = response.getData().get("totalOwed").asDouble();
                            } else {
                                double totalFine = response.getData().has("totalFine") ? 
                                        response.getData().get("totalFine").asDouble() : 0.0;
                                double currentOverdueFine = response.getData().has("currentOverdueFine") ? 
                                        response.getData().get("currentOverdueFine").asDouble() : 0.0;
                                totalOwedValue = totalFine + currentOverdueFine;
                            }
                        } catch (Exception e) {
                            totalOwedValue = 0.0;
                        }
                        
                        final double finalTotalOwed = totalOwedValue;
                        Platform.runLater(() -> {
                            if (fineLabel != null) {
                                fineLabel.setText(String.format("欠费: %.2f元", finalTotalOwed));
                            }
                        });
                        success = true;
                    } else {
                        retryCount++;
                        if (retryCount < maxRetries) {
                            Thread.sleep(500 * retryCount);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    retryCount++;
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(500 * retryCount);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            if (!success) {
                Platform.runLater(() -> {
                    if (fineLabel != null) {
                        fineLabel.setText("欠费: 0.00元");
                    }
                });
            }
        }).start();
    }
    
    private void loadRecommendations() {
        // 显示加载动画
        Platform.runLater(() -> {
            if (loadingPane != null) {
                loadingPane.setVisible(true);
                loadingPane.toFront();  // 确保在最前面
            }
            if (cardsScrollPane != null) {
                cardsScrollPane.setVisible(false);
            }
            // 重新开始步骤动画
            if (stepsAnimation != null) {
                stepsAnimation.play();
            }
        });
        
        long startTime = System.currentTimeMillis();
        final long MIN_LOADING_TIME = 3000;  // 最小显示时间3秒（再增加一倍），让用户能看到动画
        
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
                
                // 确保至少显示最小时间
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime < MIN_LOADING_TIME) {
                    Thread.sleep(MIN_LOADING_TIME - elapsedTime);
                }
                
                Platform.runLater(() -> {
                    // 停止步骤动画
                    if (stepsAnimation != null) {
                        stepsAnimation.stop();
                    }
                    // 隐藏加载动画，显示卡片
                    if (loadingPane != null) {
                        loadingPane.setVisible(false);
                    }
                    if (cardsScrollPane != null) {
                        cardsScrollPane.setVisible(true);
                    }
                    
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
                                
                                // 检查是否是AI增强推荐
                                if (bookNode.has("aiEnhanced") && bookNode.get("aiEnhanced").asBoolean()) {
                                    item.setAiEnhanced(true);
                                }
                                
                                recommendations.add(item);
                            }
                        }
                        
                        updateRecommendationCards();
                    } else {
                        recommendationCardsPane.getChildren().clear();
                        Label errorLabel = new Label("加载推荐失败: " + (response != null ? response.getMessage() : "未知错误"));
                        errorLabel.setTextFill(Color.WHITE);
                        errorLabel.setFont(Font.font("Microsoft YaHei", 16));
                        recommendationCardsPane.getChildren().add(errorLabel);
                        if (cardsScrollPane != null) {
                            cardsScrollPane.setVisible(true);
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (loadingPane != null) {
                        loadingPane.setVisible(false);
                    }
                    recommendationCardsPane.getChildren().clear();
                    Label errorLabel = new Label("加载推荐失败: " + e.getMessage());
                    errorLabel.setTextFill(Color.WHITE);
                    errorLabel.setFont(Font.font("Microsoft YaHei", 16));
                    recommendationCardsPane.getChildren().add(errorLabel);
                    if (cardsScrollPane != null) {
                        cardsScrollPane.setVisible(true);
                    }
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
            recommendationCardsPane.setVisible(true);
            return;
        }
        
        // 显示卡片容器
        recommendationCardsPane.setVisible(true);
        
        // 添加卡片，带淡入动画
        SequentialTransition sequentialTransition = new SequentialTransition();
        
        // 动画参数
        final long CARD_ANIMATION_DURATION = 600;  // 每个卡片动画600ms
        final long CARD_DELAY_BETWEEN = 120;  // 卡片之间延迟120ms
        final double INITIAL_SCALE = 0.7;  // 初始缩放0.7
        
        for (int i = 0; i < recommendations.size(); i++) {
            RecommendationItem item = recommendations.get(i);
            VBox card = createBookCard(item);
            card.setOpacity(0);
            card.setScaleX(INITIAL_SCALE);
            card.setScaleY(INITIAL_SCALE);
            recommendationCardsPane.getChildren().add(card);
            
            // 为每个卡片创建淡入动画（使用缓动效果）
            FadeTransition fadeIn = new FadeTransition(Duration.millis(CARD_ANIMATION_DURATION), card);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);  // 添加缓动效果
            
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(CARD_ANIMATION_DURATION), card);
            scaleIn.setFromX(INITIAL_SCALE);
            scaleIn.setFromY(INITIAL_SCALE);
            scaleIn.setToX(1.0);
            scaleIn.setToY(1.0);
            scaleIn.setInterpolator(Interpolator.EASE_OUT);  // 添加缓动效果
            
            ParallelTransition cardAnimation = new ParallelTransition(fadeIn, scaleIn);
            
            // 延迟显示，创建依次出现的效果
            if (i == 0) {
                sequentialTransition.getChildren().add(cardAnimation);
            } else {
                SequentialTransition delayAndShow = new SequentialTransition();
                delayAndShow.getChildren().add(new PauseTransition(Duration.millis(CARD_DELAY_BETWEEN * i)));
                delayAndShow.getChildren().add(cardAnimation);
                sequentialTransition.getChildren().add(delayAndShow);
            }
        }
        
        // 播放动画
        sequentialTransition.play();
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
        
        // 推荐度可视化（进度条样式）
        Double score = item.getScore();
        if (score == null || score <= 0) {
            score = 0.0;
        }
        
        // 推荐度标签和进度条
        VBox scoreBox = new VBox(4);
        scoreBox.setPrefWidth(100);
        
        Label scoreLabel = new Label(String.format("%.1f/10", score));
        scoreLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        
        // 推荐度进度条
        ProgressBar scoreBar = new ProgressBar(score / 10.0);
        scoreBar.setPrefWidth(100);
        scoreBar.setPrefHeight(8);
        scoreBar.setStyle("-fx-accent: " + getScoreColor(score) + "; " +
                         "-fx-background-color: rgba(0, 0, 0, 0.1); " +
                         "-fx-background-radius: 4; " +
                         "-fx-border-radius: 4;");
        
        // 根据推荐度设置颜色
        if (score >= 7.0) {
            scoreLabel.setStyle("-fx-text-fill: #27ae60;"); // 绿色
        } else if (score >= 4.0) {
            scoreLabel.setStyle("-fx-text-fill: #f39c12;"); // 橙色
        } else {
            scoreLabel.setStyle("-fx-text-fill: #95a5a6;"); // 灰色
        }
        
        scoreBox.getChildren().addAll(scoreLabel, scoreBar);
        
        // AI推荐标签
        HBox tagBox = new HBox(5);
        tagBox.setAlignment(Pos.CENTER_LEFT);
        
        if (item.getAiEnhanced() != null && item.getAiEnhanced()) {
            Label aiTag = new Label("🤖 AI推荐");
            aiTag.setFont(Font.font("Microsoft YaHei", 9));
            aiTag.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2); " +
                          "-fx-text-fill: white; " +
                          "-fx-background-radius: 10; " +
                          "-fx-padding: 2 8 2 8;");
            tagBox.getChildren().add(aiTag);
        }
        
        metaBox.getChildren().addAll(categoryLabel, scoreBox);
        
        // 推荐来源标签
        if (tagBox.getChildren().size() > 0) {
            infoPane.getChildren().add(tagBox);
        }
        
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
        
        // 重新组织infoPane的子元素
        infoPane.getChildren().clear();
        infoPane.getChildren().addAll(titleLabel, authorLabel, metaBox);
        if (tagBox.getChildren().size() > 0) {
            infoPane.getChildren().add(tagBox);
        }
        infoPane.getChildren().add(borrowButton);
        
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
    
    private String getScoreColor(double score) {
        if (score >= 7.0) {
            return "#27ae60"; // 绿色
        } else if (score >= 4.0) {
            return "#f39c12"; // 橙色
        } else {
            return "#95a5a6"; // 灰色
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
        if (stepsAnimation != null) {
            stepsAnimation.stop();
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
        private Boolean aiEnhanced;
        
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
        public Boolean getAiEnhanced() { return aiEnhanced; }
        public void setAiEnhanced(Boolean aiEnhanced) { this.aiEnhanced = aiEnhanced; }
    }
}







