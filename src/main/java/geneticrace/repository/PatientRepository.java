// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.repository;

import geneticrace.db.DatabaseConnection;
import geneticrace.model.Patient;
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
public class PatientRepository {
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

    private static final String SELECT_PATIENT_PC =
        "SELECT patientID, pe, vab, pEarly, plicat, stroke, thrombosis, chyle, avb, snd " +
        "FROM PatientPC WHERE patientID = ?";
    
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
    
    /**
     * Gets full patient details for treatment calculation (FirstStage).
     * Returns list of values in order expected by GA algorithm.
     */
    public List<String> getPatientDetailsForFirstStage(int patientId) throws SQLException {
        List<String> details = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_PATIENT_DETAILS)) {

            pstmt.setInt(1, patientId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    mapFirstStageDetails(rs, details);
                }
            }
        }

        return details;
    }

    private void mapFirstStageDetails(ResultSet rs, List<String> details) throws SQLException {
        // Demographics (indices 0-4)
        details.add(rs.getString("surname"));
        details.add(rs.getString("firstname"));
        details.add(rs.getString("middlename"));
        details.add(rs.getString("sex"));
        details.add(rs.getString("dateOfBirth"));

        // Clinical values for GA (indices 5-16)
        details.add(Integer.toString(rs.getInt("x101")));
        details.add(Double.toString(rs.getDouble("x102")));
        details.add(Double.toString(rs.getDouble("x103")));
        details.add(Integer.toString(rs.getInt("x104")));
        details.add(Integer.toString(rs.getInt("x105")));
        details.add(Double.toString(rs.getDouble("x106")));
        details.add(Double.toString(rs.getDouble("x107")));
        details.add(Double.toString(rs.getDouble("x108")));
        details.add(Double.toString(rs.getDouble("x109")));
        details.add(rs.getString("x110"));
        details.add(rs.getString("x111"));
        details.add(rs.getString("x112"));
    }
    
    /**
     * Gets patient details for SecondStage including post-condition data.
     */
    public List<String> getPatientDetailsForSecondStage(int patientId) throws SQLException {
        List<String> details = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Basic patient info
            try (PreparedStatement pstmt = conn.prepareStatement(SELECT_PATIENT_DETAILS)) {
                pstmt.setInt(1, patientId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        // Demographics (indices 0-4)
                        details.add(rs.getString("surname"));
                        details.add(rs.getString("firstname"));
                        details.add(rs.getString("middlename"));
                        details.add(rs.getString("sex"));
                        details.add(rs.getString("dateOfBirth"));
                    }
                }
            }

            // Post-condition data
            try (PreparedStatement pstmt = conn.prepareStatement(SELECT_PATIENT_PC)) {
                pstmt.setInt(1, patientId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        mapSecondStageDetails(rs, details);
                    }
                }
            }
        }

        return details;
    }

    private void mapSecondStageDetails(ResultSet rs, List<String> details) throws SQLException {
        // Post-condition values (indices 5-13)
        details.add(rs.getString("pe"));
        details.add(rs.getString("vab"));
        details.add(rs.getString("pEarly"));
        details.add(rs.getString("plicat"));
        details.add(rs.getString("stroke"));
        details.add(rs.getString("thrombosis"));
        details.add(rs.getString("chyle"));
        details.add(rs.getString("avb"));
        details.add(rs.getString("snd"));
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
