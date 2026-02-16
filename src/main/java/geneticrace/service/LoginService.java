// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.service;

import geneticrace.db.DatabaseConnection;
import geneticrace.model.User;
import geneticrace.session.SessionManager;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles user authentication with bcrypt password hashing.
 * Supports migration from legacy plain-text passwords.
 * Includes in-memory login rate limiting.
 */
public class LoginService {
    private static final Logger LOGGER = Logger.getLogger(LoginService.class.getName());

    private static final String SELECT_USER =
        "SELECT userID, username, realname, userRole, password_hash FROM Users WHERE username = ?";

    private static final String UPDATE_PASSWORD_HASH =
        "UPDATE Users SET password_hash = ? WHERE userID = ?";

    // For legacy migration: check against old plain-text column
    private static final String SELECT_LEGACY_PASSWORD =
        "SELECT password FROM Users WHERE username = ?";

    // Rate limiting: 5 attempts, 5-minute lockout
    static final int MAX_ATTEMPTS = 5;
    static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000;

    // Map: username -> [attemptCount, firstAttemptTimestamp]
    final Map<String, long[]> attemptTracker = new ConcurrentHashMap<>();

    /**
     * Result of authentication attempt.
     */
    public enum AuthResult {
        SUCCESS,
        INVALID_CREDENTIALS,
        LOCKED_OUT,
        NEEDS_PASSWORD_RESET,
        ERROR
    }

    /**
     * Authenticates user and establishes session.
     *
     * @return AuthResult indicating the outcome
     */
    public AuthResult authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return AuthResult.INVALID_CREDENTIALS;
        }

        if (isLockedOut(username)) {
            LOGGER.warning("Login blocked: account locked out - " + username);
            return AuthResult.LOCKED_OUT;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_USER)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    LOGGER.info("Login failed: user not found - " + username);
                    recordFailedAttempt(username);
                    return AuthResult.INVALID_CREDENTIALS;
                }

                User user = new User(
                    rs.getInt("userID"),
                    rs.getString("username"),
                    rs.getString("realname"),
                    rs.getString("userRole"),
                    rs.getString("password_hash")
                );

                // Check if user needs migration from legacy passwords
                if (user.needsPasswordMigration()) {
                    AuthResult result = handleLegacyLogin(user, password);
                    if (result == AuthResult.SUCCESS) {
                        clearAttempts(username);
                    } else {
                        recordFailedAttempt(username);
                    }
                    return result;
                }

                // Verify bcrypt password
                if (BCrypt.checkpw(password, user.getPasswordHash())) {
                    establishSession(user);
                    clearAttempts(username);
                    LOGGER.info("Login successful: " + username);
                    return AuthResult.SUCCESS;
                } else {
                    LOGGER.info("Login failed: invalid password - " + username);
                    recordFailedAttempt(username);
                    return AuthResult.INVALID_CREDENTIALS;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Login error", e);
            return AuthResult.ERROR;
        }
    }

    /**
     * Handles login for users with legacy plain-text passwords.
     * If password matches, migrates to bcrypt.
     */
    private AuthResult handleLegacyLogin(User user, String password) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_LEGACY_PASSWORD)) {

            pstmt.setString(1, user.getUsername());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String legacyPassword = rs.getString("password");
                    if (password.equals(legacyPassword)) {
                        migratePassword(user.getId(), password);
                        establishSession(user);
                        LOGGER.info("Login successful + migrated to bcrypt: " + user.getUsername());
                        return AuthResult.SUCCESS;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Legacy login check failed", e);
        }

        return AuthResult.INVALID_CREDENTIALS;
    }

    /**
     * Migrates a user's password to bcrypt hash.
     */
    private void migratePassword(int userId, String plainPassword) {
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_PASSWORD_HASH)) {

            pstmt.setString(1, hash);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();

            LOGGER.info("Password migrated to bcrypt for user ID: " + userId);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to migrate password", e);
        }
    }

    /**
     * Updates password for a user (for password reset flow).
     */
    public boolean updatePassword(int userId, String newPassword) {
        String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_PASSWORD_HASH)) {

            pstmt.setString(1, hash);
            pstmt.setInt(2, userId);
            int updated = pstmt.executeUpdate();

            LOGGER.info("Password updated for user ID: " + userId);
            return updated > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update password", e);
            return false;
        }
    }

    private void establishSession(User user) {
        SessionManager.getInstance().login(
            user.getId(),
            user.getUsername(),
            user.getRealName(),
            user.getRole()
        );
    }

    public void logout() {
        SessionManager.getInstance().logout();
    }

    // Rate limiting helpers (package-private for testability)

    boolean isLockedOut(String username) {
        long[] entry = attemptTracker.get(username);
        if (entry == null) return false;
        if (entry[0] < MAX_ATTEMPTS) return false;
        // Check if lockout has expired
        if (System.currentTimeMillis() - entry[1] > LOCKOUT_DURATION_MS) {
            attemptTracker.remove(username);
            return false;
        }
        return true;
    }

    void recordFailedAttempt(String username) {
        attemptTracker.compute(username, (key, entry) -> {
            if (entry == null) {
                return new long[]{1, System.currentTimeMillis()};
            }
            // Reset if previous lockout expired
            if (System.currentTimeMillis() - entry[1] > LOCKOUT_DURATION_MS) {
                return new long[]{1, System.currentTimeMillis()};
            }
            entry[0]++;
            return entry;
        });
    }

    void clearAttempts(String username) {
        attemptTracker.remove(username);
    }
}
