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
import com.realtimetxt.shared.enums.OperationType;

@Controller
public class ServerSocketHandler {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SessionManager manager;

    // Store document data for retrieval
    private final Map<String, List<CRDTOperation>> documentData = new ConcurrentHashMap<>();

    @MessageMapping("/createDocument")
    public void createDocument(@Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String username = (String) payload.get("username");

        if (userId == null || username == null) {
            System.err.println("Error: User ID and username are required");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("errorMessage", "User ID and username are required");
            messagingTemplate.convertAndSend("/topic/createResponse/" + userId, errorResponse);
            return;
        }

        String documentId = UUID.randomUUID().toString();
        Map<String, String> codes = manager.createSession(documentId);
        String editorCode = codes.get("editor");
        String viewerCode = codes.get("viewer");

        manager.joinSession(userId, editorCode);

        // Initialize document data
        documentData.put(documentId, new CopyOnWriteArrayList<>());

        Map<String, Object> response = new HashMap<>();
        response.put("documentId", documentId);
        response.put("editorCode", editorCode);
        response.put("viewerCode", viewerCode);
        response.put("success", true);

        notifyUserPresence(documentId, userId, username, true);

        messagingTemplate.convertAndSend("/topic/createResponse/" + userId, response);

        System.out.println("Created document: " + documentId + " for user: " + userId);
    }

    @MessageMapping("/joinDocument")
    public void joinDocument(@Payload Map<String, Object> payload) {
        String username = (String) payload.get("username");
        String userId = (String) payload.get("userId");
        String sharingCode = (String) payload.get("sharingCode");

        Map<String, Object> response = new HashMap<>();

        // Validate required fields
        if (username == null || userId == null || sharingCode == null) {
            String errorMessage = "Username, user ID, and sharing code are required";
            System.err.println("Error: " + errorMessage);
            response.put("success", false);
            response.put("errorMessage", errorMessage);
            messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);
            return;
        }

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
        String roleType = isEditor ? "editor" : "viewer";

        // Check if user is already in the document with the same role
        if (manager.isUserInDocument(userId, documentId, roleType)) {
            response.put("success", false);
            response.put("errorMessage", "Already joined as " + roleType);
            messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);
            return;
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

        messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);
        messagingTemplate.convertAndSend("/topic/document/" + documentId, response);
        notifyUserPresence(documentId, userId, username, true);

        System.out.println("User " + userId + " joined document " + documentId + " as " + roleType);
    }

    /**
     * Handles document operations (inserts/deletes)
     */
    @MessageMapping("/document/{documentId}/operation")
    public void handleOperation(@DestinationVariable String documentId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        System.out.println("Received operation from user " + userId + " on document " + documentId);

        // Validate user and permissions
        if (userId == null) {
            System.err.println("Error: User ID is required for operations");
            return;
        }

        String userRole = manager.getUserRole(userId, documentId);
        if (userRole == null) {
            System.err.println("Error: User " + userId + " is not in document " + documentId);
            return; // User is not in the document
        }

        if (!manager.getUserDocuments(userId).contains(documentId)) {
            System.err.println("Error: User " + userId + " is not in the correct document");
            return; // User is not in the correct document
        }

        if (!userRole.equals("editor")) {
            System.err.println("Error: User " + userId + " is not an editor and cannot perform operations");
            return; // Only editors can perform operations
        }

        // Extract operation details from payload
        try {
            // Assuming the payload contains the operation details or a serialized
            // CRDTOperation
            CRDTOperation operation = extractOperationFromPayload(payload);

            if (operation == null) {
                System.err.println("Error: Invalid operation data");
                return;
            }

            // Safely get or create the document's operation list
            List<CRDTOperation> operations = documentData.computeIfAbsent(documentId,
                    k -> new CopyOnWriteArrayList<>());

            // Add the operation
            operations.add(operation);

            // Broadcast the operation to all clients
            messagingTemplate.convertAndSend("/topic/document/" + documentId + "/operations", operation);

            System.out.println("Operation from user " + userId + " on document " + documentId
                    + ": " + operation.getOperation()
                    + (operation.getValue() != null ? " '" + operation.getValue() + "'" : ""));
        } catch (Exception e) {
            System.err.println("Error processing operation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extract a CRDTOperation from the payload map
     */
    private CRDTOperation extractOperationFromPayload(Map<String, Object> payload) {
        try {
            // Extract parameters based on the actual CRDTOperation class structure
            String userId = (String) payload.get("userId");
            String operationTypeStr = (String) payload.get("operation");
            String value = (String) payload.get("value");
            String parentIdStr = (String) payload.get("parentId");
            String itemIdStr = (String) payload.get("itemId");

            // Validate required fields
            if (userId == null || operationTypeStr == null) {
                System.err.println("Error: userId and operation are required for CRDTOperation");
                return null;
            }

            // Convert operation string to enum
            OperationType operationType;
            try {
                operationType = OperationType.valueOf(operationTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Error: Invalid operation type: " + operationTypeStr);
                return null;
            }

            // Convert string IDs to UUID objects
            UUID parentId = null;
            if (parentIdStr != null) {
                try {
                    parentId = UUID.fromString(parentIdStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("Error: Invalid parent ID format: " + parentIdStr);
                    return null;
                }
            }

            // If itemId is provided, use it, otherwise let the constructor generate a new
            // one
            if (itemIdStr != null) {
                try {
                    UUID itemId = UUID.fromString(itemIdStr);
                    return new CRDTOperation(userId, operationType, value, parentId, itemId);
                } catch (IllegalArgumentException e) {
                    System.err.println("Error: Invalid item ID format: " + itemIdStr);
                    return null;
                }
            } else {
                return new CRDTOperation(userId, operationType, value, parentId);
            }
        } catch (Exception e) {
            System.err.println("Failed to extract operation from payload: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Handles user leaving the document
     */
    @MessageMapping("/leaveDocument")
    public void leaveDocument(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String userId = (String) payload.get("userId");
        String documentId = (String) payload.get("documentId");
        String username = (String) payload.get("username");

        if (userId == null || documentId == null || username == null) {
            System.err.println("Error: User ID, document ID, and username are required to leave a document");
            return;
        }

        // Remove user from session
        manager.leaveSession(userId, documentId);

        // Clean up session attributes
        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().remove("documentId");
            headerAccessor.getSessionAttributes().remove("username");
            headerAccessor.getSessionAttributes().remove("userId");
        }

        // Notify other users
        notifyUserPresence(documentId, userId, username, false);

        System.out.println("User " + userId + " left document " + documentId);
    }

    private void notifyUserPresence(String documentId, String userId, String username, boolean isJoining) {
        if (documentId == null || userId == null || username == null) {
            System.err.println("Error: Document ID, user ID, and username are required for presence notification");
            return;
        }

        Map<String, Object> presenceUpdate = new HashMap<>();
        presenceUpdate.put("userId", userId);
        presenceUpdate.put("username", username);
        presenceUpdate.put("joining", isJoining);

        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/users", presenceUpdate);
    }
}
