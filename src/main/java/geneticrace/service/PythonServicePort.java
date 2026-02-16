// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.service;

import java.io.IOException;
import java.util.List;

/**
 * Interface for Python GA script execution.
 * Allows TreatmentService to be tested with a simple mock (no subclass mock maker needed).
 */
public interface PythonServicePort {

    /**
     * Result of a Python GA script execution.
     */
    class GaResult {
        public List<List<Double>> treatments;
        public List<Integer> complications;
        public String error;

        public boolean isSuccess() {
            return error == null && treatments != null;
        }
    }

    GaResult runFirstStage(List<Double> xList) throws IOException, InterruptedException;

    GaResult runSecondStage(List<Double> xList) throws IOException, InterruptedException;
}
