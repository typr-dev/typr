package testdb

import org.junit.Assert._
import org.junit.Test
import testdb.customer_search._
import testdb.customtypes.Defaulted
import testdb.department_employee_details._
import testdb.employee_salary_update._
import testdb.order_summary_by_customer._
import testdb.product_summary._
import testdb.userdefined.Email

import java.time.LocalDateTime
import scala.util.Random

/** Tests for SQL script generated repositories. These tests exercise the typed query classes generated from SQL files in sql-scripts/duckdb/.
  */
class SqlScriptTest {
  private val testInsert = TestInsert(Random(42))
  private val customerSearchRepo = CustomerSearchSqlRepoImpl()
  private val orderSummaryRepo = OrderSummaryByCustomerSqlRepoImpl()
  private val productSummaryRepo = ProductSummarySqlRepoImpl()
  private val deptEmpDetailsRepo = DepartmentEmployeeDetailsSqlRepoImpl()
  private val empSalaryUpdateRepo = EmployeeSalaryUpdateSqlRepoImpl()

  @Test
  def testCustomerSearchWithNamePattern(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(name = "ScalaSearchMe Customer")

    val results = customerSearchRepo(
      namePattern = Some("ScalaSearchMe%"),
      emailPattern = None,
      minPriority = None,
      createdAfter = None,
      maxResults = 10L
    )

    assertTrue(results.nonEmpty)
    assertTrue(results.exists(_.customerId == customer.customerId))
  }

  @Test
  def testCustomerSearchWithEmailPattern(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers(email = Some(Email("unique-scala-search@example.com")))

    val results = customerSearchRepo(
      namePattern = None,
      emailPattern = Some("%unique-scala-search%"),
      minPriority = None,
      createdAfter = None,
      maxResults = 10L
    )

    assertTrue(results.nonEmpty)
    assertTrue(results.exists(_.customerId == customer.customerId))
  }

  @Test
  def testCustomerSearchWithPriorityFilter(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val highPriorityCustomer = testInsert.Customers(priority = Defaulted.Provided(Some(Priority.high)))

    val results = customerSearchRepo(
      namePattern = None,
      emailPattern = None,
      minPriority = Some(Priority.high),
      createdAfter = None,
      maxResults = 100L
    )

    assertTrue(results.nonEmpty)
    assertTrue(results.exists(_.customerId == highPriorityCustomer.customerId))
  }

  @Test
  def testCustomerSearchWithDateFilter(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = testInsert.Customers()
    val yesterday = LocalDateTime.now().minusDays(1)

    val results = customerSearchRepo(
      namePattern = None,
      emailPattern = None,
      minPriority = None,
      createdAfter = Some(yesterday),
      maxResults = 10L
    )

    assertTrue(results.nonEmpty)
  }

  @Test
  def testCustomerSearchWithMaxResults(): Unit = withConnection { c =>
    given java.sql.Connection = c

    for (i <- 0 until 5) {
      val _ = testInsert.Customers(name = s"ScalaLimitTest$i")
    }

    val results = customerSearchRepo(
      namePattern = Some("ScalaLimitTest%"),
      emailPattern = None,
      minPriority = None,
      createdAfter = None,
      maxResults = 3L
    )

    assertEquals(3, results.size)
  }

  @Test
  def testOrderSummaryByCustomerNoFilters(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer = testInsert.Customers()
    val _ = testInsert.Orders(customerId = customer.customerId.value, totalAmount = Some(BigDecimal("150.00")))

    val results = orderSummaryRepo(
      customerIds = None,
      minTotal = None,
      minOrderCount = None
    )

    assertTrue(results.nonEmpty)
    val customerSummary = results.find(_.customerId == customer.customerId)
    assertTrue(customerSummary.isDefined)
    assertEquals(Some(1L), customerSummary.get.orderCount)
  }

  @Test
  def testOrderSummaryByCustomerIds(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val customer1 = testInsert.Customers()
    val customer2 = testInsert.Customers()
    val _ = testInsert.Orders(customerId = customer1.customerId.value)
    val _ = testInsert.Orders(customerId = customer2.customerId.value)

    val ids = Array(customer1.customerId.value, customer2.customerId.value)
    val results = orderSummaryRepo(
      customerIds = Some(ids),
      minTotal = None,
      minOrderCount = None
    )

    assertTrue(results.size >= 2)
  }

  @Test
  def testOrderSummaryWithMinTotal(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val bigSpender = testInsert.Customers()
    val _ = testInsert.Orders(customerId = bigSpender.customerId.value, totalAmount = Some(BigDecimal("1000.00")))

    val smallSpender = testInsert.Customers()
    val _ = testInsert.Orders(customerId = smallSpender.customerId.value, totalAmount = Some(BigDecimal("10.00")))

    val results = orderSummaryRepo(
      customerIds = None,
      minTotal = Some(BigDecimal("500.00")),
      minOrderCount = None
    )

    assertTrue(results.exists(_.customerId == bigSpender.customerId))
    assertFalse(results.exists(_.customerId == smallSpender.customerId))
  }

  @Test
  def testOrderSummaryWithMinOrderCount(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val frequentBuyer = testInsert.Customers()
    for (_ <- 0 until 3) {
      val _ = testInsert.Orders(customerId = frequentBuyer.customerId.value)
    }

    val results = orderSummaryRepo(
      customerIds = None,
      minTotal = None,
      minOrderCount = Some(3)
    )

    assertTrue(results.exists(_.customerId == frequentBuyer.customerId))
  }

  @Test
  def testProductSummary(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val _ = testInsert.Products()

    val results = productSummaryRepo.apply

    assertTrue(results.nonEmpty)
  }

  @Test
  def testDepartmentEmployeeDetails(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val dept = testInsert.Departments()
    val _ = testInsert.Employees(deptCode = dept.deptCode, deptRegion = dept.deptRegion)

    val results = deptEmpDetailsRepo(
      deptCode = Some(dept.deptCode),
      deptRegion = Some(dept.deptRegion),
      minSalary = None,
      hiredAfter = None
    )

    assertTrue(results.nonEmpty)
  }

  @Test
  def testEmployeeSalaryUpdate(): Unit = withConnection { c =>
    given java.sql.Connection = c

    val dept = testInsert.Departments()
    val emp = testInsert.Employees(
      deptCode = dept.deptCode,
      deptRegion = dept.deptRegion,
      salary = Some(BigDecimal("50000"))
    )

    val updatedRows = empSalaryUpdateRepo(
      raisePercentage = None,
      newSalary = BigDecimal("55000.00"),
      empNumber = emp.empNumber,
      empSuffix = emp.empSuffix
    )

    assertEquals(1, updatedRows.size)
  }
}
