package com.realtimetxt.client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

import com.realtimetxt.client.logic.CRDTController;
import com.realtimetxt.client.network.ClientSocket;
import com.realtimetxt.shared.CRDTOperation;

public class EditorUI implements ClientSocket.TextEditorCallback {

    @Override
    public void onRemoteOperation(CRDTOperation operation) {
        Platform.runLater(() -> {
            showNotification("Remote operation received");
            // TODO: Apply the CRDT operation to the local document
        });
    }

    @Override
    public void onDocumentCreated(String documentId, String editorCode, String viewerCode) {
        Platform.runLater(() -> {
            showNotification("Document created with ID: " + documentId);
            setEditorCode(editorCode);
            setViewerCode(viewerCode);
        });
    }

    @Override
    public void onDocumentJoined(String documentId, boolean isEditor, List<CRDTOperation> operations) {
        Platform.runLater(() -> {
            showNotification("Joined document: " + documentId);
            StringBuilder initialContent = new StringBuilder();
            for (CRDTOperation operation : operations) {
                crdtController.onRemoteOperation(operation);
            }
            initialContent.append(crdtController.renderText());
            textArea.setText(initialContent.toString());
        });
    }

    @Override
    public void onUserPresenceUpdate(Map<String, Object> presenceUpdate) {
        Platform.runLater(() -> {
            showNotification("User presence updated: " + presenceUpdate);
            // TODO: Handle user presence update logic here
        });
    }

    @Override
    public void onReconnected() {
        Platform.runLater(() -> {
            showNotification("Reconnected to the server");
        });
    }

    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> {
            showAlert("Disconnected: " + reason);
        });
    }

    @Override
    public void onError(String error) {
        Platform.runLater(() -> {
            showAlert(error);
        });
    }

    private Stage primaryStage;
    private TextArea textArea;
    private VBox userListBox;
    private Label viewerCodeLabel;
    private Label editorCodeLabel;

    private String viewerCode = "";
    private String editorCode = "";
    private String currentUser = "";

    private boolean isViewer = false;
    private Label messageLabel;

    private final CRDTController crdtController = new CRDTController();
    private ClientSocket clientSocket; // Client socket for network communication

    private final Map<String, UserCaret> remoteCursors = new ConcurrentHashMap<>();

    // ...existing code...
    public EditorUI(Stage stage) {
        this.primaryStage = stage;

        // Prompt user for their name
        TextInputDialog dialog = new TextInputDialog("Anonymous Frog");
        dialog.setTitle("Enter Your Name");
        dialog.setHeaderText("Welcome to RealTimeTXT");
        dialog.setContentText("Please enter your name:");

        dialog.showAndWait().ifPresent(name -> {
            currentUser = name;
            String authenticatedUserId = UUID.randomUUID().toString(); // Generate a random ID for this user
            this.clientSocket = new ClientSocket(name, authenticatedUserId, new ClientSocket.TextEditorCallback() {
                @Override
                public void onUserPresenceUpdate(Map<String, Object> presenceUpdate) {
                    Platform.runLater(() -> {
                        showNotification("User presence updated: " + presenceUpdate);
                        // TODO: Handle user presence update logic here
                    });
                }

                @Override
                public void onDocumentCreated(String documentId, String editorCode, String viewerCode) {
                    Platform.runLater(() -> {
                        showNotification("Document created with ID: " + documentId);
                        setEditorCode(editorCode);
                        setViewerCode(viewerCode);
                        showNotification("New document created with ID: " + documentId);
                    });
                }

                @Override
                public void onDocumentJoined(String documentId, boolean isEditor, List<CRDTOperation> operations) {
                    Platform.runLater(() -> {
                        showNotification("Joined document: " + documentId);
                        isViewer = !isEditor;

                        StringBuilder initialContent = new StringBuilder();
                        for (CRDTOperation operation : operations) {
                            crdtController.onRemoteOperation(operation);
                        }
                        initialContent.append(crdtController.renderText());
                        textArea.setText(initialContent.toString());

                        // Disable editing if the user is a viewer
                        textArea.setEditable(!isViewer);
                    });
                }

                @Override
                public void onRemoteOperation(CRDTOperation operation) {
                    Platform.runLater(() -> {
                        showNotification("Remote operation received");
                        // TODO: Apply the CRDT operation to the local document
                    });
                }

                @Override
                public void onReconnected() {
                    Platform.runLater(() -> {
                        showNotification("Reconnected to the server");
                    });
                }

                @Override
                public void onDisconnected(String reason) {
                    Platform.runLater(() -> {
                        showAlert("Disconnected: " + reason);
                    });
                }

                @Override
                public void onError(String error) {
                    Platform.runLater(() -> {
                        showAlert(error);
                    });
                }
            });
        });

        initUI();
    }

    private void initUI() {
        primaryStage.setTitle("Realtime Text Editor");

        BorderPane root = new BorderPane();
        root.setTop(createMenuBar());
        root.setCenter(createEditorArea());
        root.setLeft(createSidebar());

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
    }

    // ──────────────────────────────── Menu Bar (Collaboration & File)
    // ────────────────────────────────
    private String fetchViewerCodeFromServer() {
        if (clientSocket == null || !clientSocket.connect()) {
            showAlert("Failed to connect to server");
            return "";
        }

        clientSocket.createNewDocument();
        // The codes will be set via the callback in onDocumentCreated
        // viewerCode = "t3bt";
        return viewerCode;
    }

    private String fetchEditorCodeFromServer() {
        // Simulate fetching editor code from the server
        // TODO : Implement actual server call to fetch editor code
        // For now, iam return a hardcoded value
        // editorCode = "t3bt2";// this is a placeholder
        return editorCode;
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
        MenuItem undoItem = new MenuItem("Undo");
        MenuItem redoItem = new MenuItem("Redo");

        // Add TODO comments for Undo and Redo logic
        undoItem.setOnAction(e -> {
            // TODO: Implement Undo functionality
            showNotification("Undo action triggered (logic not implemented).");
        });

        redoItem.setOnAction(e -> {
            // TODO: Implement Redo functionality
            showNotification("Redo action triggered (logic not implemented).");
        });

        editMenu.getItems().addAll(undoItem, redoItem);

        // Collaboration Menu
        Menu collabMenu = new Menu("Collaboration");
        MenuItem requestCodesItem = new MenuItem("Request Session Codes");
        MenuItem joinCollabItem = new MenuItem("Join Collaboration");

        requestCodesItem.setOnAction(e -> {
            // Simulate fetching session codes from the server
            String fetchedViewerCode = fetchViewerCodeFromServer();
            String fetchedEditorCode = fetchEditorCodeFromServer();
            setViewerCode(fetchedViewerCode);
            setEditorCode(fetchedEditorCode);

            // Enable editing and remove the message
            textArea.setEditable(true);
            messageLabel.setText(""); // Clear the message
            showNotification("Session codes updated! Editing is now enabled.");
        });

        joinCollabItem.setOnAction(e -> showJoinSessionDialog());

        collabMenu.getItems().addAll(requestCodesItem, joinCollabItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, collabMenu);

        return menuBar;
    }

    private void showJoinSessionDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Join Collaboration");
        dialog.setHeaderText("Enter the session code:");
        dialog.setContentText("Session Code:");

        dialog.showAndWait().ifPresent(code -> {
            if (clientSocket != null) {
                // Connect if not already connected
                if (!clientSocket.isConnected()) {
                    clientSocket.connect();
                }
                // Actually join the document with the entered code
                clientSocket.joinDocument(code);
                showNotification("Attempting to join session with code: " + code);
            } else {
                showAlert("Client socket not initialized");
            }
        });
    }

    // ──────────────────────────────── Editor Area ────────────────────────────────
    private StackPane createEditorArea() {
        StackPane editorPane = new StackPane();
        textArea = new TextArea();
        textArea.setStyle("-fx-font-family: monospace; -fx-font-size: 14px;");
        textArea.setEditable(false); // Initially set to non-editable

        // Add a message label
        messageLabel = new Label("Editing is disabled. Please request a session code to start editing.");
        messageLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px;");

        VBox editorContainer = new VBox(10, messageLabel, textArea);
        editorContainer.setPadding(new Insets(10));
        editorPane.getChildren().add(editorContainer);

        return editorPane;
    }

    // ──────────────────────────────── Sidebar ────────────────────────────────
    private VBox createSidebar() {
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(15));
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: #f8f8f8;");

        // Viewer Code Section
        Label viewerHeader = createHeaderLabel("Viewer Code");
        viewerCodeLabel = createCodeLabel(viewerCode);
        Button copyViewerButton = new Button("Copy");
        copyViewerButton.setOnAction(e -> copyToClipboard(viewerCode));
        HBox viewerBox = new HBox(10, viewerCodeLabel, copyViewerButton);

        // Editor Code Section
        Label editorHeader = createHeaderLabel("Editor Code");
        editorCodeLabel = createCodeLabel(editorCode);
        Button copyEditorButton = new Button("Copy");
        copyEditorButton.setOnAction(e -> copyToClipboard(editorCode));
        HBox editorBox = new HBox(10, editorCodeLabel, copyEditorButton);

        // Active Users Section
        Label usersHeader = createHeaderLabel("Active Users");
        userListBox = new VBox(5);
        updateUserList();

        sidebar.getChildren().addAll(
                viewerHeader, viewerBox,
                editorHeader, editorBox,
                usersHeader, userListBox);

        return sidebar;
    }

    private Label createHeaderLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        return label;
    }

    private Label createCodeLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5px 10px;");
        return label;
    }

    // ──────────────────────────────── File Import/Export
    // ────────────────────────────────
    private void importFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Document");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showOpenDialog(primaryStage);

        if (file != null) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                textArea.setText(content.toString().trim());
                showNotification("File imported successfully!");
            } catch (IOException e) {
                showAlert("Error reading file: " + e.getMessage());
            }
        }

    }

    private void exportFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Document");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(primaryStage);

        if (file != null) {
            if (file.exists()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Overwrite File");
                confirm.setHeaderText("The file already exists.");
                confirm.setContentText("Do you want to overwrite it?");
                confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

                confirm.showAndWait();
                if (confirm.getResult() != ButtonType.YES) {
                    return;
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(textArea.getText());
                showAlertInfo("Success", "File saved successfully!");
            } catch (IOException e) {
                showAlert("Error saving file: " + e.getMessage());
            }
        }
    }

    private void showAlertInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ──────────────────────────────── Utility ────────────────────────────────
    private void showNotification(String message) {
        System.out.println(message);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        showNotification("Copied to clipboard!");
    }

    // ──────────────────────────────── Remote Cursor & User List
    // ────────────────────────────────
    public void addRemoteCursor(String userId, int position, Color color) {
        Platform.runLater(() -> {
            remoteCursors.put(userId, new UserCaret(position, color));
            updateRemoteCursors();
            updateUserList();
        });
    }

    public void removeRemoteCursor(String userId) {
        Platform.runLater(() -> {
            remoteCursors.remove(userId);
            updateUserList();
        });
    }

    private void updateRemoteCursors() {
        // TODO: Update the remote cursors in the text area
    }

    private void updateUserList() {
        userListBox.getChildren().clear();
        Label youLabel = new Label(currentUser + " (you)");
        youLabel.setTextFill(Color.DARKGREEN);
        userListBox.getChildren().add(new HBox(5, youLabel));

        remoteCursors.forEach((userId, caret) -> {
            int line = getLineNumber(caret.getPosition());
            Label label = new Label(userId + " - line " + line);
            label.setTextFill(caret.getColor());
            userListBox.getChildren().add(new HBox(5, label));
        });
    }

    private int getLineNumber(int position) {
        String text = textArea.getText(); // Get the all text from the TextArea
        position = Math.min(position, text.length()); // Ensure position is within bounds
        return (int) text.substring(0, position).chars().filter(ch -> ch == '\n').count() + 1; // Count the number of
        // newlines before the
        // position
    }

    // ──────────────────────────────── Text Updates & Codes
    // ────────────────────────────────
    public void updateText(String newText, boolean preserveCaret) {
        Platform.runLater(() -> {
            int caretPosition = textArea.getCaretPosition();
            textArea.setText(newText);
            if (preserveCaret) {
                textArea.positionCaret(Math.min(caretPosition, newText.length()));
            }
        });
    }

    public String getText() {
        return textArea.getText();
    }

    public void setViewerCode(String code) {
        this.viewerCode = code;
        Platform.runLater(() -> viewerCodeLabel.setText(code));
    }

    public void setEditorCode(String code) {
        this.editorCode = code;
        Platform.runLater(() -> editorCodeLabel.setText(code));
    }

    // ──────────────────────────────── Client Socket Callbacks
    // ────────────────────────────────

    public static class UserCaret {

        private int position;
        private final Color color;

        public UserCaret(int position, Color color) {
            this.position = position;
            this.color = color;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public Color getColor() {
            return color;
        }
    }
}
