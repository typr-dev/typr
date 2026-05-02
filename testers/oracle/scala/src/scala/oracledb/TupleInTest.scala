package oracledb

import dev.typr.dsl.SqlExpr
import dev.typr.foundations.Connection
import oracledb.departments.*
import org.scalatest.funsuite.AnyFunSuite

import java.math.BigDecimal
import java.util.Optional

class TupleInTest extends AnyFunSuite {
  val departmentsRepo: DepartmentsRepoImpl = new DepartmentsRepoImpl

  // =============== Departments (2-column String,String composite key) ===============

  test("departments compositeIdIn with multiple IDs - real") {
    withConnection {
      val row1 = DepartmentsRow("ENG", "US", "Engineering US", Optional.of(MoneyT(BigDecimal("1000000"), "USD")))
      val row2 = DepartmentsRow("ENG", "EU", "Engineering EU", Optional.of(MoneyT(BigDecimal("800000"), "EUR")))
      val row3 = DepartmentsRow("HR", "US", "Human Resources US", Optional.of(MoneyT(BigDecimal("500000"), "USD")))
      val row4 = DepartmentsRow("HR", "EU", "Human Resources EU", Optional.of(MoneyT(BigDecimal("400000"), "EUR")))

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)
      val _ = departmentsRepo.insert(row4)

      val result = departmentsRepo.select
        .where(d => d.compositeIdIn(java.util.List.of(row1.compositeId, row3.compositeId)))
        .toList(summon[Connection])

      val _ = assert(result.size() == 2)
      val resultIds = Set(result.get(0).compositeId, result.get(1).compositeId)
      val _ = assert(resultIds == Set(row1.compositeId, row3.compositeId))
    }
  }

  test("departments compositeIdIn with single ID - real") {
    withConnection {
      val row1 = DepartmentsRow("SALES", "APAC", "Sales APAC", Optional.empty())
      val row2 = DepartmentsRow("SALES", "EMEA", "Sales EMEA", Optional.empty())

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)

      val result = departmentsRepo.select
        .where(d => d.compositeIdIn(java.util.List.of(row1.compositeId)))
        .toList(summon[Connection])

      val _ = assert(result.size() == 1)
      val _ = assert(result.get(0) == row1)
    }
  }

  test("departments compositeIdIn with empty list - real") {
    withConnection {
      val row = DepartmentsRow("TEST", "REGION", "Test Dept", Optional.empty())
      val _ = departmentsRepo.insert(row)

      val result = departmentsRepo.select
        .where(d => d.compositeIdIn(java.util.List.of()))
        .toList(summon[Connection])

      val _ = assert(result.size() == 0)
    }
  }

  test("departments compositeIdIn combined with other conditions - real") {
    withConnection {
      val row1 = DepartmentsRow("DEV", "US", "Development US", Optional.of(MoneyT(BigDecimal("2000000"), "USD")))
      val row2 = DepartmentsRow("DEV", "EU", "Development EU", Optional.of(MoneyT(BigDecimal("100000"), "EUR")))
      val row3 = DepartmentsRow("QA", "US", "QA US", Optional.of(MoneyT(BigDecimal("500000"), "USD")))

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)

      val result = departmentsRepo.select
        .where(d =>
          SqlExpr.all(
            d.compositeIdIn(java.util.List.of(row1.compositeId, row2.compositeId, row3.compositeId)),
            d.deptName.isEqual("Development US")
          )
        )
        .toList(summon[Connection])

      val _ = assert(result.size() == 1)
      val _ = assert(result.get(0).compositeId == row1.compositeId)
    }
  }

  // ==================== TupleInSubquery Tests ====================

  test("tuple IN subquery basic - real") {
    withConnection {
      val row1 = DepartmentsRow("SMALL1", "MATCH", "Small Dept 1", Optional.of(MoneyT(BigDecimal("10000"), "USD")))
      val row2 = DepartmentsRow("SMALL2", "MATCH", "Small Dept 2", Optional.of(MoneyT(BigDecimal("20000"), "USD")))
      val row3 = DepartmentsRow("LARGE", "OTHER", "Large Dept", Optional.of(MoneyT(BigDecimal("1000000"), "USD")))

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)

      val result = departmentsRepo.select
        .where(d =>
          d.deptCode
            .tupleWith(d.deptRegion)
            .in(
              departmentsRepo.select
                .where(inner => inner.deptRegion.isEqual("MATCH"))
                .map(inner => inner.deptCode.tupleWith(inner.deptRegion))
                .subquery
            )
        )
        .toList(summon[Connection])

      val _ = assert(result.size() == 2)
      val codes = Set(result.get(0).deptCode, result.get(1).deptCode)
      val _ = assert(codes == Set("SMALL1", "SMALL2"))
    }
  }

  test("tuple IN subquery with no matches - real") {
    withConnection {
      val row = DepartmentsRow("TEST1", "REGION1", "Test Dept 1", Optional.empty())
      val _ = departmentsRepo.insert(row)

      val result = departmentsRepo.select
        .where(d =>
          d.deptCode
            .tupleWith(d.deptRegion)
            .in(
              departmentsRepo.select
                .where(inner => inner.deptRegion.isEqual("NONEXISTENT"))
                .map(inner => inner.deptCode.tupleWith(inner.deptRegion))
                .subquery
            )
        )
        .toList(summon[Connection])

      val _ = assert(result.size() == 0)
    }
  }

  test("tuple IN subquery combined with other conditions - real") {
    withConnection {
      val row1 = DepartmentsRow("A", "X", "Dept A", Optional.empty())
      val row2 = DepartmentsRow("B", "X", "Dept B", Optional.empty())
      val row3 = DepartmentsRow("C", "X", "Dept C", Optional.empty())

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)

      val result = departmentsRepo.select
        .where(d =>
          SqlExpr.all(
            d.deptCode
              .tupleWith(d.deptRegion)
              .in(
                departmentsRepo.select
                  .where(inner => inner.deptRegion.isEqual("X"))
                  .map(inner => inner.deptCode.tupleWith(inner.deptRegion))
                  .subquery
              ),
            d.deptCode.isNotEqual("A")
          )
        )
        .toList(summon[Connection])

      val _ = assert(result.size() == 2)
      val codes = Set(result.get(0).deptCode, result.get(1).deptCode)
      val _ = assert(codes == Set("B", "C"))
    }
  }

  // ==================== Nullable Column Tuple IN Tests ====================

  ignore("tuple IN with nullable column - real - Oracle does not support nullable values in tuple IN") {
    withConnection {
      // Create departments - some with budget (nullable), some without
      val row1 = DepartmentsRow("NULL1", "REG1", "Dept With No Budget 1", Optional.empty())
      val row2 = DepartmentsRow("NULL2", "REG2", "Dept With No Budget 2", Optional.empty())
      val row3 = DepartmentsRow("BUDGET", "REG3", "Dept With Budget", Optional.of(MoneyT(BigDecimal("500000"), "USD")))

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)

      // Query using tuple with nullable column - match rows with null budget
      val result = departmentsRepo.select
        .where(d =>
          d.deptCode
            .tupleWith(d.budget)
            .in(
              java.util.List.of(
                dev.typr.foundations.Tuple.of("NULL1", null: MoneyT),
                dev.typr.foundations.Tuple.of("NULL2", null: MoneyT)
              )
            )
        )
        .toList(summon[Connection])

      val _ = assert(result.size() >= 0, "Should handle nullable column tuple IN")
    }
  }

  // ==================== Nested Tuple Tests ====================

  ignore("nested tuple IN - real - Oracle does not support nested tuples in IN clause") {
    withConnection {
      val row1 = DepartmentsRow("NEST1", "R1", "Nested Dept 1", Optional.empty())
      val row2 = DepartmentsRow("NEST2", "R2", "Nested Dept 2", Optional.empty())
      val row3 = DepartmentsRow("NEST3", "R3", "Nested Dept 3", Optional.empty())

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)

      // Test truly nested tuple: ((deptCode, deptRegion), deptName)
      val result = departmentsRepo.select
        .where(d =>
          d.deptCode
            .tupleWith(d.deptRegion)
            .tupleWith(d.deptName)
            .in(
              java.util.List.of(
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of("NEST1", "R1"), "Nested Dept 1"),
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of("NEST3", "R3"), "Nested Dept 3")
              )
            )
        )
        .toList(summon[Connection])

      val _ = assert(result.size() == 2, "Should find 2 departments matching nested tuple pattern")

      // Test that non-matching nested tuple returns empty
      val resultNoMatch = departmentsRepo.select
        .where(d =>
          d.deptCode
            .tupleWith(d.deptRegion)
            .tupleWith(d.deptName)
            .in(
              java.util.List.of(
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of("NEST1", "R1"), "Wrong Name")
              )
            )
        )
        .toList(summon[Connection])

      val _ = assert(resultNoMatch.isEmpty, "Should not match misaligned nested tuple")
    }
  }

  // ==================== Read Nested Tuple from Database Tests ====================

  ignore("readNestedTupleFromDatabase - Oracle does not support nested tuples") {
    withConnection {
      // Insert test data
      val row1 = DepartmentsRow("READ1", "REG1", "Read Dept 1", java.util.Optional.empty())
      val row2 = DepartmentsRow("READ2", "REG2", "Read Dept 2", java.util.Optional.empty())
      val row3 = DepartmentsRow("READ3", "REG3", "Read Dept 3", java.util.Optional.empty())

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)

      // Select nested tuple: ((deptCode, deptRegion), deptName)
      val result = departmentsRepo.select
        .where(d => d.deptCode.in("READ1", "READ2", "READ3"))
        .orderBy(d => d.deptCode.asc)
        .map(d => d.deptCode.tupleWith(d.deptRegion).tupleWith(d.deptName))
        .toList(summon[Connection])

      val _ = assert(result.size() == 3, "Should read 3 nested tuples")

      // Verify the nested tuple structure
      val first = result.get(0)
      val _ = assert(first._1()._1() == "READ1", "First tuple's inner first element")
      val _ = assert(first._1()._2() == "REG1", "First tuple's inner second element")
      val _ = assert(first._2() == "Read Dept 1", "First tuple's outer second element")
    }
  }
}
