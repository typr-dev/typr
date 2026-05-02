package adventureworks

import adventureworks.public.ShortText
import adventureworks.public.flaff.*
import adventureworks.public.only_pk_columns.*
import dev.typr.dslkt.MockConnection
import dev.typr.dslkt.SqlExpr
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import dev.typr.foundationskt.Connection

/**
 * Comprehensive tests for tuple IN functionality on PostgreSQL.
 * Tests cover:
 * - Composite ID IN with various key sizes (2, 4 columns)
 * - Computed vs manually created composite IDs
 * - Tuple IN with subqueries using tupleWith()
 * - Combined with other conditions using SqlExpr.all
 * - Both real database and mock repository evaluation
 */
class TupleInTest {

    data class Repos(
        val onlyPkColumnsRepo: OnlyPkColumnsRepo,
        val flaffRepo: FlaffRepo
    )

    // ==================== Composite ID tests (2-column) ====================

    @Test
    fun compositeIdInWithMultipleIds_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            compositeIdInWithMultipleIds(repos, c)
        }
    }

    @Test
    fun compositeIdInWithMultipleIds_Mock() {
        val repos = createMockRepos()
        compositeIdInWithMultipleIds(repos, MockConnection.instance)
    }

    fun compositeIdInWithMultipleIds(repos: Repos, c: Connection) {
        val row1 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("A", 1), c)
        val row2 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("B", 2), c)
        val row3 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("C", 3), c)
        val row4 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("D", 4), c)

        val result = repos.onlyPkColumnsRepo
            .select()
            .where { r -> r.compositeIdIn(listOf(row1.compositeId(), row3.compositeId())) }
            .toList(c)

        assertEquals(2, result.size)
        val resultIds = result.map { it.compositeId() }.toSet()
        assertEquals(setOf(row1.compositeId(), row3.compositeId()), resultIds)
    }

    @Test
    fun compositeIdInWithSingleId_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            compositeIdInWithSingleId(repos, c)
        }
    }

    @Test
    fun compositeIdInWithSingleId_Mock() {
        val repos = createMockRepos()
        compositeIdInWithSingleId(repos, MockConnection.instance)
    }

    fun compositeIdInWithSingleId(repos: Repos, c: Connection) {
        val row1 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("SINGLE", 100), c)
        repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("OTHER", 200), c)

        val result = repos.onlyPkColumnsRepo
            .select()
            .where { r -> r.compositeIdIn(listOf(row1.compositeId())) }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(row1, result[0])
    }

    @Test
    fun compositeIdInWithEmptyList_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            compositeIdInWithEmptyList(repos, c)
        }
    }

    @Test
    fun compositeIdInWithEmptyList_Mock() {
        val repos = createMockRepos()
        compositeIdInWithEmptyList(repos, MockConnection.instance)
    }

    fun compositeIdInWithEmptyList(repos: Repos, c: Connection) {
        repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("TEST", 999), c)

        val result = repos.onlyPkColumnsRepo
            .select()
            .where { r -> r.compositeIdIn(emptyList()) }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun compositeIdInWithComputedVsManual_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            compositeIdInWithComputedVsManual(repos, c)
        }
    }

    @Test
    fun compositeIdInWithComputedVsManual_Mock() {
        val repos = createMockRepos()
        compositeIdInWithComputedVsManual(repos, MockConnection.instance)
    }

    fun compositeIdInWithComputedVsManual(repos: Repos, c: Connection) {
        val row1 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("A", 1), c)
        val row2 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("B", 2), c)
        val row3 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("A", 3), c)

        val computedId = row1.compositeId()
        val manualId = OnlyPkColumnsId("A", 1)

        assertEquals(computedId, manualId)

        val result = repos.onlyPkColumnsRepo
            .select()
            .where { r ->
                r.compositeIdIn(listOf(
                    computedId,
                    manualId,
                    OnlyPkColumnsId("B", 2),
                    row3.compositeId()
                ))
            }
            .toList(c)

        assertEquals(3, result.size)
    }

    @Test
    fun compositeIdInWithNonExistentIds_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            compositeIdInWithNonExistentIds(repos, c)
        }
    }

    @Test
    fun compositeIdInWithNonExistentIds_Mock() {
        val repos = createMockRepos()
        compositeIdInWithNonExistentIds(repos, MockConnection.instance)
    }

    fun compositeIdInWithNonExistentIds(repos: Repos, c: Connection) {
        val row1 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("EXISTING", 1), c)

        val result = repos.onlyPkColumnsRepo
            .select()
            .where { r ->
                r.compositeIdIn(listOf(
                    row1.compositeId(),
                    OnlyPkColumnsId("NONEXISTENT", 999),
                    OnlyPkColumnsId("ALSO", 888)
                ))
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(row1, result[0])
    }

    @Test
    fun compositeIdInWithLargeList_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            compositeIdInWithLargeList(repos, c)
        }
    }

    @Test
    fun compositeIdInWithLargeList_Mock() {
        val repos = createMockRepos()
        compositeIdInWithLargeList(repos, MockConnection.instance)
    }

    fun compositeIdInWithLargeList(repos: Repos, c: Connection) {
        val insertedRows = (0 until 20).map { i ->
            repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("KEY$i", i), c)
        }

        val idsToSelect = insertedRows
            .filter { it.keyColumn2 % 2 == 0 }
            .map { it.compositeId() }

        val result = repos.onlyPkColumnsRepo
            .select()
            .where { r -> r.compositeIdIn(idsToSelect) }
            .toList(c)

        assertEquals(10, result.size)
    }

    // ==================== 4-Column Composite ID (Flaff table) ====================

    @Test
    fun compositeIdInWith4ColumnKey_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            compositeIdInWith4ColumnKey(repos, c)
        }
    }

    @Test
    fun compositeIdInWith4ColumnKey_Mock() {
        val repos = createMockRepos()
        compositeIdInWith4ColumnKey(repos, MockConnection.instance)
    }

    fun compositeIdInWith4ColumnKey(repos: Repos, c: Connection) {
        val row1 = repos.flaffRepo.insert(
            FlaffRow(ShortText("CODE1"), "OTHER1", 100, ShortText("SPEC1"), null), c)
        val row2 = repos.flaffRepo.insert(
            FlaffRow(ShortText("CODE2"), "OTHER2", 200, ShortText("SPEC2"), null), c)
        val row3 = repos.flaffRepo.insert(
            FlaffRow(ShortText("CODE1"), "OTHER1", 100, ShortText("SPEC2"), null), c)
        val row4 = repos.flaffRepo.insert(
            FlaffRow(ShortText("CODE3"), "OTHER3", 300, ShortText("SPEC3"), null), c)

        val result = repos.flaffRepo
            .select()
            .where { f ->
                f.compositeIdIn(listOf(
                    row1.compositeId(),
                    FlaffId(ShortText("CODE2"), "OTHER2", 200, ShortText("SPEC2")),
                    row4.compositeId()
                ))
            }
            .toList(c)

        assertEquals(3, result.size)
        val specifiers = result.map { it.specifier.value }.toSet()
        assertEquals(setOf("SPEC1", "SPEC2", "SPEC3"), specifiers)
    }

    @Test
    fun compositeIdInWith4ColumnKeyComputedVsManual_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            compositeIdInWith4ColumnKeyComputedVsManual(repos, c)
        }
    }

    @Test
    fun compositeIdInWith4ColumnKeyComputedVsManual_Mock() {
        val repos = createMockRepos()
        compositeIdInWith4ColumnKeyComputedVsManual(repos, MockConnection.instance)
    }

    fun compositeIdInWith4ColumnKeyComputedVsManual(repos: Repos, c: Connection) {
        val row = repos.flaffRepo.insert(
            FlaffRow(ShortText("COMP"), "MANUAL", 999, ShortText("TEST"), null), c)

        val computedId = row.compositeId()
        val manualId = FlaffId(ShortText("COMP"), "MANUAL", 999, ShortText("TEST"))

        assertEquals(computedId, manualId)

        val result = repos.flaffRepo
            .select()
            .where { f -> f.compositeIdIn(listOf(computedId, manualId)) }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals(row, result[0])
    }

    // ==================== Combined with other conditions ====================

    @Test
    fun compositeIdInCombinedWithOtherConditions_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            compositeIdInCombinedWithOtherConditions(repos, c)
        }
    }

    @Test
    fun compositeIdInCombinedWithOtherConditions_Mock() {
        val repos = createMockRepos()
        compositeIdInCombinedWithOtherConditions(repos, MockConnection.instance)
    }

    fun compositeIdInCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val row1 = repos.flaffRepo.insert(
            FlaffRow(ShortText("A"), "X", 1, ShortText("S1"), null), c)
        val row2 = repos.flaffRepo.insert(
            FlaffRow(ShortText("B"), "X", 2, ShortText("S2"), null), c)
        val row3 = repos.flaffRepo.insert(
            FlaffRow(ShortText("C"), "X", 3, ShortText("S3"), null), c)

        val result = repos.flaffRepo
            .select()
            .where { f ->
                SqlExpr.all(
                    f.compositeIdIn(listOf(row1.compositeId(), row2.compositeId(), row3.compositeId())),
                    f.specifier().isEqual(ShortText("S2"))
                )
            }
            .toList(c)

        assertEquals(1, result.size)
        assertEquals("S2", result[0].specifier.value)
    }

    // ==================== TupleInSubquery tests ====================

    @Test
    fun tupleInSubqueryBasic_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryBasic(repos, c)
        }
    }

    fun tupleInSubqueryBasic(repos: Repos, c: Connection) {
        val row1 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("MATCH1", 1), c)
        val row2 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("MATCH2", 2), c)
        val row3 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("NOMATCH", 99), c)

        val result = repos.onlyPkColumnsRepo
            .select()
            .where { r ->
                r.keyColumn1()
                    .tupleWith(r.keyColumn2())
                    .among(
                        repos.onlyPkColumnsRepo
                            .select()
                            .where { inner -> inner.keyColumn2().lessThan(10) }
                            .map { inner -> inner.keyColumn1().tupleWith(inner.keyColumn2()) }
                            .subquery()
                    )
            }
            .toList(c)

        assertEquals(2, result.size)
        val resultKeys = result.map { it.keyColumn1 }.toSet()
        assertEquals(setOf("MATCH1", "MATCH2"), resultKeys)
    }

    @Test
    fun tupleInSubqueryWithNoMatches_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryWithNoMatches(repos, c)
        }
    }

    fun tupleInSubqueryWithNoMatches(repos: Repos, c: Connection) {
        repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("TEST1", 100), c)
        repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("TEST2", 200), c)

        val result = repos.onlyPkColumnsRepo
            .select()
            .where { r ->
                r.keyColumn1()
                    .tupleWith(r.keyColumn2())
                    .among(
                        repos.onlyPkColumnsRepo
                            .select()
                            .where { inner -> inner.keyColumn2().lessThan(0) }
                            .map { inner -> inner.keyColumn1().tupleWith(inner.keyColumn2()) }
                            .subquery()
                    )
            }
            .toList(c)

        assertEquals(0, result.size)
    }

    @Test
    fun tupleInSubqueryCombinedWithOtherConditions_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            tupleInSubqueryCombinedWithOtherConditions(repos, c)
        }
    }

    fun tupleInSubqueryCombinedWithOtherConditions(repos: Repos, c: Connection) {
        val row1 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("A", 1), c)
        val row2 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("B", 2), c)
        val row3 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("C", 3), c)

        val result = repos.onlyPkColumnsRepo
            .select()
            .where { r ->
                SqlExpr.all(
                    r.keyColumn1()
                        .tupleWith(r.keyColumn2())
                        .among(
                            repos.onlyPkColumnsRepo
                                .select()
                                .where { inner -> inner.keyColumn2().lessThan(10) }
                                .map { inner -> inner.keyColumn1().tupleWith(inner.keyColumn2()) }
                                .subquery()
                        ),
                    r.keyColumn1().isNotEqual("A")
                )
            }
            .toList(c)

        assertEquals(2, result.size)
        val resultKeys = result.map { it.keyColumn1 }.toSet()
        assertEquals(setOf("B", "C"), resultKeys)
    }

    // ==================== Nullable Column Tuple IN Tests ====================

    @Ignore("Nullable columns in tuples not yet supported")
    @Test
    fun tupleInWithNullableColumn_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            tupleInWithNullableColumn(repos, c)
        }
    }

    @Ignore("Nullable columns in tuples not yet supported")
    @Test
    fun tupleInWithNullableColumn_Mock() {
        val repos = createMockRepos()
        tupleInWithNullableColumn(repos, MockConnection.instance)
    }

    fun tupleInWithNullableColumn(repos: Repos, c: Connection) {
        // Create flaff rows - some with parent (nullable), some without
        val row1 = repos.flaffRepo.insert(
            FlaffRow(ShortText("NULL1"), "NULL_P", 1, ShortText("S1"), null), c)
        val row2 = repos.flaffRepo.insert(
            FlaffRow(ShortText("NULL2"), "NULL_P", 2, ShortText("S2"), null), c)
        val row3 = repos.flaffRepo.insert(
            FlaffRow(ShortText("HAS"), "HAS_P", 3, ShortText("S3"), ShortText("Has parent")), c)

        // TODO: Re-enable when nullable columns in tuples are supported
        // Query using tuple with nullable column - match rows with null parent
        // val result = repos.flaffRepo
        //     .select()
        //     .where { f ->
        //         f.code()
        //             .tupleWith(f.parent())
        //             .among(listOf(
        //                 dev.typr.foundations.Tuple.of(ShortText("NULL1"), null as String?),
        //                 dev.typr.foundations.Tuple.of(ShortText("NULL2"), null as String?)
        //             ))
        //     }
        //     .toList(c)
        // assertTrue("Should handle nullable column tuple IN", result.size >= 0)
    }

    // ==================== Nested Tuple Tests ====================

    @Ignore("Nested tuples not yet supported")
    @Test
    fun nestedTupleIn_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            nestedTupleIn(repos, c)
        }
    }

    @Ignore("Nested tuples not yet supported")
    @Test
    fun nestedTupleIn_Mock() {
        val repos = createMockRepos()
        nestedTupleIn(repos, MockConnection.instance)
    }

    fun nestedTupleIn(repos: Repos, c: Connection) {
        val row1 = repos.flaffRepo.insert(
            FlaffRow(ShortText("NEST1"), "OTHER1", 100, ShortText("NS1"), null), c)
        val row2 = repos.flaffRepo.insert(
            FlaffRow(ShortText("NEST2"), "OTHER2", 200, ShortText("NS2"), null), c)
        val row3 = repos.flaffRepo.insert(
            FlaffRow(ShortText("NEST3"), "OTHER3", 300, ShortText("NS3"), null), c)

        // TODO: Re-enable when nested tuples are supported
        // Test truly nested tuple: ((code, other), specifier)
        // val result = repos.flaffRepo
        //     .select()
        //     .where { f ->
        //         f.code()
        //             .tupleWith(f.other())
        //             .tupleWith(f.specifier())
        //             .among(listOf(
        //                 dev.typr.foundations.Tuple.of(
        //                     dev.typr.foundations.Tuple.of(ShortText("NEST1"), "OTHER1"),
        //                     ShortText("NS1")),
        //                 dev.typr.foundations.Tuple.of(
        //                     dev.typr.foundations.Tuple.of(ShortText("NEST3"), "OTHER3"),
        //                     ShortText("NS3"))
        //             ))
        //     }
        //     .toList(c)
        // assertEquals("Should find 2 flaff rows matching nested tuple pattern", 2, result.size)

        // Test that non-matching nested tuple returns empty
        // val resultNoMatch = repos.flaffRepo
        //     .select()
        //     .where { f ->
        //         f.code()
        //             .tupleWith(f.other())
        //             .tupleWith(f.specifier())
        //             .among(listOf(
        //                 dev.typr.foundations.Tuple.of(
        //                     dev.typr.foundations.Tuple.of(ShortText("NEST1"), "OTHER1"),
        //                     ShortText("WRONG_SPEC"))
        //             ))
        //     }
        //     .toList(c)
        // assertTrue("Should not match misaligned nested tuple", resultNoMatch.isEmpty())
    }

    // ==================== Read Nested Tuple from Database Tests ====================

    @Ignore("Nested tuples not yet supported")
    @Test
    fun readNestedTupleFromDatabase_Real() {
        WithConnection.run { c ->
            val repos = createRealRepos()
            readNestedTupleFromDatabase(repos, c)
        }
    }

    @Ignore("Nested tuples not yet supported")
    @Test
    fun readNestedTupleFromDatabase_Mock() {
        val repos = createMockRepos()
        readNestedTupleFromDatabase(repos, MockConnection.instance)
    }

    fun readNestedTupleFromDatabase(repos: Repos, c: Connection) {
        // Insert test data
        val row1 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("READ_A", 10), c)
        val row2 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("READ_B", 20), c)
        val row3 = repos.onlyPkColumnsRepo.insert(OnlyPkColumnsRow("READ_C", 30), c)

        // TODO: Re-enable when nested tuples are supported
        // Select nested tuple: ((keyColumn1, keyColumn2), keyColumn1)
        // val result = repos.onlyPkColumnsRepo
        //     .select()
        //     .where { r -> r.keyColumn1().among("READ_A", "READ_B", "READ_C") }
        //     .orderBy { r -> r.keyColumn2().asc() }
        //     .map { r -> r.keyColumn1().tupleWith(r.keyColumn2()).tupleWith(r.keyColumn1()) }
        //     .toList(c)
        // assertEquals("Should read 3 nested tuples", 3, result.size)

        // Verify the nested tuple structure
        // val first = result[0]
        // assertEquals("First tuple's inner first element", "READ_A", first._1()._1())
        // assertEquals("First tuple's inner second element", 10, first._1()._2())
        // assertEquals("First tuple's outer second element", "READ_A", first._2())

        // val second = result[1]
        // assertEquals("Second tuple's inner first element", "READ_B", second._1()._1())
        // assertEquals("Second tuple's inner second element", 20, second._1()._2())
        // assertEquals("Second tuple's outer second element", "READ_B", second._2())
    }

    // ==================== Helper Methods ====================

    private fun createRealRepos(): Repos {
        return Repos(OnlyPkColumnsRepoImpl(), FlaffRepoImpl())
    }

    private fun createMockRepos(): Repos {
        return Repos(OnlyPkColumnsRepoMock(), FlaffRepoMock())
    }
}
