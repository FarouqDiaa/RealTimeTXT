package com.realtimetxt.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.realtimetxt.server.SessionManager;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    @Autowired
    private SessionManager sessionManager;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable a memory-based message broker for sending messages to clients
        // Set user destination prefix first
        registry.enableSimpleBroker("/topic", "/queue", "/user");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the /ws endpoint for WebSocket connections
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS(); // Enable SockJS fallback options
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        // Log disconnection using proper logger
        logger.info("Client disconnected: {}", sessionId);

        // Check if we can find user ID from the session
        Object principal = event.getUser();

        if (principal != null) {
            String userId = principal.toString(); // Convert principal to String directly
            logger.info("User {} disconnected from session {}", userId, sessionId);
            // Handle user disconnect by removing from all documents
            sessionManager.handleUserDisconnect(userId);
        } else {
            // If principal is null, we can still handle disconnect by sessionId
            logger.info("Anonymous session disconnected: {}", sessionId);
            sessionManager.handleUserDisconnect(sessionId);
        }
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(500 * 1024);
        registration.setSendBufferSizeLimit(1024 * 1024);
        registration.setSendTimeLimit(20000);
    }
}
