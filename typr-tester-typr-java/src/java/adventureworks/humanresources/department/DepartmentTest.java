package adventureworks.humanresources.department;

import static org.junit.Assert.*;

import adventureworks.DbNow;
import adventureworks.WithConnection;
import adventureworks.customtypes.Defaulted;
import adventureworks.public_.Name;
import org.junit.Test;

public class DepartmentTest {
  private final DepartmentRepoImpl departmentRepo = new DepartmentRepoImpl();

  @Test
  public void works() {
    WithConnection.run(
        c -> {
          // setup - use short ctor + wither for modifieddate
          var unsaved =
              new DepartmentRowUnsaved(new Name("foo"), new Name("bar"))
                  .withModifieddate(new Defaulted.Provided<>(DbNow.localDateTime()));

          // insert and round trip check
          var saved1 = departmentRepo.insert(unsaved, c);
          var saved2 = unsaved.toRow(saved1::departmentid, saved1::modifieddate);
          assertEquals(saved1, saved2);

          // check field values
          departmentRepo.update(saved1.withName(new Name("baz")), c);
          var all = departmentRepo.selectAll(c);
          assertEquals(1, all.size());
          assertEquals(new Name("baz"), all.get(0).name());

          // delete
          departmentRepo.deleteById(saved1.departmentid(), c);
          var afterDelete = departmentRepo.selectAll(c);
          assertTrue(afterDelete.isEmpty());
        });
  }

  @Test
  public void upsertsWorks() {
    WithConnection.run(
        c -> {
          // setup - use short ctor + wither
          var unsaved =
              new DepartmentRowUnsaved(new Name("foo"), new Name("bar"))
                  .withModifieddate(new Defaulted.Provided<>(DbNow.localDateTime()));

          // Scala: insert and verify upsert
          // inserted2 <- departmentRepo.upsert(saved1.copy(name = newName))
          // _ <- ZIO.succeed(assert(inserted2.rowsUpdated === 1L))
          // saved2 = inserted2.updatedKeys.head
          // _ <- ZIO.succeed(assert(saved2.name === newName))
          //
          // Note: Java's upsert returns Row directly, not UpdateResult with rowsUpdated
          var saved1 = departmentRepo.insert(unsaved, c);
          var newName = new Name("baz");
          var saved2 = departmentRepo.upsert(saved1.withName(newName), c);
          assertEquals(newName, saved2.name());

          // Verify only one row exists (implicit verification that upsert updated, not inserted)
          var all = departmentRepo.selectAll(c);
          assertEquals(1, all.size());
          assertEquals(newName, all.get(0).name());
        });
  }
}
