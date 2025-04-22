package com.realtimetxt.client.network;

import java.io.IOException;
import java.net.Socket;

public class ClientSocket {
    private Socket socket;

    public void connect(String host, int port) throws IOException {
        // TODO: implement connection logic
        socket = new Socket(host, port);
    }
}
