package adventureworks

import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.customtypes.TypoUUID
import adventureworks.person.businessentity.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import typo.dsl.SqlExpr
import typo.runtime.PgType
import java.time.LocalDateTime
import java.util.Optional

/**
 * Tests for seek-based pagination.
 */
class SeekDbTest {
    companion object {
        // Need to use timestamp type for seek comparisons, not text type
        private val timestampPgType: PgType<TypoLocalDateTime> = TypoLocalDateTime.pgType.withTypename("timestamp")
    }

    private fun testUniformSeek(businessentityRepo: BusinessentityRepo) {
        val limit = 3
        val now = LocalDateTime.of(2021, 1, 1, 0, 0)

        // Create rows with some duplicate modifieddate values
        val rows = mutableListOf<BusinessentityRow>()
        for (i in 0 until limit * 2) {
            // ensure some duplicate values
            val time = TypoLocalDateTime(now.minusDays((i % limit).toLong()))
            rows.add(BusinessentityRow(BusinessentityId(i), TypoUUID.randomUUID(), time))
        }

        // Sort to get expected groups - same as SQL: ORDER BY modifieddate ASC, businessentityid ASC
        val sortedRows = rows.sortedWith(
            compareBy<BusinessentityRow> { it.modifieddate.value }
                .thenBy { it.businessentityid.value }
        )

        val group1 = sortedRows.subList(0, limit)
        val group2 = sortedRows.subList(limit, limit * 2)

        WithConnection.run { c ->
            // batch insert some rows
            businessentityRepo.insertStreaming(rows.iterator(), 100, c)

            // First page - no seek values (DSL fields use method calls)
            val rows1 = businessentityRepo.select()
                .maybeSeek({ f -> f.modifieddate().asc() }, Optional.empty()) { v -> SqlExpr.ConstReq(v, timestampPgType) }
                .maybeSeek({ f -> f.businessentityid().asc() }, Optional.empty()) { v -> SqlExpr.ConstReq(v, BusinessentityId.pgType) }
                .maybeSeek({ f -> f.rowguid().asc() }, Optional.empty()) { v -> SqlExpr.ConstReq(v, TypoUUID.pgType) }
                .limit(limit)
                .toList(c)
            assertEquals(group1, rows1)

            // Second page - seek from last row of first page (row properties don't use ())
            val lastRow = rows1[rows1.size - 1]
            val rows2 = businessentityRepo.select()
                .maybeSeek({ f -> f.modifieddate().asc() }, Optional.of(lastRow.modifieddate)) { v -> SqlExpr.ConstReq(v, timestampPgType) }
                .maybeSeek({ f -> f.businessentityid().asc() }, Optional.of(lastRow.businessentityid)) { v -> SqlExpr.ConstReq(v, BusinessentityId.pgType) }
                .maybeSeek({ f -> f.rowguid().asc() }, Optional.of(lastRow.rowguid)) { v -> SqlExpr.ConstReq(v, TypoUUID.pgType) }
                .limit(limit)
                .toList(c)
            assertEquals(group2, rows2)
        }
    }

    @Test
    fun uniformInMemory() {
        // The mock's toRow converter isn't used because we insert fully-constructed rows
        testUniformSeek(BusinessentityRepoMock({ throw UnsupportedOperationException() }))
    }

    @Test
    fun uniformPg() {
        testUniformSeek(BusinessentityRepoImpl())
    }

    private fun testNonUniformSeek(businessentityRepo: BusinessentityRepo) {
        val limit = 3
        val now = LocalDateTime.of(2021, 1, 1, 0, 0)

        // Create rows with some duplicate modifieddate values
        val rows = mutableListOf<BusinessentityRow>()
        for (i in 0 until limit * 2) {
            // ensure some duplicate values
            val time = TypoLocalDateTime(now.minusDays((i % limit).toLong()))
            rows.add(BusinessentityRow(BusinessentityId(i), TypoUUID.randomUUID(), time))
        }

        // Sort to get expected groups - same as SQL: ORDER BY modifieddate DESC, businessentityid ASC
        val sortedRows = rows.sortedWith(
            compareByDescending<BusinessentityRow> { it.modifieddate.value }
                .thenBy { it.businessentityid.value }
        )

        val group1 = sortedRows.subList(0, limit)
        val group2 = sortedRows.subList(limit, limit * 2)

        WithConnection.run { c ->
            // batch insert some rows
            businessentityRepo.insertStreaming(rows.iterator(), 100, c)

            // First page - no seek values (descending modifieddate, ascending for rest)
            val rows1 = businessentityRepo.select()
                .maybeSeek({ f -> f.modifieddate().desc() }, Optional.empty()) { v -> SqlExpr.ConstReq(v, timestampPgType) }
                .maybeSeek({ f -> f.businessentityid().asc() }, Optional.empty()) { v -> SqlExpr.ConstReq(v, BusinessentityId.pgType) }
                .maybeSeek({ f -> f.rowguid().asc() }, Optional.empty()) { v -> SqlExpr.ConstReq(v, TypoUUID.pgType) }
                .limit(limit)
                .toList(c)
            assertEquals(group1, rows1)

            // Second page - seek from last row of first page
            val lastRow = rows1[rows1.size - 1]
            val rows2 = businessentityRepo.select()
                .maybeSeek({ f -> f.modifieddate().desc() }, Optional.of(lastRow.modifieddate)) { v -> SqlExpr.ConstReq(v, timestampPgType) }
                .maybeSeek({ f -> f.businessentityid().asc() }, Optional.of(lastRow.businessentityid)) { v -> SqlExpr.ConstReq(v, BusinessentityId.pgType) }
                .maybeSeek({ f -> f.rowguid().asc() }, Optional.of(lastRow.rowguid)) { v -> SqlExpr.ConstReq(v, TypoUUID.pgType) }
                .limit(limit)
                .toList(c)
            assertEquals(group2, rows2)
        }
    }

    @Test
    fun nonUniformInMemory() {
        // The mock's toRow converter isn't used because we insert fully-constructed rows
        testNonUniformSeek(BusinessentityRepoMock({ throw UnsupportedOperationException() }))
    }

    @Test
    fun nonUniformPg() {
        testNonUniformSeek(BusinessentityRepoImpl())
    }
}
