package combined.api.api

import combined.api.model.Employee
import io.smallrye.mutiny.Uni
import kotlin.collections.List

interface EmployeesApi {
  /** Get employee by ID */
  abstract fun getEmployee(employeeId: Int): Uni<Employee>

  /** List all employees */
  abstract fun listEmployees(
    /** Filter by active status */
    isActive: kotlin.Boolean?
  ): Uni<List<Employee>>
}