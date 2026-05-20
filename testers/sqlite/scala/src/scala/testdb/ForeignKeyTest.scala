package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.order_items.*
import testdb.orders.*
import testdb.products.*
import testdb.userdefined.Email

import java.time.{LocalDate, LocalDateTime}

class ForeignKeyTest {
  private val customers = new CustomersRepoImpl()
  private val products = new ProductsRepoImpl()
  private val orders = new OrdersRepoImpl()
  private val orderItems = new OrderItemsRepoImpl()

  @Test def customerInsert(): Unit = withConnection {
    val inserted = customers.insert(
      CustomersRow(CustomersId(100L), "John", Some(Email("john@x.com")), LocalDateTime.of(2025, 1, 1, 0, 0))
    )
    assertEquals("John", inserted.name)
  }

  @Test def productInsert(): Unit = withConnection {
    val inserted = products.insert(ProductsRow(ProductsId(100L), "PROD-100", "Widget", BigDecimal("29.99"), None))
    assertEquals("PROD-100", inserted.sku)
  }

  @Test def orderWithCustomerFK(): Unit = withConnection {
    val cust = customers.insert(
      CustomersRow(CustomersId(101L), "Jane", Some(Email("jane@x.com")), LocalDateTime.of(2025, 1, 2, 0, 0))
    )
    val ord = orders.insert(
      OrdersRow(OrdersId(101L), cust.customerId, LocalDate.of(2025, 1, 15), Some(BigDecimal("99.99")), "pending")
    )
    assertEquals(cust.customerId, ord.customerId)
  }

  @Test def orderItemWithCompositePK(): Unit = withConnection {
    customers.insert(CustomersRow(CustomersId(102L), "x", None, LocalDateTime.of(2025, 1, 1, 0, 0)))
    val prod = products.insert(ProductsRow(ProductsId(102L), "PROD-102", "W", BigDecimal("49.99"), None))
    val ord = orders.insert(
      OrdersRow(OrdersId(102L), CustomersId(102L), LocalDate.of(2025, 1, 16), Some(BigDecimal("149.97")), "pending")
    )
    val item = orderItems.insert(OrderItemsRow(ord.orderId, prod.productId, 3L, BigDecimal("49.99")))
    assertEquals(3L, item.quantity)
  }

  @Test def foreignKeyEnforcedOnInsert(): Unit = {
    var threw = false
    try {
      withConnection {
        orders.insert(OrdersRow(OrdersId(9999L), CustomersId(99999L), LocalDate.of(2025, 1, 1), None, "pending"))
      }
    } catch {
      case e: Exception =>
        val msg = Option(e.getMessage).getOrElse("")
        threw = msg.contains("FOREIGN KEY") || msg.toLowerCase.contains("foreign key")
    }
    assertTrue("Expected FK violation", threw)
  }

  @Test def typeSafeIds(): Unit = withConnection {
    val cid = CustomersId(200L); val pid = ProductsId(200L); val oid = OrdersId(200L)
    customers.insert(CustomersRow(cid, "TS", None, LocalDateTime.of(2025, 1, 1, 0, 0)))
    products.insert(ProductsRow(pid, "SKU-200", "TS Prod", BigDecimal("1.00"), None))
    orders.insert(OrdersRow(oid, cid, LocalDate.of(2025, 1, 1), None, "pending"))
    assertTrue(customers.selectById(cid).isDefined)
    assertTrue(products.selectById(pid).isDefined)
    assertTrue(orders.selectById(oid).isDefined)
  }
}
