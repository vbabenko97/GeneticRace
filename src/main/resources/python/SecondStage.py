#!/usr/bin/env python3
# Copyright © 2019, 2026. All rights reserved.
# Authors: Vitalii Babenko, Anastasiia Dydyk
# Refactored: 2026 - JSON IPC, CLI arguments

"""
SecondStage genetic algorithm for finding conservative (medication) treatment strategies.
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

# Treatment parameter ranges for medication treatment (x401-x409)
X401_RANGE = (1, 4200)    # scaled by /100
X402_RANGE = (1, 81)
X403_RANGE = (0, 100)     # scaled by /100
X404_RANGE = (1, 25)
X405_RANGE = (1, 7)
X406_RANGE = (1, 4)
X407_RANGE = (1, 3)
X408_RANGE = (1, 2)
X409_RANGE = (1, 2)

# --- Logging setup ---

def _setup_logging():
    """Configure logging to a rotating file. Falls back to NullHandler."""
    logger = logging.getLogger("SecondStage")
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
    """Calculate condition criteria for late postoperative period."""
    x301, x302, x303, x304, x305, x306, x307, x308, x309 = x_list

    criterion_list = []
    for u_list in sol_list:
        x401, x402, x403, x404, x405, x406, x407, x408, x409 = u_list

        # GMDH classification models for late postoperative period
        x501_eq_two = 0.203898 + x301**(-1)*x405**(-1)*0.910959 + x404**(-1)*x405*0.427516 + x403*x406**(-1)*(-3.25314) + x301*x403*4.30283 + x404*x405**(-1)*(-0.0507695) + x303*x403*(-1.25164) + x304**(-1)*x405**(-1)*(-1.13018) + x401*x409**(-1)*0.013292 + x308*x309**(-1)*(-0.382442) + x306**(-1)*x404**(-1)*(-2.07729) + x401**(-1)*x409*1.05208 + x402**(-1)*x407**(-1)*(-19.254) + x404*x409*(-0.0224005) + x305**(-1)*x401**(-1)*(-10.406) + x305**(-1)*x404*0.101907 + x401**(-1)*x405**(-1)*3.49662 + x302**(-1)*x405**(-1)*1.15775 + x302*x303*0.17399 + x401**(-1)*x404**(-1)*(-7.90677) + x301*x407*(-0.122005) + x308*x402**(-1)*5.83819 + x301**(-1)*x404*(-0.0369662) + x401**(-1)*x407**(-1)*2.73641
        x502_eq_two = -0.471431 + x305**(-1)*x406**(-1)*1.09482 + x403*x408*(-3.06197) + x304*x403*2.72805 + x302**(-1)*x401**(-1)*2.92486 + x304**(-1)*x307**(-1)*1.72336 + x402**(-1)*x408**(-1)*(-5.41859) + x402*x404**(-1)*(-0.0445508) + x405*x407**(-1)*(-0.298849) + x308**(-1)*x405**(-1)*(-1.36086) + x301*x407**(-1)*1.09577 + x301*x405**(-1)*(-0.783258) + x402**(-1)*x404*(-0.493502) + x401*x404**(-1)*0.0744676 + x405**(-1)*x406*0.265279 + x406**(-1)*x407*0.501585 + x303**(-1)*x409*(-0.36206) + x405**(-1)*x407**(-1)*0.802625 + x304**(-1)*x406**(-1)*(-1.03788)
        x503_eq_two = 1.95339 + x408**(-1)*x409**(-1)*0.32464 + x308**(-1)*x404**(-1)*2.31705 + x407*x408*(-0.276162) + x407**(-1)*x408**(-1)*(-2.4394) + x304*x406*(-0.111947) + x404*x407**(-1)*0.0305552 + x405**(-1)*x408**(-1)*1.27569 + x401*x405**(-1)*(-0.0607239) + x401**(-1)*x409*(-1.0994) + x303**(-1)*x401*0.0271928
        x504_eq_two = 1.06655 + x403*x409**(-1)*3.59357 + x406**(-1)*x407**(-1)*(-1.19368) + x404*x405*(-0.00880017) + x308**(-1)*x407**(-1)*2.06499 + x401*x403*(-0.150814) + x402*x407**(-1)*0.011523 + x301**(-1)*x407*0.6211 + x401*x405**(-1)*0.0241819 + x401*x404**(-1)*(-0.0143991) + x301*x405*0.136269 + x405*x407*(-0.00884287) + x407*x408*(-0.211492) + x404*x407*0.00221758 + x303**(-1)*x307**(-1)*(-1.33289) + x301**(-1)*x308**(-1)*(-1.01545) + x405*x408**(-1)*(-0.206286) + x305**(-1)*x309*(-0.303684) + x402**(-1)*x409*(-1.84102) + x301*x401**(-1)*0.514069
        x505_eq_two = 0.091119 + x308**(-1)*x406**(-1)*1.15482 + x304*x309*(-0.234552) + x303*x305**(-1)*(-0.468262) + x401*x402*(-0.00120581) + x407**(-1)*x408**(-1)*2.20372 + x407*x408*0.203365 + x301**(-1)*x408**(-1)*(-0.224786) + x403*x407**(-1)*1.75401 + x402*x404**(-1)*0.0515831 + x401*x408**(-1)*0.0186275 + x306**(-1)*x403*(-3.10445) + x404*x408*0.0266439 + x302*x407*(-0.143367) + x302**(-1)*x407**(-1)*(-0.655295) + x403*x409**(-1)*3.61478 + x403*x408**(-1)*(-4.82115) + x401**(-1)*x404*(-0.222182) + x401**(-1)*x405**(-1)*1.68566 + x403*x404*0.154106 + x404**(-1)*x406*1.94052 + x401*x407*0.0123596 + x404**(-1)*x407**(-1)*(-1.7251) + x304**(-1)*x406*(-0.303317) + x306*x404**(-1)*(-0.768984) + x303**(-1)*x402**(-1)*6.71103
        x506_eq_two = 1.22334 + x403*x406*0.64041 + x402**(-1)*x404*1.92831 + x305*x307*0.186573 + x403*x404*(-0.204982) + x401**(-1)*x408**(-1)*(-12.1129) + x304**(-1)*x401**(-1)*7.87555 + x309**(-1)*x408*(-2.69499) + x302*x304**(-1)*0.874263 + x401**(-1)*x402*0.113644 + x303**(-1)*x402**(-1)*58.3697 + x303**(-1)*x401**(-1)*(-8.91198) + x309*x402**(-1)*(-57.1008) + x301**(-1)*x402**(-1)*12.9686 + x402**(-1)*x409*11.804 + x408*x409**(-1)*1.0522 + x308**(-1)*x404*(-0.0536574) + x305**(-1)*x403*2.98424 + x302*x409**(-1)*(-1.11811) + x304*x402**(-1)*25.7593 + x405**(-1)*x407*0.086348 + x401**(-1)*x409**(-1)*5.31789 + x401*x409*(-0.00743753)
        x507_eq_two = 2.70189 + x301**2*0.387945 + x305*x308*(-0.0851309) + x306*x307*(-0.232534) + x302*x409*0.0657112 + x303*x408*0.208682 + x403*x407*(-2.58931) + x403*x405*2.53922 + x402*x403*(-0.205441) + x301*x408*(-0.624969) + x401*x404*(-0.00361108) + x301*x401*0.014996 + x402*x404*0.000855651 + x302*x401*(-0.0180873) + x403*x409*3.3802 + x302*x403*(-4.60527) + x304*x403*8.3029 + x303*x304*(-0.192993) + x407*x408*0.22353 + x407*x409*(-0.159675) + x302*x309*(-0.118495) + x301*x403*(-7.86307) + x406*x407*(-0.0519393) + x403**2*(-10.6638) + x403*x408*3.47469 + x403*x406*(-1.3094) + x401**2*0.000791461
        x508_eq_two = 0.491547 + x302*x408*0.293381 + x402*x407*(-0.00138491) + x403*x404*(-0.10257) + x405*x406*0.102928 + x301*x304*0.150368 + x401*x408*(-0.0278252) + x404*x405*(-0.00818883) + x404*x409*0.0115062 + x308*x406*(-0.45356) + x304*x406*0.439602 + x304*x306*(-0.153749) + x305*x401*0.040654 + x401*x407*(-0.0169291) + x401*x402*(-0.000416154) + x304*x305*(-0.142557)
        x509_eq_two = 2.24957 + x307*x308*(-0.184216) + x401**(-1)*x404*0.149113 + x302*x404*(-0.0107084) + x402**(-1)*x407**(-1)*(-8.19724) + x403*x405**(-1)*(-6.10407) + x403*x409*3.4668 + x408**(-1)*x409*(-0.35094) + x301*x401*0.0394856 + x306*x401*(-0.0306855) + x302*x303**(-1)*(-0.230414) + x405*x407*(-0.0215096) + x301*x408*(-0.109036) + x306**(-1)*x403*(-2.45236) + x304**(-1)*x306**(-1)*0.715238

        c_list = [
            1 if round(x501_eq_two) == 0 else 2,
            1 if round(x502_eq_two) == 0 else 2,
            1 if round(x503_eq_two) == 0 else 2,
            1 if round(x504_eq_two) == 0 else 2,
            1 if round(x505_eq_two) == 0 else 2,
            1 if round(x506_eq_two) == 0 else 2,
            1 if round(x507_eq_two) == 0 else 2,
            1 if round(x508_eq_two) == 0 else 2,
            1 if round(x509_eq_two) == 0 else 2,
        ]
        criterion_list.append(c_list)

    return criterion_list


def calculate_perfect_value(x_list, coeff_list):
    """Calculate the ideal Saati value for the given condition."""
    x301, x302, _, _, _, _, x307, _, _ = x_list

    x509 = round(1.70408 + x302 * x307 * (-0.238892) + x301**2 * 0.170947)
    c_list = [1, 1, 1, 1, 1, 1, 1, 1, x509]

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
    """Generate a single random medication solution."""
    return [
        randint(*X401_RANGE) / 100,
        randint(*X402_RANGE),
        randint(*X403_RANGE) / 100,
        randint(*X404_RANGE),
        randint(*X405_RANGE),
        randint(*X406_RANGE),
        randint(*X407_RANGE),
        randint(*X408_RANGE),
        randint(*X409_RANGE),
    ]


def generate_first_population():
    """Generate initial random population of medication solutions."""
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
    """Main genetic algorithm for finding optimal medication strategies."""
    coeff_list = saati_method()
    perfect_value = calculate_perfect_value(x_list, coeff_list)

    treatment_list = []
    complication_list = []

    logger.info("Starting medication calculation with %d runs", GA_RUNS)

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

    logger.info("Found %d medication solutions", len(treatment_list))

    return {
        "treatments": treatment_list[:5],
        "complications": complication_list[0] if complication_list else []
    }


def main():
    parser = argparse.ArgumentParser(description="SecondStage GA for medication optimization")
    parser.add_argument("--input", type=str, required=True, help="JSON input with xList")
    args = parser.parse_args()

    try:
        data = json.loads(args.input)
        x_list = data["xList"]

        if len(x_list) != 9:
            raise ValueError(f"Expected 9 input values, got {len(x_list)}")

        result = calculate_treatment(x_list)
        print(json.dumps(result))

    except Exception as e:
        print(json.dumps({"error": str(e)}), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
