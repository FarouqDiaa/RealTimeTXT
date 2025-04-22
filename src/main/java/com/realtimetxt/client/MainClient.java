package com.realtimetxt.client;

import com.realtimetxt.client.ui.EditorUI;

public class MainClient {
    public static void main(String[] args) {
        EditorUI ui = new EditorUI();
        ui.initialize();
        // TODO: start client networking and CRDT logic
    }
}
