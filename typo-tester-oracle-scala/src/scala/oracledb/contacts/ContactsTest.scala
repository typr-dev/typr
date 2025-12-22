package oracledb.contacts

import oracledb.{EmailTableT, TagVarrayT}
import oracledb.customtypes.Defaulted
import oracledb.withConnection
import org.scalatest.funsuite.AnyFunSuite

class ContactsTest extends AnyFunSuite {
  val repo: ContactsRepoImpl = new ContactsRepoImpl

  test("insert contact with nested table and varray") {
    withConnection { c =>
      given java.sql.Connection = c
      val emails = new EmailTableT(Array("john@example.com", "john.doe@work.com", "jdoe@personal.net"))
      val tags = new TagVarrayT(Array("customer", "vip"))

      val unsaved = new ContactsRowUnsaved(
        "John Doe",
        java.util.Optional.of(emails),
        java.util.Optional.of(tags),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)

      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.name == "John Doe")
      val _ = assert(inserted.emails.isPresent())
      val _ = assert(inserted.emails.orElseThrow().value.sameElements(Array("john@example.com", "john.doe@work.com", "jdoe@personal.net")))
      val _ = assert(inserted.tags.isPresent())
      val _ = assert(inserted.tags.orElseThrow().value.sameElements(Array("customer", "vip")))
    }
  }

  test("insert contact with only emails") {
    withConnection { c =>
      given java.sql.Connection = c
      val emails = new EmailTableT(Array("jane@example.com"))

      val unsaved = new ContactsRowUnsaved(
        "Jane Smith",
        java.util.Optional.of(emails),
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)

      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.emails.isPresent())
      val _ = assert(inserted.emails.orElseThrow().value.length == 1)
      val _ = assert(inserted.tags.isEmpty())
    }
  }

  test("insert contact with only tags") {
    withConnection { c =>
      given java.sql.Connection = c
      val tags = new TagVarrayT(Array("partner", "active"))

      val unsaved = new ContactsRowUnsaved(
        "No Email Contact",
        java.util.Optional.empty[EmailTableT](),
        java.util.Optional.of(tags),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)

      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.emails.isEmpty())
      val _ = assert(inserted.tags.isPresent())
    }
  }

  test("insert contact with no collections") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new ContactsRowUnsaved(
        "Minimal Contact",
        java.util.Optional.empty[EmailTableT](),
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)

      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.name == "Minimal Contact")
      val _ = assert(inserted.emails.isEmpty())
      val _ = assert(inserted.tags.isEmpty())
    }
  }

  test("nested table roundtrip") {
    withConnection { c =>
      given java.sql.Connection = c
      val emailArray = Array(
        "email1@test.com",
        "email2@test.com",
        "email3@test.com",
        "email4@test.com",
        "email5@test.com"
      )
      val emails = new EmailTableT(emailArray)

      val unsaved = new ContactsRowUnsaved(
        "Nested Table Test",
        java.util.Optional.of(emails),
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)

      val found = repo.selectById(insertedId)
      val _ = assert(found.isPresent())
      val _ = assert(found.orElseThrow().emails.isPresent())
      val _ = assert(found.orElseThrow().emails.orElseThrow().value.sameElements(emailArray))
    }
  }

  test("update emails") {
    withConnection { c =>
      given java.sql.Connection = c
      val originalEmails = new EmailTableT(Array("old1@example.com", "old2@example.com"))
      val unsaved = new ContactsRowUnsaved(
        "Update Emails Test",
        java.util.Optional.of(originalEmails),
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()

      val newEmails = new EmailTableT(Array("new1@example.com", "new2@example.com", "new3@example.com"))
      val updatedRow = inserted.copy(emails = java.util.Optional.of(newEmails))

      val wasUpdated = repo.update(updatedRow)
      val _ = assert(wasUpdated)
      val fetched = repo.selectById(insertedId).orElseThrow()
      val _ = assert(fetched.emails.isPresent())
      val _ = assert(fetched.emails.orElseThrow().value.sameElements(Array("new1@example.com", "new2@example.com", "new3@example.com")))
    }
  }

  test("update tags") {
    withConnection { c =>
      given java.sql.Connection = c
      val originalTags = new TagVarrayT(Array("old"))
      val unsaved = new ContactsRowUnsaved(
        "Update Tags Test",
        java.util.Optional.empty[EmailTableT](),
        java.util.Optional.of(originalTags),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()

      val newTags = new TagVarrayT(Array("new", "updated"))
      val updatedRow = inserted.copy(tags = java.util.Optional.of(newTags))

      val wasUpdated = repo.update(updatedRow)
      val _ = assert(wasUpdated)
      val fetched = repo.selectById(insertedId).orElseThrow()
      val _ = assert(fetched.tags.isPresent())
      val _ = assert(fetched.tags.orElseThrow().value.sameElements(Array("new", "updated")))
    }
  }

  test("update both collections") {
    withConnection { c =>
      given java.sql.Connection = c
      val originalEmails = new EmailTableT(Array("original@test.com"))
      val originalTags = new TagVarrayT(Array("original"))

      val unsaved = new ContactsRowUnsaved(
        "Update Both Test",
        java.util.Optional.of(originalEmails),
        java.util.Optional.of(originalTags),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()

      val newEmails = new EmailTableT(Array("updated1@test.com", "updated2@test.com"))
      val newTags = new TagVarrayT(Array("updated1", "updated2", "updated3"))

      val updatedRow = inserted.copy(
        emails = java.util.Optional.of(newEmails),
        tags = java.util.Optional.of(newTags)
      )

      val wasUpdated = repo.update(updatedRow)
      val _ = assert(wasUpdated)
      val fetched = repo.selectById(insertedId).orElseThrow()
      val _ = assert(fetched.emails.isPresent())
      val _ = assert(fetched.tags.isPresent())
      val _ = assert(fetched.emails.orElseThrow().value.length == 2)
      val _ = assert(fetched.tags.orElseThrow().value.length == 3)
    }
  }

  test("clear emails") {
    withConnection { c =>
      given java.sql.Connection = c
      val emails = new EmailTableT(Array("clear@test.com"))
      val unsaved = new ContactsRowUnsaved(
        "Clear Emails Test",
        java.util.Optional.of(emails),
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.emails.isPresent())

      val cleared = inserted.copy(emails = java.util.Optional.empty[EmailTableT]())
      val wasUpdated = repo.update(cleared)

      val _ = assert(wasUpdated)
      val fetched = repo.selectById(insertedId).orElseThrow()
      val _ = assert(fetched.emails.isEmpty())
    }
  }

  test("nested table with many emails") {
    withConnection { c =>
      given java.sql.Connection = c
      val manyEmails = (0 until 20).map(i => s"email$i@test.com").toArray
      val emails = new EmailTableT(manyEmails)

      val unsaved = new ContactsRowUnsaved(
        "Many Emails Test",
        java.util.Optional.of(emails),
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.emails.isPresent())
      val _ = assert(inserted.emails.orElseThrow().value.length == 20)
    }
  }

  test("delete contact") {
    withConnection { c =>
      given java.sql.Connection = c
      val emails = new EmailTableT(Array("delete@test.com"))
      val tags = new TagVarrayT(Array("delete"))

      val unsaved = new ContactsRowUnsaved(
        "To Delete",
        java.util.Optional.of(emails),
        java.util.Optional.of(tags),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)

      val deleted = repo.deleteById(insertedId)
      val _ = assert(deleted)

      val found = repo.selectById(insertedId)
      val _ = assert(found.isEmpty())
    }
  }

  test("select all") {
    withConnection { c =>
      given java.sql.Connection = c
      val emails1 = new EmailTableT(Array("contact1@test.com"))
      val emails2 = new EmailTableT(Array("contact2@test.com"))

      val unsaved1 = new ContactsRowUnsaved(
        "Contact 1",
        java.util.Optional.of(emails1),
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ContactsId]()
      )

      val unsaved2 = new ContactsRowUnsaved(
        "Contact 2",
        java.util.Optional.of(emails2),
        java.util.Optional.of(new TagVarrayT(Array("test"))),
        Defaulted.UseDefault[ContactsId]()
      )

      val _ = repo.insert(unsaved1)
      val _ = repo.insert(unsaved2)

      val all = repo.selectAll
      val _ = assert(all.size() >= 2)
    }
  }

  test("nested table vs varray difference") {
    val nestedTable1 = new EmailTableT(Array("a@test.com", "b@test.com"))
    val nestedTable2 = new EmailTableT(Array("a@test.com", "b@test.com"))

    val _ = assert(nestedTable1 != nestedTable2)
    val _ = assert(nestedTable1.value.sameElements(nestedTable2.value))

    val varray1 = new TagVarrayT(Array("tag1", "tag2"))
    val varray2 = new TagVarrayT(Array("tag1", "tag2"))

    val _ = assert(varray1 != varray2)
    val _ = assert(varray1.value.sameElements(varray2.value))
  }

  test("empty email array") {
    withConnection { c =>
      given java.sql.Connection = c
      val emptyEmails = new EmailTableT(Array())

      val unsaved = new ContactsRowUnsaved(
        "Empty Emails Test",
        java.util.Optional.of(emptyEmails),
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ContactsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.emails.isPresent())
      val _ = assert(inserted.emails.orElseThrow().value.length == 0)
    }
  }
}
