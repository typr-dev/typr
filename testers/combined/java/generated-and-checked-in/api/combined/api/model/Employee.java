package combined.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Optional;

public record Employee(
  /** Employee's email address (matches Email type) */
  @JsonProperty("email") String email,
  /** Employee business entity ID */
  @JsonProperty("employeeId") Integer employeeId,
  /** Employee's first name (matches FirstName type) */
  @JsonProperty("firstName") String firstName,
  /** Date employee was hired */
  @JsonProperty("hireDate") Optional<LocalDate> hireDate,
  /** Whether employee is currently active (matches IsActive type) */
  @JsonProperty("isActive") Boolean isActive,
  /** Whether employee is salaried vs hourly (matches SalariedFlag type) */
  @JsonProperty("isSalaried") Boolean isSalaried,
  /** Job title */
  @JsonProperty("jobTitle") Optional<String> jobTitle,
  /** Employee's last name (matches LastName type) */
  @JsonProperty("lastName") String lastName,
  /** Employee's middle name (matches MiddleName type) */
  @JsonProperty("middleName") Optional<String> middleName
) {
  /** Employee's email address (matches Email type) */
  public Employee withEmail(String email) {
    return new Employee(email, employeeId, firstName, hireDate, isActive, isSalaried, jobTitle, lastName, middleName);
  }

  /** Employee business entity ID */
  public Employee withEmployeeId(Integer employeeId) {
    return new Employee(email, employeeId, firstName, hireDate, isActive, isSalaried, jobTitle, lastName, middleName);
  }

  /** Employee's first name (matches FirstName type) */
  public Employee withFirstName(String firstName) {
    return new Employee(email, employeeId, firstName, hireDate, isActive, isSalaried, jobTitle, lastName, middleName);
  }

  /** Date employee was hired */
  public Employee withHireDate(Optional<LocalDate> hireDate) {
    return new Employee(email, employeeId, firstName, hireDate, isActive, isSalaried, jobTitle, lastName, middleName);
  }

  /** Whether employee is currently active (matches IsActive type) */
  public Employee withIsActive(Boolean isActive) {
    return new Employee(email, employeeId, firstName, hireDate, isActive, isSalaried, jobTitle, lastName, middleName);
  }

  /** Whether employee is salaried vs hourly (matches SalariedFlag type) */
  public Employee withIsSalaried(Boolean isSalaried) {
    return new Employee(email, employeeId, firstName, hireDate, isActive, isSalaried, jobTitle, lastName, middleName);
  }

  /** Job title */
  public Employee withJobTitle(Optional<String> jobTitle) {
    return new Employee(email, employeeId, firstName, hireDate, isActive, isSalaried, jobTitle, lastName, middleName);
  }

  /** Employee's last name (matches LastName type) */
  public Employee withLastName(String lastName) {
    return new Employee(email, employeeId, firstName, hireDate, isActive, isSalaried, jobTitle, lastName, middleName);
  }

  /** Employee's middle name (matches MiddleName type) */
  public Employee withMiddleName(Optional<String> middleName) {
    return new Employee(email, employeeId, firstName, hireDate, isActive, isSalaried, jobTitle, lastName, middleName);
  }
}