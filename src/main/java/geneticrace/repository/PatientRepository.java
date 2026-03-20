// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.repository;

import geneticrace.db.DatabaseConnection;
import geneticrace.model.FirstStageData;
import geneticrace.model.Patient;
import geneticrace.model.SecondStageData;
import geneticrace.session.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Repository for Patient data access.
 * Uses PreparedStatement to prevent SQL injection.
 */
public class PatientRepository implements PatientDataPort {
    private static final Logger LOGGER = Logger.getLogger(PatientRepository.class.getName());

    private static final String SELECT_ALL =
        "SELECT patientId, surname, firstname, middlename, sex, dateOfBirth FROM Patients";

    private static final String SELECT_BY_ID =
        SELECT_ALL + " WHERE patientId = ?";

    private static final String SELECT_BY_DOCTOR =
        SELECT_ALL + " WHERE doctorID = ? OR secondDoctorID = ?";

    private static final String SELECT_PATIENT_DETAILS =
        "SELECT patientId, doctorID, secondDoctorID, surname, firstname, middlename, " +
        "sex, dateOfBirth, diagnosis, x101, x102, x103, x104, x105, x106, " +
        "x107, x108, x109, x110, x111, x112 FROM Patients WHERE patientId = ?";

    private static final String SELECT_PATIENT_WITH_PC =
        "SELECT p.patientId, p.surname, p.firstname, p.middlename, p.sex, p.dateOfBirth, " +
        "pc.pe, pc.vab, pc.pEarly, pc.plicat, pc.stroke, pc.thrombosis, pc.chyle, pc.avb, pc.snd " +
        "FROM Patients p LEFT JOIN PatientPC pc ON p.patientId = pc.patientID " +
        "WHERE p.patientId = ?";

    /**
     * Gets all patients the current user has access to.
     * Admin sees all, doctors see only their assigned patients.
     */
    public List<Patient> getAccessiblePatients() throws SQLException {
        SessionManager session = SessionManager.getInstance();

        if (session.isAdmin()) {
            return getAllPatients();
        } else {
            return getPatientsByDoctor(session.getCurrentUserId());
        }
    }

    /**
     * Gets all patients (admin only).
     */
    public List<Patient> getAllPatients() throws SQLException {
        List<Patient> patients = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                patients.add(mapPatient(rs));
            }
        }

        LOGGER.fine("Loaded " + patients.size() + " patients");
        return patients;
    }

    /**
     * Gets patients assigned to a specific doctor.
     */
    public List<Patient> getPatientsByDoctor(int doctorId) throws SQLException {
        List<Patient> patients = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_DOCTOR)) {

            pstmt.setInt(1, doctorId);
            pstmt.setInt(2, doctorId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    patients.add(mapPatient(rs));
                }
            }
        }

        LOGGER.fine("Loaded " + patients.size() + " patients for doctor " + doctorId);
        return patients;
    }

    /**
     * Gets a patient by ID.
     */
    public Optional<Patient> getById(int patientId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, patientId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPatient(rs));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<FirstStageData> getFirstStageData(int patientId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_PATIENT_DETAILS)) {

            pstmt.setInt(1, patientId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapFirstStageData(rs, patientId));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<SecondStageResult> getSecondStageData(int patientId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_PATIENT_WITH_PC)) {

            pstmt.setInt(1, patientId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty(); // patient doesn't exist
                }

                Patient patient = mapPatient(rs);

                // Check if PatientPC row exists (LEFT JOIN → NULLs if no PC row)
                rs.getString("pe");
                if (rs.wasNull()) {
                    return Optional.of(new SecondStageResult.PatientHasNoPostConditions(patient));
                }

                SecondStageData data = mapSecondStageData(rs, patient);
                return Optional.of(new SecondStageResult.Found(data));
            }
        }
    }

    private FirstStageData mapFirstStageData(ResultSet rs, int patientId) throws SQLException {
        Patient patient = mapPatient(rs);

        int x101 = mapNonNull(rs, "x101", Integer.class, patientId);
        double x102 = mapNonNull(rs, "x102", Double.class, patientId);
        double x103 = mapNonNull(rs, "x103", Double.class, patientId);
        int x104 = mapNonNull(rs, "x104", Integer.class, patientId);
        int x105 = mapNonNull(rs, "x105", Integer.class, patientId);
        double x106 = mapNonNull(rs, "x106", Double.class, patientId);
        double x107 = mapNonNull(rs, "x107", Double.class, patientId);
        double x108 = mapNonNull(rs, "x108", Double.class, patientId);
        double x109 = mapNonNull(rs, "x109", Double.class, patientId);

        return new FirstStageData(
            patient, x101, x102, x103, x104, x105,
            x106, x107, x108, x109,
            rs.getString("x110"), rs.getString("x111"), rs.getString("x112")
        );
    }

    private <T extends Number> T mapNonNull(ResultSet rs, String column, Class<T> type, int patientId)
            throws SQLException {
        Object value;
        if (type == Integer.class) {
            value = rs.getInt(column);
        } else if (type == Double.class) {
            value = rs.getDouble(column);
        } else {
            throw new IllegalArgumentException("Unsupported numeric type: " + type.getSimpleName());
        }

        if (rs.wasNull()) {
            throw new SQLException("Column " + column + " is NULL for patient " + patientId);
        }
        return type.cast(value);
    }

    private SecondStageData mapSecondStageData(ResultSet rs, Patient patient) throws SQLException {
        return new SecondStageData(
            patient,
            rs.getString("pe"),
            rs.getString("vab"),
            rs.getString("pEarly"),
            rs.getString("plicat"),
            rs.getString("stroke"),
            rs.getString("thrombosis"),
            rs.getString("chyle"),
            rs.getString("avb"),
            rs.getString("snd")
        );
    }

    private static final int TREATMENT_VALUES_COUNT = 9;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private static final String INSERT_FIRST_STAGE =
        "INSERT INTO FirstStage(patientID, x201, x202, x203, x204, x205, x206, x207, x208, x209, lastcommit) " +
        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SECOND_STAGE =
        "INSERT INTO SecondStage(patientID, x401, x402, x403, x404, x405, x406, x407, x408, x409, lastcommit) " +
        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public boolean saveFirstStageResult(int patientId, List<Double> treatment) throws SQLException {
        return saveStageResult(patientId, treatment, INSERT_FIRST_STAGE, "FirstStage");
    }

    @Override
    public boolean saveSecondStageResult(int patientId, List<Double> treatment) throws SQLException {
        return saveStageResult(patientId, treatment, INSERT_SECOND_STAGE, "SecondStage");
    }

    private boolean saveStageResult(int patientId, List<Double> treatment,
                                    String sql, String stageName) throws SQLException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, patientId);
            for (int i = 0; i < TREATMENT_VALUES_COUNT; i++) {
                pstmt.setDouble(i + 2, treatment.get(i));
            }
            pstmt.setString(11, timestamp);

            int rows = pstmt.executeUpdate();
            LOGGER.info("Saved " + stageName + " result for patient " + patientId);
            return rows > 0;
        }
    }

    private Patient mapPatient(ResultSet rs) throws SQLException {
        return new Patient(
            rs.getInt("patientId"),
            rs.getString("surname"),
            rs.getString("firstname"),
            rs.getString("middlename"),
            rs.getString("sex"),
            rs.getString("dateOfBirth")
        );
    }
}
