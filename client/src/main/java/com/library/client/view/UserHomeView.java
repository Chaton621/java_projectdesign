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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import java.net.URL;

/**
 * 读者主界面
 * 搜索图书、借书、还书、查看借阅记录、查看欠费、热门榜单
 */
public class UserHomeView {
    private final ClientApp app;
    private final SocketClient client;
    private final Session session;
    private TableView<BookItem> bookTable;
    private TableView<RecordItem> recordsTable;
    private TableView<RecommendationItem> recommendationTable;
    private ComboBox<String> recordsStatusFilter;
    private ComboBox<String> categoryFilter;
    private TextField searchField;
    private Label statusLabel;
    private Label fineLabel;
    private Label overdueWarningLabel;
    private MediaPlayer mediaPlayer;
    private ObservableList<RecordItem> selectedRecords;
    
    public UserHomeView(ClientApp app, SocketClient client, Session session) {
        this.app = app;
        this.client = client;
        this.session = session;
    }
    
    public Scene createScene() {
        // 使用StackPane以支持视频背景
        StackPane backgroundPane = new StackPane();
        
        // 尝试加载视频背景
        boolean videoLoaded = false;
        try {
            URL videoUrl = getClass().getResource("/images/user-background.mp4");
            if (videoUrl == null) {
                videoUrl = getClass().getResource("/images/admin-background.mp4");
            }
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
        
        // 主内容面板
        BorderPane root = new BorderPane();
        
        // 顶部栏
        HBox topBox = new HBox(15);
        topBox.setPadding(new Insets(15, 20, 15, 20));
        topBox.getStyleClass().add("top-bar");
        topBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("图书馆管理系统 - 读者界面");
        titleLabel.getStyleClass().add("top-title");
        
        // 返回主页按钮
        Button homeButton = new Button("返回主页");
        homeButton.getStyleClass().add("action-button");
        homeButton.setOnAction(e -> {
            app.showHomeView();
        });
        
        HBox rightBox = new HBox(15);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.setSpacing(15);
        
        Label userLabel = new Label("用户: " + session.getUsername());
        userLabel.getStyleClass().add("top-user-info");
        
        fineLabel = new Label("欠费: 加载中...");
        fineLabel.getStyleClass().add("top-user-info");
        
        overdueWarningLabel = new Label();
        overdueWarningLabel.getStyleClass().add("top-user-info");
        overdueWarningLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        
        Button logoutButton = new Button("登出");
        logoutButton.getStyleClass().add("logout-button");
        logoutButton.setOnAction(e -> {
            session.logout();
            app.showLoginView();
        });
        
        rightBox.getChildren().addAll(homeButton, overdueWarningLabel, userLabel, fineLabel, logoutButton);
        
        // 使用Region填充中间空间
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        topBox.getChildren().addAll(titleLabel, spacer, rightBox);
        root.setTop(topBox);
        
        // 中间：标签页
        TabPane tabPane = new TabPane();
        
        Tab searchTab = new Tab("搜索图书");
        searchTab.setContent(createSearchTab());
        searchTab.setClosable(false);
        
        Tab recordsTab = new Tab("我的记录");
        recordsTab.setContent(createRecordsTab());
        recordsTab.setClosable(false);
        
        Tab recommendTab = new Tab("推荐");
        recommendTab.setContent(createRecommendTab());
        recommendTab.setClosable(false);
        
        tabPane.getTabs().addAll(searchTab, recordsTab, recommendTab);
        root.setCenter(tabPane);
        
        // 底部状态栏
        HBox statusBar = new HBox();
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(8, 15, 8, 15));
        statusLabel = new Label("就绪");
        statusLabel.getStyleClass().add("status-label-info");
        statusBar.getChildren().add(statusLabel);
        root.setBottom(statusBar);
        
        // 将内容面板添加到背景面板
        backgroundPane.getChildren().add(root);
        
        Scene scene = new Scene(backgroundPane, 1200, 800);
        
        // 监听场景大小变化，调整视频大小
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
        
        // 加载CSS样式
        try {
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("无法加载CSS样式: " + e.getMessage());
        }
        
        // 自动加载欠费信息
        javafx.application.Platform.runLater(() -> loadFineInfo());
        
        return scene;
    }
    
    /**
     * 清理资源，停止视频播放
     */
    public void cleanup() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }
    
    private VBox createSearchTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        // 搜索栏
        HBox searchBox = new HBox(10);
        searchBox.setPadding(new Insets(0, 0, 10, 0));
        
        searchField = new TextField();
        searchField.setPromptText("输入书名、作者或ISBN搜索...");
        searchField.setPrefWidth(300);
        searchField.setOnAction(e -> searchBooks());
        
        categoryFilter = new ComboBox<>();
        categoryFilter.setPromptText("选择分类");
        categoryFilter.setPrefWidth(150);
        categoryFilter.getItems().addAll("全部", "文学", "科技", "历史", "艺术", "教育", "其他");
        categoryFilter.setValue("全部");
        
        Button searchButton = new Button("搜索");
        searchButton.getStyleClass().add("action-button");
        searchButton.setOnAction(e -> searchBooks());
        
        Button refreshButton = new Button("刷新");
        refreshButton.getStyleClass().add("action-button");
        refreshButton.setOnAction(e -> searchBooks());
        
        searchBox.getChildren().addAll(searchField, categoryFilter, searchButton, refreshButton);
        
        // 图书表格
        bookTable = new TableView<>();
        bookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<BookItem, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("bookId"));
        idCol.setPrefWidth(80);
        
        TableColumn<BookItem, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(200);
        
        TableColumn<BookItem, String> authorCol = new TableColumn<>("作者");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));
        authorCol.setPrefWidth(150);
        
        TableColumn<BookItem, String> categoryCol = new TableColumn<>("分类");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(100);
        
        TableColumn<BookItem, Integer> availableCol = new TableColumn<>("可用数量");
        availableCol.setCellValueFactory(new PropertyValueFactory<>("availableCount"));
        availableCol.setPrefWidth(100);
        
        TableColumn<BookItem, String> actionCol = new TableColumn<>("操作");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(column -> new TableCell<BookItem, String>() {
            private final Button borrowButton = new Button("借阅");
            
            {
                borrowButton.getStyleClass().add("action-button");
                borrowButton.setOnAction(e -> {
                    BookItem item = getTableView().getItems().get(getIndex());
                    borrowBook(item);
                });
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    BookItem bookItem = getTableView().getItems().get(getIndex());
                    if (bookItem.getAvailableCount() > 0) {
                        borrowButton.setText("借阅");
                        borrowButton.setDisable(false);
                    } else {
                        borrowButton.setText("无库存");
                        borrowButton.setDisable(true);
                    }
                    setGraphic(borrowButton);
                }
            }
        });
        
        bookTable.getColumns().add(idCol);
        bookTable.getColumns().add(titleCol);
        bookTable.getColumns().add(authorCol);
        bookTable.getColumns().add(categoryCol);
        bookTable.getColumns().add(availableCol);
        bookTable.getColumns().add(actionCol);
        
        vbox.getChildren().addAll(searchBox, bookTable);
        
        // 自动加载
        javafx.application.Platform.runLater(() -> searchBooks());
        
        return vbox;
    }
    
    private VBox createRecommendTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(0, 0, 10, 0));
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        
        Button refreshButton = new Button("刷新推荐");
        refreshButton.getStyleClass().add("action-button");
        refreshButton.setOnAction(e -> loadRecommendations());
        
        buttonBox.getChildren().addAll(refreshButton);
        
        // 推荐表格
        recommendationTable = new TableView<>();
        recommendationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<RecommendationItem, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(200);
        
        TableColumn<RecommendationItem, String> authorCol = new TableColumn<>("作者");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));
        authorCol.setPrefWidth(150);
        
        TableColumn<RecommendationItem, String> categoryCol = new TableColumn<>("分类");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(100);
        
        TableColumn<RecommendationItem, String> reasonCol = new TableColumn<>("推荐理由");
        reasonCol.setCellValueFactory(new PropertyValueFactory<>("reason"));
        reasonCol.setPrefWidth(300);
        
        TableColumn<RecommendationItem, String> scoreCol = new TableColumn<>("推荐度");
        scoreCol.setCellValueFactory(cellData -> {
            Double score = cellData.getValue().getScore();
            if (score == null || score <= 0) {
                return new javafx.beans.property.SimpleStringProperty("0.0");
            }
            // 推荐度范围是0-10，保留1位小数
            return new javafx.beans.property.SimpleStringProperty(String.format("%.1f", score));
        });
        scoreCol.setPrefWidth(100);
        
        TableColumn<RecommendationItem, String> actionCol = new TableColumn<>("操作");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(column -> new TableCell<RecommendationItem, String>() {
            private final Button borrowButton = new Button("借阅");
            
            {
                borrowButton.getStyleClass().add("action-button");
                borrowButton.setOnAction(e -> {
                    RecommendationItem item = getTableView().getItems().get(getIndex());
                    borrowRecommendationBook(item);
                });
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    RecommendationItem recItem = getTableView().getItems().get(getIndex());
                    if (recItem.getAvailableCount() > 0) {
                        borrowButton.setText("借阅");
                        borrowButton.setDisable(false);
                    } else {
                        borrowButton.setText("无库存");
                        borrowButton.setDisable(true);
                    }
                    setGraphic(borrowButton);
                }
            }
        });
        
        recommendationTable.getColumns().add(titleCol);
        recommendationTable.getColumns().add(authorCol);
        recommendationTable.getColumns().add(categoryCol);
        recommendationTable.getColumns().add(reasonCol);
        recommendationTable.getColumns().add(scoreCol);
        recommendationTable.getColumns().add(actionCol);
        
        vbox.getChildren().addAll(buttonBox, recommendationTable);
        
        // 自动加载推荐
        javafx.application.Platform.runLater(() -> loadRecommendations());
        
        return vbox;
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
                
                javafx.application.Platform.runLater(() -> {
                    if (response.isSuccess() && response.getData() != null) {
                        JsonNode booksNode = response.getData().get("books");
                        ObservableList<RecommendationItem> recommendations = FXCollections.observableArrayList();
                        
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
                        
                        recommendationTable.setItems(recommendations);
                        if (statusLabel != null) {
                            statusLabel.setText("为您推荐了 " + recommendations.size() + " 本图书");
                        }
                    } else {
                        recommendationTable.setItems(FXCollections.observableArrayList());
                        if (statusLabel != null) {
                            statusLabel.setText("加载推荐失败: " + (response != null ? response.getMessage() : "未知错误"));
                        }
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    recommendationTable.setItems(FXCollections.observableArrayList());
                    if (statusLabel != null) {
                        statusLabel.setText("加载推荐失败: " + e.getMessage());
                    }
                });
            }
        }).start();
    }
    
    private void borrowRecommendationBook(RecommendationItem item) {
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
                    
                    if (statusLabel != null) {
                        statusLabel.setText("借阅成功");
                    }
                    loadRecommendations();
                    loadFineInfo();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("借阅失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("借阅失败: " + response.getMessage());
                    errorAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("借阅失败: " + response.getMessage());
                    }
                }
            } catch (Exception e) {
                if (statusLabel != null) {
                    statusLabel.setText("借阅失败: " + e.getMessage());
                }
            }
        }
    }
    
    private VBox createRecordsTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(0, 0, 10, 0));
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        
        Button refreshButton = new Button("刷新");
        refreshButton.getStyleClass().add("action-button");
        refreshButton.setOnAction(e -> loadMyRecords());
        
        Button returnButton = new Button("还书");
        returnButton.getStyleClass().add("action-button");
        returnButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white;");
        returnButton.setOnAction(e -> returnSelectedBooks());
        
        Button selectAllButton = new Button("全选");
        selectAllButton.getStyleClass().add("action-button");
        selectAllButton.setOnAction(e -> selectAllRecords());
        
        Button deselectAllButton = new Button("取消全选");
        deselectAllButton.getStyleClass().add("action-button");
        deselectAllButton.setOnAction(e -> deselectAllRecords());
        
        Label selectedLabel = new Label("已选择: 0 条");
        selectedLabel.setStyle("-fx-text-fill: #666;");
        
        recordsStatusFilter = new ComboBox<>();
        recordsStatusFilter.getItems().addAll("全部", "BORROWED", "RETURNED", "OVERDUE");
        recordsStatusFilter.setValue("全部");
        recordsStatusFilter.getStyleClass().add("text-field");
        recordsStatusFilter.setOnAction(e -> loadMyRecords());
        
        buttonBox.getChildren().addAll(refreshButton, returnButton, selectAllButton, deselectAllButton, selectedLabel, 
                                       new Region(), new Label("状态筛选:"), recordsStatusFilter);
        HBox.setHgrow(buttonBox.getChildren().get(5), Priority.ALWAYS);
        
        recordsTable = new TableView<>();
        recordsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        selectedRecords = FXCollections.observableArrayList();
        
        // 复选框列
        TableColumn<RecordItem, Boolean> checkBoxCol = new TableColumn<>("");
        checkBoxCol.setPrefWidth(50);
        checkBoxCol.setCellFactory(column -> new TableCell<RecordItem, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            
            {
                checkBox.setOnAction(e -> {
                    RecordItem item = getTableView().getItems().get(getIndex());
                    if (checkBox.isSelected()) {
                        if (!selectedRecords.contains(item)) {
                            selectedRecords.add(item);
                        }
                    } else {
                        selectedRecords.remove(item);
                    }
                    selectedLabel.setText("已选择: " + selectedRecords.size() + " 条");
                });
            }
            
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    RecordItem recordItem = getTableView().getItems().get(getIndex());
                    checkBox.setSelected(selectedRecords.contains(recordItem));
                    // 只有BORROWED或OVERDUE状态的记录才能选择
                    checkBox.setDisable(!"BORROWED".equals(recordItem.getStatus()) && !"OVERDUE".equals(recordItem.getStatus()));
                    setGraphic(checkBox);
                }
            }
        });
        
        TableColumn<RecordItem, String> bookTitleCol = new TableColumn<>("书名");
        bookTitleCol.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        bookTitleCol.setPrefWidth(200);
        
        TableColumn<RecordItem, String> borrowTimeCol = new TableColumn<>("借阅时间");
        borrowTimeCol.setCellValueFactory(new PropertyValueFactory<>("borrowTime"));
        borrowTimeCol.setPrefWidth(180);
        
        TableColumn<RecordItem, String> dueTimeCol = new TableColumn<>("应还时间");
        dueTimeCol.setCellValueFactory(new PropertyValueFactory<>("dueTime"));
        dueTimeCol.setPrefWidth(180);
        
        TableColumn<RecordItem, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(column -> new TableCell<RecordItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("OVERDUE".equals(item)) {
                        setStyle("-fx-background-color: #ffcccc; -fx-text-fill: red; -fx-font-weight: bold;");
                    } else if ("BORROWED".equals(item)) {
                        setStyle("-fx-background-color: #ccffcc; -fx-text-fill: green;");
                    } else {
                        setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #666;");
                    }
                }
            }
        });
        
        TableColumn<RecordItem, String> fineCol = new TableColumn<>("欠费");
        fineCol.setCellValueFactory(new PropertyValueFactory<>("fineAmount"));
        fineCol.setPrefWidth(100);
        
        TableColumn<RecordItem, String> actionCol = new TableColumn<>("操作");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(column -> new TableCell<RecordItem, String>() {
            private final Button returnButton = new Button("还书");
            
            {
                returnButton.getStyleClass().add("action-button");
                returnButton.setOnAction(e -> {
                    RecordItem item = getTableView().getItems().get(getIndex());
                    returnBook(item);
                });
            }
            
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    RecordItem recordItem = getTableView().getItems().get(getIndex());
                    if ("BORROWED".equals(recordItem.getStatus()) || "OVERDUE".equals(recordItem.getStatus())) {
                        returnButton.setText("还书");
                        returnButton.setDisable(false);
                    } else {
                        returnButton.setText("已归还");
                        returnButton.setDisable(true);
                    }
                    setGraphic(returnButton);
                }
            }
        });
        
        recordsTable.getColumns().add(checkBoxCol);
        recordsTable.getColumns().add(bookTitleCol);
        recordsTable.getColumns().add(borrowTimeCol);
        recordsTable.getColumns().add(dueTimeCol);
        recordsTable.getColumns().add(statusCol);
        recordsTable.getColumns().add(fineCol);
        recordsTable.getColumns().add(actionCol);
        
        vbox.getChildren().addAll(buttonBox, recordsTable);
        
        // 自动加载
        javafx.application.Platform.runLater(() -> loadMyRecords());
        
        return vbox;
    }
    
    private void selectAllRecords() {
        selectedRecords.clear();
        for (RecordItem item : recordsTable.getItems()) {
            if ("BORROWED".equals(item.getStatus()) || "OVERDUE".equals(item.getStatus())) {
                selectedRecords.add(item);
            }
        }
        recordsTable.refresh();
        updateSelectedLabel();
    }
    
    private void deselectAllRecords() {
        selectedRecords.clear();
        recordsTable.refresh();
        updateSelectedLabel();
    }
    
    private void updateSelectedLabel() {
        if (statusLabel != null) {
            // 这个会在loadMyRecords中更新
        }
    }
    
    private void returnSelectedBooks() {
        if (selectedRecords.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText("请先选择要归还的图书");
            alert.showAndWait();
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认还书");
        confirm.setHeaderText("确定要归还选中的 " + selectedRecords.size() + " 本图书吗？");
        confirm.setContentText("选中的图书将全部归还");
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            int successCount = 0;
            int failCount = 0;
            
            for (RecordItem item : selectedRecords) {
                try {
                    Request request = new Request();
                    request.setRequestId(java.util.UUID.randomUUID().toString());
                    request.setOpCode(OpCode.RETURN_BOOK);
                    request.setToken(session.getToken());
                    request.setPayload(JsonUtil.createObjectNode()
                            .put("recordId", item.getRecordId()));
                    
                    Response response = client.send(request);
                    if (response.isSuccess()) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                }
            }
            
            Alert resultAlert = new Alert(Alert.AlertType.INFORMATION);
            resultAlert.setTitle("批量还书结果");
            resultAlert.setHeaderText(null);
            resultAlert.setContentText(String.format("成功归还 %d 本，失败 %d 本", successCount, failCount));
            resultAlert.showAndWait();
            
            selectedRecords.clear();
            loadMyRecords();
            loadFineInfo();
        }
    }
    
    private void searchBooks() {
        try {
            String keyword = searchField.getText();
            String category = categoryFilter.getValue();
            if ("全部".equals(category)) {
                category = null;
            }
            
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.SEARCH_BOOK);
            request.setToken(session.getToken());
            ObjectNode payload = JsonUtil.createObjectNode();
            if (keyword != null && !keyword.trim().isEmpty()) {
                payload.put("keyword", keyword);
            }
            if (category != null) {
                payload.put("category", category);
            }
            payload.put("limit", 100);
            payload.put("offset", 0);
            request.setPayload(payload);
            
            Response response = client.send(request);
            
            if (response.isSuccess() && response.getData() != null) {
                JsonNode booksNode = response.getData().get("books");
                ObservableList<BookItem> books = FXCollections.observableArrayList();
                
                if (booksNode != null && booksNode.isArray()) {
                    for (JsonNode bookNode : booksNode) {
                        BookItem item = new BookItem();
                        item.setBookId(bookNode.get("id").asLong());
                        item.setIsbn(bookNode.has("isbn") ? bookNode.get("isbn").asText() : "");
                        item.setTitle(bookNode.has("title") ? bookNode.get("title").asText() : "");
                        item.setAuthor(bookNode.has("author") ? bookNode.get("author").asText() : "");
                        item.setCategory(bookNode.has("category") ? bookNode.get("category").asText() : "");
                        item.setAvailableCount(bookNode.has("availableCount") ? bookNode.get("availableCount").asInt() : 0);
                        books.add(item);
                    }
                }
                
                bookTable.setItems(books);
                if (statusLabel != null) {
                    statusLabel.setText("找到 " + books.size() + " 本图书");
                }
            } else {
                if (statusLabel != null) {
                    statusLabel.setText("搜索失败: " + response.getMessage());
                }
            }
        } catch (Exception e) {
            if (statusLabel != null) {
                statusLabel.setText("搜索失败: " + e.getMessage());
            }
        }
    }
    
    private void borrowBook(BookItem book) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认借阅");
        confirm.setHeaderText("确定要借阅这本图书吗？");
        confirm.setContentText("书名: " + book.getTitle());
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.BORROW_BOOK);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode()
                        .put("bookId", book.getBookId()));
                
                Response response = client.send(request);
                if (response.isSuccess()) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("借阅成功");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("图书借阅成功！");
                    successAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("借阅成功");
                    }
                    searchBooks();
                    loadMyRecords();
                    loadFineInfo();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("借阅失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("借阅失败: " + response.getMessage());
                    errorAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("借阅失败: " + response.getMessage());
                    }
                }
            } catch (Exception e) {
                if (statusLabel != null) {
                    statusLabel.setText("借阅失败: " + e.getMessage());
                }
            }
        }
    }
    
    private void returnBook(RecordItem record) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认还书");
        confirm.setHeaderText("确定要归还这本图书吗？");
        confirm.setContentText("书名: " + record.getBookTitle());
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.RETURN_BOOK);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode()
                        .put("recordId", record.getRecordId()));
                
                Response response = client.send(request);
                if (response.isSuccess()) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("还书成功");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("图书归还成功！");
                    successAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("还书成功");
                    }
                    loadMyRecords();
                    loadFineInfo();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("还书失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("还书失败: " + response.getMessage());
                    errorAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("还书失败: " + response.getMessage());
                    }
                }
            } catch (Exception e) {
                if (statusLabel != null) {
                    statusLabel.setText("还书失败: " + e.getMessage());
                }
            }
        }
    }
    
    private void loadMyRecords() {
        try {
            String status = null;
            if (recordsStatusFilter != null && !"全部".equals(recordsStatusFilter.getValue())) {
                status = recordsStatusFilter.getValue();
            }
            
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.MY_RECORDS);
            request.setToken(session.getToken());
            
            ObjectNode payload = JsonUtil.createObjectNode();
            if (status != null) {
                payload.put("status", status);
            }
            request.setPayload(payload);
            
            Response response = client.send(request);
            
            if (response.isSuccess() && response.getData() != null) {
                JsonNode recordsNode = response.getData().get("records");
                ObservableList<RecordItem> records = FXCollections.observableArrayList();
                
                if (recordsNode != null && recordsNode.isArray()) {
                    for (JsonNode recordNode : recordsNode) {
                        RecordItem item = new RecordItem();
                        item.setRecordId(recordNode.get("id").asLong());
                        item.setBookTitle(recordNode.has("bookTitle") ? recordNode.get("bookTitle").asText() : "");
                        item.setBorrowTime(recordNode.has("borrowTime") ? recordNode.get("borrowTime").asText() : "");
                        item.setDueTime(recordNode.has("dueTime") ? recordNode.get("dueTime").asText() : "");
                        item.setStatus(recordNode.has("status") ? recordNode.get("status").asText() : "");
                        
                        double fine = 0.0;
                        if (recordNode.has("fineAmount")) {
                            fine += recordNode.get("fineAmount").asDouble();
                        }
                        if (recordNode.has("currentFine")) {
                            fine += recordNode.get("currentFine").asDouble();
                        }
                        item.setFineAmount(String.format("%.2f元", fine));
                        
                        records.add(item);
                    }
                }
                
                recordsTable.setItems(records);
                
                // 统计逾期数量
                int overdueCount = 0;
                for (RecordItem record : records) {
                    if ("OVERDUE".equals(record.getStatus())) {
                        overdueCount++;
                    }
                }
                
                // 更新状态栏
                if (statusLabel != null) {
                    statusLabel.setText("加载了 " + records.size() + " 条记录, 其中有 " + overdueCount + " 条逾期");
                }
                
                // 更新逾期警告
                if (overdueWarningLabel != null) {
                    if (overdueCount > 0) {
                        overdueWarningLabel.setText("⚠ 您有 " + overdueCount + " 本图书逾期, 请尽快归还!");
                    } else {
                        overdueWarningLabel.setText("");
                    }
                }
            } else {
                if (statusLabel != null) {
                    statusLabel.setText("加载失败: " + response.getMessage());
                }
            }
        } catch (Exception e) {
            if (statusLabel != null) {
                statusLabel.setText("加载失败: " + e.getMessage());
            }
        }
    }
    
    
    private void loadFineInfo() {
        try {
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.GET_USER_FINE);
            request.setToken(session.getToken());
            request.setPayload(JsonUtil.createObjectNode());
            
            Response response = client.send(request);
            
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
        } catch (Exception e) {
            if (fineLabel != null) {
                fineLabel.setText("欠费: 加载失败");
            }
        }
    }
    
    // 数据模型
    public static class BookItem {
        private Long bookId;
        private String isbn;
        private String title;
        private String author;
        private String category;
        private Integer availableCount;
        
        public Long getBookId() { return bookId; }
        public void setBookId(Long bookId) { this.bookId = bookId; }
        public String getIsbn() { return isbn; }
        public void setIsbn(String isbn) { this.isbn = isbn; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public Integer getAvailableCount() { return availableCount; }
        public void setAvailableCount(Integer availableCount) { this.availableCount = availableCount; }
    }
    
    
    public static class RecordItem {
        private Long recordId;
        private String bookTitle;
        private String borrowTime;
        private String dueTime;
        private String status;
        private String fineAmount;
        
        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
        public String getBookTitle() { return bookTitle; }
        public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
        public String getBorrowTime() { return borrowTime; }
        public void setBorrowTime(String borrowTime) { this.borrowTime = borrowTime; }
        public String getDueTime() { return dueTime; }
        public void setDueTime(String dueTime) { this.dueTime = dueTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getFineAmount() { return fineAmount; }
        public void setFineAmount(String fineAmount) { this.fineAmount = fineAmount; }
    }
    
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
