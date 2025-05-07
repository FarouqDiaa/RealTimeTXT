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

    public void setId(UUID uuid) {
        this.uuid = uuid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public OperationType getOperation() {
        return operation;
    }

    public void setOperation(OperationType operation) {
        this.operation = operation;
    }
}
