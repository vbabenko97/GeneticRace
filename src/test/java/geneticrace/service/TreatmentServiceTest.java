package geneticrace.service;

import geneticrace.model.FirstStageData;
import geneticrace.model.Patient;
import geneticrace.model.SecondStageData;
import geneticrace.repository.PatientDataPort;
import geneticrace.repository.PatientDataPort.SecondStageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TreatmentServiceTest {

    @Mock
    private PatientDataPort patientRepository;

    @Mock
    private PythonServicePort pythonService;

    private TreatmentService service;

    private static final Patient TEST_PATIENT =
        new Patient(1, "Іванов", "Іван", "Іванович", "Чоловіча", "1990-01-01");

    @BeforeEach
    void setUp() {
        service = new TreatmentService(patientRepository, pythonService);
    }

    // parseFirstStageValue tests

    @Test
    void parseFirstStageValueTakReturnsOne() {
        assertEquals(1.0, service.parseFirstStageValue("Так"));
    }

    @Test
    void parseFirstStageValueNiReturnsTwo() {
        assertEquals(2.0, service.parseFirstStageValue("Ні"));
    }

    @Test
    void parseFirstStageValueNumericReturnsDouble() {
        assertEquals(3.14, service.parseFirstStageValue("3.14"), 0.001);
    }

    @Test
    void parseFirstStageValueIntegerReturnsDouble() {
        assertEquals(42.0, service.parseFirstStageValue("42"));
    }

    @Test
    void parseFirstStageValueNullThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> service.parseFirstStageValue(null));
    }

    @Test
    void parseFirstStageValueBlankThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> service.parseFirstStageValue(""));
    }

    @Test
    void parseFirstStageValueWhitespaceOnlyThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> service.parseFirstStageValue("   "));
    }

    @Test
    void parseFirstStageValueTrimsWhitespace() {
        assertEquals(1.0, service.parseFirstStageValue("  Так  "));
        assertEquals(2.0, service.parseFirstStageValue("  Ні  "));
        assertEquals(5.0, service.parseFirstStageValue("  5.0  "));
    }

    @Test
    void parseFirstStageValueUnparseableThrows() {
        assertThrows(NumberFormatException.class,
            () -> service.parseFirstStageValue("abc"));
    }

    // parseSecondStageValue tests — reversed encoding

    @Test
    void parseSecondStageValueNiReturnsOne() {
        assertEquals(1.0, service.parseSecondStageValue("Ні"));
    }

    @Test
    void parseSecondStageValueTakReturnsTwo() {
        assertEquals(2.0, service.parseSecondStageValue("Так"));
    }

    @Test
    void parseSecondStageValueNumericReturnsDouble() {
        assertEquals(7.5, service.parseSecondStageValue("7.5"), 0.001);
    }

    @Test
    void parseSecondStageValueNullThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> service.parseSecondStageValue(null));
    }

    @Test
    void parseSecondStageValueBlankThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> service.parseSecondStageValue(""));
    }

    @Test
    void parseSecondStageValueTrimsWhitespace() {
        assertEquals(1.0, service.parseSecondStageValue("  Ні  "));
        assertEquals(2.0, service.parseSecondStageValue("  Так  "));
    }

    // Verify encoding difference between stages

    @Test
    void firstAndSecondStageHaveReversedEncodings() {
        assertEquals(1.0, service.parseFirstStageValue("Так"));
        assertEquals(2.0, service.parseFirstStageValue("Ні"));
        assertEquals(1.0, service.parseSecondStageValue("Ні"));
        assertEquals(2.0, service.parseSecondStageValue("Так"));
    }

    // calculateFirstStage tests

    @Test
    void calculateFirstStagePassesCorrectXListToPython() throws Exception {
        FirstStageData data = new FirstStageData(
            TEST_PATIENT,
            100, 2.5, 3.0, 4, 5, 1.1, 2.2, 3.3, 4.4, "Так", "Ні", "Так"
        );
        when(patientRepository.getFirstStageData(1)).thenReturn(Optional.of(data));

        PythonServicePort.GaResult gaResult = new PythonServicePort.GaResult();
        gaResult.treatments = List.of(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0));
        gaResult.complications = List.of(1, 1, 1, 1, 1, 1, 1, 1, 1);
        when(pythonService.runFirstStage(anyList())).thenReturn(gaResult);

        TreatmentService.TreatmentResult result = service.calculateFirstStage(1);

        assertTrue(result.isSuccess());
        assertNull(result.errorType);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Double>> captor = ArgumentCaptor.forClass(List.class);
        verify(pythonService).runFirstStage(captor.capture());

        List<Double> xList = captor.getValue();
        assertEquals(12, xList.size());
        assertEquals(100.0, xList.get(0));  // x101
        assertEquals(2.5, xList.get(1));    // x102
        assertEquals(1.0, xList.get(9));    // x110 = "Так" → 1.0
        assertEquals(2.0, xList.get(10));   // x111 = "Ні" → 2.0
        assertEquals(1.0, xList.get(11));   // x112 = "Так" → 1.0
    }

    @Test
    void calculateFirstStageReturnsErrorWhenPatientNotFound() throws Exception {
        when(patientRepository.getFirstStageData(1)).thenReturn(Optional.empty());

        TreatmentService.TreatmentResult result = service.calculateFirstStage(1);

        assertFalse(result.isSuccess());
        assertEquals(TreatmentError.PATIENT_NOT_FOUND, result.errorType);
    }

    @Test
    void calculateFirstStageReturnsErrorOnInvalidClinicalData() throws Exception {
        // x110 = "INVALID" will fail parseFirstStageValue
        FirstStageData data = new FirstStageData(
            TEST_PATIENT,
            100, 2.5, 3.0, 4, 5, 1.1, 2.2, 3.3, 4.4, "INVALID", "Ні", "Так"
        );
        when(patientRepository.getFirstStageData(1)).thenReturn(Optional.of(data));

        TreatmentService.TreatmentResult result = service.calculateFirstStage(1);

        assertFalse(result.isSuccess());
        assertEquals(TreatmentError.INVALID_CLINICAL_DATA, result.errorType);
        assertTrue(result.error.contains("Invalid clinical data"));
    }

    @Test
    void calculateFirstStageReturnsScriptFailedOnPythonError() throws Exception {
        FirstStageData data = new FirstStageData(
            TEST_PATIENT,
            100, 2.5, 3.0, 4, 5, 1.1, 2.2, 3.3, 4.4, "Так", "Ні", "Так"
        );
        when(patientRepository.getFirstStageData(1)).thenReturn(Optional.of(data));

        PythonServicePort.GaResult gaResult = new PythonServicePort.GaResult();
        gaResult.error = "Script crashed";
        when(pythonService.runFirstStage(anyList())).thenReturn(gaResult);

        TreatmentService.TreatmentResult result = service.calculateFirstStage(1);

        assertFalse(result.isSuccess());
        assertEquals(TreatmentError.SCRIPT_FAILED, result.errorType);
        assertEquals("Script crashed", result.error);
    }

    // calculateSecondStage tests

    @Test
    void calculateSecondStagePassesCorrectXListToPython() throws Exception {
        SecondStageData data = new SecondStageData(
            TEST_PATIENT,
            "Ні", "Так", "Ні", "Так", "Ні", "Так", "Ні", "Так", "Ні"
        );
        when(patientRepository.getSecondStageData(1))
            .thenReturn(Optional.of(new SecondStageResult.Found(data)));

        PythonServicePort.GaResult gaResult = new PythonServicePort.GaResult();
        gaResult.treatments = List.of(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0));
        gaResult.complications = List.of(1, 1, 1, 1, 1, 1, 1, 1, 1);
        when(pythonService.runSecondStage(anyList())).thenReturn(gaResult);

        TreatmentService.TreatmentResult result = service.calculateSecondStage(1);

        assertTrue(result.isSuccess());
        assertNull(result.errorType);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Double>> captor = ArgumentCaptor.forClass(List.class);
        verify(pythonService).runSecondStage(captor.capture());

        List<Double> xList = captor.getValue();
        assertEquals(9, xList.size());
        assertEquals(1.0, xList.get(0));  // pe = "Ні" → 1.0
        assertEquals(2.0, xList.get(1));  // vab = "Так" → 2.0
        assertEquals(1.0, xList.get(2));  // pEarly = "Ні" → 1.0
    }

    @Test
    void calculateSecondStageReturnsErrorWhenPatientNotFound() throws Exception {
        when(patientRepository.getSecondStageData(1)).thenReturn(Optional.empty());

        TreatmentService.TreatmentResult result = service.calculateSecondStage(1);

        assertFalse(result.isSuccess());
        assertEquals(TreatmentError.PATIENT_NOT_FOUND, result.errorType);
    }

    @Test
    void calculateSecondStageReturnsErrorWhenNoPostConditionData() throws Exception {
        when(patientRepository.getSecondStageData(1))
            .thenReturn(Optional.of(new SecondStageResult.PatientHasNoPostConditions(TEST_PATIENT)));

        TreatmentService.TreatmentResult result = service.calculateSecondStage(1);

        assertFalse(result.isSuccess());
        assertEquals(TreatmentError.NO_POST_CONDITION_DATA, result.errorType);
        assertTrue(result.error.contains("Іванов"));
    }

    @Test
    void calculateSecondStageReturnsErrorOnInvalidPostConditionData() throws Exception {
        // "BOGUS" will fail parseSecondStageValue
        SecondStageData data = new SecondStageData(
            TEST_PATIENT,
            "BOGUS", "Так", "Ні", "Так", "Ні", "Так", "Ні", "Так", "Ні"
        );
        when(patientRepository.getSecondStageData(1))
            .thenReturn(Optional.of(new SecondStageResult.Found(data)));

        TreatmentService.TreatmentResult result = service.calculateSecondStage(1);

        assertFalse(result.isSuccess());
        assertEquals(TreatmentError.INVALID_CLINICAL_DATA, result.errorType);
        assertTrue(result.error.contains("Invalid post-condition data"));
    }
}
