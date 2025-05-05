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
    private ClientSocket clientSocket;
    private String userId;
    private ArrayList<CRDTOperation> operations = new ArrayList<>();

    public CRDTController(ClientSocket clientSocket, String userId) {
        this.clientSocket = clientSocket;
        this.crdt = new CRDT();
        this.items.add(crdt.getRoot());
        this.userId = userId;

        // _prepareFirstOperation();
    }

    public CRDTController() {
        this.crdt = new CRDT();
        this.items.add(crdt.getRoot());

        // _prepareFirstOperation();
    }

    public void sendOperationsToServer() {
        for (CRDTOperation operation : operations) {
            clientSocket.sendOperation(operation);
        }
    }

    public void startNewDocument(List<CRDTOperation> operations) {
        this.operations.clear();
        this.undoStack.clear();
        this.redoStack.clear();
        this.items.clear();
        this.crdt = new CRDT();
        for (CRDTOperation operation : operations) {
            crdt.newOperation(operation);
        }
        _updateItemsList();
        currentText = renderText();
    }

    public void undo() {
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

    public void redo() {
        System.out.println("Redo: " + redoStack.size());
        if (!redoStack.isEmpty()) {
            isRedoing = true;
            CRDTOperation lastOperation = redoStack.remove(redoStack.size() - 1);
            if (lastOperation.getOperation() == OperationType.INSERT) {
                CRDTOperation newOperation = new CRDTOperation(
                        "0",
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

    public void textChanged(String newText, int index) {
        System.out.println("Text changed: " + newText + " at index: " + index);
        if (isUndoing || isRedoing) {
            isUndoing = false;
            isRedoing = false;
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

        System.out.println("Rendered text: " + renderText());
        System.out.println("************************");
    }

    public void onRemoteOperation(CRDTOperation operation) {
        System.out.println("Remote operation: " + operation);
        System.out.println(
                "Inserted: " + operation.getValue() +
                        " with itemId: " + operation.getItemId() +
                        " and parentId: " + operation.getParentId());
        crdt.newOperation(operation);
        _updateItemsList();
        System.out.println("Rendered text: " + renderText());
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
        clientSocket.sendOperation(operation);

        System.out.println(
                "Inserted: " + operation.getValue() +
                        " at index: " + index +
                        " with itemId: " + operation.getItemId() +
                        " and parentId: " + operation.getParentId());
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
        clientSocket.sendOperation(operation);
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