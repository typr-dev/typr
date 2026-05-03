package combined.api.api

import combined.api.model.Employee
import io.smallrye.mutiny.Uni
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlin.collections.List

interface EmployeesApiServer : EmployeesApi {
  /** Get employee by ID */
  @GET
  @Path("/{employeeId}")
  @Produces(value = [MediaType.APPLICATION_JSON])
  abstract override fun getEmployee(employeeId: Int): Uni<Employee>

  /** List all employees */
  @GET
  @Path("")
  @Produces(value = [MediaType.APPLICATION_JSON])
  abstract override fun listEmployees(
    /** Filter by active status */
    isActive: kotlin.Boolean?
  ): Uni<List<Employee>>
}