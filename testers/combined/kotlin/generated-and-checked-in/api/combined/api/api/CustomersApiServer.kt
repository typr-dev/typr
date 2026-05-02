package combined.api.api

import combined.api.model.Customer
import combined.api.model.CustomerCreate
import combined.api.model.CustomerUpdate
import io.smallrye.mutiny.Uni
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlin.collections.List

interface CustomersApiServer : CustomersApi {
  /** Create a new customer */
  @POST
  @Path("")
  @Consumes(value = [MediaType.APPLICATION_JSON])
  @Produces(value = [MediaType.APPLICATION_JSON])
  abstract override fun createCustomer(body: CustomerCreate): Uni<Customer>

  /** Get customer by ID */
  @GET
  @Path("/{customerId}")
  @Produces(value = [MediaType.APPLICATION_JSON])
  abstract override fun getCustomer(customerId: kotlin.Long): Uni<Customer>

  /** List all customers */
  @GET
  @Path("")
  @Produces(value = [MediaType.APPLICATION_JSON])
  abstract override fun listCustomers(
    /** Filter by active status */
    isActive: kotlin.Boolean?
  ): Uni<List<Customer>>

  /** Update customer */
  @PUT
  @Path("/{customerId}")
  @Consumes(value = [MediaType.APPLICATION_JSON])
  @Produces(value = [MediaType.APPLICATION_JSON])
  abstract override fun updateCustomer(
    customerId: kotlin.Long,
    body: CustomerUpdate
  ): Uni<Customer>
}