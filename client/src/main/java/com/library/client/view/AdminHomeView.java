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
import javafx.stage.FileChooser;
import java.net.URL;

import java.io.File;
import java.nio.file.Files;

/**
 * 管理员主界面
 * 添加/修改/删除书、导入CSV、冻结/解冻用户、查看借阅记录、热门榜单
 */
public class AdminHomeView {
    private final ClientApp app;
    private final SocketClient client;
    private final Session session;
    private TableView<BookItem> bookTable;
    private TableView<TrendingItem> trendingTable;
    private TableView<RecordItem> recordsTable;
    private TableView<UserItem> usersTable;
    private TableView<FineItem> fineTable;
    private TableView<FineRateConfigItem> fineRateConfigTable;
    private ComboBox<String> recordsStatusFilter;
    private ComboBox<String> usersStatusFilter;
    private ObservableList<UserItem> allUsersList; // 保存所有用户数据用于筛选
    private Label statusLabel;
    private MediaPlayer mediaPlayer;
    
    public AdminHomeView(ClientApp app, SocketClient client, Session session) {
        this.app = app;
        this.client = client;
        this.session = session;
    }
    
    public Scene createScene() {
        // 使用StackPane以支持视频背景
        StackPane backgroundPane = new StackPane();
        
        // 尝试加载视频背景（管理员系统专用）
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
                
                // 监听场景大小变化
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
        
        // 如果没有视频，使用默认背景
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
        
        Label titleLabel = new Label("图书馆管理系统 - 管理员界面");
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
        
        Label userLabel = new Label("管理员: " + session.getUsername());
        userLabel.getStyleClass().add("top-user-info");
        
        Button logoutButton = new Button("登出");
        logoutButton.getStyleClass().add("logout-button");
        logoutButton.setOnAction(e -> {
            session.logout();
            app.showLoginView();
        });
        
        rightBox.getChildren().addAll(homeButton, userLabel, logoutButton);
        
        // 使用Region填充中间空间
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        topBox.getChildren().addAll(titleLabel, spacer, rightBox);
        root.setTop(topBox);
        
        // 中间：标签页
        TabPane tabPane = new TabPane();
        
        Tab bookTab = new Tab("图书管理");
        bookTab.setContent(createBookManagementTab());
        bookTab.setClosable(false);
        
        Tab trendingTab = new Tab("热门榜单");
        trendingTab.setContent(createTrendingTab());
        trendingTab.setClosable(false);
        
        Tab recordsTab = new Tab("借阅记录");
        recordsTab.setContent(createRecordsTab());
        recordsTab.setClosable(false);
        
        Tab usersTab = new Tab("用户管理");
        usersTab.setContent(createUsersTab());
        usersTab.setClosable(false);
        
        Tab fineTab = new Tab("欠费管理");
        fineTab.setContent(createFineManagementTab());
        fineTab.setClosable(false);
        
        Tab fineRateTab = new Tab("梯度价格");
        fineRateTab.setContent(createFineRateConfigTab());
        fineRateTab.setClosable(false);
        
        tabPane.getTabs().addAll(bookTab, trendingTab, recordsTab, usersTab, fineTab, fineRateTab);
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
    
    private VBox createBookManagementTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        // 操作按钮
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(0, 0, 10, 0));
        
        Button addButton = new Button("添加图书");
        addButton.getStyleClass().add("action-button");
        addButton.setOnAction(e -> showAddBookDialog());
        
        Button updateButton = new Button("修改图书");
        updateButton.getStyleClass().add("action-button");
        updateButton.setOnAction(e -> showUpdateBookDialog());
        
        Button deleteButton = new Button("删除图书");
        deleteButton.getStyleClass().addAll("action-button", "danger-button");
        deleteButton.setOnAction(e -> deleteBook());
        
        Button importButton = new Button("批量导入");
        importButton.getStyleClass().add("action-button");
        importButton.setOnAction(e -> importBooks());
        
        Button refreshButton = new Button("刷新");
        refreshButton.getStyleClass().add("action-button");
        refreshButton.setOnAction(e -> loadAllBooks());
        
        buttonBox.getChildren().addAll(addButton, updateButton, deleteButton, importButton, refreshButton);
        
        // 图书表格
        bookTable = new TableView<>();
        bookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<BookItem, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("bookId"));
        idCol.setPrefWidth(80);
        
        TableColumn<BookItem, String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        isbnCol.setPrefWidth(150);
        
        TableColumn<BookItem, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(200);
        
        TableColumn<BookItem, String> authorCol = new TableColumn<>("作者");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));
        authorCol.setPrefWidth(150);
        
        TableColumn<BookItem, String> categoryCol = new TableColumn<>("分类");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(100);
        
        TableColumn<BookItem, Integer> totalCol = new TableColumn<>("总数量");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalCount"));
        totalCol.setPrefWidth(80);
        
        TableColumn<BookItem, Integer> availableCol = new TableColumn<>("可用数量");
        availableCol.setCellValueFactory(new PropertyValueFactory<>("availableCount"));
        availableCol.setPrefWidth(80);
        
        bookTable.getColumns().addAll(idCol, isbnCol, titleCol, authorCol, categoryCol, totalCol, availableCol);
        
        vbox.getChildren().addAll(buttonBox, bookTable);
        
        // 延迟加载，等场景创建完成后再加载
        javafx.application.Platform.runLater(() -> loadAllBooks());
        
        return vbox;
    }
    
    private VBox createTrendingTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        Button loadButton = new Button("加载热门榜单");
        loadButton.getStyleClass().add("action-button");
        loadButton.setOnAction(e -> loadTrending());
        
        trendingTable = new TableView<>();
        trendingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<TrendingItem, String> titleCol = new TableColumn<>("书名");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(200);
        
        TableColumn<TrendingItem, String> authorCol = new TableColumn<>("作者");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));
        authorCol.setPrefWidth(150);
        
        TableColumn<TrendingItem, Integer> borrowCountCol = new TableColumn<>("借阅次数");
        borrowCountCol.setCellValueFactory(new PropertyValueFactory<>("borrowCount"));
        borrowCountCol.setPrefWidth(100);
        
        trendingTable.getColumns().addAll(titleCol, authorCol, borrowCountCol);
        
        vbox.getChildren().addAll(loadButton, trendingTable);
        return vbox;
    }
    
    private void loadAllBooks() {
        try {
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.SEARCH_BOOK);
            request.setToken(session.getToken());
            request.setPayload(JsonUtil.createObjectNode()
                    .put("limit", 1000)
                    .put("offset", 0));
            
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
                        item.setPublisher(bookNode.has("publisher") ? bookNode.get("publisher").asText() : "");
                        item.setDescription(bookNode.has("description") ? bookNode.get("description").asText() : "");
                        item.setTotalCount(bookNode.has("totalCount") ? bookNode.get("totalCount").asInt() : 0);
                        item.setAvailableCount(bookNode.has("availableCount") ? bookNode.get("availableCount").asInt() : 0);
                        books.add(item);
                    }
                }
                
                bookTable.setItems(books);
                if (statusLabel != null) {
                    statusLabel.setText("加载了 " + books.size() + " 本图书");
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
    
    private void showAddBookDialog() {
        Dialog<BookItem> dialog = new Dialog<>();
        dialog.setTitle("添加图书");
        dialog.setHeaderText("请输入图书信息");
        
        TextField isbnField = new TextField();
        TextField titleField = new TextField();
        TextField authorField = new TextField();
        TextField categoryField = new TextField();
        TextField publisherField = new TextField();
        TextArea descriptionArea = new TextArea();
        TextField totalCountField = new TextField();
        Label coverPathLabel = new Label("未选择封面");
        coverPathLabel.setWrapText(true);
        coverPathLabel.setPrefWidth(300);
        Button selectCoverButton = new Button("选择封面图片");
        selectCoverButton.getStyleClass().add("action-button");
        
        java.util.concurrent.atomic.AtomicReference<String> coverImagePath = new java.util.concurrent.atomic.AtomicReference<>(null);
        
        selectCoverButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择封面图片");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                coverImagePath.set(file.getAbsolutePath());
                coverPathLabel.setText("已选择: " + file.getName());
            }
        });
        
        HBox coverBox = new HBox(10);
        coverBox.getChildren().addAll(selectCoverButton, coverPathLabel);
        coverBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox vbox = new VBox(10);
        vbox.getChildren().addAll(
                new Label("ISBN:"), isbnField,
                new Label("书名:"), titleField,
                new Label("作者:"), authorField,
                new Label("分类:"), categoryField,
                new Label("出版社:"), publisherField,
                new Label("描述:"), descriptionArea,
                new Label("总数量:"), totalCountField,
                new Label("封面图片:"), coverBox
        );
        vbox.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(vbox);
        
        ButtonType addButtonType = new ButtonType("添加", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    Request request = new Request();
                    request.setRequestId(java.util.UUID.randomUUID().toString());
                    request.setOpCode(OpCode.ADMIN_ADD_BOOK);
                    request.setToken(session.getToken());
                    ObjectNode payload = JsonUtil.createObjectNode();
                    payload.put("isbn", isbnField.getText());
                    payload.put("title", titleField.getText());
                    payload.put("author", authorField.getText());
                    payload.put("category", categoryField.getText());
                    payload.put("publisher", publisherField.getText());
                    payload.put("description", descriptionArea.getText());
                    payload.put("totalCount", Integer.parseInt(totalCountField.getText()));
                    payload.put("availableCount", Integer.parseInt(totalCountField.getText()));
                    
                    // 如果有选择封面，发送封面路径
                    if (coverImagePath.get() != null) {
                        payload.put("coverImagePath", coverImagePath.get());
                    }
                    
                    request.setPayload(payload);
                    
                    Response response = client.send(request);
                    if (response.isSuccess()) {
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("添加成功");
                        successAlert.setHeaderText(null);
                        successAlert.setContentText("图书添加成功！");
                        successAlert.showAndWait();
                        
                        if (statusLabel != null) {
                            statusLabel.setText("添加图书成功");
                        }
                        loadAllBooks();
                        return new BookItem();
                    } else {
                        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                        errorAlert.setTitle("添加失败");
                        errorAlert.setHeaderText(null);
                        errorAlert.setContentText("添加失败: " + response.getMessage());
                        errorAlert.showAndWait();
                        
                        if (statusLabel != null) {
                            statusLabel.setText("添加失败: " + response.getMessage());
                        }
                    }
                } catch (Exception e) {
                    if (statusLabel != null) {
                        statusLabel.setText("添加失败: " + e.getMessage());
                    }
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    private void showUpdateBookDialog() {
        BookItem selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            if (statusLabel != null) {
                statusLabel.setText("请先选择要修改的图书");
            }
            return;
        }
        
        Dialog<BookItem> dialog = new Dialog<>();
        dialog.setTitle("修改图书");
        dialog.setHeaderText("修改图书信息");
        
        TextField isbnField = new TextField(selected.getIsbn());
        TextField titleField = new TextField(selected.getTitle());
        TextField authorField = new TextField(selected.getAuthor());
        TextField categoryField = new TextField(selected.getCategory());
        TextField publisherField = new TextField(selected.getPublisher() != null ? selected.getPublisher() : "");
        TextArea descriptionArea = new TextArea(selected.getDescription() != null ? selected.getDescription() : "");
        TextField totalCountField = new TextField(String.valueOf(selected.getTotalCount()));
        TextField availableCountField = new TextField(String.valueOf(selected.getAvailableCount()));
        
        Label coverPathLabel = new Label("未选择封面");
        coverPathLabel.setWrapText(true);
        coverPathLabel.setPrefWidth(300);
        Button selectCoverButton = new Button("选择封面图片");
        selectCoverButton.getStyleClass().add("action-button");
        
        java.util.concurrent.atomic.AtomicReference<String> coverImagePath = new java.util.concurrent.atomic.AtomicReference<>(null);
        
        selectCoverButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择封面图片");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
            );
            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                coverImagePath.set(file.getAbsolutePath());
                coverPathLabel.setText("已选择: " + file.getName());
            }
        });
        
        HBox coverBox = new HBox(10);
        coverBox.getChildren().addAll(selectCoverButton, coverPathLabel);
        coverBox.setAlignment(Pos.CENTER_LEFT);
        
        VBox vbox = new VBox(10);
        vbox.getChildren().addAll(
                new Label("ISBN:"), isbnField,
                new Label("书名:"), titleField,
                new Label("作者:"), authorField,
                new Label("分类:"), categoryField,
                new Label("出版社:"), publisherField,
                new Label("描述:"), descriptionArea,
                new Label("总数量:"), totalCountField,
                new Label("可用数量:"), availableCountField,
                new Label("封面图片:"), coverBox
        );
        vbox.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(vbox);
        
        ButtonType updateButtonType = new ButtonType("更新", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(updateButtonType, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == updateButtonType) {
                try {
                    Request request = new Request();
                    request.setRequestId(java.util.UUID.randomUUID().toString());
                    request.setOpCode(OpCode.ADMIN_UPDATE_BOOK);
                    request.setToken(session.getToken());
                    
                    ObjectNode payload = JsonUtil.createObjectNode();
                    payload.put("bookId", selected.getBookId());
                    payload.put("title", titleField.getText());
                    payload.put("author", authorField.getText());
                    payload.put("category", categoryField.getText());
                    payload.put("publisher", publisherField.getText());
                    payload.put("description", descriptionArea.getText());
                    payload.put("totalCount", Integer.parseInt(totalCountField.getText()));
                    payload.put("availableCount", Integer.parseInt(availableCountField.getText()));
                    
                    // 如果有选择封面，发送封面路径
                    if (coverImagePath.get() != null) {
                        payload.put("coverImagePath", coverImagePath.get());
                    }
                    
                    request.setPayload(payload);
                    
                    Response response = client.send(request);
                    if (response.isSuccess()) {
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("更新成功");
                        successAlert.setHeaderText(null);
                        successAlert.setContentText("图书更新成功！");
                        successAlert.showAndWait();
                        
                        if (statusLabel != null) {
                            statusLabel.setText("更新图书成功");
                        }
                        loadAllBooks();
                        return new BookItem();
                    } else {
                        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                        errorAlert.setTitle("更新失败");
                        errorAlert.setHeaderText(null);
                        errorAlert.setContentText("更新失败: " + response.getMessage());
                        errorAlert.showAndWait();
                        
                        if (statusLabel != null) {
                            statusLabel.setText("更新失败: " + response.getMessage());
                        }
                    }
                } catch (Exception e) {
                    if (statusLabel != null) {
                        statusLabel.setText("更新失败: " + e.getMessage());
                    }
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    private void deleteBook() {
        BookItem selected = bookTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            if (statusLabel != null) {
                statusLabel.setText("请先选择要删除的图书");
            }
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("确定要删除这本图书吗？");
        confirm.setContentText("书名: " + selected.getTitle());
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.ADMIN_DELETE_BOOK);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode()
                        .put("bookId", selected.getBookId()));
                
                Response response = client.send(request);
                if (response.isSuccess()) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("删除成功");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("图书删除成功！");
                    successAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("删除成功");
                    }
                    loadAllBooks();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("删除失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("删除失败: " + response.getMessage());
                    errorAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("删除失败: " + response.getMessage());
                    }
                }
            } catch (Exception e) {
                if (statusLabel != null) {
                    statusLabel.setText("删除失败: " + e.getMessage());
                }
            }
        }
    }
    
    private void importBooks() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择文件（支持CSV/TXT）");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV文件", "*.csv"),
                new FileChooser.ExtensionFilter("文本文件", "*.txt"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        
        File file = fileChooser.showOpenDialog(null);
        if (file == null) {
            return;
        }
        
        try {
            String fileContent = new String(Files.readAllBytes(file.toPath()));
            
            // 对于TXT文件，尝试按CSV格式解析（逗号分隔）
            // 如果TXT文件不是CSV格式，可以按行解析，每行格式：isbn,title,author,category,publisher,description,totalCount
            String content = fileContent;
            
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.ADMIN_IMPORT_BOOKS);
            request.setToken(session.getToken());
            request.setPayload(JsonUtil.createObjectNode()
                    .put("csvContent", content));
            
            Response response = client.send(request);
            if (response.isSuccess()) {
                String message = "导入成功";
                if (response.getData() != null) {
                    int successCount = response.getData().has("successCount") ? 
                            response.getData().get("successCount").asInt() : 0;
                    int failCount = response.getData().has("failCount") ? 
                            response.getData().get("failCount").asInt() : 0;
                    message = String.format("导入完成：成功%d本，失败%d本", successCount, failCount);
                }
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("导入完成");
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
                
                if (statusLabel != null) {
                    statusLabel.setText(message);
                }
                loadAllBooks();
            } else {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("导入失败");
                errorAlert.setHeaderText(null);
                errorAlert.setContentText("导入失败: " + response.getMessage());
                errorAlert.showAndWait();
                
                if (statusLabel != null) {
                    statusLabel.setText("导入失败: " + response.getMessage());
                }
            }
        } catch (Exception e) {
            if (statusLabel != null) {
                statusLabel.setText("导入失败: " + e.getMessage());
            }
        }
    }
    
    private VBox createRecordsTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(0, 0, 10, 0));
        
        Button refreshButton = new Button("刷新");
        refreshButton.getStyleClass().add("action-button");
        refreshButton.setOnAction(e -> loadAllRecords());
        
        recordsStatusFilter = new ComboBox<>();
        recordsStatusFilter.getItems().addAll("全部", "BORROWED", "RETURNED", "OVERDUE");
        recordsStatusFilter.setValue("全部");
        recordsStatusFilter.getStyleClass().add("text-field");
        recordsStatusFilter.setOnAction(e -> loadAllRecords());
        
        buttonBox.getChildren().addAll(refreshButton, new Label("状态筛选:"), recordsStatusFilter);
        
        recordsTable = new TableView<>();
        recordsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<RecordItem, String> usernameCol = new TableColumn<>("用户名");
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        usernameCol.setPrefWidth(120);
        
        TableColumn<RecordItem, String> bookTitleCol = new TableColumn<>("书名");
        bookTitleCol.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        bookTitleCol.setPrefWidth(200);
        
        TableColumn<RecordItem, String> borrowTimeCol = new TableColumn<>("借阅时间");
        borrowTimeCol.setCellValueFactory(new PropertyValueFactory<>("borrowTime"));
        borrowTimeCol.setPrefWidth(150);
        
        TableColumn<RecordItem, String> dueTimeCol = new TableColumn<>("应还时间");
        dueTimeCol.setCellValueFactory(new PropertyValueFactory<>("dueTime"));
        dueTimeCol.setPrefWidth(150);
        
        TableColumn<RecordItem, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        
        recordsTable.getColumns().addAll(usernameCol, bookTitleCol, borrowTimeCol, dueTimeCol, statusCol);
        
        vbox.getChildren().addAll(buttonBox, recordsTable);
        
        // 自动加载
        loadAllRecords();
        
        return vbox;
    }
    
    private VBox createUsersTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(0, 0, 10, 0));
        
        Button refreshButton = new Button("刷新");
        refreshButton.getStyleClass().add("action-button");
        refreshButton.setOnAction(e -> loadAllUsers());
        
        Button freezeButton = new Button("冻结用户");
        freezeButton.getStyleClass().addAll("action-button", "danger-button");
        freezeButton.setOnAction(e -> freezeUser());
        
        Button unfreezeButton = new Button("解冻用户");
        unfreezeButton.getStyleClass().addAll("action-button", "success-button");
        unfreezeButton.setOnAction(e -> unfreezeUser());
        
        // 用户状态筛选
        usersStatusFilter = new ComboBox<>();
        usersStatusFilter.getItems().addAll("全部", "ACTIVE", "FROZEN");
        usersStatusFilter.setValue("全部");
        usersStatusFilter.getStyleClass().add("text-field");
        usersStatusFilter.setOnAction(e -> filterUsers());
        
        buttonBox.getChildren().addAll(refreshButton, freezeButton, unfreezeButton, 
                new Label("状态筛选:"), usersStatusFilter);
        
        usersTable = new TableView<>();
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<UserItem, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("userId"));
        idCol.setPrefWidth(80);
        
        TableColumn<UserItem, String> usernameCol = new TableColumn<>("用户名");
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        usernameCol.setPrefWidth(150);
        
        TableColumn<UserItem, String> roleCol = new TableColumn<>("角色");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));
        roleCol.setPrefWidth(100);
        
        TableColumn<UserItem, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        
        TableColumn<UserItem, String> fineCol = new TableColumn<>("欠费");
        fineCol.setCellValueFactory(new PropertyValueFactory<>("fineAmount"));
        fineCol.setPrefWidth(100);
        
        usersTable.getColumns().addAll(idCol, usernameCol, roleCol, statusCol, fineCol);
        
        vbox.getChildren().addAll(buttonBox, usersTable);
        
        // 自动加载
        loadAllUsers();
        
        return vbox;
    }
    
    private void loadAllRecords() {
        try {
            String status = null;
            if (recordsStatusFilter != null && !"全部".equals(recordsStatusFilter.getValue())) {
                status = recordsStatusFilter.getValue();
            }
            
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.ADMIN_ALL_RECORDS);
            request.setToken(session.getToken());
            
            ObjectNode payload = JsonUtil.createObjectNode();
            if (status != null) {
                payload.put("status", status);
            }
            payload.put("limit", 1000);
            payload.put("offset", 0);
            request.setPayload(payload);
            
            Response response = client.send(request);
            
            if (response.isSuccess() && response.getData() != null) {
                JsonNode recordsNode = response.getData().get("records");
                ObservableList<RecordItem> records = FXCollections.observableArrayList();
                
                if (recordsNode != null && recordsNode.isArray()) {
                    for (JsonNode recordNode : recordsNode) {
                        RecordItem item = new RecordItem();
                        item.setRecordId(recordNode.get("id").asLong());
                        item.setUsername(recordNode.has("username") ? recordNode.get("username").asText() : "");
                        item.setBookTitle(recordNode.has("bookTitle") ? recordNode.get("bookTitle").asText() : "");
                        item.setBorrowTime(recordNode.has("borrowTime") ? recordNode.get("borrowTime").asText() : "");
                        item.setDueTime(recordNode.has("dueTime") ? recordNode.get("dueTime").asText() : "");
                        item.setStatus(recordNode.has("status") ? recordNode.get("status").asText() : "");
                        records.add(item);
                    }
                }
                
                recordsTable.setItems(records);
                if (statusLabel != null) {
                    statusLabel.setText("加载了 " + records.size() + " 条借阅记录");
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
    
    private void loadAllUsers() {
        try {
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.ADMIN_LIST_USERS);
            request.setToken(session.getToken());
            request.setPayload(JsonUtil.createObjectNode());
            
            Response response = client.send(request);
            
            if (response.isSuccess() && response.getData() != null) {
                JsonNode usersNode = response.getData().get("users");
                ObservableList<UserItem> users = FXCollections.observableArrayList();
                
                if (usersNode != null && usersNode.isArray()) {
                    for (JsonNode userNode : usersNode) {
                        UserItem item = new UserItem();
                        item.setUserId(userNode.get("id").asLong());
                        item.setUsername(userNode.has("username") ? userNode.get("username").asText() : "");
                        item.setRole(userNode.has("role") ? userNode.get("role").asText() : "");
                        item.setStatus(userNode.has("status") ? userNode.get("status").asText() : "");
                        if (userNode.has("fineAmount")) {
                            double fine = userNode.get("fineAmount").asDouble();
                            item.setFineAmount(String.format("%.2f元", fine));
                        } else {
                            item.setFineAmount("0.00元");
                        }
                        users.add(item);
                    }
                }
                
                // 保存所有用户数据用于筛选
                allUsersList = FXCollections.observableArrayList(users);
                
                // 应用筛选
                filterUsers();
                
                if (statusLabel != null) {
                    statusLabel.setText("加载了 " + allUsersList.size() + " 个用户");
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
    
    /**
     * 根据筛选条件过滤用户列表
     */
    private void filterUsers() {
        if (allUsersList == null || usersStatusFilter == null) {
            return;
        }
        
        String selectedStatus = usersStatusFilter.getValue();
        ObservableList<UserItem> filteredUsers = FXCollections.observableArrayList();
        
        if ("全部".equals(selectedStatus)) {
            filteredUsers.addAll(allUsersList);
        } else {
            for (UserItem user : allUsersList) {
                if (selectedStatus.equals(user.getStatus())) {
                    filteredUsers.add(user);
                }
            }
        }
        
        usersTable.setItems(filteredUsers);
        
        if (statusLabel != null) {
            statusLabel.setText("显示 " + filteredUsers.size() + " / " + allUsersList.size() + " 个用户");
        }
    }
    
    private void freezeUser() {
        UserItem selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            if (statusLabel != null) {
                statusLabel.setText("请先选择要冻结的用户");
            }
            return;
        }
        
        if ("ADMIN".equals(selected.getRole())) {
            if (statusLabel != null) {
                statusLabel.setText("不能冻结管理员账户");
            }
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认冻结");
        confirm.setHeaderText("确定要冻结该用户吗？");
        confirm.setContentText("用户名: " + selected.getUsername());
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.ADMIN_USER_FREEZE);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode()
                        .put("userId", selected.getUserId()));
                
                Response response = client.send(request);
                if (response.isSuccess()) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("冻结成功");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("用户冻结成功！");
                    successAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("冻结用户成功");
                    }
                    loadAllUsers();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("冻结失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("冻结失败: " + response.getMessage());
                    errorAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("冻结失败: " + response.getMessage());
                    }
                }
            } catch (Exception e) {
                if (statusLabel != null) {
                    statusLabel.setText("冻结失败: " + e.getMessage());
                }
            }
        }
    }
    
    private void unfreezeUser() {
        UserItem selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            if (statusLabel != null) {
                statusLabel.setText("请先选择要解冻的用户");
            }
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认解冻");
        confirm.setHeaderText("确定要解冻该用户吗？");
        confirm.setContentText("用户名: " + selected.getUsername());
        
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.ADMIN_USER_UNFREEZE);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode()
                        .put("userId", selected.getUserId()));
                
                Response response = client.send(request);
                if (response.isSuccess()) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("解冻成功");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("用户解冻成功！");
                    successAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("解冻用户成功");
                    }
                    loadAllUsers();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("解冻失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("解冻失败: " + response.getMessage());
                    errorAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("解冻失败: " + response.getMessage());
                    }
                }
            } catch (Exception e) {
                if (statusLabel != null) {
                    statusLabel.setText("解冻失败: " + e.getMessage());
                }
            }
        }
    }
    
    private void loadTrending() {
        try {
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.TRENDING);
            request.setToken(session.getToken());
            request.setPayload(JsonUtil.createObjectNode()
                    .put("topN", 20));
            
            Response response = client.send(request);
            if (response.isSuccess() && response.getData() != null) {
                JsonNode booksNode = response.getData().get("books");
                ObservableList<TrendingItem> trendingItems = FXCollections.observableArrayList();
                
                if (booksNode != null && booksNode.isArray()) {
                    for (JsonNode bookNode : booksNode) {
                        TrendingItem item = new TrendingItem();
                        item.setTitle(bookNode.has("title") ? bookNode.get("title").asText() : "");
                        item.setAuthor(bookNode.has("author") ? bookNode.get("author").asText() : "");
                        item.setBorrowCount(bookNode.has("borrowCount") ? bookNode.get("borrowCount").asInt() : 0);
                        trendingItems.add(item);
                    }
                }
                
                trendingTable.setItems(trendingItems);
                if (statusLabel != null) {
                    statusLabel.setText("加载了 " + trendingItems.size() + " 本热门图书");
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
    
    // 数据模型
    public static class BookItem {
        private Long bookId;
        private String isbn;
        private String title;
        private String author;
        private String category;
        private String publisher;
        private String description;
        private Integer totalCount;
        private Integer availableCount;
        
        // Getters and Setters
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
        public String getPublisher() { return publisher; }
        public void setPublisher(String publisher) { this.publisher = publisher; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Integer getAvailableCount() { return availableCount; }
        public void setAvailableCount(Integer availableCount) { this.availableCount = availableCount; }
    }
    
    public static class TrendingItem {
        private String title;
        private String author;
        private Integer borrowCount;
        
        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public Integer getBorrowCount() { return borrowCount; }
        public void setBorrowCount(Integer borrowCount) { this.borrowCount = borrowCount; }
    }
    
    public static class RecordItem {
        private Long recordId;
        private String username;
        private String bookTitle;
        private String borrowTime;
        private String dueTime;
        private String status;
        
        // Getters and Setters
        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getBookTitle() { return bookTitle; }
        public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
        public String getBorrowTime() { return borrowTime; }
        public void setBorrowTime(String borrowTime) { this.borrowTime = borrowTime; }
        public String getDueTime() { return dueTime; }
        public void setDueTime(String dueTime) { this.dueTime = dueTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    public static class UserItem {
        private Long userId;
        private String username;
        private String role;
        private String status;
        private String fineAmount;
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getFineAmount() { return fineAmount; }
        public void setFineAmount(String fineAmount) { this.fineAmount = fineAmount; }
    }
    
    private VBox createFineManagementTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(0, 0, 10, 0));
        
        Button refreshButton = new Button("刷新");
        refreshButton.getStyleClass().add("action-button");
        refreshButton.setOnAction(e -> loadAllFines());
        
        Button sendReminderButton = new Button("发送提醒");
        sendReminderButton.getStyleClass().addAll("action-button", "success-button");
        sendReminderButton.setOnAction(e -> sendReminder());
        
        buttonBox.getChildren().addAll(refreshButton, sendReminderButton);
        
        fineTable = new TableView<>();
        fineTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<FineItem, Long> userIdCol = new TableColumn<>("用户ID");
        userIdCol.setCellValueFactory(new PropertyValueFactory<>("userId"));
        userIdCol.setPrefWidth(80);
        
        TableColumn<FineItem, String> usernameCol = new TableColumn<>("用户名");
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        usernameCol.setPrefWidth(150);
        
        TableColumn<FineItem, String> totalFineCol = new TableColumn<>("已产生罚款");
        totalFineCol.setCellValueFactory(new PropertyValueFactory<>("totalFine"));
        totalFineCol.setPrefWidth(120);
        
        TableColumn<FineItem, String> currentFineCol = new TableColumn<>("当前逾期罚款");
        currentFineCol.setCellValueFactory(new PropertyValueFactory<>("currentOverdueFine"));
        currentFineCol.setPrefWidth(130);
        
        TableColumn<FineItem, String> totalOwedCol = new TableColumn<>("欠费总额");
        totalOwedCol.setCellValueFactory(new PropertyValueFactory<>("totalOwed"));
        totalOwedCol.setPrefWidth(120);
        
        TableColumn<FineItem, Integer> overdueCountCol = new TableColumn<>("逾期数量");
        overdueCountCol.setCellValueFactory(new PropertyValueFactory<>("overdueCount"));
        overdueCountCol.setPrefWidth(100);
        
        fineTable.getColumns().addAll(userIdCol, usernameCol, totalFineCol, currentFineCol, totalOwedCol, overdueCountCol);
        
        vbox.getChildren().addAll(buttonBox, fineTable);
        
        loadAllFines();
        
        return vbox;
    }
    
    private void loadAllFines() {
        try {
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.ADMIN_ALL_USERS_FINE);
            request.setToken(session.getToken());
            request.setPayload(JsonUtil.createObjectNode());
            
            Response response = client.send(request);
            
            if (response.isSuccess() && response.getData() != null) {
                JsonNode usersNode = response.getData().get("users");
                ObservableList<FineItem> fines = FXCollections.observableArrayList();
                
                if (usersNode != null && usersNode.isArray()) {
                    for (JsonNode userNode : usersNode) {
                        FineItem item = new FineItem();
                        item.setUserId(userNode.get("userId").asLong());
                        item.setUsername(userNode.has("username") ? userNode.get("username").asText() : "");
                        item.setTotalFine(String.format("%.2f元", userNode.get("totalFine").asDouble()));
                        item.setCurrentOverdueFine(String.format("%.2f元", userNode.get("currentOverdueFine").asDouble()));
                        item.setTotalOwed(String.format("%.2f元", userNode.get("totalOwed").asDouble()));
                        item.setOverdueCount(userNode.get("overdueCount").asInt());
                        fines.add(item);
                    }
                }
                
                fineTable.setItems(fines);
                if (statusLabel != null) {
                    statusLabel.setText("加载了 " + fines.size() + " 个欠费用户");
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
    
    private void sendReminder() {
        FineItem selected = fineTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            if (statusLabel != null) {
                statusLabel.setText("请先选择要发送提醒的用户");
            }
            return;
        }
        
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("发送提醒");
        dialog.setHeaderText("发送提醒给: " + selected.getUsername());
        
        TextArea messageArea = new TextArea();
        messageArea.setPrefRowCount(8);
        messageArea.setWrapText(true);
        messageArea.setText("尊敬的读者 " + selected.getUsername() + "，您好！\n\n" +
            "您目前有逾期未还的图书，请尽快归还。\n" +
            "逾期图书数量：" + selected.getOverdueCount() + "本\n" +
            "当前欠费总额：" + selected.getTotalOwed() + "\n" +
            "（其中已产生罚款：" + selected.getTotalFine() + "，当前逾期预计罚款：" + selected.getCurrentOverdueFine() + "）\n\n" +
            "请尽快到图书馆办理还书和缴费手续，感谢您的配合！");
        
        VBox vbox = new VBox(10);
        vbox.getChildren().addAll(new Label("提醒内容:"), messageArea);
        vbox.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(vbox);
        
        ButtonType sendButtonType = new ButtonType("发送", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(sendButtonType, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == sendButtonType) {
                return messageArea.getText();
            }
            return null;
        });
        
        String message = dialog.showAndWait().orElse(null);
        if (message != null) {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.ADMIN_SEND_REMINDER);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode()
                    .put("userId", selected.getUserId())
                    .put("message", message));
                
                Response response = client.send(request);
                if (response.isSuccess()) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("发送成功");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("提醒已发送给用户: " + selected.getUsername());
                    successAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("提醒发送成功");
                    }
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("发送失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("发送失败: " + response.getMessage());
                    errorAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("发送失败: " + response.getMessage());
                    }
                }
            } catch (Exception e) {
                if (statusLabel != null) {
                    statusLabel.setText("发送失败: " + e.getMessage());
                }
            }
        }
    }
    
    public static class FineItem {
        private Long userId;
        private String username;
        private String totalFine;
        private String currentOverdueFine;
        private String totalOwed;
        private Integer overdueCount;
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getTotalFine() { return totalFine; }
        public void setTotalFine(String totalFine) { this.totalFine = totalFine; }
        public String getCurrentOverdueFine() { return currentOverdueFine; }
        public void setCurrentOverdueFine(String currentOverdueFine) { this.currentOverdueFine = currentOverdueFine; }
        public String getTotalOwed() { return totalOwed; }
        public void setTotalOwed(String totalOwed) { this.totalOwed = totalOwed; }
        public Integer getOverdueCount() { return overdueCount; }
        public void setOverdueCount(Integer overdueCount) { this.overdueCount = overdueCount; }
    }
    
    private VBox createFineRateConfigTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(0, 0, 10, 0));
        
        Button refreshButton = new Button("刷新");
        refreshButton.getStyleClass().add("action-button");
        refreshButton.setOnAction(e -> loadFineRateConfigs());
        
        Button addButton = new Button("添加梯度");
        addButton.getStyleClass().add("action-button");
        addButton.setOnAction(e -> showAddFineRateConfigDialog());
        
        Button updateButton = new Button("修改");
        updateButton.getStyleClass().add("action-button");
        updateButton.setOnAction(e -> showUpdateFineRateConfigDialog());
        
        Button deleteButton = new Button("删除");
        deleteButton.getStyleClass().addAll("action-button", "danger-button");
        deleteButton.setOnAction(e -> deleteFineRateConfig());
        
        buttonBox.getChildren().addAll(refreshButton, addButton, updateButton, deleteButton);
        
        fineRateConfigTable = new TableView<>();
        fineRateConfigTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        TableColumn<FineRateConfigItem, Integer> orderCol = new TableColumn<>("顺序");
        orderCol.setCellValueFactory(new PropertyValueFactory<>("displayOrder"));
        orderCol.setPrefWidth(60);
        
        TableColumn<FineRateConfigItem, String> rangeCol = new TableColumn<>("天数范围");
        rangeCol.setCellValueFactory(new PropertyValueFactory<>("dayRange"));
        rangeCol.setPrefWidth(150);
        
        TableColumn<FineRateConfigItem, String> rateCol = new TableColumn<>("每日费率");
        rateCol.setCellValueFactory(new PropertyValueFactory<>("ratePerDay"));
        rateCol.setPrefWidth(100);
        
        TableColumn<FineRateConfigItem, String> descCol = new TableColumn<>("描述");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(200);
        
        fineRateConfigTable.getColumns().addAll(orderCol, rangeCol, rateCol, descCol);
        
        vbox.getChildren().addAll(buttonBox, fineRateConfigTable);
        
        javafx.application.Platform.runLater(() -> loadFineRateConfigs());
        
        return vbox;
    }
    
    private void loadFineRateConfigs() {
        try {
            Request request = new Request();
            request.setRequestId(java.util.UUID.randomUUID().toString());
            request.setOpCode(OpCode.ADMIN_GET_FINE_RATE_CONFIG);
            request.setToken(session.getToken());
            request.setPayload(JsonUtil.createObjectNode());
            
            Response response = client.send(request);
            
            if (response.isSuccess() && response.getData() != null) {
                JsonNode configsNode = response.getData().get("configs");
                ObservableList<FineRateConfigItem> configs = FXCollections.observableArrayList();
                
                if (configsNode != null && configsNode.isArray()) {
                    for (JsonNode configNode : configsNode) {
                        FineRateConfigItem item = new FineRateConfigItem();
                        item.setId(configNode.get("id").asLong());
                        item.setDayRangeStart(configNode.get("dayRangeStart").asInt());
                        if (configNode.has("dayRangeEnd") && !configNode.get("dayRangeEnd").isNull()) {
                            item.setDayRangeEnd(configNode.get("dayRangeEnd").asInt());
                        }
                        item.setRatePerDay(String.format("%.2f元/天", configNode.get("ratePerDay").asDouble()));
                        item.setDescription(configNode.has("description") ? configNode.get("description").asText() : "");
                        item.setDisplayOrder(configNode.get("displayOrder").asInt());
                        
                        String rangeStr;
                        if (item.getDayRangeEnd() == null) {
                            rangeStr = "第" + item.getDayRangeStart() + "天以上";
                        } else {
                            rangeStr = "第" + item.getDayRangeStart() + "-" + item.getDayRangeEnd() + "天";
                        }
                        item.setDayRange(rangeStr);
                        
                        configs.add(item);
                    }
                }
                
                fineRateConfigTable.setItems(configs);
                if (statusLabel != null) {
                    statusLabel.setText("加载了 " + configs.size() + " 个梯度配置");
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
    
    private void showAddFineRateConfigDialog() {
        Dialog<FineRateConfigItem> dialog = new Dialog<>();
        dialog.setTitle("添加梯度价格");
        dialog.setHeaderText("请输入梯度价格配置");
        
        TextField dayRangeStartField = new TextField();
        TextField dayRangeEndField = new TextField();
        TextField ratePerDayField = new TextField();
        TextField descriptionField = new TextField();
        TextField displayOrderField = new TextField();
        
        VBox vbox = new VBox(10);
        vbox.getChildren().addAll(
            new Label("起始天数:"), dayRangeStartField,
            new Label("结束天数 (留空表示无上限):"), dayRangeEndField,
            new Label("每日费率 (元):"), ratePerDayField,
            new Label("描述:"), descriptionField,
            new Label("显示顺序:"), displayOrderField
        );
        vbox.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(vbox);
        
        ButtonType addButtonType = new ButtonType("添加", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    Request request = new Request();
                    request.setRequestId(java.util.UUID.randomUUID().toString());
                    request.setOpCode(OpCode.ADMIN_ADD_FINE_RATE_CONFIG);
                    request.setToken(session.getToken());
                    
                    ObjectNode payload = JsonUtil.createObjectNode();
                    payload.put("dayRangeStart", Integer.parseInt(dayRangeStartField.getText()));
                    if (!dayRangeEndField.getText().trim().isEmpty()) {
                        payload.put("dayRangeEnd", Integer.parseInt(dayRangeEndField.getText()));
                    }
                    payload.put("ratePerDay", Double.parseDouble(ratePerDayField.getText()));
                    payload.put("description", descriptionField.getText());
                    payload.put("displayOrder", Integer.parseInt(displayOrderField.getText()));
                    
                    request.setPayload(payload);
                    
                    Response response = client.send(request);
                    if (response.isSuccess()) {
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("添加成功");
                        successAlert.setHeaderText(null);
                        successAlert.setContentText("梯度价格配置添加成功！");
                        successAlert.showAndWait();
                        
                        if (statusLabel != null) {
                            statusLabel.setText("添加梯度价格配置成功");
                        }
                        loadFineRateConfigs();
                        return new FineRateConfigItem();
                    } else {
                        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                        errorAlert.setTitle("添加失败");
                        errorAlert.setHeaderText(null);
                        errorAlert.setContentText("添加失败: " + response.getMessage());
                        errorAlert.showAndWait();
                    }
                } catch (Exception e) {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("添加失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("添加失败: " + e.getMessage());
                    errorAlert.showAndWait();
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    private void showUpdateFineRateConfigDialog() {
        FineRateConfigItem selected = fineRateConfigTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText("请先选择一条配置");
            alert.showAndWait();
            return;
        }
        
        Dialog<FineRateConfigItem> dialog = new Dialog<>();
        dialog.setTitle("修改梯度价格");
        dialog.setHeaderText("修改梯度价格配置");
        
        TextField dayRangeStartField = new TextField(String.valueOf(selected.getDayRangeStart()));
        TextField dayRangeEndField = new TextField(selected.getDayRangeEnd() != null ? String.valueOf(selected.getDayRangeEnd()) : "");
        TextField ratePerDayField = new TextField();
        TextField descriptionField = new TextField(selected.getDescription());
        TextField displayOrderField = new TextField(String.valueOf(selected.getDisplayOrder()));
        
        String rateStr = selected.getRatePerDay().replace("元/天", "");
        ratePerDayField.setText(rateStr);
        
        VBox vbox = new VBox(10);
        vbox.getChildren().addAll(
            new Label("起始天数:"), dayRangeStartField,
            new Label("结束天数 (留空表示无上限):"), dayRangeEndField,
            new Label("每日费率 (元):"), ratePerDayField,
            new Label("描述:"), descriptionField,
            new Label("显示顺序:"), displayOrderField
        );
        vbox.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(vbox);
        
        ButtonType updateButtonType = new ButtonType("修改", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(updateButtonType, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == updateButtonType) {
                try {
                    Request request = new Request();
                    request.setRequestId(java.util.UUID.randomUUID().toString());
                    request.setOpCode(OpCode.ADMIN_UPDATE_FINE_RATE_CONFIG);
                    request.setToken(session.getToken());
                    
                    ObjectNode payload = JsonUtil.createObjectNode();
                    payload.put("id", selected.getId());
                    payload.put("dayRangeStart", Integer.parseInt(dayRangeStartField.getText()));
                    if (!dayRangeEndField.getText().trim().isEmpty()) {
                        payload.put("dayRangeEnd", Integer.parseInt(dayRangeEndField.getText()));
                    } else {
                        payload.putNull("dayRangeEnd"); // 显式设置为null
                    }
                    payload.put("ratePerDay", Double.parseDouble(ratePerDayField.getText()));
                    payload.put("description", descriptionField.getText());
                    payload.put("displayOrder", Integer.parseInt(displayOrderField.getText()));
                    
                    request.setPayload(payload);
                    
                    Response response = client.send(request);
                    if (response.isSuccess()) {
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("修改成功");
                        successAlert.setHeaderText(null);
                        successAlert.setContentText("梯度价格配置修改成功！");
                        successAlert.showAndWait();
                        
                        if (statusLabel != null) {
                            statusLabel.setText("修改梯度价格配置成功");
                        }
                        loadFineRateConfigs();
                        return new FineRateConfigItem();
                    } else {
                        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                        errorAlert.setTitle("修改失败");
                        errorAlert.setHeaderText(null);
                        errorAlert.setContentText("修改失败: " + response.getMessage());
                        errorAlert.showAndWait();
                    }
                } catch (Exception e) {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("修改失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("修改失败: " + e.getMessage());
                    errorAlert.showAndWait();
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    private void deleteFineRateConfig() {
        FineRateConfigItem selected = fineRateConfigTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText("请先选择一条配置");
            alert.showAndWait();
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认删除");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("确定要删除这条梯度价格配置吗？");
        
        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                Request request = new Request();
                request.setRequestId(java.util.UUID.randomUUID().toString());
                request.setOpCode(OpCode.ADMIN_DELETE_FINE_RATE_CONFIG);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode().put("id", selected.getId()));
                
                Response response = client.send(request);
                if (response.isSuccess()) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("删除成功");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("梯度价格配置删除成功！");
                    successAlert.showAndWait();
                    
                    if (statusLabel != null) {
                        statusLabel.setText("删除梯度价格配置成功");
                    }
                    loadFineRateConfigs();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("删除失败");
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("删除失败: " + response.getMessage());
                    errorAlert.showAndWait();
                }
            } catch (Exception e) {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("删除失败");
                errorAlert.setHeaderText(null);
                errorAlert.setContentText("删除失败: " + e.getMessage());
                errorAlert.showAndWait();
            }
        }
    }
    
    public static class FineRateConfigItem {
        private Long id;
        private Integer dayRangeStart;
        private Integer dayRangeEnd;
        private String dayRange;
        private String ratePerDay;
        private String description;
        private Integer displayOrder;
        
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getDayRangeStart() { return dayRangeStart; }
        public void setDayRangeStart(Integer dayRangeStart) { this.dayRangeStart = dayRangeStart; }
        public Integer getDayRangeEnd() { return dayRangeEnd; }
        public void setDayRangeEnd(Integer dayRangeEnd) { this.dayRangeEnd = dayRangeEnd; }
        public String getDayRange() { return dayRange; }
        public void setDayRange(String dayRange) { this.dayRange = dayRange; }
        public String getRatePerDay() { return ratePerDay; }
        public void setRatePerDay(String ratePerDay) { this.ratePerDay = ratePerDay; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    }
}




