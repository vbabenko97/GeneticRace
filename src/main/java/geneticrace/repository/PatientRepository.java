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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
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

        int x101 = rs.getInt("x101");
        if (rs.wasNull()) throw new SQLException("Column x101 is NULL for patient " + patientId);

        double x102 = rs.getDouble("x102");
        if (rs.wasNull()) throw new SQLException("Column x102 is NULL for patient " + patientId);

        double x103 = rs.getDouble("x103");
        if (rs.wasNull()) throw new SQLException("Column x103 is NULL for patient " + patientId);

        int x104 = rs.getInt("x104");
        if (rs.wasNull()) throw new SQLException("Column x104 is NULL for patient " + patientId);

        int x105 = rs.getInt("x105");
        if (rs.wasNull()) throw new SQLException("Column x105 is NULL for patient " + patientId);

        double x106 = rs.getDouble("x106");
        if (rs.wasNull()) throw new SQLException("Column x106 is NULL for patient " + patientId);

        double x107 = rs.getDouble("x107");
        if (rs.wasNull()) throw new SQLException("Column x107 is NULL for patient " + patientId);

        double x108 = rs.getDouble("x108");
        if (rs.wasNull()) throw new SQLException("Column x108 is NULL for patient " + patientId);

        double x109 = rs.getDouble("x109");
        if (rs.wasNull()) throw new SQLException("Column x109 is NULL for patient " + patientId);

        return new FirstStageData(
            patient, x101, x102, x103, x104, x105,
            x106, x107, x108, x109,
            rs.getString("x110"), rs.getString("x111"), rs.getString("x112")
        );
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
