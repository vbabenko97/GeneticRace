#!/usr/bin/env python3
# Copyright © 2019, 2026. All rights reserved.
# Authors: Vitalii Babenko, Anastasiia Dydyk
# Refactored: 2026 - shared GA core extracted from FirstStage/SecondStage

"""
Shared genetic algorithm core: AHP (Saati) method, selection, crossover, mutation,
and the main GA loop.  Stage-specific logic (GMDH models, parameter ranges) lives
in FirstStage.py / SecondStage.py and is injected via function parameters.
"""

import argparse
import copy
import json
import logging
import logging.handlers
import math
import os
import sys
from random import choices


# --- Configuration constants ---

POPULATION_NUMBER = 32
POPULATION_RANGE = range(POPULATION_NUMBER)
GA_RUNS = 20
SOLUTION_SIZE = 9
MAX_GENERATIONS = 500

# Tolerance for floating-point comparisons in convergence checks
_FLOAT_REL_TOL = 1e-12


# --- Logging setup ---

def setup_logging(name):
    """Configure logging to a rotating file. Falls back to NullHandler."""
    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)

    log_dir = os.path.expanduser("~/.geneticrace/logs")
    try:
        os.makedirs(log_dir, exist_ok=True)
        handler = logging.handlers.RotatingFileHandler(
            os.path.join(log_dir, "ga.log"),
            maxBytes=1_000_000,
            backupCount=3,
        )
        handler.setFormatter(logging.Formatter(
            "%(asctime)s [%(name)s] %(levelname)s %(message)s"
        ))
        logger.addHandler(handler)
    except OSError:
        logger.addHandler(logging.NullHandler())

    return logger


# --- Math helpers ---

def geometric_mean(values):
    """Compute geometric mean using stdlib math (no scipy dependency)."""
    log_sum = sum(math.log(v) for v in values)
    return math.exp(log_sum / len(values))


# --- AHP / Saati ---

def saati_method():
    """Calculate AHP priority vectors."""
    first_row = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0]
    relationship_matrix = [first_row]

    for i in range(2, 10):
        relationship_matrix.append([v / i for v in first_row])

    gmean_list = [geometric_mean(row) for row in relationship_matrix]
    total = sum(gmean_list)
    return [g / total for g in gmean_list]


def calculate_saati(coeff_list, criterion_list):
    """Calculate Saati values for all solutions."""
    return [
        round(sum(-c * coeff for c, coeff in zip(c_list, coeff_list)), 15)
        for c_list in criterion_list
    ]


# --- Selection helpers ---

def get_discrepancies(perfect_value, saati_list):
    """Calculate discrepancies from perfect value."""
    return [perfect_value - s for s in saati_list]


def get_probabilities(disc_list):
    """Convert discrepancies to selection probabilities.

    Clamps each discrepancy to a small positive floor to prevent
    ZeroDivisionError (when a solution already matches the optimum)
    and negative probabilities (when a solution overshoots).
    """
    eps = 1e-15
    clamped = [max(d, eps) for d in disc_list]
    inv_list = [1.0 / d for d in clamped]
    total = sum(inv_list)
    return [i / total for i in inv_list]


def get_mothers(father_list, prob_list):
    """Select mother indices for crossover."""
    mother_list = []
    for i in range(POPULATION_NUMBER):
        m = choices(POPULATION_RANGE, prob_list)[0]
        while m == father_list[i]:
            m = choices(POPULATION_RANGE, prob_list)[0]
        mother_list.append(m)
    return mother_list


# --- Genetic operators ---

def crossover(sol_list, temp_sol_list, father_list, mother_list):
    """Perform crossover between parent solutions."""
    for i in range(POPULATION_NUMBER):
        j, k = father_list[i], mother_list[i]
        for idx in range(SOLUTION_SIZE):
            if idx < (i % 8) + 1:
                sol_list[i][idx] = temp_sol_list[j if (i % 16) < 8 else k][idx]
            else:
                sol_list[i][idx] = temp_sol_list[k if (i % 16) < 8 else j][idx]
    return sol_list


def mutation(sol_list, random_solution_fn):
    """Apply mutation to population (replace all but the best with random solutions)."""
    for i in range(1, POPULATION_NUMBER):
        sol_list[i] = random_solution_fn()
    return sol_list


# --- Main GA loop ---

def run_ga(x_list, random_solution_fn, calculate_criterions_fn,
           calculate_perfect_value_fn, logger):
    """Main genetic algorithm for finding optimal treatment strategies.

    Parameters
    ----------
    x_list : list[float]
        Patient clinical input values.
    random_solution_fn : callable() -> list[float]
        Generates a single random treatment solution.
    calculate_criterions_fn : callable(x_list, sol_list) -> list[list[int]]
        Evaluates GMDH condition criteria for a population.
    calculate_perfect_value_fn : callable(x_list, coeff_list) -> float
        Computes the ideal Saati value for the patient.
    logger : logging.Logger
        Logger instance for this stage.
    """
    coeff_list = saati_method()
    perfect_value = calculate_perfect_value_fn(x_list, coeff_list)

    treatment_list = []
    complication_list = []

    logger.info("Starting calculation with %d runs", GA_RUNS)

    for run in range(GA_RUNS):
        sol_list = [random_solution_fn() for _ in range(POPULATION_NUMBER)]
        mean_list = []

        for generation in range(MAX_GENERATIONS):
            criterion_list = calculate_criterions_fn(x_list, sol_list)
            saati_list = calculate_saati(coeff_list, criterion_list)

            # Check for optimal solution (using tolerance instead of exact equality)
            found = False
            for i in range(POPULATION_NUMBER):
                if math.isclose(saati_list[i], perfect_value, rel_tol=_FLOAT_REL_TOL):
                    treatment_list.append(sol_list[i])
                    complication_list.append(criterion_list[i])
                    found = True
                    break

            if found:
                break

            # No optimal found — continue evolution
            disc_list = get_discrepancies(perfect_value, saati_list)
            mean_disc = sum(disc_list) / len(disc_list)
            mean_list.append(mean_disc)

            # Stagnation detection (tolerance-based)
            if (len(mean_list) > 2
                    and math.isclose(mean_list[-2], mean_list[-1], rel_tol=_FLOAT_REL_TOL)):
                sol_list = mutation(copy.deepcopy(sol_list), random_solution_fn)
            else:
                prob_list = get_probabilities(disc_list)
                father_list = choices(POPULATION_RANGE, prob_list, k=POPULATION_NUMBER)

                if len(set(father_list)) <= 1:
                    treatment_list.append(sol_list[father_list[0]])
                    complication_list.append(criterion_list[father_list[0]])
                    break

                mother_list = get_mothers(father_list, prob_list)
                temp_sol_list = copy.deepcopy(sol_list)
                sol_list = crossover(sol_list, temp_sol_list, father_list, mother_list)
        else:
            # Generation limit reached — take the best solution from final population
            disc_list = get_discrepancies(perfect_value, saati_list)
            best_idx = min(range(POPULATION_NUMBER), key=lambda i: disc_list[i])
            treatment_list.append(sol_list[best_idx])
            complication_list.append(criterion_list[best_idx])
            logger.warning("Run %d hit generation limit (%d)", run, MAX_GENERATIONS)

    logger.info("Found %d solutions", len(treatment_list))

    return {
        "treatments": treatment_list[:5],
        "complications": complication_list[0] if complication_list else []
    }


# --- CLI entry point shared by stage scripts ---

def run_stage_cli(description, expected_input_length, random_solution_fn,
                  calculate_criterions_fn, calculate_perfect_value_fn, logger):
    """CLI boilerplate shared by FirstStage and SecondStage scripts."""
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--input", type=str, required=True, help="JSON input with xList")
    args = parser.parse_args()

    try:
        data = json.loads(args.input)
        x_list = data["xList"]

        if len(x_list) != expected_input_length:
            raise ValueError(f"Expected {expected_input_length} input values, got {len(x_list)}")

        result = run_ga(x_list, random_solution_fn, calculate_criterions_fn,
                        calculate_perfect_value_fn, logger)
        print(json.dumps(result))

    except Exception as e:
        print(json.dumps({"error": str(e)}), file=sys.stderr)
        sys.exit(1)
