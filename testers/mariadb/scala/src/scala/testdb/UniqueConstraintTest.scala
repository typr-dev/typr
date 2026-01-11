package testdb

import org.scalatest.funsuite.AnyFunSuite
import testdb.mariatest_unique.*
import testdb.userdefined.Email

/** Tests for unique constraint handling in MariaDB. */
class UniqueConstraintTest extends AnyFunSuite {
  val repo: MariatestUniqueRepoImpl = new MariatestUniqueRepoImpl

  test("insertWithUniqueEmail") {
    withConnection { c =>
      given java.sql.Connection = c
      val row = MariatestUniqueRowUnsaved(Email("test@example.com"), "CODE001", "CategoryA")
      val inserted = repo.insert(row)

      val _ = assert(inserted != null)
      val _ = assert(inserted.id.value >= 0)
      val _ = assert(inserted.email == Email("test@example.com"))
      val _ = assert(inserted.code == "CODE001")
      assert(inserted.category == "CategoryA")
    }
  }

  test("uniqueEmailConstraint") {
    withConnection { c =>
      given java.sql.Connection = c
      // Insert first row
      val row1 = MariatestUniqueRowUnsaved(Email("unique@example.com"), "CODE001", "CategoryA")
      val _ = repo.insert(row1)

      // Try to insert with same email (should fail in real db scenario)
      // Here we test with different codes/categories but same email
      val row2 = MariatestUniqueRowUnsaved(Email("unique@example.com"), "CODE002", "CategoryB")

      val thrown = intercept[Exception] {
        repo.insert(row2)
      }
      // Expected - duplicate key violation
      assert(thrown.getMessage.contains("Duplicate") || thrown.getMessage.contains("unique"))
    }
  }

  test("compositeUniqueConstraint") {
    withConnection { c =>
      given java.sql.Connection = c
      // Insert first row
      val row1 = MariatestUniqueRowUnsaved(Email("email1@example.com"), "COMP001", "CategoryA")
      val _ = repo.insert(row1)

      // Same code, different category - should work (composite unique on code+category)
      val row2 = MariatestUniqueRowUnsaved(Email("email2@example.com"), "COMP001", "CategoryB")
      val inserted2 = repo.insert(row2)
      val _ = assert(inserted2 != null)

      // Same category, different code - should work
      val row3 = MariatestUniqueRowUnsaved(Email("email3@example.com"), "COMP002", "CategoryA")
      val inserted3 = repo.insert(row3)
      val _ = assert(inserted3 != null)

      // Same code AND category - should fail
      val row4 = MariatestUniqueRowUnsaved(Email("email4@example.com"), "COMP001", "CategoryA")
      val thrown = intercept[Exception] {
        repo.insert(row4)
      }
      // Expected - duplicate key violation
      assert(thrown.getMessage.contains("Duplicate") || thrown.getMessage.contains("unique"))
    }
  }

  test("upsertOnUniqueEmail") {
    withConnection { c =>
      given java.sql.Connection = c
      // Insert initial row
      val row = MariatestUniqueRowUnsaved(Email("upsert@example.com"), "UPSERT001", "CategoryA")
      val inserted = repo.insert(row)

      // Upsert with same ID - should update
      val toUpsert = inserted.copy(code = "UPSERT001-UPDATED", category = "CategoryA-Updated")
      val upserted = repo.upsert(toUpsert)

      val _ = assert(upserted.id == inserted.id)
      val _ = assert(upserted.code == "UPSERT001-UPDATED")
      assert(upserted.category == "CategoryA-Updated")
    }
  }

  test("selectByIdAfterUpdate") {
    withConnection { c =>
      given java.sql.Connection = c
      val row = MariatestUniqueRowUnsaved(Email("select@example.com"), "SELECT001", "CategoryA")
      val inserted = repo.insert(row)

      // Update
      val updated = inserted.copy(code = "SELECT001-UPDATED")
      val _ = repo.update(updated)

      // Select and verify
      val found = repo.selectById(inserted.id).get
      val _ = assert(found.code == "SELECT001-UPDATED")
      assert(found.email == Email("select@example.com")) // Email unchanged
    }
  }

  test("deleteAndReuseUniqueValue") {
    withConnection { c =>
      given java.sql.Connection = c
      // Insert
      val row = MariatestUniqueRowUnsaved(Email("reuse@example.com"), "REUSE001", "CategoryA")
      val inserted = repo.insert(row)

      // Delete
      val deleted = repo.deleteById(inserted.id)
      val _ = assert(deleted)

      // Should now be able to reuse the email
      val row2 = MariatestUniqueRowUnsaved(Email("reuse@example.com"), "REUSE002", "CategoryB")
      val inserted2 = repo.insert(row2)

      val _ = assert(inserted2 != null)
      assert(inserted2.email == Email("reuse@example.com"))
    }
  }

  test("selectAllWithUniqueRows") {
    withConnection { c =>
      given java.sql.Connection = c
      // Insert multiple unique rows
      val _ = repo.insert(MariatestUniqueRowUnsaved(Email("all1@example.com"), "ALL001", "CatA"))
      val _ = repo.insert(MariatestUniqueRowUnsaved(Email("all2@example.com"), "ALL002", "CatB"))
      val _ = repo.insert(MariatestUniqueRowUnsaved(Email("all3@example.com"), "ALL003", "CatC"))

      val all = repo.selectAll
      val _ = assert(all.size >= 3)

      // Verify all emails are unique
      val emails = all.map(_.email).distinct
      assert(all.size == emails.size)
    }
  }
}
