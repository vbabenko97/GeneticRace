package geneticrace.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    private SessionManager session;

    @BeforeEach
    void setUp() {
        session = SessionManager.getInstance();
        session.logout(); // Reset state between tests
    }

    @Test
    void loginSetsAllFields() {
        session.login(1, "admin", "Адміністратор", "Admin");

        assertTrue(session.isLoggedIn());
        assertEquals(1, session.getCurrentUserId());
        assertEquals("admin", session.getCurrentUsername());
        assertEquals("Адміністратор", session.getCurrentUserRealName());
        assertEquals("Admin", session.getUserRole());
    }

    @Test
    void logoutClearsAllFields() {
        session.login(1, "admin", "Адміністратор", "Admin");
        session.logout();

        assertFalse(session.isLoggedIn());
        assertNull(session.getCurrentUserId());
        assertNull(session.getCurrentUsername());
        assertNull(session.getCurrentUserRealName());
        assertNull(session.getUserRole());
        assertNull(session.getCurrentPatientId());
    }

    @Test
    void isAdminReturnsTrueForAdminRole() {
        session.login(1, "admin", "Адміністратор", "Admin");
        assertTrue(session.isAdmin());
    }

    @Test
    void isAdminReturnsTrueForAdminRoleCaseInsensitive() {
        session.login(1, "admin", "Адміністратор", "admin");
        assertTrue(session.isAdmin());
    }

    @Test
    void isAdminReturnsFalseForDoctorRole() {
        session.login(2, "doctor", "Лікар", "Doctor");
        assertFalse(session.isAdmin());
    }

    @Test
    void selectPatientSetsPatientId() {
        session.login(1, "admin", "Адміністратор", "Admin");
        session.selectPatient(42);

        assertTrue(session.hasPatientSelected());
        assertEquals(42, session.getCurrentPatientId());
    }

    @Test
    void clearPatientSelectionResetsPatientId() {
        session.login(1, "admin", "Адміністратор", "Admin");
        session.selectPatient(42);
        session.clearPatientSelection();

        assertFalse(session.hasPatientSelected());
        assertNull(session.getCurrentPatientId());
    }

    @Test
    void isLoggedInReturnsFalseWhenNotLoggedIn() {
        assertFalse(session.isLoggedIn());
    }

    // Session timeout tests

    @Test
    void checkSessionValidReturnsTrueWhenSessionIsFresh() {
        session.login(1, "admin", "Адміністратор", "Admin");
        assertTrue(session.checkSessionValid());
    }

    @Test
    void checkSessionValidReturnsFalseWhenExpired() {
        session.login(1, "admin", "Адміністратор", "Admin");
        // Set activity time to past the timeout
        session.setLastActivityTime(
            System.currentTimeMillis() - SessionManager.SESSION_TIMEOUT_MS - 1000
        );

        assertFalse(session.checkSessionValid());
        // Should auto-logout
        assertFalse(session.isLoggedIn());
    }

    @Test
    void checkSessionValidReturnsFalseWhenNotLoggedIn() {
        assertFalse(session.checkSessionValid());
    }

    @Test
    void touchSessionRefreshesActivityTime() {
        session.login(1, "admin", "Адміністратор", "Admin");
        long before = session.getLastActivityTime();

        // Small delay to ensure time difference
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        session.touchSession();
        assertTrue(session.getLastActivityTime() >= before);
    }

    @Test
    void selectPatientTouchesSession() {
        session.login(1, "admin", "Адміністратор", "Admin");
        // Set activity time to long ago (but not expired)
        long oldTime = System.currentTimeMillis() - 1000;
        session.setLastActivityTime(oldTime);

        session.selectPatient(42);
        assertTrue(session.getLastActivityTime() > oldTime);
    }
}
