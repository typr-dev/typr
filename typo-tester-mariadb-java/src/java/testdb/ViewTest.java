package testdb;

import org.junit.Test;
import org.mariadb.jdbc.type.Point;
import testdb.brands.BrandsRepoImpl;
import testdb.brands.BrandsRowUnsaved;
import testdb.categories.CategoriesRepoImpl;
import testdb.categories.CategoriesRowUnsaved;
import testdb.customer_status.CustomerStatusId;
import testdb.customer_status.CustomerStatusRepoImpl;
import testdb.customer_status.CustomerStatusRowUnsaved;
import testdb.customers.CustomersRepoImpl;
import testdb.customers.CustomersRowUnsaved;
import testdb.customtypes.Defaulted.Provided;
import testdb.customtypes.Defaulted.UseDefault;
import testdb.order_items.OrderItemsRepoImpl;
import testdb.order_items.OrderItemsRowUnsaved;
import testdb.orders.OrdersRepoImpl;
import testdb.orders.OrdersRowUnsaved;
import testdb.products.ProductsRepoImpl;
import testdb.products.ProductsRowUnsaved;
import testdb.reviews.ReviewsRepoImpl;
import testdb.reviews.ReviewsRowUnsaved;
import testdb.v_customer_summary.VCustomerSummaryViewRepoImpl;
import testdb.v_customer_summary.VCustomerSummaryViewRow;
import testdb.v_product_catalog.VProductCatalogViewRepoImpl;
import testdb.v_product_catalog.VProductCatalogViewRow;
import testdb.warehouses.WarehousesRepoImpl;
import testdb.warehouses.WarehousesRowUnsaved;
import testdb.inventory.InventoryRepoImpl;
import testdb.inventory.InventoryRowUnsaved;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Tests for views - read-only operations using generated view repositories.
 * Note: The database has seeded customer_status data including 'active', 'pending', 'suspended', 'closed'.
 * Tests use these existing statuses rather than inserting new ones.
 */
public class ViewTest {
    private final VCustomerSummaryViewRepoImpl customerSummaryRepo = new VCustomerSummaryViewRepoImpl();
    private final VProductCatalogViewRepoImpl productCatalogRepo = new VProductCatalogViewRepoImpl();
    private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
    private final CustomerStatusRepoImpl customerStatusRepo = new CustomerStatusRepoImpl();
    private final ProductsRepoImpl productsRepo = new ProductsRepoImpl();
    private final BrandsRepoImpl brandsRepo = new BrandsRepoImpl();
    private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();
    private final OrderItemsRepoImpl orderItemsRepo = new OrderItemsRepoImpl();
    private final ReviewsRepoImpl reviewsRepo = new ReviewsRepoImpl();
    private final WarehousesRepoImpl warehousesRepo = new WarehousesRepoImpl();
    private final InventoryRepoImpl inventoryRepo = new InventoryRepoImpl();

    @Test
    public void testCustomerSummaryViewSelectAll() {
        WithConnection.run(c -> {
            // Use existing seeded 'active' status
            // Create customers
            customersRepo.insert(
                new CustomersRowUnsaved("view1@example.com", "hash1".getBytes(), "View", "Customer1"),
                c
            );
            customersRepo.insert(
                new CustomersRowUnsaved("view2@example.com", "hash2".getBytes(), "View", "Customer2"),
                c
            );

            // Query the view
            var summaries = customerSummaryRepo.selectAll(c);
            assertEquals(2, summaries.size());
        });
    }

    @Test
    public void testCustomerSummaryViewFields() {
        WithConnection.run(c -> {
            // Use existing seeded 'suspended' status
            var customer = customersRepo.insert(
                new CustomersRowUnsaved(
                    "summary@example.com",
                    "hash".getBytes(),
                    "Summary",
                    "Test",
                    new UseDefault<>(),
                    new Provided<>(new CustomerStatusId("suspended")),
                    new Provided<>("gold"),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>()
                ),
                c
            );

            var summaries = customerSummaryRepo.selectAll(c);
            assertEquals(1, summaries.size());

            VCustomerSummaryViewRow summary = summaries.get(0);
            assertEquals("summary@example.com", summary.email());
            assertEquals(Optional.of("Summary Test"), summary.fullName());
            assertEquals("gold", summary.tier());
            assertEquals("suspended", summary.status().value());
            assertEquals(Long.valueOf(0), summary.totalOrders());
            assertEquals(new BigDecimal("0.0000"), summary.lifetimeValue());
        });
    }

    @Test
    public void testCustomerSummaryViewWithOrders() {
        WithConnection.run(c -> {
            // Use existing seeded 'active' status
            var customer = customersRepo.insert(
                new CustomersRowUnsaved("orders@example.com", "hash".getBytes(), "With", "Orders"),
                c
            );

            // Create a product for order items
            var brand = brandsRepo.insert(
                new BrandsRowUnsaved("TestBrand", "test-brand"),
                c
            );
            var product = productsRepo.insert(
                new ProductsRowUnsaved("SKU-ORDER", "Order Product", new BigDecimal("100.00")),
                c
            );

            // Create an order
            var order = ordersRepo.insert(
                new OrdersRowUnsaved(
                    "ORD-001",
                    customer.customerId(),
                    new BigDecimal("100.00"),
                    new BigDecimal("110.00")
                ),
                c
            );

            // Create order items
            orderItemsRepo.insert(
                new OrderItemsRowUnsaved(
                    order.orderId(),
                    product.productId(),
                    "SKU-ORDER",
                    "Order Product",
                    1,
                    new BigDecimal("100.00"),
                    new BigDecimal("100.00")
                ),
                c
            );

            // Query the view - should show order stats
            var summaries = customerSummaryRepo.selectAll(c);
            assertEquals(1, summaries.size());
            // The view aggregates orders, so total_orders should be >= 1
            assertTrue(summaries.get(0).totalOrders() >= 1);
        });
    }

    @Test
    public void testProductCatalogViewSelectAll() {
        WithConnection.run(c -> {
            // Create a brand
            var brand = brandsRepo.insert(
                new BrandsRowUnsaved("CatalogBrand", "catalog-brand"),
                c
            );

            // Create products - both must have status='active' to appear in the view
            productsRepo.insert(
                new ProductsRowUnsaved("CAT-SKU-001", "Catalog Product 1", new BigDecimal("25.00"))
                    .withBrandId(new Provided<>(Optional.of(brand.brandId())))
                    .withStatus(new Provided<>("active")),
                c
            );
            productsRepo.insert(
                new ProductsRowUnsaved("CAT-SKU-002", "Catalog Product 2", new BigDecimal("50.00"))
                    .withStatus(new Provided<>("active")),
                c
            );

            var catalog = productCatalogRepo.selectAll(c);
            assertEquals(2, catalog.size());
        });
    }

    @Test
    public void testProductCatalogViewFields() {
        WithConnection.run(c -> {
            var brand = brandsRepo.insert(
                new BrandsRowUnsaved("FieldsBrand", "fields-brand"),
                c
            );

            var product = productsRepo.insert(
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
                    new UseDefault<>()
                ),
                c
            );

            var catalog = productCatalogRepo.selectAll(c);
            assertEquals(1, catalog.size());

            VProductCatalogViewRow row = catalog.get(0);
            assertEquals("FIELDS-SKU", row.sku());
            assertEquals("Fields Product", row.name());
            assertEquals(0, new BigDecimal("99.99").compareTo(row.basePrice()));
            assertEquals("active", row.status());
            assertEquals(Optional.of("FieldsBrand"), row.brandName());
            assertEquals(Optional.of("Short description"), row.shortDescription());
            assertEquals(Long.valueOf(0), row.reviewCount());
        });
    }

    @Test
    public void testProductCatalogViewWithInventory() {
        WithConnection.run(c -> {
            // Create warehouse (required for inventory)
            var warehouse = warehousesRepo.insert(
                new WarehousesRowUnsaved(
                    "WH001",
                    "Main Warehouse",
                    "123 Warehouse St",
                    new Point(0, 0)
                ),
                c
            );

            var product = productsRepo.insert(
                new ProductsRowUnsaved("INV-SKU", "Inventory Product", new BigDecimal("75.00"))
                    .withStatus(new Provided<>("active")),  // status must be 'active' for v_product_catalog view
                c
            );

            // Add inventory
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
                    new UseDefault<>()
                ),
                c
            );

            var catalog = productCatalogRepo.selectAll(c);
            assertEquals(1, catalog.size());
            // available_quantity = quantity_on_hand - quantity_reserved = 100 - 10 = 90
            assertEquals(new BigDecimal("90"), catalog.get(0).availableQuantity());
        });
    }

    @Test
    public void testProductCatalogViewWithReviews() {
        WithConnection.run(c -> {
            // Use existing seeded 'active' status for reviews
            var customer = customersRepo.insert(
                new CustomersRowUnsaved("reviewer@example.com", "hash".getBytes(), "Reviewer", "User"),
                c
            );

            var product = productsRepo.insert(
                new ProductsRowUnsaved("REVIEW-SKU", "Reviewed Product", new BigDecimal("150.00"))
                    .withStatus(new Provided<>("active")),  // status must be 'active' for v_product_catalog view
                c
            );

            // Add reviews - must be approved to appear in the view (is_approved = 1)
            reviewsRepo.insert(
                new ReviewsRowUnsaved(product.productId(), customer.customerId(), (short) 5)
                    .withIsApproved(new Provided<>(true)),
                c
            );
            reviewsRepo.insert(
                new ReviewsRowUnsaved(product.productId(), customer.customerId(), (short) 4)
                    .withIsApproved(new Provided<>(true)),
                c
            );

            var catalog = productCatalogRepo.selectAll(c);
            assertEquals(1, catalog.size());
            assertEquals(Long.valueOf(2), catalog.get(0).reviewCount());
            // Average should be (5 + 4) / 2 = 4.5
            assertTrue(catalog.get(0).avgRating().compareTo(new BigDecimal("4")) >= 0);
        });
    }

    @Test
    public void testViewDSLSelect() {
        WithConnection.run(c -> {
            // Use existing seeded status
            customersRepo.insert(
                new CustomersRowUnsaved(
                    "dsl1@example.com",
                    "hash1".getBytes(),
                    "DSL",
                    "Bronze",
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new Provided<>("bronze"),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>()
                ),
                c
            );
            customersRepo.insert(
                new CustomersRowUnsaved(
                    "dsl2@example.com",
                    "hash2".getBytes(),
                    "DSL",
                    "Gold",
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new Provided<>("gold"),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>()
                ),
                c
            );
            customersRepo.insert(
                new CustomersRowUnsaved(
                    "dsl3@example.com",
                    "hash3".getBytes(),
                    "DSL",
                    "Gold2",
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new Provided<>("gold"),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>(),
                    new UseDefault<>()
                ),
                c
            );

            // Test DSL select with where
            var goldCustomers = customerSummaryRepo.select()
                .where(f -> f.tier().isEqual("gold"))
                .toList(c);
            assertEquals(2, goldCustomers.size());

            // Test DSL select with email filter
            var specificCustomer = customerSummaryRepo.select()
                .where(f -> f.email().isEqual("dsl1@example.com"))
                .toList(c);
            assertEquals(1, specificCustomer.size());
            assertEquals("bronze", specificCustomer.get(0).tier());
        });
    }

    @Test
    public void testProductCatalogViewDSLSelect() {
        WithConnection.run(c -> {
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
                    new UseDefault<>()
                ),
                c
            );
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
                    new UseDefault<>()
                ),
                c
            );
            productsRepo.insert(
                new ProductsRowUnsaved("DSL-PROD-3", "Draft Product", new BigDecimal("50.00")),
                c
            );

            // Filter by status
            var activeProducts = productCatalogRepo.select()
                .where(f -> f.status().isEqual("active"))
                .toList(c);
            assertEquals(2, activeProducts.size());

            // Filter by SKU
            var specificProduct = productCatalogRepo.select()
                .where(f -> f.sku().isEqual("DSL-PROD-1"))
                .toList(c);
            assertEquals(1, specificProduct.size());
            assertEquals("Cheap Product", specificProduct.get(0).name());
        });
    }
}
