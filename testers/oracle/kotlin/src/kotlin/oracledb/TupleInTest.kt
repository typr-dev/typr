package oracledb

import dev.typr.foundations.dsl.SqlExpr
import oracledb.departments.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.sql.Connection
import java.util.Optional

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
        departmentsCompositeIdInWithMultipleIds(repos, null)
    }

    fun departmentsCompositeIdInWithMultipleIds(repos: Repos, c: Connection?) {
        val row1 = DepartmentsRow(
            "ENG", "US", "Engineering US",
            Optional.of(MoneyT(BigDecimal("1000000"), "USD")))
        val row2 = DepartmentsRow(
            "ENG", "EU", "Engineering EU",
            Optional.of(MoneyT(BigDecimal("800000"), "EUR")))
        val row3 = DepartmentsRow(
            "HR", "US", "Human Resources US",
            Optional.of(MoneyT(BigDecimal("500000"), "USD")))
        val row4 = DepartmentsRow(
            "HR", "EU", "Human Resources EU",
            Optional.of(MoneyT(BigDecimal("400000"), "EUR")))

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
        departmentsCompositeIdInWithSingleId(repos, null)
    }

    fun departmentsCompositeIdInWithSingleId(repos: Repos, c: Connection?) {
        val row1 = DepartmentsRow("SALES", "APAC", "Sales APAC", Optional.empty())
        val row2 = DepartmentsRow("SALES", "EMEA", "Sales EMEA", Optional.empty())

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
        departmentsCompositeIdInWithEmptyList(repos, null)
    }

    fun departmentsCompositeIdInWithEmptyList(repos: Repos, c: Connection?) {
        val row = DepartmentsRow("TEST", "REGION", "Test Dept", Optional.empty())
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
        departmentsCompositeIdInCombinedWithOtherConditions(repos, null)
    }

    fun departmentsCompositeIdInCombinedWithOtherConditions(repos: Repos, c: Connection?) {
        val row1 = DepartmentsRow(
            "DEV", "US", "Development US",
            Optional.of(MoneyT(BigDecimal("2000000"), "USD")))
        val row2 = DepartmentsRow(
            "DEV", "EU", "Development EU",
            Optional.of(MoneyT(BigDecimal("100000"), "EUR")))
        val row3 = DepartmentsRow(
            "QA", "US", "QA US",
            Optional.of(MoneyT(BigDecimal("500000"), "USD")))

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
        departmentsCompositeIdInWithNonExistentIds(repos, null)
    }

    fun departmentsCompositeIdInWithNonExistentIds(repos: Repos, c: Connection?) {
        val row1 = DepartmentsRow("EXISTING", "DEPT", "Existing Dept", Optional.empty())
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
        departmentsCompositeIdComputedVsManual(repos, null)
    }

    fun departmentsCompositeIdComputedVsManual(repos: Repos, c: Connection?) {
        val row = DepartmentsRow("COMPUTED", "TEST", "Computed Test", Optional.empty())
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

    fun tupleInSubqueryBasic(repos: Repos, c: Connection?) {
        val row1 = DepartmentsRow(
            "SMALL1", "MATCH", "Small Dept 1",
            Optional.of(MoneyT(BigDecimal("10000"), "USD")))
        val row2 = DepartmentsRow(
            "SMALL2", "MATCH", "Small Dept 2",
            Optional.of(MoneyT(BigDecimal("20000"), "USD")))
        val row3 = DepartmentsRow(
            "LARGE", "OTHER", "Large Dept",
            Optional.of(MoneyT(BigDecimal("1000000"), "USD")))

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
        val codes = result.map { it.deptCode() }.toSet()
        assertEquals(setOf("SMALL1", "SMALL2"), codes)
    }

    @Test
    fun tupleInSubqueryWithNoMatches_Real() {
        OracleTestHelper.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryWithNoMatches(repos, c)
        }
    }

    fun tupleInSubqueryWithNoMatches(repos: Repos, c: Connection?) {
        val row = DepartmentsRow("TEST1", "REGION1", "Test Dept 1", Optional.empty())
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

    fun tupleInSubqueryCombinedWithOtherConditions(repos: Repos, c: Connection?) {
        val row1 = DepartmentsRow("A", "X", "Dept A", Optional.empty())
        val row2 = DepartmentsRow("B", "X", "Dept B", Optional.empty())
        val row3 = DepartmentsRow("C", "X", "Dept C", Optional.empty())

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
        val codes = result.map { it.deptCode() }.toSet()
        assertEquals(setOf("B", "C"), codes)
    }

    // ==================== Helper Methods ====================

    private fun createRealRepos(): Repos {
        return Repos(DepartmentsRepoImpl())
    }

    private fun createMockRepos(): Repos {
        return Repos(DepartmentsRepoMock())
    }
}
