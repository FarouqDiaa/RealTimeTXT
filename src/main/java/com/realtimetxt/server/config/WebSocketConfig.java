package com.realtimetxt.server.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{
@Override
public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic");  // Enable a simple memory-based message broker
    config.setApplicationDestinationPrefixes("/app");  // Prefix for messages bound for @MessageMapping methods
}

@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws");  // Register the "/ws" endpoint for WebSocket connections
    registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
}
}
