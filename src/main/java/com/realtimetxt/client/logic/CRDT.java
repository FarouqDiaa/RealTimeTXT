package com.realtimetxt.client.logic;

import java.util.UUID;

import com.realtimetxt.shared.enums.OperationType;
import com.realtimetxt.shared.exceptions.NotImplementedException;

public class CRDT {
        private CRDTItem root;

    public CRDT(CRDTItem root) {
    }

    public CRDTItem findCrItem(UUID itemId) {
        return _findCrItem(root, itemId);
    }

    public void newOperation(OperationType operation) {
        throw new NotImplementedException();
    }

    private CRDTItem _findCrItem(CRDTItem root, UUID itemId) {
        if (root.getId() == itemId) {
            return root;
        }

        for (CRDTItem item : root.getChildren()) {
            if (_findCrItem(item, itemId) != null) {
                return item;
            }
        }

        return null;
    }
}
