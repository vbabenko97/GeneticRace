package geneticrace.service;

import geneticrace.model.FirstStageData;
import geneticrace.model.Patient;
import geneticrace.model.SecondStageData;
import geneticrace.repository.PatientDataPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: exercises the full treatment pipeline
 * (record → service → mock Python → result) without Mockito,
 * using simple lambda-based fakes.
 */
class TreatmentServiceIntegrationTest {

    private static final Patient PATIENT =
        new Patient(42, "Тестов", "Тест", "Тестович", "Чоловіча", "1985-03-15");

    private static final FirstStageData FIRST_STAGE_DATA = new FirstStageData(
        PATIENT,
        120, 3.5, 4.2, 2, 1, 1.8, 2.1, 3.0, 4.5, "Так", "Ні", "Так"
    );

    private static final SecondStageData SECOND_STAGE_DATA = new SecondStageData(
        PATIENT,
        "Ні", "Так", "Ні", "Ні", "Так", "Ні", "Так", "Ні", "Так"
    );

    private static final PythonServicePort.GaResult SUCCESS_RESULT = makeSuccessResult();

    private static PythonServicePort.GaResult makeSuccessResult() {
        PythonServicePort.GaResult r = new PythonServicePort.GaResult();
        r.treatments = List.of(
            List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0),
            List.of(9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0)
        );
        r.complications = List.of(1, 2);
        return r;
    }

    @Test
    void firstStagePipelineEndToEnd() {
        PatientDataPort fakeRepo = new PatientDataPort() {
            @Override
            public Optional<FirstStageData> getFirstStageData(int patientId) {
                return patientId == 42 ? Optional.of(FIRST_STAGE_DATA) : Optional.empty();
            }
            @Override
            public Optional<SecondStageResult> getSecondStageData(int patientId) {
                return Optional.empty();
            }
        };

        PythonServicePort fakePython = new PythonServicePort() {
            @Override
            public PythonServicePort.GaResult runFirstStage(List<Double> xList) {
                assertEquals(12, xList.size(), "FirstStage expects 12 clinical values");
                // Verify numeric values pass through correctly
                assertEquals(120.0, xList.get(0));
                assertEquals(3.5, xList.get(1));
                // Verify categorical encoding: Так=1.0, Ні=2.0
                assertEquals(1.0, xList.get(9));   // x110 = "Так"
                assertEquals(2.0, xList.get(10));  // x111 = "Ні"
                assertEquals(1.0, xList.get(11));  // x112 = "Так"
                return SUCCESS_RESULT;
            }
            @Override
            public PythonServicePort.GaResult runSecondStage(List<Double> xList) {
                fail("Should not call SecondStage");
                return null;
            }
        };

        TreatmentService service = new TreatmentService(fakeRepo, fakePython);
        TreatmentService.TreatmentResult result = service.calculateFirstStage(42);

        assertTrue(result.isSuccess());
        assertNull(result.errorType);
        assertEquals(2, result.treatments.size());
        assertEquals(9, result.treatments.get(0).size());
        assertEquals(List.of(1, 2), result.complications);
    }

    @Test
    void secondStagePipelineEndToEnd() {
        PatientDataPort fakeRepo = new PatientDataPort() {
            @Override
            public Optional<FirstStageData> getFirstStageData(int patientId) {
                return Optional.empty();
            }
            @Override
            public Optional<SecondStageResult> getSecondStageData(int patientId) {
                return patientId == 42
                    ? Optional.of(new SecondStageResult.Found(SECOND_STAGE_DATA))
                    : Optional.empty();
            }
        };

        PythonServicePort fakePython = new PythonServicePort() {
            @Override
            public PythonServicePort.GaResult runFirstStage(List<Double> xList) {
                fail("Should not call FirstStage");
                return null;
            }
            @Override
            public PythonServicePort.GaResult runSecondStage(List<Double> xList) {
                assertEquals(9, xList.size(), "SecondStage expects 9 post-condition values");
                // SecondStage encoding: Ні=1.0, Так=2.0
                assertEquals(1.0, xList.get(0));  // pe = "Ні"
                assertEquals(2.0, xList.get(1));  // vab = "Так"
                assertEquals(1.0, xList.get(2));  // pEarly = "Ні"
                return SUCCESS_RESULT;
            }
        };

        TreatmentService service = new TreatmentService(fakeRepo, fakePython);
        TreatmentService.TreatmentResult result = service.calculateSecondStage(42);

        assertTrue(result.isSuccess());
        assertNull(result.errorType);
        assertEquals(2, result.treatments.size());
    }

    @Test
    void firstStageNotFoundPropagatesCorrectly() {
        PatientDataPort emptyRepo = new PatientDataPort() {
            @Override
            public Optional<FirstStageData> getFirstStageData(int id) {
                return Optional.empty();
            }
            @Override
            public Optional<SecondStageResult> getSecondStageData(int id) {
                return Optional.empty();
            }
        };

        PythonServicePort neverCalled = new PythonServicePort() {
            @Override
            public PythonServicePort.GaResult runFirstStage(List<Double> xList) {
                fail("Should not call Python for missing patient");
                return null;
            }
            @Override
            public PythonServicePort.GaResult runSecondStage(List<Double> xList) {
                fail("Should not call Python for missing patient");
                return null;
            }
        };

        TreatmentService service = new TreatmentService(emptyRepo, neverCalled);

        TreatmentService.TreatmentResult r1 = service.calculateFirstStage(999);
        assertFalse(r1.isSuccess());
        assertEquals(TreatmentError.PATIENT_NOT_FOUND, r1.errorType);

        TreatmentService.TreatmentResult r2 = service.calculateSecondStage(999);
        assertFalse(r2.isSuccess());
        assertEquals(TreatmentError.PATIENT_NOT_FOUND, r2.errorType);
    }
}
