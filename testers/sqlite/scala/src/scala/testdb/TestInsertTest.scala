package testdb

import org.junit.Assert.*
import org.junit.Test

import scala.util.Random

/** Generated TestInsert helpers actually persist random rows via the connection (the Scala variant differs from Java's pure-construction API). Verifies the SqliteAdapter arm in ComputedTestInserts
  * produces values that round-trip.
  */
class TestInsertTest {
  private val testInsert = TestInsert(Random(1169258584))

  @Test def customersInsert(): Unit = withConnection {
    val row = testInsert.Customers()
    assertNotNull(row.customerId)
    assertNotNull(row.name)
  }

  @Test def customersWithCustomization(): Unit = withConnection {
    val row = testInsert.Customers(name = "Custom Name")
    assertEquals("Custom Name", row.name)
  }

  @Test def departmentsInsert(): Unit = withConnection {
    val row = testInsert.Departments()
    assertNotNull(row.deptCode)
    assertNotNull(row.deptRegion)
  }

  @Test def productsInsert(): Unit = withConnection {
    val row = testInsert.Products()
    assertNotNull(row.productId)
    assertNotNull(row.sku)
  }

  @Test def allScalarTypesInsert(): Unit = withConnection {
    val row = testInsert.AllScalarTypes()
    assertNotNull(row.id)
    assertNotNull(row.colNotNull)
  }

  @Test def employeesWithDepartmentFK(): Unit = withConnection {
    val dept = testInsert.Departments()
    val emp = testInsert.Employees(DepartmentsId = dept.compositeId)
    assertEquals(dept.deptCode, emp.deptCode)
    assertEquals(dept.deptRegion, emp.deptRegion)
  }

  @Test def ordersWithCustomerFK(): Unit = withConnection {
    val cust = testInsert.Customers()
    val ord = testInsert.Orders(customerId = cust.customerId)
    assertEquals(cust.customerId, ord.customerId)
  }

  @Test def multipleInserts(): Unit = withConnection {
    val r1 = testInsert.Customers()
    val r2 = testInsert.Customers()
    val r3 = testInsert.Customers()
    assertNotEquals(r1.customerId, r2.customerId)
    assertNotEquals(r2.customerId, r3.customerId)
  }

  @Test def seededRandomReproducible(): Unit = {
    val name1 = withConnection {
      TestInsert(Random(123)).Customers().name
    }
    val name2 = withConnection {
      TestInsert(Random(123)).Customers().name
    }
    assertEquals(name1, name2)
  }
}
