package testdb

import dev.typr.foundations.data.{Json, Uint1, Uint2, Uint4, Uint8}
import org.junit.Assert._
import org.junit.Test
import testdb.all_scalar_types._
import testdb.customer_orders._
import testdb.customers._
import testdb.customtypes.Defaulted
import testdb.order_details._

import java.math.BigInteger
import java.time._
import scala.util.Random

/** Tests for DuckDB-specific features: enums, special types, and views.
  */
class DatabaseFeaturesTest {
  private val testInsert = TestInsert(Random(1917394517))
  private val allTypesRepo = AllScalarTypesRepoImpl()
  private val customersRepo = CustomersRepoImpl()
  private val customerOrdersViewRepo = CustomerOrdersViewRepoImpl()
  private val orderDetailsViewRepo = OrderDetailsViewRepoImpl()

  // ==================== Enum Tests ====================

  @Test
  def testPriorityEnumValues(): Unit = withConnection {
    val lowPriority = testInsert.Customers(priority = Defaulted.Provided(Some(Priority.low)))
    val highPriority = testInsert.Customers(priority = Defaulted.Provided(Some(Priority.high)))
    val criticalPriority = testInsert.Customers(priority = Defaulted.Provided(Some(Priority.critical)))

    assertEquals(Some(Priority.low), lowPriority.priority)
    assertEquals(Some(Priority.high), highPriority.priority)
    assertEquals(Some(Priority.critical), criticalPriority.priority)
  }

  @Test
  def testEnumDSLFilter(): Unit = withConnection {
    val _ = testInsert.Customers(priority = Defaulted.Provided(Some(Priority.low)))
    val _ = testInsert.Customers(priority = Defaulted.Provided(Some(Priority.high)))
    val _ = testInsert.Customers(priority = Defaulted.Provided(Some(Priority.critical)))

    val highPriorityCustomers = customersRepo.select
      .where(_.priority.isEqual(Priority.high))
      .toList

    assertTrue(highPriorityCustomers.nonEmpty)
    assertTrue(highPriorityCustomers.forall(_.priority == Some(Priority.high)))
  }

  @Test
  def testMoodEnum(): Unit = withConnection {
    val row = testInsert.AllScalarTypes(colMood = Some(Mood.happy))

    assertEquals(Some(Mood.happy), row.colMood)

    val found = allTypesRepo.selectById(row.id).get
    assertEquals(Some(Mood.happy), found.colMood)
  }

  // ==================== UUID Tests ====================

  @Test
  def testUuidType(): Unit = withConnection {
    val uuid = java.util.UUID.randomUUID()
    val row = testInsert.AllScalarTypes(colUuid = Some(uuid))

    assertEquals(Some(uuid), row.colUuid)
  }

  @Test
  def testUuidUniqueness(): Unit = withConnection {
    val uuid1 = java.util.UUID.randomUUID()
    val uuid2 = java.util.UUID.randomUUID()

    val row1 = testInsert.AllScalarTypes(colUuid = Some(uuid1))
    val row2 = testInsert.AllScalarTypes(colUuid = Some(uuid2))

    assertNotEquals(row1.colUuid, row2.colUuid)
  }

  // ==================== JSON Tests ====================

  @Test
  def testJsonType(): Unit = withConnection {
    val json = "{\"name\": \"test\", \"values\": [1, 2, 3], \"nested\": {\"key\": \"value\"}}"
    val row = testInsert.AllScalarTypes(colJson = Some(Json(json)))

    assertTrue(row.colJson.isDefined)
    assertTrue(row.colJson.get.value.contains("name"))
    assertTrue(row.colJson.get.value.contains("nested"))
  }

  // ==================== Date/Time Tests ====================

  @Test
  def testTimestampWithTimezone(): Unit = withConnection {
    val timestamptz = OffsetDateTime.of(2025, 6, 15, 14, 30, 45, 0, ZoneOffset.ofHours(-5)).toInstant
    val row = testInsert.AllScalarTypes(colTimestamptz = Some(timestamptz))

    assertTrue(row.colTimestamptz.isDefined)
  }

  @Test
  def testIntervalType(): Unit = withConnection {
    val interval = Duration.ofDays(30).plusHours(12)
    val row = testInsert.AllScalarTypes(colInterval = Some(interval))

    assertTrue(row.colInterval.isDefined)
  }

  // ==================== Large Integer Tests ====================

  @Test
  def testHugeintType(): Unit = withConnection {
    val hugeValue = new BigInteger("170141183460469231731687303715884105727")
    val row = testInsert.AllScalarTypes(colHugeint = Some(hugeValue))

    assertTrue(row.colHugeint.isDefined)
  }

  @Test
  def testUnsignedIntegerTypes(): Unit = withConnection {
    val row = testInsert.AllScalarTypes(
      colUtinyint = Some(Uint1(255)),
      colUsmallint = Some(Uint2(65535)),
      colUinteger = Some(Uint4(4294967295L)),
      colUbigint = Some(Uint8(new BigInteger("18446744073709551615")))
    )

    assertEquals(Some(Uint1(255)), row.colUtinyint)
    assertEquals(Some(Uint2(65535)), row.colUsmallint)
    assertEquals(Some(Uint4(4294967295L)), row.colUinteger)
  }

  // ==================== View Tests ====================

  @Test
  def testCustomerOrdersView(): Unit = withConnection {
    val customer = testInsert.Customers(name = "View Test Customer")
    val _ = testInsert.Orders(customerId = customer.customerId.value, totalAmount = Some(BigDecimal("199.99")))

    val viewResults = customerOrdersViewRepo.select.toList

    assertTrue(viewResults.nonEmpty)
    val customerView = viewResults.find(_.customerId.contains(customer.customerId.value))
    assertTrue(customerView.isDefined)
    assertEquals(Some("View Test Customer"), customerView.get.customerName)
  }

  @Test
  def testOrderDetailsView(): Unit = withConnection {
    val customer = testInsert.Customers()
    val product = testInsert.Products(name = "View Test Product")
    val order = testInsert.Orders(customerId = customer.customerId.value)
    val _ = testInsert.OrderItems(
      orderId = order.orderId.value,
      productId = product.productId.value,
      quantity = Defaulted.Provided(3),
      unitPrice = BigDecimal("25.00")
    )

    val viewResults = orderDetailsViewRepo.select.toList

    assertTrue(viewResults.nonEmpty)
    val orderDetail = viewResults.find(_.orderId.contains(order.orderId.value))
    assertTrue(orderDetail.isDefined)
    assertEquals(Some("View Test Product"), orderDetail.get.productName)
    assertEquals(3, orderDetail.get.quantity.get.intValue)
  }

  // ==================== Default Value Tests ====================

  @Test
  def testEnumDefaultValue(): Unit = withConnection {
    val customer = testInsert.Customers()

    assertEquals(Some(Priority.medium), customer.priority)
  }
}
