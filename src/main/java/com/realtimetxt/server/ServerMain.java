package com.realtimetxt.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerMain {
    public static void main(String[] args) {
        // Start the Spring Boot application
        SpringApplication.run(ServerMain.class, args);
        System.out.println("WebSocket server is running...");
    }
}