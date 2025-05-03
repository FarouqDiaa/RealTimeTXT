package com.realtimetxt.client.network;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.realtimetxt.shared.CRDTOperation;

public class ClientSocket {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ExecutorService executor;
    private boolean connected = false;
    private String userId;
    
    // Callbacks for different message types
    private Consumer<CRDTOperation> operationConsumer;
    private Consumer<String> connectionStatusHandler;
    private Consumer<CursorUpdate> cursorUpdateHandler;
    private Consumer<String[]> activeUsersUpdateHandler;
    private Runnable reconnectionHandler;
    
    // Reconnection parameters
    private String lastHost;
    private int lastPort;
    private boolean autoReconnect = true;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_MS = 2000; // 2 seconds

    public ClientSocket(String userId) {
        this.userId = userId;
        this.executor = Executors.newFixedThreadPool(2); // One thread for receiving, one for reconnection attempts
    }

    /**
     * Connect to the specified server
     * @param host The server hostname or IP address
     * @param port The server port
     * @throws IOException If connection fails
     */
    public void connect(String host, int port) throws IOException {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            // Store connection info for potential reconnection
            this.lastHost = host;
            this.lastPort = port;
            
            // Send initial identification message
            out.writeObject("USER:" + userId);
            out.flush();
            
            connected = true;
            reconnectAttempts = 0;
            
            if (connectionStatusHandler != null) {
                connectionStatusHandler.accept("Connected to " + host + ":" + port);
            }
            
            // Start background thread to listen for server messages
            startReceiving();
        } catch (IOException e) {
            throw new IOException("Failed to connect: " + e.getMessage(), e);
        }
    }
    
    /**
     * Disconnects from the server
     */
    public void disconnect() {
        autoReconnect = false;
        connected = false;
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            System.err.println("Error during disconnect: " + e.getMessage());
        }
        
        if (connectionStatusHandler != null) {
            connectionStatusHandler.accept("Disconnected");
        }
    }
    
    /**
     * Checks if client is currently connected to the server
     * @return True if connected, false otherwise
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
    
    /**
     * Sends a CRDT operation to the server
     * @param operation The operation to send
     * @return True if operation was sent, false otherwise
     */
    public boolean sendOperation(CRDTOperation operation) {
        if (!isConnected()) {
            return false;
        }
        
        try {
            out.writeObject(operation);
            out.flush();
            return true;
        } catch (IOException e) {
            handleDisconnection("Failed to send operation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Sends a cursor position update to the server
     * @param position Current cursor position
     * @return True if update was sent, false otherwise
     */
    public boolean sendCursorPosition(int position) {
        if (!isConnected()) {
            return false;
        }
        
        try {
            out.writeObject("CURSOR:" + position);
            out.flush();
            return true;
        } catch (IOException e) {
            handleDisconnection("Failed to send cursor position: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Starts background thread that listens for incoming messages from the server
     */
    private void startReceiving() {
        executor.submit(() -> {
            try {
                while (connected && !Thread.currentThread().isInterrupted()) {
                    try {
                        Object received = in.readObject();
                        
                        if (received instanceof CRDTOperation) {
                            // Handle incoming CRDT operation
                            if (operationConsumer != null) {
                                operationConsumer.accept((CRDTOperation) received);
                            }
                        } else if (received instanceof String) {
                            String message = (String) received;
                            processStringMessage(message);
                        }
                    } catch (SocketException se) {
                        if (connected) {
                            handleDisconnection("Connection lost: Socket closed");
                        }
                        break;
                    } catch (EOFException eof) {
                        handleDisconnection("Connection lost: Server closed connection");
                        break;
                    } catch (ClassNotFoundException | IOException e) {
                        if (connected) {
                            handleDisconnection("Error receiving data: " + e.getMessage());
                        }
                        break;
                    }
                }
            } finally {
                if (autoReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    attemptReconnection();
                }
            }
        });
    }
    
    /**
     * Process string messages from the server
     * @param message The received message
     */
    private void processStringMessage(String message) {
        if (message.startsWith("CURSOR:")) {
            // Format: "CURSOR:userId:position"
            String[] parts = message.split(":");
            if (parts.length >= 3) {
                String remoteUserId = parts[1];
                try {
                    int position = Integer.parseInt(parts[2]);
                    if (cursorUpdateHandler != null && !remoteUserId.equals(userId)) {
                        cursorUpdateHandler.accept(new CursorUpdate(remoteUserId, position));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid cursor position: " + parts[2]);
                }
            }
        } else if (message.startsWith("USERS:")) {
            // Format: "USERS:user1,user2,user3"
            String[] parts = message.split(":", 2);
            if (parts.length == 2) {
                String[] users = parts[1].split(",");
                if (activeUsersUpdateHandler != null) {
                    activeUsersUpdateHandler.accept(users);
                }
            }
        } else if (message.equals("RECONNECT_STATE")) {
            // Server is sending reconnection state
            if (reconnectionHandler != null) {
                reconnectionHandler.run();
            }
        }
    }
    
    /**
     * Handles disconnection from the server
     * @param message Disconnection reason
     */
    private void handleDisconnection(String message) {
        if (!connected) {
            return; // Already disconnected
        }
        
        connected = false;
        
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore close errors
        }
        
        if (connectionStatusHandler != null) {
            connectionStatusHandler.accept("Disconnected: " + message);
        }
        
        if (autoReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            attemptReconnection();
        }
    }
    
    /**
     * Attempts to reconnect to the last known server
     */
    private void attemptReconnection() {
        reconnectAttempts++;
        
        if (connectionStatusHandler != null) {
            connectionStatusHandler.accept("Reconnection attempt " + reconnectAttempts + " of " + MAX_RECONNECT_ATTEMPTS);
        }
        
        executor.submit(() -> {
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
                try {
                    connect(lastHost, lastPort);
                } catch (IOException e) {
                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        attemptReconnection();
                    } else if (connectionStatusHandler != null) {
                        connectionStatusHandler.accept("Failed to reconnect after " + MAX_RECONNECT_ATTEMPTS + " attempts");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    /**
     * Set callback for received CRDT operations
     */
    public void setOperationConsumer(Consumer<CRDTOperation> consumer) {
        this.operationConsumer = consumer;
    }
    
    /**
     * Set callback for connection status messages
     */
    public void setConnectionStatusHandler(Consumer<String> handler) {
        this.connectionStatusHandler = handler;
    }
    
    /**
     * Set callback for cursor position updates
     */
    public void setCursorUpdateHandler(Consumer<CursorUpdate> handler) {
        this.cursorUpdateHandler = handler;
    }
    
    /**
     * Set callback for active users list updates
     */
    public void setActiveUsersUpdateHandler(Consumer<String[]> handler) {
        this.activeUsersUpdateHandler = handler;
    }
    
    /**
     * Set callback for reconnection handling
     */
    public void setReconnectionHandler(Runnable handler) {
        this.reconnectionHandler = handler;
    }
    
    /**
     * Enable/disable automatic reconnection attempts
     */
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }
    
    /**
     * Inner class to represent cursor position updates
     */
    public static class CursorUpdate {
        private final String userId;
        private final int position;
        
        public CursorUpdate(String userId, int position) {
            this.userId = userId;
            this.position = position;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public int getPosition() {
            return position;
        }
    }
}