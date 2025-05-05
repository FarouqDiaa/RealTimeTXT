package com.realtimetxt.shared;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.realtimetxt.shared.enums.OperationType;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CRDTOperation {

    private UUID uuid = UUID.randomUUID();
    private long timestamp = System.currentTimeMillis();
    private String userId;
    private OperationType operation;
    private String value = null;
    private UUID parentId = null;
    private UUID itemId = null;

    public CRDTOperation() {
    }

    public CRDTOperation(String userId, OperationType operation, String value, UUID parentId) {
        this.userId = userId;
        this.operation = operation;
        this.value = value;
        this.parentId = parentId;
        this.itemId = UUID.randomUUID();
    }

    public CRDTOperation(String userId, OperationType operation, String value, UUID parentId, UUID itemId) {
        this.userId = userId;
        this.operation = operation;
        this.value = value;
        this.parentId = parentId;
        this.itemId = itemId;
    }

    public UUID getId() {
        return uuid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getValue() {
        return value;
    }

    public OperationType getOperation() {
        return operation;
    }
}
