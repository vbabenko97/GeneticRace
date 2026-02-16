// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace;

import geneticrace.config.AppConfig;
import geneticrace.controller.NavigationHelper;
import geneticrace.db.DatabaseConnection;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.Logger;

/**
 * Main application entry point.
 */
public class Main extends Application {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    @Override
    public void start(Stage stage) throws Exception {
        // Initialize configuration
        LOGGER.info("App directory: " + AppConfig.getAppDirectory());
        LOGGER.info("Database path: " + AppConfig.getDatabasePath());

        // Test database connection
        if (!DatabaseConnection.testConnection()) {
            LOGGER.severe("Failed to connect to database");
        }

        // Load login view
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
        Parent root = loader.load();

        stage.setTitle("Авторизація в системі");
        stage.setScene(new Scene(root));
        NavigationHelper.loadIcon(stage);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        LOGGER.info("Application shutting down");
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
