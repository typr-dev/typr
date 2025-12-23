package adventureworks;

import static org.junit.Assert.*;

import adventureworks.public_.identity_test.*;
import org.junit.Test;

public class IdentityTest {
  private final IdentityTestRepoImpl repo = new IdentityTestRepoImpl();

  @Test
  public void works() {
    WithConnection.run(
        c -> {
          // Use short ctor
          var unsaved = new IdentityTestRowUnsaved(new IdentityTestId("a"));

          // Scala: inserted <- repo.insert(unsaved)
          var inserted = repo.insert(unsaved, c);

          // Scala: upserted <- repo.upsert(inserted)
          // Scala: assert(inserted === upserted.updatedKeys.head)
          var upserted = repo.upsert(inserted, c);
          assertEquals(inserted, upserted);

          // DSL is disabled for Java - use selectAll instead
          var rows = repo.selectAll(c);

          assertEquals(1, rows.size());
          var row = rows.get(0);
          assertEquals(new IdentityTestId("a"), row.name());
          // always_generated and default_generated are auto-generated, just verify they exist
          assertNotNull(row.alwaysGenerated());
          assertNotNull(row.defaultGenerated());
        });
  }
}
