package com.realtimetxt.client;

import com.realtimetxt.client.ui.EditorUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainClient extends Application {
    private EditorUI ui;
    private ExecutorService executor;
    private String userId;

    @Override
    public void start(Stage primaryStage) {
        // Initialize UI
        ui = new EditorUI(primaryStage);
        primaryStage.show();

        // Initialize client network components
        executor = Executors.newSingleThreadExecutor();

        // Generate a random ID for this user
        userId = generateUserId();

        // You would typically connect to the server here
        // connectToServer("localhost", 12345);

        // For demo purposes, add a sample remote cursor
        ui.addRemoteCursor("Anonymous Crab", 20, Color.DARKRED);

    }

    private String generateUserId() {
        String[] adjectives = { "Anonymous", "Happy", "Sleepy", "Curious", "Excited" };
        String[] animals = { "Frog", "Bear", "Cat", "Dog", "Rabbit", "Lion", "Tiger" };

        Random random = new Random();
        String adjective = adjectives[random.nextInt(adjectives.length)];
        String animal = animals[random.nextInt(animals.length)];

        return adjective + " " + animal;
    }

    public static void main(String[] args) {
        launch(args);
    }
}