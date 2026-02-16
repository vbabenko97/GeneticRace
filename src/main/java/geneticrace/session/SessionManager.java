// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.session;

import java.util.logging.Logger;

/**
 * In-memory session manager replacing file-based session storage.
 * Singleton pattern - thread-safe lazy initialization.
 *
 * Session timeout uses "timeout on next interaction" model:
 * the session expires after inactivity, but only checked when
 * {@link #checkSessionValid()} is called.
 */
public class SessionManager {
    private static final Logger LOGGER = Logger.getLogger(SessionManager.class.getName());
    private static volatile SessionManager instance;

    static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    private Integer currentUserId;
    private String currentUsername;
    private String currentUserRealName;
    private String userRole;
    private Integer currentPatientId;
    private long lastActivityTime;

    private SessionManager() {
        // Private constructor for singleton
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    // User session methods

    public void login(Integer userId, String username, String realName, String role) {
        this.currentUserId = userId;
        this.currentUsername = username;
        this.currentUserRealName = realName;
        this.userRole = role;
        this.lastActivityTime = System.currentTimeMillis();
    }

    public void logout() {
        this.currentUserId = null;
        this.currentUsername = null;
        this.currentUserRealName = null;
        this.userRole = null;
        this.currentPatientId = null;
        this.lastActivityTime = 0;
    }

    /**
     * Pure query: returns true if a user is logged in.
     * Does NOT check timeout — use {@link #checkSessionValid()} for that.
     */
    public boolean isLoggedIn() {
        return currentUserId != null;
    }

    /**
     * Checks whether the session is still valid (not expired).
     * If expired, auto-logouts and returns false.
     * Call this before sensitive operations.
     */
    public boolean checkSessionValid() {
        if (currentUserId == null) return false;
        if (System.currentTimeMillis() - lastActivityTime > SESSION_TIMEOUT_MS) {
            LOGGER.info("Session expired for user: " + currentUsername);
            logout();
            return false;
        }
        touchSession();
        return true;
    }

    /**
     * Refreshes the session activity timestamp.
     */
    public void touchSession() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public Integer getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public String getCurrentUserRealName() {
        return currentUserRealName;
    }

    public String getUserRole() {
        return userRole;
    }

    public boolean isAdmin() {
        return "Admin".equalsIgnoreCase(userRole);
    }

    // Patient selection methods

    public void selectPatient(Integer patientId) {
        this.currentPatientId = patientId;
        touchSession();
    }

    public void clearPatientSelection() {
        this.currentPatientId = null;
    }

    public Integer getCurrentPatientId() {
        return currentPatientId;
    }

    public boolean hasPatientSelected() {
        return currentPatientId != null;
    }

    // For testing: allow access to lastActivityTime
    long getLastActivityTime() {
        return lastActivityTime;
    }

    void setLastActivityTime(long time) {
        this.lastActivityTime = time;
    }
}
