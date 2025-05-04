package com.realtimetxt.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for collaborative text editor
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable a simple memory-based message broker for sending messages to clients
        // Prefix with /topic for broadcasts and /queue for user-specific messages
        registry.enableSimpleBroker("/topic", "/queue");
        
        // Prefix for application destination paths (controller methods)
        registry.setApplicationDestinationPrefixes("/app");
        
        // User destination prefix for user-specific messages
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the /ws endpoint for WebSocket connections
        registry
            .addEndpoint("/ws")
            .setAllowedOrigins("*") // In production, restrict to your domain
            .withSockJS(); // Enable SockJS fallback options
    }
}