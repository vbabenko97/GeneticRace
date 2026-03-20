// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import geneticrace.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for executing Python genetic algorithm scripts.
 * Handles script extraction from JAR and JSON-based IPC.
 */
public class PythonService implements PythonServicePort {
    private static final Logger LOGGER = Logger.getLogger(PythonService.class.getName());
    private static final Gson GSON = new Gson();

    private static final String GA_CORE_MODULE = "ga_core.py";
    private static final String FIRST_STAGE_SCRIPT = "FirstStage.py";
    private static final String SECOND_STAGE_SCRIPT = "SecondStage.py";
    private static final int TIMEOUT_SECONDS = 120;

    private final Path scriptsDirectory;
    private final ProcessRunner processRunner;
    private boolean scriptsExtracted = false;

    public PythonService() {
        this(AppConfig.getPythonScriptsDirectory(), new ProcessRunner());
    }

    PythonService(Path scriptsDirectory, ProcessRunner processRunner) {
        this.scriptsDirectory = scriptsDirectory;
        this.processRunner = processRunner;
    }

    /**
     * Runs the FirstStage genetic algorithm.
     *
     * @param xList Input clinical values (12 elements)
     * @return GA result with treatment suggestions
     */
    public PythonServicePort.GaResult runFirstStage(List<Double> xList) throws IOException, InterruptedException {
        ensureScriptsExtracted();

        JsonObject input = new JsonObject();
        input.add("xList", GSON.toJsonTree(xList));

        return executeScript(FIRST_STAGE_SCRIPT, input.toString());
    }

    /**
     * Runs the SecondStage genetic algorithm.
     *
     * @param xList Input post-condition values (9 elements)
     * @return GA result with treatment suggestions
     */
    public PythonServicePort.GaResult runSecondStage(List<Double> xList) throws IOException, InterruptedException {
        ensureScriptsExtracted();

        JsonObject input = new JsonObject();
        input.add("xList", GSON.toJsonTree(xList));

        return executeScript(SECOND_STAGE_SCRIPT, input.toString());
    }

    private PythonServicePort.GaResult executeScript(String scriptName, String jsonInput)
            throws IOException, InterruptedException {

        Path scriptPath = scriptsDirectory.resolve(scriptName);

        if (!Files.exists(scriptPath)) {
            PythonServicePort.GaResult error = new PythonServicePort.GaResult();
            error.error = "Script not found: " + scriptPath;
            return error;
        }

        String pythonExe = AppConfig.getPythonExecutable();

        LOGGER.info("Executing script: " + scriptName);

        ProcessRunner.Result result = processRunner.run(
            List.of(pythonExe, scriptPath.toString(), "--input", jsonInput),
            TIMEOUT_SECONDS
        );

        if (result.timedOut()) {
            PythonServicePort.GaResult error = new PythonServicePort.GaResult();
            error.error = "Script timed out after " + TIMEOUT_SECONDS + " seconds";
            return error;
        }

        return parseOutput(result.stdout(), result.stderr(), result.exitCode());
    }

    /**
     * Parses script output into a GaResult. Package-private for testability.
     */
    PythonServicePort.GaResult parseOutput(String stdout, String stderr, int exitCode) {
        if (exitCode != 0) {
            LOGGER.warning("Script failed with exit code " + exitCode + ": " + stderr);
            PythonServicePort.GaResult error = new PythonServicePort.GaResult();
            error.error = "Script failed: " + stderr.trim();
            return error;
        }

        try {
            PythonServicePort.GaResult parsed = GSON.fromJson(stdout, PythonServicePort.GaResult.class);
            if (parsed == null) {
                PythonServicePort.GaResult error = new PythonServicePort.GaResult();
                error.error = "Script produced no output";
                return error;
            }
            return parsed;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse script output", e);
            PythonServicePort.GaResult error = new PythonServicePort.GaResult();
            error.error = "Failed to parse output: " + e.getMessage();
            return error;
        }
    }

    /**
     * Extracts Python scripts from JAR resources to the scripts directory.
     */
    private synchronized void ensureScriptsExtracted() throws IOException {
        if (scriptsExtracted) {
            return;
        }

        extractScript("/python/" + GA_CORE_MODULE, GA_CORE_MODULE);
        extractScript("/python/" + FIRST_STAGE_SCRIPT, FIRST_STAGE_SCRIPT);
        extractScript("/python/" + SECOND_STAGE_SCRIPT, SECOND_STAGE_SCRIPT);

        scriptsExtracted = true;
        LOGGER.info("Python scripts extracted to: " + scriptsDirectory);
    }

    private void extractScript(String resourcePath, String targetName) throws IOException {
        Path targetPath = scriptsDirectory.resolve(targetName);

        // Always overwrite to ensure latest version
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Make executable on Unix
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            targetPath.toFile().setExecutable(true);
        }
    }
}
