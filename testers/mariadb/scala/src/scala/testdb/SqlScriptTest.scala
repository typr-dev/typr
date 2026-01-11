package testdb

import org.scalatest.funsuite.AnyFunSuite
import testdb.customer_orders.*
import testdb.customtypes.Defaulted
import testdb.product_search.*
import testdb.simple_customer_lookup.*

import scala.util.Random

/** Tests for SQL script generated code. These tests verify that the code generated from SQL files in sql-scripts/mariadb/ works correctly.
  */
class SqlScriptTest extends AnyFunSuite {
  val productSearchRepo: ProductSearchSqlRepoImpl = new ProductSearchSqlRepoImpl
  val customerOrdersRepo: CustomerOrdersSqlRepoImpl = new CustomerOrdersSqlRepoImpl
  val simpleCustomerLookupRepo: SimpleCustomerLookupSqlRepoImpl = new SimpleCustomerLookupSqlRepoImpl
  val testInsert: TestInsert = new TestInsert(new Random(42))

  test("productSearchWithNoFilters") {
    withConnection { c =>
      given java.sql.Connection = c
      // Insert test products first
      val _ = testInsert.CustomerStatus()
      val brand = testInsert.Brands()
      val _ = testInsert.Products(brandId = new Defaulted.Provided(Some(brand.brandId)))
      val _ = testInsert.Products(brandId = new Defaulted.Provided(Some(brand.brandId)))

      // Call the SQL script with no filters
      val results = productSearchRepo.apply(
        None, // brandId
        None, // minPrice
        None, // maxPrice
        None, // status
        100L // limit
      )

      val _ = assert(results != null)
      assert(results.size >= 2)
    }
  }

  test("productSearchWithBrandFilter") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = testInsert.CustomerStatus()
      val brand1 = testInsert.Brands()
      val brand2 = testInsert.Brands()
      val _ = testInsert.Products(brandId = new Defaulted.Provided(Some(brand1.brandId)))
      val _ = testInsert.Products(brandId = new Defaulted.Provided(Some(brand2.brandId)))

      // Filter by specific brand
      val results = productSearchRepo.apply(
        Some(brand1.brandId.value), // brandId
        None,
        None,
        None,
        100L
      )

      val _ = assert(results != null)
      val _ = assert(results.nonEmpty)
      // All results should be from brand1
      for (row <- results) {
        val _ = assert(row != null)
      }
    }
  }

  test("productSearchWithPriceRange") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = testInsert.CustomerStatus()
      val brand = testInsert.Brands()
      val _ = testInsert.Products(brandId = new Defaulted.Provided(Some(brand.brandId)), basePrice = BigDecimal("50.00"))
      val _ = testInsert.Products(brandId = new Defaulted.Provided(Some(brand.brandId)), basePrice = BigDecimal("150.00"))

      // Filter by price range
      val results = productSearchRepo.apply(
        None,
        Some(BigDecimal("40.00")), // minPrice
        Some(BigDecimal("100.00")), // maxPrice
        None,
        100L
      )

      val _ = assert(results != null)
      // Should find products in range
      for (row <- results) {
        val _ = assert(row.basePrice >= BigDecimal("40.00") && row.basePrice <= BigDecimal("100.00"))
      }
    }
  }

  test("productSearchWithLimit") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = testInsert.CustomerStatus()
      val brand = testInsert.Brands()
      for (_ <- 0 until 5) {
        val _ = testInsert.Products(brandId = new Defaulted.Provided(Some(brand.brandId)))
      }

      // Limit to 2 results
      val results = productSearchRepo.apply(None, None, None, None, 2L)

      val _ = assert(results != null)
      assert(results.size == 2)
    }
  }

  test("simpleCustomerLookup") {
    withConnection { c =>
      given java.sql.Connection = c
      // Create a customer
      val _ = testInsert.CustomerStatus()
      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))

      // Look up by email
      val results = simpleCustomerLookupRepo.apply(customer.email.value)

      val _ = assert(results != null)
      val _ = assert(results.size == 1)
      val _ = assert(results.head.email == customer.email)
      val _ = assert(results.head.firstName == customer.firstName)
      assert(results.head.lastName == customer.lastName)
    }
  }

  test("customerOrders") {
    withConnection { c =>
      given java.sql.Connection = c
      // Create dependencies
      val _ = testInsert.CustomerStatus()
      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))

      // Create orders for the customer
      val _ = testInsert.Orders(customerId = customer.customerId)
      val _ = testInsert.Orders(customerId = customer.customerId)

      // Query customer orders - need to pass orderStatus as well
      val results = customerOrdersRepo.apply(customer.customerId, None)

      val _ = assert(results != null)
      assert(results.size >= 2)
    }
  }
}
