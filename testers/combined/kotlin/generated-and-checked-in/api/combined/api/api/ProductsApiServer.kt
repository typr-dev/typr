package combined.api.api

import combined.api.model.Product
import io.smallrye.mutiny.Uni
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlin.collections.List

interface ProductsApiServer : ProductsApi {
  /** List all products from both databases */
  @GET
  @Path("")
  @Produces(value = [MediaType.APPLICATION_JSON])
  abstract override fun listProducts(
    /** Filter by data source */
    source: kotlin.String?,
    /** Filter by active status */
    isActive: kotlin.Boolean?
  ): Uni<List<Product>>
}