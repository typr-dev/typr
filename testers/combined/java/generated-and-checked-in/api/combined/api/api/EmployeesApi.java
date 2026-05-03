package combined.api.api;

import combined.api.model.Employee;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;

public interface EmployeesApi {
  /** Get employee by ID */
  Uni<Employee> getEmployee(Integer employeeId);

  /** List all employees */
  Uni<List<Employee>> listEmployees(
  
    /** Filter by active status */
    Optional<Boolean> isActive
  );
}