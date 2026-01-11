package testdb;

import static org.junit.Assert.*;

import org.junit.Test;
import testdb.mariatest_unique.*;
import testdb.userdefined.Email;

/** Tests for unique constraint handling in MariaDB. */
public class UniqueConstraintTest {
  private final MariatestUniqueRepoImpl repo = new MariatestUniqueRepoImpl();

  @Test
  public void testInsertWithUniqueEmail() {
    MariaDbTestHelper.run(
        c -> {
          var row =
              new MariatestUniqueRowUnsaved(new Email("test@example.com"), "CODE001", "CategoryA");
          var inserted = repo.insert(row, c);

          assertNotNull(inserted);
          assertNotNull(inserted.id());
          assertEquals(new Email("test@example.com"), inserted.email());
          assertEquals("CODE001", inserted.code());
          assertEquals("CategoryA", inserted.category());
        });
  }

  @Test
  public void testUniqueEmailConstraint() {
    MariaDbTestHelper.run(
        c -> {
          // Insert first row
          var row1 =
              new MariatestUniqueRowUnsaved(
                  new Email("unique@example.com"), "CODE001", "CategoryA");
          repo.insert(row1, c);

          // Try to insert with same email (should fail in real db scenario)
          // Here we test with different codes/categories but same email
          var row2 =
              new MariatestUniqueRowUnsaved(
                  new Email("unique@example.com"), "CODE002", "CategoryB");

          try {
            repo.insert(row2, c);
            fail("Should have thrown exception for duplicate email");
          } catch (Exception e) {
            // Expected - duplicate key violation
            assertTrue(e.getMessage().contains("Duplicate") || e.getMessage().contains("unique"));
          }
        });
  }

  @Test
  public void testCompositeUniqueConstraint() {
    MariaDbTestHelper.run(
        c -> {
          // Insert first row
          var row1 =
              new MariatestUniqueRowUnsaved(
                  new Email("email1@example.com"), "COMP001", "CategoryA");
          repo.insert(row1, c);

          // Same code, different category - should work (composite unique on code+category)
          var row2 =
              new MariatestUniqueRowUnsaved(
                  new Email("email2@example.com"), "COMP001", "CategoryB");
          var inserted2 = repo.insert(row2, c);
          assertNotNull(inserted2);

          // Same category, different code - should work
          var row3 =
              new MariatestUniqueRowUnsaved(
                  new Email("email3@example.com"), "COMP002", "CategoryA");
          var inserted3 = repo.insert(row3, c);
          assertNotNull(inserted3);

          // Same code AND category - should fail
          var row4 =
              new MariatestUniqueRowUnsaved(
                  new Email("email4@example.com"), "COMP001", "CategoryA");
          try {
            repo.insert(row4, c);
            fail("Should have thrown exception for duplicate code+category");
          } catch (Exception e) {
            // Expected - duplicate key violation
            assertTrue(e.getMessage().contains("Duplicate") || e.getMessage().contains("unique"));
          }
        });
  }

  @Test
  public void testUpsertOnUniqueEmail() {
    MariaDbTestHelper.run(
        c -> {
          // Insert initial row
          var row =
              new MariatestUniqueRowUnsaved(
                  new Email("upsert@example.com"), "UPSERT001", "CategoryA");
          var inserted = repo.insert(row, c);

          // Upsert with same ID - should update
          var toUpsert = inserted.withCode("UPSERT001-UPDATED").withCategory("CategoryA-Updated");
          var upserted = repo.upsert(toUpsert, c);

          assertEquals(inserted.id(), upserted.id());
          assertEquals("UPSERT001-UPDATED", upserted.code());
          assertEquals("CategoryA-Updated", upserted.category());
        });
  }

  @Test
  public void testSelectByIdAfterUpdate() {
    MariaDbTestHelper.run(
        c -> {
          var row =
              new MariatestUniqueRowUnsaved(
                  new Email("select@example.com"), "SELECT001", "CategoryA");
          var inserted = repo.insert(row, c);

          // Update
          var updated = inserted.withCode("SELECT001-UPDATED");
          repo.update(updated, c);

          // Select and verify
          var found = repo.selectById(inserted.id(), c).orElseThrow();
          assertEquals("SELECT001-UPDATED", found.code());
          assertEquals(new Email("select@example.com"), found.email()); // Email unchanged
        });
  }

  @Test
  public void testDeleteAndReuseUniqueValue() {
    MariaDbTestHelper.run(
        c -> {
          // Insert
          var row =
              new MariatestUniqueRowUnsaved(
                  new Email("reuse@example.com"), "REUSE001", "CategoryA");
          var inserted = repo.insert(row, c);

          // Delete
          boolean deleted = repo.deleteById(inserted.id(), c);
          assertTrue(deleted);

          // Should now be able to reuse the email
          var row2 =
              new MariatestUniqueRowUnsaved(
                  new Email("reuse@example.com"), "REUSE002", "CategoryB");
          var inserted2 = repo.insert(row2, c);

          assertNotNull(inserted2);
          assertEquals(new Email("reuse@example.com"), inserted2.email());
        });
  }

  @Test
  public void testSelectAllWithUniqueRows() {
    MariaDbTestHelper.run(
        c -> {
          // Insert multiple unique rows
          repo.insert(
              new MariatestUniqueRowUnsaved(new Email("all1@example.com"), "ALL001", "CatA"), c);
          repo.insert(
              new MariatestUniqueRowUnsaved(new Email("all2@example.com"), "ALL002", "CatB"), c);
          repo.insert(
              new MariatestUniqueRowUnsaved(new Email("all3@example.com"), "ALL003", "CatC"), c);

          var all = repo.selectAll(c);
          assertTrue(all.size() >= 3);

          // Verify all emails are unique
          var emails = all.stream().map(MariatestUniqueRow::email).distinct().toList();
          assertEquals(all.size(), emails.size());
        });
  }
}
