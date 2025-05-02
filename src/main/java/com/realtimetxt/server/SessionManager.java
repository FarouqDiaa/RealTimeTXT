package com.realtimetxt.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SessionManager {

    private final Map<String, String> codeOfDocument = new HashMap<>();

    private final Map<String, String> codeOfRole = new HashMap<>();

    private final Map<String, Set<String>> documentsUsers = new HashMap<>();

    private final Map<String, String> usersDocument = new HashMap<>();

    private final Map<String, String> usersRoles = new HashMap<>();

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

        documentsUsers.putIfAbsent(docId, new HashSet<>());
        documentsUsers.get(docId).add(userId);

        usersDocument.put(userId, docId);
        usersRoles.put(userId, role);
        return true;
    }

    public void leaveSession(String userId) {
        String docId = usersDocument.get(userId);
        if (docId == null) {
            return;
        }

        Set<String> users = documentsUsers.get(docId);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                documentsUsers.remove(docId);
                System.out.println("Session for document " + docId + " removed as it is empty");
            }
        }

        usersDocument.remove(userId);
        usersRoles.remove(userId);
    }

    public String getUserRole(String userId) {
        return usersRoles.get(userId);
    }

    public String getUserDocument(String userId) {
        return usersDocument.get(userId);
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

    public boolean isValidCode(String code) {
        return codeOfDocument.containsKey(code);
    }

    public String getCodeRole(String code) {
        return codeOfRole.get(code);
    }

    public String getDocumentFromCode(String code) {
        return codeOfDocument.get(code);
    }
}
