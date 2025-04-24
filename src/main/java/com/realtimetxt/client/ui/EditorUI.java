package com.realtimetxt.client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EditorUI {
    private Stage primaryStage;
    private TextArea textArea;
    // A map to hold remote cursors with userId as key and UserCaret as value
    private Map<String, UserCaret> remoteCursors = new ConcurrentHashMap<>();

    private String viewerCode = "#yq1xrx";
    private String editorCode = "#1jEo2K";
    private String currentUser = "Anonymous Frog";
    private VBox userListBox;
    private Label viewerCodeLabel;
    private Label editorCodeLabel;

    public EditorUI(Stage stage) {
        this.primaryStage = stage;
        initUI();
    }

    private void initUI() {
        primaryStage.setTitle("Realtime Text Editor");
        BorderPane borderPane = new BorderPane();

        HBox toolbar = createToolbar();
        borderPane.setTop(toolbar);

        StackPane editorPane = new StackPane();
        textArea = new TextArea();
        textArea.setStyle("-fx-font-family: monospace; -fx-font-size: 14px;");

        textArea.textProperty().addListener((obs, oldText, newText) -> updateRemoteCursors());
        textArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            // Send caret position to server (to be implemented by backend)
        });

        editorPane.getChildren().add(textArea);
        borderPane.setCenter(editorPane);

        VBox sidebar = createSidebar();
        borderPane.setLeft(sidebar);

        Scene scene = new Scene(borderPane, 800, 600);
        primaryStage.setScene(scene);
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: #f4f4f4;");

        Button backButton = new Button("←");
        Button forwardButton = new Button("→");
        Button exportButton = new Button("Export");
        exportButton.setOnAction(e -> exportFile());

        toolbar.getChildren().addAll(backButton, forwardButton, exportButton);
        return toolbar;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(15));
        sidebar.setPrefWidth(200);
        sidebar.setStyle("-fx-background-color: #f8f8f8;");

        Label viewerHeader = new Label("Viewer Code");
        viewerHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        HBox viewerCodeBox = new HBox(10);
        viewerCodeLabel = new Label(viewerCode);
        viewerCodeLabel.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5px 10px;");
        Button copyViewerButton = new Button("Copy");
        copyViewerButton.setOnAction(e -> copyToClipboard(viewerCode));
        viewerCodeBox.getChildren().addAll(viewerCodeLabel, copyViewerButton);

        Label editorHeader = new Label("Editor Code");
        editorHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        HBox editorCodeBox = new HBox(10);
        editorCodeLabel = new Label(editorCode);
        editorCodeLabel.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5px 10px;");
        Button copyEditorButton = new Button("Copy");
        copyEditorButton.setOnAction(e -> copyToClipboard(editorCode));
        editorCodeBox.getChildren().addAll(editorCodeLabel, copyEditorButton);

        Label usersHeader = new Label("Active Users");
        usersHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        userListBox = new VBox(5);
        updateUserList();

        sidebar.getChildren().addAll(
                viewerHeader, viewerCodeBox,
                editorHeader, editorCodeBox,
                usersHeader, userListBox);

        return sidebar;
    }

    private void updateUserList() {
        userListBox.getChildren().clear();

        HBox currentUserBox = new HBox(5);
        Label userLabel = new Label(currentUser + " (you)");
        userLabel.setTextFill(Color.DARKGREEN);
        currentUserBox.getChildren().add(userLabel);
        userListBox.getChildren().add(currentUserBox);

        for (Map.Entry<String, UserCaret> entry : remoteCursors.entrySet()) {
            String userId = entry.getKey();
            UserCaret caret = entry.getValue();
            int lineNumber = getLineNumber(caret.getPosition());

            HBox userBox = new HBox(5);
            Label userLabelRemote = new Label(userId + " - line " + lineNumber);
            userLabelRemote.setTextFill(caret.getColor());

            userBox.getChildren().add(userLabelRemote);
            userListBox.getChildren().add(userBox);
        }
    }

    private int getLineNumber(int position) {
        String text = textArea.getText();
        if (position > text.length())
            position = text.length();
        return (int) text.substring(0, position).chars().filter(ch -> ch == '\n').count() + 1;
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        showNotification("Copied to clipboard!");
    }

    private void showNotification(String message) {
        System.out.println(message);
    }

    private void exportFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Document");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(primaryStage);

        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(textArea.getText());
                showNotification("File saved successfully!");
            } catch (IOException e) {
                showAlert("Error saving file: " + e.getMessage());
            }
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

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

        // Visual representation of remote cursors (e.g., highlights or colored carets)
        // Backend sync and rendering not implemented in this UI stub
    }

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

    private static class UserCaret {
        private int position;
        private Color color;

        public UserCaret(int position, Color color) {
            this.position = position;
            this.color = color;
        }

        public int getPosition() {
            return position;
        }

        public Color getColor() {
            return color;
        }

        public void setPosition(int position) {
            this.position = position;
        }
    }
}
