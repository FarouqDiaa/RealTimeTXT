package client.network;

import java.net.Socket;
import java.io.*;

public class ClientSocket {
    private Socket socket;

    public void connect(String host, int port) throws IOException {
        // TODO: implement connection logic
        socket = new Socket(host, port);
    }
}
