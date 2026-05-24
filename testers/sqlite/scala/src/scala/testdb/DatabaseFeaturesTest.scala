package testdb

import dev.typr.foundations.data.Json
import org.junit.Assert.*
import org.junit.Test
import testdb.customer_orders.CustomerOrdersViewRepoImpl
import testdb.customers.*
import testdb.customtypes.Defaulted
import testdb.order_details.OrderDetailsViewRepoImpl
import testdb.orders.*
import testdb.products.*
import testdb.userdefined.Email

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

class DatabaseFeaturesTest {
  private val customers = new CustomersRepoImpl()
  private val products = new ProductsRepoImpl()
  private val orders = new OrdersRepoImpl()
  private val customerOrdersRepo = new CustomerOrdersViewRepoImpl()
  private val orderDetailsRepo = new OrderDetailsViewRepoImpl()

  @Test def insertReturningPopulatesGeneratedColumns(): Unit = withConnection {
    val unsaved = CustomersRowUnsaved(CustomersId(10001L), "Returning Test")
    val inserted = customers.insert(unsaved)
    assertEquals(10001L, inserted.customerId.value)
    assertNotNull(inserted.createdAt)
  }

  @Test def insertWithProvidedDefault(): Unit = withConnection {
    val explicit = LocalDateTime.of(2020, 5, 1, 12, 0)
    val unsaved = CustomersRowUnsaved(
      CustomersId(10002L),
      "Explicit",
      Some(Email("u@x.com")),
      Defaulted.Provided(explicit)
    )
    assertEquals(explicit, customers.insert(unsaved).createdAt)
  }

  @Test def upsertInsertsNew(): Unit = withConnection {
    val row = CustomersRow(CustomersId(20001L), "Upsert Insert", None, LocalDateTime.of(2025, 1, 1, 0, 0))
    assertEquals("Upsert Insert", customers.upsert(row).name)
  }

  @Test def upsertUpdatesOnConflict(): Unit = withConnection {
    val row = CustomersRow(CustomersId(20002L), "Original", Some(Email("a@x.com")), LocalDateTime.of(2025, 1, 1, 0, 0))
    customers.insert(row)
    val updated = row.copy(name = "Updated via Upsert", email = Some(Email("b@x.com")))
    val result = customers.upsert(updated)
    assertEquals("Updated via Upsert", result.name)
    assertEquals("Updated via Upsert", customers.selectById(CustomersId(20002L)).get.name)
  }

  @Test def uuidJsonRoundTrip(): Unit = withConnection {
    val uuid = UUID.randomUUID()
    val product = ProductsRow(
      ProductsId(30001L),
      s"SKU-UUID-$uuid",
      "UUID Product",
      BigDecimal("1.00"),
      Some(Json(s"""{"uuid":"$uuid"}"""))
    )
    val inserted = products.insert(product)
    assertTrue(inserted.metadata.get.value.contains(uuid.toString))
  }

  @Test def customerOrdersView(): Unit = withConnection {
    val rows = customerOrdersRepo.selectAll
    assertEquals(2, rows.size)
    assertTrue(rows.exists(_.customerName.contains("John Doe")))
    assertTrue(rows.exists(_.customerName.contains("Jane Smith")))
  }

  @Test def orderDetailsView(): Unit = withConnection {
    val rows = orderDetailsRepo.selectAll
    assertEquals(3, rows.size)
    rows.foreach { r =>
      val s = r.lineTotal.get
      assertTrue(BigDecimal(s) > BigDecimal(0))
    }
  }

  @Test def selectByIdsReturnsRequested(): Unit = withConnection {
    val found = customers.selectByIds(List(CustomersId(1L), CustomersId(2L)))
    assertEquals(2, found.size)
  }

  @Test def insertWithExplicitId(): Unit = withConnection {
    val row = CustomersRow(CustomersId(50001L), "Explicit ID", None, LocalDateTime.of(2025, 1, 1, 0, 0))
    val inserted = customers.insert(row)
    assertEquals(50001L, inserted.customerId.value)
  }

  @Test def orderInsertWithForeignKeyValue(): Unit = withConnection {
    val ord = orders.insert(
      OrdersRow(
        OrdersId(70001L),
        CustomersId(1L),
        LocalDate.of(2025, 7, 1),
        Some(BigDecimal("250.00")),
        "pending"
      )
    )
    assertEquals(70001L, ord.orderId.value)
  }
}
