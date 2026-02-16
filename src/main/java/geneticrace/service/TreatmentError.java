// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.service;

/**
 * Typed error categories for treatment calculation failures.
 * Allows UI to branch on error type without parsing strings.
 */
public enum TreatmentError {
    PATIENT_NOT_FOUND,
    NO_POST_CONDITION_DATA,
    INVALID_CLINICAL_DATA,
    SCRIPT_FAILED,
    CALCULATION_FAILED
}
