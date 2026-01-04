package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.data.Uint1;
import dev.typr.foundations.data.Uint2;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.Test;
import org.mariadb.jdbc.type.Point;
import testdb.brands.*;
import testdb.customer_status.CustomerStatusId;
import testdb.customers.*;
import testdb.customtypes.Defaulted.Provided;
import testdb.customtypes.Defaulted.UseDefault;
import testdb.inventory.*;
import testdb.order_items.*;
import testdb.orders.*;
import testdb.products.*;
import testdb.reviews.*;
import testdb.v_customer_summary.*;
import testdb.v_product_catalog.*;
import testdb.warehouses.*;

/**
 * Tests for views - read-only operations using generated view repositories. Note: The database has
 * seeded customer_status data including 'active', 'pending', 'suspended', 'closed'. Tests use these
 * existing statuses rather than inserting new ones.
 */
public class ViewTest {
  private final VCustomerSummaryViewRepoImpl customerSummaryRepo =
      new VCustomerSummaryViewRepoImpl();
  private final VProductCatalogViewRepoImpl productCatalogRepo = new VProductCatalogViewRepoImpl();
  private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
  private final ProductsRepoImpl productsRepo = new ProductsRepoImpl();
  private final BrandsRepoImpl brandsRepo = new BrandsRepoImpl();
  private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();
  private final OrderItemsRepoImpl orderItemsRepo = new OrderItemsRepoImpl();
  private final ReviewsRepoImpl reviewsRepo = new ReviewsRepoImpl();
  private final WarehousesRepoImpl warehousesRepo = new WarehousesRepoImpl();
  private final InventoryRepoImpl inventoryRepo = new InventoryRepoImpl();

  @Test
  public void testCustomerSummaryViewSelectAll() {
    MariaDbTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRowUnsaved("view1@example.com", "hash1".getBytes(), "View", "Customer1"),
              c);
          customersRepo.insert(
              new CustomersRowUnsaved("view2@example.com", "hash2".getBytes(), "View", "Customer2"),
              c);

          var summaries = customerSummaryRepo.selectAll(c);
          assertEquals(2, summaries.size());
        });
  }

  @Test
  public void testCustomerSummaryViewFields() {
    MariaDbTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRowUnsaved("summary@example.com", "hash".getBytes(), "Summary", "Test")
                  .withStatus(new Provided<>(new CustomerStatusId("suspended")))
                  .withTier(new Provided<>("gold")),
              c);

          var summaries = customerSummaryRepo.selectAll(c);
          assertEquals(1, summaries.size());

          var summary = summaries.get(0);
          assertEquals("summary@example.com", summary.email());
          assertEquals(Optional.of("Summary Test"), summary.fullName());
          assertEquals("gold", summary.tier());
          assertEquals("suspended", summary.status().value());
          assertEquals(0L, summary.totalOrders().longValue());
          assertEquals(new BigDecimal("0.0000"), summary.lifetimeValue());
        });
  }

  @Test
  public void testCustomerSummaryViewWithOrders() {
    MariaDbTestHelper.run(
        c -> {
          var customer =
              customersRepo.insert(
                  new CustomersRowUnsaved(
                      "orders@example.com", "hash".getBytes(), "With", "Orders"),
                  c);

          var brand = brandsRepo.insert(new BrandsRowUnsaved("TestBrand", "test-brand"), c);
          var product =
              productsRepo.insert(
                  new ProductsRowUnsaved("SKU-ORDER", "Order Product", new BigDecimal("100.00")),
                  c);

          var order =
              ordersRepo.insert(
                  new OrdersRowUnsaved(
                      "ORD-001",
                      customer.customerId(),
                      new BigDecimal("100.00"),
                      new BigDecimal("110.00")),
                  c);

          orderItemsRepo.insert(
              new OrderItemsRowUnsaved(
                  order.orderId(),
                  product.productId(),
                  "SKU-ORDER",
                  "Order Product",
                  Uint2.of(1),
                  new BigDecimal("100.00"),
                  new BigDecimal("100.00"),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>()),
              c);

          var summaries = customerSummaryRepo.selectAll(c);
          assertEquals(1, summaries.size());
          assertTrue(summaries.get(0).totalOrders() >= 1);
        });
  }

  @Test
  public void testProductCatalogViewSelectAll() {
    MariaDbTestHelper.run(
        c -> {
          var brand = brandsRepo.insert(new BrandsRowUnsaved("CatalogBrand", "catalog-brand"), c);

          productsRepo.insert(
              new ProductsRowUnsaved(
                  "CAT-SKU-001",
                  "Catalog Product 1",
                  new BigDecimal("25.00"),
                  new Provided<>(Optional.of(brand.brandId())),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new Provided<>("active"),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>()),
              c);
          productsRepo.insert(
              new ProductsRowUnsaved(
                  "CAT-SKU-002",
                  "Catalog Product 2",
                  new BigDecimal("50.00"),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new Provided<>("active"),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>()),
              c);

          var catalog = productCatalogRepo.selectAll(c);
          assertEquals(2, catalog.size());
        });
  }

  @Test
  public void testProductCatalogViewFields() {
    MariaDbTestHelper.run(
        c -> {
          var brand = brandsRepo.insert(new BrandsRowUnsaved("FieldsBrand", "fields-brand"), c);

          productsRepo.insert(
              new ProductsRowUnsaved(
                  "FIELDS-SKU",
                  "Fields Product",
                  new BigDecimal("99.99"),
                  new Provided<>(Optional.of(brand.brandId())),
                  new Provided<>(Optional.of("Short description")),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new Provided<>("active"),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>()),
              c);

          var catalog = productCatalogRepo.selectAll(c);
          assertEquals(1, catalog.size());

          var row = catalog.get(0);
          assertEquals("FIELDS-SKU", row.sku());
          assertEquals("Fields Product", row.name());
          assertEquals(0, new BigDecimal("99.99").compareTo(row.basePrice()));
          assertEquals("active", row.status());
          assertEquals(Optional.of("FieldsBrand"), row.brandName());
          assertEquals(Optional.of("Short description"), row.shortDescription());
          assertEquals(0L, row.reviewCount().longValue());
        });
  }

  @Test
  public void testProductCatalogViewWithInventory() {
    MariaDbTestHelper.run(
        c -> {
          var warehouse =
              warehousesRepo.insert(
                  new WarehousesRowUnsaved(
                      "WH001", "Main Warehouse", "123 Warehouse St", new Point(0.0, 0.0)),
                  c);

          var product =
              productsRepo.insert(
                  new ProductsRowUnsaved(
                      "INV-SKU",
                      "Inventory Product",
                      new BigDecimal("75.00"),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new Provided<>("active"),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>()),
                  c);

          inventoryRepo.insert(
              new InventoryRowUnsaved(
                  product.productId(),
                  warehouse.warehouseId(),
                  new Provided<>(100),
                  new Provided<>(10),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>()),
              c);

          var catalog = productCatalogRepo.selectAll(c);
          assertEquals(1, catalog.size());
          assertEquals(new BigDecimal("90"), catalog.get(0).availableQuantity());
        });
  }

  @Test
  public void testProductCatalogViewWithReviews() {
    MariaDbTestHelper.run(
        c -> {
          var customer =
              customersRepo.insert(
                  new CustomersRowUnsaved(
                      "reviewer@example.com", "hash".getBytes(), "Reviewer", "User"),
                  c);

          var product =
              productsRepo.insert(
                  new ProductsRowUnsaved(
                      "REVIEW-SKU",
                      "Reviewed Product",
                      new BigDecimal("150.00"),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new Provided<>("active"),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>(),
                      new UseDefault<>()),
                  c);

          reviewsRepo.insert(
              new ReviewsRowUnsaved(
                  product.productId(),
                  customer.customerId(),
                  Uint1.of(5),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new Provided<>(true),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>()),
              c);
          reviewsRepo.insert(
              new ReviewsRowUnsaved(
                  product.productId(),
                  customer.customerId(),
                  Uint1.of(4),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new Provided<>(true),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>()),
              c);

          var catalog = productCatalogRepo.selectAll(c);
          assertEquals(1, catalog.size());
          assertEquals(2L, catalog.get(0).reviewCount().longValue());
          assertTrue(catalog.get(0).avgRating().compareTo(new BigDecimal("4")) >= 0);
        });
  }

  @Test
  public void testViewDSLSelect() {
    MariaDbTestHelper.run(
        c -> {
          customersRepo.insert(
              new CustomersRowUnsaved("dsl1@example.com", "hash1".getBytes(), "DSL", "Bronze")
                  .withTier(new Provided<>("bronze")),
              c);
          customersRepo.insert(
              new CustomersRowUnsaved("dsl2@example.com", "hash2".getBytes(), "DSL", "Gold")
                  .withTier(new Provided<>("gold")),
              c);
          customersRepo.insert(
              new CustomersRowUnsaved("dsl3@example.com", "hash3".getBytes(), "DSL", "Gold2")
                  .withTier(new Provided<>("gold")),
              c);

          var goldCustomers =
              customerSummaryRepo.select().where(f -> f.tier().isEqual("gold")).toList(c);
          assertEquals(2, goldCustomers.size());

          var specificCustomer =
              customerSummaryRepo
                  .select()
                  .where(f -> f.email().isEqual("dsl1@example.com"))
                  .toList(c);
          assertEquals(1, specificCustomer.size());
          assertEquals("bronze", specificCustomer.get(0).tier());
        });
  }

  @Test
  public void testProductCatalogViewDSLSelect() {
    MariaDbTestHelper.run(
        c -> {
          productsRepo.insert(
              new ProductsRowUnsaved(
                  "DSL-PROD-1",
                  "Cheap Product",
                  new BigDecimal("10.00"),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new Provided<>("active"),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>()),
              c);
          productsRepo.insert(
              new ProductsRowUnsaved(
                  "DSL-PROD-2",
                  "Expensive Product",
                  new BigDecimal("1000.00"),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new Provided<>("active"),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>(),
                  new UseDefault<>()),
              c);
          productsRepo.insert(
              new ProductsRowUnsaved("DSL-PROD-3", "Draft Product", new BigDecimal("50.00")), c);

          var activeProducts =
              productCatalogRepo.select().where(f -> f.status().isEqual("active")).toList(c);
          assertEquals(2, activeProducts.size());

          var specificProduct =
              productCatalogRepo.select().where(f -> f.sku().isEqual("DSL-PROD-1")).toList(c);
          assertEquals(1, specificProduct.size());
          assertEquals("Cheap Product", specificProduct.get(0).name());
        });
  }
}
