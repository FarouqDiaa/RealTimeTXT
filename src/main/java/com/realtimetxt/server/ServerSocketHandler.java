package com.realtimetxt.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.realtimetxt.shared.CRDTOperation;

@Controller
public class ServerSocketHandler {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SessionManager manager;

    // Store document data for retrieval
    private Map<String, List<CRDTOperation>> documentData = new ConcurrentHashMap<>();

    @MessageMapping("/createDocument")
    public void createDocument(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) payload.get("username");
        if (username == null) {
            username = "Guest";
        }
        String userId = (String) payload.get("userId");
        if (userId == null) {
            System.err.println("Error: User ID is required");
            return;
        }
        String documentId = UUID.randomUUID().toString();
        var codes = manager.createSession(documentId);
        String editorCode = codes.get("editor");
        String viewerCode = codes.get("viewer");

        manager.joinSession(userId, editorCode);

        // Initialize document data
        documentData.put(documentId, new CopyOnWriteArrayList<>());

        Map<String, Object> response = new HashMap<>();
        response.put("documentId", documentId);
        response.put("editorCode", editorCode);
        response.put("viewerCode", viewerCode);
        response.put("username", username);
        response.put("userId", userId);

        notifyUserPresence(documentId, userId, username, true);

        messagingTemplate.convertAndSend("/topic/document/" + documentId, response);

        System.out.println("Created document: " + documentId + " for user: " + userId);
    }

    @MessageMapping("/joinDocument")
    public void joinDocument(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) payload.get("username");
        if (username == null) {
            username = "Guest";
        }
        String userId = (String) payload.get("userId");
        if (userId == null) {
            System.err.println("Error: User ID is required");
            return;
        }
        String sharingCode = (String) payload.get("sharingCode");
        if (sharingCode == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("errorMessage", "Sharing code is required");
            messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);
            return;
        }

        Map<String, Object> response = new HashMap<>();

        if (!manager.isEditorOrViewerCode(sharingCode)) {
            response.put("success", false);
            response.put("errorMessage", "Invalid sharing code");
            messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);
            return;
        }

        String documentId = manager.getDocumentFromCode(sharingCode);
        if (documentId == null) {
            response.put("success", false);
            response.put("errorMessage", "Document not found");
            messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);
            return;
        }

        boolean isEditor = manager.isEditorCode(sharingCode, documentId);
        if (isEditor) {
            // Check if user is already in the document as editor
            if (manager.isUserInDocument(userId, documentId, "editor")) {
                response.put("success", false);
                response.put("errorMessage", "Already joined as editor");
                messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);
                return;
            }
        } else {
            // Check if user is already in the document as viewer
            if (manager.isUserInDocument(userId, documentId, "viewer")) {
                response.put("success", false);
                response.put("errorMessage", "Already joined as viewer");
                messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);
                return;
            }
        }
        // Join the user to the document
        manager.joinSession(userId, sharingCode);

        // Ensure document data is initialized or apply existing data
        documentData.putIfAbsent(documentId, new CopyOnWriteArrayList<>());
        List<CRDTOperation> existingData = documentData.get(documentId);

        // Send join response
        response.put("success", true);
        response.put("documentId", documentId);
        response.put("isEditor", isEditor);
        response.put("existingData", existingData);

        messagingTemplate.convertAndSend("/topic/document/" + documentId, response);
        notifyUserPresence(documentId, userId, username, true);
        System.out.println("User " + userId + " joined document " + documentId + " as "
                + (isEditor ? "editor" : "viewer"));
    }

    /**
     * Handles document operations (inserts/deletes)
     */
    @MessageMapping("/document/{documentId}/operation")
    public void handleOperation(@Payload Map<String, Object> payload, @DestinationVariable String documentId,
            @Payload CRDTOperation operation,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = (String) payload.get("userId");
        String userRole = manager.getUserRole(userId, documentId);
        if (userRole == null) {
            return; // User is not in the document
        }
        if (!manager.getUserDocuments(userId).contains(documentId)) {
            return; // User is not in the correct document
        }
        if (!userRole.equals("editor")) {
            return; // Only editors can perform operations
        }
        documentData.get(documentId).add(operation);
        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/operations", operation);

        System.out.println("Operation from user " + userId + " on document " + documentId
                + ": " + operation.getOperation()
                + (operation.getValue() != null ? " '" + operation.getValue() + "'" : ""));
    }

    /**
     * Handles user leaving the document
     */
    @MessageMapping("/leaveDocument")
    public void leaveDocument(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userId = (String) payload.get("userId");
        String documentId = (String) payload.get("documentId");
        String username = (String) payload.get("username");
        if (userId == null || documentId == null) {
            return; // User is not in a session
        }

        // Remove user from session
        manager.leaveSession(userId, documentId);
        headerAccessor.getSessionAttributes().remove("documentId");
        headerAccessor.getSessionAttributes().remove("username");
        headerAccessor.getSessionAttributes().remove("userId");

        // Notify other users
        notifyUserPresence(documentId, userId, username, false);

        System.out.println("User " + userId + " left document " + documentId);
    }

    private void notifyUserPresence(String documentId, String userId, String username, boolean isJoining) {
        Map<String, Object> presenceUpdate = new HashMap<>();
        presenceUpdate.put("userId", userId);
        presenceUpdate.put("username", username);
        presenceUpdate.put("joining", isJoining);

        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/users", presenceUpdate);
    }

}
