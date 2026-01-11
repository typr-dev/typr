package testdb

import dev.typr.foundations.data.Json
import org.scalatest.funsuite.AnyFunSuite
import testdb.customer_status.*
import testdb.customers.*
import testdb.orders.*
import testdb.customtypes.Defaulted.UseDefault
import testdb.userdefined.Email
import testdb.userdefined.FirstName
import testdb.userdefined.LastName

import java.util.Random

/** Tests for foreign key relationships in the MariaDB ordering system. Tests type-safe FK references.
  */
class ForeignKeyTest extends AnyFunSuite {
  val customerStatusRepo: CustomerStatusRepoImpl = new CustomerStatusRepoImpl
  val customersRepo: CustomersRepoImpl = new CustomersRepoImpl
  val ordersRepo: OrdersRepoImpl = new OrdersRepoImpl
  val testInsert: TestInsert = new TestInsert(new Random(42))

  test("customerStatusInsert") {
    withConnection { c =>
      given java.sql.Connection = c
      val status = CustomerStatusRowUnsaved(
        CustomerStatusId("active_test"),
        "Active Test Status",
        UseDefault()
      )
      val inserted = customerStatusRepo.insert(status)

      val _ = assert(inserted != null)
      val _ = assert(inserted.statusCode == CustomerStatusId("active_test"))
      assert(inserted.description == "Active Test Status")
    }
  }

  test("customerWithForeignKeyToStatus") {
    withConnection { c =>
      given java.sql.Connection = c
      // First create a customer status
      val _ = customerStatusRepo.insert(
        CustomerStatusRowUnsaved(
          CustomerStatusId("verified"),
          "Verified Customer",
          UseDefault()
        )
      )

      // Create a customer with FK to the status - use short constructor
      val customer = CustomersRowUnsaved(
        Email("test@example.com"),
        Array[Byte](1, 2, 3, 4),
        FirstName("John"),
        LastName("Doe")
      )

      val insertedCustomer = customersRepo.insert(customer)

      val _ = assert(insertedCustomer != null)
      val _ = assert(insertedCustomer.customerId.value.value.compareTo(java.math.BigInteger.ZERO) >= 0)
      val _ = assert(insertedCustomer.email == Email("test@example.com"))
      val _ = assert(insertedCustomer.firstName == FirstName("John"))
      assert(insertedCustomer.lastName == LastName("Doe"))
    }
  }

  test("orderWithForeignKeyToCustomer") {
    withConnection { c =>
      given java.sql.Connection = c
      // Create customer status first
      val _ = testInsert.CustomerStatus()

      // Create a customer
      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))

      // Create an order with FK to customer - use short constructor
      val order = OrdersRowUnsaved(
        "ORD-001",
        customer.customerId, // FK to customer
        BigDecimal("89.99"),
        BigDecimal("99.99")
      )

      val insertedOrder = ordersRepo.insert(order)

      val _ = assert(insertedOrder != null)
      val _ = assert(insertedOrder.orderId.value.value.compareTo(java.math.BigInteger.ZERO) >= 0)
      val _ = assert(insertedOrder.orderNumber == "ORD-001")
      assert(insertedOrder.customerId == customer.customerId)
    }
  }

  test("orderLookupByCustomer") {
    withConnection { c =>
      given java.sql.Connection = c
      // Create dependencies
      val _ = testInsert.CustomerStatus()
      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))

      // Create multiple orders for the same customer
      val order1 = testInsert.Orders(customerId = customer.customerId)
      val order2 = testInsert.Orders(customerId = customer.customerId)

      // Verify we can find orders
      val foundOrder1 = ordersRepo.selectById(order1.orderId)
      val foundOrder2 = ordersRepo.selectById(order2.orderId)

      val _ = assert(foundOrder1.isDefined)
      val _ = assert(foundOrder2.isDefined)
      val _ = assert(foundOrder1.get.customerId == customer.customerId)
      assert(foundOrder2.get.customerId == customer.customerId)
    }
  }

  test("typeSafeForeignKeyIds") {
    withConnection { c =>
      given java.sql.Connection = c
      // Demonstrate that IDs are type-safe
      val _ = testInsert.CustomerStatus()
      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))
      val order = testInsert.Orders(customerId = customer.customerId)

      // These are all different types and cannot be confused
      // val statusId: CustomerStatusId = status.statusCode  // unused - showing type safety
      // val customerId: CustomersId = customer.customerId   // unused - showing type safety
      val orderId: OrdersId = order.orderId

      // Can't accidentally pass wrong ID type (would be compile error):
      // ordersRepo.selectById(customerId)  // Compile error!
      // customersRepo.selectById(orderId)  // Compile error!

      // Correct usage
      val foundOrder = ordersRepo.selectById(orderId)
      assert(foundOrder.isDefined)
    }
  }

  test("customerUpdateStatus") {
    withConnection { c =>
      given java.sql.Connection = c
      // Create initial customer
      val _ = testInsert.CustomerStatus()
      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))

      // Create a new status
      val newStatus = customerStatusRepo.insert(
        CustomerStatusRowUnsaved(
          CustomerStatusId("premium"),
          "Premium Customer",
          UseDefault()
        )
      )

      // Update customer to use new status
      val updated = customer.copy(status = newStatus.statusCode)
      val _ = customersRepo.update(updated)

      // Verify update
      val found = customersRepo.selectById(customer.customerId).get
      assert(found.status == newStatus.statusCode)
    }
  }

  test("optionalFieldsOnCustomer") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = testInsert.CustomerStatus()
      val customer = testInsert.Customers(passwordHash = Array[Byte](1, 2, 3))

      // Optional fields should be empty by default
      val _ = assert(customer.phone.isEmpty)
      val _ = assert(customer.preferences.isEmpty)
      val _ = assert(customer.notes.isEmpty)
      val _ = assert(customer.lastLoginAt.isEmpty)

      // Update with values
      val updated = customer.copy(
        phone = Some("+1-555-1234"),
        preferences = Some(Json("""{"theme": "dark"}""")),
        notes = Some("VIP customer")
      )

      val _ = customersRepo.update(updated)

      val found = customersRepo.selectById(customer.customerId).get
      val _ = assert(found.phone == Some("+1-555-1234"))
      val _ = assert(found.preferences == Some(Json("""{"theme": "dark"}""")))
      assert(found.notes == Some("VIP customer"))
    }
  }
}
