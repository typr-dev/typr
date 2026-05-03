/**
 * Example server implementation demonstrating unified TypeDefinitions across: - PostgreSQL database
 * (AdventureWorks - HR data) - MariaDB database (ordering system - customer data) - OpenAPI spec
 * (REST API)
 *
 * <p>All three sources share the same semantic types: - FirstName, LastName, MiddleName for names -
 * IsActive, IsSalaried for boolean flags
 */
package combined.server;

import combined.api.api.CustomersApiServer;
import combined.api.api.EmployeesApiServer;
import combined.api.api.ProductsApiServer;
import combined.api.model.Customer;
import combined.api.model.CustomerCreate;
import combined.api.model.CustomerUpdate;
import combined.api.model.Employee;
import combined.api.model.Product;
import combined.mariadb.customers.CustomersRepo;
import combined.postgres.humanresources.employee.EmployeeRepo;
import combined.postgres.person.person.PersonRepo;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;

/**
 * Server implementation that bridges between: - Generated database repositories (PostgreSQL +
 * MariaDB) - Generated OpenAPI server interfaces
 *
 * <p>The key insight is that TypeDefinitions creates semantic wrapper types that are UNIFIED across
 * all sources. The shared types in combined.shared package have database-specific instances
 * (pgType, mariaType) that handle the conversion from/to different underlying types.
 */
public class CombinedApiServer
    implements EmployeesApiServer, CustomersApiServer, ProductsApiServer {

  private final EmployeeRepo employeeRepo;
  private final PersonRepo personRepo;
  private final CustomersRepo customersRepo;

  public CombinedApiServer(
      EmployeeRepo employeeRepo, PersonRepo personRepo, CustomersRepo customersRepo) {
    this.employeeRepo = employeeRepo;
    this.personRepo = personRepo;
    this.customersRepo = customersRepo;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // EmployeesApiServer implementation - data from PostgreSQL
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  public Uni<List<Employee>> listEmployees(Optional<Boolean> isActive) {
    return Uni.createFrom()
        .item(
            () -> {
              // Query PostgreSQL using generated repositories
              // The Employee and Person tables are joined to get full employee info
              // TypeDefinitions matches:
              // - person.firstname -> combined.shared.FirstName
              // - person.lastname -> combined.shared.LastName
              // - person.middlename -> combined.postgres.userdefined.MiddleName
              // - employee.currentflag -> combined.shared.IsActive
              // - employee.salariedflag -> combined.postgres.userdefined.IsSalaried
              throw new UnsupportedOperationException(
                  "Implementation requires database connection");
            });
  }

  @Override
  public Uni<Employee> getEmployee(Integer employeeId) {
    return Uni.createFrom()
        .item(
            () -> {
              // Fetch from PostgreSQL and map to API model
              // The wrapper types (FirstName, LastName, etc.) bridge the gap
              // between database columns and API fields
              throw new UnsupportedOperationException(
                  "Implementation requires database connection");
            });
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // CustomersApiServer implementation - data from MariaDB
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  public Uni<List<Customer>> listCustomers(Optional<Boolean> isActive) {
    return Uni.createFrom()
        .item(
            () -> {
              // Query MariaDB using generated repositories
              // TypeDefinitions matches:
              // - customers.first_name -> combined.shared.FirstName
              // - customers.last_name -> combined.shared.LastName
              throw new UnsupportedOperationException(
                  "Implementation requires database connection");
            });
  }

  @Override
  public Uni<Customer> createCustomer(CustomerCreate customerCreate) {
    return Uni.createFrom()
        .item(
            () -> {
              // Insert into MariaDB
              String firstName = customerCreate.firstName();
              String lastName = customerCreate.lastName();
              throw new UnsupportedOperationException(
                  "Implementation requires database connection");
            });
  }

  @Override
  public Uni<Customer> getCustomer(Long customerId) {
    return Uni.createFrom()
        .item(
            () -> {
              throw new UnsupportedOperationException(
                  "Implementation requires database connection");
            });
  }

  @Override
  public Uni<Customer> updateCustomer(Long customerId, CustomerUpdate customerUpdate) {
    return Uni.createFrom()
        .item(
            () -> {
              Optional<String> firstName = customerUpdate.firstName();
              Optional<String> lastName = customerUpdate.lastName();
              Optional<Boolean> isActive = customerUpdate.isActive();
              throw new UnsupportedOperationException(
                  "Implementation requires database connection");
            });
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ProductsApiServer implementation - data from both databases
  // ═══════════════════════════════════════════════════════════════════════════

  @Override
  public Uni<List<Product>> listProducts(Optional<String> source, Optional<Boolean> isActive) {
    return Uni.createFrom()
        .item(
            () -> {
              // Aggregate products from both databases
              // This demonstrates how a single API can serve data from multiple sources
              // while maintaining type safety through shared TypeDefinitions
              throw new UnsupportedOperationException(
                  "Implementation requires database connection");
            });
  }

  private void exampleUsage() {
    // Database repositories and API models use consistent packages:
    // - combined.postgres.* for PostgreSQL tables
    // - combined.mariadb.* for MariaDB tables
    // - combined.api.* for OpenAPI models
  }
}
