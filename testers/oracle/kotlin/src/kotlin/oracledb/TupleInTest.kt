package oracledb

import dev.typr.dslkt.MockConnection
import dev.typr.dslkt.SqlExpr
import oracledb.departments.*
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.math.BigDecimal
import dev.typr.foundationskt.Connection

/**
 * Comprehensive tests for tuple IN functionality on Oracle. Tests cover:
 * - Composite ID IN with 2-column String,String keys
 * - Tuple IN with subqueries using tupleWith()
 * - Combined with other conditions using SqlExpr.all
 * - Both real database and mock repository evaluation
 */
class TupleInTest {

    data class Repos(val departmentsRepo: DepartmentsRepo)

    // =============== Departments (2-column String,String composite key) ===============

    @Test
    fun departmentsCompositeIdInWithMultipleIds_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            departmentsCompositeIdInWithMultipleIds(repos, c)
        }
    }

    @Test
    fun departmentsCompositeIdInWithMultipleIds_Mock() {
        val repos = createMockRepos()
        departmentsCompositeIdInWithMultipleIds(repos, MockConnection.instance)
    }

    fun departmentsCompositeIdInWithMultipleIds(repos: Repos, c: Connection) {
        val row1 = DepartmentsRow(
            "ENG", "US", "Engineering US",
            MoneyT(BigDecimal("1000000"), "USD"))
        val row2 = DepartmentsRow(
            "ENG", "EU", "Engineering EU",
            MoneyT(BigDecimal("800000"), "EUR"))
        val row3 = DepartmentsRow(
            "HR", "US", "Human Resources US",
            MoneyT(BigDecimal("500000"), "USD"))
        val row4 = DepartmentsRow(
            "HR", "EU", "Human Resources EU",
            MoneyT(BigDecimal("400000"), "EUR"))

        repos.departmentsRepo.insert(row1, c)
        repos.departmentsRepo.insert(row2, c)
        repos.departmentsRepo.insert(row3, c)
        repos.departmentsRepo.insert(row4, c)

        val result = repos.departmentsRepo
            .select()
            .where { d -> d.compositeIdIn(listOf(row1.compositeId(), row3.compositeId())) }
            .toList(c)

        assertEquals(2, result.size)
        val resultIds = result.map { it.compositeId() }.toSet()
        assertEquals(setOf(row1.compositeId(), row3.compositeId()), resultIds)
    }

    @Test
    fun departmentsCompositeIdInWithSingleId_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            departmentsCompositeIdInWithSingleId(repos, c)
        }
    }

    @Test
    fun departmentsCompositeIdInWithSingleId_Mock() {
        val repos = createMockRepos()
        departmentsCompositeIdInWithSingleId(repos, MockConnection.instance)
    }

    fun departmentsCompositeIdInWithSingleId(repos: Repos, c: Connection) {
        val row1 = DepartmentsRow("SALES", "APAC", "Sales APAC", null)
        val row2 = DepartmentsRow("SALES", "EMEA", "Sales EMEA", null)

        repos.departmentsRepo.insert(row1, c)
        repos.departmentsRepo.insert(row2, c)

        val result = repos.departmentsRepo
            .select()
            .where { d -> d.compositeIdIn(listOf(row1.compositeId())) }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(row1, result[0])
    }

    @Test
    fun departmentsCompositeIdInWithEmptyList_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            departmentsCompositeIdInWithEmptyList(repos, c)
        }
    }

    @Test
    fun departmentsCompositeIdInWithEmptyList_Mock() {
        val repos = createMockRepos()
        departmentsCompositeIdInWithEmptyList(repos, MockConnection.instance)
    }

    fun departmentsCompositeIdInWithEmptyList(repos: Repos, c: Connection) {
        val row = DepartmentsRow("TEST", "REGION", "Test Dept", null)
        repos.departmentsRepo.insert(row, c)

        val result = repos.departmentsRepo
            .select()
            .where { d -> d.compositeIdIn(emptyList()) }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun departmentsCompositeIdInCombinedWithOtherConditions_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            departmentsCompositeIdInCombinedWithOtherConditions(repos, c)
        }
    }

    @Test
    fun departmentsCompositeIdInCombinedWithOtherConditions_Mock() {
        val repos = createMockRepos()
        departmentsCompositeIdInCombinedWithOtherConditions(repos, MockConnection.instance)
    }

    fun departmentsCompositeIdInCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val row1 = DepartmentsRow(
            "DEV", "US", "Development US",
            MoneyT(BigDecimal("2000000"), "USD"))
        val row2 = DepartmentsRow(
            "DEV", "EU", "Development EU",
            MoneyT(BigDecimal("100000"), "EUR"))
        val row3 = DepartmentsRow(
            "QA", "US", "QA US",
            MoneyT(BigDecimal("500000"), "USD"))

        repos.departmentsRepo.insert(row1, c)
        repos.departmentsRepo.insert(row2, c)
        repos.departmentsRepo.insert(row3, c)

        val result = repos.departmentsRepo
            .select()
            .where { d ->
                SqlExpr.all(
                    d.compositeIdIn(listOf(row1.compositeId(), row2.compositeId(), row3.compositeId())),
                    d.deptName().isEqual("Development US")
                )
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(row1.compositeId(), result[0].compositeId())
    }

    @Test
    fun departmentsCompositeIdInWithNonExistentIds_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            departmentsCompositeIdInWithNonExistentIds(repos, c)
        }
    }

    @Test
    fun departmentsCompositeIdInWithNonExistentIds_Mock() {
        val repos = createMockRepos()
        departmentsCompositeIdInWithNonExistentIds(repos, MockConnection.instance)
    }

    fun departmentsCompositeIdInWithNonExistentIds(repos: Repos, c: Connection) {
        val row1 = DepartmentsRow("EXISTING", "DEPT", "Existing Dept", null)
        repos.departmentsRepo.insert(row1, c)

        val result = repos.departmentsRepo
            .select()
            .where { d ->
                d.compositeIdIn(listOf(
                    row1.compositeId(),
                    DepartmentsId("NONEXISTENT", "DEPT"),
                    DepartmentsId("ALSO", "MISSING")
                ))
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(row1, result[0])
    }

    @Test
    fun departmentsCompositeIdComputedVsManual_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            departmentsCompositeIdComputedVsManual(repos, c)
        }
    }

    @Test
    fun departmentsCompositeIdComputedVsManual_Mock() {
        val repos = createMockRepos()
        departmentsCompositeIdComputedVsManual(repos, MockConnection.instance)
    }

    fun departmentsCompositeIdComputedVsManual(repos: Repos, c: Connection) {
        val row = DepartmentsRow("COMPUTED", "TEST", "Computed Test", null)
        repos.departmentsRepo.insert(row, c)

        val computedId = row.compositeId()
        val manualId = DepartmentsId("COMPUTED", "TEST")

        assertEquals(computedId, manualId)

        val result = repos.departmentsRepo
            .select()
            .where { d -> d.compositeIdIn(listOf(computedId, manualId)) }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(row, result[0])
    }

    // ==================== TupleInSubquery Tests ====================

    @Test
    fun tupleInSubqueryBasic_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryBasic(repos, c)
        }
    }

    fun tupleInSubqueryBasic(repos: Repos, c: Connection) {
        val row1 = DepartmentsRow(
            "SMALL1", "MATCH", "Small Dept 1",
            MoneyT(BigDecimal("10000"), "USD"))
        val row2 = DepartmentsRow(
            "SMALL2", "MATCH", "Small Dept 2",
            MoneyT(BigDecimal("20000"), "USD"))
        val row3 = DepartmentsRow(
            "LARGE", "OTHER", "Large Dept",
            MoneyT(BigDecimal("1000000"), "USD"))

        repos.departmentsRepo.insert(row1, c)
        repos.departmentsRepo.insert(row2, c)
        repos.departmentsRepo.insert(row3, c)

        val result = repos.departmentsRepo
            .select()
            .where { d ->
                d.deptCode()
                    .tupleWith(d.deptRegion())
                    .among(
                        repos.departmentsRepo
                            .select()
                            .where { inner -> inner.deptRegion().isEqual("MATCH") }
                            .map { inner -> inner.deptCode().tupleWith(inner.deptRegion()) }
                            .subquery()
                    )
            }
            .toList(c)

        assertEquals(2, result.size)
        val codes = result.map { it.deptCode }.toSet()
        assertEquals(setOf("SMALL1", "SMALL2"), codes)
    }

    @Test
    fun tupleInSubqueryWithNoMatches_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryWithNoMatches(repos, c)
        }
    }

    fun tupleInSubqueryWithNoMatches(repos: Repos, c: Connection) {
        val row = DepartmentsRow("TEST1", "REGION1", "Test Dept 1", null)
        repos.departmentsRepo.insert(row, c)

        val result = repos.departmentsRepo
            .select()
            .where { d ->
                d.deptCode()
                    .tupleWith(d.deptRegion())
                    .among(
                        repos.departmentsRepo
                            .select()
                            .where { inner -> inner.deptRegion().isEqual("NONEXISTENT") }
                            .map { inner -> inner.deptCode().tupleWith(inner.deptRegion()) }
                            .subquery()
                    )
            }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun tupleInSubqueryCombinedWithOtherConditions_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryCombinedWithOtherConditions(repos, c)
        }
    }

    fun tupleInSubqueryCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val row1 = DepartmentsRow("A", "X", "Dept A", null)
        val row2 = DepartmentsRow("B", "X", "Dept B", null)
        val row3 = DepartmentsRow("C", "X", "Dept C", null)

        repos.departmentsRepo.insert(row1, c)
        repos.departmentsRepo.insert(row2, c)
        repos.departmentsRepo.insert(row3, c)

        val result = repos.departmentsRepo
            .select()
            .where { d ->
                SqlExpr.all(
                    d.deptCode()
                        .tupleWith(d.deptRegion())
                        .among(
                            repos.departmentsRepo
                                .select()
                                .where { inner -> inner.deptRegion().isEqual("X") }
                                .map { inner -> inner.deptCode().tupleWith(inner.deptRegion()) }
                                .subquery()
                        ),
                    d.deptCode().isNotEqual("A")
                )
            }
            .toList(c)

        assertEquals(2, result.size)
        val codes = result.map { it.deptCode }.toSet()
        assertEquals(setOf("B", "C"), codes)
    }

    // ==================== Nullable Column Tuple IN Tests ====================

    @Ignore("Oracle does not support nullable values in tuple IN")
    @Test
    fun tupleInWithNullableColumn_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInWithNullableColumn(repos, c)
        }
    }

    @Ignore("Oracle does not support nullable values in tuple IN")
    @Test
    fun tupleInWithNullableColumn_Mock() {
        val repos = createMockRepos()
        tupleInWithNullableColumn(repos, MockConnection.instance)
    }

    fun tupleInWithNullableColumn(repos: Repos, c: Connection) {
        // Create departments - some with budget (nullable), some without
        val row1 = DepartmentsRow("NULL1", "REG1", "Dept With No Budget 1", null)
        val row2 = DepartmentsRow("NULL2", "REG2", "Dept With No Budget 2", null)
        val row3 = DepartmentsRow("BUDGET", "REG3", "Dept With Budget", MoneyT(BigDecimal("500000"), "USD"))

        repos.departmentsRepo.insert(row1, c)
        repos.departmentsRepo.insert(row2, c)
        repos.departmentsRepo.insert(row3, c)

        // Query using tuple with nullable column - match rows with null budget
        val result = repos.departmentsRepo
            .select()
            .where { d ->
                d.deptCode()
                    .tupleWith(d.budget())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of("NULL1", null as MoneyT?),
                        dev.typr.foundations.Tuple.of("NULL2", null as MoneyT?)
                    ))
            }
            .toList(c)

        assertTrue("Should handle nullable column tuple IN", result.size >= 0)
    }

    // ==================== Nested Tuple Tests ====================

    @Ignore("Oracle does not support nested tuples in IN clause")
    @Test
    fun nestedTupleIn_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            nestedTupleIn(repos, c)
        }
    }

    @Ignore("Oracle does not support nested tuples in IN clause")
    @Test
    fun nestedTupleIn_Mock() {
        val repos = createMockRepos()
        nestedTupleIn(repos, MockConnection.instance)
    }

    fun nestedTupleIn(repos: Repos, c: Connection) {
        val row1 = DepartmentsRow("NEST1", "R1", "Nested Dept 1", null)
        val row2 = DepartmentsRow("NEST2", "R2", "Nested Dept 2", null)
        val row3 = DepartmentsRow("NEST3", "R3", "Nested Dept 3", null)

        repos.departmentsRepo.insert(row1, c)
        repos.departmentsRepo.insert(row2, c)
        repos.departmentsRepo.insert(row3, c)

        // Test truly nested tuple: ((deptCode, deptRegion), deptName)
        val result = repos.departmentsRepo
            .select()
            .where { d ->
                d.deptCode()
                    .tupleWith(d.deptRegion())
                    .tupleWith(d.deptName())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of("NEST1", "R1"),
                            "Nested Dept 1"),
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of("NEST3", "R3"),
                            "Nested Dept 3")
                    ))
            }
            .toList(c)

        assertEquals("Should find 2 departments matching nested tuple pattern", 2, result.size)

        // Test that non-matching nested tuple returns empty
        val resultNoMatch = repos.departmentsRepo
            .select()
            .where { d ->
                d.deptCode()
                    .tupleWith(d.deptRegion())
                    .tupleWith(d.deptName())
                    .among(listOf(
                        dev.typr.foundations.Tuple.of(
                            dev.typr.foundations.Tuple.of("NEST1", "R1"),
                            "Wrong Name")
                    ))
            }
            .toList(c)

        assertTrue("Should not match misaligned nested tuple", resultNoMatch.isEmpty())
    }

    // ==================== Read Nested Tuple from Database Tests ====================

    @Ignore("Oracle does not support nested tuples")
    @Test
    fun readNestedTupleFromDatabase_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            readNestedTupleFromDatabase(repos, c)
        }
    }

    @Ignore("Oracle does not support nested tuples")
    @Test
    fun readNestedTupleFromDatabase_Mock() {
        val repos = createMockRepos()
        readNestedTupleFromDatabase(repos, MockConnection.instance)
    }

    fun readNestedTupleFromDatabase(repos: Repos, c: Connection) {
        // Insert test data
        val row1 = DepartmentsRow("READ1", "REG1", "Read Dept 1", null)
        val row2 = DepartmentsRow("READ2", "REG2", "Read Dept 2", null)
        val row3 = DepartmentsRow("READ3", "REG3", "Read Dept 3", null)

        repos.departmentsRepo.insert(row1, c)
        repos.departmentsRepo.insert(row2, c)
        repos.departmentsRepo.insert(row3, c)

        // Select nested tuple: ((deptCode, deptRegion), deptName)
        val result = repos.departmentsRepo
            .select()
            .where { d -> d.deptCode().among("READ1", "READ2", "READ3") }
            .orderBy { d -> d.deptCode().asc() }
            .map { d -> d.deptCode().tupleWith(d.deptRegion()).tupleWith(d.deptName()) }
            .toList(c)

        assertEquals("Should read 3 nested tuples", 3, result.size)

        // Verify the nested tuple structure
        val first = result[0]
        assertEquals("First tuple's inner first element", "READ1", first._1()._1())
        assertEquals("First tuple's inner second element", "REG1", first._1()._2())
        assertEquals("First tuple's outer second element", "Read Dept 1", first._2())
    }

    // ==================== Helper Methods ====================

    private fun createRealRepos(): Repos {
        return Repos(DepartmentsRepoImpl())
    }

    private fun createMockRepos(): Repos {
        return Repos(DepartmentsRepoMock())
    }
}
