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

    private final Map<String, String> codeOfDocument = new ConcurrentHashMap<>();
    private final Map<String, String> codeOfRole = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> documentsUsers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> usersDocuments = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> usersRoles = new ConcurrentHashMap<>();

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

    public boolean isValidCode(String code) {
        // Add logic to validate the code
        return code != null && !code.isEmpty(); // Example logic
    }

    public String getUserDocument(String userId) {
        Set<String> documents = usersDocuments.get(userId);
        return documents != null && !documents.isEmpty() ? documents.iterator().next() : null;
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

    public String getCodeRole(String code) {
        return codeOfRole.get(code);
    }

    public String getDocumentFromCode(String code) {
        return codeOfDocument.get(code);
    }
}
