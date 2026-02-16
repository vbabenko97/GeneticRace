// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.service;

import geneticrace.db.DatabaseConnection;
import geneticrace.model.FirstStageData;
import geneticrace.model.SecondStageData;
import geneticrace.repository.PatientDataPort;
import geneticrace.repository.PatientDataPort.SecondStageResult;
import geneticrace.repository.PatientRepository;
import geneticrace.session.SessionManager;
import javafx.concurrent.Task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for calculating and saving treatment strategies.
 * Wraps Python GA calls in JavaFX Tasks for background execution.
 */
public class TreatmentService {
    private static final Logger LOGGER = Logger.getLogger(TreatmentService.class.getName());

    private static final int TREATMENT_VALUES_COUNT = 9;

    private static final String INSERT_FIRST_STAGE =
        "INSERT INTO FirstStage(patientID, x201, x202, x203, x204, x205, x206, x207, x208, x209, lastcommit) " +
        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SECOND_STAGE =
        "INSERT INTO SecondStage(patientID, x401, x402, x403, x404, x405, x406, x407, x408, x409, lastcommit) " +
        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final PatientDataPort patientRepository;
    private final PythonServicePort pythonService;

    public TreatmentService() {
        this(new PatientRepository(), new PythonService());
    }

    public TreatmentService(PatientDataPort patientRepository, PythonServicePort pythonService) {
        this.patientRepository = patientRepository;
        this.pythonService = pythonService;
    }

    /**
     * Result containing treatment suggestions and complications.
     */
    public static class TreatmentResult {
        public List<List<Double>> treatments;
        public List<Integer> complications;
        public TreatmentError errorType;
        public String error;

        public boolean isSuccess() {
            return error == null && treatments != null && !treatments.isEmpty();
        }

        static TreatmentResult failure(TreatmentError type, String message) {
            TreatmentResult r = new TreatmentResult();
            r.errorType = type;
            r.error = message;
            return r;
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
        try {
            Optional<FirstStageData> optData = patientRepository.getFirstStageData(patientId);

            if (optData.isEmpty()) {
                return TreatmentResult.failure(TreatmentError.PATIENT_NOT_FOUND,
                    "Patient data not found");
            }

            FirstStageData data = optData.get();
            List<Double> xList;
            try {
                xList = List.of(
                    (double) data.x101(), data.x102(), data.x103(),
                    (double) data.x104(), (double) data.x105(),
                    data.x106(), data.x107(), data.x108(), data.x109(),
                    parseFirstStageValue(data.x110()),
                    parseFirstStageValue(data.x111()),
                    parseFirstStageValue(data.x112())
                );
            } catch (IllegalArgumentException e) {
                return TreatmentResult.failure(TreatmentError.INVALID_CLINICAL_DATA,
                    "Invalid clinical data: " + e.getMessage());
            }

            PythonService.GaResult gaResult = pythonService.runFirstStage(xList);

            if (!gaResult.isSuccess()) {
                return TreatmentResult.failure(TreatmentError.SCRIPT_FAILED, gaResult.error);
            }

            TreatmentResult result = new TreatmentResult();
            result.treatments = gaResult.treatments;
            result.complications = gaResult.complications;

            LOGGER.info("FirstStage calculation completed for patient " + patientId);
            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FirstStage calculation failed", e);
            return TreatmentResult.failure(TreatmentError.CALCULATION_FAILED,
                "Calculation failed: " + e.getMessage());
        }
    }

    /**
     * Calculates SecondStage (medication) treatment strategy.
     */
    public TreatmentResult calculateSecondStage(int patientId) {
        try {
            Optional<SecondStageResult> optResult = patientRepository.getSecondStageData(patientId);

            if (optResult.isEmpty()) {
                return TreatmentResult.failure(TreatmentError.PATIENT_NOT_FOUND,
                    "Patient not found");
            }

            SecondStageResult secondStageResult = optResult.get();
            if (secondStageResult instanceof SecondStageResult.PatientHasNoPostConditions noPC) {
                return TreatmentResult.failure(TreatmentError.NO_POST_CONDITION_DATA,
                    "Patient " + noPC.patient().surname() + " has no post-condition data");
            }

            SecondStageData data = ((SecondStageResult.Found) secondStageResult).data();
            List<Double> xList;
            try {
                xList = List.of(
                    parseSecondStageValue(data.pe()),
                    parseSecondStageValue(data.vab()),
                    parseSecondStageValue(data.pEarly()),
                    parseSecondStageValue(data.plicat()),
                    parseSecondStageValue(data.stroke()),
                    parseSecondStageValue(data.thrombosis()),
                    parseSecondStageValue(data.chyle()),
                    parseSecondStageValue(data.avb()),
                    parseSecondStageValue(data.snd())
                );
            } catch (IllegalArgumentException e) {
                return TreatmentResult.failure(TreatmentError.INVALID_CLINICAL_DATA,
                    "Invalid post-condition data: " + e.getMessage());
            }

            PythonService.GaResult gaResult = pythonService.runSecondStage(xList);

            if (!gaResult.isSuccess()) {
                return TreatmentResult.failure(TreatmentError.SCRIPT_FAILED, gaResult.error);
            }

            TreatmentResult result = new TreatmentResult();
            result.treatments = gaResult.treatments;
            result.complications = gaResult.complications;

            LOGGER.info("SecondStage calculation completed for patient " + patientId);
            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "SecondStage calculation failed", e);
            return TreatmentResult.failure(TreatmentError.CALCULATION_FAILED,
                "Calculation failed: " + e.getMessage());
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
