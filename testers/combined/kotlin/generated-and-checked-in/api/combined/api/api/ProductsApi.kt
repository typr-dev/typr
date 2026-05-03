package combined.api.api

import combined.api.model.Product
import io.smallrye.mutiny.Uni
import kotlin.collections.List

interface ProductsApi {
  /** List all products from both databases */
  abstract fun listProducts(
    /** Filter by data source */
    source: kotlin.String?,
    /** Filter by active status */
    isActive: kotlin.Boolean?
  ): Uni<List<Product>>
}