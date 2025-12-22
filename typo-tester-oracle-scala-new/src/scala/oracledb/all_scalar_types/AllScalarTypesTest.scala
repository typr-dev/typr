package oracledb.all_scalar_types

import oracledb.customtypes.Defaulted
import oracledb.withConnection
import org.scalatest.funsuite.AnyFunSuite

import java.time.LocalDateTime

class AllScalarTypesTest extends AnyFunSuite {
  val repo: AllScalarTypesRepoImpl = new AllScalarTypesRepoImpl

  test("insert and select all scalar types") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllScalarTypesRowUnsaved(
        Some("test varchar2"),
        Some(BigDecimal("123.45")),
        Some(LocalDateTime.of(2025, 1, 1, 12, 0)),
        Some(LocalDateTime.of(2025, 1, 2, 13, 30)),
        Some("test clob content"),
        "required not null field",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)

      val _ = assert(inserted.colVarchar2 == Some("test varchar2"))
      val _ = assert(inserted.colNumber == Some(BigDecimal("123.45")))
      val _ = assert(inserted.colDate.isDefined)
      val _ = assert(inserted.colTimestamp.isDefined)
      val _ = assert(inserted.colClob == Some("test clob content"))
      val _ = assert(inserted.colNotNull == "required not null field")

      val found = repo.selectById(inserted.id)
      val _ = assert(found.isDefined)
      val _ = assert(inserted == found.get)
    }
  }

  test("insert with null values") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllScalarTypesRowUnsaved(
        None,
        None,
        None,
        None,
        None,
        "only required field",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)

      val _ = assert(inserted.colVarchar2.isEmpty)
      val _ = assert(inserted.colNumber.isEmpty)
      val _ = assert(inserted.colDate.isEmpty)
      val _ = assert(inserted.colTimestamp.isEmpty)
      val _ = assert(inserted.colClob.isEmpty)
      val _ = assert(inserted.colNotNull == "only required field")
    }
  }

  test("update all scalar types") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllScalarTypesRowUnsaved(
        Some("original"),
        Some(BigDecimal("100.00")),
        None,
        None,
        None,
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)

      val updatedRow = inserted.copy(
        colVarchar2 = Some("updated"),
        colNumber = Some(BigDecimal("200.01"))
      )

      val wasUpdated = repo.update(updatedRow)
      val _ = assert(wasUpdated)

      val fetched = repo.selectById(inserted.id).get
      val _ = assert(fetched.colVarchar2 == Some("updated"))
      val _ = assert(fetched.colNumber == Some(BigDecimal("200.01")))
    }
  }

  test("delete all scalar types") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllScalarTypesRowUnsaved(
        Some("to delete"),
        None,
        None,
        None,
        None,
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)

      val deleted = repo.deleteById(inserted.id)
      val _ = assert(deleted)

      val found = repo.selectById(inserted.id)
      val _ = assert(found.isEmpty)
    }
  }

  test("select all") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved1 = new AllScalarTypesRowUnsaved(
        Some("row 1"),
        None,
        None,
        None,
        None,
        "required 1",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val unsaved2 = new AllScalarTypesRowUnsaved(
        Some("row 2"),
        None,
        None,
        None,
        None,
        "required 2",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val _ = repo.insert(unsaved1)
      val _ = repo.insert(unsaved2)

      val all = repo.selectAll
      val _ = assert(all.length >= 2)
    }
  }

  test("scalar type varchar2") {
    withConnection { c =>
      given java.sql.Connection = c
      val longString = "A" * 100
      val unsaved = new AllScalarTypesRowUnsaved(
        Some(longString),
        None,
        None,
        None,
        None,
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colVarchar2 == Some(longString))
    }
  }

  test("scalar type number") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllScalarTypesRowUnsaved(
        None,
        Some(BigDecimal("999999.99")),
        None,
        None,
        None,
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colNumber == Some(BigDecimal("999999.99")))
    }
  }

  test("scalar type date") {
    withConnection { c =>
      given java.sql.Connection = c
      val testDate = LocalDateTime.of(2025, 12, 31, 23, 59, 59)
      val unsaved = new AllScalarTypesRowUnsaved(
        None,
        None,
        Some(testDate),
        None,
        None,
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colDate.isDefined)
      val _ = assert(inserted.colDate.get.getYear == 2025)
      val _ = assert(inserted.colDate.get.getMonthValue == 12)
      val _ = assert(inserted.colDate.get.getDayOfMonth == 31)
    }
  }

  test("scalar type timestamp") {
    withConnection { c =>
      given java.sql.Connection = c
      val testTimestamp = LocalDateTime.of(2025, 6, 15, 14, 30, 45)
      val unsaved = new AllScalarTypesRowUnsaved(
        None,
        None,
        None,
        Some(testTimestamp),
        None,
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colTimestamp.isDefined)
      val _ = assert(inserted.colTimestamp.get.getYear == testTimestamp.getYear)
      val _ = assert(inserted.colTimestamp.get.getMonthValue == testTimestamp.getMonthValue)
      val _ = assert(inserted.colTimestamp.get.getDayOfMonth == testTimestamp.getDayOfMonth)
    }
  }

  test("scalar type clob") {
    withConnection { c =>
      given java.sql.Connection = c
      val largeClobContent = "Clob content " * 1000
      val unsaved = new AllScalarTypesRowUnsaved(
        None,
        None,
        None,
        None,
        Some(largeClobContent),
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colClob == Some(largeClobContent))
    }
  }
}
