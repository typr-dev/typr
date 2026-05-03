package testdb

import dev.typr.foundations.data.{Json, Uint1, Uint2, Uint4, Uint8}
import org.junit.Assert._
import org.junit.Test
import testdb.all_scalar_types._

import java.math.BigInteger
import java.time._
import java.util.UUID

/** Comprehensive tests for all DuckDB scalar data types. Tests the all_scalar_types table which has 26 columns covering all supported DuckDB types.
  */
class AllTypesTest {
  private val repo = AllScalarTypesRepoImpl()

  def createSampleRow(id: Int): AllScalarTypesRow = {
    AllScalarTypesRow(
      id = AllScalarTypesId(id),
      colTinyint = Some(42.toByte),
      colSmallint = Some(1000.toShort),
      colInteger = Some(100000),
      colBigint = Some(10000000000L),
      colHugeint = Some(new BigInteger("123456789012345678901234567890")),
      colUtinyint = Some(Uint1(200)),
      colUsmallint = Some(Uint2(50000)),
      colUinteger = Some(Uint4(3000000000L)),
      colUbigint = Some(Uint8(new BigInteger("18446744073709551615"))),
      colFloat = Some(3.14f),
      colDouble = Some(2.718281828),
      colDecimal = Some(BigDecimal("12345.67")),
      colBoolean = Some(true),
      colVarchar = Some("varchar_value"),
      colText = Some("text content"),
      colBlob = Some(Array[Byte](1, 2, 3, 4, 5)),
      colDate = Some(LocalDate.of(2025, 1, 15)),
      colTime = Some(LocalTime.of(14, 30, 45)),
      colTimestamp = Some(LocalDateTime.of(2025, 1, 15, 14, 30, 45)),
      colTimestamptz = Some(OffsetDateTime.of(2025, 1, 15, 14, 30, 45, 0, ZoneOffset.UTC).toInstant),
      colInterval = Some(Duration.ofHours(2).plusMinutes(30)),
      colUuid = Some(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
      colJson = Some(Json("{\"key\": \"value\"}")),
      colMood = Some(Mood.happy),
      colNotNull = "required_value"
    )
  }

  @Test
  def testInsertAndSelectAllTypes(): Unit = withConnection {
    val row = createSampleRow(1001)
    val inserted = repo.insert(row)

    assertNotNull(inserted)
    assertEquals(row.id, inserted.id)
    assertEquals(row.colTinyint, inserted.colTinyint)
    assertEquals(row.colSmallint, inserted.colSmallint)
    assertEquals(row.colInteger, inserted.colInteger)
    assertEquals(row.colBigint, inserted.colBigint)
    assertEquals(row.colBoolean, inserted.colBoolean)
    assertEquals(row.colVarchar, inserted.colVarchar)
    assertEquals(row.colDate, inserted.colDate)
    assertEquals(row.colUuid, inserted.colUuid)
    assertEquals(row.colMood, inserted.colMood)
    assertEquals(row.colNotNull, inserted.colNotNull)

    val found = repo.selectById(inserted.id)
    assertTrue(found.isDefined)
    assertEquals(inserted.id, found.get.id)
  }

  @Test
  def testUpdateAllTypes(): Unit = withConnection {
    val row = createSampleRow(1002)
    val inserted = repo.insert(row)

    val updated = inserted.copy(
      colVarchar = Some("updated_varchar"),
      colDecimal = Some(BigDecimal("999.99")),
      colBoolean = Some(false),
      colMood = Some(Mood.sad)
    )

    val wasUpdated = repo.update(updated)
    assertTrue(wasUpdated)

    val found = repo.selectById(inserted.id).get
    assertEquals(Some("updated_varchar"), found.colVarchar)
    assertEquals(Some(BigDecimal("999.99")), found.colDecimal)
    assertEquals(Some(false), found.colBoolean)
    assertEquals(Some(Mood.sad), found.colMood)
  }

  @Test
  def testDeleteAllTypes(): Unit = withConnection {
    val row = createSampleRow(1003)
    val inserted = repo.insert(row)

    val deleted = repo.deleteById(inserted.id)
    assertTrue(deleted)

    val found = repo.selectById(inserted.id)
    assertFalse(found.isDefined)
  }

  @Test
  def testInsertWithNulls(): Unit = withConnection {
    val row = AllScalarTypesRow(
      id = AllScalarTypesId(1004),
      colTinyint = None,
      colSmallint = None,
      colInteger = None,
      colBigint = None,
      colHugeint = None,
      colUtinyint = None,
      colUsmallint = None,
      colUinteger = None,
      colUbigint = None,
      colFloat = None,
      colDouble = None,
      colDecimal = None,
      colBoolean = None,
      colVarchar = None,
      colText = None,
      colBlob = None,
      colDate = None,
      colTime = None,
      colTimestamp = None,
      colTimestamptz = None,
      colInterval = None,
      colUuid = None,
      colJson = None,
      colMood = None,
      colNotNull = "required_only"
    )

    val inserted = repo.insert(row)
    assertNotNull(inserted)

    assertTrue(inserted.colTinyint.isEmpty)
    assertTrue(inserted.colSmallint.isEmpty)
    assertTrue(inserted.colInteger.isEmpty)
    assertTrue(inserted.colBigint.isEmpty)
    assertTrue(inserted.colHugeint.isEmpty)
    assertTrue(inserted.colBoolean.isEmpty)
    assertTrue(inserted.colVarchar.isEmpty)
    assertTrue(inserted.colDate.isEmpty)
    assertTrue(inserted.colUuid.isEmpty)
    assertTrue(inserted.colMood.isEmpty)
    assertEquals("required_only", inserted.colNotNull)
  }

  @Test
  def testIntegerTypes(): Unit = withConnection {
    val row = createSampleRow(1010)
    val inserted = repo.insert(row)

    assertEquals(Some(42.toByte), inserted.colTinyint)
    assertEquals(Some(1000.toShort), inserted.colSmallint)
    assertEquals(Some(100000), inserted.colInteger)
    assertEquals(Some(10000000000L), inserted.colBigint)
    assertEquals(Some(new BigInteger("123456789012345678901234567890")), inserted.colHugeint)
  }

  @Test
  def testUnsignedTypes(): Unit = withConnection {
    val row = createSampleRow(1011)
    val inserted = repo.insert(row)

    assertEquals(Some(Uint1(200)), inserted.colUtinyint)
    assertEquals(Some(Uint2(50000)), inserted.colUsmallint)
    assertEquals(Some(Uint4(3000000000L)), inserted.colUinteger)
    assertEquals(Some(Uint8(new BigInteger("18446744073709551615"))), inserted.colUbigint)
  }

  @Test
  def testFloatingPointTypes(): Unit = withConnection {
    val row = createSampleRow(1012)
    val inserted = repo.insert(row)

    assertEquals(Some(3.14f), inserted.colFloat)
    assertEquals(Some(2.718281828), inserted.colDouble)
    assertEquals(Some(BigDecimal("12345.67")), inserted.colDecimal)
  }

  @Test
  def testDateTimeTypes(): Unit = withConnection {
    val row = createSampleRow(1013)
    val inserted = repo.insert(row)

    assertEquals(Some(LocalDate.of(2025, 1, 15)), inserted.colDate)
    assertEquals(Some(LocalTime.of(14, 30, 45)), inserted.colTime)
    assertEquals(Some(LocalDateTime.of(2025, 1, 15, 14, 30, 45)), inserted.colTimestamp)
    assertNotNull(inserted.colTimestamptz)
    assertNotNull(inserted.colInterval)
  }

  @Test
  def testUuidType(): Unit = withConnection {
    val uuid = UUID.randomUUID()
    val row = createSampleRow(1014).copy(colUuid = Some(uuid))
    val inserted = repo.insert(row)

    assertEquals(Some(uuid), inserted.colUuid)
  }

  @Test
  def testJsonType(): Unit = withConnection {
    val jsonValue = Json("{\"name\": \"test\", \"values\": [1, 2, 3]}")
    val row = createSampleRow(1015).copy(colJson = Some(jsonValue))
    val inserted = repo.insert(row)

    assertTrue(inserted.colJson.isDefined)
    assertTrue(inserted.colJson.get.value.contains("name"))
  }

  @Test
  def testBlobType(): Unit = withConnection {
    val blobData = Array[Byte](0x00, 0x01, 0x02, 0xff.toByte, 0xfe.toByte)
    val row = createSampleRow(1016).copy(colBlob = Some(blobData))
    val inserted = repo.insert(row)

    assertTrue(inserted.colBlob.isDefined)
    assertArrayEquals(blobData, inserted.colBlob.get)
  }

  @Test
  def testEnumType(): Unit = withConnection {
    for ((mood, idx) <- Mood.All.zipWithIndex) {
      val row = createSampleRow(1020 + idx).copy(colMood = Some(mood))
      val inserted = repo.insert(row)
      assertEquals(Some(mood), inserted.colMood)
    }
  }
}
