package testdb

import dev.typr.foundations.data.Json
import org.junit.Assert.*
import org.junit.Test
import testdb.all_scalar_types.*

import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

/** Round-trip every SQLite scalar type through generated codecs. */
class AllTypesTest {
  private val repo = new AllScalarTypesRepoImpl()

  private def createSampleRow(id: Long): AllScalarTypesRow =
    AllScalarTypesRow(
      id = AllScalarTypesId(id),
      colTinyint = Some(42.toByte),
      colSmallint = Some(1000.toShort),
      colInt = Some(100000),
      colInteger = Some(10000000000L),
      colBigint = Some(9223372036854775000L),
      colBoolean = Some(true),
      colReal = Some(3.14159),
      colDouble = Some(2.718281828),
      colFloat = Some(1.5f),
      colDecimal = Some(BigDecimal("12345.67")),
      colNumeric = Some(BigDecimal("999.999")),
      colText = Some("text content"),
      colVarchar = Some("varchar_value"),
      colChar = Some("char5"),
      colClob = Some("clob content"),
      colBlob = Some(Array[Byte](1, 2, 3, 4, 5)),
      colBinary = Some(Array[Byte](0x0a, 0x0b, 0x0c)),
      colDate = Some(LocalDate.of(2025, 1, 15)),
      colTime = Some(LocalTime.of(14, 30, 45)),
      colDatetime = Some(LocalDateTime.of(2025, 1, 15, 14, 30, 45)),
      colTimestamp = Some(LocalDateTime.of(2025, 6, 1, 9, 0, 0)),
      colUuid = Some(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
      colJson = Some(Json("{\"key\": \"value\"}")),
      colNotNull = "required_value"
    )

  @Test def testInsertAndSelectAllTypes(): Unit = withConnection {
    val row = createSampleRow(1001L)
    val inserted = repo.insert(row)
    assertEquals(row.id, inserted.id)
    assertEquals(row.colTinyint, inserted.colTinyint)
    assertEquals(row.colSmallint, inserted.colSmallint)
    assertEquals(row.colInt, inserted.colInt)
    assertEquals(row.colInteger, inserted.colInteger)
    assertEquals(row.colBoolean, inserted.colBoolean)
    assertEquals(row.colVarchar, inserted.colVarchar)
    assertEquals(row.colUuid, inserted.colUuid)
    assertEquals(row.colNotNull, inserted.colNotNull)
    assertTrue(repo.selectById(inserted.id).isDefined)
  }

  @Test def testUpdateAllTypes(): Unit = withConnection {
    val row = createSampleRow(1002L)
    val inserted = repo.insert(row)
    val updated = inserted.copy(
      colVarchar = Some("updated"),
      colDecimal = Some(BigDecimal("999.99")),
      colBoolean = Some(false)
    )
    assertTrue(repo.update(updated))
    val found = repo.selectById(inserted.id).get
    assertEquals(Some("updated"), found.colVarchar)
    assertEquals(Some(false), found.colBoolean)
  }

  @Test def testDeleteAllTypes(): Unit = withConnection {
    val inserted = repo.insert(createSampleRow(1003L))
    assertTrue(repo.deleteById(inserted.id))
    assertFalse(repo.selectById(inserted.id).isDefined)
  }

  @Test def testInsertWithNulls(): Unit = withConnection {
    val row = AllScalarTypesRow(
      AllScalarTypesId(1004L),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      "required_only"
    )
    val inserted = repo.insert(row)
    assertEquals("required_only", inserted.colNotNull)
    assertTrue(inserted.colTinyint.isEmpty)
    assertTrue(inserted.colUuid.isEmpty)
    assertTrue(inserted.colJson.isEmpty)
  }

  @Test def testIntegerTypes(): Unit = withConnection {
    val inserted = repo.insert(createSampleRow(1010L))
    assertEquals(Some(42.toByte), inserted.colTinyint)
    assertEquals(Some(1000.toShort), inserted.colSmallint)
    assertEquals(Some(100000), inserted.colInt)
    assertEquals(Some(10000000000L), inserted.colInteger)
    assertEquals(Some(9223372036854775000L), inserted.colBigint)
  }

  @Test def testBooleanType(): Unit = withConnection {
    val inserted = repo.insert(createSampleRow(1011L))
    assertEquals(Some(true), inserted.colBoolean)
    repo.update(inserted.copy(colBoolean = Some(false)))
    assertEquals(Some(false), repo.selectById(inserted.id).get.colBoolean)
  }

  @Test def testFloatingPointTypes(): Unit = withConnection {
    val inserted = repo.insert(createSampleRow(1012L))
    assertEquals(Some(3.14159), inserted.colReal)
    assertEquals(Some(2.718281828), inserted.colDouble)
    assertEquals(Some(1.5f), inserted.colFloat)
  }

  @Test def testDecimalAndNumericTypes(): Unit = withConnection {
    val inserted = repo.insert(createSampleRow(1013L))
    assertEquals(0, BigDecimal("12345.67").compare(inserted.colDecimal.get))
    assertEquals(0, BigDecimal("999.999").compare(inserted.colNumeric.get))
  }

  @Test def testTextTypes(): Unit = withConnection {
    val inserted = repo.insert(createSampleRow(1014L))
    assertEquals(Some("text content"), inserted.colText)
    assertEquals(Some("varchar_value"), inserted.colVarchar)
    assertEquals(Some("char5"), inserted.colChar)
    assertEquals(Some("clob content"), inserted.colClob)
  }

  @Test def testBlobAndBinaryTypes(): Unit = withConnection {
    val blob = Array[Byte](0, 1, 2, -1, -2)
    val bin = Array[Byte](-54, -2, -70, -66)
    val inserted = repo.insert(createSampleRow(1015L).copy(colBlob = Some(blob), colBinary = Some(bin)))
    assertArrayEquals(blob, inserted.colBlob.get)
    assertArrayEquals(bin, inserted.colBinary.get)
  }

  @Test def testDateTimeTypes(): Unit = withConnection {
    val inserted = repo.insert(createSampleRow(1016L))
    assertEquals(Some(LocalDate.of(2025, 1, 15)), inserted.colDate)
    assertEquals(Some(LocalTime.of(14, 30, 45)), inserted.colTime)
    assertEquals(Some(LocalDateTime.of(2025, 1, 15, 14, 30, 45)), inserted.colDatetime)
    assertEquals(Some(LocalDateTime.of(2025, 6, 1, 9, 0, 0)), inserted.colTimestamp)
  }

  @Test def testUuidType(): Unit = withConnection {
    val uuid = UUID.randomUUID()
    val inserted = repo.insert(createSampleRow(1017L).copy(colUuid = Some(uuid)))
    assertEquals(Some(uuid), inserted.colUuid)
  }

  @Test def testJsonType(): Unit = withConnection {
    val json = Json("{\"name\":\"test\"}")
    val inserted = repo.insert(createSampleRow(1018L).copy(colJson = Some(json)))
    assertTrue(inserted.colJson.get.value.contains("name"))
  }

  @Test def testSelectAllReturnsInsertedRow(): Unit = withConnection {
    val row = createSampleRow(1019L)
    repo.insert(row)
    val all = repo.selectAll
    assertTrue(all.size >= 2)
    assertTrue(all.exists(_.id.value == 1019L))
  }
}
