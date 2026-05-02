package combined.api.api

import combined.api.model.Customer
import combined.api.model.CustomerCreate
import combined.api.model.CustomerUpdate
import io.smallrye.mutiny.Uni
import kotlin.collections.List

interface CustomersApi {
  /** Create a new customer */
  abstract fun createCustomer(body: CustomerCreate): Uni<Customer>

  /** Get customer by ID */
  abstract fun getCustomer(customerId: kotlin.Long): Uni<Customer>

  /** List all customers */
  abstract fun listCustomers(
    /** Filter by active status */
    isActive: kotlin.Boolean?
  ): Uni<List<Customer>>

  /** Update customer */
  abstract fun updateCustomer(
    customerId: kotlin.Long,
    body: CustomerUpdate
  ): Uni<Customer>
}