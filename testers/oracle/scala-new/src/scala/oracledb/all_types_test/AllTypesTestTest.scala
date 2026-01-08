package oracledb.all_types_test

import dev.typr.foundations.data.{OracleIntervalDS, OracleIntervalYM}
import oracledb.{AddressT, AllTypesStructNoLobs, AllTypesStructNoLobsArray, CoordinatesT, PhoneList}
import oracledb.customtypes.Defaulted
import oracledb.withConnection
import org.scalatest.funsuite.AnyFunSuite

/** Tests for Oracle comprehensive object type that includes all supported data types. This validates that complex nested Oracle OBJECT types with various field types can be correctly inserted, read
  * back, and round-tripped through the database.
  */
class AllTypesTestTest extends AnyFunSuite {
  val repo: AllTypesTestRepoImpl = new AllTypesTestRepoImpl

  def createTestStruct(prefix: String): AllTypesStructNoLobs = {
    val coords = new CoordinatesT(BigDecimal("40.7128"), BigDecimal("-74.0060"))
    val address = new AddressT(s"$prefix Main St", s"$prefix City", coords)
    val phones = new PhoneList(Array("555-0100", "555-0101"))

    new AllTypesStructNoLobs(
      s"${prefix}_varchar",
      s"${prefix}_nvarchar",
      "CHAR10    ",
      "NCHAR10   ",
      BigDecimal("12345.6789"),
      BigDecimal("1234567890"),
      BigDecimal("1234567890123456789"),
      1.5f,
      2.5,
      java.time.LocalDateTime.of(2025, 6, 15, 10, 30, 0),
      java.time.LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123000000),
      java.time.OffsetDateTime.of(2025, 6, 15, 10, 30, 45, 0, java.time.ZoneOffset.ofHours(-5)),
      java.time.OffsetDateTime.of(2025, 6, 15, 10, 30, 45, 0, java.time.ZoneOffset.UTC),
      new OracleIntervalYM(2, 6),
      new OracleIntervalDS(5, 12, 30, 45, 0),
      address,
      phones
    )
  }

  test("insert and select all types struct") {
    withConnection { c =>
      given java.sql.Connection = c
      val data = createTestStruct("Test1")

      val unsaved = new AllTypesTestRowUnsaved(
        "Test Row",
        Some(data),
        None,
        Defaulted.UseDefault[AllTypesTestId]()
      )

      val insertedId = repo.insert(unsaved)

      val inserted = repo.selectById(insertedId).get
      val _ = assert(inserted.name == "Test Row")
      val _ = assert(inserted.data.isDefined)

      val retrieved = inserted.data.get
      val _ = assert(retrieved.varcharField == "Test1_varchar")
      val _ = assert(retrieved.nvarcharField == "Test1_nvarchar")
      val _ = assert(retrieved.numberField == BigDecimal("12345.6789"))
      val _ = assert(Math.abs(retrieved.binaryFloatField - 1.5f) < 0.001f)
      val _ = assert(Math.abs(retrieved.binaryDoubleField - 2.5) < 0.001)

      val _ = assert(retrieved.nestedObjectField.street == "Test1 Main St")
      val _ = assert(retrieved.nestedObjectField.city == "Test1 City")

      val _ = assert(retrieved.varrayField.value.length == 2)
      val _ = assert(retrieved.varrayField.value(0) == "555-0100")
    }
  }

  test("roundtrip all fields") {
    withConnection { c =>
      given java.sql.Connection = c
      val original = createTestStruct("Roundtrip")

      val unsaved = new AllTypesTestRowUnsaved(
        "Roundtrip Test",
        Some(original),
        None,
        Defaulted.UseDefault[AllTypesTestId]()
      )

      val insertedId = repo.insert(unsaved)
      val retrieved = repo.selectById(insertedId).get

      val data = retrieved.data.get

      val _ = assert(original.varcharField == data.varcharField)
      val _ = assert(original.nvarcharField == data.nvarcharField)
      val _ = assert(original.numberField == data.numberField)
      val _ = assert(original.numberIntField == data.numberIntField)
      val _ = assert(original.numberLongField == data.numberLongField)
      val _ = assert(Math.abs(original.binaryFloatField - data.binaryFloatField) < 0.001f)
      val _ = assert(Math.abs(original.binaryDoubleField - data.binaryDoubleField) < 0.001)
      val _ = assert(original.dateField == data.dateField)

      val _ = assert(original.nestedObjectField.street == data.nestedObjectField.street)
      val _ = assert(original.nestedObjectField.city == data.nestedObjectField.city)
      val _ = assert(original.nestedObjectField.location.latitude == data.nestedObjectField.location.latitude)

      val _ = assert(original.varrayField.value.sameElements(data.varrayField.value))
    }
  }

  test("insert with null data") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllTypesTestRowUnsaved(
        "Null Data Test",
        None,
        None,
        Defaulted.UseDefault[AllTypesTestId]()
      )

      val insertedId = repo.insert(unsaved)
      val retrieved = repo.selectById(insertedId).get

      val _ = assert(retrieved.name == "Null Data Test")
      val _ = assert(retrieved.data.isEmpty)
      val _ = assert(retrieved.dataArray.isEmpty)
    }
  }

  test("insert with array") {
    withConnection { c =>
      given java.sql.Connection = c
      val struct1 = createTestStruct("Array1")
      val struct2 = createTestStruct("Array2")

      val dataArray = new AllTypesStructNoLobsArray(Array(struct1, struct2))

      val unsaved = new AllTypesTestRowUnsaved(
        "Array Test",
        Some(struct1),
        Some(dataArray),
        Defaulted.UseDefault[AllTypesTestId]()
      )

      val insertedId = repo.insert(unsaved)
      val retrieved = repo.selectById(insertedId).get

      val _ = assert(retrieved.dataArray.isDefined)
      val array = retrieved.dataArray.get.value
      val _ = assert(array.length == 2)
      val _ = assert(array(0).varcharField == "Array1_varchar")
      val _ = assert(array(1).varcharField == "Array2_varchar")
    }
  }

  test("update data") {
    withConnection { c =>
      given java.sql.Connection = c
      val original = createTestStruct("Original")

      val unsaved = new AllTypesTestRowUnsaved(
        "Update Test",
        Some(original),
        None,
        Defaulted.UseDefault[AllTypesTestId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).get

      val updated = createTestStruct("Updated")
      val updatedRow = inserted.copy(data = Some(updated))

      val wasUpdated = repo.update(updatedRow)
      val _ = assert(wasUpdated)

      val fetched = repo.selectById(insertedId).get
      val _ = assert(fetched.data.get.varcharField == "Updated_varchar")
    }
  }

  test("delete") {
    withConnection { c =>
      given java.sql.Connection = c
      val unsaved = new AllTypesTestRowUnsaved(
        "To Delete",
        None,
        None,
        Defaulted.UseDefault[AllTypesTestId]()
      )

      val insertedId = repo.insert(unsaved)
      val _ = assert(repo.selectById(insertedId).isDefined)

      val deleted = repo.deleteById(insertedId)
      val _ = assert(deleted)

      val _ = assert(repo.selectById(insertedId).isEmpty)
    }
  }
}
