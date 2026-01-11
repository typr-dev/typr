package testdb

import dev.typr.foundations.data.maria.{Inet4, Inet6}
import org.scalatest.funsuite.AnyFunSuite

import java.time.Year
import scala.util.Random

/** Tests for TestInsert functionality - automatic random data generation for testing. */
class TestInsertTest extends AnyFunSuite {
  val testInsert: TestInsert = new TestInsert(new Random(42))

  test("mariatestIdentityInsert") {
    withConnection { c =>
      given java.sql.Connection = c
      // TestInsert generates random data for required fields and inserts directly
      val row = testInsert.MariatestIdentity()

      // Verify - ID types are non-null by design
      val _ = assert(row.id.value >= 0)
      assert(row.name.nonEmpty)
    }
  }

  test("mariatestIdentityWithCustomName") {
    withConnection { c =>
      given java.sql.Connection = c
      // Customize by passing parameters
      val row = testInsert.MariatestIdentity(name = "Custom Name")

      val _ = assert(row != null)
      assert(row.name == "Custom Name")
    }
  }

  test("mariatestInsert") {
    withConnection { c =>
      given java.sql.Connection = c
      // Mariatest requires additional parameters for complex types
      val row = testInsert.Mariatest(
        bitCol = Array(0xff.toByte),
        bit1Col = Array(0x01.toByte),
        binaryCol = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
        varbinaryCol = Array[Byte](1, 2, 3),
        tinyblobCol = Array[Byte](4, 5, 6),
        blobCol = Array[Byte](7, 8, 9),
        mediumblobCol = Array[Byte](10, 11, 12),
        longblobCol = Array[Byte](13, 14, 15),
        yearCol = Year.of(2025),
        setCol = XYZSet.fromString("x,y"),
        inet4Col = new Inet4("192.168.1.1"),
        inet6Col = new Inet6("::1")
      )

      // Row and ID are non-null by design
      assert(row.intCol.value >= 0)
    }
  }

  test("customerStatusInsert") {
    withConnection { c =>
      given java.sql.Connection = c
      val row = testInsert.CustomerStatus()

      val _ = assert(row != null)
      val _ = assert(row.statusCode.value.nonEmpty)
      assert(row.description != null)
    }
  }

  test("customersInsertWithForeignKey") {
    withConnection { c =>
      given java.sql.Connection = c
      // First create the required customer status
      val _ = testInsert.CustomerStatus()

      // Now create a customer - requires password_hash
      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))

      // Customer and its fields are non-null by design
      val _ = assert(customer.customerId.value.value().compareTo(java.math.BigInteger.ZERO) >= 0)
      assert(customer.email.value.nonEmpty)
    }
  }

  test("ordersInsertChain") {
    withConnection { c =>
      given java.sql.Connection = c
      // Create required dependencies
      val _ = testInsert.CustomerStatus()
      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))

      // Create an order
      val order = testInsert.Orders(customerId = customer.customerId)

      // Order and its fields are non-null by design
      val _ = assert(order.orderId.value.value.compareTo(java.math.BigInteger.ZERO) >= 0)
      assert(order.customerId == customer.customerId)
    }
  }

  test("mariatestUniqueInsert") {
    withConnection { c =>
      given java.sql.Connection = c
      val row = testInsert.MariatestUnique()

      // Row and its fields are non-null by design
      val _ = assert(row.id.value >= 0)
      val _ = assert(row.email.value.nonEmpty)
      val _ = assert(row.code.nonEmpty)
      assert(row.category.nonEmpty)
    }
  }

  test("mariatestnullInsert") {
    withConnection { c =>
      given java.sql.Connection = c
      // Mariatestnull has all nullable columns - use short constructor
      val row = testInsert.Mariatestnull()

      // Row is non-null, nullable columns are wrapped in Option
      // The test passes if insert succeeds
      assert(row.tinyintCol.isEmpty || row.tinyintCol.isDefined)
    }
  }

  test("multipleInserts") {
    withConnection { c =>
      given java.sql.Connection = c
      // Insert multiple rows using same TestInsert instance
      val row1 = testInsert.MariatestIdentity()
      val row2 = testInsert.MariatestIdentity()
      val row3 = testInsert.MariatestIdentity()

      val _ = assert(row1.id != row2.id)
      val _ = assert(row2.id != row3.id)
      assert(row1.id != row3.id)
    }
  }

  test("insertWithSeededRandom") {
    withConnection { c =>
      given java.sql.Connection = c
      // Using seeded random for reproducible tests
      val testInsert1 = new TestInsert(new Random(123))
      val testInsert2 = new TestInsert(new Random(123))

      // Both should generate same random names
      val row1 = testInsert1.MariatestIdentity()
      val row2 = testInsert2.MariatestIdentity()

      assert(row1.name == row2.name)
    }
  }
}
