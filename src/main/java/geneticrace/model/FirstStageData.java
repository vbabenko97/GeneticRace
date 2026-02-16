// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.model;

/**
 * Typed representation of patient clinical data for FirstStage (operational) treatment calculation.
 * Fields x101-x109 are numeric; x110-x112 are categorical (Ukrainian Yes/No).
 */
public record FirstStageData(
    Patient patient,
    int x101, double x102, double x103, int x104, int x105,
    double x106, double x107, double x108, double x109,
    String x110, String x111, String x112
) {}
