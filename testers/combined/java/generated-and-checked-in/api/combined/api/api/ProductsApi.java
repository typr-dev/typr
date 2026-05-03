package combined.api.api;

import combined.api.model.Product;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;

public interface ProductsApi {
  /** List all products from both databases */
  Uni<List<Product>> listProducts(
    /** Filter by data source */
    Optional<String> source,
    /** Filter by active status */
    Optional<Boolean> isActive
  );
}