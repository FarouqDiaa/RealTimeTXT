package com.realtimetxt.client.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

import com.realtimetxt.shared.CRDTOperation;
import com.realtimetxt.shared.enums.OperationType;

public class CRDTController {

    private CRDT crdt;
    private String currentText = "";
    private ArrayList<CRDTItem> items = new ArrayList<>();
    private ArrayList<CRDTOperation> undoStack = new ArrayList<>();
    private ArrayList<CRDTOperation> redoStack = new ArrayList<>();

    public CRDTController() {
        this.crdt = new CRDT();
        this.items.add(crdt.getRoot());
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            CRDTOperation lastOperation = undoStack.remove(undoStack.size() - 1);
            if (lastOperation.getOperation() == OperationType.INSERT) {
                CRDTOperation newOperation = new CRDTOperation(
                        "0",
                        OperationType.DELETE,
                        null,
                        null,
                        lastOperation.getItemId());

                crdt.newOperation(newOperation);
                int index = items.indexOf(crdt.findCrItem(lastOperation.getItemId()));
                items.remove(index);
            } else if (lastOperation.getOperation() == OperationType.DELETE) {
                CRDTOperation newOperation = new CRDTOperation(
                        "0",
                        OperationType.INSERT,
                        lastOperation.getValue(),
                        lastOperation.getParentId(),
                        UUID.randomUUID());

                crdt.newOperation(newOperation);
                int index = items.indexOf(crdt.findCrItem(lastOperation.getParentId()));
                items.add(index + 1, crdt.findCrItem(newOperation.getItemId()));
            }
            redoStack.add(lastOperation);
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            CRDTOperation lastOperation = redoStack.remove(redoStack.size() - 1);
            if (lastOperation.getOperation() == OperationType.DELETE) {
                CRDTOperation newOperation = new CRDTOperation(
                        "0",
                        OperationType.DELETE,
                        null,
                        null,
                        lastOperation.getItemId());

                crdt.newOperation(newOperation);
                int index = items.indexOf(crdt.findCrItem(lastOperation.getItemId()));
                items.remove(index);
            } else if (lastOperation.getOperation() == OperationType.DELETE) {
                CRDTOperation newOperation = new CRDTOperation(
                        "0",
                        OperationType.INSERT,
                        lastOperation.getValue(),
                        lastOperation.getParentId(),
                        UUID.randomUUID());

                crdt.newOperation(newOperation);
                int index = items.indexOf(crdt.findCrItem(lastOperation.getParentId()));
                items.add(index + 1, crdt.findCrItem(newOperation.getItemId()));
            }
            undoStack.add(lastOperation);
        }
    }

    public void textChanged(String newText, int index) {
        System.out.println("Text changed: " + newText + " at index: " + index);

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
        System.out.println("Current text: " + renderText());
    }

    public void onRemoteOperation(CRDTOperation operation) {
        System.out.println("Remote operation: " + operation);
        if (operation.getOperation() == OperationType.INSERT) {
            CRDTItem item = crdt.findCrItem(operation.getItemId());
            if (item != null) {
                items.add(item);
            }
        } else if (operation.getOperation() == OperationType.DELETE) {
            CRDTItem item = crdt.findCrItem(operation.getItemId());
            if (item != null) {
                items.remove(item);
            }
        }
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
                "0",
                OperationType.INSERT,
                String.valueOf(newText.charAt(index)),
                items.get(index).getId(),
                UUID.randomUUID());

        crdt.newOperation(operation);
        items.add(index + 1, crdt.findCrItem(operation.getItemId()));
        undoStack.add(operation);
    }

    private void _deleteText(String newText, int index) {
        CRDTOperation operation = new CRDTOperation(
                "0",
                OperationType.DELETE,
                items.get(index).getValue(),
                items.get(index).getParent().getId(),
                items.get(index).getId());

        crdt.newOperation(operation);
        items.remove(index);
        undoStack.add(operation);
    }

}
