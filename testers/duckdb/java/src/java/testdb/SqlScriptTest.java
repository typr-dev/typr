package testdb;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import org.junit.Test;
import testdb.customer_search.*;
import testdb.customers.*;
import testdb.customtypes.Defaulted;
import testdb.department_employee_details.*;
import testdb.departments.*;
import testdb.employee_salary_update.*;
import testdb.employees.*;
import testdb.order_summary_by_customer.*;
import testdb.orders.*;
import testdb.product_summary.*;
import testdb.products.*;
import testdb.userdefined.Email;

/**
 * Tests for SQL script generated repositories. These tests exercise the typed query classes
 * generated from SQL files in sql-scripts/duckdb/.
 */
public class SqlScriptTest {
  private final TestInsert testInsert = new TestInsert(new Random(42));
  private final CustomerSearchSqlRepoImpl customerSearchRepo = new CustomerSearchSqlRepoImpl();
  private final OrderSummaryByCustomerSqlRepoImpl orderSummaryRepo =
      new OrderSummaryByCustomerSqlRepoImpl();
  private final ProductSummarySqlRepoImpl productSummaryRepo = new ProductSummarySqlRepoImpl();
  private final DepartmentEmployeeDetailsSqlRepoImpl deptEmpDetailsRepo =
      new DepartmentEmployeeDetailsSqlRepoImpl();
  private final EmployeeSalaryUpdateSqlRepoImpl empSalaryUpdateRepo =
      new EmployeeSalaryUpdateSqlRepoImpl();

  @Test
  public void testCustomerSearchWithNamePattern() {
    DuckDbTestHelper.run(
        c -> {
          var customer =
              testInsert.Customers().with(r -> r.withName("SearchMe Customer")).insert(c);

          var results =
              customerSearchRepo.apply(
                  Optional.of("SearchMe%"),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  10L,
                  c);

          assertFalse(results.isEmpty());
          assertTrue(results.stream().anyMatch(r -> r.customerId().equals(customer.customerId())));
        });
  }

  @Test
  public void testCustomerSearchWithEmailPattern() {
    DuckDbTestHelper.run(
        c -> {
          var customer =
              testInsert
                  .Customers()
                  .with(r -> r.withEmail(Optional.of(new Email("unique-search@example.com"))))
                  .insert(c);

          var results =
              customerSearchRepo.apply(
                  Optional.empty(),
                  Optional.of("%unique-search%"),
                  Optional.empty(),
                  Optional.empty(),
                  10L,
                  c);

          assertFalse(results.isEmpty());
          assertTrue(results.stream().anyMatch(r -> r.customerId().equals(customer.customerId())));
        });
  }

  @Test
  public void testCustomerSearchWithPriorityFilter() {
    DuckDbTestHelper.run(
        c -> {
          var highPriorityCustomer =
              testInsert
                  .Customers()
                  .with(r -> r.withPriority(new Defaulted.Provided<>(Optional.of(Priority.high))))
                  .insert(c);

          var results =
              customerSearchRepo.apply(
                  Optional.empty(),
                  Optional.empty(),
                  Optional.of(Priority.high),
                  Optional.empty(),
                  100L,
                  c);

          assertFalse(results.isEmpty());
          assertTrue(
              results.stream()
                  .anyMatch(r -> r.customerId().equals(highPriorityCustomer.customerId())));
        });
  }

  @Test
  public void testCustomerSearchWithDateFilter() {
    DuckDbTestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var yesterday = LocalDateTime.now().minusDays(1);

          var results =
              customerSearchRepo.apply(
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.of(yesterday),
                  10L,
                  c);

          assertFalse(results.isEmpty());
        });
  }

  @Test
  public void testCustomerSearchWithMaxResults() {
    DuckDbTestHelper.run(
        c -> {
          for (int i = 0; i < 5; i++) {
            final int idx = i;
            testInsert.Customers().with(r -> r.withName("LimitTest" + idx)).insert(c);
          }

          var results =
              customerSearchRepo.apply(
                  Optional.of("LimitTest%"),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  3L,
                  c);

          assertEquals(3, results.size());
        });
  }

  @Test
  public void testOrderSummaryByCustomerNoFilters() {
    DuckDbTestHelper.run(
        c -> {
          var customer = testInsert.Customers().insert(c);
          var order =
              testInsert
                  .Orders()
                  .with(
                      r ->
                          r.withCustomerId(customer.customerId().value())
                              .withTotalAmount(Optional.of(new BigDecimal("150.00"))))
                  .insert(c);

          var results =
              orderSummaryRepo.apply(Optional.empty(), Optional.empty(), Optional.empty(), c);

          assertFalse(results.isEmpty());
          var customerSummary =
              results.stream()
                  .filter(r -> r.customerId().equals(customer.customerId()))
                  .findFirst();
          assertTrue(customerSummary.isPresent());
          assertEquals(1L, customerSummary.get().orderCount().get().longValue());
        });
  }

  @Test
  public void testOrderSummaryByCustomerIds() {
    DuckDbTestHelper.run(
        c -> {
          var customer1 = testInsert.Customers().insert(c);
          var customer2 = testInsert.Customers().insert(c);
          testInsert.Orders().with(r -> r.withCustomerId(customer1.customerId().value())).insert(c);
          testInsert.Orders().with(r -> r.withCustomerId(customer2.customerId().value())).insert(c);

          Integer[] ids = {customer1.customerId().value(), customer2.customerId().value()};
          var results =
              orderSummaryRepo.apply(Optional.of(ids), Optional.empty(), Optional.empty(), c);

          assertTrue(results.size() >= 2);
        });
  }

  @Test
  public void testOrderSummaryWithMinTotal() {
    DuckDbTestHelper.run(
        c -> {
          var bigSpender = testInsert.Customers().insert(c);
          testInsert
              .Orders()
              .with(
                  r ->
                      r.withCustomerId(bigSpender.customerId().value())
                          .withTotalAmount(Optional.of(new BigDecimal("1000.00"))))
              .insert(c);

          var smallSpender = testInsert.Customers().insert(c);
          testInsert
              .Orders()
              .with(
                  r ->
                      r.withCustomerId(smallSpender.customerId().value())
                          .withTotalAmount(Optional.of(new BigDecimal("10.00"))))
              .insert(c);

          var results =
              orderSummaryRepo.apply(
                  Optional.empty(), Optional.of(new BigDecimal("500.00")), Optional.empty(), c);

          assertTrue(
              results.stream().anyMatch(r -> r.customerId().equals(bigSpender.customerId())));
          assertFalse(
              results.stream().anyMatch(r -> r.customerId().equals(smallSpender.customerId())));
        });
  }

  @Test
  public void testOrderSummaryWithMinOrderCount() {
    DuckDbTestHelper.run(
        c -> {
          var frequentBuyer = testInsert.Customers().insert(c);
          testInsert
              .Orders()
              .with(r -> r.withCustomerId(frequentBuyer.customerId().value()))
              .insert(c);
          testInsert
              .Orders()
              .with(r -> r.withCustomerId(frequentBuyer.customerId().value()))
              .insert(c);
          testInsert
              .Orders()
              .with(r -> r.withCustomerId(frequentBuyer.customerId().value()))
              .insert(c);

          var results =
              orderSummaryRepo.apply(Optional.empty(), Optional.empty(), Optional.of(3), c);

          assertTrue(
              results.stream().anyMatch(r -> r.customerId().equals(frequentBuyer.customerId())));
        });
  }

  @Test
  public void testProductSummary() {
    DuckDbTestHelper.run(
        c -> {
          var product = testInsert.Products().insert(c);

          var results = productSummaryRepo.apply(c);

          assertFalse(results.isEmpty());
        });
  }

  @Test
  public void testDepartmentEmployeeDetails() {
    DuckDbTestHelper.run(
        c -> {
          var dept = testInsert.Departments().insert(c);
          var emp =
              testInsert
                  .Employees()
                  .with(e -> e.withDeptCode(dept.deptCode()).withDeptRegion(dept.deptRegion()))
                  .insert(c);

          var results =
              deptEmpDetailsRepo.apply(
                  Optional.of(dept.deptCode()),
                  Optional.of(dept.deptRegion()),
                  Optional.empty(),
                  Optional.empty(),
                  c);

          assertFalse(results.isEmpty());
        });
  }

  @Test
  public void testEmployeeSalaryUpdate() {
    DuckDbTestHelper.run(
        c -> {
          var dept = testInsert.Departments().insert(c);
          var emp =
              testInsert
                  .Employees()
                  .with(
                      e ->
                          e.withDeptCode(dept.deptCode())
                              .withDeptRegion(dept.deptRegion())
                              .withSalary(Optional.of(new BigDecimal("50000"))))
                  .insert(c);

          var updatedRows =
              empSalaryUpdateRepo.apply(
                  Optional.empty(),
                  new BigDecimal("55000.00"),
                  emp.empNumber(),
                  emp.empSuffix(),
                  c);

          assertEquals(1, updatedRows.size());
        });
  }
}
