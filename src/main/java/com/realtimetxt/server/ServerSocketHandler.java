package com.realtimetxt.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimetxt.client.logic.CRDT;
import com.realtimetxt.client.logic.CRDTItem;
import com.realtimetxt.shared.CRDTOperation;
import com.realtimetxt.shared.enums.OperationType;

@Controller
public class ServerSocketHandler {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SessionManager manager;

    private final Map<String, List<CRDTOperation>> documentData = new ConcurrentHashMap<>();

    @MessageMapping("/createDocument")
    public void createDocument(@Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String username = (String) payload.get("username");

        if (userId == null || username == null) {
            sendErrorResponse("/topic/createResponse/" + userId, "User ID and username are required");
            return;
        }

        String documentId = UUID.randomUUID().toString();
        Map<String, String> codes = manager.createSession(documentId);

        manager.joinSession(userId, codes.get("editor"), username);
        documentData.put(documentId, new CopyOnWriteArrayList<>());
        manager.addUserToDocument(documentId, userId, username, true);

        sendUserListUpdate(documentId);
        messagingTemplate.convertAndSend("/topic/createResponse/" + userId, Map.of(
                "documentId", documentId,
                "editorCode", codes.get("editor"),
                "viewerCode", codes.get("viewer"),
                "success", true));

        System.out.println("Created document: " + documentId + " for user: " + userId);
    }

    @MessageMapping("/joinDocument")
    public void joinDocument(@Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String username = (String) payload.get("username");
        String sharingCode = (String) payload.get("sharingCode");

        if (userId == null || username == null || sharingCode == null) {
            sendErrorResponse("/topic/joinResponse/" + userId, "User ID, username, and sharing code are required");
            return;
        }

        String documentId = manager.getDocumentFromCode(sharingCode);
        if (documentId == null) {
            sendErrorResponse("/topic/joinResponse/" + userId, "Document not found");
            return;
        }

        // Check if the code is valid and get the role
        String role = manager.getCodeRole(sharingCode);
        if (role == null) {
            sendErrorResponse("/topic/joinResponse/" + userId, "Invalid sharing code");
            return;
        }

        boolean isEditor = "editor".equals(role);

        if (!manager.joinSession(userId, sharingCode, username)) {
            sendErrorResponse("/topic/joinResponse/" + userId, "Failed to join session with provided sharing code");
            return;
        }

        // Add user to document with proper role
        manager.addUserToDocument(documentId, userId, username, isEditor);

        // Get the current document operations with proper serialization
        sendUserListUpdate(documentId);

        // Send response to joining user with correctly serialized data
        Map<String, Object> response = new HashMap<>();
        response.put("documentId", documentId);
        response.put("isEditor", isEditor);
        response.put("success", true);
        response.put("currentUsers", manager.getDocumentUsers(documentId).stream()
                .map(u -> Map.of(
                        "username", u.getUsername(),
                        "isEditor", u.isEditor()))
                .collect(Collectors.toList()));

        messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);

        System.out.println(
                "User " + userId + " joined document " + documentId + " as " + (isEditor ? "editor" : "viewer"));

        for (CRDTOperation operation : documentData.getOrDefault(documentId, Collections.emptyList())) {
            messagingTemplate.convertAndSend("/topic/document/" + documentId + "/operations/" + userId, operation);
        }
    }

    private boolean validateEditorRole(String userId, String documentId) {
        if (userId == null || documentId == null) {
            return false;
        }

        String role = manager.getUserRole(userId, documentId);
        return role != null && "editor".equals(role);
    }

    @MessageMapping("/document/{documentId}/requestCurrentState")
    public void requestCurrentState(@DestinationVariable String documentId, @Payload Map<String, Object> payload) {
        System.out.println("Requesting current state for document: " + documentId);
        String userId = (String) payload.get("userId");

        System.out.println("User ID: " + userId);

        // TODO: Validate user role

        if (userId == null || documentId == null) {
            sendErrorResponse("/topic/document/" + documentId + "/currentState/" + userId,
                    "User ID and document ID are required");
            return;
        }

        // Send the current state of the document to the user
        List<CRDTOperation> operations = documentData.getOrDefault(documentId, Collections.emptyList());
        ArrayList<CRDTOperation> operationsToSend = new ArrayList<CRDTOperation>();
        int i = 0;
        while (i < operations.size()) {
            operationsToSend.clear();
            // Send only 10 operations at a time to avoid overwhelming the client
            while (operationsToSend.size() < 10 && i < operations.size()) {
                operationsToSend.add(operations.get(i));
                i++;
            }
            System.out.println("Sending operations: " + i + " of " + operations.size());
            messagingTemplate.convertAndSend("/topic/document/" + documentId + "/bulkOperations", operationsToSend);
        }
    }

    @MessageMapping("/document/{documentId}/operation")
    public void handleOperation(@DestinationVariable String documentId, @Payload CRDTOperation operation) {
        String userId = operation.getUserId();

        if (!validateEditorRole(userId, documentId)) {
            System.err.println("Unauthorized operation from user: " + userId);
            return;
        }

        // Check for duplicate operations by looking at the itemId
        synchronized (documentData) {
            boolean isDuplicate = documentData.getOrDefault(documentId, Collections.emptyList())
                    .stream()
                    .anyMatch(op -> op.getId().equals(operation.getId()));

            if (isDuplicate) {
                System.err.println("Duplicate operation detected for id: " + operation.getId());
                return;
            }

            documentData.computeIfAbsent(documentId, k -> new CopyOnWriteArrayList<>()).add(operation);

            // Broadcast to all subscribers
            messagingTemplate.convertAndSend("/topic/document/" + documentId + "/operations", operation);
        }
    }

    @MessageMapping("/document/{documentId}/bulkOperation")
    public void handleBulkOperation(@DestinationVariable String documentId, @Payload List<CRDTOperation> operations) {
        for (CRDTOperation operation : operations) {
            String userId = operation.getUserId();
            if (!validateEditorRole(userId, documentId)) {
                System.err.println("Unauthorized operation from user: " + userId);
                return;
            }

            // Check for duplicate operations by looking at the itemId
            synchronized (documentData) {
                boolean isDuplicate = documentData.getOrDefault(documentId, Collections.emptyList())
                        .stream()
                        .anyMatch(op -> op.getId().equals(operation.getId()));

                if (isDuplicate) {
                    System.err.println("Duplicate operation detected for id: " + operation.getId());
                    return;
                }

                documentData.computeIfAbsent(documentId, k -> new CopyOnWriteArrayList<>()).add(operation);
            }
        }

        // Send all operations to the document topic
        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/bulkOperations", operations);
    }

    @MessageMapping("/leaveDocument")
    public void leaveDocument(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userId = (String) payload.get("userId");
        String documentId = (String) payload.get("documentId");
        String username = (String) payload.get("username"); // Add username to payload

        if (userId == null || documentId == null) {
            System.err.println("Error: User ID and document ID are required to leave a document");
            return;
        }

        System.out.println("User " + userId + " is leaving document " + documentId);

        // Remove user from document in session manager
        manager.removeUserFromDocument(documentId, userId);

        // Clear session attributes
        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().clear();
        }

        // Force immediate update to all clients
        sendUserListUpdate(documentId);

        // Log user leaving
        System.out.println("User " + username + " (" + userId + ") has left document " + documentId);

        // Add a slightly longer delay to ensure message propagation
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @MessageMapping("/document/{documentId}/cursor")
    public void handleCursorPosition(
            @DestinationVariable String documentId,
            @Payload Map<String, Object> payload) {

        String userId = (String) payload.get("userId");
        String username = (String) payload.get("username");
        int position = (int) payload.get("position");

        // Refresh user presence
        manager.refreshUserPresence(documentId, userId);

        // Broadcast cursor position to all subscribers
        try {
            messagingTemplate.convertAndSend("/topic/document/" + documentId + "/cursors", Map.of(
                    "userId", userId,
                    "username", username,
                    "position", position));

            // Don't send a user list update on every cursor position to avoid feedback
            // loops
            // Only send user list updates if a user actually joins or leaves
            // sendUserListUpdate(documentId); <-- Comment out this line
            System.out.println("Broadcasting cursor position for " + username + " at position " + position);
        } catch (Exception e) {
            System.err.println("Error broadcasting cursor position: " + e.getMessage());
        }
    }

    private void sendErrorResponse(String destination, String errorMessage) {
        messagingTemplate.convertAndSend(destination, Map.of("success", false, "errorMessage", errorMessage));
        System.err.println("Error response sent to " + destination + ": " + errorMessage);
    }

    private void sendUserListUpdate(String documentId) {
        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/users",
                manager.getDocumentUsers(documentId).stream()
                        .map(user -> Map.of(
                                "username", user.getUsername(),
                                "isEditor", user.isEditor()))
                        .collect(Collectors.toList()));
    }

}
