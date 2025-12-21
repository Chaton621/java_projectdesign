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
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.UUID;

/**
 * 聊天视图 - 三栏布局
 */
public class ChatView {
    private final ClientApp app;
    private final SocketClient client;
    private final Session session;
    
    // 左侧：最近对话
    private ListView<ConversationItem> conversationList;
    private ObservableList<ConversationItem> conversations;
    
    // 中间：聊天窗口
    private VBox chatMessagesBox;
    private ScrollPane chatScrollPane;
    private TextField messageField;
    private Button sendButton;
    private Label chatTitleLabel;
    private Long currentChatUserId;
    
    // 右侧：用户搜索和推荐
    private TextField searchField;
    private ListView<UserItem> recommendedUsersList;
    private ObservableList<UserItem> recommendedUsers;
    
    // 轮询任务
    private javafx.concurrent.Task<Void> pollingTask;
    
    public ChatView(ClientApp app, SocketClient client, Session session) {
        this.app = app;
        this.client = client;
        this.session = session;
        this.conversations = FXCollections.observableArrayList();
        this.recommendedUsers = FXCollections.observableArrayList();
    }
    
    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");
        
        // 顶部栏
        HBox topBar = createTopBar();
        root.setTop(topBar);
        
        // 主内容区域：三栏布局
        HBox mainContent = new HBox();
        mainContent.setSpacing(0);
        
        // 左侧：最近对话
        VBox leftPanel = createLeftPanel();
        leftPanel.setPrefWidth(250);
        
        // 中间：聊天窗口
        VBox centerPanel = createCenterPanel();
        HBox.setHgrow(centerPanel, Priority.ALWAYS);
        
        // 右侧：用户搜索和推荐
        VBox rightPanel = createRightPanel();
        rightPanel.setPrefWidth(300);
        
        mainContent.getChildren().addAll(leftPanel, centerPanel, rightPanel);
        root.setCenter(mainContent);
        
        Scene scene = new Scene(root, 1400, 800);
        
        try {
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("无法加载CSS样式: " + e.getMessage());
        }
        
        // 加载初始数据
        loadRecentConversations();
        loadRecommendedUsers();
        startPolling();
        
        return scene;
    }
    
    private HBox createTopBar() {
        HBox topBar = new HBox(15);
        topBar.setPadding(new Insets(15, 20, 15, 20));
        topBar.setStyle("-fx-background-color: #2c3e50;");
        topBar.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("图书馆管理系统");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.WHITE);
        
        Label pageTitleLabel = new Label("聊天交友");
        pageTitleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        pageTitleLabel.setTextFill(Color.WHITE);
        pageTitleLabel.setPadding(new Insets(0, 0, 0, 20));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button backButton = new Button("返回");
        backButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8 20;");
        backButton.setOnAction(e -> app.showHomeView());
        
        topBar.getChildren().addAll(titleLabel, pageTitleLabel, spacer, backButton);
        return topBar;
    }
    
    private VBox createLeftPanel() {
        VBox leftPanel = new VBox();
        leftPanel.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 1 0 0;");
        
        Label titleLabel = new Label("最近对话");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        titleLabel.setPadding(new Insets(15, 15, 10, 15));
        
        conversationList = new ListView<>();
        conversationList.setItems(conversations);
        conversationList.setCellFactory(listView -> new ConversationListCell());
        conversationList.setOnMouseClicked(e -> {
            ConversationItem item = conversationList.getSelectionModel().getSelectedItem();
            if (item != null) {
                openConversation(item.getOtherUserId(), item.getOtherUsername());
            }
        });
        VBox.setVgrow(conversationList, Priority.ALWAYS);
        
        leftPanel.getChildren().addAll(titleLabel, conversationList);
        return leftPanel;
    }
    
    private VBox createCenterPanel() {
        VBox centerPanel = new VBox();
        centerPanel.setStyle("-fx-background-color: white;");
        
        // 聊天标题
        HBox titleBox = new HBox();
        titleBox.setPadding(new Insets(15, 20, 15, 20));
        titleBox.setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        
        chatTitleLabel = new Label("选择一个对话开始聊天");
        chatTitleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        titleBox.getChildren().add(chatTitleLabel);
        
        // 聊天消息区域
        chatScrollPane = new ScrollPane();
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setStyle("-fx-background: white;");
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        chatMessagesBox = new VBox(10);
        chatMessagesBox.setPadding(new Insets(15));
        chatMessagesBox.setStyle("-fx-background-color: white;");
        chatScrollPane.setContent(chatMessagesBox);
        VBox.setVgrow(chatScrollPane, Priority.ALWAYS);
        
        // 输入区域
        HBox inputBox = new HBox(10);
        inputBox.setPadding(new Insets(15, 20, 15, 20));
        inputBox.setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");
        
        messageField = new TextField();
        messageField.setPromptText("输入消息...");
        messageField.setPrefWidth(600);
        messageField.setOnAction(e -> sendMessage());
        HBox.setHgrow(messageField, Priority.ALWAYS);
        
        sendButton = new Button("发送");
        sendButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8 20;");
        sendButton.setOnAction(e -> sendMessage());
        sendButton.setDisable(true);
        
        inputBox.getChildren().addAll(messageField, sendButton);
        
        centerPanel.getChildren().addAll(titleBox, chatScrollPane, inputBox);
        return centerPanel;
    }
    
    private VBox createRightPanel() {
        VBox rightPanel = new VBox(15);
        rightPanel.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 0 1;");
        rightPanel.setPadding(new Insets(15));
        
        // 搜索用户
        Label searchTitleLabel = new Label("搜索用户");
        searchTitleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        
        HBox searchBox = new HBox(10);
        searchField = new TextField();
        searchField.setPromptText("输入用户名...");
        searchField.setPrefWidth(200);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        Button searchButton = new Button("搜索");
        searchButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 15;");
        searchButton.setOnAction(e -> searchUsers());
        searchField.setOnAction(e -> searchUsers());
        
        searchBox.getChildren().addAll(searchField, searchButton);
        
        // 推荐用户
        Label recommendTitleLabel = new Label("推荐用户");
        recommendTitleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        recommendTitleLabel.setPadding(new Insets(10, 0, 0, 0));
        
        recommendedUsersList = new ListView<>();
        recommendedUsersList.setItems(recommendedUsers);
        recommendedUsersList.setCellFactory(listView -> new UserListCell());
        VBox.setVgrow(recommendedUsersList, Priority.ALWAYS);
        
        rightPanel.getChildren().addAll(searchTitleLabel, searchBox, recommendTitleLabel, recommendedUsersList);
        return rightPanel;
    }
    
    private void loadRecentConversations() {
        new Thread(() -> {
            try {
                Request request = new Request();
                request.setRequestId(UUID.randomUUID().toString());
                request.setOpCode(OpCode.GET_RECENT_CONVERSATIONS);
                request.setToken(session.getToken());
                
                Response response = client.send(request);
                Platform.runLater(() -> {
                    if (response.isSuccess() && response.getData() != null) {
                        JsonNode conversationsNode = response.getData().get("conversations");
                        conversations.clear();
                        
                        if (conversationsNode != null && conversationsNode.isArray()) {
                            for (JsonNode convNode : conversationsNode) {
                                ConversationItem item = new ConversationItem();
                                item.setOtherUserId(convNode.has("otherUserId") ? convNode.get("otherUserId").asLong() : 0);
                                item.setOtherUsername(convNode.has("otherUsername") ? convNode.get("otherUsername").asText() : "");
                                item.setLastMessage(convNode.has("lastMessage") ? convNode.get("lastMessage").asText() : "");
                                item.setLastMessageTime(convNode.has("lastMessageTime") ? convNode.get("lastMessageTime").asText() : "");
                                item.setUnread(convNode.has("isUnread") && convNode.get("isUnread").asBoolean());
                                conversations.add(item);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void loadRecommendedUsers() {
        new Thread(() -> {
            try {
                Request request = new Request();
                request.setRequestId(UUID.randomUUID().toString());
                request.setOpCode(OpCode.RECOMMEND_USERS);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode().put("topN", 20));
                
                Response response = client.send(request);
                Platform.runLater(() -> {
                    if (response.isSuccess() && response.getData() != null) {
                        JsonNode usersNode = response.getData().get("users");
                        recommendedUsers.clear();
                        
                        if (usersNode != null && usersNode.isArray()) {
                            for (JsonNode userNode : usersNode) {
                                UserItem item = new UserItem();
                                item.setUserId(userNode.has("userId") ? userNode.get("userId").asLong() : 0);
                                item.setUsername(userNode.has("username") ? userNode.get("username").asText() : "");
                                item.setSimilarity(userNode.has("similarity") ? userNode.get("similarity").asDouble() : 0.0);
                                item.setCommonBooks(userNode.has("commonBooks") ? userNode.get("commonBooks").asInt() : 0);
                                recommendedUsers.add(item);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void searchUsers() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            return;
        }
        
        new Thread(() -> {
            try {
                Request request = new Request();
                request.setRequestId(UUID.randomUUID().toString());
                request.setOpCode(OpCode.SEARCH_USERS);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode().put("keyword", keyword));
                
                Response response = client.send(request);
                Platform.runLater(() -> {
                    if (response.isSuccess() && response.getData() != null) {
                        JsonNode usersNode = response.getData().get("users");
                        recommendedUsers.clear();
                        
                        if (usersNode != null && usersNode.isArray()) {
                            for (JsonNode userNode : usersNode) {
                                UserItem item = new UserItem();
                                item.setUserId(userNode.has("userId") ? userNode.get("userId").asLong() : 0);
                                item.setUsername(userNode.has("username") ? userNode.get("username").asText() : "");
                                item.setSimilarity(0.0);
                                item.setCommonBooks(0);
                                recommendedUsers.add(item);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void openConversation(Long otherUserId, String otherUsername) {
        currentChatUserId = otherUserId;
        chatTitleLabel.setText("与 " + otherUsername + " 的对话");
        sendButton.setDisable(false);
        
        loadConversation(otherUserId);
    }
    
    private void loadConversation(Long otherUserId) {
        new Thread(() -> {
            try {
                Request request = new Request();
                request.setRequestId(UUID.randomUUID().toString());
                request.setOpCode(OpCode.GET_CONVERSATION);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode()
                    .put("otherUserId", otherUserId)
                    .put("limit", 50)
                    .put("offset", 0));
                
                Response response = client.send(request);
                Platform.runLater(() -> {
                    if (response.isSuccess() && response.getData() != null) {
                        JsonNode messagesNode = response.getData().get("messages");
                        chatMessagesBox.getChildren().clear();
                        
                        if (messagesNode != null && messagesNode.isArray()) {
                            // 反转消息列表，使最早的消息在顶部
                            java.util.List<JsonNode> messages = new java.util.ArrayList<>();
                            for (JsonNode msgNode : messagesNode) {
                                messages.add(0, msgNode);
                            }
                            
                            for (JsonNode msgNode : messages) {
                                boolean isMine = msgNode.has("isMine") && msgNode.get("isMine").asBoolean();
                                String content = msgNode.has("content") ? msgNode.get("content").asText() : "";
                                String time = msgNode.has("time") ? msgNode.get("time").asText() : "";
                                
                                HBox messageBox = createMessageBubble(isMine, content, time);
                                chatMessagesBox.getChildren().add(messageBox);
                            }
                            
                            // 滚动到底部
                            Platform.runLater(() -> {
                                chatScrollPane.setVvalue(1.0);
                            });
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private HBox createMessageBubble(boolean isMine, String content, String time) {
        HBox messageBox = new HBox(10);
        messageBox.setPadding(new Insets(5, 10, 5, 10));
        
        VBox bubbleBox = new VBox(5);
        bubbleBox.setPadding(new Insets(8, 12, 8, 12));
        bubbleBox.setMaxWidth(400);
        
        Label contentLabel = new Label(content);
        contentLabel.setWrapText(true);
        contentLabel.setFont(Font.font("Microsoft YaHei", 13));
        contentLabel.setTextFill(Color.BLACK);
        
        Label timeLabel = new Label(time);
        timeLabel.setFont(Font.font("Microsoft YaHei", 10));
        timeLabel.setTextFill(Color.GRAY);
        
        bubbleBox.getChildren().addAll(contentLabel, timeLabel);
        
        if (isMine) {
            // 我的消息：右对齐，蓝色背景
            HBox.setHgrow(messageBox, Priority.ALWAYS);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            messageBox.getChildren().add(spacer);
            bubbleBox.setStyle("-fx-background-color: #3498db; -fx-background-radius: 10;");
            contentLabel.setTextFill(Color.WHITE);
            timeLabel.setTextFill(Color.rgb(255, 255, 255, 0.8));
        } else {
            // 对方的消息：左对齐，灰色背景
            bubbleBox.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 10;");
        }
        
        messageBox.getChildren().add(bubbleBox);
        return messageBox;
    }
    
    private void sendMessage() {
        if (currentChatUserId == null) {
            return;
        }
        
        String content = messageField.getText().trim();
        if (content.isEmpty()) {
            return;
        }
        
        new Thread(() -> {
            try {
                Request request = new Request();
                request.setRequestId(UUID.randomUUID().toString());
                request.setOpCode(OpCode.SEND_MESSAGE);
                request.setToken(session.getToken());
                request.setPayload(JsonUtil.createObjectNode()
                    .put("receiverId", currentChatUserId)
                    .put("content", content));
                
                Response response = client.send(request);
                Platform.runLater(() -> {
                    if (response.isSuccess()) {
                        messageField.clear();
                        // 重新加载对话
                        loadConversation(currentChatUserId);
                        loadRecentConversations();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void startPolling() {
        pollingTask = new javafx.concurrent.Task<Void>() {
            @Override
            protected Void call() throws Exception {
                while (!isCancelled()) {
                    try {
                        Thread.sleep(3000); // 每3秒轮询一次
                        
                        // 刷新最近对话列表
                        Platform.runLater(() -> {
                            loadRecentConversations();
                            if (currentChatUserId != null) {
                                loadConversation(currentChatUserId);
                            }
                        });
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                return null;
            }
        };
        
        new Thread(pollingTask).start();
    }
    
    // 内部类：对话项
    public static class ConversationItem {
        private Long otherUserId;
        private String otherUsername;
        private String lastMessage;
        private String lastMessageTime;
        private boolean unread;
        
        public Long getOtherUserId() { return otherUserId; }
        public void setOtherUserId(Long otherUserId) { this.otherUserId = otherUserId; }
        public String getOtherUsername() { return otherUsername; }
        public void setOtherUsername(String otherUsername) { this.otherUsername = otherUsername; }
        public String getLastMessage() { return lastMessage; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
        public String getLastMessageTime() { return lastMessageTime; }
        public void setLastMessageTime(String lastMessageTime) { this.lastMessageTime = lastMessageTime; }
        public boolean isUnread() { return unread; }
        public void setUnread(boolean unread) { this.unread = unread; }
    }
    
    // 内部类：用户项
    public static class UserItem {
        private Long userId;
        private String username;
        private double similarity;
        private int commonBooks;
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public double getSimilarity() { return similarity; }
        public void setSimilarity(double similarity) { this.similarity = similarity; }
        public int getCommonBooks() { return commonBooks; }
        public void setCommonBooks(int commonBooks) { this.commonBooks = commonBooks; }
    }
    
    // 对话列表单元格
    private class ConversationListCell extends ListCell<ConversationItem> {
        @Override
        protected void updateItem(ConversationItem item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
            } else {
                VBox vbox = new VBox(5);
                vbox.setPadding(new Insets(10, 15, 10, 15));
                
                HBox nameBox = new HBox(10);
                Label nameLabel = new Label(item.getOtherUsername());
                nameLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
                
                if (item.isUnread()) {
                    nameLabel.setStyle("-fx-text-fill: #3498db;");
                }
                
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                Label timeLabel = new Label(item.getLastMessageTime());
                timeLabel.setFont(Font.font("Microsoft YaHei", 10));
                timeLabel.setTextFill(Color.GRAY);
                
                nameBox.getChildren().addAll(nameLabel, spacer, timeLabel);
                
                Label messageLabel = new Label(item.getLastMessage());
                messageLabel.setFont(Font.font("Microsoft YaHei", 11));
                messageLabel.setTextFill(Color.GRAY);
                messageLabel.setMaxWidth(200);
                messageLabel.setWrapText(true);
                
                vbox.getChildren().addAll(nameBox, messageLabel);
                
                if (item.isUnread()) {
                    vbox.setStyle("-fx-background-color: #e8f4f8;");
                } else {
                    vbox.setStyle("-fx-background-color: white;");
                }
                
                setGraphic(vbox);
            }
        }
    }
    
    // 用户列表单元格
    private class UserListCell extends ListCell<UserItem> {
        @Override
        protected void updateItem(UserItem item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
            } else {
                VBox vbox = new VBox(8);
                vbox.setPadding(new Insets(10, 15, 10, 15));
                
                HBox nameBox = new HBox(10);
                Label nameLabel = new Label(item.getUsername());
                nameLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
                
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                Button chatButton = new Button("聊天");
                chatButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 4 12;");
                chatButton.setOnAction(e -> {
                    openConversation(item.getUserId(), item.getUsername());
                });
                
                nameBox.getChildren().addAll(nameLabel, spacer, chatButton);
                
                if (item.getSimilarity() > 0 || item.getCommonBooks() > 0) {
                    HBox infoBox = new HBox(15);
                    if (item.getSimilarity() > 0) {
                        Label similarityLabel = new Label(String.format("相似度: %.1f%%", item.getSimilarity() * 100));
                        similarityLabel.setFont(Font.font("Microsoft YaHei", 11));
                        similarityLabel.setTextFill(Color.GRAY);
                        infoBox.getChildren().add(similarityLabel);
                    }
                    if (item.getCommonBooks() > 0) {
                        Label commonLabel = new Label(String.format("共同借阅: %d本", item.getCommonBooks()));
                        commonLabel.setFont(Font.font("Microsoft YaHei", 11));
                        commonLabel.setTextFill(Color.GRAY);
                        infoBox.getChildren().add(commonLabel);
                    }
                    vbox.getChildren().add(infoBox);
                }
                
                vbox.getChildren().add(0, nameBox);
                vbox.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
                
                setGraphic(vbox);
            }
        }
    }
}


