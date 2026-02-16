# GeneticRace

Treatment strategy optimization for patients with congenital heart defects using GMDH, AHP, and Genetic Algorithms.

[![CI](https://github.com/vbabenko97/GeneticRace/actions/workflows/ci.yml/badge.svg)](https://github.com/vbabenko97/GeneticRace/actions/workflows/ci.yml)

## About

Bachelor thesis project for Igor Sikorsky Kyiv Polytechnic Institute.

The system finds optimal treatment strategies for patients with congenital heart defects with a single ventricle. Patients are treated in two stages:

1. **First Stage (Operational)** — surgical interventions
2. **Second Stage (Conservative)** — medication therapy to improve post-operative condition

The algorithm combines three methods to find and evaluate treatment strategies that optimize patient outcomes.

### Group Method of Data Handling (GMDH)

- Classification models built in [GMDH Shell DS](https://gmdhsoftware.com/docs/)
- Data split: **85%** training / **15%** test
- **18 models** total
- Test accuracy: **75.9% – 96.6%**
- Sensitivity: **0.814 – 1.0**, Specificity: **0.73 – 1.0**

### Analytic Hierarchy Process (AHP)

- Optimizes **9 early** and **9 late** postoperative condition criteria
- Weights derived from pairwise comparison matrices

### Genetic Algorithm (GA)

- Finds treatment strategies in ~**20 seconds** (~700 iterations)
- Python scripts communicate with Java via JSON IPC

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
  model/          Domain records (Patient, User, FirstStageData, SecondStageData)
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
- **Python 3** (for running GA scripts at runtime; not needed for tests)

## Build & Run

```bash
# Compile
mvn compile

# Run tests (55 tests)
mvn test

# Run the application
mvn javafx:run

# Build JAR
mvn package
```

## Sources

1. [Group Method of Data Handling](http://www.gmdh.net/)
2. [Manoj, Mathew. Analytic Hierarchy Process (AHP). 2018.](https://www.youtube.com/watch?v=J4T70o8gjlk)
3. Saaty, Thomas L. *Decision Making for Leaders: The Analytic Hierarchy Process for Decisions in a Complex World.* RWS Publications, 1990.
4. [Mallawaarachchi, Vijini. Introduction to Genetic Algorithms. 2017.](https://towardsdatascience.com/introduction-to-genetic-algorithms-including-example-code-e396e98d8bf3)
5. [What is a Genetic Algorithm. 2015.](https://www.youtube.com/watch?v=1i8muvzZkPw)
6. Goldberg, David E. *Genetic Algorithms in Search, Optimization & Machine Learning.* Addison-Wesley, 1989.
