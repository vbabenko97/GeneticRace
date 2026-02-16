#!/usr/bin/env python3
# Copyright © 2019, 2026. All rights reserved.
# Authors: Vitalii Babenko, Anastasiia Dydyk
# Refactored: 2026 - JSON IPC, CLI arguments

"""
FirstStage genetic algorithm for finding operational treatment strategies.
Uses GMDH classification models and AHP optimization.
"""

import argparse
import json
import logging
import logging.handlers
import os
import sys
from random import randint, choices
import copy
from scipy.stats.mstats import gmean


# --- Configuration constants ---

POPULATION_NUMBER = 32
POPULATION_RANGE = range(POPULATION_NUMBER)
GA_RUNS = 20
SOLUTION_SIZE = 9
MAX_GENERATIONS = 500

# Treatment parameter ranges for operational treatment (x201-x209)
X201_RANGE = (1, 10000)    # scaled by /100
X202_RANGE = (1, 25000)    # scaled by /100
X203_RANGE = (5, 60)
X204_RANGE = (1, 5)
X205_RANGE = (1, 2)
X206_RANGE = (1, 2)
X207_RANGE = (1, 2)
X208_RANGE = (1, 2)
X209_RANGE = (1, 2)

# --- Logging setup ---

def _setup_logging():
    """Configure logging to a rotating file. Falls back to NullHandler."""
    logger = logging.getLogger("FirstStage")
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

logger = _setup_logging()


# --- GA functions ---

def calculate_criterions(x_list, sol_list):
    """Calculate condition criteria for a list of solutions."""
    x101, x102, x103, x104, x105, x106, x107, x108, x109, x110, x111, x112 = x_list

    criterion_list = []
    for u_list in sol_list:
        x201, x202, x203, x204, x205, x206, x207, x208, x209 = u_list

        # GMDH classification models for early postoperative period
        x301_eq_two = -0.832705 + x111**(-1)*x201*0.00291197 + x106*x208*(-0.00135807) + x105*x109*(-0.00232767) + x101**(-1)*x112*17.0861 + x103**(-1)*x109**(-1)*10.4146 + x101*x202*0.000502042 + x205**(-1)*x207*0.358865 + x109**(-1)*x202*(-0.00771969) + x202*x204**(-1)*(-0.0530489) + x101**(-1)*x204**(-1)*35.5495
        x302_eq_two = 1.8959 + x103*x104*4.62265e-05 + x105*x205**(-1)*(-0.0705953) + x104**(-1)*x106*(-0.0233172) + x201**(-1)*x207**(-1)*(-41.7888) + x107**(-1)*x108*0.0905328 + x103**(-1)*x207**(-1)*(-121.413) + x111*x209**(-1)*(-0.585375) + x105**(-1)*x109*0.620899 + x106**(-1)*x109**(-1)*(-2.62234) + x107*x204**(-1)*0.12513
        x303_eq_two = -0.0102022 + x201**(-1)*x203**(-1)*716.757 + x106*x209*0.00518668 + x103**(-1)*x106*(-0.994431) + x103*x112*(-0.000324448) + x105**(-1)*x108*(-1.12948) + x110**(-1)*x209*0.247751 + x103**(-1)*x109**(-1)*(-2.46223) + x106*x107**(-1)*(-0.00429694) + x103**(-1)*x107**(-1)*73.8395 + x108*x204*0.0123085
        x304_eq_two = 1.44158 + x102*x202*(-0.00673552) + x105*x112*(-0.00959038) + x202*x207**(-1)*0.044888 + x206*x209**(-1)*0.518793 + x104*x112*(-0.0113419) + x101*x108*0.000239275 + x107*x201*(-0.00032867) + x105**(-1)*x202**(-1)*(-11.7529) + x103*x111**(-1)*0.0013597 + x103*x206**(-1)*(-0.000968004)
        x305_eq_two = -1.6615 + x104*x110*(-0.0346257) + x105**(-1)*x112*3.10562 + x103**(-1)*x208*(-21.4803) + x109**(-1)*x201*0.00047157 + x108*x202*0.00862588 + x110*x203**(-1)*10.1393 + x101**(-1)*x106*(-0.159258) + x109**(-1)*x112**(-1)*(-0.0942774) + x104*x203*0.00720075 + x110*x202**(-1)*0.557958
        x306_eq_two = -0.975001 + x108*x209*0.0475912 + x105**(-1)*x112*0.697149 + x111*x208*0.46606 + x112**(-1)*x201**(-1)*(-242.356) + x108*x112**(-1)*0.261032 + x201**(-1)*x209*58.4543 + x108*x208**(-1)*0.30269 + x108*x111*(-0.183598) + x106**(-1)*x112**(-1)*12.3259 + x106**(-1)*x111**(-1)*(-13.7248)
        x307_eq_two = -4.40144 + x102**(-1)*x110**(-1)*(-3.4971) + x112*x202*(-0.0306613) + x103**(-1)*x204*(-43.0255) + x111*x205*0.155946 + x110*x208*0.0941545 + x203*x206**(-1)*0.386844 + x203**(-1)*x206*12.2186 + x101*x204*0.00132263 + x101**(-1)*x103**(-1)*6595.83 + x202*x208*0.0178928
        x308_eq_two = 0.756968 + x103**(-1)*x205**(-1)*(-645.415) + x101**(-1)*x109*7.78827 + x103*x206*(-0.000590993) + x109*x204*(-0.0105553) + x101*x103**(-1)*(-0.507904) + x103**(-1)*x203*21.8255 + x203**(-1)*x205**(-1)*19.0435 + x109*x205*(-0.0813564) + x104**(-1)*x109*1.49701 + x101*x208*0.000912961
        x309_eq_two = 1.28185 + x102*x206*0.0191846 + x204*x207*0.0272657 + x101*x207**(-1)*(-0.00709976) + x101**(-1)*x204**(-1)*(-60.0672) + x104**(-1)*x106*(-0.0216769) + x108*x205*0.07052 + x105*x108*(-0.00645342) + x108*x204*(-0.023769) + x108*x207**(-1)*0.123772 + x104*x105**(-1)*(-0.24784)

        c_list = [
            1 if round(x301_eq_two) == 0 else 2,
            1 if round(x302_eq_two) == 0 else 2,
            1 if round(x303_eq_two) == 0 else 2,
            1 if round(x304_eq_two) == 0 else 2,
            1 if round(x305_eq_two) == 0 else 2,
            1 if round(x306_eq_two) == 0 else 2,
            1 if round(x307_eq_two) == 0 else 2,
            1 if round(x308_eq_two) == 0 else 2,
            1 if round(x309_eq_two) == 0 else 2,
        ]
        criterion_list.append(c_list)

    return criterion_list


def calculate_perfect_value(x_list, coeff_list):
    """Calculate the ideal Saati value for the given patient."""
    x101, x102, x103, x104, x105, x106, x107, x108, x109, x110, x111, x112 = x_list

    x303_eq_two = 0.369661 + x106*x111*0.00310719 + x106*x108*(-0.000751507) + x103*x106*(-1.03015e-05) + x103*x108*4.53861e-05 + x111*(-0.357763) + x106*x112*0.00105434 + x102*x112*(-0.00670676) + x102*x108*0.00417287 + x103*0.000583292 + x102*x106*(-8.76937e-05)
    x304_eq_two = 0.175478 + x110*x112*(-0.154013) + x101*x111*0.0055126 + x103*x107*0.000703851 + x107*x109*(-0.0270747) + x103*x108*(-0.00028867) + x102*x110*0.0515488 + x107*x112*(-0.413692) + x108*x112*0.0185017 + x108*x109*0.00789789 + x101*x110*(-0.00487274) + x102*x111*(-0.0490524) + x101*x108*(-0.000395743) + x101*x107*0.000153086 + x107*0.920759 + x105*x107*(-0.0243143) + x105*x112*0.0514067 + x105*(-0.0825905) + x105*x108*0.00620842
    x305_eq_two = -0.227066 + x105*x111*(-0.0734265) + x107*x108*(-0.028469) + x103*x107*0.000299704 + x101*x104*0.000299719 + x104*x108*(-0.0224514) + x111*x112*0.358285 + x104*x106*0.000163868 + x103*x106*(-9.55294e-06) + x103*x108*0.0002392 + x105*x106*(-0.000231602) + x108*0.512628 + x108*x112*(-0.0751962) + x105*x110*0.0754866 + x101*x107*(-0.001482) + x102*x108*(-0.0126606) + x106*x108*0.000532562 + x102*x104*0.00464377 + x102*x110*(-0.0334515) + x105*x108*0.00934543 + x105*x112*(-0.0342288) + x101*x102*0.000261614 + x101*x110*(-0.00219381) + x107*x112*0.109794
    x306_eq_two = -0.0114987 + x108*x109*(-0.00578912) + x104**(-1)*x109**(-1)*1.1257 + x109**(-1)*x112**(-1)*(-0.0419663) + x106*x111**(-1)*(-0.00358674) + x106*x109*(-0.000515749) + x109*x110*0.0632775 + x109*x111*(-0.0298963) + x108*x109**(-1)*0.0109361 + x109**(-1)*x112*(-0.0432296) + x102*x109*0.00138459 + x103**(-1)*x109**(-1)*(-5.55096) + x106**(-1)*x108*0.88429 + x110*x111**(-1)*0.261843 + x106*x108*0.000715337 + x108*x110*(-0.0391553) + x103**(-1)*x108*2.59644 + x106*x107**(-1)*0.000557014 + x107*x110**(-1)*0.341142 + x105*x110**(-1)*(-0.00959482) + x107*x111**(-1)*(-0.0715939) + x106**(-1)*x109*(-0.78666) + x103**(-1)*x106**(-1)*(-656.336) + x106*x110**(-1)*(-0.00633399) + x104**(-1)*x106*0.0286779 + x104**(-1)*x107*(-0.886387) + x107*x109*0.0132561 + x105*x109*(-0.00200309) + x107*x110*(-0.0264503)
    x307_eq_two = 2.6937 + x101*x110*0.00637564 + x101*x109*(-0.00117356) + x109**2*(-0.00267333) + x105*x111*0.00413604 + x103*x112*(-0.00073236) + x109*0.204242 + x103*x106*1.62601e-05 + x106**2*(-1.08259e-05) + x102*x112*0.153545 + x101*x102*(-0.000396763) + x109*x112*(-0.104191) + x103*0.00284761 + x102*x103*(-0.000144981) + x106*x107*0.00251191 + x103*x110*(-0.000936957) + x101*x105*(-0.000577864) + x104*x105*0.00385265 + x112**2*(-0.83391) + x102*(-0.307295) + x102**2*0.00483699 + x107*(-0.112072) + x104*x110*(-0.0459923) + x101*x104*0.000413922 + x110*x112*0.216481 + x104*x109*0.0068067 + x102*x108*(-0.00353053) + x106*(-0.00962964) + x105*x106*0.000303744 + x107**2*(-0.0391921)
    x308_eq_two = -1.14363 + x102*x109*0.00138057 + x103*x106*1.10351e-05 + x105*x111*0.122882 + x106*x108*(-0.0012283) + x108*x109*(-0.0121911) + x110**2*0.44975 + x101*x107*0.00167406 + x106*x111*0.00572702 + x103*0.00782358 + x103**2*(-5.48141e-06) + x103*x107*(-0.000982276) + x101**2*(-2.41892e-05) + x103*x108*0.000759317 + x101*x110*0.00210718 + x105*x108*(-0.01283) + x107*x108*0.0327821 + x103*x105*(-0.00028816) + x110*x111*(-0.862231) + x108**2*0.00717402 + x103*x109*0.000164123 + x108*x111*(-0.134656) + x109*x112*(-0.0522137) + x109*0.141487 + x104*x108*0.0154947 + x104*x105*(-0.00406125) + x106*(-0.0119813) + x101*x106*1.94922e-05 + x106*x109*(-0.00031123) + x101*x105*(-0.000104463)
    x309_eq_two = 1.67402 + x103**(-1)*x111*64.7561 + x108*x109*(-0.00257077) + x103*x109**(-1)*0.000177662 + x101**(-1)*x102**(-1)*(-892.92) + x108*x109**(-1)*(-0.0215984) + x105*x109*0.00529672 + x107*x109*(-0.0818487) + x106**(-1)*x107**(-1)*8.77323 + x103**(-1)*x106*(-1.63549) + x101*x103*(-1.69885e-05) + x105**(-1)*x110**(-1)*(-4.57522) + x106**(-1)*x108*2.06638 + x104*x109**(-1)*0.00764918 + x107**(-1)*x112**(-1)*(-1.33627) + x107**(-1)*x111**(-1)*0.163089 + x109*x112**(-1)*(-0.250843) + x102**(-1)*x109*2.04587 + x110*x111**(-1)*0.144078 + x102*x112**(-1)*0.0875985 + x102**(-1)*x112*4.40152 + x101*x105*(-0.000362291) + x102**(-1)*x106**(-1)*(-267.832) + x101*x106*3.30086e-05 + x102*x110*(-0.0331608) + x107**(-1)*x110*0.687005 + x104*x105**(-1)*(-0.523747) + x104**(-1)*x107**(-1)*(-8.04198) + x101*x107*0.000894778 + x101**(-1)*x107**(-1)*26.1572

    c_list = [
        1, 1,  # x301, x302 always 1 for perfect
        1 if round(x303_eq_two) == 0 else 2,
        1 if round(x304_eq_two) == 0 else 2,
        1 if round(x305_eq_two) == 0 else 2,
        1 if round(x306_eq_two) == 0 else 2,
        1 if round(x307_eq_two) == 0 else 2,
        1 if round(x308_eq_two) == 0 else 2,
        1 if round(x309_eq_two) == 0 else 2,
    ]

    saati_value = sum(-c * coeff for c, coeff in zip(c_list, coeff_list))
    return round(saati_value, 15)


def saati_method():
    """Calculate AHP priority vectors."""
    first_row = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0]
    relationship_matrix = [first_row]

    for i in range(2, 10):
        relationship_matrix.append([v / i for v in first_row])

    gmean_list = [gmean(row) for row in relationship_matrix]
    total = sum(gmean_list)
    return [g / total for g in gmean_list]


def _random_solution():
    """Generate a single random treatment solution."""
    return [
        randint(*X201_RANGE) / 100,
        randint(*X202_RANGE) / 100,
        randint(*X203_RANGE),
        randint(*X204_RANGE),
        randint(*X205_RANGE),
        randint(*X206_RANGE),
        randint(*X207_RANGE),
        randint(*X208_RANGE),
        randint(*X209_RANGE),
    ]


def generate_first_population():
    """Generate initial random population of treatment solutions."""
    return [_random_solution() for _ in range(POPULATION_NUMBER)]


def calculate_saati(coeff_list, criterion_list):
    """Calculate Saati values for all solutions."""
    return [
        round(sum(-c * coeff for c, coeff in zip(c_list, coeff_list)), 15)
        for c_list in criterion_list
    ]


def get_discrepancies(perfect_value, saati_list):
    """Calculate discrepancies from perfect value."""
    return [perfect_value - s for s in saati_list]


def get_probabilities(disc_list):
    """Convert discrepancies to selection probabilities."""
    inv_list = [1 / d for d in disc_list]
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


def crossover(sol_list, temp_sol_list, father_list, mother_list):
    """Perform crossover between parent solutions."""
    for i in range(POPULATION_NUMBER):
        j, k = father_list[i], mother_list[i]
        for l in range(SOLUTION_SIZE):
            if l < (i % 8) + 1:
                sol_list[i][l] = temp_sol_list[j if (i % 16) < 8 else k][l]
            else:
                sol_list[i][l] = temp_sol_list[k if (i % 16) < 8 else j][l]
    return sol_list


def mutation(sol_list):
    """Apply mutation to population."""
    for i in range(1, POPULATION_NUMBER):
        sol_list[i] = _random_solution()
    return sol_list


def calculate_treatment(x_list):
    """Main genetic algorithm for finding optimal treatment strategies."""
    coeff_list = saati_method()
    perfect_value = calculate_perfect_value(x_list, coeff_list)

    treatment_list = []
    complication_list = []

    logger.info("Starting treatment calculation with %d runs", GA_RUNS)

    for run in range(GA_RUNS):
        sol_list = generate_first_population()
        mean_list = []

        for generation in range(MAX_GENERATIONS):
            criterion_list = calculate_criterions(x_list, sol_list)
            saati_list = calculate_saati(coeff_list, criterion_list)

            # Check for optimal solution
            for i in range(POPULATION_NUMBER):
                if saati_list[i] == perfect_value:
                    treatment_list.append(sol_list[i])
                    complication_list.append(criterion_list[i])
                    break
            else:
                # No optimal found, continue evolution
                disc_list = get_discrepancies(perfect_value, saati_list)
                mean_disc = sum(disc_list) / len(disc_list)
                mean_list.append(mean_disc)

                if len(mean_list) > 2 and mean_list[-2] == mean_list[-1]:
                    sol_list = mutation(copy.deepcopy(sol_list))
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
                continue
            break
        else:
            # Generation limit reached — take the best solution from final population
            disc_list = get_discrepancies(perfect_value, saati_list)
            best_idx = min(range(POPULATION_NUMBER), key=lambda i: disc_list[i])
            treatment_list.append(sol_list[best_idx])
            complication_list.append(criterion_list[best_idx])
            logger.warning("Run %d hit generation limit (%d)", run, MAX_GENERATIONS)

    logger.info("Found %d treatment solutions", len(treatment_list))

    return {
        "treatments": treatment_list[:5],
        "complications": complication_list[0] if complication_list else []
    }


def main():
    parser = argparse.ArgumentParser(description="FirstStage GA for treatment optimization")
    parser.add_argument("--input", type=str, required=True, help="JSON input with xList")
    args = parser.parse_args()

    try:
        data = json.loads(args.input)
        x_list = data["xList"]

        if len(x_list) != 12:
            raise ValueError(f"Expected 12 input values, got {len(x_list)}")

        result = calculate_treatment(x_list)
        print(json.dumps(result))

    except Exception as e:
        print(json.dumps({"error": str(e)}), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
