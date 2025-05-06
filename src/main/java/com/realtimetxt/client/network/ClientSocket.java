package com.realtimetxt.client.network;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimetxt.shared.CRDTOperation;

public class ClientSocket {

    private final String SERVER_URL = "ws://localhost:8080/ws";
    private final int SOCKET_TIMEOUT = 5000;

    public StompSession stompSession;
    public SockJsClient sockJsClient;
    private String userId;
    private String username;
    private String documentId;
    private boolean isEditor;
    private TextEditorCallback callback;
    private boolean isReconnecting = false;
    private final List<CRDTOperation> pendingOperations = Collections.synchronizedList(new ArrayList<>());
    private Map<String, StompSession.Subscription> subscriptions = new HashMap<>();
    private int lastCursorPosition = 0;
    private boolean isProcessingUserUpdate = false;
    private long lastCursorUpdateTime = 0;
    private static final long CURSOR_UPDATE_THROTTLE_MS = 500; // Throttle cursor updates to 500ms

    public ClientSocket(String username, String userId, TextEditorCallback callback) {
        this.username = username;
        this.userId = userId;
        this.callback = callback;
    }

    public boolean isConnected() {
        return stompSession != null && stompSession.isConnected();
    }

    public boolean connect() {
        try {
            List<Transport> transports = Collections
                    .singletonList(new WebSocketTransport(new StandardWebSocketClient()));
            sockJsClient = new SockJsClient(transports);

            WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
            List<MessageConverter> converters = new ArrayList<>();
            converters.add(new MappingJackson2MessageConverter());
            converters.add(new StringMessageConverter());
            stompClient.setMessageConverter(new CompositeMessageConverter(converters));

            StompSessionHandler sessionHandler = new EditorStompSessionHandler();
            stompSession = stompClient.connect(SERVER_URL, sessionHandler).get(SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            return false;
        }
    }

    public void createNewDocument() {
        if (!isConnected() && !connect()) {
            callback.onError("Failed to connect to server");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);

        stompSession.send("/app/createDocument", payload);

        stompSession.subscribe("/topic/createResponse/" + userId, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> response = (Map<String, Object>) payload;
                if (response == null || !response.containsKey("success")) {
                    callback.onError("Invalid response from server");
                    return;
                }

                if (!(boolean) response.get("success")) {
                    String error = response.containsKey("errorMessage")
                            ? (String) response.get("errorMessage")
                            : "Unknown error";
                    callback.onError("Failed to create document: " + error);
                    return;
                }

                documentId = response.get("documentId").toString();
                String editorCode = response.get("editorCode").toString();
                String viewerCode = response.get("viewerCode").toString();
                isEditor = true;

                subscribeToDocument(documentId);
                callback.onDocumentCreated(documentId, editorCode, viewerCode);
            }
        });
    }

    public void joinDocument(String sharingCode) {
        if (sharingCode == null || sharingCode.isEmpty()) {
            callback.onError("Invalid sharing code");
            return;
        }

        if (!isConnected() && !connect()) {
            callback.onError("Failed to connect to server");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("sharingCode", sharingCode);

        // Log joining attempt for debugging
        System.out.println("Joining document with code: " + sharingCode);

        stompSession.send("/app/joinDocument", payload);

        stompSession.subscribe("/topic/joinResponse/" + userId, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> response = (Map<String, Object>) payload;
                if (response == null || !response.containsKey("success")) {
                    callback.onError("Invalid response from server");
                    return;
                }

                if (!(boolean) response.get("success")) {
                    String error = response.containsKey("errorMessage")
                            ? (String) response.get("errorMessage")
                            : "Unknown error";
                    callback.onError("Failed to join document: " + error);
                    return;
                }

                documentId = response.get("documentId").toString();
                isEditor = (boolean) response.get("isEditor");

                // Handle user list update
                if (response.containsKey("currentUsers")) {
                    try {
                        List<Map<String, Object>> usersList = (List<Map<String, Object>>) response.get("currentUsers");
                        Set<String> usernames = new HashSet<>();

                        for (Map<String, Object> userData : usersList) {
                            if (userData.containsKey("username")) {
                                usernames.add((String) userData.get("username"));
                            }
                        }

                        if (!usernames.isEmpty()) {
                            onUserPresenceUpdate(usernames);
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing user data: " + e.getMessage());
                    }
                }

                subscribeToDocument(documentId);
                callback.onDocumentJoined(documentId, isEditor);
            }
        });
    }

    public void subscribeToDocument(String docId) {
        System.out.println("Subscribing to document: " + docId);

        if (docId == null || docId.isEmpty()) {
            callback.onError("Invalid document ID");
            return;
        }

        // Unsubscribe from previous subscriptions if any
        if (documentId != null && !documentId.equals(docId)) {
            unsubscribeFromCurrentDocument();
        }

        // Subscribe to operations channel
        StompSession.Subscription operationsSub = stompSession.subscribe(
                "/topic/document/" + docId + "/operations",
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return CRDTOperation.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        CRDTOperation operation = (CRDTOperation) payload;
                        if (operation.getUserId().equals(userId)) {
                            return; // Ignore our own operations
                        }
                        callback.onRemoteOperation(operation);
                    }
                });

        // Store the subscription
        subscriptions.put("operations", operationsSub);

        // Subscribe to bulk operations channel
        StompSession.Subscription bulkOperationsSub = stompSession.subscribe(
                "/topic/document/" + docId + "/bulkOperations",
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return List.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // List<CRDTOperation> operations = (List<CRDTOperation>) payload;
                        ObjectMapper mapper = new ObjectMapper();
                        List<CRDTOperation> operations = mapper.convertValue(
                                payload,
                                new TypeReference<List<CRDTOperation>>() {
                                });
                        if (operations == null || operations.isEmpty()) {
                            System.err.println("Received empty bulk operation list");
                            return; // Ignore empty operations
                        }
                        for (CRDTOperation operation : operations) {
                            if (!operation.getUserId().equals(userId)) {
                                // return; // Ignore our own operations
                                callback.onRemoteOperation(operation);
                            }
                        }
                    }
                });

        // Store the subscription
        subscriptions.put("bulkOperations", bulkOperationsSub);

        // Subscribe to users channel with improved error handling
        StompSession.Subscription usersSub = stompSession.subscribe(
                "/topic/document/" + docId + "/users",
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return List.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        try {
                            List<Map<String, Object>> users = (List<Map<String, Object>>) payload;
                            Set<String> usernames = new HashSet<>();

                            for (Map<String, Object> user : users) {
                                String username = (String) user.get("username");
                                if (username != null) {
                                    usernames.add(username);
                                }
                            }

                            if (!usernames.isEmpty()) {
                                onUserPresenceUpdate(usernames);
                            }

                            // After receiving user presence update, send our cursor position
                            sendCursorPosition(lastCursorPosition);
                        } catch (Exception e) {
                            System.err.println("Error processing user list: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
        subscriptions.put("users", usersSub);

        // Subscribe to cursor positions channel with improved error handling
        StompSession.Subscription cursorsSub = stompSession.subscribe(
                "/topic/document/" + docId + "/cursors",
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        try {
                            Map<String, Object> cursorData = (Map<String, Object>) payload;
                            String cursorUserId = (String) cursorData.get("userId");
                            String username = (String) cursorData.get("username");
                            int position = Integer.parseInt(String.valueOf(cursorData.get("position")));

                            // Don't process our own cursor updates
                            if (!userId.equals(cursorUserId)) {
                                System.out.println(
                                        "Received cursor update from " + username + " at position " + position);
                                callback.onCursorPositionUpdate(username, position);
                            }
                        } catch (Exception e) {
                            System.err.println("Error processing cursor position: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
        subscriptions.put("cursors", cursorsSub);

        // Also announce our presence
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("documentId", docId);

        try {
            stompSession.send("/app/document/" + docId + "/announce", payload);
            System.out.println("Announced presence in document: " + docId);
        } catch (Exception e) {
            System.err.println("Error announcing presence: " + e.getMessage());
        }

        this.documentId = docId;
        requestCurrentState(docId);
    }

    private void requestCurrentState(String docId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("documentId", docId);

        try {
            stompSession.send("/app/document/" + docId + "/requestCurrentState", payload);
            System.out.println("Requested current state for document: " + docId);
        } catch (Exception e) {
            System.err.println("Error requesting current state: " + e.getMessage());
        }
    }

    private void unsubscribeFromCurrentDocument() {
        // Unsubscribe from all stored subscriptions
        for (StompSession.Subscription subscription : subscriptions.values()) {
            if (subscription != null) {
                subscription.unsubscribe();
            }
        }
        // Clear the subscriptions map
        subscriptions.clear();
    }

    public void sendOperation(CRDTOperation operation) {
        if (!isEditor) {
            callback.onError("You don't have permission to edit this document");
            return;
        }

        if (documentId == null) {
            callback.onError("Not connected to any document");
            return;
        }

        if (!isConnected()) {
            pendingOperations.add(operation);
            if (!isReconnecting) {
                isReconnecting = true;
                scheduleReconnection();
            }
            return;
        }

        stompSession.send("/app/document/" + documentId + "/operation", operation);
    }

    public void sendOperations(ArrayList<CRDTOperation> operations) {
        if (!isEditor) {
            callback.onError("You don't have permission to edit this document");
            return;
        }

        if (documentId == null) {
            callback.onError("Not connected to any document");
            return;
        }

        if (!isConnected()) {
            for (CRDTOperation operation : operations) {
                pendingOperations.add(operation);
            }
            if (!isReconnecting) {
                isReconnecting = true;
                scheduleReconnection();
            }
            return;
        }

        stompSession.send("/app/document/" + documentId + "/bulkOperation", operations);
    }

    public void sendCursorPosition(int position) {
        if (documentId == null || !isConnected()) {
            return;
        }

        // Skip if the position hasn't changed
        if (position == lastCursorPosition) {
            return;
        }

        // Throttle updates to prevent flooding
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCursorUpdateTime < CURSOR_UPDATE_THROTTLE_MS) {
            return;
        }

        this.lastCursorPosition = position;
        lastCursorUpdateTime = currentTime;

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("position", position);
        payload.put("documentId", documentId);

        try {
            stompSession.send("/app/document/" + documentId + "/cursor", payload);
            System.out.println(
                    "Sent cursor position: " + position + " for user: " + username + " in document: " + documentId);
        } catch (Exception e) {
            System.err.println("Failed to send cursor position: " + e.getMessage());
        }
    }

    public int getLastCursorPosition() {
        return lastCursorPosition;
    }

    synchronized public void onUserPresenceUpdate(Set<String> presenceUpdate) {
        try {
            // Prevent recursive calling by using a flag
            if (isProcessingUserUpdate) {
                return;
            }

            isProcessingUserUpdate = true;
            callback.onUserPresenceUpdate(presenceUpdate);

            // After user presence update, resend our cursor position, but only if it's been
            // a while
            if (documentId != null && isConnected()
                    && System.currentTimeMillis() - lastCursorUpdateTime > CURSOR_UPDATE_THROTTLE_MS) {
                sendCursorPosition(lastCursorPosition);
            }

            isProcessingUserUpdate = false;
        } catch (Exception e) {
            isProcessingUserUpdate = false;
            System.err.println("Error processing user presence update: " + e.getMessage());
        }
    }

    private void scheduleReconnection() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            try {
                if (connect()) {
                    isReconnecting = false;
                    if (documentId != null) {
                        subscribeToDocument(documentId);

                        // Send any pending operations
                        synchronized (pendingOperations) {
                            for (CRDTOperation op : pendingOperations) {
                                sendOperation(op);
                            }
                            pendingOperations.clear();
                        }

                        callback.onReconnected();
                    }
                } else {
                    scheduleReconnection();
                }
            } catch (Exception e) {
                System.err.println("Reconnection attempt failed: " + e.getMessage());
                scheduleReconnection();
            }
        }, 5, TimeUnit.SECONDS);
    }

    public boolean isEditor() {
        return isEditor;
    }

    public String getUserId() {
        return userId;
    }

    public void requestUserPresence(String docId) {
        if (!isConnected() || docId == null || docId.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("documentId", docId);

        // For debugging
        System.out.println("Explicitly requesting user presence for document: " + docId);

        stompSession.send("/app/document/" + docId + "/requestUserPresence", payload);
    }

    public void leaveDocument(String documentId) {
        if (!isConnected() || documentId == null || documentId.isEmpty()) {
            System.err.println("Cannot leave document: Not connected or invalid document ID");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("documentId", documentId);

        try {
            stompSession.send("/app/leaveDocument", payload);
            System.out.println("Sent leave document message for document: " + documentId);
        } catch (Exception e) {
            System.err.println("Failed to send leave document message: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (sockJsClient != null) {
            try {
                sockJsClient.stop();
                System.out.println("Disconnected from server");
            } catch (Exception e) {
                System.err.println("Error during disconnect: " + e.getMessage());
            }
        }
    }

    /**
     * Session handler for STOMP connections
     */
    private class EditorStompSessionHandler extends StompSessionHandlerAdapter {

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            System.out.println("Connected to WebSocket server");
            // When connected, make sure to initialize presence
            if (documentId != null && !documentId.isEmpty()) {
                sendCursorPosition(lastCursorPosition);
            }
        }

        @Override
        public void handleException(StompSession session, StompCommand command,
                StompHeaders headers, byte[] payload, Throwable exception) {
            System.err.println("WebSocket error: " + exception.getMessage());
            exception.printStackTrace();
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            System.err.println("WebSocket transport error: " + exception.getMessage());

            if (!isReconnecting) {
                isReconnecting = true;
                scheduleReconnection();
            }
        }

    }

    /**
     * Callback interface for UI updates
     */
    public interface TextEditorCallback {

        void onDocumentCreated(String documentId, String editorCode, String viewerCode);

        void onDocumentJoined(String documentId, boolean isEditor);

        void onRemoteOperation(CRDTOperation operation);

        void onRemoteBulkOperation(List<CRDTOperation> operations);

        void onUserPresenceUpdate(Set<String> presenceUpdate);

        void onReconnected();

        void onDisconnected(String reason);

        void onError(String message);

        void onCursorPositionUpdate(String username, int position);
    }
}
