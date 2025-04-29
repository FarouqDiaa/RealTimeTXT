package com.realtimetxt.client.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CRDTItem {
    private UUID uuid = UUID.randomUUID();
    private long timestamp = System.currentTimeMillis();
    private CRDTItem parent;
    private String value;
    private Boolean isDeleted = false;
    private List<CRDTItem> children;

    public CRDTItem(CRDTItem parent, String value) {
        this.parent = parent;
        this.value = value;
        this.children = new ArrayList<>();
    }

    public CRDTItem(CRDTItem parent, String value, UUID uuid) {
        this.parent = parent;
        this.value = value;
        this.uuid = uuid;
        this.children = new ArrayList<>();
    }

    public UUID getId() {
        return uuid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public CRDTItem getParent() {
        return parent;
    }

    public String getValue() {
        return value;
    }

    public Boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public List<CRDTItem> getChildren() {
        return children;
    }

    public void addChild(CRDTItem child) {
        for (CRDTItem item : children) {
            if (child.getTimestamp() >= item.getTimestamp()) {
                children.add(children.indexOf(item), child);
                return;
            }
        }
        children.add(child);
    }

    @Override
    public String toString() {
        return "CRDTItem{" +
                "id=" + uuid +
                ", timestamp=" + timestamp +
                ", parent=" + (parent != null ? parent.getId() : "null") +
                '}';
    }
}
