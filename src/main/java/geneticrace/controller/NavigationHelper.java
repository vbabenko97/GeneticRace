// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared navigation helpers for FXML-based screen transitions.
 */
public final class NavigationHelper {
    private static final Logger LOGGER = Logger.getLogger(NavigationHelper.class.getName());
    private static final String ICON_PATH = "/images/logo.png";

    private NavigationHelper() {}

    /**
     * Creates and shows a new window with the given FXML view.
     *
     * @param fxmlPath resource path to the FXML file
     * @param title    window title
     * @param modality window modality, or null for no modality
     * @return the new Stage
     */
    public static Stage openNewWindow(String fxmlPath, String title, Modality modality)
            throws IOException {
        FXMLLoader loader = new FXMLLoader(NavigationHelper.class.getResource(fxmlPath));
        Parent root = loader.load();

        Stage stage = new Stage();
        if (modality != null) {
            stage.initModality(modality);
        }
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        loadIcon(stage);
        stage.show();

        return stage;
    }

    /**
     * Loads the application icon onto a stage, suppressing errors.
     */
    public static void loadIcon(Stage stage) {
        try (InputStream iconStream = NavigationHelper.class.getResourceAsStream(ICON_PATH)) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load application icon", e);
        }
    }
}
