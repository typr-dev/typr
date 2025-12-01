package adventureworks.production.unitmeasure

import adventureworks.WithConnection
import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.public.Name
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests upsertStreaming and upsertBatch functionality.
 */
class RepoTest {

    private fun upsertStreaming(unitmeasureRepo: UnitmeasureRepo) {
        WithConnection.run { c ->
            val um1 = UnitmeasureRow(UnitmeasureId("kg1"), Name("name1"), TypoLocalDateTime.now())
            val um2 = UnitmeasureRow(UnitmeasureId("kg2"), Name("name2"), TypoLocalDateTime.now())
            val list1 = mutableListOf(um1, um2)
            unitmeasureRepo.upsertStreaming(list1.iterator(), 1000, c)

            val all1 = unitmeasureRepo.selectAll(c)
                .sortedBy { it.name.value }
            assertEquals(listOf(um1, um2), all1)

            val um1a = um1.copy(name = Name("name1a"))
            val um2a = um2.copy(name = Name("name2a"))
            val list2 = mutableListOf(um1a, um2a)
            unitmeasureRepo.upsertStreaming(list2.iterator(), 1000, c)

            val all2 = unitmeasureRepo.selectAll(c)
                .sortedBy { it.name.value }
            assertEquals(listOf(um1a, um2a), all2)
        }
    }

    private fun upsertBatch(unitmeasureRepo: UnitmeasureRepo) {
        WithConnection.run { c ->
            val um1 = UnitmeasureRow(UnitmeasureId("kg1"), Name("name1"), TypoLocalDateTime.now())
            val um2 = UnitmeasureRow(UnitmeasureId("kg2"), Name("name2"), TypoLocalDateTime.now())
            val list1 = mutableListOf(um1, um2)
            val initial = unitmeasureRepo.upsertBatch(list1.iterator(), c)
                .sortedBy { it.name.value }
            assertEquals(listOf(um1, um2), initial)

            val um1a = um1.copy(name = Name("name1a"))
            val um2a = um2.copy(name = Name("name2a"))
            val list2 = mutableListOf(um1a, um2a)
            val returned = unitmeasureRepo.upsertBatch(list2.iterator(), c)
                .sortedBy { it.name.value }
            assertEquals(listOf(um1a, um2a), returned)

            val all = unitmeasureRepo.selectAll(c)
                .sortedBy { it.name.value }
            assertEquals(listOf(um1a, um2a), all)
        }
    }

    @Test
    fun upsertStreamingInMemory() {
        upsertStreaming(UnitmeasureRepoMock({ unsaved -> unsaved.toRow { TypoLocalDateTime.now() } }))
    }

    @Test
    fun upsertStreamingPg() {
        upsertStreaming(UnitmeasureRepoImpl())
    }

    @Test
    fun upsertBatchInMemory() {
        upsertBatch(UnitmeasureRepoMock({ unsaved -> unsaved.toRow { TypoLocalDateTime.now() } }))
    }

    @Test
    fun upsertBatchPg() {
        upsertBatch(UnitmeasureRepoImpl())
    }
}
