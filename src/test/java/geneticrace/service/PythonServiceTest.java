package geneticrace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PythonServiceTest {

    private PythonService pythonService;

    @BeforeEach
    void setUp() {
        pythonService = new PythonService();
    }

    // parseOutput tests

    @Test
    void parseOutputReturnsErrorOnNonZeroExitCode() {
        PythonServicePort.GaResult result = pythonService.parseOutput("", "some error\n", 1);

        assertFalse(result.isSuccess());
        assertTrue(result.error.contains("some error"));
    }

    @Test
    void parseOutputReturnsErrorOnEmptyStderr() {
        PythonServicePort.GaResult result = pythonService.parseOutput("", "", 1);

        assertFalse(result.isSuccess());
        assertTrue(result.error.contains("Script failed"));
    }

    @Test
    void parseOutputParsesValidJson() {
        String json = "{\"treatments\":[[1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0]]," +
                      "\"complications\":[1,1,1,1,1,1,1,1,1]}";

        PythonServicePort.GaResult result = pythonService.parseOutput(json, "", 0);

        assertTrue(result.isSuccess());
        assertEquals(1, result.treatments.size());
        assertEquals(9, result.treatments.get(0).size());
        assertEquals(1.0, result.treatments.get(0).get(0));
        assertEquals(9, result.complications.size());
    }

    @Test
    void parseOutputReturnsErrorOnInvalidJson() {
        PythonServicePort.GaResult result = pythonService.parseOutput("not json", "", 0);

        assertFalse(result.isSuccess());
        assertTrue(result.error.contains("Failed to parse output"));
    }

    @Test
    void parseOutputReturnsErrorOnEmptyStdout() {
        PythonServicePort.GaResult result = pythonService.parseOutput("", "", 0);

        assertFalse(result.isSuccess());
        assertTrue(result.error.contains("no output"));
    }

    @Test
    void parseOutputHandlesMultipleTreatments() {
        String json = "{\"treatments\":" +
                      "[[1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0]," +
                      "[9.0,8.0,7.0,6.0,5.0,4.0,3.0,2.0,1.0]]," +
                      "\"complications\":[1,2,1,1,1,1,1,1,1]}";

        PythonServicePort.GaResult result = pythonService.parseOutput(json, "", 0);

        assertTrue(result.isSuccess());
        assertEquals(2, result.treatments.size());
        assertEquals(9.0, result.treatments.get(1).get(0));
    }

    @Test
    void parseOutputHandlesErrorFieldInJson() {
        String json = "{\"error\":\"Expected 12 input values, got 9\"}";

        PythonServicePort.GaResult result = pythonService.parseOutput(json, "", 0);

        assertFalse(result.isSuccess());
        assertEquals("Expected 12 input values, got 9", result.error);
    }

    // Additional boundary tests

    @Test
    void parseOutputPreservesStderrInErrorMessage() {
        PythonServicePort.GaResult result = pythonService.parseOutput(
            "", "Traceback: something broke\n", 1);

        assertFalse(result.isSuccess());
        assertTrue(result.error.contains("Traceback: something broke"));
    }

    @Test
    void parseOutputTrimsStderrWhitespace() {
        PythonServicePort.GaResult result = pythonService.parseOutput(
            "", "  error with spaces  \n", 1);

        assertFalse(result.isSuccess());
        assertTrue(result.error.contains("error with spaces"));
    }

    @Test
    void parseOutputMalformedJsonWithExitCodeZero() {
        // Script exits 0 but produces garbage stdout
        PythonServicePort.GaResult result = pythonService.parseOutput(
            "{truncated", "", 0);

        assertFalse(result.isSuccess());
        assertTrue(result.error.contains("Failed to parse output"));
    }

    @Test
    void parseOutputNullStdoutWithExitCodeZero() {
        PythonServicePort.GaResult result = pythonService.parseOutput("null", "", 0);

        assertFalse(result.isSuccess());
        assertTrue(result.error.contains("no output"));
    }

    @Test
    void parseOutputPartialJsonMissingTreatments() {
        String json = "{\"complications\":[1,1,1,1,1,1,1,1,1]}";

        PythonServicePort.GaResult result = pythonService.parseOutput(json, "", 0);

        // Parsed successfully by Gson, but treatments is null → isSuccess returns false
        assertNull(result.treatments);
        assertFalse(result.isSuccess());
    }
}
