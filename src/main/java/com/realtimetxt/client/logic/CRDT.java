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
        this.root = new CRDTItem(null, "", UUID.randomUUID());
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
        if (root.getId() == itemId) {
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
