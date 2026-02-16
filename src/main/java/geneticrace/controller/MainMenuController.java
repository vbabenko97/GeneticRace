// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.controller;

import geneticrace.service.LoginService;
import geneticrace.session.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the main menu view.
 */
public class MainMenuController {
    private static final Logger LOGGER = Logger.getLogger(MainMenuController.class.getName());

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label userLabel;

    @FXML
    private Button selectPatientBtn;

    @FXML
    private Button firstStageBtn;

    @FXML
    private Button secondStageBtn;

    @FXML
    private Button logoutBtn;

    @FXML
    public void initialize() {
        SessionManager session = SessionManager.getInstance();

        if (session.isLoggedIn()) {
            userLabel.setText("Користувач: " + session.getCurrentUserRealName());

            if (session.isAdmin()) {
                welcomeLabel.setText("Панель адміністратора");
            }
        }

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasPatient = SessionManager.getInstance().hasPatientSelected();
        firstStageBtn.setDisable(!hasPatient);
        secondStageBtn.setDisable(!hasPatient);
    }

    @FXML
    public void handleSelectPatient() {
        LOGGER.info("Opening patient selection");

        try {
            Stage dialog = NavigationHelper.openNewWindow(
                "/fxml/PatientChooserView.fxml", "Вибір пацієнта", Modality.APPLICATION_MODAL);
            dialog.setOnHidden(e -> updateButtonStates());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open patient chooser", e);
        }
    }

    @FXML
    public void handleFirstStage() {
        if (!SessionManager.getInstance().hasPatientSelected()) {
            LOGGER.warning("No patient selected");
            return;
        }

        LOGGER.info("Opening FirstStage for patient: " +
                    SessionManager.getInstance().getCurrentPatientId());
        openTreatmentView(TreatmentController.StageType.FIRST, "Перший етап лікування");
    }

    @FXML
    public void handleSecondStage() {
        if (!SessionManager.getInstance().hasPatientSelected()) {
            LOGGER.warning("No patient selected");
            return;
        }

        LOGGER.info("Opening SecondStage for patient: " +
                    SessionManager.getInstance().getCurrentPatientId());
        openTreatmentView(TreatmentController.StageType.SECOND, "Другий етап лікування");
    }

    private void openTreatmentView(TreatmentController.StageType stageType, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TreatmentView.fxml"));
            Parent root = loader.load();

            TreatmentController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(title);
            stage.setScene(new javafx.scene.Scene(root));
            NavigationHelper.loadIcon(stage);
            stage.show();

            controller.setStage(stageType);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open treatment view", e);
        }
    }

    @FXML
    public void handleLogout() {
        LOGGER.info("User logging out");

        new LoginService().logout();

        try {
            Stage currentStage = (Stage) logoutBtn.getScene().getWindow();
            currentStage.close();

            NavigationHelper.openNewWindow("/fxml/LoginView.fxml", "Авторизація в системі", null);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to return to login", e);
        }
    }
}
