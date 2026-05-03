package testdb

import dev.typr.dslkt.MockConnection
import org.junit.Assert.*
import org.junit.Test
import testdb.mariatest_identity.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for mock repository implementations.
 */
class MockRepoTest {

    private fun createMockRepo(): Pair<MariatestIdentityRepoMock, AtomicInteger> {
        val idCounter = AtomicInteger(1)
        val mockRepo = MariatestIdentityRepoMock({ unsaved ->
            MariatestIdentityRow(
                id = MariatestIdentityId(idCounter.getAndIncrement()),
                name = unsaved.name
            )
        })
        return Pair(mockRepo, idCounter)
    }

    @Test
    fun testMockInsert() {
        val (mockRepo, _) = createMockRepo()

        val row1 = mockRepo.insert(MariatestIdentityRowUnsaved("Row 1"), MockConnection.instance)
        val row2 = mockRepo.insert(MariatestIdentityRowUnsaved("Row 2"), MockConnection.instance)

        assertNotNull(row1.id)
        assertNotNull(row2.id)
        assertNotEquals(row1.id, row2.id)
        assertEquals("Row 1", row1.name)
        assertEquals("Row 2", row2.name)
    }

    @Test
    fun testMockSelectById() {
        val (mockRepo, _) = createMockRepo()

        val inserted = mockRepo.insert(MariatestIdentityRowUnsaved("Test Row"), MockConnection.instance)

        val found = mockRepo.selectById(inserted.id, MockConnection.instance)
        assertNotNull(found)
        assertEquals(inserted.id, found!!.id)
        assertEquals("Test Row", found.name)
    }

    @Test
    fun testMockSelectByIdNotFound() {
        val (mockRepo, _) = createMockRepo()

        val found = mockRepo.selectById(MariatestIdentityId(999), MockConnection.instance)
        assertNull(found)
    }

    @Test
    fun testMockSelectAll() {
        val (mockRepo, _) = createMockRepo()

        mockRepo.insert(MariatestIdentityRowUnsaved("Row A"), MockConnection.instance)
        mockRepo.insert(MariatestIdentityRowUnsaved("Row B"), MockConnection.instance)
        mockRepo.insert(MariatestIdentityRowUnsaved("Row C"), MockConnection.instance)

        val all = mockRepo.selectAll(MockConnection.instance)
        assertEquals(3, all.size)
    }

    @Test
    fun testMockUpdate() {
        val (mockRepo, _) = createMockRepo()

        val inserted = mockRepo.insert(MariatestIdentityRowUnsaved("Original"), MockConnection.instance)
        val updated = inserted.copy(name = "Updated")

        val wasUpdated = mockRepo.update(updated, MockConnection.instance)
        assertTrue(wasUpdated)

        val found = mockRepo.selectById(inserted.id, MockConnection.instance)!!
        assertEquals("Updated", found.name)
    }

    @Test
    fun testMockUpdateNotFound() {
        val (mockRepo, _) = createMockRepo()

        val nonExistent = MariatestIdentityRow(MariatestIdentityId(999), "Test")
        val wasUpdated = mockRepo.update(nonExistent, MockConnection.instance)
        assertFalse(wasUpdated)
    }

    @Test
    fun testMockDelete() {
        val (mockRepo, _) = createMockRepo()

        val inserted = mockRepo.insert(MariatestIdentityRowUnsaved("To Delete"), MockConnection.instance)

        val deleted = mockRepo.deleteById(inserted.id, MockConnection.instance)
        assertTrue(deleted)

        val found = mockRepo.selectById(inserted.id, MockConnection.instance)
        assertNull(found)
    }

    @Test
    fun testMockDeleteNotFound() {
        val (mockRepo, _) = createMockRepo()

        val deleted = mockRepo.deleteById(MariatestIdentityId(999), MockConnection.instance)
        assertFalse(deleted)
    }

    @Test
    fun testMockUpsertInsert() {
        val (mockRepo, _) = createMockRepo()

        val newRow = MariatestIdentityRow(MariatestIdentityId(100), "New Row")
        val upserted = mockRepo.upsert(newRow, MockConnection.instance)

        assertEquals(MariatestIdentityId(100), upserted.id)
        assertEquals("New Row", upserted.name)

        val found = mockRepo.selectById(MariatestIdentityId(100), MockConnection.instance)
        assertNotNull(found)
    }

    @Test
    fun testMockUpsertUpdate() {
        val (mockRepo, _) = createMockRepo()

        val inserted = mockRepo.insert(MariatestIdentityRowUnsaved("Original"), MockConnection.instance)
        val toUpsert = inserted.copy(name = "Upserted")
        val upserted = mockRepo.upsert(toUpsert, MockConnection.instance)

        assertEquals(inserted.id, upserted.id)
        assertEquals("Upserted", upserted.name)
        assertEquals(1, mockRepo.selectAll(MockConnection.instance).size)
    }

    @Test
    fun testMockUpsertBatch() {
        val (mockRepo, _) = createMockRepo()

        val row1 = MariatestIdentityRow(MariatestIdentityId(1), "Row 1")
        val row2 = MariatestIdentityRow(MariatestIdentityId(2), "Row 2")

        mockRepo.upsertBatch(listOf(row1, row2).iterator(), MockConnection.instance)

        val all = mockRepo.selectAll(MockConnection.instance).sortedBy { it.id.value }
        assertEquals(2, all.size)
        assertEquals("Row 1", all[0].name)
        assertEquals("Row 2", all[1].name)

        val row1Updated = row1.copy(name = "Row 1 Updated")
        val row2Updated = row2.copy(name = "Row 2 Updated")

        mockRepo.upsertBatch(listOf(row1Updated, row2Updated).iterator(), MockConnection.instance)

        val allAfter = mockRepo.selectAll(MockConnection.instance).sortedBy { it.id.value }
        assertEquals(2, allAfter.size)
        assertEquals("Row 1 Updated", allAfter[0].name)
        assertEquals("Row 2 Updated", allAfter[1].name)
    }

    @Test
    fun testMockWithInterfacePolymorphism() {
        val idCounter = AtomicInteger(1)

        val repo: MariatestIdentityRepo = MariatestIdentityRepoMock({ unsaved ->
            MariatestIdentityRow(
                id = MariatestIdentityId(idCounter.getAndIncrement()),
                name = unsaved.name
            )
        })

        val inserted = repo.insert(MariatestIdentityRowUnsaved("Test"), MockConnection.instance)
        val found = repo.selectById(inserted.id, MockConnection.instance)

        assertNotNull(found)
        assertEquals("Test", found!!.name)
    }
}
