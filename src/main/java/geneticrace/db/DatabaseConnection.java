// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.db;

import geneticrace.config.AppConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple database connection utility.
 * Uses one connection per operation with try-with-resources pattern.
 * No connection pooling - SQLite doesn't benefit from it and it can cause contention.
 */
public class DatabaseConnection {
    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    
    /**
     * Creates a new database connection.
     * Caller is responsible for closing via try-with-resources.
     * 
     * Example:
     * <pre>
     * try (Connection conn = DatabaseConnection.getConnection()) {
     *     // use connection
     * }
     * </pre>
     */
    public static Connection getConnection() throws SQLException {
        Path dbPath = AppConfig.getDatabasePath();
        String url = JDBC_PREFIX + dbPath.toAbsolutePath();
        
        LOGGER.fine("Opening database connection: " + url);
        Connection conn = DriverManager.getConnection(url);
        
        // Enable foreign keys for SQLite
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        
        return conn;
    }
    
    /**
     * Tests if the database is accessible.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Database connection test failed", e);
            return false;
        }
    }
}
