package geneticrace.service;

import geneticrace.model.Patient;
import geneticrace.repository.PatientRepository;

import java.sql.SQLException;
import java.util.List;

/**
 * Service for patient listing and selection.
 * Keeps controllers off repositories.
 */
public class PatientService {

    private final PatientRepository repository;

    public PatientService() {
        this(new PatientRepository());
    }

    public PatientService(PatientRepository repository) {
        this.repository = repository;
    }

    public List<Patient> getAccessiblePatients() throws SQLException {
        return repository.getAccessiblePatients();
    }
}
