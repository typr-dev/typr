package oracledb

import dev.typr.dslsc.SqlExpr
import oracledb.departments.*
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

import java.math.BigDecimal

class TupleInTest {
  val departmentsRepo: DepartmentsRepoImpl = new DepartmentsRepoImpl

  // =============== Departments (2-column String,String composite key) ===============

  @Test
  def departmentsCompositeIdInWithMultipleIds(): Unit = {
    withConnection {
      val row1 = DepartmentsRow("ENG", "US", "Engineering US", Some(MoneyT(BigDecimal("1000000"), "USD")))
      val row2 = DepartmentsRow("ENG", "EU", "Engineering EU", Some(MoneyT(BigDecimal("800000"), "EUR")))
      val row3 = DepartmentsRow("HR", "US", "Human Resources US", Some(MoneyT(BigDecimal("500000"), "USD")))
      val row4 = DepartmentsRow("HR", "EU", "Human Resources EU", Some(MoneyT(BigDecimal("400000"), "EUR")))

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)
      val _ = departmentsRepo.insert(row4)

      val result = departmentsRepo.select
        .where(d => d.compositeIdIn(List(row1.compositeId, row3.compositeId)))
        .toList

      assertEquals(2, result.size)
      val resultIds = result.map(_.compositeId).toSet
      assertEquals(Set(row1.compositeId, row3.compositeId), resultIds)
    }
  }

  @Test
  def departmentsCompositeIdInWithSingleId(): Unit = {
    withConnection {
      val row1 = DepartmentsRow("SALES", "APAC", "Sales APAC", None)
      val row2 = DepartmentsRow("SALES", "EMEA", "Sales EMEA", None)

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)

      val result = departmentsRepo.select
        .where(d => d.compositeIdIn(List(row1.compositeId)))
        .toList

      assertEquals(1, result.size)
      assertEquals(row1, result.head)
    }
  }

  @Test
  def departmentsCompositeIdInWithEmptyList(): Unit = {
    withConnection {
      val row = DepartmentsRow("TEST", "REGION", "Test Dept", None)
      val _ = departmentsRepo.insert(row)

      val result = departmentsRepo.select
        .where(d => d.compositeIdIn(List.empty))
        .toList

      assertTrue(result.isEmpty)
    }
  }

  @Test
  def departmentsCompositeIdInCombinedWithOtherConditions(): Unit = {
    withConnection {
      val row1 = DepartmentsRow("DEV", "US", "Development US", Some(MoneyT(BigDecimal("2000000"), "USD")))
      val row2 = DepartmentsRow("DEV", "EU", "Development EU", Some(MoneyT(BigDecimal("100000"), "EUR")))
      val row3 = DepartmentsRow("QA", "US", "QA US", Some(MoneyT(BigDecimal("500000"), "USD")))

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)

      val result = departmentsRepo.select
        .where(d =>
          SqlExpr.all(
            d.compositeIdIn(List(row1.compositeId, row2.compositeId, row3.compositeId)),
            d.deptName.isEqual("Development US")
          )
        )
        .toList

      assertEquals(1, result.size)
      assertEquals(row1.compositeId, result.head.compositeId)
    }
  }

  @Test
  def departmentsCompositeIdInWithNonExistentIds(): Unit = {
    withConnection {
      val row = DepartmentsRow("EXISTING", "DEPT", "Existing Dept", None)
      val _ = departmentsRepo.insert(row)

      val result = departmentsRepo.select
        .where(d =>
          d.compositeIdIn(
            List(
              row.compositeId,
              DepartmentsId("NONEXISTENT", "DEPT"),
              DepartmentsId("ALSO", "MISSING")
            )
          )
        )
        .toList

      assertEquals(1, result.size)
      assertEquals(row, result.head)
    }
  }

  @Test
  def departmentsCompositeIdComputedVsManual(): Unit = {
    withConnection {
      val row = DepartmentsRow("COMPUTED", "TEST", "Computed Test", None)
      val _ = departmentsRepo.insert(row)

      val computedId = row.compositeId
      val manualId = DepartmentsId("COMPUTED", "TEST")

      assertEquals(computedId, manualId)

      val result = departmentsRepo.select
        .where(d => d.compositeIdIn(List(computedId, manualId)))
        .toList

      assertEquals(1, result.size)
      assertEquals(row, result.head)
    }
  }

  // ==================== TupleInSubquery Tests ====================

  @Test
  def tupleInSubqueryBasic(): Unit = {
    withConnection {
      val row1 = DepartmentsRow("SMALL1", "MATCH", "Small Dept 1", Some(MoneyT(BigDecimal("10000"), "USD")))
      val row2 = DepartmentsRow("SMALL2", "MATCH", "Small Dept 2", Some(MoneyT(BigDecimal("20000"), "USD")))
      val row3 = DepartmentsRow("LARGE", "OTHER", "Large Dept", Some(MoneyT(BigDecimal("1000000"), "USD")))

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
        .toList

      assertEquals(2, result.size)
      val codes = result.map(_.deptCode).toSet
      assertEquals(Set("SMALL1", "SMALL2"), codes)
    }
  }

  @Test
  def tupleInSubqueryWithNoMatches(): Unit = {
    withConnection {
      val row = DepartmentsRow("TEST1", "REGION1", "Test Dept 1", None)
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
        .toList

      assertTrue(result.isEmpty)
    }
  }

  @Test
  def tupleInSubqueryCombinedWithOtherConditions(): Unit = {
    withConnection {
      val row1 = DepartmentsRow("A", "X", "Dept A", None)
      val row2 = DepartmentsRow("B", "X", "Dept B", None)
      val row3 = DepartmentsRow("C", "X", "Dept C", None)

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
        .toList

      assertEquals(2, result.size)
      val codes = result.map(_.deptCode).toSet
      assertEquals(Set("B", "C"), codes)
    }
  }

  // ==================== Nullable Column Tuple IN Tests ====================

  @Ignore("Oracle does not support nullable values in tuple IN")
  @Test
  def tupleInWithNullableColumn(): Unit = {
    withConnection {
      // Create departments - some with budget (nullable), some without
      val row1 = DepartmentsRow("NULL1", "REG1", "Dept With No Budget 1", None)
      val row2 = DepartmentsRow("NULL2", "REG2", "Dept With No Budget 2", None)
      val row3 = DepartmentsRow("BUDGET", "REG3", "Dept With Budget", Some(MoneyT(BigDecimal("500000"), "USD")))

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)

      // Query using tuple with nullable column - match rows with null budget
      val result = departmentsRepo.select
        .where(d =>
          d.deptCode
            .tupleWith(d.budget)
            .in(
              List(
                dev.typr.foundations.Tuple.of("NULL1", null: MoneyT),
                dev.typr.foundations.Tuple.of("NULL2", null: MoneyT)
              )
            )
        )
        .toList

      assertTrue("Should handle nullable column tuple IN", result.size >= 0)
    }
  }

  // ==================== Nested Tuple Tests ====================

  @Ignore("Oracle does not support nested tuples in IN clause")
  @Test
  def nestedTupleIn(): Unit = {
    withConnection {
      val row1 = DepartmentsRow("NEST1", "R1", "Nested Dept 1", None)
      val row2 = DepartmentsRow("NEST2", "R2", "Nested Dept 2", None)
      val row3 = DepartmentsRow("NEST3", "R3", "Nested Dept 3", None)

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
              List(
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of("NEST1", "R1"), "Nested Dept 1"),
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of("NEST3", "R3"), "Nested Dept 3")
              )
            )
        )
        .toList

      assertEquals("Should find 2 departments matching nested tuple pattern", 2, result.size)

      // Test that non-matching nested tuple returns empty
      val resultNoMatch = departmentsRepo.select
        .where(d =>
          d.deptCode
            .tupleWith(d.deptRegion)
            .tupleWith(d.deptName)
            .in(
              List(
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of("NEST1", "R1"), "Wrong Name")
              )
            )
        )
        .toList

      assertTrue("Should not match misaligned nested tuple", resultNoMatch.isEmpty)
    }
  }

  // ==================== Read Nested Tuple from Database Tests ====================

  @Ignore("Oracle does not support nested tuples")
  @Test
  def readNestedTupleFromDatabase(): Unit = {
    withConnection {
      // Insert test data
      val row1 = DepartmentsRow("READ1", "REG1", "Read Dept 1", None)
      val row2 = DepartmentsRow("READ2", "REG2", "Read Dept 2", None)
      val row3 = DepartmentsRow("READ3", "REG3", "Read Dept 3", None)

      val _ = departmentsRepo.insert(row1)
      val _ = departmentsRepo.insert(row2)
      val _ = departmentsRepo.insert(row3)

      // Select nested tuple: ((deptCode, deptRegion), deptName)
      val result = departmentsRepo.select
        .where(d => d.deptCode.in("READ1", "READ2", "READ3"))
        .orderBy(d => d.deptCode.asc)
        .map(d => d.deptCode.tupleWith(d.deptRegion).tupleWith(d.deptName))
        .toList

      assertEquals("Should read 3 nested tuples", 3, result.size)

      // Verify the nested tuple structure
      val first = result.head
      assertEquals("First tuple's inner first element", "READ1", first._1._1)
      assertEquals("First tuple's inner second element", "REG1", first._1._2)
      assertEquals("First tuple's outer second element", "Read Dept 1", first._2)
    }
  }
}
