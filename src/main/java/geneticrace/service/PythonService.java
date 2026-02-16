// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import geneticrace.config.AppConfig;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for executing Python genetic algorithm scripts.
 * Handles script extraction from JAR and JSON-based IPC.
 */
public class PythonService implements PythonServicePort {
    private static final Logger LOGGER = Logger.getLogger(PythonService.class.getName());
    private static final Gson GSON = new Gson();
    
    private static final String FIRST_STAGE_SCRIPT = "FirstStage.py";
    private static final String SECOND_STAGE_SCRIPT = "SecondStage.py";
    private static final int TIMEOUT_SECONDS = 120;
    
    private final Path scriptsDirectory;
    private boolean scriptsExtracted = false;
    
    public PythonService() {
        this.scriptsDirectory = AppConfig.getPythonScriptsDirectory();
    }
    
    /**
     * Result of a Python script execution.
     */
    public static class GaResult {
        public List<List<Double>> treatments;
        public List<Integer> complications;
        public String error;
        
        public boolean isSuccess() {
            return error == null && treatments != null;
        }
    }
    
    /**
     * Runs the FirstStage genetic algorithm.
     * 
     * @param xList Input clinical values (12 elements)
     * @return GA result with treatment suggestions
     */
    public GaResult runFirstStage(List<Double> xList) throws IOException, InterruptedException {
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
    public GaResult runSecondStage(List<Double> xList) throws IOException, InterruptedException {
        ensureScriptsExtracted();
        
        JsonObject input = new JsonObject();
        input.add("xList", GSON.toJsonTree(xList));
        
        return executeScript(SECOND_STAGE_SCRIPT, input.toString());
    }
    
    private GaResult executeScript(String scriptName, String jsonInput) 
            throws IOException, InterruptedException {
        
        Path scriptPath = scriptsDirectory.resolve(scriptName);
        
        if (!Files.exists(scriptPath)) {
            GaResult error = new GaResult();
            error.error = "Script not found: " + scriptPath;
            return error;
        }
        
        String pythonExe = AppConfig.getPythonExecutable();
        
        ProcessBuilder pb = new ProcessBuilder(
            pythonExe,
            scriptPath.toString(),
            "--input", jsonInput
        );
        
        pb.redirectErrorStream(false);
        
        LOGGER.info("Executing: " + String.join(" ", pb.command()));
        
        Process process = pb.start();
        
        // Read stdout
        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line);
            }
        }
        
        // Read stderr
        StringBuilder stderr = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stderr.append(line).append("\n");
            }
        }
        
        boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        if (!completed) {
            process.destroyForcibly();
            GaResult error = new GaResult();
            error.error = "Script timed out after " + TIMEOUT_SECONDS + " seconds";
            return error;
        }
        
        int exitCode = process.exitValue();
        
        if (exitCode != 0) {
            LOGGER.warning("Script failed with exit code " + exitCode + ": " + stderr);
            GaResult error = new GaResult();
            error.error = "Script failed: " + stderr.toString().trim();
            return error;
        }
        
        // Parse JSON output
        try {
            return GSON.fromJson(stdout.toString(), GaResult.class);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse script output: " + stdout, e);
            GaResult error = new GaResult();
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
