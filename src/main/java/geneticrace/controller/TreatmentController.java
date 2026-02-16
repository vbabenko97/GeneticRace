// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.controller;

import geneticrace.service.TreatmentService;
import geneticrace.service.TreatmentService.TreatmentResult;
import geneticrace.session.SessionManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for treatment calculation views (FirstStage and SecondStage).
 * Shared FXML — stage type is set via {@link #setStage(StageType)} before display.
 */
public class TreatmentController {
    private static final Logger LOGGER = Logger.getLogger(TreatmentController.class.getName());

    public enum StageType { FIRST, SECOND }

    @FXML private Label titleLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private TextArea resultArea;
    @FXML private Button saveBtn;

    private final TreatmentService treatmentService = new TreatmentService();
    private StageType stageType;
    private TreatmentResult currentResult;

    public void setStage(StageType stageType) {
        this.stageType = stageType;

        int patientId = SessionManager.getInstance().getCurrentPatientId();

        if (stageType == StageType.FIRST) {
            titleLabel.setText("Перший етап лікування");
            runCalculation(treatmentService.createFirstStageTask(patientId));
        } else {
            titleLabel.setText("Другий етап лікування");
            runCalculation(treatmentService.createSecondStageTask(patientId));
        }
    }

    private void runCalculation(Task<TreatmentResult> task) {
        statusLabel.setText("Розрахунок...");
        progressIndicator.setVisible(true);
        resultArea.clear();

        task.setOnSucceeded(e -> {
            progressIndicator.setVisible(false);
            currentResult = task.getValue();

            if (currentResult.isSuccess()) {
                statusLabel.setText("Розрахунок завершено");
                resultArea.setText(formatResult(currentResult));
                saveBtn.setDisable(false);
            } else {
                statusLabel.setText("Помилка: " + currentResult.errorType);
                resultArea.setText(currentResult.error);
            }
        });

        task.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            statusLabel.setText("Помилка розрахунку");
            Throwable ex = task.getException();
            LOGGER.log(Level.SEVERE, "Treatment calculation failed", ex);
            resultArea.setText("Помилка: " + ex.getMessage());
        });

        new Thread(task, "treatment-calc").start();
    }

    private String formatResult(TreatmentResult result) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.treatments.size(); i++) {
            sb.append("Варіант ").append(i + 1).append(":\n");
            List<Double> treatment = result.treatments.get(i);
            for (int j = 0; j < treatment.size(); j++) {
                sb.append(String.format("  x%d = %.4f%n", j + 1, treatment.get(j)));
            }
            if (result.complications != null && i < result.complications.size()) {
                sb.append("  Ускладнення: ").append(result.complications.get(i)).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @FXML
    public void handleSave() {
        if (currentResult == null || !currentResult.isSuccess()) return;

        int patientId = SessionManager.getInstance().getCurrentPatientId();
        List<Double> treatment = currentResult.treatments.get(0);

        try {
            boolean saved;
            if (stageType == StageType.FIRST) {
                saved = treatmentService.saveFirstStageResult(patientId, treatment);
            } else {
                saved = treatmentService.saveSecondStageResult(patientId, treatment);
            }

            statusLabel.setText(saved ? "Збережено" : "Помилка збереження");
            saveBtn.setDisable(true);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save treatment result", e);
            statusLabel.setText("Помилка збереження: " + e.getMessage());
        }
    }

    @FXML
    public void handleClose() {
        Stage stage = (Stage) resultArea.getScene().getWindow();
        stage.close();
    }
}
