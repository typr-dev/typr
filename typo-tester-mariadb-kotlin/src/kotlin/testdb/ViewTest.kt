package testdb

import org.junit.Assert.*
import org.junit.Test
import org.mariadb.jdbc.type.Point
import testdb.brands.BrandsRepoImpl
import testdb.brands.BrandsRowUnsaved
import testdb.categories.CategoriesRepoImpl
import testdb.customer_status.CustomerStatusId
import testdb.customer_status.CustomerStatusRepoImpl
import testdb.customers.CustomersRepoImpl
import testdb.customers.CustomersRowUnsaved
import testdb.customtypes.Defaulted.Provided
import testdb.customtypes.Defaulted.UseDefault
import testdb.inventory.InventoryRepoImpl
import testdb.inventory.InventoryRowUnsaved
import testdb.order_items.OrderItemsRepoImpl
import testdb.order_items.OrderItemsRowUnsaved
import testdb.orders.OrdersRepoImpl
import testdb.orders.OrdersRowUnsaved
import testdb.products.ProductsRepoImpl
import testdb.products.ProductsRowUnsaved
import testdb.reviews.ReviewsRepoImpl
import testdb.reviews.ReviewsRowUnsaved
import testdb.v_customer_summary.VCustomerSummaryViewRepoImpl
import testdb.v_product_catalog.VProductCatalogViewRepoImpl
import testdb.warehouses.WarehousesRepoImpl
import testdb.warehouses.WarehousesRowUnsaved
import java.math.BigDecimal
import java.util.Optional

/**
 * Tests for views - read-only operations using generated view repositories.
 * Note: The database has seeded customer_status data including 'active', 'pending', 'suspended', 'closed'.
 * Tests use these existing statuses rather than inserting new ones.
 */
class ViewTest {
    private val customerSummaryRepo = VCustomerSummaryViewRepoImpl()
    private val productCatalogRepo = VProductCatalogViewRepoImpl()
    private val customersRepo = CustomersRepoImpl()
    private val customerStatusRepo = CustomerStatusRepoImpl()
    private val productsRepo = ProductsRepoImpl()
    private val brandsRepo = BrandsRepoImpl()
    private val ordersRepo = OrdersRepoImpl()
    private val orderItemsRepo = OrderItemsRepoImpl()
    private val reviewsRepo = ReviewsRepoImpl()
    private val warehousesRepo = WarehousesRepoImpl()
    private val inventoryRepo = InventoryRepoImpl()

    @Test
    fun testCustomerSummaryViewSelectAll() {
        WithConnection.run { c ->
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "view1@example.com",
                    passwordHash = "hash1".toByteArray(),
                    firstName = "View",
                    lastName = "Customer1"
                ),
                c
            )
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "view2@example.com",
                    passwordHash = "hash2".toByteArray(),
                    firstName = "View",
                    lastName = "Customer2"
                ),
                c
            )

            val summaries = customerSummaryRepo.selectAll(c)
            assertEquals(2, summaries.size)
        }
    }

    @Test
    fun testCustomerSummaryViewFields() {
        WithConnection.run { c ->
            val customer = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "summary@example.com",
                    passwordHash = "hash".toByteArray(),
                    firstName = "Summary",
                    lastName = "Test",
                    status = Provided(CustomerStatusId("suspended")),
                    tier = Provided("gold")
                ),
                c
            )

            val summaries = customerSummaryRepo.selectAll(c)
            assertEquals(1, summaries.size)

            val summary = summaries[0]
            assertEquals("summary@example.com", summary.email)
            assertEquals(Optional.of("Summary Test"), summary.fullName)
            assertEquals("gold", summary.tier)
            assertEquals("suspended", summary.status.value)
            assertEquals(0L, summary.totalOrders)
            assertEquals(BigDecimal("0.0000"), summary.lifetimeValue)
        }
    }

    @Test
    fun testCustomerSummaryViewWithOrders() {
        WithConnection.run { c ->
            val customer = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "orders@example.com",
                    passwordHash = "hash".toByteArray(),
                    firstName = "With",
                    lastName = "Orders"
                ),
                c
            )

            val brand = brandsRepo.insert(
                BrandsRowUnsaved(name = "TestBrand", slug = "test-brand"),
                c
            )
            val product = productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "SKU-ORDER",
                    name = "Order Product",
                    basePrice = BigDecimal("100.00")
                ),
                c
            )

            val order = ordersRepo.insert(
                OrdersRowUnsaved(
                    orderNumber = "ORD-001",
                    customerId = customer.customerId,
                    subtotal = BigDecimal("100.00"),
                    totalAmount = BigDecimal("110.00")
                ),
                c
            )

            orderItemsRepo.insert(
                OrderItemsRowUnsaved(
                    orderId = order.orderId,
                    productId = product.productId,
                    sku = "SKU-ORDER",
                    productName = "Order Product",
                    quantity = 1,
                    unitPrice = BigDecimal("100.00"),
                    lineTotal = BigDecimal("100.00")
                ),
                c
            )

            val summaries = customerSummaryRepo.selectAll(c)
            assertEquals(1, summaries.size)
            assertTrue(summaries[0].totalOrders >= 1)
        }
    }

    @Test
    fun testProductCatalogViewSelectAll() {
        WithConnection.run { c ->
            val brand = brandsRepo.insert(
                BrandsRowUnsaved(name = "CatalogBrand", slug = "catalog-brand"),
                c
            )

            productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "CAT-SKU-001",
                    name = "Catalog Product 1",
                    basePrice = BigDecimal("25.00"),
                    brandId = Provided(Optional.of(brand.brandId)),
                    status = Provided("active")
                ),
                c
            )
            productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "CAT-SKU-002",
                    name = "Catalog Product 2",
                    basePrice = BigDecimal("50.00"),
                    status = Provided("active")
                ),
                c
            )

            val catalog = productCatalogRepo.selectAll(c)
            assertEquals(2, catalog.size)
        }
    }

    @Test
    fun testProductCatalogViewFields() {
        WithConnection.run { c ->
            val brand = brandsRepo.insert(
                BrandsRowUnsaved(name = "FieldsBrand", slug = "fields-brand"),
                c
            )

            val product = productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "FIELDS-SKU",
                    name = "Fields Product",
                    basePrice = BigDecimal("99.99"),
                    brandId = Provided(Optional.of(brand.brandId)),
                    shortDescription = Provided(Optional.of("Short description")),
                    status = Provided("active")
                ),
                c
            )

            val catalog = productCatalogRepo.selectAll(c)
            assertEquals(1, catalog.size)

            val row = catalog[0]
            assertEquals("FIELDS-SKU", row.sku)
            assertEquals("Fields Product", row.name)
            assertEquals(0, BigDecimal("99.99").compareTo(row.basePrice))
            assertEquals("active", row.status)
            assertEquals(Optional.of("FieldsBrand"), row.brandName)
            assertEquals(Optional.of("Short description"), row.shortDescription)
            assertEquals(0L, row.reviewCount)
        }
    }

    @Test
    fun testProductCatalogViewWithInventory() {
        WithConnection.run { c ->
            val warehouse = warehousesRepo.insert(
                WarehousesRowUnsaved(
                    code = "WH001",
                    name = "Main Warehouse",
                    address = "123 Warehouse St",
                    location = Point(0.0, 0.0)
                ),
                c
            )

            val product = productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "INV-SKU",
                    name = "Inventory Product",
                    basePrice = BigDecimal("75.00"),
                    status = Provided("active")
                ),
                c
            )

            inventoryRepo.insert(
                InventoryRowUnsaved(
                    productId = product.productId,
                    warehouseId = warehouse.warehouseId,
                    quantityOnHand = Provided(100),
                    quantityReserved = Provided(10)
                ),
                c
            )

            val catalog = productCatalogRepo.selectAll(c)
            assertEquals(1, catalog.size)
            assertEquals(BigDecimal("90"), catalog[0].availableQuantity)
        }
    }

    @Test
    fun testProductCatalogViewWithReviews() {
        WithConnection.run { c ->
            val customer = customersRepo.insert(
                CustomersRowUnsaved(
                    email = "reviewer@example.com",
                    passwordHash = "hash".toByteArray(),
                    firstName = "Reviewer",
                    lastName = "User"
                ),
                c
            )

            val product = productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "REVIEW-SKU",
                    name = "Reviewed Product",
                    basePrice = BigDecimal("150.00"),
                    status = Provided("active")
                ),
                c
            )

            reviewsRepo.insert(
                ReviewsRowUnsaved(
                    productId = product.productId,
                    customerId = customer.customerId,
                    rating = 5,
                    isApproved = Provided(true)
                ),
                c
            )
            reviewsRepo.insert(
                ReviewsRowUnsaved(
                    productId = product.productId,
                    customerId = customer.customerId,
                    rating = 4,
                    isApproved = Provided(true)
                ),
                c
            )

            val catalog = productCatalogRepo.selectAll(c)
            assertEquals(1, catalog.size)
            assertEquals(2L, catalog[0].reviewCount)
            assertTrue(catalog[0].avgRating.compareTo(BigDecimal("4")) >= 0)
        }
    }

    @Test
    fun testViewDSLSelect() {
        WithConnection.run { c ->
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl1@example.com",
                    passwordHash = "hash1".toByteArray(),
                    firstName = "DSL",
                    lastName = "Bronze",
                    tier = Provided("bronze")
                ),
                c
            )
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl2@example.com",
                    passwordHash = "hash2".toByteArray(),
                    firstName = "DSL",
                    lastName = "Gold",
                    tier = Provided("gold")
                ),
                c
            )
            customersRepo.insert(
                CustomersRowUnsaved(
                    email = "dsl3@example.com",
                    passwordHash = "hash3".toByteArray(),
                    firstName = "DSL",
                    lastName = "Gold2",
                    tier = Provided("gold")
                ),
                c
            )

            val goldCustomers = customerSummaryRepo.select()
                .where { f -> f.tier().isEqual("gold") }
                .toList(c)
            assertEquals(2, goldCustomers.size)

            val specificCustomer = customerSummaryRepo.select()
                .where { f -> f.email().isEqual("dsl1@example.com") }
                .toList(c)
            assertEquals(1, specificCustomer.size)
            assertEquals("bronze", specificCustomer[0].tier)
        }
    }

    @Test
    fun testProductCatalogViewDSLSelect() {
        WithConnection.run { c ->
            productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "DSL-PROD-1",
                    name = "Cheap Product",
                    basePrice = BigDecimal("10.00"),
                    status = Provided("active")
                ),
                c
            )
            productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "DSL-PROD-2",
                    name = "Expensive Product",
                    basePrice = BigDecimal("1000.00"),
                    status = Provided("active")
                ),
                c
            )
            productsRepo.insert(
                ProductsRowUnsaved(
                    sku = "DSL-PROD-3",
                    name = "Draft Product",
                    basePrice = BigDecimal("50.00")
                ),
                c
            )

            val activeProducts = productCatalogRepo.select()
                .where { f -> f.status().isEqual("active") }
                .toList(c)
            assertEquals(2, activeProducts.size)

            val specificProduct = productCatalogRepo.select()
                .where { f -> f.sku().isEqual("DSL-PROD-1") }
                .toList(c)
            assertEquals(1, specificProduct.size)
            assertEquals("Cheap Product", specificProduct[0].name)
        }
    }
}
