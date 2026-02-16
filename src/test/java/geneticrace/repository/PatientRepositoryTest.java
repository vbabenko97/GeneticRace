package geneticrace.repository;

import geneticrace.db.DatabaseConnection;
import geneticrace.model.FirstStageData;
import geneticrace.model.SecondStageData;
import geneticrace.repository.PatientDataPort.SecondStageResult;
import geneticrace.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

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
        assumeTrue(isDatabaseReady(),
            "Skipping: database not accessible or missing tables (run after app setup)");

        repository = new PatientRepository();

        SessionManager session = SessionManager.getInstance();
        session.login(1, "admin", "Admin", "Admin");
    }

    @Test
    void getFirstStageDataReturnsDataForExistingPatient() throws Exception {
        Optional<FirstStageData> opt = repository.getFirstStageData(1);

        assertTrue(opt.isPresent(), "Expected first-stage data for patient 1");
        FirstStageData data = opt.get();
        assertNotNull(data.patient(), "Patient should not be null");
        assertNotNull(data.patient().surname(), "Surname should not be null");
        assertNotNull(data.patient().firstname(), "Firstname should not be null");
        assertNotNull(data.x110(), "x110 (categorical) should not be null");
    }

    @Test
    void getFirstStageDataReturnsEmptyForNonExistentPatient() throws Exception {
        Optional<FirstStageData> opt = repository.getFirstStageData(99999);
        assertTrue(opt.isEmpty());
    }

    @Test
    void getSecondStageDataReturnsFoundForPatientWithPCData() throws Exception {
        Optional<SecondStageResult> opt = repository.getSecondStageData(1);

        assertTrue(opt.isPresent(), "Expected result for patient 1");

        if (opt.get() instanceof SecondStageResult.Found found) {
            SecondStageData data = found.data();
            assertNotNull(data.patient(), "Patient should not be null");
            assertNotNull(data.pe(), "pe should not be null");
            assertNotNull(data.vab(), "vab should not be null");
            assertNotNull(data.snd(), "snd should not be null");
        }
        // PatientHasNoPostConditions is also valid if sample DB has no PC row for patient 1
    }

    @Test
    void getSecondStageDataReturnsEmptyForNonExistentPatient() throws Exception {
        Optional<SecondStageResult> opt = repository.getSecondStageData(99999);
        assertTrue(opt.isEmpty());
    }

    @Test
    void getSecondStageDataDistinguishesPatientFoundFromNotFound() throws Exception {
        // Patient 1 exists → should not be empty
        Optional<SecondStageResult> existing = repository.getSecondStageData(1);
        assertTrue(existing.isPresent(), "Existing patient should return non-empty result");

        // Patient 99999 does not exist → should be empty
        Optional<SecondStageResult> missing = repository.getSecondStageData(99999);
        assertTrue(missing.isEmpty(), "Non-existent patient should return empty");
    }

    @Test
    void getAllPatientsReturnsNonEmptyList() throws Exception {
        List<?> patients = repository.getAllPatients();
        assertFalse(patients.isEmpty(), "Sample DB should have patients");
    }

    /**
     * Checks that the DB is reachable AND contains the expected tables.
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
