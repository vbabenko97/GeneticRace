// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.model;

/**
 * Typed representation of patient post-condition data for SecondStage (medication) treatment calculation.
 * All fields are categorical (Ukrainian Yes/No).
 */
public record SecondStageData(
    Patient patient,
    String pe, String vab, String pEarly, String plicat, String stroke,
    String thrombosis, String chyle, String avb, String snd
) {}
