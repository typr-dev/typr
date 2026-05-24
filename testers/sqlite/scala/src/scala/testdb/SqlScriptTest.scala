package testdb

import dev.typr.foundationssc.{Connection, ConnectionRead}
import org.junit.Assert.*
import org.junit.Test
import testdb.all_scalar_types_search.AllScalarTypesSearchSqlRepoImpl
import testdb.customer_search.CustomerSearchSqlRepoImpl
import testdb.delete_old_orders.DeleteOldOrdersSqlRepoImpl
import testdb.order_summary_by_customer.OrderSummaryByCustomerSqlRepoImpl
import testdb.product_details_with_sales.ProductDetailsWithSalesSqlRepoImpl

import java.time.{LocalDate, LocalDateTime}

class SqlScriptTest {
  private val customerSearch = new CustomerSearchSqlRepoImpl()
  private val orderSummary = new OrderSummaryByCustomerSqlRepoImpl()
  private val productDetails = new ProductDetailsWithSalesSqlRepoImpl()
  private val scalarSearch = new AllScalarTypesSearchSqlRepoImpl()
  private val deleteOld = new DeleteOldOrdersSqlRepoImpl()

  @Test def customerSearchAll(): Unit = withConnection {
    val rows = customerSearch(None, None, None, 100L)(using summon[ConnectionRead])
    assertEquals(2, rows.size)
  }

  @Test def customerSearchByName(): Unit = withConnection {
    val rows = customerSearch(Some("John%"), None, None, 100L)(using summon[ConnectionRead])
    assertEquals(1, rows.size)
    assertEquals("John Doe", rows.head.name)
  }

  @Test def customerSearchByEmail(): Unit = withConnection {
    val rows = customerSearch(None, Some("%jane%"), None, 100L)(using summon[ConnectionRead])
    assertEquals(1, rows.size)
  }

  @Test def customerSearchLimit(): Unit = withConnection {
    val rows = customerSearch(None, None, None, 1L)(using summon[ConnectionRead])
    assertEquals(1, rows.size)
  }

  @Test def customerSearchByCreatedAfter(): Unit = withConnection {
    val rows = customerSearch(None, None, Some(LocalDateTime.of(2099, 1, 1, 0, 0)), 100L)(using summon[ConnectionRead])
    assertEquals(0, rows.size)
  }

  @Test def orderSummaryInRange(): Unit = withConnection {
    val rows = orderSummary(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))(using summon[ConnectionRead])
    assertEquals(2, rows.size)
    val total = rows.flatMap(_.totalRevenue).sum
    assertEquals(0, BigDecimal("109.97").compare(total))
  }

  @Test def orderSummaryEmptyRange(): Unit = withConnection {
    val rows = orderSummary(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31))(using summon[ConnectionRead])
    assertEquals(0, rows.size)
  }

  @Test def productDetailsAll(): Unit = withConnection {
    val rows = productDetails(None)(using summon[ConnectionRead])
    assertEquals(2, rows.size)
  }

  @Test def productDetailsMinPrice(): Unit = withConnection {
    val rows = productDetails(Some(BigDecimal("40.00")))(using summon[ConnectionRead])
    assertEquals(1, rows.size)
    assertEquals("Widget B", rows.head.productName)
  }

  @Test def allScalarSearchAll(): Unit = withConnection {
    val rows = scalarSearch(None, None, None)(using summon[ConnectionRead])
    assertEquals(1, rows.size)
  }

  @Test def allScalarSearchMinId(): Unit = withConnection {
    val rows = scalarSearch(Some(100L), None, None)(using summon[ConnectionRead])
    assertEquals(0, rows.size)
  }

  @Test def allScalarSearchByText(): Unit = withConnection {
    val rows = scalarSearch(None, Some("hel%"), None)(using summon[ConnectionRead])
    assertEquals(1, rows.size)
  }

  @Test def deleteOldOrders(): Unit = withConnection {
    val ordersRepo = new testdb.orders.OrdersRepoImpl()
    ordersRepo.insert(
      testdb.orders.OrdersRow(
        testdb.orders.OrdersId(9001L),
        testdb.customers.CustomersId(1L),
        LocalDate.of(2020, 1, 1),
        Some(BigDecimal("10.00")),
        "completed"
      )
    )
    val deleted = deleteOld(LocalDate.of(2025, 1, 1))
    assertEquals(1, deleted)
  }

  @Test def deleteOldOrdersNoneMatch(): Unit = withConnection {
    val deleted = deleteOld(LocalDate.of(1900, 1, 1))
    assertEquals(0, deleted)
  }
}
