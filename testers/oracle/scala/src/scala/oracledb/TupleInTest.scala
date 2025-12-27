package oracledb

import dev.typr.foundations.dsl.{SqlExpr, Tuples}
import oracledb.departments.*
import org.scalatest.funsuite.AnyFunSuite

import java.math.BigDecimal
import java.util.Optional

class TupleInTest extends AnyFunSuite {
  val departmentsRepo: DepartmentsRepoImpl = new DepartmentsRepoImpl

  // =============== Departments (2-column String,String composite key) ===============

  test("departments compositeIdIn with multiple IDs - real") {
    withConnection { c =>
      given java.sql.Connection = c
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
        .toList(c)

      val _ = assert(result.size() == 2)
      val resultIds = Set(result.get(0).compositeId, result.get(1).compositeId)
      assert(resultIds == Set(row1.compositeId, row3.compositeId))
    }
  }

  test("departments compositeIdIn with single ID - real") {
    withConnection { c =>
      given java.sql.Connection = c
      val row1 = DepartmentsRow("SALES", "APAC", "Sales APAC", Optional.empty())
      val row2 = DepartmentsRow("SALES", "EMEA", "Sales EMEA", Optional.empty())

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)

      val result = departmentsRepo.select
        .where(d => d.compositeIdIn(java.util.List.of(row1.compositeId)))
        .toList(c)

      val _ = assert(result.size() == 1)
      assert(result.get(0) == row1)
    }
  }

  test("departments compositeIdIn with empty list - real") {
    withConnection { c =>
      given java.sql.Connection = c
      val row = DepartmentsRow("TEST", "REGION", "Test Dept", Optional.empty())
      val _ = departmentsRepo.insert(row)

      val result = departmentsRepo.select
        .where(d => d.compositeIdIn(java.util.List.of()))
        .toList(c)

      val _ = assert(result.size() == 0)
    }
  }

  test("departments compositeIdIn combined with other conditions - real") {
    withConnection { c =>
      given java.sql.Connection = c
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
        .toList(c)

      val _ = assert(result.size() == 1)
      assert(result.get(0).compositeId == row1.compositeId)
    }
  }

  // ==================== TupleInSubquery Tests ====================

  test("tuple IN subquery basic - real") {
    withConnection { c =>
      given java.sql.Connection = c
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
                .map(inner => Tuples.of(inner.deptCode, inner.deptRegion))
                .subquery
            )
        )
        .toList(c)

      val _ = assert(result.size() == 2)
      val codes = Set(result.get(0).deptCode, result.get(1).deptCode)
      assert(codes == Set("SMALL1", "SMALL2"))
    }
  }

  test("tuple IN subquery with no matches - real") {
    withConnection { c =>
      given java.sql.Connection = c
      val row = DepartmentsRow("TEST1", "REGION1", "Test Dept 1", Optional.empty())
      val _ = departmentsRepo.insert(row)

      val result = departmentsRepo.select
        .where(d =>
          d.deptCode
            .tupleWith(d.deptRegion)
            .in(
              departmentsRepo.select
                .where(inner => inner.deptRegion.isEqual("NONEXISTENT"))
                .map(inner => Tuples.of(inner.deptCode, inner.deptRegion))
                .subquery
            )
        )
        .toList(c)

      val _ = assert(result.size() == 0)
    }
  }

  test("tuple IN subquery combined with other conditions - real") {
    withConnection { c =>
      given java.sql.Connection = c
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
                  .map(inner => Tuples.of(inner.deptCode, inner.deptRegion))
                  .subquery
              ),
            d.deptCode.isNotEqual("A")
          )
        )
        .toList(c)

      val _ = assert(result.size() == 2)
      val codes = Set(result.get(0).deptCode, result.get(1).deptCode)
      assert(codes == Set("B", "C"))
    }
  }
}
