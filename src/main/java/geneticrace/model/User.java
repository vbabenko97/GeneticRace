// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.model;

/**
 * User data model.
 */
public class User {
    private final int id;
    private final String username;
    private final String realName;
    private final String role;
    private String passwordHash; // mutable for password reset flow
    
    public User(int id, String username, String realName, String role, String passwordHash) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.role = role;
        this.passwordHash = passwordHash;
    }
    
    public int getId() {
        return id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getRealName() {
        return realName;
    }
    
    public String getRole() {
        return role;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    public boolean needsPasswordMigration() {
        // NULL or empty hash means user needs to reset password
        return passwordHash == null || passwordHash.isBlank() || !passwordHash.startsWith("$2a$");
    }
    
    public boolean isAdmin() {
        return "Admin".equalsIgnoreCase(role);
    }
}
