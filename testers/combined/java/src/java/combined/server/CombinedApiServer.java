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
              // The wrapper types ensure type safety:
              // customerCreate.firstName() returns FirstName (not String)
              combined.shared.FirstName firstName = customerCreate.firstName();
              combined.shared.LastName lastName = customerCreate.lastName();
              // ... create customer in database
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
              // Update MariaDB customer
              // Optional wrapper types for partial updates:
              // customerUpdate.firstName() returns Optional<FirstName>
              Optional<combined.shared.FirstName> firstName = customerUpdate.firstName();
              Optional<combined.shared.LastName> lastName = customerUpdate.lastName();
              Optional<combined.shared.IsActive> isActive = customerUpdate.isActive();
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

  /**
   * Demonstrates how unified shared types work across all sources.
   *
   * <p>With shared types enabled (sharedPkg in GenerateConfig), TypeDefinitions that match across
   * multiple sources are unified into a single type in combined.shared:
   *
   * <p>combined.shared.FirstName - Used by PostgreSQL (via Name domain) and MariaDB
   * combined.shared.LastName - Used by PostgreSQL (via Name domain) and MariaDB
   * combined.shared.IsActive - Used by PostgreSQL (via Flag domain) and MariaDB
   *
   * <p>Each shared type contains database-specific instances: - pgType: handles PostgreSQL domain
   * types (Name -> String -> FirstName) - mariaType: handles MariaDB types (varchar -> FirstName)
   *
   * <p>The underlying value is always the canonical JVM type (String for names, Boolean for flags),
   * making it trivial to use the same shared type across all sources.
   */
  private void sharedTypesExample(combined.shared.FirstName sharedFirstName) {
    // The shared type can be used with any database
    // The underlying value is the canonical type (String for text, Boolean for flags)
    String value = sharedFirstName.value();

    // Database adapters use the type's pgType/mariaType to handle conversions:
    // - PostgreSQL: Name.pgType.bimap() converts through the Name domain
    // - MariaDB: MariaTypes.text.bimap() handles direct String mapping
  }
}
