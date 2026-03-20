// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.service;

import geneticrace.model.FirstStageData;
import geneticrace.model.SecondStageData;
import geneticrace.repository.PatientDataPort;
import geneticrace.repository.PatientDataPort.SecondStageResult;
import geneticrace.repository.PatientRepository;
import javafx.concurrent.Task;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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
     * Result of a treatment calculation — either a successful set of
     * treatment strategies or a typed failure.
     */
    public sealed interface TreatmentResult {
        record Success(List<List<Double>> treatments, List<Integer> complications) implements TreatmentResult {}
        record Failure(TreatmentError errorType, String message) implements TreatmentResult {}
    }

    /**
     * Creates a background task for FirstStage treatment calculation.
     * Use with JavaFX to avoid UI freezing.
     *
     * Example:
     * <pre>
     * Task&lt;TreatmentResult&gt; task = service.createFirstStageTask(patientId);
     * task.setOnSucceeded(e -&gt; handleResult(task.getValue()));
     * task.setOnFailed(e -&gt; handleError(task.getException()));
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
                return new TreatmentResult.Failure(TreatmentError.PATIENT_NOT_FOUND,
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
                return new TreatmentResult.Failure(TreatmentError.INVALID_CLINICAL_DATA,
                    "Invalid clinical data: " + e.getMessage());
            }

            PythonServicePort.GaResult gaResult = pythonService.runFirstStage(xList);

            if (!gaResult.isSuccess()) {
                return new TreatmentResult.Failure(TreatmentError.SCRIPT_FAILED, gaResult.error);
            }

            LOGGER.info("FirstStage calculation completed for patient " + patientId);
            return new TreatmentResult.Success(gaResult.treatments, gaResult.complications);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FirstStage calculation failed", e);
            return new TreatmentResult.Failure(TreatmentError.CALCULATION_FAILED,
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
                return new TreatmentResult.Failure(TreatmentError.PATIENT_NOT_FOUND,
                    "Patient not found");
            }

            SecondStageResult secondStageResult = optResult.get();
            if (secondStageResult instanceof SecondStageResult.PatientHasNoPostConditions noPC) {
                return new TreatmentResult.Failure(TreatmentError.NO_POST_CONDITION_DATA,
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
                return new TreatmentResult.Failure(TreatmentError.INVALID_CLINICAL_DATA,
                    "Invalid post-condition data: " + e.getMessage());
            }

            PythonServicePort.GaResult gaResult = pythonService.runSecondStage(xList);

            if (!gaResult.isSuccess()) {
                return new TreatmentResult.Failure(TreatmentError.SCRIPT_FAILED, gaResult.error);
            }

            LOGGER.info("SecondStage calculation completed for patient " + patientId);
            return new TreatmentResult.Success(gaResult.treatments, gaResult.complications);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "SecondStage calculation failed", e);
            return new TreatmentResult.Failure(TreatmentError.CALCULATION_FAILED,
                "Calculation failed: " + e.getMessage());
        }
    }

    /**
     * Encoding of Ukrainian Yes/No to numeric values for GMDH models.
     * The reversal between stages matches the original GMDH training data and must not be "normalized."
     */
    enum StageEncoding {
        // FirstStage: Так=1.0, Ні=2.0
        FIRST_STAGE("FirstStage", Map.of("Так", 1.0, "Ні", 2.0)),
        // SecondStage: Ні=1.0, Так=2.0 (intentionally reversed)
        SECOND_STAGE("SecondStage", Map.of("Ні", 1.0, "Так", 2.0));

        private final String label;
        private final Map<String, Double> mapping;

        StageEncoding(String label, Map<String, Double> mapping) {
            this.label = label;
            this.mapping = mapping;
        }

        Double lookup(String trimmed) {
            return mapping.get(trimmed);
        }
    }

    double parseFirstStageValue(String value) {
        return parseStageValue(value, StageEncoding.FIRST_STAGE);
    }

    double parseSecondStageValue(String value) {
        return parseStageValue(value, StageEncoding.SECOND_STAGE);
    }

    private double parseStageValue(String value, StageEncoding encoding) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Blank or null clinical value in " + encoding.label + " data");
        }
        String trimmed = value.trim();
        Double mapped = encoding.lookup(trimmed);
        return mapped != null ? mapped : Double.parseDouble(trimmed);
    }

    /**
     * Saves FirstStage result to database.
     */
    public boolean saveFirstStageResult(int patientId, List<Double> treatment) throws SQLException {
        if (treatment.size() < TREATMENT_VALUES_COUNT) {
            throw new IllegalArgumentException("Treatment must have " + TREATMENT_VALUES_COUNT + " values");
        }
        return patientRepository.saveFirstStageResult(patientId, treatment);
    }

    /**
     * Saves SecondStage result to database.
     */
    public boolean saveSecondStageResult(int patientId, List<Double> treatment) throws SQLException {
        if (treatment.size() < TREATMENT_VALUES_COUNT) {
            throw new IllegalArgumentException("Treatment must have " + TREATMENT_VALUES_COUNT + " values");
        }
        return patientRepository.saveSecondStageResult(patientId, treatment);
    }
}
