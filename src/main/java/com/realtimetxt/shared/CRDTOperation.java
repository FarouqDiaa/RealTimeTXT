package com.realtimetxt.shared;

import java.util.UUID;

import com.realtimetxt.shared.enums.OperationType;

public class CRDTOperation {
    private UUID uuid = UUID.randomUUID();
    private long timestamp = System.currentTimeMillis();
    private int userId;
    private OperationType operation;
    private String value = null;
    private UUID parent = null;

    public CRDTOperation(int userId, OperationType operation, String value, UUID parent) {
        this.userId = userId;
        this.operation = operation;
        this.value = value;
        this.parent = parent;
    }

    public UUID getId() {
        return uuid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getUserId() {
        return userId;
    }

    public UUID getParent() {
        return parent;
    }

    public String getValue() {
        return value;
    }

    public OperationType getOperation() {
        return operation;
    }
}
