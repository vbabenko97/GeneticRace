// Copyright © 2019, 2026. All rights reserved.
// Authors: Vitalii Babenko, Anastasiia Dydyk
// Refactored: 2026

package geneticrace.model;

/**
 * Patient data model.
 */
public class Patient {
    private final int id;
    private final String surname;
    private final String firstname;
    private final String middlename;
    private final String sex;
    private final String dateOfBirth;
    
    public Patient(int id, String surname, String firstname, String middlename, 
                   String sex, String dateOfBirth) {
        this.id = id;
        this.surname = surname;
        this.firstname = firstname;
        this.middlename = middlename;
        this.sex = sex;
        this.dateOfBirth = dateOfBirth;
    }
    
    public int getId() {
        return id;
    }
    
    public String getSurname() {
        return surname;
    }
    
    public String getFirstname() {
        return firstname;
    }
    
    public String getMiddlename() {
        return middlename;
    }
    
    public String getSex() {
        return sex;
    }
    
    public String getDateOfBirth() {
        return dateOfBirth;
    }
    
    public String getFullName() {
        return String.format("%s %s %s", surname, firstname, middlename);
    }
    
    @Override
    public String toString() {
        return getFullName();
    }
}
