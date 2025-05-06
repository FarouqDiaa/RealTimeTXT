package com.realtimetxt.client.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

import com.realtimetxt.client.network.ClientSocket;
import com.realtimetxt.shared.CRDTOperation;
import com.realtimetxt.shared.enums.OperationType;

public class CRDTController {

    private CRDT crdt;
    private String currentText = "";
    private ArrayList<CRDTItem> items = new ArrayList<>();
    private ArrayList<CRDTOperation> undoStack = new ArrayList<>();
    private ArrayList<CRDTOperation> redoStack = new ArrayList<>();
    private boolean isUndoing = false;
    private boolean isRedoing = false;
    private boolean isRemoteOperation = false;
    private boolean isImporting = false;
    private ClientSocket clientSocket;
    private String userId;
    private ArrayList<CRDTOperation> operations = new ArrayList<>();
    private ArrayList<CRDTOperation> pendingOperations = new ArrayList<>();
    private ArrayList<CRDTOperation> buffOperations = new ArrayList<>();

    public CRDTController(ClientSocket clientSocket, String userId) {
        this.clientSocket = clientSocket;
        this.crdt = new CRDT();
        this.items.add(crdt.getRoot());
        this.userId = userId;
    }

    public CRDTController() {
        this.crdt = new CRDT();
        this.items.add(crdt.getRoot());
        this.pendingOperations = new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public void openNewDocument() {
        this.operations.clear();
        this.undoStack.clear();
        this.redoStack.clear();
        this.items.clear();
        this.crdt = new CRDT();
        this.items.add(crdt.getRoot());
        _updateItemsList();
    }

    synchronized public void undo() {
        if (!undoStack.isEmpty()) {
            isUndoing = true;
            CRDTOperation lastOperation = undoStack.remove(undoStack.size() - 1);
            if (lastOperation.getOperation() == OperationType.INSERT) {
                CRDTOperation newOperation = new CRDTOperation(
                        userId,
                        OperationType.DELETE,
                        lastOperation.getValue(),
                        lastOperation.getParentId(),
                        lastOperation.getItemId());

                crdt.newOperation(newOperation);

                _updateItemsList();
                currentText = renderText();
                redoStack.add(newOperation);
                operations.add(newOperation);
                clientSocket.sendOperation(newOperation);

            } else if (lastOperation.getOperation() == OperationType.DELETE) {
                CRDTOperation newOperation = new CRDTOperation(
                        userId,
                        OperationType.INSERT,
                        lastOperation.getValue(),
                        lastOperation.getParentId(),
                        UUID.randomUUID());

                crdt.newOperation(newOperation);

                _updateItemsList();
                currentText = renderText();
                redoStack.add(newOperation);
                operations.add(newOperation);
                clientSocket.sendOperation(newOperation);
            }
        }
    }

    synchronized public void redo() {
        System.out.println("Redo: " + redoStack.size());
        if (!redoStack.isEmpty()) {
            isRedoing = true;
            CRDTOperation lastOperation = redoStack.remove(redoStack.size() - 1);
            if (lastOperation.getOperation() == OperationType.INSERT) {
                CRDTOperation newOperation = new CRDTOperation(
                        userId,
                        OperationType.DELETE,
                        lastOperation.getValue(),
                        lastOperation.getParentId(),
                        lastOperation.getItemId());

                crdt.newOperation(newOperation);

                _updateItemsList();
                currentText = renderText();
                undoStack.add(newOperation);
                operations.add(newOperation);
                clientSocket.sendOperation(newOperation);

            } else if (lastOperation.getOperation() == OperationType.DELETE) {
                CRDTOperation newOperation = new CRDTOperation(
                        userId,
                        OperationType.INSERT,
                        lastOperation.getValue(),
                        lastOperation.getParentId(),
                        UUID.randomUUID());

                crdt.newOperation(newOperation);

                _updateItemsList();
                currentText = renderText();
                undoStack.add(newOperation);
                operations.add(newOperation);
                clientSocket.sendOperation(newOperation);
            }
        }
    }

    synchronized public void setImportedText(String newText) {
        textChanged("", currentText.length());
        textChanged(newText, 0);
        isImporting = true;
    }

    synchronized public void textChanged(String newText, int index) {
        System.out.println("Text changed: " + newText + " at index: " + index);
        if (isUndoing || isRedoing || isRemoteOperation || isImporting) {
            isRemoteOperation = false;
            isUndoing = false;
            isRedoing = false;
            isImporting = false;
            return;
        }

        if (newText.length() > currentText.length()) {
            for (int i = 0; i < (newText.length() - currentText.length()); i++) {
                _insertText(newText, index + i);
            }
        } else if (newText.length() < currentText.length()) {
            for (int i = 0; i < (currentText.length() - newText.length()); i++) {
                _deleteText(newText, index - i);
            }
        }

        currentText = newText;
        redoStack.clear();
        sendOperations();

        System.out.println("Rendered text: " + renderText());
        System.out.println("************************");
    }

    synchronized public void onRemoteOperation(CRDTOperation newOperation) {
        for (CRDTOperation operation : operations) {
            if (operation.getId().equals(newOperation.getId())) {
                System.out.println("Duplicate operation detected: " + newOperation.getId());
                return;
            }
        }

        isRemoteOperation = true;

        try {
            crdt.newOperation(newOperation);
            operations.add(newOperation);
        } catch (NullPointerException e) {
            System.out.println("Error applying remote operation: " + e.getMessage());
            pendingOperations.add(newOperation);
        }

        while (true) {
            boolean updated = false;
            for (CRDTOperation operation : new ArrayList<>(pendingOperations)) {
                try {
                    crdt.newOperation(operation);
                } catch (NullPointerException e) {
                    System.out.println("Error applying remote operation: " + e.getMessage());
                    continue;
                }
                pendingOperations.remove(operation);
                operations.add(operation);
                updated = true;
            }

            if (!updated) {
                break;
            }
        }

        _updateItemsList();
        currentText =

                renderText();

    }

    public String renderText() {
        StringBuilder sb = new StringBuilder();
        Stack<CRDTItem> stack = new Stack<>();
        stack.push(crdt.getRoot());

        while (!stack.isEmpty()) {
            CRDTItem item = stack.pop();
            if (!item.isDeleted()) {
                sb.append(item.getValue());
            }
            for (CRDTItem child : item.getChildren()) {
                stack.push(child);
            }
        }

        return sb.toString();
    }

    private void _insertText(String newText, int index) {
        CRDTOperation operation = new CRDTOperation(
                userId,
                OperationType.INSERT,
                String.valueOf(newText.charAt(index)),
                items.get(index).getId(),
                UUID.randomUUID());

        crdt.newOperation(operation);
        items.add(index + 1, crdt.findCrItem(operation.getItemId()));
        undoStack.add(operation);
        operations.add(operation);
        buffOperations.add(operation);
    }

    private void _deleteText(String newText, int index) {
        CRDTOperation operation = new CRDTOperation(
                userId,
                OperationType.DELETE,
                items.get(index).getValue(),
                items.get(index).getParent().getId(),
                items.get(index).getId());

        crdt.newOperation(operation);
        items.remove(index);
        undoStack.add(operation);
        operations.add(operation);
        buffOperations.add(operation);
    }

    private void sendOperations() {
        while (!buffOperations.isEmpty()) {
            ArrayList<CRDTOperation> operationsToSend = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                if (buffOperations.isEmpty()) {
                    break;
                }
                CRDTOperation operation = buffOperations.remove(0);
                operationsToSend.add(operation);
            }
            clientSocket.sendOperations(operationsToSend);
        }
    }

    private void _updateItemsList() {
        Stack<CRDTItem> stack = new Stack<>();
        stack.push(crdt.getRoot());
        items.clear();
        while (!stack.isEmpty()) {
            CRDTItem item = stack.pop();
            if (!item.isDeleted()) {
                items.add(item);
            }
            for (CRDTItem child : item.getChildren()) {
                stack.push(child);
            }
        }
    }
}