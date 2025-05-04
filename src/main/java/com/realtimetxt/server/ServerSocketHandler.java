package com.realtimetxt.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.realtimetxt.client.network.ClientSocket;
import com.realtimetxt.shared.CRDTOperation;
import com.realtimetxt.shared.enums.OperationType;

/**
 * Controller handling WebSocket communication for the collaborative text editor
 */
@Controller
public class ServerSocketHandler {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SessionManager sessionManager;

    // Document storage - in a production system, this would be in a database
    private final Map<String, String> documentContents = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> userCursors = new ConcurrentHashMap<>();

    /**
     * Handles document creation requests
     */
    @MessageMapping("/createDocument")
    public void createDocument(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userId = (String) payload.get("userId");
        String username = (String) payload.get("username");

        // Store user information in session
        headerAccessor.getSessionAttributes().put("userId", userId);
        headerAccessor.getSessionAttributes().put("username", username);

        // Generate a new document ID
        String documentId = UUID.randomUUID().toString();

        // Create sharing codes
        Map<String, String> codes = sessionManager.createSession(documentId);
        String editorCode = codes.get("editor");
        String viewerCode = codes.get("viewer");

        // Initialize document content
        documentContents.put(documentId, "");
        userCursors.put(documentId, new ConcurrentHashMap<>());

        // Join the user to the document as editor
        sessionManager.joinSession(userId, editorCode);

        // Send response to the client
        Map<String, Object> response = new HashMap<>();
        response.put("documentId", documentId);
        response.put("editorCode", editorCode);
        response.put("viewerCode", viewerCode);

        messagingTemplate.convertAndSendToUser(userId, "/queue/documentCreated", response);

        System.out.println("Created document: " + documentId + " for user: " + userId);
    }

    /**
     * Handles document join requests
     */
    @MessageMapping("/joinDocument")
    public void joinDocument(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userId = (String) payload.get("userId");
        String username = (String) payload.get("username");
        String sharingCode = (String) payload.get("sharingCode");

        // Store user information in session
        headerAccessor.getSessionAttributes().put("userId", userId);
        headerAccessor.getSessionAttributes().put("username", username);

        Map<String, Object> response = new HashMap<>();

        // Validate sharing code
        if (!sessionManager.isValidDocument(sharingCode)) {
            response.put("success", false);
            response.put("errorMessage", "Invalid sharing code");
            messagingTemplate.convertAndSendToUser(userId, "/queue/joinResponse", response);
            return;
        }

        // Get document ID from code
        String documentId = sessionManager.getDocumentFromCode(sharingCode);
        boolean isEditor = "editor".equals(sessionManager.getCodeRole(sharingCode));

        // Join the user to the document
        sessionManager.joinSession(userId, sharingCode);

        // Get initial document content
        String content = documentContents.getOrDefault(documentId, "");

        // Setup user cursor tracking
        userCursors.putIfAbsent(documentId, new ConcurrentHashMap<>());
        userCursors.get(documentId).put(userId, 0);

        // Send join response
        response.put("success", true);
        response.put("documentId", documentId);
        response.put("editor", isEditor);
        response.put("initialContent", content);

        messagingTemplate.convertAndSendToUser(userId, "/queue/joinResponse", response);

        // Notify other users about the new user
        notifyUserPresence(documentId, userId, username, true);

        System.out.println("User " + userId + " joined document " + documentId + " as " +
                (isEditor ? "editor" : "viewer"));
    }

    /**
     * Handles document operations (inserts/deletes)
     */
    @MessageMapping("/document/{documentId}/operation")
    public void handleOperation(@DestinationVariable String documentId,
            @Payload CRDTOperation operation,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = (String) headerAccessor.getSessionAttributes().get("userId");

        // Validate user is in the correct document with edit permissions
        if (!documentId.equals(sessionManager.getUserDocument(userId)) ||
                !"editor".equals(sessionManager.getUserRole(userId, documentId))) {
            return;
        }

        // Process the operation locally (append operation info)
        // operation.setTimestamp(System.currentTimeMillis());

        // Broadcast to all document users
        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/operations", operation);

        System.out.println("Operation from user " + userId + " on document " + documentId +
                ": " + operation.getOperation() +
                (operation.getValue() != null ? " '" + operation.getValue() + "'" : ""));
    }

    /**
     * Handles cursor position updates
     */
    @MessageMapping("/document/{documentId}/cursor")
    public void handleCursorUpdate(@DestinationVariable String documentId,
            @Payload ClientSocket.CursorUpdate cursorUpdate,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = cursorUpdate.getUserId();
        String username = cursorUpdate.getUsername();
        Integer position = cursorUpdate.getPosition();

        // Validate user is in the correct document
        if (!documentId.equals(sessionManager.getUserDocument(userId))) {
            return;
        }

        // Update user cursor position
        userCursors.get(documentId).put(userId, position);

        // Broadcast to all document users - reuse the received object
        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/cursors", cursorUpdate);
    }

    /**
     * Handles user leaving the document
     */
    @MessageMapping("/leaveDocument")
    public void leaveDocument(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userId = (String) payload.get("userId");
        String documentId = (String) payload.get("documentId");
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        // Remove user from session
        sessionManager.leaveSession(userId, documentId);

        // Remove user cursor
        if (userCursors.containsKey(documentId)) {
            userCursors.get(documentId).remove(userId);
        }

        // Notify other users
        notifyUserPresence(documentId, userId, username, false);

        System.out.println("User " + userId + " left document " + documentId);
    }

    /**
     * Notifies all users in a document about a user joining or leaving
     */
    private void notifyUserPresence(String documentId, String userId, String username, boolean isJoining) {
        Map<String, Object> presenceUpdate = new HashMap<>();
        presenceUpdate.put("userId", userId);
        presenceUpdate.put("username", username);
        presenceUpdate.put("joining", isJoining);

        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/users", presenceUpdate);
    }

    /**
     * Method called by Spring when a WebSocket session ends
     * Can be used to handle unexpected disconnections
     */
    public void afterConnectionClosed(String userId) {
        String documentId = sessionManager.getUserDocument(userId);
        if (documentId != null) {
            String username = "Unknown"; // In a real implementation, store username in session

            // Clean up user data
            sessionManager.leaveSession(userId, documentId);
            if (userCursors.containsKey(documentId)) {
                userCursors.get(documentId).remove(userId);
            }

            // Notify other users
            notifyUserPresence(documentId, userId, username, false);
        }
    }
}