package testdb

import dev.typr.foundations.dsl.Bijection
import org.junit.Assert._
import org.junit.Test
import testdb.customers._
import testdb.orders._
import testdb.products._
import testdb.userdefined.Email

import scala.util.Random

/** Tests for the DSL query builder functionality. Tests type-safe query building with where, orderBy, limit, count, and projection.
  */
class DSLTest {
  private val testInsert = TestInsert(Random(42))
  private val customersRepo = CustomersRepoImpl()
  private val productsRepo = ProductsRepoImpl()
  private val ordersRepo = OrdersRepoImpl()

  @Test
  def testSelectWithWhere(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(name = "DSL Where Test")

    val results = customersRepo.select
      .where(_.name.isEqual("DSL Where Test"))
      .toList

    assertTrue(results.nonEmpty)
    assertTrue(results.exists(_.customerId == customer.customerId))
  }

  @Test
  def testSelectWithOrderBy(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = testInsert.Customers(name = "Zebra")
    val _ = testInsert.Customers(name = "Alpha")
    val _ = testInsert.Customers(name = "Mike")

    val results = customersRepo.select
      .orderBy(_.name.asc)
      .limit(10)
      .toList

    assertTrue(results.size >= 3)
    var firstName: String = null
    for (result <- results) {
      if (firstName != null) {
        assertTrue(result.name.compareTo(firstName) >= 0)
      }
      firstName = result.name
    }
  }

  @Test
  def testSelectWithLimit(): Unit = withConnection { c =>
    given java.sql.Connection = c

    for (i <- 0 until 10) {
      testInsert.Customers(name = s"LimitTest$i")
    }

    val results = customersRepo.select
      .where(_.name.like("LimitTest%", Bijection.asString()))
      .limit(5)
      .toList

    assertEquals(5, results.size)
  }

  @Test
  def testSelectWithCount(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = testInsert.Customers(name = "CountA")
    val _ = testInsert.Customers(name = "CountB")
    val _ = testInsert.Customers(name = "CountC")

    val count = customersRepo.select
      .where(_.name.like("Count%", Bijection.asString()))
      .count

    assertEquals(3, count)
  }

  @Test
  def testSelectWithIn(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val c1 = testInsert.Customers(name = "InTest1")
    val _ = testInsert.Customers(name = "InTest2")
    val c3 = testInsert.Customers(name = "InTest3")

    val results = customersRepo.select
      .where(_.customerId.in(c1.customerId, c3.customerId))
      .toList

    assertEquals(2, results.size)
  }

  @Test
  def testSelectWithProjection(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = testInsert.Customers(email = Email("projection@test.com"), name = "ProjectionTest")

    val results = customersRepo.select
      .where(_.name.isEqual("ProjectionTest"))
      .map(cust => cust.name.tupleWith(cust.email))
      .toList

    assertEquals(1, results.size)
    assertEquals("ProjectionTest", results.head._1)
    assertEquals(Email("projection@test.com"), results.head._2)
  }

  @Test
  def testProductDSLQuery(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = testInsert.Products(name = "Expensive Product", price = BigDecimal("999.99"))
    val _ = testInsert.Products(name = "Cheap Product", price = BigDecimal("9.99"))

    val expensiveProducts = productsRepo.select
      .where(_.price.greaterThan(BigDecimal("100.00")))
      .toList

    assertTrue(expensiveProducts.nonEmpty)
    assertTrue(expensiveProducts.forall(_.price.compareTo(BigDecimal("100.00")) > 0))
  }

  @Test
  def testOrdersDSLQuery(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers()

    val _ = testInsert.Orders(customer.customerId, totalAmount = BigDecimal("500.00"))
    val _ = testInsert.Orders(customer.customerId, totalAmount = BigDecimal("1500.00"))

    val largeOrders = ordersRepo.select
      .where(_.totalAmount.greaterThan(BigDecimal("1000.00")))
      .toList

    assertTrue(largeOrders.nonEmpty)
    assertTrue(largeOrders.forall(_.totalAmount.compareTo(BigDecimal("1000.00")) > 0))
  }

  @Test
  def testComplexWhereClause(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = testInsert.Customers(email = Email("complex-a@test.com"), name = "Complex A")
    val _ = testInsert.Customers(email = Email("complex-b@test.com"), name = "Complex B")
    val _ = testInsert.Customers(email = Email("other@test.com"), name = "Other")

    val results = customersRepo.select
      .where(cust =>
        cust.name
          .like("Complex%", Bijection.asString())
          .and(cust.email.like("%@test.com", Email.bijection))
      )
      .toList

    assertEquals(2, results.size)
  }
}
