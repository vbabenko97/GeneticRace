# GeneticRace

Desktop decision-support prototype (JavaFX) for optimizing treatment strategies for patients with congenital heart defects, using GMDH, AHP, and Genetic Algorithms.

[![CI](https://github.com/vbabenko97/GeneticRace/actions/workflows/ci.yml/badge.svg)](https://github.com/vbabenko97/GeneticRace/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](license)

> **Disclaimer:** This is a research prototype developed as a bachelor thesis at Igor Sikorsky Kyiv Polytechnic Institute. It is **not** a medical device and is **not** intended for clinical decision-making. Use for educational and research purposes only.

## About

The system finds optimal treatment strategies for patients with congenital heart defects with a single ventricle. Patients are treated in two stages:

1. **First Stage (Operational)** — surgical interventions
2. **Second Stage (Conservative)** — medication therapy to improve post-operative condition

The algorithm combines three methods to find and evaluate treatment strategies that optimize patient outcomes.

### Group Method of Data Handling (GMDH)

Classification models for predicting postoperative complications (e.g. pulmonary embolism, stroke, thrombosis). All metrics below are on a held-out test set (single 85/15 split); see the thesis for per-model breakdowns and dataset details.

- Built in [GMDH Shell DS](https://gmdhsoftware.com/docs/)
- **18 models** total (9 early + 9 late postoperative condition variables)
- Test set accuracy: **75.9% – 96.6%**
- Sensitivity: **0.814 – 1.0**, Specificity: **0.73 – 1.0**

### Analytic Hierarchy Process (AHP)

- Optimizes **9 early** and **9 late** postoperative condition criteria
- Weights derived from pairwise comparison matrices (consistency ratio CR < 0.1)

### Genetic Algorithm (GA)

- Finds treatment strategies in approximately **20 seconds** (~700 iterations) on a 2019-era laptop
- Python scripts communicate with Java via JSON over CLI arguments / stdout

## Tech Stack

- **Java 17** — application core, JavaFX desktop UI
- **Maven** — build system
- **SQLite** — patient and treatment data storage
- **Python 3** — GA scripts (FirstStage.py, SecondStage.py)
- **JUnit 5 + Mockito** — testing
- **JaCoCo** — code coverage
- **GitHub Actions** — CI

## Project Structure

```
src/main/java/geneticrace/
  config/         App configuration (paths, Python executable)
  controller/     JavaFX controllers (Login, MainMenu, PatientChooser, Treatment, PasswordReset)
  db/             Database connection management
  model/          Domain models (Patient, User, FirstStageData, SecondStageData — all Java records)
  repository/     Data access layer (PatientRepository, PatientDataPort interface)
  service/        Business logic (TreatmentService, LoginService, PythonService)
  session/        In-memory session management

src/main/resources/
  fxml/           JavaFX view definitions
  python/         GA scripts
  data/           Sample database

src/test/java/geneticrace/
  repository/     PatientRepository tests (against sample DB)
  service/        TreatmentService unit + integration tests, LoginService tests
  session/        SessionManager tests
```

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Python 3** with standard library only (for running GA scripts at runtime; not needed for tests)

## Quickstart

1. Clone and build:
   ```bash
   git clone https://github.com/vbabenko97/GeneticRace.git
   cd GeneticRace
   mvn compile
   ```

2. Run the application:
   ```bash
   mvn javafx:run
   ```

3. Log in with a sample account:

   | Username  | Role    |
   |-----------|---------|
   | `admin`   | Admin   |
   | `doctor1` | Doctor  |
   | `doctor2` | Doctor  |

   Default passwords match the username (legacy plain-text; auto-migrated to bcrypt on first login).

On first launch, the app creates `~/.geneticrace/` and copies `HeartDefects_sample.db` to `~/.geneticrace/HeartDefects.db`. Python scripts are extracted to `~/.geneticrace/scripts/`.

> **Note:** `mvn package` builds a JAR, but JavaFX apps require module path setup to run standalone. Use `mvn javafx:run` for development. For distribution, see the `native` Maven profile which uses jpackage.

### Configuration

Defaults are in `src/main/resources/config.properties`. Override via `~/.geneticrace/config.properties` or environment variables:

| Setting             | Default     | Env variable           |
|---------------------|-------------|------------------------|
| `db.path`           | `~/.geneticrace/HeartDefects.db` | `GENETICRACE_DB_PATH` |
| `python.executable` | `python3`   | `GENETICRACE_PYTHON`   |

On Windows, set `python.executable=python` (or the full path to your Python 3 installation).

### Running Tests

```bash
mvn test
```

Tests mock the Python layer entirely, so Python is not required. The repository tests run against the sample DB copied from classpath resources.

## Python IPC Protocol

Java invokes Python scripts as subprocesses, passing JSON via `--input` CLI argument and reading JSON from stdout. Payloads are intentionally small (< 1 KB) to avoid OS argument length limits.

**Request** (Java -> Python via `--input`):
```json
{"xList": [120.0, 3.5, 4.2, 2.0, 1.0, 1.8, 2.1, 3.0, 4.5, 1.0, 2.0, 1.0]}
```

`xList` contains clinical values: 12 for FirstStage, 9 for SecondStage. Categorical values are pre-encoded (e.g. "Yes"=1.0, "No"=2.0 for FirstStage; reversed for SecondStage to match training data).

**Success response** (Python stdout, exit code 0):
```json
{
  "treatments": [[1.23, 4.56, ...], [7.89, 0.12, ...]],
  "complications": [1, 2]
}
```

`treatments` is a list of up to 5 treatment strategy vectors (9 values each). `complications` is the predicted complication vector (1 = complication absent, 2 = present).

**Error response** (Python stderr, exit code 1):
```json
{"error": "Expected 12 input values, got 9"}
```

Java reads stderr as a raw error message and wraps it in a `TreatmentError.SCRIPT_FAILED` result.

## License

Apache License 2.0. See [license](license) for details.

## Sources

### Primary

1. Saaty, Thomas L. *Decision Making for Leaders: The Analytic Hierarchy Process for Decisions in a Complex World.* RWS Publications, 1990.
2. Goldberg, David E. *Genetic Algorithms in Search, Optimization & Machine Learning.* Addison-Wesley, 1989.
3. [Group Method of Data Handling — GMDH](http://www.gmdh.net/)

### Supplementary

4. [Manoj, Mathew. Analytic Hierarchy Process (AHP). 2018.](https://www.youtube.com/watch?v=J4T70o8gjlk)
5. [Mallawaarachchi, Vijini. Introduction to Genetic Algorithms. 2017.](https://towardsdatascience.com/introduction-to-genetic-algorithms-including-example-code-e396e98d8bf3)
6. [What is a Genetic Algorithm. 2015.](https://www.youtube.com/watch?v=1i8muvzZkPw)
