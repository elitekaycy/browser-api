package com.browserapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time recorder frame streaming and event broadcasting.
 *
 * Configures STOMP over WebSocket to enable:
 * - Browser frame streaming from backend to frontend
 * - Real-time action event broadcasting
 * - Bidirectional communication for recorder sessions
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure message broker for STOMP messaging.
     *
     * - /topic: Simple in-memory broker for broadcasting (frames, actions)
     * - /app: Application destination prefix for client-to-server messages
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for topic-based broadcasting
        config.enableSimpleBroker("/topic");

        // Set application destination prefix for @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Register STOMP endpoints for WebSocket connections.
     *
     * Endpoint: /ws-recorder
     * - Clients connect to ws://localhost:8080/ws-recorder
     * - SockJS fallback enabled for browsers without native WebSocket support
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-recorder")
                .setAllowedOriginPatterns("*")  // Allow all origins for development
                .withSockJS();                   // Enable SockJS fallback
    }
}
