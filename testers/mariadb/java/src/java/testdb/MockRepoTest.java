package testdb;

import static org.junit.Assert.*;

import dev.typr.dsl.MockConnection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import testdb.mariatest_identity.*;

/**
 * Tests for Mock repository implementations. Mock repos can be used for in-memory testing without a
 * database connection.
 */
public class MockRepoTest {

  @Test
  public void testMockInsert() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    // Insert without connection (mock doesn't need one)
    var row1 = mockRepo.insert(new MariatestIdentityRowUnsaved("Row 1"), null);
    var row2 = mockRepo.insert(new MariatestIdentityRowUnsaved("Row 2"), null);

    assertNotNull(row1.id());
    assertNotNull(row2.id());
    assertNotEquals(row1.id(), row2.id());
    assertEquals("Row 1", row1.name());
    assertEquals("Row 2", row2.name());
  }

  @Test
  public void testMockSelectById() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var inserted = mockRepo.insert(new MariatestIdentityRowUnsaved("Test Row"), null);

    var found = mockRepo.selectById(inserted.id(), null);
    assertTrue(found.isPresent());
    assertEquals(inserted.id(), found.get().id());
    assertEquals("Test Row", found.get().name());
  }

  @Test
  public void testMockSelectByIdNotFound() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var found = mockRepo.selectById(new MariatestIdentityId(999), null);
    assertFalse(found.isPresent());
  }

  @Test
  public void testMockSelectAll() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    mockRepo.insert(new MariatestIdentityRowUnsaved("Row A"), null);
    mockRepo.insert(new MariatestIdentityRowUnsaved("Row B"), null);
    mockRepo.insert(new MariatestIdentityRowUnsaved("Row C"), null);

    List<MariatestIdentityRow> all = mockRepo.selectAll(null);
    assertEquals(3, all.size());
  }

  @Test
  public void testMockUpdate() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var inserted = mockRepo.insert(new MariatestIdentityRowUnsaved("Original"), null);
    var updated = inserted.withName("Updated");

    boolean wasUpdated = mockRepo.update(updated, null);
    assertTrue(wasUpdated);

    var found = mockRepo.selectById(inserted.id(), null).orElseThrow();
    assertEquals("Updated", found.name());
  }

  @Test
  public void testMockUpdateNotFound() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var nonExistent = new MariatestIdentityRow(new MariatestIdentityId(999), "Test");
    boolean wasUpdated = mockRepo.update(nonExistent, null);
    assertFalse(wasUpdated);
  }

  @Test
  public void testMockDelete() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var inserted = mockRepo.insert(new MariatestIdentityRowUnsaved("To Delete"), null);

    boolean deleted = mockRepo.deleteById(inserted.id(), null);
    assertTrue(deleted);

    var found = mockRepo.selectById(inserted.id(), null);
    assertFalse(found.isPresent());
  }

  @Test
  public void testMockDeleteNotFound() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    boolean deleted = mockRepo.deleteById(new MariatestIdentityId(999), null);
    assertFalse(deleted);
  }

  @Test
  public void testMockUpsertInsert() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var newRow = new MariatestIdentityRow(new MariatestIdentityId(100), "New Row");
    var upserted = mockRepo.upsert(newRow, null);

    assertEquals(new MariatestIdentityId(100), upserted.id());
    assertEquals("New Row", upserted.name());

    // Verify it's in the mock storage
    var found = mockRepo.selectById(new MariatestIdentityId(100), null);
    assertTrue(found.isPresent());
  }

  @Test
  public void testMockUpsertUpdate() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var inserted = mockRepo.insert(new MariatestIdentityRowUnsaved("Original"), null);
    var toUpsert = inserted.withName("Upserted");
    var upserted = mockRepo.upsert(toUpsert, null);

    assertEquals(inserted.id(), upserted.id());
    assertEquals("Upserted", upserted.name());

    // Only one row should exist
    assertEquals(1, mockRepo.selectAll(null).size());
  }

  @Test
  public void testMockUpsertStreaming() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var row1 = new MariatestIdentityRow(new MariatestIdentityId(1), "Row 1");
    var row2 = new MariatestIdentityRow(new MariatestIdentityId(2), "Row 2");

    mockRepo.upsertBatch(List.of(row1, row2).iterator(), MockConnection.instance);

    var all =
        mockRepo.selectAll(null).stream()
            .sorted(Comparator.comparing(r -> r.id().value()))
            .toList();
    assertEquals(2, all.size());
    assertEquals("Row 1", all.get(0).name());
    assertEquals("Row 2", all.get(1).name());

    // Update via upsertStreaming
    var row1Updated = row1.withName("Row 1 Updated");
    var row2Updated = row2.withName("Row 2 Updated");

    mockRepo.upsertBatch(List.of(row1Updated, row2Updated).iterator(), MockConnection.instance);

    all =
        mockRepo.selectAll(null).stream()
            .sorted(Comparator.comparing(r -> r.id().value()))
            .toList();
    assertEquals(2, all.size());
    assertEquals("Row 1 Updated", all.get(0).name());
    assertEquals("Row 2 Updated", all.get(1).name());
  }

  @Test
  public void testMockUpsertBatch() {
    AtomicInteger idCounter = new AtomicInteger(1);
    var mockRepo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var row1 = new MariatestIdentityRow(new MariatestIdentityId(1), "Row 1");
    var row2 = new MariatestIdentityRow(new MariatestIdentityId(2), "Row 2");

    var result = mockRepo.upsertBatch(List.of(row1, row2).iterator(), null);
    assertEquals(2, result.size());

    // Verify they're stored
    assertEquals(2, mockRepo.selectAll(null).size());

    // Update via upsertBatch and verify returned rows
    var row1Updated = row1.withName("Row 1 Updated");
    var row2Updated = row2.withName("Row 2 Updated");

    var returned =
        mockRepo.upsertBatch(List.of(row1Updated, row2Updated).iterator(), null).stream()
            .sorted(Comparator.comparing(r -> r.id().value()))
            .toList();
    assertEquals(2, returned.size());
    assertEquals("Row 1 Updated", returned.get(0).name());
    assertEquals("Row 2 Updated", returned.get(1).name());
  }

  @Test
  public void testMockWithInterfacePolymorphism() {
    AtomicInteger idCounter = new AtomicInteger(1);

    // Use the interface type to demonstrate polymorphism
    MariatestIdentityRepo repo =
        new MariatestIdentityRepoMock(
            unsaved -> unsaved.toRow(() -> new MariatestIdentityId(idCounter.getAndIncrement())));

    var inserted = repo.insert(new MariatestIdentityRowUnsaved("Test"), null);
    var found = repo.selectById(inserted.id(), null);

    assertTrue(found.isPresent());
    assertEquals("Test", found.get().name());
  }
}
