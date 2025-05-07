package com.realtimetxt.client.logic;

import java.util.UUID;

import com.realtimetxt.shared.CRDTOperation;
import com.realtimetxt.shared.enums.OperationType;

public class CRDT {
    private CRDTItem root;

    public CRDT(CRDTItem root) {
        this.root = root;
    }

    public CRDT() {
        this.root = new CRDTItem(null, "", UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    public CRDTItem getRoot() {
        return root;
    }

    public CRDTItem findCrItem(UUID itemId) {
        if (root == null) {
            return null;
        }
        return _findCrItem(root, itemId);
    }

    public void newOperation(CRDTOperation operation) {
        // System.out.println("Current root: " + root.getId());
        // System.out.println("New operation: " + operation.getOperation() +
        // " Operation value: " + operation.getValue() +
        // " Item ID: " + operation.getItemId() +
        // " Parent ID: " + operation.getParentId());
        if (operation.getOperation() == OperationType.INSERT) {
            CRDTItem parent = findCrItem(operation.getParentId());
            CRDTItem newItem = new CRDTItem(parent, operation.getValue(), operation.getItemId());
            parent.addChild(newItem);
        } else if (operation.getOperation() == OperationType.DELETE) {
            CRDTItem itemToDelete = findCrItem(operation.getItemId());
            itemToDelete.setDeleted(true);
        }
    }

    private CRDTItem _findCrItem(CRDTItem root, UUID itemId) {
        // System.out.println("Searching for item ID: " + itemId + " in root ID: " +
        // root.getId());
        if (root.getId().equals(itemId)) {
            // System.out.println("Found item ID: " + itemId + " in root ID: " +
            // root.getId());
            return root;
        }

        for (CRDTItem item : root.getChildren()) {
            CRDTItem foundItem = _findCrItem(item, itemId);
            if (foundItem != null) {
                return foundItem;
            }
        }

        return null;
    }
}
