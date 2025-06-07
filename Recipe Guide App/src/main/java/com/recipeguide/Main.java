package com.recipeguide;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.recipeguide.db.DatabaseManager;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Veritabanı tablolarını oluştur
        DatabaseManager.initializeDatabase();
        
        Parent root = FXMLLoader.load(getClass().getResource("/com/recipeguide/main.fxml"));
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());
        
        primaryStage.setTitle("Recipe Guide");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        primaryStage.show();
    }

    @Override
    public void stop() {
        // Close database connection when application closes
        com.recipeguide.database.DatabaseConfig.closeConnection();
    }

    public static void main(String[] args) {
        launch(args);
    }
} 