package geneticrace.service;

import geneticrace.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TreatmentServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PythonService pythonService;

    private TreatmentService service;

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
        // FirstStage: Так=1.0, Ні=2.0
        // SecondStage: Ні=1.0, Так=2.0
        assertEquals(1.0, service.parseFirstStageValue("Так"));
        assertEquals(2.0, service.parseFirstStageValue("Ні"));
        assertEquals(1.0, service.parseSecondStageValue("Ні"));
        assertEquals(2.0, service.parseSecondStageValue("Так"));
    }

    // Integration-level test with mocks: verify xList passed to PythonService

    @Test
    void calculateFirstStagePassesCorrectXListToPython() throws Exception {
        // Build patient data: 5 demographics + 12 clinical values = 17 total
        List<String> patientData = new ArrayList<>(Arrays.asList(
            "Іванов", "Іван", "Іванович", "Чоловіча", "1990-01-01", // demographics
            "100", "2.5", "3.0", "4", "5", "1.1", "2.2", "3.3", "4.4", "Так", "Ні", "Так" // clinical
        ));

        when(patientRepository.getPatientDetailsForFirstStage(1)).thenReturn(patientData);

        PythonService.GaResult gaResult = new PythonService.GaResult();
        gaResult.treatments = List.of(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0));
        gaResult.complications = List.of(1, 1, 1, 1, 1, 1, 1, 1, 1);
        when(pythonService.runFirstStage(anyList())).thenReturn(gaResult);

        TreatmentService.TreatmentResult result = service.calculateFirstStage(1);

        assertTrue(result.isSuccess());

        // Capture the xList passed to PythonService
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
    void calculateFirstStageReturnsErrorWhenInsufficientData() throws Exception {
        when(patientRepository.getPatientDetailsForFirstStage(1))
            .thenReturn(new ArrayList<>(List.of("a", "b", "c"))); // only 3 values

        TreatmentService.TreatmentResult result = service.calculateFirstStage(1);

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertTrue(result.error.contains("Insufficient"));
    }
}
