package com.realtimetxt.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.springframework.stereotype.Component;

@Component
public class SessionManager {

    /**
     * Maps a unique code to a document ID. This is used to associate a session
     * code with the corresponding document.
     */
    private Map<String, String> codeOfDocument = new ConcurrentHashMap<>();

    /**
     * Maps a unique code to a role (e.g., "editor" or "viewer"). This is used
     * to determine the role associated with a session code.
     */
    private Map<String, String> codeOfRole = new ConcurrentHashMap<>();

    /**
     * Maps a document ID to a set of user IDs. This is used to track which
     * users are currently in a session for a specific document.
     */
    private Map<String, Set<String>> documentsUsers = new ConcurrentHashMap<>();

    /**
     * Maps a user ID to a set of document IDs. This is used to track which
     * documents a user is currently participating in.
     */
    private Map<String, Set<String>> usersDocuments = new ConcurrentHashMap<>();

    /**
     * Maps a user ID to a map of document IDs and their corresponding roles.
     * This is used to track the role of a user for each document they are
     * participating in.
     */
    private Map<String, Map<String, String>> usersRoles = new ConcurrentHashMap<>();
    // Add to SessionManager.java
    private final Map<String, Map<String, UserPresence>> documentUsers = new ConcurrentHashMap<>();

    public static class UserPresence {
        private String userId;
        private String username;
        private boolean isEditor;
        private long lastActive;

        public UserPresence(String userId, String username, boolean isEditor, long lastActive) {
            this.userId = userId;
            this.username = username;
            this.isEditor = isEditor;
            this.lastActive = lastActive;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public boolean isEditor() {
            return isEditor;
        }
        
        public long getLastActive() {
            return lastActive;
        }
        
        public void setLastActive(long lastActive) {
            this.lastActive = lastActive;
        }
    }

    // Add user to document
    public void addUserToDocument(String documentId, String userId, String username, boolean isEditor) {
        documentUsers.computeIfAbsent(documentId, k -> new ConcurrentHashMap<>())
            .put(userId, new UserPresence(userId, username, isEditor, System.currentTimeMillis()));
    }

    // Remove user from document
    public void removeUserFromDocument(String documentId, String userId) {
        if (documentUsers.containsKey(documentId)) {
            documentUsers.get(documentId).remove(userId);
        }
    }

    // Get all users in a document
    public Map<String, UserPresence> getDocumentUsers(String documentId) {
        return documentUsers.getOrDefault(documentId, new ConcurrentHashMap<>());
    }

    // Update user's last active timestamp
    public void updateUserActivity(String documentId, String userId) {
        if (documentUsers.containsKey(documentId) && documentUsers.get(documentId).containsKey(userId)) {
            documentUsers.get(documentId).get(userId).setLastActive(System.currentTimeMillis());
        }
    }

    public Map<String, String> createSession(String documentId) {
        String editorCode = generateUniqueCode();
        String viewerCode = generateUniqueCode();

        codeOfDocument.put(editorCode, documentId);
        codeOfRole.put(editorCode, "editor");

        codeOfDocument.put(viewerCode, documentId);
        codeOfRole.put(viewerCode, "viewer");

        Map<String, String> codes = new HashMap<>();
        codes.put("editor", editorCode);
        codes.put("viewer", viewerCode);
        return codes;
    }

    public boolean joinSession(String userId, String code) {
        if (!codeOfDocument.containsKey(code)) {
            return false;
        }

        String docId = codeOfDocument.get(code);
        String role = codeOfRole.get(code);

        documentsUsers.computeIfAbsent(docId, k -> new ConcurrentSkipListSet<>()).add(userId);
        usersDocuments.computeIfAbsent(userId, k -> new ConcurrentSkipListSet<>()).add(docId);

        usersRoles.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(docId, role);

        return true;
    }

    public void leaveSession(String userId, String documentId) {
        Set<String> users = documentsUsers.get(documentId);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                documentsUsers.remove(documentId);
                System.out.println("Session for document " + documentId + " removed as it is empty");
            }
        }

        Set<String> docs = usersDocuments.get(userId);
        if (docs != null) {
            docs.remove(documentId);
            if (docs.isEmpty()) {
                usersDocuments.remove(userId);
            }
        }

        Map<String, String> roles = usersRoles.get(userId);
        if (roles != null) {
            roles.remove(documentId);
            if (roles.isEmpty()) {
                usersRoles.remove(userId);
            }
        }
    }

    public String getUserRole(String userId, String documentId) {
        Map<String, String> roles = usersRoles.get(userId);
        return roles != null ? roles.get(documentId) : null;
    }

    public Set<String> getUserDocuments(String userId) {
        return usersDocuments.getOrDefault(userId, Collections.emptySet());
    }

    public Set<String> getUsersInDocument(String documentId) {
        return documentsUsers.getOrDefault(documentId, Collections.emptySet());
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8);
        } while (codeOfDocument.containsKey(code));
        return code;
    }

    public boolean isValidDocument(String code) {
        return codeOfDocument.containsKey(code);
    }

    public boolean isEditorOrViewerCode(String code) {
        return codeOfDocument.containsKey(code) && (codeOfRole.get(code).equals("editor") || codeOfRole.get(code).equals("viewer"));
    }

    public String getCodeRole(String code) {
        return codeOfRole.get(code);
    }

    public String getDocumentFromCode(String code) {
        return codeOfDocument.get(code);
    }

    public boolean isEditorCode(String code, String documentId) {
        return "editor".equals(codeOfRole.get(code)) && documentId.equals(codeOfDocument.get(code));
    }

    public boolean isUserInDocument(String userId, String documentId, String role) {
        Map<String, String> roles = usersRoles.get(userId);
        return roles != null && role.equals(roles.get(documentId));
    }
}
