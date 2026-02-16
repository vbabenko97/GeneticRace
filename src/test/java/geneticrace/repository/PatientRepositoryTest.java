package geneticrace.repository;

import geneticrace.db.DatabaseConnection;
import geneticrace.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke test for PatientRepository against the real sample SQLite DB.
 * Tests skip gracefully if the database is not accessible or has no tables.
 */
class PatientRepositoryTest {

    private PatientRepository repository;

    @BeforeEach
    void setUp() {
        // Skip tests if DB is not accessible or tables don't exist
        assumeTrue(isDatabaseReady(),
            "Skipping: database not accessible or missing tables (run after app setup)");

        repository = new PatientRepository();

        // Ensure session is set for access control
        SessionManager session = SessionManager.getInstance();
        session.login(1, "admin", "Admin", "Admin");
    }

    @Test
    void getPatientDetailsForFirstStageReturnsCorrectSize() throws Exception {
        List<String> details = repository.getPatientDetailsForFirstStage(1);

        // 5 demographics + 12 clinical values = 17
        assertEquals(17, details.size(), "Expected 17 values (5 demographics + 12 clinical)");
    }

    @Test
    void getPatientDetailsForFirstStageReturnsDemographicsFirst() throws Exception {
        List<String> details = repository.getPatientDetailsForFirstStage(1);

        assumeTrue(!details.isEmpty(), "No data returned for patient 1");

        // First 5 should be demographics
        assertNotNull(details.get(0), "surname should not be null");
        assertNotNull(details.get(1), "firstname should not be null");
        // index 3 = sex, index 4 = dateOfBirth
        assertNotNull(details.get(3), "sex should not be null");
        assertNotNull(details.get(4), "dateOfBirth should not be null");
    }

    @Test
    void getPatientDetailsForSecondStageReturnsCorrectSize() throws Exception {
        List<String> details = repository.getPatientDetailsForSecondStage(1);

        // 5 demographics + 9 post-condition values = 14
        assertEquals(14, details.size(), "Expected 14 values (5 demographics + 9 post-condition)");
    }

    @Test
    void getPatientDetailsForSecondStageContainsPostConditionValues() throws Exception {
        List<String> details = repository.getPatientDetailsForSecondStage(1);

        assumeTrue(details.size() >= 14, "Insufficient data for patient 1");

        // Post-condition values at indices 5-13 should be text (Yes/No type)
        for (int i = 5; i < 14; i++) {
            assertNotNull(details.get(i), "Post-condition value at index " + i + " should not be null");
        }
    }

    @Test
    void getPatientDetailsForFirstStageReturnsEmptyForNonExistentPatient() throws Exception {
        List<String> details = repository.getPatientDetailsForFirstStage(99999);
        assertTrue(details.isEmpty());
    }

    @Test
    void getAllPatientsReturnsNonEmptyList() throws Exception {
        List<?> patients = repository.getAllPatients();
        assertFalse(patients.isEmpty(), "Sample DB should have patients");
    }

    /**
     * Checks that the DB is reachable AND contains the expected tables.
     * SQLite silently creates empty files, so SELECT 1 alone is insufficient.
     */
    private static boolean isDatabaseReady() {
        try (Connection conn = DatabaseConnection.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "Patients", null)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
}
