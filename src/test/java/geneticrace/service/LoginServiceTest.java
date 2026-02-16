package geneticrace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginServiceTest {

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService();
    }

    // Rate limiting tests

    @Test
    void isLockedOutReturnsFalseForUnknownUser() {
        assertFalse(loginService.isLockedOut("unknown"));
    }

    @Test
    void isLockedOutReturnsFalseAfterFewerThanMaxAttempts() {
        for (int i = 0; i < LoginService.MAX_ATTEMPTS - 1; i++) {
            loginService.recordFailedAttempt("testuser");
        }
        assertFalse(loginService.isLockedOut("testuser"));
    }

    @Test
    void isLockedOutReturnsTrueAfterMaxAttempts() {
        for (int i = 0; i < LoginService.MAX_ATTEMPTS; i++) {
            loginService.recordFailedAttempt("testuser");
        }
        assertTrue(loginService.isLockedOut("testuser"));
    }

    @Test
    void clearAttemptsResetsLockout() {
        for (int i = 0; i < LoginService.MAX_ATTEMPTS; i++) {
            loginService.recordFailedAttempt("testuser");
        }
        assertTrue(loginService.isLockedOut("testuser"));

        loginService.clearAttempts("testuser");
        assertFalse(loginService.isLockedOut("testuser"));
    }

    @Test
    void lockoutExpiresAfterDuration() {
        // Manually insert an expired entry
        loginService.attemptTracker.put("testuser", new long[]{
            LoginService.MAX_ATTEMPTS,
            System.currentTimeMillis() - LoginService.LOCKOUT_DURATION_MS - 1000
        });

        assertFalse(loginService.isLockedOut("testuser"));
    }

    @Test
    void recordFailedAttemptIncrementsCount() {
        loginService.recordFailedAttempt("testuser");
        loginService.recordFailedAttempt("testuser");
        loginService.recordFailedAttempt("testuser");

        long[] entry = loginService.attemptTracker.get("testuser");
        assertNotNull(entry);
        assertEquals(3, entry[0]);
    }

    @Test
    void recordFailedAttemptResetsAfterLockoutExpires() {
        // Insert expired entry
        loginService.attemptTracker.put("testuser", new long[]{
            LoginService.MAX_ATTEMPTS,
            System.currentTimeMillis() - LoginService.LOCKOUT_DURATION_MS - 1000
        });

        // Record a new attempt — should reset counter
        loginService.recordFailedAttempt("testuser");

        long[] entry = loginService.attemptTracker.get("testuser");
        assertNotNull(entry);
        assertEquals(1, entry[0]);
    }

    @Test
    void differentUsersTrackedIndependently() {
        for (int i = 0; i < LoginService.MAX_ATTEMPTS; i++) {
            loginService.recordFailedAttempt("user1");
        }

        assertTrue(loginService.isLockedOut("user1"));
        assertFalse(loginService.isLockedOut("user2"));
    }

    @Test
    void clearAttemptsForNonExistentUserIsNoOp() {
        // Should not throw
        loginService.clearAttempts("nonexistent");
    }
}
