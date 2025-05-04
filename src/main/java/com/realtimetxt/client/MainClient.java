package com.realtimetxt.client;

import com.realtimetxt.client.network.ClientSocket;
import com.realtimetxt.client.ui.EditorUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainClient extends Application {
    private EditorUI ui;
    private ExecutorService executor;
    private String username;

    @Override
    public void start(Stage primaryStage) {
        // Initialize UI
        ui = new EditorUI(primaryStage);
        primaryStage.show();

        // Initialize client network components
        executor = Executors.newSingleThreadExecutor();

        // Generate a random ID for this user
        username = generateUsername();
        System.out.println("User Name: " + username);
    }

    private String generateUsername() {
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