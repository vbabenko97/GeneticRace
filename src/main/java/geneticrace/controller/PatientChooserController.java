// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.controller;

import geneticrace.model.Patient;
import geneticrace.repository.PatientRepository;
import geneticrace.session.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the patient selection dialog.
 */
public class PatientChooserController {
    private static final Logger LOGGER = Logger.getLogger(PatientChooserController.class.getName());

    @FXML
    private ListView<Patient> patientList;

    @FXML
    private Label statusLabel;

    private final PatientRepository repository = new PatientRepository();

    @FXML
    public void initialize() {
        try {
            List<Patient> patients = repository.getAccessiblePatients();
            patientList.setItems(FXCollections.observableArrayList(patients));
            statusLabel.setText("Знайдено пацієнтів: " + patients.size());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load patients", e);
            statusLabel.setText("Помилка завантаження пацієнтів");
        }
    }

    @FXML
    public void handleSelect() {
        Patient selected = patientList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Виберіть пацієнта зі списку");
            return;
        }

        SessionManager.getInstance().selectPatient(selected.id());
        LOGGER.info("Selected patient: " + selected.id() + " - " + selected.fullName());
        closeWindow();
    }

    @FXML
    public void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) patientList.getScene().getWindow();
        stage.close();
    }
}
