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
                "success", true
        ));

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
        List<Map<String, Object>> serializedOps = new ArrayList<>();
        List<CRDTOperation> docOps = documentData.getOrDefault(documentId, new ArrayList<>());

        for (CRDTOperation op : docOps) {
            Map<String, Object> opMap = new HashMap<>();
            opMap.put("userId", op.getUserId());
            opMap.put("operation", op.getOperation().toString());
            opMap.put("value", op.getValue());
            opMap.put("parentId", op.getParentId().toString());
            opMap.put("itemId", op.getItemId().toString());
            serializedOps.add(opMap);
        }

        // Notify all users about the updated user list
        sendUserListUpdate(documentId);

        // Send response to joining user with correctly serialized data
        Map<String, Object> response = new HashMap<>();
        response.put("documentId", documentId);
        response.put("isEditor", isEditor);
        response.put("success", true);
        response.put("existingData", serializedOps);
        response.put("currentUsers", manager.getDocumentUsers(documentId).stream()
                .map(u -> Map.of(
                "username", u.getUsername(),
                "isEditor", u.isEditor()
        ))
                .collect(Collectors.toList()));

        messagingTemplate.convertAndSend("/topic/joinResponse/" + userId, response);

        // Send full state to ensure consistency
        String content = buildContentFromOperations(docOps);
        CRDTOperation fullStateOp = new CRDTOperation();
        fullStateOp.setFullState(true);
        fullStateOp.setContent(content);

        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/document/" + documentId + "/operations",
                fullStateOp
        );

        System.out.println("User " + userId + " joined document " + documentId + " as " + (isEditor ? "editor" : "viewer"));
    }

    private boolean validateEditorRole(String userId, String documentId) {
        if (userId == null || documentId == null) {
            return false;
        }

        String role = manager.getUserRole(userId, documentId);
        return role != null && "editor".equals(role);
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
                    .anyMatch(op -> op.getItemId().equals(operation.getItemId()));

            if (isDuplicate) {
                System.err.println("Duplicate operation detected for itemId: " + operation.getItemId());
                return;
            }

            documentData.computeIfAbsent(documentId, k -> new CopyOnWriteArrayList<>()).add(operation);

            // Broadcast to all subscribers and to the user's personal queue
            messagingTemplate.convertAndSend("/topic/document/" + documentId + "/operations", operation);
            messagingTemplate.convertAndSendToUser(userId, "/queue/document/" + documentId + "/operations", operation);
        }
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
                    "position", position
            ));

            // Don't send a user list update on every cursor position to avoid feedback loops
            // Only send user list updates if a user actually joins or leaves
            // sendUserListUpdate(documentId);  <-- Comment out this line
            System.out.println("Broadcasting cursor position for " + username + " at position " + position);
        } catch (Exception e) {
            System.err.println("Error broadcasting cursor position: " + e.getMessage());
        }
    }

    @MessageMapping("/document/{documentId}/fullState")
    public void handleFullState(
            @DestinationVariable String documentId,
            @Payload Map<String, Object> payload) {

        String userId = (String) payload.get("userId");
        String content = (String) payload.get("content");

        if (validateEditorRole(userId, documentId) && content != null) {
            // Clear existing operations
            documentData.put(documentId, new CopyOnWriteArrayList<>());

            // Convert content to CRDTOperations
            List<CRDTOperation> operations = convertTextToOperations(userId, content);
            documentData.get(documentId).addAll(operations);

            // Broadcast to all clients
            operations.forEach(op
                    -> messagingTemplate.convertAndSend(
                            "/topic/document/" + documentId + "/operations",
                            op
                    )
            );
        }
    }

    @MessageMapping("/document/{documentId}/requestFullState")
    public void handleFullStateRequest(
            @DestinationVariable String documentId,
            @Payload Map<String, Object> payload) {

        String requestingUserId = (String) payload.get("userId");
        String username = (String) payload.get("username");

        System.out.println("Full state requested by user: " + username + " (" + requestingUserId + ") for document: " + documentId);

        // Get the current document state
        List<CRDTOperation> docOps = documentData.getOrDefault(documentId, new ArrayList<>());

        // Create a special operation for the full state
        CRDTOperation fullStateOp = new CRDTOperation();
        fullStateOp.setFullState(true);

        // Build content from operations
        String content = buildContentFromOperations(docOps);
        fullStateOp.setContent(content);

        // Debug output
        System.out.println("Sending full state with content length: "
                + (content != null ? content.length() : 0)
                + " and operations count: " + docOps.size());

        try {
            // Send directly to the requesting user via their personal queue
            messagingTemplate.convertAndSendToUser(
                    requestingUserId,
                    "/queue/document/" + documentId + "/operations",
                    fullStateOp
            );

            // Also send to topic to ensure all users are synchronized
            messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/operations",
                    fullStateOp
            );
        } catch (Exception e) {
            System.err.println("Error sending full state: " + e.getMessage());
            e.printStackTrace();
        }

        // Ensure user list is also sent
        sendUserListUpdate(documentId);

        // Refresh this user's presence
        manager.refreshUserPresence(documentId, requestingUserId);
    }

    private String buildContentFromOperations(List<CRDTOperation> operations) {
        // Create a temporary CRDT to process operations
        CRDT tempCrdt = new CRDT();

        // Apply all operations
        for (CRDTOperation op : operations) {
            tempCrdt.newOperation(op);
        }

        // Build a properly ordered traversal of non-deleted nodes
        StringBuilder sb = new StringBuilder();
        List<CRDTItem> orderedItems = new ArrayList<>();
        collectItemsInOrder(tempCrdt.getRoot(), orderedItems);

        // Skip the root item (which has no value) and append values
        for (int i = 1; i < orderedItems.size(); i++) {
            CRDTItem item = orderedItems.get(i);
            sb.append(item.getValue());
        }

        return sb.toString();
    }

    private void collectItemsInOrder(CRDTItem root, List<CRDTItem> items) {
        if (root == null) {
            return;
        }
        if (!root.isDeleted()) {
            items.add(root);
        }
        for (CRDTItem child : root.getChildren()) {
            collectItemsInOrder(child, items);
        }
    }

    private List<CRDTOperation> convertTextToOperations(String userId, String text) {
        List<CRDTOperation> operations = new ArrayList<>();
        CRDT tempCrdt = new CRDT();
        CRDTItem currentParent = tempCrdt.getRoot();

        for (char c : text.toCharArray()) {
            CRDTOperation op = new CRDTOperation(
                    userId,
                    OperationType.INSERT,
                    String.valueOf(c),
                    currentParent.getId(),
                    UUID.randomUUID()
            );
            operations.add(op);
            tempCrdt.newOperation(op);
            currentParent = tempCrdt.findCrItem(op.getItemId());
        }

        return operations;
    }

    private CRDTOperation extractOperationFromPayload(Map<String, Object> payload) {
        try {
            String operationTypeStr = (String) payload.get("operation");
            OperationType operationType = OperationType.valueOf(operationTypeStr.toUpperCase());
            return new CRDTOperation(
                    (String) payload.get("userId"),
                    operationType,
                    (String) payload.get("value"),
                    UUID.fromString((String) payload.get("parentId")),
                    UUID.fromString((String) payload.get("itemId"))
            );
        } catch (Exception e) {
            System.err.println("Error extracting operation: " + e.getMessage());
            return null;
        }
    }

    private void sendErrorResponse(String destination, String errorMessage) {
        messagingTemplate.convertAndSend(destination, Map.of("success", false, "errorMessage", errorMessage));
    }

    private void sendUserListUpdate(String documentId) {
        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/users", manager.getDocumentUsers(documentId).stream()
                .map(user -> Map.of(
                "username", user.getUsername(),
                "isEditor", user.isEditor()
        ))
                .collect(Collectors.toList()));
    }

}
