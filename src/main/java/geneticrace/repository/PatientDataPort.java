// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.repository;

import geneticrace.model.FirstStageData;
import geneticrace.model.Patient;
import geneticrace.model.SecondStageData;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Narrow interface for patient clinical data access used by TreatmentService.
 * Follows ISP — only exposes stage-data retrieval, not patient listing.
 */
public interface PatientDataPort {

    /**
     * Gets first-stage clinical data for a patient.
     * @return the data, or empty if patient doesn't exist or has no clinical data
     */
    Optional<FirstStageData> getFirstStageData(int patientId) throws SQLException;

    /**
     * Result of looking up second-stage (post-condition) data.
     * Distinguishes "patient not found" (Optional.empty) from
     * "patient exists but has no post-condition row" (PatientHasNoPostConditions).
     */
    sealed interface SecondStageResult {
        record Found(SecondStageData data) implements SecondStageResult {}
        record PatientHasNoPostConditions(Patient patient) implements SecondStageResult {}
    }

    /**
     * Gets second-stage post-condition data for a patient.
     * @return empty if patient doesn't exist; Found if full data present;
     *         PatientHasNoPostConditions if patient exists but has no PC row
     */
    Optional<SecondStageResult> getSecondStageData(int patientId) throws SQLException;
}
