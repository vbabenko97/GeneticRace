// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.controller;

import geneticrace.service.LoginService;
import geneticrace.session.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the password reset dialog.
 * Shown when a user's account requires a password change.
 */
public class PasswordResetController {
    private static final Logger LOGGER = Logger.getLogger(PasswordResetController.class.getName());
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final LoginService loginService = new LoginService();

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label errorLabel;

    @FXML
    public void handleReset() {
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            showError("Заповніть обидва поля");
            return;
        }

        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            showError("Пароль має містити щонайменше " + MIN_PASSWORD_LENGTH + " символів");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showError("Паролі не збігаються");
            return;
        }

        Integer userId = SessionManager.getInstance().getCurrentUserId();
        if (userId == null) {
            showError("Помилка сесії");
            return;
        }

        boolean updated = loginService.updatePassword(userId, newPassword);
        if (updated) {
            LOGGER.info("Password reset successful for user ID: " + userId);
            navigateToMainMenu();
        } else {
            showError("Не вдалося змінити пароль");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private void navigateToMainMenu() {
        try {
            Stage currentStage = (Stage) newPasswordField.getScene().getWindow();
            currentStage.close();

            String title = "Головне меню - " + SessionManager.getInstance().getCurrentUserRealName();
            NavigationHelper.openNewWindow("/fxml/MainMenuView.fxml", title, Modality.APPLICATION_MODAL);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load main menu after password reset", e);
            showError("Не вдалося завантажити головне меню");
        }
    }
}
