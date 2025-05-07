package com.realtimetxt.server;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.springframework.stereotype.Component;

@Component
public class SessionManager {

    // Maps session codes to document IDs and roles
    private final Map<String, String> codeOfDocument = new ConcurrentHashMap<>();
    private final Map<String, String> codeOfRole = new ConcurrentHashMap<>();

    // Tracks user presence in documents
    private final Map<String, Set<UserPresence>> documentUsers = new ConcurrentHashMap<>();

    // Tracks which documents a user is part of and their roles
    private final Map<String, Set<String>> usersDocuments = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> usersRoles = new ConcurrentHashMap<>();

    public static class UserPresence {

        private final String userId;
        private final String username;
        private final boolean isEditor;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UserPresence)) {
                return false;
            }
            UserPresence that = (UserPresence) o;
            return userId.equals(that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId);
        }
    }

    public void addUserToDocument(String documentId, String userId, String username, boolean isEditor) {
        Set<UserPresence> users = documentUsers.computeIfAbsent(
                documentId,
                k -> new ConcurrentSkipListSet<>(Comparator.comparing(UserPresence::getUserId))
        );

        // First remove any existing entry for the userId
        users.removeIf(user -> user.getUserId().equals(userId));

        // Ensure unique display names by appending a number if needed
        String uniqueUsername = ensureUniqueUsername(users, username, userId);

        // Add new user presence
        users.add(new UserPresence(userId, uniqueUsername, isEditor, System.currentTimeMillis()));

        usersDocuments.computeIfAbsent(userId, k -> new ConcurrentSkipListSet<>()).add(documentId);
        usersRoles.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(documentId, isEditor ? "editor" : "viewer");
    }

    private String ensureUniqueUsername(Set<UserPresence> users, String baseUsername, String userId) {
        String uniqueName = baseUsername;
        int counter = 2;

        boolean isDuplicate = true;
        while (isDuplicate) {
            final String currentName = uniqueName;
            isDuplicate = users.stream().anyMatch(u -> u.getUsername().equals(currentName) && !u.getUserId().equals(userId));
            if (isDuplicate) {
                uniqueName = baseUsername + " (" + counter + ")";
                counter++;
            }
        }

        return uniqueName;
    }

    public boolean validateRoleAssignment(String code, String documentId, boolean requestedEditor) {
        String role = codeOfRole.get(code);
        if (role == null) {
            return false;
        }

        // If requesting editor role, code must be editor code
        if (requestedEditor && !"editor".equals(role)) {
            return false;
        }

        // Document ID must match
        return documentId.equals(codeOfDocument.get(code));
    }

    public void refreshUserPresence(String documentId, String userId) {
        Set<UserPresence> users = documentUsers.get(documentId);
        if (users != null) {
            for (UserPresence user : users) {
                if (user.getUserId().equals(userId)) {
                    user.setLastActive(System.currentTimeMillis());
                    break;
                }
            }
        }
    }

    public void removeUserFromDocument(String documentId, String userId) {
        Set<UserPresence> users = documentUsers.get(documentId);
        if (users != null) {
            // Find the username before removing
            String username = users.stream()
                    .filter(user -> user.getUserId().equals(userId))
                    .map(UserPresence::getUsername)
                    .findFirst()
                    .orElse("Unknown");

            // Remove the user
            users.removeIf(user -> user.getUserId().equals(userId));

            if (users.isEmpty()) {
                documentUsers.remove(documentId);
                System.out.println("All users left document " + documentId + " (access codes still valid)");
            } else {
                System.out.println("User " + username + " (" + userId + ") removed from document " + documentId);
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

    /**
     * Handle user disconnect by removing them from all documents they were part
     * of
     *
     * @param userId The ID of the disconnected user
     */
    public void handleUserDisconnect(String userId) {
        Set<String> documents = getUserDocuments(userId);
        if (documents != null && !documents.isEmpty()) {
            // Create a copy to avoid ConcurrentModificationException
            Set<String> docsCopy = new HashSet<>(documents);
            for (String docId : docsCopy) {
                removeUserFromDocument(docId, userId);
                System.out.println("User " + userId + " removed from document " + docId + " due to disconnect");
            }
        }
    }

    public Set<UserPresence> getDocumentUsers(String documentId) {
        return documentUsers.getOrDefault(documentId, Collections.emptySet());
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

    public boolean joinSession(String userId, String code, String username) {
        if (!codeOfDocument.containsKey(code)) {
            return false;
        }

        String documentId = codeOfDocument.get(code);
        String role = codeOfRole.get(code);
        boolean isEditor = "editor".equals(role);

        addUserToDocument(documentId, userId, username, isEditor);
        return true;
    }

    public String getUserRole(String userId, String documentId) {
        Map<String, String> roles = usersRoles.get(userId);
        return roles != null ? roles.get(documentId) : null;
    }

    public Set<String> getUserDocuments(String userId) {
        return usersDocuments.getOrDefault(userId, Collections.emptySet());
    }

    public boolean isUserInDocument(String userId, String documentId, String role) {
        Map<String, String> roles = usersRoles.get(userId);
        return roles != null && role.equals(roles.get(documentId));
    }

    public boolean isValidDocument(String code) {
        return codeOfDocument.containsKey(code);
    }

    public boolean isEditorOrViewerCode(String code) {
        String role = codeOfRole.get(code);
        return codeOfDocument.containsKey(code) && ("editor".equals(role) || "viewer".equals(role));
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

    private String generateUniqueCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8);
        } while (codeOfDocument.containsKey(code));
        return code;
    }
}
