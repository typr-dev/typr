package combined.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class Employee(
  /** Employee's email address (matches Email type) */
  @field:JsonProperty("email") val email: kotlin.String,
  /** Employee business entity ID */
  @field:JsonProperty("employeeId") val employeeId: Int,
  /** Employee's first name (matches FirstName type) */
  @field:JsonProperty("firstName") val firstName: kotlin.String,
  /** Date employee was hired */
  @field:JsonProperty("hireDate") val hireDate: LocalDate?,
  /** Whether employee is currently active (matches IsActive type) */
  @field:JsonProperty("isActive") val isActive: kotlin.Boolean,
  /** Whether employee is salaried vs hourly (matches SalariedFlag type) */
  @field:JsonProperty("isSalaried") val isSalaried: kotlin.Boolean,
  /** Job title */
  @field:JsonProperty("jobTitle") val jobTitle: kotlin.String?,
  /** Employee's last name (matches LastName type) */
  @field:JsonProperty("lastName") val lastName: kotlin.String,
  /** Employee's middle name (matches MiddleName type) */
  @field:JsonProperty("middleName") val middleName: kotlin.String?
)