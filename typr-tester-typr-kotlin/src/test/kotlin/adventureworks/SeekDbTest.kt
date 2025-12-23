package adventureworks

import adventureworks.DbNow
import java.util.UUID
import adventureworks.person.businessentity.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SeekDbTest {
    private fun testUniformSeek(businessentityRepo: BusinessentityRepo) {
        val limit = 3
        val now = LocalDateTime.of(2021, 1, 1, 0, 0)
        val rows = (0 until limit * 2).map { i ->
            // ensure some duplicate values
            val time = now.minusDays((i % limit).toLong())
            BusinessentityRow(BusinessentityId(i), UUID.randomUUID(), time)
        }

        // same as sql below
        val sorted = rows.sortedWith(compareBy({ it.modifieddate }, { it.businessentityid.value }))
        val group1 = sorted.take(limit)
        val group2 = sorted.drop(limit).take(limit)

        WithConnection.run { c ->
            // batch insert some rows
            businessentityRepo.insertStreaming(rows.iterator(), 1000, c)
            val rows1 = businessentityRepo.select()
                .maybeSeek({ it.modifieddate().asc() }, null)
                .maybeSeek({ it.businessentityid().asc() }, null)
                .maybeSeek({ it.rowguid().asc() }, null)
                .limit(limit)
                .toList(c)
            assertEquals(group1, rows1)
            val rows2 = businessentityRepo.select()
                .maybeSeek({ it.modifieddate().asc() }, rows1.last().modifieddate)
                .maybeSeek({ it.businessentityid().asc() }, rows1.last().businessentityid)
                .maybeSeek({ it.rowguid().asc() }, rows1.last().rowguid)
                .limit(limit)
                .toList(c)
            assertEquals(group2, rows2)
        }
    }

    @Test
    fun uniformInMemory() {
        testUniformSeek(BusinessentityRepoMock(toRow = { it.toRow(
            businessentityidDefault = { BusinessentityId(0) },
            rowguidDefault = { UUID.randomUUID() },
            modifieddateDefault = { DbNow.localDateTime() }
        ) }))
    }

    @Test
    fun uniformPg() {
        testUniformSeek(BusinessentityRepoImpl())
    }

    private fun testNonUniformSeek(businessentityRepo: BusinessentityRepo) {
        val limit = 3
        val now = LocalDateTime.of(2021, 1, 1, 0, 0)
        val rows = (0 until limit * 2).map { i ->
            // ensure some duplicate values
            val time = now.minusDays((i % limit).toLong())
            BusinessentityRow(BusinessentityId(i), UUID.randomUUID(), time)
        }

        // same as sql below
        val sorted = rows.sortedWith(compareBy({ -it.modifieddate.toEpochSecond(java.time.ZoneOffset.UTC) }, { it.businessentityid.value }))
        val group1 = sorted.take(limit)
        val group2 = sorted.drop(limit).take(limit)

        WithConnection.run { c ->
            // batch insert some rows
            businessentityRepo.insertStreaming(rows.iterator(), 1000, c)
            val rows1 = businessentityRepo.select()
                .maybeSeek({ it.modifieddate().desc() }, null)
                .maybeSeek({ it.businessentityid().asc() }, null)
                .maybeSeek({ it.rowguid().asc() }, null)
                .limit(limit)
                .toList(c)
            assertEquals(group1, rows1)
            val rows2 = businessentityRepo.select()
                .maybeSeek({ it.modifieddate().desc() }, rows1.last().modifieddate)
                .maybeSeek({ it.businessentityid().asc() }, rows1.last().businessentityid)
                .maybeSeek({ it.rowguid().asc() }, rows1.last().rowguid)
                .limit(limit)
                .toList(c)
            assertEquals(group2, rows2)
        }
    }

    @Test
    fun nonUniformInMemory() {
        testNonUniformSeek(BusinessentityRepoMock(toRow = { it.toRow(
            businessentityidDefault = { BusinessentityId(0) },
            rowguidDefault = { UUID.randomUUID() },
            modifieddateDefault = { DbNow.localDateTime() }
        ) }))
    }

    @Test
    fun nonUniformPg() {
        testNonUniformSeek(BusinessentityRepoImpl())
    }
}
