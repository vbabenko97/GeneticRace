// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.model;

/**
 * User data model.
 */
public record User(int id, String username, String realName, String role, String passwordHash) {

    public boolean needsPasswordMigration() {
        return passwordHash == null || passwordHash.isBlank() || !passwordHash.startsWith("$2a$");
    }

    public boolean isAdmin() {
        return "Admin".equalsIgnoreCase(role);
    }
}
