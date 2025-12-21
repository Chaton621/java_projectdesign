package com.library.client.view;

import com.library.client.ClientApp;
import com.library.client.model.Session;
import com.library.client.net.SocketClient;
import com.library.common.protocol.OpCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AdminDashboardView {
    private final ClientApp app;
    private final SocketClient client;
    private final AdminHomeView adminHomeView;
    private final Session session;
    private MediaPlayer mediaPlayer;
    private Label timeLabel;
    private Label welcomeLabel;
    private Label greetingLabel;  // 欢迎界面的大时间标签
    private Label totalBorrowedLabel;
    private Label categoryLabel;  // 分类统计标签
    private PieChart categoryChart;
    private LineChart<String, Number> trendChart;
    private ScheduledExecutorService scheduler;

    public AdminDashboardView(ClientApp app, SocketClient client, AdminHomeView adminHomeView, Session session) {
        this.app = app;
        this.client = client;
        this.adminHomeView = adminHomeView;
        this.session = session;
    }

    public Scene createScene() {
        StackPane backgroundPane = new StackPane();

        boolean videoLoaded = false;
        try {
            URL videoUrl = getClass().getResource("/images/admin-background.mp4");
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

        HBox topBox = new HBox(15);
        topBox.setPadding(new Insets(20, 30, 20, 30));
        topBox.getStyleClass().add("top-bar");
        topBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("图书馆管理系统 - 管理员仪表盘");
        titleLabel.getStyleClass().add("top-title");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));

        HBox rightBox = new HBox(15);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.setSpacing(15);

        String username = session.getUsername();
        welcomeLabel = new Label("管理员: " + username);
        welcomeLabel.getStyleClass().add("top-user-info");
        welcomeLabel.setFont(Font.font("Microsoft YaHei", 16));

        timeLabel = new Label();
        timeLabel.getStyleClass().add("top-user-info");
        timeLabel.setFont(Font.font("Microsoft YaHei", 14));
        updateTime();

        Button logoutButton = new Button("登出");
        logoutButton.getStyleClass().add("logout-button");
        logoutButton.setOnAction(e -> {
            session.logout();
            app.showLoginView();
        });

        rightBox.getChildren().addAll(welcomeLabel, timeLabel, logoutButton);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBox.getChildren().addAll(titleLabel, spacer, rightBox);
        root.setTop(topBox);

        VBox centerBox = new VBox(20);
        centerBox.setPadding(new Insets(30));
        centerBox.setAlignment(Pos.CENTER);

        HBox welcomeBox = new HBox();
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.setPadding(new Insets(20, 0, 30, 0));

        // 将 greetingLabel 作为成员变量，以便后续更新
        greetingLabel = new Label();
        greetingLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        greetingLabel.setTextFill(Color.WHITE);
        greetingLabel.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 5, 0, 0, 0);");
        updateGreeting();  // 初始化欢迎信息
        welcomeBox.getChildren().add(greetingLabel);

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(20);
        statsGrid.setVgap(20);
        statsGrid.setAlignment(Pos.CENTER);
        statsGrid.setPadding(new Insets(20));

        VBox totalBorrowedBox = createStatCard("已借阅图书", "0", "本");
        totalBorrowedLabel = (Label) ((HBox) totalBorrowedBox.getChildren().get(1)).getChildren().get(0);

        VBox categoryBox = createStatCard("图书分类", "0", "种");
        categoryLabel = (Label) ((HBox) categoryBox.getChildren().get(1)).getChildren().get(0);

        statsGrid.add(totalBorrowedBox, 0, 0);
        statsGrid.add(categoryBox, 1, 0);

        HBox chartsBox = new HBox(30);
        chartsBox.setAlignment(Pos.CENTER);
        chartsBox.setPadding(new Insets(20));

        VBox categoryChartBox = new VBox(10);
        categoryChartBox.setAlignment(Pos.CENTER);
        categoryChartBox.setPadding(new Insets(15));
        categoryChartBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); " +
                                 "-fx-background-radius: 15; " +
                                 "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0);");
        Label categoryTitle = new Label("图书分类分布");
        categoryTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        categoryTitle.setTextFill(Color.web("#2c3e50"));
        categoryChart = new PieChart();
        categoryChart.setPrefSize(400, 300);
        categoryChart.setStyle("-fx-background-color: rgba(255, 255, 255, 1.0); " +
                             "-fx-background-radius: 10;");
        categoryChart.setLabelLineLength(10);
        categoryChart.setStartAngle(90);
        categoryChartBox.getChildren().addAll(categoryTitle, categoryChart);

        VBox trendChartBox = new VBox(10);
        trendChartBox.setAlignment(Pos.CENTER);
        trendChartBox.setPadding(new Insets(15));
        trendChartBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); " +
                              "-fx-background-radius: 15; " +
                              "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0);");
        Label trendTitle = new Label("借阅趋势（最近7天）");
        trendTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        trendTitle.setTextFill(Color.web("#2c3e50"));

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("借阅数量");
        yAxis.setAutoRanging(true);
        yAxis.setTickLabelFont(Font.font("Microsoft YaHei", 11));
        yAxis.setTickLabelFill(Color.web("#34495e"));
        
        javafx.scene.chart.CategoryAxis xAxis = new javafx.scene.chart.CategoryAxis();
        xAxis.setLabel("日期");
        xAxis.setTickLabelFont(Font.font("Microsoft YaHei", 11));
        xAxis.setTickLabelFill(Color.web("#34495e"));
        xAxis.setTickLabelRotation(-45);  // 旋转标签45度，避免拥挤
        xAxis.setAnimated(false);  // 禁用动画，提高性能

        trendChart = new LineChart<>(xAxis, yAxis);
        trendChart.setPrefSize(600, 300);
        trendChart.setStyle("-fx-background-color: rgba(255, 255, 255, 1.0); " +
                           "-fx-background-radius: 10;");
        trendChart.setTitle("");
        trendChart.setAnimated(false);  // 禁用动画
        trendChart.setLegendVisible(false);  // 隐藏图例（只有一个系列）
        trendChartBox.getChildren().addAll(trendTitle, trendChart);

        chartsBox.getChildren().addAll(categoryChartBox, trendChartBox);

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));
        
        Button enterSystemButton = new Button("进入管理系统");
        enterSystemButton.getStyleClass().add("action-button");
        enterSystemButton.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        enterSystemButton.setPrefSize(250, 60);
        enterSystemButton.setStyle("-fx-background-color: #667eea; -fx-text-fill: white; -fx-background-radius: 10;");
        enterSystemButton.setOnMouseEntered(e -> enterSystemButton.setStyle(
            "-fx-background-color: #5568d3; -fx-text-fill: white; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(102, 126, 234, 0.6), 10, 0, 0, 0);"));
        enterSystemButton.setOnMouseExited(e -> enterSystemButton.setStyle(
            "-fx-background-color: #667eea; -fx-text-fill: white; -fx-background-radius: 10;"));
        enterSystemButton.setOnAction(e -> {
            cleanup();
            Scene adminScene = adminHomeView.createScene();
            app.getPrimaryStage().setScene(adminScene);
        });
        
        Button chatButton = new Button("聊天交友");
        chatButton.getStyleClass().add("action-button");
        chatButton.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        chatButton.setPrefSize(250, 60);
        chatButton.setStyle("-fx-background-color: #764ba2; -fx-text-fill: white; -fx-background-radius: 10;");
        chatButton.setOnMouseEntered(e -> chatButton.setStyle(
            "-fx-background-color: #653a8a; -fx-text-fill: white; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(118, 75, 162, 0.6), 10, 0, 0, 0);"));
        chatButton.setOnMouseExited(e -> chatButton.setStyle(
            "-fx-background-color: #764ba2; -fx-text-fill: white; -fx-background-radius: 10;"));
        chatButton.setOnAction(e -> {
            cleanup();
            app.showChatView();
        });
        
        buttonBox.getChildren().addAll(enterSystemButton, chatButton);

        centerBox.getChildren().addAll(welcomeBox, statsGrid, chartsBox, buttonBox);

        root.setCenter(centerBox);

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
        scheduler.scheduleAtFixedRate(this::loadStatistics, 0, 5, TimeUnit.MINUTES);

        Platform.runLater(this::loadStatistics);

        return scene;
    }

    private VBox createStatCard(String title, String value, String unit) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9); -fx-background-radius: 10; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 10, 0, 0, 0);");
        card.setPrefWidth(200);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft YaHei", 14));
        titleLabel.setTextFill(Color.GRAY);

        HBox valueBox = new HBox(5);
        valueBox.setAlignment(Pos.CENTER);

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 32));
        valueLabel.setTextFill(Color.web("#667eea"));

        Label unitLabel = new Label(unit);
        unitLabel.setFont(Font.font("Microsoft YaHei", 16));
        unitLabel.setTextFill(Color.GRAY);

        valueBox.getChildren().addAll(valueLabel, unitLabel);
        card.getChildren().addAll(titleLabel, valueBox);

        return card;
    }

    private void updateTime() {
        Platform.runLater(() -> {
            LocalDateTime now = LocalDateTime.now();
            String timeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            if (timeLabel != null) {
                timeLabel.setText(timeStr);
            }
            // 同时更新欢迎界面的大时间显示
            updateGreeting();
        });
    }
    
    /**
     * 更新欢迎界面的时间显示
     */
    private void updateGreeting() {
        if (greetingLabel != null && session != null) {
            String username = session.getUsername();
            LocalDateTime now = LocalDateTime.now();
            String greetingText = "你好，管理员 " + username + "，现在是 " + 
                now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分"));
            greetingLabel.setText(greetingText);
        }
    }

    private void loadStatistics() {
        new Thread(() -> {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.ADMIN_STATISTICS);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode());

                Response response = client.send(request);

                if (response == null) {
                    System.err.println("加载统计数据失败: 响应为null");
                    Platform.runLater(() -> {
                        if (totalBorrowedLabel != null) {
                            totalBorrowedLabel.setText("0");
                        }
                        if (categoryLabel != null) {
                            categoryLabel.setText("0");
                        }
                        if (categoryChart != null) {
                            categoryChart.setData(FXCollections.observableArrayList());
                        }
                        if (trendChart != null) {
                            trendChart.getData().clear();
                        }
                    });
                    return;
                }

                if (!response.isSuccess()) {
                    System.err.println("加载统计数据失败: " + response.getMessage());
                    Platform.runLater(() -> {
                        if (totalBorrowedLabel != null) {
                            totalBorrowedLabel.setText("0");
                        }
                        if (categoryLabel != null) {
                            categoryLabel.setText("0");
                        }
                        if (categoryChart != null) {
                            categoryChart.setData(FXCollections.observableArrayList());
                        }
                        if (trendChart != null) {
                            trendChart.getData().clear();
                        }
                    });
                    return;
                }

                if (response.getData() == null) {
                    System.err.println("加载统计数据失败: 响应数据为null");
                    Platform.runLater(() -> {
                        if (totalBorrowedLabel != null) {
                            totalBorrowedLabel.setText("0");
                        }
                        if (categoryLabel != null) {
                            categoryLabel.setText("0");
                        }
                        if (categoryChart != null) {
                            categoryChart.setData(FXCollections.observableArrayList());
                        }
                        if (trendChart != null) {
                            trendChart.getData().clear();
                        }
                    });
                    return;
                }

                JsonNode data = response.getData();

                int totalBorrowed = data.has("totalBorrowed") ? data.get("totalBorrowed").asInt() : 0;
                int totalCategories = data.has("totalCategories") ? data.get("totalCategories").asInt() : 0;

                Platform.runLater(() -> {
                    if (totalBorrowedLabel != null) {
                        totalBorrowedLabel.setText(String.valueOf(totalBorrowed));
                    }
                    if (categoryLabel != null) {
                        categoryLabel.setText(String.valueOf(totalCategories));
                    }
                });

                    if (data.has("categoryStats")) {
                        JsonNode categoryStats = data.get("categoryStats");
                        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
                        
                        // 存储每个分类的详细信息，用于后续创建 Tooltip
                        Map<String, Integer> categoryCountMap = new HashMap<>();

                        // 先计算总数并收集数据
                        int totalCount = 0;
                        if (categoryStats.isArray()) {
                            for (JsonNode item : categoryStats) {
                                String category = item.has("category") ? item.get("category").asText() : "未知";
                                int count = item.has("count") ? item.get("count").asInt() : 0;
                                if (count > 0) {
                                    totalCount += count;
                                    categoryCountMap.put(category, count);
                                    pieData.add(new PieChart.Data(category, count));
                                }
                            }
                        }

                        // 在 UI 线程中设置数据并添加 Tooltip
                        final int finalTotalCount = totalCount;
                        Platform.runLater(() -> {
                            categoryChart.setData(pieData);
                            
                            // 为每个数据项设置Tooltip（使用节点属性监听器确保节点创建后添加）
                            for (PieChart.Data pieDataItem : pieData) {
                                String category = pieDataItem.getName();
                                Integer count = categoryCountMap.get(category);
                                
                                if (count != null) {
                                    // 计算比例
                                    double percentage = finalTotalCount > 0 ? (count * 100.0 / finalTotalCount) : 0;
                                    
                                    // 创建 Tooltip
                                    String tooltipText = String.format(
                                        "分类: %s\n数量: %d 本\n比例: %.2f%%",
                                        category, count, percentage
                                    );
                                    Tooltip tooltip = new Tooltip(tooltipText);
                                    tooltip.setStyle("-fx-font-size: 12px; -fx-font-family: 'Microsoft YaHei';");
                                    tooltip.setShowDelay(javafx.util.Duration.millis(200));
                                    
                                    // 使用节点属性监听器，确保节点创建后添加Tooltip
                                    pieDataItem.nodeProperty().addListener((obs, oldNode, newNode) -> {
                                        if (newNode != null) {
                                            Tooltip.install(newNode, tooltip);
                                            
                                            // 添加鼠标悬停效果
                                            newNode.setOnMouseEntered(mouseEvent -> {
                                                newNode.setStyle("-fx-opacity: 0.8;");
                                            });
                                            newNode.setOnMouseExited(mouseEvent -> {
                                                newNode.setStyle("-fx-opacity: 1.0;");
                                            });
                                        }
                                    });
                                    
                                    // 节点存在时安装Tooltip
                                    javafx.scene.Node node = pieDataItem.getNode();
                                    if (node != null) {
                                        Tooltip.install(node, tooltip);
                                        node.setOnMouseEntered(mouseEvent -> {
                                            node.setStyle("-fx-opacity: 0.8;");
                                        });
                                        node.setOnMouseExited(mouseEvent -> {
                                            node.setStyle("-fx-opacity: 1.0;");
                                        });
                                    }
                                }
                            }
                        });
                    }

                    if (data.has("trendData")) {
                        JsonNode trendData = data.get("trendData");
                        XYChart.Series<String, Number> series = new XYChart.Series<>();
                        series.setName("借阅数量");
                        
                        // 存储日期和数量的映射，用于创建 Tooltip
                        Map<String, Integer> dateCountMap = new HashMap<>();

                        if (trendData.isArray()) {
                            for (JsonNode item : trendData) {
                                String date = item.has("date") ? item.get("date").asText() : "";
                                int count = item.has("count") ? item.get("count").asInt() : 0;
                                XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(date, count);
                                series.getData().add(dataPoint);
                                dateCountMap.put(date, count);
                            }
                        }

                        Platform.runLater(() -> {
                            trendChart.getData().clear();
                            trendChart.getData().add(series);
                            
                            // 为每个数据点添加 Tooltip
                            for (XYChart.Data<String, Number> dataPoint : series.getData()) {
                                String date = dataPoint.getXValue();
                                Number count = dataPoint.getYValue();
                                
                                if (date != null && count != null) {
                                    // 创建 Tooltip
                                    String tooltipText = String.format(
                                        "日期: %s\n借阅数量: %d 本",
                                        date, count.intValue()
                                    );
                                    Tooltip tooltip = new Tooltip(tooltipText);
                                    tooltip.setStyle("-fx-font-size: 12px; -fx-font-family: 'Microsoft YaHei';");
                                    tooltip.setShowDelay(javafx.util.Duration.millis(200));
                                    
                                    // 使用节点属性监听器，确保节点创建后添加Tooltip
                                    dataPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
                                        if (newNode != null) {
                                            Tooltip.install(newNode, tooltip);
                                            
                                            // 添加鼠标悬停效果
                                            newNode.setOnMouseEntered(mouseEvent -> {
                                                newNode.setStyle("-fx-opacity: 0.8;");
                                            });
                                            newNode.setOnMouseExited(mouseEvent -> {
                                                newNode.setStyle("-fx-opacity: 1.0;");
                                            });
                                        }
                                    });
                                    
                                    // 节点存在时安装Tooltip
                                    javafx.scene.Node node = dataPoint.getNode();
                                    if (node != null) {
                                        Tooltip.install(node, tooltip);
                                        node.setOnMouseEntered(mouseEvent -> {
                                            node.setStyle("-fx-opacity: 0.8;");
                                        });
                                        node.setOnMouseExited(mouseEvent -> {
                                            node.setStyle("-fx-opacity: 1.0;");
                                        });
                                    }
                                }
                            }
                            
                            // 为线条上的每个点也添加Tooltip（通过监听整个系列）
                            series.nodeProperty().addListener((obs, oldNode, newNode) -> {
                                if (newNode != null) {
                                    // 线条节点已创建
                                }
                            });
                        });
                    }
            } catch (Exception e) {
                System.err.println("加载统计数据失败: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (totalBorrowedLabel != null) {
                        totalBorrowedLabel.setText("0");
                    }
                    if (categoryLabel != null) {
                        categoryLabel.setText("0");
                    }
                    if (categoryChart != null) {
                        categoryChart.setData(FXCollections.observableArrayList());
                    }
                    if (trendChart != null) {
                        trendChart.getData().clear();
                    }
                });
            }
        }).start();
    }

    public void cleanup() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}





