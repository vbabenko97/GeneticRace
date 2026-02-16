// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized configuration with layered loading:
 * 1. Classpath defaults (src/main/resources/config.properties)
 * 2. User config file (~/.geneticrace/config.properties)
 * 3. Environment variables (GENETICRACE_*)
 */
public class AppConfig {
    private static final Logger LOGGER = Logger.getLogger(AppConfig.class.getName());
    
    private static final String APP_DIR_NAME = ".geneticrace";
    private static final String CONFIG_FILE_NAME = "config.properties";
    private static final String DB_FILE_NAME = "HeartDefects.db";
    private static final String SAMPLE_DB_RESOURCE = "/data/HeartDefects_sample.db";
    private static final String SCRIPTS_DIR_NAME = "scripts";
    
    private static final Properties config = new Properties();
    private static boolean initialized = false;
    
    static {
        loadConfig();
    }
    
    private static void loadConfig() {
        // 1. Load classpath defaults
        try (InputStream is = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (is != null) {
                config.load(is);
                LOGGER.info("Loaded classpath config defaults");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not load classpath config", e);
        }
        
        // 2. Override with user config if exists
        Path userConfigPath = getAppDirectory().resolve(CONFIG_FILE_NAME);
        if (Files.exists(userConfigPath)) {
            try (InputStream is = Files.newInputStream(userConfigPath)) {
                config.load(is);
                LOGGER.info("Loaded user config from: " + userConfigPath);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not load user config", e);
            }
        }
        
        // 3. Override with environment variables
        overrideFromEnv("db.path", "GENETICRACE_DB_PATH");
        overrideFromEnv("python.executable", "GENETICRACE_PYTHON");
        
        initialized = true;
    }
    
    private static void overrideFromEnv(String configKey, String envVar) {
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isBlank()) {
            config.setProperty(configKey, envValue);
            LOGGER.info("Config " + configKey + " overridden by env var " + envVar);
        }
    }
    
    /**
     * Returns the application data directory (~/.geneticrace).
     * Creates the directory if it doesn't exist.
     */
    public static Path getAppDirectory() {
        Path appDir = Paths.get(System.getProperty("user.home"), APP_DIR_NAME);
        if (!Files.exists(appDir)) {
            try {
                Files.createDirectories(appDir);
                LOGGER.info("Created app directory: " + appDir);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not create app directory", e);
            }
        }
        return appDir;
    }
    
    /**
     * Returns the path to the SQLite database.
     * On first run, copies the sample database from resources.
     */
    public static Path getDatabasePath() {
        Path dbPath;
        String configuredPath = config.getProperty("db.path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            dbPath = Paths.get(resolveVariables(configuredPath));
        } else {
            dbPath = getAppDirectory().resolve(DB_FILE_NAME);
        }

        // Copy sample DB on first run (also handles 0-byte files from failed starts)
        if (!Files.exists(dbPath) || fileIsEmpty(dbPath)) {
            copySampleDatabase(dbPath);
        }

        return dbPath;
    }

    private static boolean fileIsEmpty(Path path) {
        try {
            return Files.size(path) == 0;
        } catch (IOException e) {
            return false;
        }
    }
    
    private static void copySampleDatabase(Path targetPath) {
        try (InputStream is = AppConfig.class.getResourceAsStream(SAMPLE_DB_RESOURCE)) {
            if (is != null) {
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Copied sample database to: " + targetPath);
            } else {
                LOGGER.warning("Sample database not found in resources: " + SAMPLE_DB_RESOURCE);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not copy sample database", e);
        }
    }
    
    /**
     * Returns the Python executable path.
     */
    public static String getPythonExecutable() {
        String configured = config.getProperty("python.executable", "python3");
        return resolveVariables(configured);
    }
    
    /**
     * Returns the directory where Python scripts are extracted.
     * Extracts from JAR resources on first run.
     */
    public static Path getPythonScriptsDirectory() {
        Path scriptsDir = getAppDirectory().resolve(SCRIPTS_DIR_NAME);
        if (!Files.exists(scriptsDir)) {
            try {
                Files.createDirectories(scriptsDir);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not create scripts directory", e);
            }
        }
        return scriptsDir;
    }
    
    /**
     * Resolves ${user.home} and similar variables in config values.
     */
    private static String resolveVariables(String value) {
        if (value == null) return null;
        return value
            .replace("${user.home}", System.getProperty("user.home"))
            .replace("~", System.getProperty("user.home"));
    }
    
    public static String getProperty(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }
}
