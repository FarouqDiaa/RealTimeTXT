package com.realtimetxt.server;

public class RequestHandler {

    private final SessionManager sessionManager;

    public RequestHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public String handleRequest(String userId, String action, String payload) {
        String role = sessionManager.getUserRole(userId);

        switch (action) {
            case "getDocumentState":
                return handleGetDocumentState(userId);

            case "insert":
            case "delete":
                if (!"editor".equals(role)) {
                    return "ERROR: Permission denied (viewers cannot modify)";
                }
                return handleEdit(userId, action, payload);

            default:
                return "ERROR: Unknown action";
        }
    }

    private String handleGetDocumentState(String userId) {
        String docId = sessionManager.getUserDocument(userId);
        return "Document content for: " + docId;
    }

    private String handleEdit(String userId, String action, String payload) {
        String docId = sessionManager.getUserDocument(userId);
        return "Applied " + action + " to " + docId;
    }
}
