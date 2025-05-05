package com.realtimetxt.client.network;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
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
import com.realtimetxt.server.SessionManager;
import com.realtimetxt.shared.CRDTOperation;

/**
 * Client WebSocket handler for collaborative text editor using STOMP protocol
 */
public class ClientSocket {

    private final String SERVER_URL = "ws://localhost:8080/ws";
    private final int SOCKET_TIMEOUT = 5000; // 5 seconds

    private StompSession stompSession;
    private SockJsClient sockJsClient;
    private String userId;
    private String username;
    private String documentId;
    private boolean isEditor;
    private TextEditorCallback callback;
    private boolean isReconnecting = false;
    private final List<CRDTOperation> pendingOperations = Collections.synchronizedList(new ArrayList<>());

    /**
     * Constructor
     *
     * @param username The user's display name
     * @param userId   The user's unique identifier
     * @param callback Callback interface for UI updates
     */
    public ClientSocket(String username, String userId, TextEditorCallback callback) {
        this.username = username;
        this.userId = userId;
        this.callback = callback;
    }

    public boolean isConnected() {
        return stompSession != null && stompSession.isConnected();
    }

    /**
     * Connect to the WebSocket server
     *
     * @return true if connection is successful, false otherwise
     */
    public boolean connect() {
        try {
            List<Transport> transports = Collections
                    .singletonList(new WebSocketTransport(new StandardWebSocketClient()));
            sockJsClient = new SockJsClient(transports);

            WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);

            // Set up message converters for JSON and String
            List<MessageConverter> converters = new ArrayList<>();
            converters.add(new MappingJackson2MessageConverter());
            converters.add(new StringMessageConverter());
            stompClient.setMessageConverter(new CompositeMessageConverter(converters));

            // Connect to the server with timeout
            StompSessionHandler sessionHandler = new EditorStompSessionHandler();
            stompSession = stompClient.connect(SERVER_URL, sessionHandler).get(SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);

            System.out.println("Connected to collaborative text editor server as " + username);
            return true;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create a new document and get sharing codes
     */
    public void createNewDocument() {
        if (stompSession == null || !stompSession.isConnected()) {
            if (connect()) {
                requestNewDocument();
            } else {
                callback.onError("Failed to connect to server");
            }
        } else {
            requestNewDocument();
        }
    }

    private void requestNewDocument() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);

        stompSession.send("/app/createDocument", payload);

        // Subscribe to document creation response
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

                boolean success = (boolean) response.get("success");
                if (!success) {
                    String errorMessage = response.containsKey("errorMessage")
                            ? (String) response.get("errorMessage")
                            : "Unknown error";
                    callback.onError("Failed to create document: " + errorMessage);
                    return;
                }

                if (!response.containsKey("documentId") || !response.containsKey("editorCode")
                        || !response.containsKey("viewerCode")) {
                    callback.onError("Invalid server response: missing document information");
                    return;
                }

                documentId = response.get("documentId").toString();
                String editorCode = response.get("editorCode").toString();
                String viewerCode = response.get("viewerCode").toString();
                isEditor = true; // Creator is always an editor

                // Subscribe to document events after creation
                subscribeToDocument(documentId);
                callback.onDocumentCreated(documentId, editorCode, viewerCode);
            }
        });
    }

    /**
     * Join an existing document with a sharing code
     *
     * @param sharingCode The code for joining the document (editor or viewer)
     */
    public void joinDocument(String sharingCode) {
        if (sharingCode == null || sharingCode.isEmpty()) {
            callback.onError("Invalid sharing code");
            return;
        }

        if (stompSession == null || !stompSession.isConnected()) {
            if (connect()) {
                requestJoinDocument(sharingCode);
            } else {
                callback.onError("Failed to connect to server");
            }
        } else {
            requestJoinDocument(sharingCode);
        }
    }

    private void requestJoinDocument(String sharingCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("sharingCode", sharingCode);
        System.out.println("Joining document with sharing code before send: " + sharingCode);
        stompSession.send("/app/joinDocument", payload);
        System.out.println("Joining document with sharing code after send: " + sharingCode);

        // Subscribe to join response
        stompSession.subscribe("/topic/joinResponse/" + userId, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("Received join response: ");
                Map<String, Object> response = (Map<String, Object>) payload;
                if (response == null || !response.containsKey("success")) {
                    callback.onError("Invalid response from server");
                    return;
                }

                boolean success = (boolean) response.get("success");
                if (!success) {
                    String errorMessage = response.containsKey("errorMessage")
                            ? (String) response.get("errorMessage")
                            : "Unknown error";
                    callback.onError("Failed to join document: " + errorMessage);
                    return;
                }

                documentId = response.get("documentId").toString();
                isEditor = (boolean) response.get("isEditor");

                // Handle existing data if available
                List<CRDTOperation> existingOperations = new ArrayList<>();
                if (response.containsKey("existingData")) {
                    ObjectMapper mapper = new ObjectMapper();
                    existingOperations = mapper.convertValue(
                            response.get("existingData"),
                            new TypeReference<List<CRDTOperation>>() {
                            });
                }

                // Subscribe to document events
                subscribeToDocument(documentId);

                // Notify UI that we've joined
                callback.onDocumentJoined(documentId, isEditor, existingOperations);
            }
        });
    }

    /**
     * Subscribe to all relevant document events
     *
     * @param docId The document ID to subscribe to
     */
    private void subscribeToDocument(String docId) {
        if (docId == null || docId.isEmpty()) {
            callback.onError("Invalid document ID");
            return;
        }

        // Subscribe to CRDT operations
        stompSession.subscribe("/topic/document/" + docId + "/operations", new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return CRDTOperation.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                CRDTOperation operation = (CRDTOperation) payload;
                System.out.println("Received operation: " + operation.getOperation());
                // Skip own operations as they are applied locally
                if (!userId.equals(operation.getUserId())) {
                    callback.onRemoteOperation(operation);
                }
            }
        });

        // Subscribe to user presence updates
        stompSession.subscribe("/topic/document/" + documentId + "/users", new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("Received user presence update: " + payload);
                Map<String, Set<String>> presenceUpdate = (Map<String, Set<String>>) payload;

                System.out.println("Presence update: " + presenceUpdate.get("users"));

                Set<String> usernames = presenceUpdate.get("users");
                    
                    callback.onUserPresenceUpdate(usernames);
                
            }
        });
    }

    /**
     * Send a text operation to the server
     *
     * @param operation The CRDT operation to send
     */
    public void sendOperation(CRDTOperation operation) {
        if (!isEditor) {
            callback.onError("You don't have permission to edit this document");
            return;
        }

        if (documentId == null) {
            callback.onError("Not connected to any document");
            return;
        }

        if (stompSession == null || !stompSession.isConnected()) {
            // Store operation for later if we're disconnected
            System.err.println("Stomp session is null or not connected. Storing operation for later.");
            pendingOperations.add(operation);
            if (!isReconnecting) {
                isReconnecting = true;
                scheduleReconnection();
            }
            return;
        }

        System.out.println("Sending operation: " + operation.getOperation());
        String destination = "/app/document/" + documentId + "/operation";
        stompSession.send(destination, operation);
    }

    /**
     * Schedule periodic reconnection attempts
     */
    private void scheduleReconnection() {
        new Thread(() -> {
            try {
                // Try to reconnect every 5 seconds for up to 5 minutes
                for (int i = 0; i < 60 && isReconnecting; i++) {
                    System.out.println("Attempting to reconnect... (" + (i + 1) + "/60)");
                    if (connect()) {
                        isReconnecting = false;

                        // Resubscribe to document
                        if (documentId != null) {
                            subscribeToDocument(documentId);

                            // Send any pending operations
                            List<CRDTOperation> operationsToSend;
                            synchronized (pendingOperations) {
                                operationsToSend = new ArrayList<>(pendingOperations);
                                pendingOperations.clear();
                            }

                            for (CRDTOperation op : operationsToSend) {
                                sendOperation(op);
                            }

                            callback.onReconnected();
                        }
                        return;
                    }

                    Thread.sleep(5000); // Wait 5 seconds before retrying
                }

                // If we couldn't reconnect after 5 minutes
                isReconnecting = false;
                callback.onDisconnected("Could not reconnect after 5 minutes");
            } catch (InterruptedException e) {
                System.err.println("Reconnection thread interrupted: " + e.getMessage());
                isReconnecting = false;
            }
        }).start();
    }

    /**
     * Close the WebSocket connection
     */
    public void disconnect() {
        isReconnecting = false; // Stop any reconnection attempts

        if (stompSession != null) {
            // Send a leave message if we're in a document
            if (documentId != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", userId);
                payload.put("documentId", documentId);
                payload.put("username", username);

                try {
                    stompSession.send("/app/leaveDocument", payload);
                } catch (Exception e) {
                    System.err.println("Error sending leave message: " + e.getMessage());
                }
            }

            try {
                stompSession.disconnect();
                System.out.println("Disconnected from server");
            } catch (Exception e) {
                System.err.println("Error disconnecting session: " + e.getMessage());
            } finally {
                stompSession = null;
            }
        }

        if (sockJsClient != null) {
            try {
                sockJsClient.stop();
            } catch (Exception e) {
                System.err.println("Error stopping sockJsClient: " + e.getMessage());
            } finally {
                sockJsClient = null;
            }
        }

        // Clear document state
        documentId = null;
        isEditor = false;
        pendingOperations.clear();
    }

    /**
     * Session handler for STOMP connections
     */
    private class EditorStompSessionHandler extends StompSessionHandlerAdapter {

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            System.out.println("Connected to WebSocket server");
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

        void onDocumentJoined(String documentId, boolean isEditor, List<CRDTOperation> existingOperations);

        void onRemoteOperation(CRDTOperation operation);

        void onUserPresenceUpdate(Set<String> presenceUpdate);

        void onReconnected();

        void onDisconnected(String reason);

        void onError(String message);
    }
}
