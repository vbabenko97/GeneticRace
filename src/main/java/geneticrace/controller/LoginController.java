// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.controller;

import geneticrace.service.LoginService;
import geneticrace.session.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the login view.
 */
public class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
    
    private final LoginService loginService = new LoginService();
    
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private Button loginBtn;
    
    @FXML
    private Label errorLabel;
    
    @FXML
    public void initialize() {
        // Clear any previous error
        if (errorLabel != null) {
            errorLabel.setText("");
        }
    }
    
    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showError("Введіть логін та пароль");
            return;
        }
        
        // Disable button during authentication
        loginBtn.setDisable(true);
        
        LoginService.AuthResult result = loginService.authenticate(username, password);
        
        switch (result) {
            case SUCCESS:
                LOGGER.info("Login successful for user: " + username);
                navigateToMainMenu();
                break;
                
            case INVALID_CREDENTIALS:
                showError("Невірний логін або пароль");
                break;
                
            case LOCKED_OUT:
                showError("Забагато спроб. Спробуйте через 5 хвилин");
                break;

            case NEEDS_PASSWORD_RESET:
                LOGGER.info("Password reset required for user: " + username);
                navigateToPasswordReset();
                break;

            case ERROR:
                showError("Помилка системи. Спробуйте пізніше");
                break;
        }
        
        loginBtn.setDisable(false);
    }
    
    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
        }
        LOGGER.warning("Login error: " + message);
    }
    
    private void navigateToPasswordReset() {
        try {
            Stage currentStage = (Stage) loginBtn.getScene().getWindow();
            currentStage.close();

            NavigationHelper.openNewWindow(
                "/fxml/PasswordResetView.fxml", "Зміна пароля", Modality.APPLICATION_MODAL);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open password reset", e);
            showError("Не вдалося відкрити зміну пароля");
        }
    }

    private void navigateToMainMenu() {
        try {
            Stage currentStage = (Stage) loginBtn.getScene().getWindow();
            currentStage.close();

            String title = "Головне меню - " + SessionManager.getInstance().getCurrentUserRealName();
            NavigationHelper.openNewWindow("/fxml/MainMenuView.fxml", title, Modality.APPLICATION_MODAL);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load main menu", e);
            showError("Не вдалося завантажити головне меню");
        }
    }
}
