package testdb

import dev.typr.foundations.data.{Uint1, Uint2}
import org.mariadb.jdbc.`type`.Point
import org.scalatest.funsuite.AnyFunSuite
import testdb.brands.*
import testdb.customer_status.CustomerStatusId
import testdb.customers.*
import testdb.customtypes.Defaulted.Provided
import testdb.inventory.*
import testdb.order_items.*
import testdb.orders.*
import testdb.products.*
import testdb.reviews.*
import testdb.v_customer_summary.*
import testdb.v_product_catalog.*
import testdb.warehouses.*

/** Tests for views - read-only operations using generated view repositories. Note: The database has seeded customer_status data including 'active', 'pending', 'suspended', 'closed'. Tests use these
  * existing statuses rather than inserting new ones.
  */
class ViewTest extends AnyFunSuite {
  val customerSummaryRepo: VCustomerSummaryViewRepoImpl = new VCustomerSummaryViewRepoImpl
  val productCatalogRepo: VProductCatalogViewRepoImpl = new VProductCatalogViewRepoImpl
  val customersRepo: CustomersRepoImpl = new CustomersRepoImpl
  val productsRepo: ProductsRepoImpl = new ProductsRepoImpl
  val brandsRepo: BrandsRepoImpl = new BrandsRepoImpl
  val ordersRepo: OrdersRepoImpl = new OrdersRepoImpl
  val orderItemsRepo: OrderItemsRepoImpl = new OrderItemsRepoImpl
  val reviewsRepo: ReviewsRepoImpl = new ReviewsRepoImpl
  val warehousesRepo: WarehousesRepoImpl = new WarehousesRepoImpl
  val inventoryRepo: InventoryRepoImpl = new InventoryRepoImpl

  test("customerSummaryViewSelectAll") {
    withConnection { c =>
      given java.sql.Connection = c

      customersRepo.insert(
        CustomersRowUnsaved(
          email = "view1@example.com",
          passwordHash = "hash1".getBytes,
          firstName = "View",
          lastName = "Customer1"
        )
      )
      customersRepo.insert(
        CustomersRowUnsaved(
          email = "view2@example.com",
          passwordHash = "hash2".getBytes,
          firstName = "View",
          lastName = "Customer2"
        )
      )

      val summaries = customerSummaryRepo.selectAll
      assert(summaries.size == 2)
    }
  }

  test("customerSummaryViewFields") {
    withConnection { c =>
      given java.sql.Connection = c

      customersRepo.insert(
        CustomersRowUnsaved(
          email = "summary@example.com",
          passwordHash = "hash".getBytes,
          firstName = "Summary",
          lastName = "Test",
          status = Provided(CustomerStatusId("suspended")),
          tier = Provided("gold")
        )
      )

      val summaries = customerSummaryRepo.selectAll
      val _ = assert(summaries.size == 1)

      val summary = summaries.head
      val _ = assert(summary.email == "summary@example.com")
      val _ = assert(summary.fullName == Some("Summary Test"))
      val _ = assert(summary.tier == "gold")
      val _ = assert(summary.status.value == "suspended")
      val _ = assert(summary.totalOrders == 0L)
      assert(summary.lifetimeValue == BigDecimal("0.0000"))
    }
  }

  test("customerSummaryViewWithOrders") {
    withConnection { c =>
      given java.sql.Connection = c

      val customer = customersRepo.insert(
        CustomersRowUnsaved(
          email = "orders@example.com",
          passwordHash = "hash".getBytes,
          firstName = "With",
          lastName = "Orders"
        )
      )

      val brand = brandsRepo.insert(BrandsRowUnsaved(name = "TestBrand", slug = "test-brand"))
      val product = productsRepo.insert(
        ProductsRowUnsaved(
          sku = "SKU-ORDER",
          name = "Order Product",
          basePrice = BigDecimal("100.00")
        )
      )

      val order = ordersRepo.insert(
        OrdersRowUnsaved(
          orderNumber = "ORD-001",
          customerId = customer.customerId,
          subtotal = BigDecimal("100.00"),
          totalAmount = BigDecimal("110.00")
        )
      )

      orderItemsRepo.insert(
        OrderItemsRowUnsaved(
          orderId = order.orderId,
          productId = product.productId,
          sku = "SKU-ORDER",
          productName = "Order Product",
          quantity = Uint2.of(1),
          unitPrice = BigDecimal("100.00"),
          lineTotal = BigDecimal("100.00")
        )
      )

      val summaries = customerSummaryRepo.selectAll
      val _ = assert(summaries.size == 1)
      assert(summaries.head.totalOrders >= 1)
    }
  }

  test("productCatalogViewSelectAll") {
    withConnection { c =>
      given java.sql.Connection = c

      val brand = brandsRepo.insert(BrandsRowUnsaved(name = "CatalogBrand", slug = "catalog-brand"))

      productsRepo.insert(
        ProductsRowUnsaved(
          sku = "CAT-SKU-001",
          name = "Catalog Product 1",
          basePrice = BigDecimal("25.00"),
          brandId = Provided(Some(brand.brandId)),
          status = Provided("active")
        )
      )
      productsRepo.insert(
        ProductsRowUnsaved(
          sku = "CAT-SKU-002",
          name = "Catalog Product 2",
          basePrice = BigDecimal("50.00"),
          status = Provided("active")
        )
      )

      val catalog = productCatalogRepo.selectAll
      assert(catalog.size == 2)
    }
  }

  test("productCatalogViewFields") {
    withConnection { c =>
      given java.sql.Connection = c

      val brand = brandsRepo.insert(BrandsRowUnsaved(name = "FieldsBrand", slug = "fields-brand"))

      productsRepo.insert(
        ProductsRowUnsaved(
          sku = "FIELDS-SKU",
          name = "Fields Product",
          basePrice = BigDecimal("99.99"),
          brandId = Provided(Some(brand.brandId)),
          shortDescription = Provided(Some("Short description")),
          status = Provided("active")
        )
      )

      val catalog = productCatalogRepo.selectAll
      val _ = assert(catalog.size == 1)

      val row = catalog.head
      val _ = assert(row.sku == "FIELDS-SKU")
      val _ = assert(row.name == "Fields Product")
      val _ = assert(row.basePrice.compareTo(BigDecimal("99.99")) == 0)
      val _ = assert(row.status == "active")
      val _ = assert(row.brandName == Some("FieldsBrand"))
      val _ = assert(row.shortDescription == Some("Short description"))
      assert(row.reviewCount == 0L)
    }
  }

  test("productCatalogViewWithInventory") {
    withConnection { c =>
      given java.sql.Connection = c

      val warehouse = warehousesRepo.insert(
        WarehousesRowUnsaved(
          code = "WH001",
          name = "Main Warehouse",
          address = "123 Warehouse St",
          location = new Point(0.0, 0.0)
        )
      )

      val product = productsRepo.insert(
        ProductsRowUnsaved(
          sku = "INV-SKU",
          name = "Inventory Product",
          basePrice = BigDecimal("75.00"),
          status = Provided("active")
        )
      )

      inventoryRepo.insert(
        InventoryRowUnsaved(
          productId = product.productId,
          warehouseId = warehouse.warehouseId,
          quantityOnHand = Provided(100),
          quantityReserved = Provided(10)
        )
      )

      val catalog = productCatalogRepo.selectAll
      val _ = assert(catalog.size == 1)
      assert(catalog.head.availableQuantity == BigDecimal("90"))
    }
  }

  test("productCatalogViewWithReviews") {
    withConnection { c =>
      given java.sql.Connection = c

      val customer = customersRepo.insert(
        CustomersRowUnsaved(
          email = "reviewer@example.com",
          passwordHash = "hash".getBytes,
          firstName = "Reviewer",
          lastName = "User"
        )
      )

      val product = productsRepo.insert(
        ProductsRowUnsaved(
          sku = "REVIEW-SKU",
          name = "Reviewed Product",
          basePrice = BigDecimal("150.00"),
          status = Provided("active")
        )
      )

      reviewsRepo.insert(
        ReviewsRowUnsaved(
          productId = product.productId,
          customerId = customer.customerId,
          rating = Uint1.of(5),
          isApproved = Provided(true)
        )
      )
      reviewsRepo.insert(
        ReviewsRowUnsaved(
          productId = product.productId,
          customerId = customer.customerId,
          rating = Uint1.of(4),
          isApproved = Provided(true)
        )
      )

      val catalog = productCatalogRepo.selectAll
      val _ = assert(catalog.size == 1)
      val _ = assert(catalog.head.reviewCount == 2L)
      assert(catalog.head.avgRating.compareTo(BigDecimal("4")) >= 0)
    }
  }

  test("viewDSLSelect") {
    withConnection { c =>
      given java.sql.Connection = c

      customersRepo.insert(
        CustomersRowUnsaved(
          email = "dsl1@example.com",
          passwordHash = "hash1".getBytes,
          firstName = "DSL",
          lastName = "Bronze",
          tier = Provided("bronze")
        )
      )
      customersRepo.insert(
        CustomersRowUnsaved(
          email = "dsl2@example.com",
          passwordHash = "hash2".getBytes,
          firstName = "DSL",
          lastName = "Gold",
          tier = Provided("gold")
        )
      )
      customersRepo.insert(
        CustomersRowUnsaved(
          email = "dsl3@example.com",
          passwordHash = "hash3".getBytes,
          firstName = "DSL",
          lastName = "Gold2",
          tier = Provided("gold")
        )
      )

      val goldCustomers = customerSummaryRepo.select
        .where(f => f.tier.isEqual("gold"))
        .toList
      val _ = assert(goldCustomers.size == 2)

      val specificCustomer = customerSummaryRepo.select
        .where(f => f.email.isEqual("dsl1@example.com"))
        .toList
      val _ = assert(specificCustomer.size == 1)
      assert(specificCustomer.head.tier == "bronze")
    }
  }

  test("productCatalogViewDSLSelect") {
    withConnection { c =>
      given java.sql.Connection = c

      productsRepo.insert(
        ProductsRowUnsaved(
          sku = "DSL-PROD-1",
          name = "Cheap Product",
          basePrice = BigDecimal("10.00"),
          status = Provided("active")
        )
      )
      productsRepo.insert(
        ProductsRowUnsaved(
          sku = "DSL-PROD-2",
          name = "Expensive Product",
          basePrice = BigDecimal("1000.00"),
          status = Provided("active")
        )
      )
      productsRepo.insert(
        ProductsRowUnsaved(
          sku = "DSL-PROD-3",
          name = "Draft Product",
          basePrice = BigDecimal("50.00")
        )
      )

      val activeProducts = productCatalogRepo.select
        .where(f => f.status.isEqual("active"))
        .toList
      val _ = assert(activeProducts.size == 2)

      val specificProduct = productCatalogRepo.select
        .where(f => f.sku.isEqual("DSL-PROD-1"))
        .toList
      val _ = assert(specificProduct.size == 1)
      assert(specificProduct.head.name == "Cheap Product")
    }
  }
}
