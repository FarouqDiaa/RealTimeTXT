package com.realtimetxt.client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.realtimetxt.client.logic.CRDTController;
import com.realtimetxt.client.network.ClientSocket;
import com.realtimetxt.shared.CRDTOperation;

public class EditorUI {

    private final Stage primaryStage;
    private TextArea textArea;
    private VBox userListBox;
    private Label viewerCodeLabel;
    private Label editorCodeLabel;
    private Label statusLabel;
    private Label userCountLabel;

    private String viewerCode = "";
    private String editorCode = "";
    private String currentUser = "Anonymous";
    private boolean isViewer = false;
    private String currentDocumentId = "";

    private CRDTController crdtController;
    private ClientSocket clientSocket;
    private final Map<String, UserCaret> remoteCursors = new ConcurrentHashMap<>();

    public EditorUI(Stage stage) {
        this.primaryStage = stage;
        initializeComponents();
        showUserLoginDialog();
    }

    private void initializeComponents() {
        this.textArea = new TextArea();
        this.textArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 14px;");
        this.textArea.setEditable(false);

        this.statusLabel = new Label("Please create or join a document");
        this.statusLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 14px;");

        this.viewerCodeLabel = new Label("Not available");
        this.editorCodeLabel = new Label("Not available");
        this.userCountLabel = new Label("ACTIVE USERS (0)");
        this.userListBox = new VBox(5);
    }

    private void showUserLoginDialog() {
        TextInputDialog dialog = new TextInputDialog("Anonymous");
        dialog.setTitle("Enter Your Name");
        dialog.setHeaderText("Welcome to RealTimeTXT");
        dialog.setContentText("Please enter your name:");

        dialog.showAndWait().ifPresent(name -> {
            currentUser = name;
            initializeNetworkComponents(UUID.randomUUID().toString());
            setupMainUI();
            primaryStage.setTitle("RealTimeTXT - " + currentUser);
        });
    }

    private void initializeNetworkComponents(String userId) {
        this.clientSocket = new ClientSocket(currentUser, userId, new ClientSocket.TextEditorCallback() {
            @Override
            public void onUserPresenceUpdate(Set<String> presenceUpdate) {
                Platform.runLater(() -> updateUserPresence(presenceUpdate));
            }

            @Override
            public void onFullStateUpdate(String content) {
                Platform.runLater(() -> {
                    System.out.println("Received full state update with content length: "
                            + (content != null ? content.length() : 0));

                    // Only update if we don't already have content
                    if (textArea.getText().isEmpty()
                            || !crdtController.renderText().equals(content)) {
                        crdtController.replaceAllContent(content);
                        updateText(content, false);
                    }
                });
            }

            @Override
            public void onCursorPositionUpdate(String username, int position) {
                Platform.runLater(() -> {
                    if (!username.equals(currentUser)) {
                        remoteCursors.put(username, new UserCaret(position));
                        updateUserList();
                    }
                });
            }

            @Override
            public void onDocumentCreated(String docId, String eCode, String vCode) {
                Platform.runLater(() -> handleDocumentCreated(docId, eCode, vCode));
            }

            @Override
            public void onDocumentJoined(String docId, boolean isEditor, List<CRDTOperation> ops) {
                Platform.runLater(() -> {
                    // Ensure we process operations before updating UI
                    handleDocumentJoined(docId, isEditor, ops);
                });
            }

            @Override
            public void onRemoteOperation(CRDTOperation operation) {
                Platform.runLater(() -> handleRemoteOperation(operation));
            }

            @Override
            public void onReconnected() {
                Platform.runLater(() -> {
                    statusLabel.setText("Reconnected to server");
                    if (currentDocumentId != null && !currentDocumentId.isEmpty()) {
                        // Resubscribe to document events
                        clientSocket.subscribeToDocument(currentDocumentId);
                    }
                });
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> showAlert("Disconnected: " + reason));
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> showAlert(error));
            }
        });

        this.crdtController = new CRDTController(clientSocket, userId);
    }

    private void handleDocumentCreated(String docId, String eCode, String vCode) {
        currentDocumentId = docId;
        editorCode = eCode;
        viewerCode = vCode;
        editorCodeLabel.setText(eCode);
        viewerCodeLabel.setText(vCode);
        statusLabel.setText("Document created - Editor mode");
        textArea.setEditable(true);
        isViewer = false;
        textArea.setStyle("");
    }

    private void handleDocumentJoined(String docId, boolean isEditor, List<CRDTOperation> ops) {
        currentDocumentId = docId;
        isViewer = !isEditor;

        // Update editor status first
        textArea.setEditable(isEditor);

        if (isEditor) {
            statusLabel.setText("Joined as editor - Editing enabled");
            textArea.setStyle("");
        } else {
            statusLabel.setText("Joined as viewer - View only");
            textArea.setStyle("-fx-control-inner-background: #f5f5f5;");
        }

        // Clear current state before applying operations
        crdtController.startNewDocument(new ArrayList<>());

        // Log the operations count received
        System.out.println("Received " + (ops != null ? ops.size() : 0) + " operations when joining document");

        // Apply operations immediately if we have them
        if (ops != null && !ops.isEmpty()) {
            Platform.runLater(() -> {
                try {
                    // Apply all operations in order
                    for (CRDTOperation op : ops) {
                        crdtController.onRemoteOperation(op);
                    }

                    // Render the text after all operations are applied
                    String textContent = crdtController.renderText();
                    System.out.println("Rendered text content length after joining: " + textContent.length());
                    textArea.setText(textContent);

                    // Send our cursor position after text is updated
                    clientSocket.sendCursorPosition(0);

                    // Request user presence information explicitly
                    clientSocket.requestUserPresence(docId);
                } catch (Exception e) {
                    System.err.println("Error applying operations: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            // If no operations, explicitly request full state with retry mechanism
            System.out.println("No operations received, requesting full state...");
            if (clientSocket.isConnected()) {
                requestFullStateWithRetry(docId);
            }
        }

        // Set up the window close handler to properly leave the document
        // Set up the window close handler to properly leave the document
        primaryStage.setOnCloseRequest(event -> {
            if (clientSocket != null && clientSocket.isConnected() && currentDocumentId != null
                    && !currentDocumentId.isEmpty()) {
                // Send leave document message to server before closing
                clientSocket.leaveDocument(currentDocumentId);
                clientSocket.disconnect();
                System.out.println("Document left and connection closed");
            }
        });
    }

    private void requestFullStateWithRetry(String docId) {
        System.out.println("Requesting full state for document: " + docId);

        clientSocket.sendFullStateRequest(docId);

        // Also explicitly request user presence
        clientSocket.requestUserPresence(docId);

        // Setup a retry timer in case we don't get a response
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    // Only retry if the text area is still empty
                    if (textArea.getText().isEmpty() && clientSocket.isConnected()) {
                        System.out.println("Retrying full state request");
                        clientSocket.sendFullStateRequest(docId);
                        clientSocket.requestUserPresence(docId);
                    }
                });
            }
        },
                2000 // 2-second delay before retry
        );
    }

    private void handleRemoteOperation(CRDTOperation operation) {
        crdtController.onRemoteOperation(operation);
        // Update text and ensure cursor positions are maintained
        Platform.runLater(() -> {
            int currentPos = textArea.getCaretPosition();
            String newText = crdtController.renderText();
            textArea.setText(newText);

            // Try to maintain cursor position within bounds
            if (currentPos <= textArea.getLength()) {
                textArea.positionCaret(currentPos);
            } else {
                textArea.positionCaret(textArea.getLength());
            }

            // Send our cursor position to keep other users informed
            // Added delay to ensure UI updates before sending cursor
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        clientSocket.sendCursorPosition(textArea.getCaretPosition());
                        // Force update of user list to reflect new line numbers
                        updateUserList();
                    });
                }
            },
                    100 // 100ms delay
            );
        });
    }

    private void setupMainUI() {
        BorderPane root = new BorderPane();
        root.setTop(createMenuBar());
        root.setCenter(createEditorArea());
        root.setLeft(createSidebar());

        Scene scene = new Scene(root, 900, 650);
        primaryStage.setScene(scene);

        // Window close handler is now moved to handleDocumentJoined
        // for a more appropriate timing after document joining
        primaryStage.show();
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File Menu
        Menu fileMenu = new Menu("File");
        MenuItem importItem = new MenuItem("Import");
        MenuItem exportItem = new MenuItem("Export");
        importItem.setOnAction(e -> importFile());
        exportItem.setOnAction(e -> exportFile());
        fileMenu.getItems().addAll(importItem, exportItem);

        // Edit Menu
        Menu editMenu = new Menu("Edit");
        MenuItem undoItem = new MenuItem("Undo (Ctrl+Z)");
        MenuItem redoItem = new MenuItem("Redo (Ctrl+Y)");
        undoItem.setOnAction(e -> performUndo());
        redoItem.setOnAction(e -> performRedo());
        editMenu.getItems().addAll(undoItem, redoItem);

        // Collaboration Menu
        Menu collabMenu = new Menu("Collaboration");
        MenuItem newDocItem = new MenuItem("New Document");
        MenuItem joinDocItem = new MenuItem("Join Document");
        newDocItem.setOnAction(e -> createNewDocument());
        joinDocItem.setOnAction(e -> showJoinDialog());
        collabMenu.getItems().addAll(newDocItem, joinDocItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, collabMenu);
        return menuBar;
    }

    private StackPane createEditorArea() {
        StackPane editorPane = new StackPane();
        VBox editorContainer = new VBox(5, statusLabel, textArea);
        editorContainer.setPadding(new Insets(10));
        editorPane.getChildren().add(editorContainer);

        // Text change listener
        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!isViewer && clientSocket != null && clientSocket.isConnected()) {
                crdtController.textChanged(newVal, textArea.getCaretPosition());
            }
        });

        // Cursor position listener
        textArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (clientSocket != null && clientSocket.isConnected() && currentDocumentId != null) {
                int position = newPos.intValue();
                remoteCursors.put(currentUser, new UserCaret(position));
                clientSocket.sendCursorPosition(position);

                // Debug cursor position
                System.out.println("Sending cursor position: " + position + " for " + currentUser);

                // Update UI to reflect current positions
                updateUserList();
            }
        });

        return editorPane;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(15));
        sidebar.setPrefWidth(250);
        sidebar.setStyle("-fx-background-color: #f5f5f5;");

        // Document Status
        VBox statusBox = new VBox(5);
        Label statusHeader = new Label("DOCUMENT STATUS");
        statusHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        statusBox.getChildren().addAll(statusHeader, statusLabel);

        // Sharing Codes
        VBox codesBox = new VBox(10);
        Label codesHeader = new Label("SHARING CODES");
        codesHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        HBox editorCodeBox = createCodeBox("Editor Code:", editorCodeLabel);
        HBox viewerCodeBox = createCodeBox("Viewer Code:", viewerCodeLabel);

        codesBox.getChildren().addAll(codesHeader, editorCodeBox, viewerCodeBox);

        // Active Users
        VBox usersBox = new VBox(5);
        userCountLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        updateUserList();
        usersBox.getChildren().addAll(userCountLabel, userListBox);

        sidebar.getChildren().addAll(statusBox, new Separator(), codesBox, new Separator(), usersBox);
        return sidebar;
    }

    private HBox createCodeBox(String labelText, Label codeLabel) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-weight: bold;");
        codeLabel.setStyle("-fx-background-color: #eee; -fx-padding: 3 8;");

        Button copyBtn = new Button("Copy");
        copyBtn.setStyle("-fx-padding: 3 8;");
        copyBtn.setOnAction(e -> copyToClipboard(codeLabel.getText()));

        HBox box = new HBox(5, label, codeLabel, copyBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void updateUserPresence(Set<String> presenceUpdate) {
        System.out.println("User presence update received with " + presenceUpdate.size() + " users: " + String.join(", ", presenceUpdate));

        // Always ensure current user is present in the list
        Set<String> allUsers = new HashSet<>(presenceUpdate);
        allUsers.add(currentUser);

        // Update remote cursors map
        for (String username : new HashSet<>(remoteCursors.keySet())) {
            if (!allUsers.contains(username) && !username.equals(currentUser)) {
                remoteCursors.remove(username);
            }
        }

        // Add all users in the update with preservation of existing positions
        for (String username : allUsers) {
            // Keep existing cursor positions if available
            if (!remoteCursors.containsKey(username)) {
                remoteCursors.put(username, new UserCaret(0));
            }
        }

        // Update the UI
        updateUserList();

        // After user list update, send our current cursor position again
        if (clientSocket != null && clientSocket.isConnected() && currentDocumentId != null) {
            int position = textArea.getCaretPosition();
            clientSocket.sendCursorPosition(position);
        }
    }

    private void updateUserList() {
        userListBox.getChildren().clear();

        // Create a distinct set of usernames
        Set<String> distinctUsers = new HashSet<>(remoteCursors.keySet());

        // Correct count is: all distinct users
        int userCount = distinctUsers.size();
        userCountLabel.setText("ACTIVE USERS (" + userCount + ")");

        // Current user at the top with green checkmark
        Label youLabel = new Label("✓ " + currentUser + " (you)");
        youLabel.setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
        userListBox.getChildren().add(youLabel);

        // Other users with line positions
        distinctUsers.stream()
                .filter(username -> !username.equals(currentUser))
                .sorted()
                .forEach(username -> {
                    UserCaret caret = remoteCursors.get(username);

                    if (caret != null) {
                        try {
                            int position = caret.getPosition();
                            // Get the current text to calculate line correctly
                            String currentText = textArea.getText();
                            int line = calculateLineNumber(currentText, position);
                            Label userLabel = new Label("• " + username + " (line " + line + ")");
                            userLabel.setStyle("-fx-text-fill: #1565C0;");
                            userListBox.getChildren().add(userLabel);

                            // Debug line information
                            System.out.println("User " + username + " at position " + position + " is on line " + line);
                        } catch (Exception e) {
                            Label userLabel = new Label("• " + username);
                            userLabel.setStyle("-fx-text-fill: #1565C0;");
                            userListBox.getChildren().add(userLabel);
                            System.err.println("Error calculating line for " + username + ": " + e.getMessage());
                        }
                    } else {
                        Label userLabel = new Label("• " + username);
                        userLabel.setStyle("-fx-text-fill: #1565C0;");
                        userListBox.getChildren().add(userLabel);
                    }
                });
    }

    private int calculateLineNumber(String text, int position) {
        if (text == null || text.isEmpty()) {
            return 1;
        }

        // Ensure position is within bounds
        position = Math.max(0, Math.min(position, text.length()));

        // Count newlines before position
        int line = 1;
        for (int i = 0; i < position && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }

        return line;
    }

    private int getLineNumber(int position) {
        return calculateLineNumber(textArea.getText(), position);
    }

    private void createNewDocument() {
        if (clientSocket.connect()) {
            clientSocket.createNewDocument();
        } else {
            showAlert("Failed to connect to server");
        }
    }

    private void showJoinDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Join Document");
        dialog.setHeaderText("Enter document code:");
        dialog.showAndWait().ifPresent(code -> {
            if (clientSocket.connect()) {
                clientSocket.joinDocument(code);
            }
        });
    }

    private void performUndo() {
        if (!isViewer) {
            crdtController.undo();
            updateText(crdtController.renderText(), true);
        }
    }

    private void performRedo() {
        if (!isViewer) {
            crdtController.redo();
            updateText(crdtController.renderText(), true);
        }
    }

    private void updateText(String text, boolean preserveCaret) {
        int caretPos = textArea.getCaretPosition();
        textArea.setText(text);
        if (preserveCaret) {
            textArea.positionCaret(Math.min(caretPos, text.length()));
        }
    }

    private void copyToClipboard(String text) {
        if (!text.equals("Not available")) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
            showAlert("Copied", "Code copied to clipboard!");
        }
    }

    private void importFile() {
        if (isViewer) {
            showAlert("Import Error", "Viewers cannot import files");
            return;
        }

        FileChooser fileChooser = new FileChooser();

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(primaryStage);

        if (file != null) {
            // Confirm with user if document isn't empty
            if (!textArea.getText().isEmpty()) {
                Alert alert = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Importing will replace current document. Continue?",
                        ButtonType.YES,
                        ButtonType.NO
                );

                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.YES) {
                        performImport(file);
                    }
                });
            } else {
                performImport(file);
            }
        }
    }

    private void performImport(File file) {
        try {
            // Read file with proper line ending handling
            String content = new String(Files.readAllBytes(file.toPath()));

            content = content.replace("\r\n", "\n"); // Normalize line endings

            if (clientSocket != null && clientSocket.isConnected() && currentDocumentId != null && !currentDocumentId.isEmpty()) {
                // Clear current CRDT state
                crdtController.importText(content);

                // Broadcast the updated full state to all users
                String finalText = crdtController.renderText();
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", crdtController.getUserId());
                payload.put("documentId", currentDocumentId);
                payload.put("content", finalText);
                clientSocket.stompSession.send("/app/document/" + currentDocumentId + "/fullState", payload);

                // Update local UI
                updateText(finalText, false);
                statusLabel.setText("File imported and synchronized with collaborators");
            } else {
                showAlert("Import Error", "Not connected to a document");
            }
        } catch (IOException e) {
            showAlert("Import Error", "Failed to read file: " + e.getMessage());
        }
    }

    private void exportFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(textArea.getText());
                showAlert("Exported", "Document saved successfully!");
            } catch (IOException e) {
                showAlert("Export Error", "Failed to save file: " + e.getMessage());
            }
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class UserCaret {

        private int position;

        public UserCaret(int position) {
            this.position = position;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }
    }
}
