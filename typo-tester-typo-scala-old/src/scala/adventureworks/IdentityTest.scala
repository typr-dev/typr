package adventureworks

import adventureworks.public.identity_test.*
import org.junit.Assert.*
import org.junit.Test

class IdentityTest {
  private val repo = new IdentityTestRepoImpl

  @Test
  def works(): Unit = {
    WithConnection {
      val unsaved = IdentityTestRowUnsaved(name = IdentityTestId("a"))
      val inserted = repo.insert(unsaved)
      val upserted = repo.upsert(inserted)
      assertEquals(inserted, upserted)

      val rows = repo.selectAll
      assertEquals(1, rows.size)
      val row = rows.get(0)
      assertEquals(IdentityTestId("a"), row.name)
      assertNotNull(row.alwaysGenerated)
      assertNotNull(row.defaultGenerated)
    }
  }
}
