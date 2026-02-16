// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.service;

import geneticrace.db.DatabaseConnection;
import geneticrace.repository.PatientRepository;
import geneticrace.session.SessionManager;
import javafx.concurrent.Task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for calculating and saving treatment strategies.
 * Wraps Python GA calls in JavaFX Tasks for background execution.
 */
public class TreatmentService {
    private static final Logger LOGGER = Logger.getLogger(TreatmentService.class.getName());

    // Patient data layout: indices 0-4 = demographics (surname, firstname, middlename, sex, dob)
    private static final int DATA_OFFSET = 5;
    private static final int FIRST_STAGE_DATA_SIZE = 17;    // 5 demographics + 12 clinical values
    private static final int FIRST_STAGE_CLINICAL_END = 17;  // exclusive
    private static final int SECOND_STAGE_DATA_SIZE = 14;    // 5 demographics + 9 post-condition values
    private static final int SECOND_STAGE_CLINICAL_END = 14; // exclusive
    private static final int TREATMENT_VALUES_COUNT = 9;

    private static final String INSERT_FIRST_STAGE =
        "INSERT INTO FirstStage(patientID, x201, x202, x203, x204, x205, x206, x207, x208, x209, lastcommit) " +
        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SECOND_STAGE =
        "INSERT INTO SecondStage(patientID, x401, x402, x403, x404, x405, x406, x407, x408, x409, lastcommit) " +
        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final PatientRepository patientRepository;
    private final PythonService pythonService;

    public TreatmentService() {
        this(new PatientRepository(), new PythonService());
    }

    public TreatmentService(PatientRepository patientRepository, PythonService pythonService) {
        this.patientRepository = patientRepository;
        this.pythonService = pythonService;
    }

    /**
     * Result containing treatment suggestions and complications.
     */
    public static class TreatmentResult {
        public List<List<Double>> treatments;
        public List<Integer> complications;
        public String error;

        public boolean isSuccess() {
            return error == null && treatments != null && !treatments.isEmpty();
        }
    }

    /**
     * Creates a background task for FirstStage treatment calculation.
     * Use with JavaFX to avoid UI freezing.
     *
     * Example:
     * <pre>
     * Task<TreatmentResult> task = service.createFirstStageTask(patientId);
     * task.setOnSucceeded(e -> handleResult(task.getValue()));
     * task.setOnFailed(e -> handleError(task.getException()));
     * new Thread(task).start();
     * </pre>
     */
    public Task<TreatmentResult> createFirstStageTask(int patientId) {
        return new Task<>() {
            @Override
            protected TreatmentResult call() throws Exception {
                return calculateFirstStage(patientId);
            }
        };
    }

    /**
     * Creates a background task for SecondStage treatment calculation.
     */
    public Task<TreatmentResult> createSecondStageTask(int patientId) {
        return new Task<>() {
            @Override
            protected TreatmentResult call() throws Exception {
                return calculateSecondStage(patientId);
            }
        };
    }

    /**
     * Calculates FirstStage (operational) treatment strategy.
     * Runs on background thread - do not call from UI thread directly.
     */
    public TreatmentResult calculateFirstStage(int patientId) {
        TreatmentResult result = new TreatmentResult();

        try {
            List<String> patientData = patientRepository.getPatientDetailsForFirstStage(patientId);

            if (patientData.size() < FIRST_STAGE_DATA_SIZE) {
                result.error = "Insufficient patient data for FirstStage calculation";
                return result;
            }

            // Extract clinical values (indices DATA_OFFSET..FIRST_STAGE_CLINICAL_END)
            List<Double> xList = new ArrayList<>();
            for (int i = DATA_OFFSET; i < FIRST_STAGE_CLINICAL_END; i++) {
                xList.add(parseFirstStageValue(patientData.get(i)));
            }

            PythonService.GaResult gaResult = pythonService.runFirstStage(xList);

            if (!gaResult.isSuccess()) {
                result.error = gaResult.error;
                return result;
            }

            result.treatments = gaResult.treatments;
            result.complications = gaResult.complications;

            LOGGER.info("FirstStage calculation completed for patient " + patientId);
            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FirstStage calculation failed", e);
            result.error = "Calculation failed: " + e.getMessage();
            return result;
        }
    }

    /**
     * Calculates SecondStage (medication) treatment strategy.
     */
    public TreatmentResult calculateSecondStage(int patientId) {
        TreatmentResult result = new TreatmentResult();

        try {
            List<String> patientData = patientRepository.getPatientDetailsForSecondStage(patientId);

            if (patientData.size() < SECOND_STAGE_DATA_SIZE) {
                result.error = "Insufficient patient data for SecondStage calculation";
                return result;
            }

            // Extract post-condition values (indices DATA_OFFSET..SECOND_STAGE_CLINICAL_END)
            List<Double> xList = new ArrayList<>();
            for (int i = DATA_OFFSET; i < SECOND_STAGE_CLINICAL_END; i++) {
                xList.add(parseSecondStageValue(patientData.get(i)));
            }

            PythonService.GaResult gaResult = pythonService.runSecondStage(xList);

            if (!gaResult.isSuccess()) {
                result.error = gaResult.error;
                return result;
            }

            result.treatments = gaResult.treatments;
            result.complications = gaResult.complications;

            LOGGER.info("SecondStage calculation completed for patient " + patientId);
            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "SecondStage calculation failed", e);
            result.error = "Calculation failed: " + e.getMessage();
            return result;
        }
    }

    /**
     * Converts Ukrainian Yes/No to numeric for FirstStage GMDH models.
     * Encoding: Так=1.0, Ні=2.0 (matches training data encoding).
     */
    double parseFirstStageValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Blank or null clinical value in FirstStage data");
        }
        String trimmed = value.trim();
        if ("Так".equals(trimmed)) return 1.0;
        if ("Ні".equals(trimmed)) return 2.0;
        return Double.parseDouble(trimmed);
    }

    /**
     * Converts Ukrainian Yes/No to numeric for SecondStage GMDH models.
     * Encoding: Ні=1.0, Так=2.0 (NOTE: reversed from FirstStage, matches training data).
     */
    double parseSecondStageValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Blank or null clinical value in SecondStage data");
        }
        String trimmed = value.trim();
        if ("Ні".equals(trimmed)) return 1.0;
        if ("Так".equals(trimmed)) return 2.0;
        return Double.parseDouble(trimmed);
    }

    /**
     * Saves FirstStage result to database.
     */
    public boolean saveFirstStageResult(int patientId, List<Double> treatment) throws SQLException {
        if (treatment.size() < TREATMENT_VALUES_COUNT) {
            throw new IllegalArgumentException("Treatment must have " + TREATMENT_VALUES_COUNT + " values");
        }

        String timestamp = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
            .format(Calendar.getInstance().getTime());

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_FIRST_STAGE)) {

            pstmt.setInt(1, patientId);
            for (int i = 0; i < TREATMENT_VALUES_COUNT; i++) {
                pstmt.setDouble(i + 2, treatment.get(i));
            }
            pstmt.setString(11, timestamp);

            int rows = pstmt.executeUpdate();
            LOGGER.info("Saved FirstStage result for patient " + patientId);
            return rows > 0;
        }
    }

    /**
     * Saves SecondStage result to database.
     */
    public boolean saveSecondStageResult(int patientId, List<Double> treatment) throws SQLException {
        if (treatment.size() < TREATMENT_VALUES_COUNT) {
            throw new IllegalArgumentException("Treatment must have " + TREATMENT_VALUES_COUNT + " values");
        }

        String timestamp = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
            .format(Calendar.getInstance().getTime());

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_SECOND_STAGE)) {

            pstmt.setInt(1, patientId);
            for (int i = 0; i < TREATMENT_VALUES_COUNT; i++) {
                pstmt.setDouble(i + 2, treatment.get(i));
            }
            pstmt.setString(11, timestamp);

            int rows = pstmt.executeUpdate();
            LOGGER.info("Saved SecondStage result for patient " + patientId);
            return rows > 0;
        }
    }
}
