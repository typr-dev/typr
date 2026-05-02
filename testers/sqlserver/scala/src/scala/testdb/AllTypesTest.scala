package testdb

import org.junit.Assert._
import org.junit.Test
import testdb.all_scalar_types._
import testdb.customtypes.Defaulted

import java.time._
import java.util.UUID

/** Comprehensive tests for all SQL Server scalar data types. Tests the all_scalar_types table which has 38 columns covering all supported SQL Server types.
  */
class AllTypesTest {
  private val repo = AllScalarTypesRepoImpl()

  def createSampleRow(): AllScalarTypesRowUnsaved = {
    AllScalarTypesRowUnsaved(
      colTinyint = Some(dev.typr.foundations.data.Uint1(255)),
      colSmallint = Some(1000.toShort),
      colInt = Some(100000),
      colBigint = Some(10000000000L),
      colDecimal = Some(BigDecimal("12345.6789")),
      colNumeric = Some(BigDecimal("999.99")),
      colMoney = Some(BigDecimal("922337203685477.5807")),
      colSmallmoney = Some(BigDecimal("214748.3647")),
      colReal = Some(3.14f),
      colFloat = Some(2.718281828),
      colBit = Some(true),
      colChar = Some("char_val  "),
      colVarchar = Some("varchar_value"),
      colVarcharMax = Some("varchar max content"),
      colText = Some("text content"),
      colNchar = Some("nchar_val "),
      colNvarchar = Some("nvarchar_value 中文"),
      colNvarcharMax = Some("nvarchar max content 日本語"),
      colNtext = Some("ntext content"),
      colBinary = Some(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
      colVarbinary = Some(Array[Byte](1, 2, 3)),
      colVarbinaryMax = Some(Array[Byte](4, 5, 6, 7, 8)),
      colImage = Some(Array[Byte](9, 10, 11)),
      colDate = Some(LocalDate.of(2025, 1, 15)),
      colTime = Some(LocalTime.of(14, 30, 45, 123456700)),
      colDatetime = Some(LocalDateTime.of(2025, 1, 15, 14, 30, 45)),
      colSmalldatetime = Some(LocalDateTime.of(2025, 1, 15, 14, 30)),
      colDatetime2 = Some(LocalDateTime.of(2025, 1, 15, 14, 30, 45, 123456700)),
      colDatetimeoffset = Some(OffsetDateTime.of(2025, 1, 15, 14, 30, 45, 0, ZoneOffset.ofHours(-5))),
      colUniqueidentifier = Some(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
      colXml = Some(dev.typr.foundations.data.Xml("<root><element>value</element></root>")),
      colJson = Some(dev.typr.foundations.data.Json("{\"key\": \"value\"}")),
      colHierarchyid = None,
      colGeography = None,
      colGeometry = None,
      colNotNull = Defaulted.Provided("test_value")
    )
  }

  @Test
  def testInsertAndSelectAllTypes(): Unit = withConnection {
    val row = createSampleRow()
    val inserted = repo.insert(row)

    assertNotNull(inserted)
    assertNotNull(inserted.id)
    assertEquals(row.colTinyint, inserted.colTinyint)
    assertEquals(row.colSmallint, inserted.colSmallint)
    assertEquals(row.colInt, inserted.colInt)
    assertEquals(row.colBigint, inserted.colBigint)
    assertEquals(row.colBit, inserted.colBit)
    assertEquals(row.colVarchar, inserted.colVarchar)
    assertEquals(row.colNvarchar, inserted.colNvarchar)
    assertEquals(row.colDate, inserted.colDate)
    assertEquals(row.colUniqueidentifier, inserted.colUniqueidentifier)

    assertNotNull(inserted.colRowversion)

    val found = repo.selectById(inserted.id)
    assertTrue(found.isDefined)
    assertEquals(inserted.id, found.get.id)
  }

  @Test
  def testUpdateAllTypes(): Unit = withConnection {
    val row = createSampleRow()
    val inserted = repo.insert(row)

    val updated = inserted.copy(
      colVarchar = Some("updated_varchar"),
      colNvarchar = Some("updated_nvarchar"),
      colDecimal = Some(BigDecimal("9999.9999")),
      colBit = Some(false)
    )

    val wasUpdated = repo.update(updated)
    assertTrue(wasUpdated)

    val found = repo.selectById(inserted.id).get
    assertEquals(Some("updated_varchar"), found.colVarchar)
    assertEquals(Some("updated_nvarchar"), found.colNvarchar)
    assertEquals(Some(BigDecimal("9999.9999")), found.colDecimal)
    assertEquals(Some(false), found.colBit)
  }

  @Test
  def testDeleteAllTypes(): Unit = withConnection {
    val row = createSampleRow()
    val inserted = repo.insert(row)

    val deleted = repo.deleteById(inserted.id)
    assertTrue(deleted)

    val found = repo.selectById(inserted.id)
    assertFalse(found.isDefined)
  }

  @Test
  def testInsertWithNulls(): Unit = withConnection {
    val row = AllScalarTypesRowUnsaved()
    val inserted = repo.insert(row)

    assertNotNull(inserted)
    assertNotNull(inserted.id)

    assertTrue(inserted.colTinyint.isEmpty)
    assertTrue(inserted.colSmallint.isEmpty)
    assertTrue(inserted.colInt.isEmpty)
    assertTrue(inserted.colBit.isEmpty)
    assertTrue(inserted.colVarchar.isEmpty)
    assertTrue(inserted.colDate.isEmpty)
    assertTrue(inserted.colUniqueidentifier.isEmpty)

    assertEquals("default_value", inserted.colNotNull)
    assertNotNull(inserted.colRowversion)
  }

  @Test
  def testIntegerTypes(): Unit = withConnection {
    val row = createSampleRow()
    val inserted = repo.insert(row)

    assertEquals(Some(dev.typr.foundations.data.Uint1(255)), inserted.colTinyint)
    assertEquals(Some(1000.toShort), inserted.colSmallint)
    assertEquals(Some(100000), inserted.colInt)
    assertEquals(Some(10000000000L), inserted.colBigint)
  }

  @Test
  def testMoneyTypes(): Unit = withConnection {
    val row = createSampleRow()
    val inserted = repo.insert(row)

    assertEquals(Some(BigDecimal("922337203685477.5807")), inserted.colMoney)
    assertEquals(Some(BigDecimal("214748.3647")), inserted.colSmallmoney)
  }

  @Test
  def testDateTimeTypes(): Unit = withConnection {
    val row = createSampleRow()
    val inserted = repo.insert(row)

    assertEquals(Some(LocalDate.of(2025, 1, 15)), inserted.colDate)
    assertNotNull(inserted.colTime)
    assertNotNull(inserted.colDatetime)
    assertNotNull(inserted.colDatetime2)
    assertNotNull(inserted.colDatetimeoffset)
  }

  @Test
  def testUuidType(): Unit = withConnection {
    val uuid = UUID.randomUUID()
    val row = createSampleRow().copy(colUniqueidentifier = Some(uuid))
    val inserted = repo.insert(row)

    assertEquals(Some(uuid), inserted.colUniqueidentifier)
  }

  @Test
  def testXmlType(): Unit = withConnection {
    val xml = dev.typr.foundations.data.Xml("<person><name>John</name><age>30</age></person>")
    val row = createSampleRow().copy(colXml = Some(xml))
    val inserted = repo.insert(row)

    assertTrue(inserted.colXml.isDefined)
    assertTrue(inserted.colXml.get.value.contains("John"))
  }

  @Test
  def testJsonType(): Unit = withConnection {
    val json = dev.typr.foundations.data.Json("{\"name\": \"test\", \"values\": [1, 2, 3]}")
    val row = createSampleRow().copy(colJson = Some(json))
    val inserted = repo.insert(row)

    assertTrue(inserted.colJson.isDefined)
    assertTrue(inserted.colJson.get.value.contains("name"))
  }

  @Test
  def testBinaryTypes(): Unit = withConnection {
    val binaryData = Array[Byte](0x00, 0x01, 0x02, 0xff.toByte, 0xfe.toByte)
    val row = createSampleRow().copy(colVarbinary = Some(binaryData))
    val inserted = repo.insert(row)

    assertTrue(inserted.colVarbinary.isDefined)
    assertArrayEquals(binaryData, inserted.colVarbinary.get)
  }

  @Test
  def testUnicodeStrings(): Unit = withConnection {
    val unicode = "Hello 世界 🌍 مرحبا"
    val row = createSampleRow().copy(colNvarchar = Some(unicode))
    val inserted = repo.insert(row)

    assertEquals(Some(unicode), inserted.colNvarchar)
  }

  @Test
  def testRowversionAutoGenerated(): Unit = withConnection {
    val row1 = repo.insert(createSampleRow())
    val row2 = repo.insert(createSampleRow())

    assertNotNull(row1.colRowversion)
    assertNotNull(row2.colRowversion)
    assertFalse(java.util.Arrays.equals(row1.colRowversion, row2.colRowversion))
  }
}
