package oracledb.contacts;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;
import oracledb.EmailTableT;
import oracledb.OracleTestHelper;
import oracledb.TagVarrayT;
import oracledb.customtypes.Defaulted;
import org.junit.Test;

public class ContactsTest {
  private final ContactsRepoImpl repo = new ContactsRepoImpl();

  @Test
  public void testInsertContactWithNestedTableAndVarray() {
    OracleTestHelper.run(
        c -> {
          EmailTableT emails =
              new EmailTableT(
                  new String[] {"john@example.com", "john.doe@work.com", "jdoe@personal.net"});
          TagVarrayT tags = new TagVarrayT(new String[] {"customer", "vip"});

          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "John Doe", Optional.of(emails), Optional.of(tags), new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);

          assertNotNull(insertedId);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertNotNull(inserted.contactId());
          assertEquals("John Doe", inserted.name());
          assertTrue(inserted.emails().isPresent());
          assertArrayEquals(
              new String[] {"john@example.com", "john.doe@work.com", "jdoe@personal.net"},
              inserted.emails().get().value());
          assertTrue(inserted.tags().isPresent());
          assertArrayEquals(new String[] {"customer", "vip"}, inserted.tags().get().value());
        });
  }

  @Test
  public void testInsertContactWithOnlyEmails() {
    OracleTestHelper.run(
        c -> {
          EmailTableT emails = new EmailTableT(new String[] {"jane@example.com"});

          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "Jane Smith",
                  Optional.of(emails),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);

          assertNotNull(insertedId);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertNotNull(inserted.contactId());
          assertTrue(inserted.emails().isPresent());
          assertEquals(1, inserted.emails().get().value().length);
          assertFalse(inserted.tags().isPresent());
        });
  }

  @Test
  public void testInsertContactWithOnlyTags() {
    OracleTestHelper.run(
        c -> {
          TagVarrayT tags = new TagVarrayT(new String[] {"partner", "active"});

          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "No Email Contact",
                  Optional.empty(),
                  Optional.of(tags),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);

          assertNotNull(insertedId);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertNotNull(inserted.contactId());
          assertFalse(inserted.emails().isPresent());
          assertTrue(inserted.tags().isPresent());
        });
  }

  @Test
  public void testInsertContactWithNoCollections() {
    OracleTestHelper.run(
        c -> {
          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "Minimal Contact",
                  Optional.empty(),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);

          assertNotNull(insertedId);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertNotNull(inserted.contactId());
          assertEquals("Minimal Contact", inserted.name());
          assertFalse(inserted.emails().isPresent());
          assertFalse(inserted.tags().isPresent());
        });
  }

  @Test
  public void testNestedTableRoundtrip() {
    OracleTestHelper.run(
        c -> {
          String[] emailArray =
              new String[] {
                "email1@test.com",
                "email2@test.com",
                "email3@test.com",
                "email4@test.com",
                "email5@test.com"
              };
          EmailTableT emails = new EmailTableT(emailArray);

          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "Nested Table Test",
                  Optional.of(emails),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);

          Optional<ContactsRow> found = repo.selectById(insertedId, c);
          assertTrue(found.isPresent());
          assertTrue(found.get().emails().isPresent());
          assertArrayEquals(emailArray, found.get().emails().get().value());
        });
  }

  @Test
  public void testUpdateEmails() {
    OracleTestHelper.run(
        c -> {
          EmailTableT originalEmails =
              new EmailTableT(new String[] {"old1@example.com", "old2@example.com"});
          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "Update Emails Test",
                  Optional.of(originalEmails),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();

          EmailTableT newEmails =
              new EmailTableT(
                  new String[] {"new1@example.com", "new2@example.com", "new3@example.com"});
          ContactsRow updatedRow = inserted.withEmails(Optional.of(newEmails));

          Boolean wasUpdated = repo.update(updatedRow, c);
          assertTrue(wasUpdated);
          ContactsRow fetched = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(fetched.emails().isPresent());
          assertArrayEquals(
              new String[] {"new1@example.com", "new2@example.com", "new3@example.com"},
              fetched.emails().get().value());
        });
  }

  @Test
  public void testUpdateTags() {
    OracleTestHelper.run(
        c -> {
          TagVarrayT originalTags = new TagVarrayT(new String[] {"old"});
          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "Update Tags Test",
                  Optional.empty(),
                  Optional.of(originalTags),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();

          TagVarrayT newTags = new TagVarrayT(new String[] {"new", "updated"});
          ContactsRow updatedRow = inserted.withTags(Optional.of(newTags));

          Boolean wasUpdated = repo.update(updatedRow, c);
          assertTrue(wasUpdated);
          ContactsRow fetched = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(fetched.tags().isPresent());
          assertArrayEquals(new String[] {"new", "updated"}, fetched.tags().get().value());
        });
  }

  @Test
  public void testUpdateBothCollections() {
    OracleTestHelper.run(
        c -> {
          EmailTableT originalEmails = new EmailTableT(new String[] {"original@test.com"});
          TagVarrayT originalTags = new TagVarrayT(new String[] {"original"});

          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "Update Both Test",
                  Optional.of(originalEmails),
                  Optional.of(originalTags),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();

          EmailTableT newEmails =
              new EmailTableT(new String[] {"updated1@test.com", "updated2@test.com"});
          TagVarrayT newTags = new TagVarrayT(new String[] {"updated1", "updated2", "updated3"});

          ContactsRow updatedRow =
              inserted.withEmails(Optional.of(newEmails)).withTags(Optional.of(newTags));

          Boolean wasUpdated = repo.update(updatedRow, c);
          assertTrue(wasUpdated);
          ContactsRow fetched = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(fetched.emails().isPresent());
          assertTrue(fetched.tags().isPresent());
          assertEquals(2, fetched.emails().get().value().length);
          assertEquals(3, fetched.tags().get().value().length);
        });
  }

  @Test
  public void testClearEmails() {
    OracleTestHelper.run(
        c -> {
          EmailTableT emails = new EmailTableT(new String[] {"clear@test.com"});
          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "Clear Emails Test",
                  Optional.of(emails),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(inserted.emails().isPresent());

          ContactsRow cleared = inserted.withEmails(Optional.empty());
          Boolean wasUpdated = repo.update(cleared, c);

          assertTrue(wasUpdated);
          ContactsRow fetched = repo.selectById(insertedId, c).orElseThrow();
          assertFalse(fetched.emails().isPresent());
        });
  }

  @Test
  public void testNestedTableWithManyEmails() {
    OracleTestHelper.run(
        c -> {
          String[] manyEmails = new String[20];
          for (int i = 0; i < 20; i++) {
            manyEmails[i] = "email" + i + "@test.com";
          }
          EmailTableT emails = new EmailTableT(manyEmails);

          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "Many Emails Test",
                  Optional.of(emails),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(inserted.emails().isPresent());
          assertEquals(20, inserted.emails().get().value().length);
        });
  }

  @Test
  public void testDeleteContact() {
    OracleTestHelper.run(
        c -> {
          EmailTableT emails = new EmailTableT(new String[] {"delete@test.com"});
          TagVarrayT tags = new TagVarrayT(new String[] {"delete"});

          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "To Delete",
                  Optional.of(emails),
                  Optional.of(tags),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);

          boolean deleted = repo.deleteById(insertedId, c);
          assertTrue(deleted);

          Optional<ContactsRow> found = repo.selectById(insertedId, c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testSelectAll() {
    OracleTestHelper.run(
        c -> {
          EmailTableT emails1 = new EmailTableT(new String[] {"contact1@test.com"});
          EmailTableT emails2 = new EmailTableT(new String[] {"contact2@test.com"});

          ContactsRowUnsaved unsaved1 =
              new ContactsRowUnsaved(
                  "Contact 1",
                  Optional.of(emails1),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ContactsRowUnsaved unsaved2 =
              new ContactsRowUnsaved(
                  "Contact 2",
                  Optional.of(emails2),
                  Optional.of(new TagVarrayT(new String[] {"test"})),
                  new Defaulted.UseDefault<>());

          repo.insert(unsaved1, c);
          repo.insert(unsaved2, c);

          List<ContactsRow> all = repo.selectAll(c);
          assertTrue(all.size() >= 2);
        });
  }

  @Test
  public void testNestedTableVsVarrayDifference() {
    EmailTableT nestedTable1 = new EmailTableT(new String[] {"a@test.com", "b@test.com"});
    EmailTableT nestedTable2 = new EmailTableT(new String[] {"a@test.com", "b@test.com"});

    assertFalse(nestedTable1.equals(nestedTable2));
    assertArrayEquals(nestedTable1.value(), nestedTable2.value());

    TagVarrayT varray1 = new TagVarrayT(new String[] {"tag1", "tag2"});
    TagVarrayT varray2 = new TagVarrayT(new String[] {"tag1", "tag2"});

    assertFalse(varray1.equals(varray2));
    assertArrayEquals(varray1.value(), varray2.value());
  }

  @Test
  public void testEmptyEmailArray() {
    OracleTestHelper.run(
        c -> {
          EmailTableT emptyEmails = new EmailTableT(new String[] {});

          ContactsRowUnsaved unsaved =
              new ContactsRowUnsaved(
                  "Empty Emails Test",
                  Optional.of(emptyEmails),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ContactsId insertedId = repo.insert(unsaved, c);
          ContactsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(inserted.emails().isPresent());
          assertEquals(0, inserted.emails().get().value().length);
        });
  }
}
