// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.model;

/**
 * Patient demographics.
 */
public record Patient(
    int id,
    String surname,
    String firstname,
    String middlename,
    String sex,
    String dateOfBirth
) {
    public String fullName() {
        return String.format("%s %s %s", surname, firstname, middlename);
    }

    @Override
    public String toString() {
        return fullName();
    }
}
