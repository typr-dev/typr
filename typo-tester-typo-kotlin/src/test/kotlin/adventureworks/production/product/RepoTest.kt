package adventureworks.production.product

import adventureworks.DbNow
import adventureworks.WithConnection
import java.time.LocalDateTime
import adventureworks.production.unitmeasure.*
import adventureworks.public.Name
import org.junit.Assert.assertEquals
import org.junit.Test

class RepoTest {
    private fun upsertStreaming(unitmeasureRepo: UnitmeasureRepo) {
        WithConnection.run { c ->
            val um1 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg1"), name = Name("name1"), modifieddate = DbNow.localDateTime())
            val um2 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg2"), name = Name("name2"), modifieddate = DbNow.localDateTime())
            unitmeasureRepo.upsertStreaming(listOf(um1, um2).iterator(), 1000, c)
            assertEquals(listOf(um1, um2), unitmeasureRepo.selectAll(c).sortedBy { it.name.value })
            val um1a = um1.copy(name = Name("name1a"))
            val um2a = um2.copy(name = Name("name2a"))
            unitmeasureRepo.upsertStreaming(listOf(um1a, um2a).iterator(), 1000, c)
            assertEquals(listOf(um1a, um2a), unitmeasureRepo.selectAll(c).sortedBy { it.name.value })
        }
    }

    private fun upsertBatch(unitmeasureRepo: UnitmeasureRepo) {
        WithConnection.run { c ->
            val um1 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg1"), name = Name("name1"), modifieddate = DbNow.localDateTime())
            val um2 = UnitmeasureRow(unitmeasurecode = UnitmeasureId("kg2"), name = Name("name2"), modifieddate = DbNow.localDateTime())
            val initial = unitmeasureRepo.upsertBatch(listOf(um1, um2).iterator(), c)
            assertEquals(listOf(um1, um2), initial.sortedBy { it.name.value })
            val um1a = um1.copy(name = Name("name1a"))
            val um2a = um2.copy(name = Name("name2a"))
            val returned = unitmeasureRepo.upsertBatch(listOf(um1a, um2a).iterator(), c)
            assertEquals(listOf(um1a, um2a), returned.sortedBy { it.name.value })
            val all = unitmeasureRepo.selectAll(c)
            assertEquals(listOf(um1a, um2a), all.sortedBy { it.name.value })
        }
    }

    @Test
    fun upsertStreamingInMemory() {
        upsertStreaming(UnitmeasureRepoMock(toRow = { it.toRow(modifieddateDefault = { DbNow.localDateTime() }) }))
    }

    @Test
    fun upsertStreamingPg() {
        upsertStreaming(UnitmeasureRepoImpl())
    }

    @Test
    fun upsertBatchInMemory() {
        upsertBatch(UnitmeasureRepoMock(toRow = { it.toRow(modifieddateDefault = { DbNow.localDateTime() }) }))
    }

    @Test
    fun upsertBatchPg() {
        upsertBatch(UnitmeasureRepoImpl())
    }
}
