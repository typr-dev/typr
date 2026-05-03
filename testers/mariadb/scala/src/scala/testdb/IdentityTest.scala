package testdb

import org.scalatest.funsuite.AnyFunSuite
import testdb.mariatest_identity.*

class IdentityTest extends AnyFunSuite {
  val repo: MariatestIdentityRepoImpl = new MariatestIdentityRepoImpl

  test("autoIncrementInsert") {
    withConnection {
      val unsaved = MariatestIdentityRowUnsaved("First Row")
      val inserted = repo.insert(unsaved)

      val _ = assert(inserted != null)
      val _ = assert(inserted.id.value >= 0)
      val _ = assert(inserted.name == "First Row")

      val unsaved2 = MariatestIdentityRowUnsaved("Second Row")
      val inserted2 = repo.insert(unsaved2)

      val _ = assert(inserted2 != null)
      val _ = assert(inserted2.id != inserted.id)
      assert(inserted2.name == "Second Row")
    }
  }

  test("selectById") {
    withConnection {
      val unsaved = MariatestIdentityRowUnsaved("Select Test")
      val inserted = repo.insert(unsaved)

      val found = repo.selectById(inserted.id)
      val _ = assert(found.isDefined)
      val _ = assert(found.get.id == inserted.id)
      assert(found.get.name == "Select Test")
    }
  }

  test("update") {
    withConnection {
      val unsaved = MariatestIdentityRowUnsaved("Original Name")
      val inserted = repo.insert(unsaved)

      val updated = inserted.copy(name = "Updated Name")
      val wasUpdated = repo.update(updated)
      val _ = assert(wasUpdated)

      val found = repo.selectById(inserted.id).get
      assert(found.name == "Updated Name")
    }
  }

  test("delete") {
    withConnection {
      val unsaved = MariatestIdentityRowUnsaved("To Delete")
      val inserted = repo.insert(unsaved)

      val deleted = repo.deleteById(inserted.id)
      val _ = assert(deleted)

      val found = repo.selectById(inserted.id)
      assert(found.isEmpty)
    }
  }

  test("upsert") {
    withConnection {
      val unsaved = MariatestIdentityRowUnsaved("Upsert Test")
      val inserted = repo.insert(unsaved)

      val toUpsert = inserted.copy(name = "Upserted Name")
      val upserted = repo.upsert(toUpsert)

      val _ = assert(upserted.id == inserted.id)
      val _ = assert(upserted.name == "Upserted Name")

      val found = repo.selectById(inserted.id).get
      assert(found.name == "Upserted Name")
    }
  }

  test("selectAll") {
    withConnection {
      val _ = repo.insert(MariatestIdentityRowUnsaved("Row A"))
      val _ = repo.insert(MariatestIdentityRowUnsaved("Row B"))
      val _ = repo.insert(MariatestIdentityRowUnsaved("Row C"))

      val all = repo.selectAll
      assert(all.size >= 3)
    }
  }

  test("toUnsavedRow") {
    withConnection {
      val unsaved = MariatestIdentityRowUnsaved("Test Name")
      val inserted = repo.insert(unsaved)

      val backToUnsaved = inserted.toUnsavedRow
      val _ = assert(backToUnsaved.name == "Test Name")

      val inserted2 = repo.insert(backToUnsaved)
      val _ = assert(inserted2.id != inserted.id)
      assert(inserted2.name == inserted.name)
    }
  }
}
