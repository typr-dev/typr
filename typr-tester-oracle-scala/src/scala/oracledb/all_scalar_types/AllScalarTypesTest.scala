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
        java.util.Optional.of("test varchar2"),
        java.util.Optional.of(new java.math.BigDecimal("123.45")),
        java.util.Optional.of(LocalDateTime.of(2025, 1, 1, 12, 0)),
        java.util.Optional.of(LocalDateTime.of(2025, 1, 2, 13, 30)),
        java.util.Optional.of("test clob content"),
        "required not null field",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)

      val _ = assert(inserted.colVarchar2 == java.util.Optional.of("test varchar2"))
      val _ = assert(inserted.colNumber == java.util.Optional.of(new java.math.BigDecimal("123.45")))
      val _ = assert(inserted.colDate.isPresent())
      val _ = assert(inserted.colTimestamp.isPresent())
      val _ = assert(inserted.colClob == java.util.Optional.of("test clob content"))
      val _ = assert(inserted.colNotNull == "required not null field")

      val found = repo.selectById(inserted.id)
      val _ = assert(found.isPresent())
      val _ = assert(inserted == found.orElseThrow())
    }
  }

  test("insert with null values") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllScalarTypesRowUnsaved(
        java.util.Optional.empty[String](),
        java.util.Optional.empty[java.math.BigDecimal](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[String](),
        "only required field",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)

      val _ = assert(inserted.colVarchar2.isEmpty())
      val _ = assert(inserted.colNumber.isEmpty())
      val _ = assert(inserted.colDate.isEmpty())
      val _ = assert(inserted.colTimestamp.isEmpty())
      val _ = assert(inserted.colClob.isEmpty())
      val _ = assert(inserted.colNotNull == "only required field")
    }
  }

  test("update all scalar types") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllScalarTypesRowUnsaved(
        java.util.Optional.of("original"),
        java.util.Optional.of(new java.math.BigDecimal("100.00")),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[String](),
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)

      val updatedRow = inserted.copy(
        colVarchar2 = java.util.Optional.of("updated"),
        colNumber = java.util.Optional.of(new java.math.BigDecimal("200.01"))
      )

      val wasUpdated = repo.update(updatedRow)
      val _ = assert(wasUpdated)

      val fetched = repo.selectById(inserted.id).orElseThrow()
      val _ = assert(fetched.colVarchar2 == java.util.Optional.of("updated"))
      val _ = assert(fetched.colNumber == java.util.Optional.of(new java.math.BigDecimal("200.01")))
    }
  }

  test("delete all scalar types") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllScalarTypesRowUnsaved(
        java.util.Optional.of("to delete"),
        java.util.Optional.empty[java.math.BigDecimal](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[String](),
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)

      val deleted = repo.deleteById(inserted.id)
      val _ = assert(deleted)

      val found = repo.selectById(inserted.id)
      val _ = assert(found.isEmpty())
    }
  }

  test("select all") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved1 = new AllScalarTypesRowUnsaved(
        java.util.Optional.of("row 1"),
        java.util.Optional.empty[java.math.BigDecimal](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[String](),
        "required 1",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val unsaved2 = new AllScalarTypesRowUnsaved(
        java.util.Optional.of("row 2"),
        java.util.Optional.empty[java.math.BigDecimal](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[String](),
        "required 2",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val _ = repo.insert(unsaved1)
      val _ = repo.insert(unsaved2)

      val all = repo.selectAll
      val _ = assert(all.size() >= 2)
    }
  }

  test("scalar type varchar2") {
    withConnection { c =>
      given java.sql.Connection = c
      val longString = "A" * 100
      val unsaved = new AllScalarTypesRowUnsaved(
        java.util.Optional.of(longString),
        java.util.Optional.empty[java.math.BigDecimal](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[String](),
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colVarchar2 == java.util.Optional.of(longString))
    }
  }

  test("scalar type number") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllScalarTypesRowUnsaved(
        java.util.Optional.empty[String](),
        java.util.Optional.of(new java.math.BigDecimal("999999.99")),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[String](),
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colNumber == java.util.Optional.of(new java.math.BigDecimal("999999.99")))
    }
  }

  test("scalar type date") {
    withConnection { c =>
      given java.sql.Connection = c
      val testDate = LocalDateTime.of(2025, 12, 31, 23, 59, 59)
      val unsaved = new AllScalarTypesRowUnsaved(
        java.util.Optional.empty[String](),
        java.util.Optional.empty[java.math.BigDecimal](),
        java.util.Optional.of(testDate),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[String](),
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colDate.isPresent())
      val _ = assert(inserted.colDate.orElseThrow().getYear == 2025)
      val _ = assert(inserted.colDate.orElseThrow().getMonthValue == 12)
      val _ = assert(inserted.colDate.orElseThrow().getDayOfMonth == 31)
    }
  }

  test("scalar type timestamp") {
    withConnection { c =>
      given java.sql.Connection = c
      val testTimestamp = LocalDateTime.of(2025, 6, 15, 14, 30, 45)
      val unsaved = new AllScalarTypesRowUnsaved(
        java.util.Optional.empty[String](),
        java.util.Optional.empty[java.math.BigDecimal](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.of(testTimestamp),
        java.util.Optional.empty[String](),
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colTimestamp.isPresent())
      val _ = assert(inserted.colTimestamp.orElseThrow().getYear == testTimestamp.getYear)
      val _ = assert(inserted.colTimestamp.orElseThrow().getMonthValue == testTimestamp.getMonthValue)
      val _ = assert(inserted.colTimestamp.orElseThrow().getDayOfMonth == testTimestamp.getDayOfMonth)
    }
  }

  test("scalar type clob") {
    withConnection { c =>
      given java.sql.Connection = c
      val largeClobContent = "Clob content " * 1000
      val unsaved = new AllScalarTypesRowUnsaved(
        java.util.Optional.empty[String](),
        java.util.Optional.empty[java.math.BigDecimal](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.empty[LocalDateTime](),
        java.util.Optional.of(largeClobContent),
        "required",
        Defaulted.UseDefault[AllScalarTypesId]()
      )

      val inserted = repo.insert(unsaved)
      val _ = assert(inserted.colClob == java.util.Optional.of(largeClobContent))
    }
  }
}
