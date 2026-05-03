package testdb

import dev.typr.foundations.data.Xml
import org.junit.Assert._
import org.junit.Test
import testdb.db2test._

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.util.Random

/** Comprehensive tests for all DB2 scalar data types. Tests the db2test table covering DB2 numeric, string, binary, and date/time types.
  */
class AllTypesTest {
  private val testInsert = TestInsert(Random(1275531346))
  private val db2testRepo = Db2testRepoImpl()

  @Test
  def testInsertAndSelectAllTypes(): Unit = withConnection {
    val binary = new Array[Byte](16) // BINARY(16) requires exactly 16 bytes
    val varbinary = Array[Byte](6, 7, 8)
    val blob = Array[Byte](9, 10, 11, 12)

    // Override graphicCol to fit GRAPHIC(10) column size limit
    val row = testInsert.Db2test(binary, varbinary, blob, graphicCol = "test      ")

    assertNotNull(row)
    assertNotNull(row.intCol)

    val found = db2testRepo.selectById(row.intCol)
    assertTrue(found.isDefined)
    assertEquals(row.intCol, found.get.intCol)
  }

  @Test
  def testIntegerTypes(): Unit = withConnection {
    val binary = new Array[Byte](16)
    val row = testInsert.Db2test(
      binaryCol = binary,
      varbinaryCol = binary,
      blobCol = binary,
      smallintCol = 123.toShort,
      intCol = Db2testId(456),
      bigintCol = 789L,
      graphicCol = "test      "
    )

    assertEquals(123.toShort, row.smallintCol)
    assertEquals(Db2testId(456), row.intCol)
    assertEquals(789L, row.bigintCol)
  }

  @Test
  def testDecimalTypes(): Unit = withConnection {
    val binary = new Array[Byte](16)
    val row = testInsert.Db2test(
      binaryCol = binary,
      varbinaryCol = binary,
      blobCol = binary,
      decimalCol = BigDecimal("1234.56"),
      numericCol = BigDecimal("7890.12"),
      graphicCol = "test      "
    )

    assertEquals(0, BigDecimal("1234.56").compareTo(row.decimalCol))
  }

  @Test
  def testStringTypes(): Unit = withConnection {
    val binary = new Array[Byte](16)
    val row = testInsert.Db2test(
      binaryCol = binary,
      varbinaryCol = binary,
      blobCol = binary,
      charCol = "char val  ",
      varcharCol = "varchar value",
      clobCol = "clob content",
      graphicCol = "test      "
    )

    assertEquals("char val  ", row.charCol)
    assertEquals("varchar value", row.varcharCol)
    assertEquals("clob content", row.clobCol)
  }

  @Test
  def testDateTimeTypes(): Unit = withConnection {
    val binary = new Array[Byte](16)
    val date = LocalDate.of(2025, 6, 15)
    val time = LocalTime.of(14, 30, 45)
    val timestamp = LocalDateTime.of(2025, 6, 15, 14, 30, 45)

    val row = testInsert.Db2test(
      binaryCol = binary,
      varbinaryCol = binary,
      blobCol = binary,
      dateCol = date,
      timeCol = time,
      timestampCol = timestamp,
      graphicCol = "test      "
    )

    assertEquals(date, row.dateCol)
    assertEquals(time, row.timeCol)
    assertEquals(timestamp, row.timestampCol)
  }

  @Test
  def testBinaryTypes(): Unit = withConnection {
    val binary = Array[Byte](0x00, 0x01, 0xff.toByte, 0xfe.toByte, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    val varbinary = Array[Byte](0x10, 0x20, 0x30)
    val blob = Array[Byte](0xab.toByte, 0xcd.toByte, 0xef.toByte)

    val row = testInsert.Db2test(binary, varbinary, blob, graphicCol = "test      ")

    val found = db2testRepo.selectById(row.intCol).get
    assertArrayEquals(varbinary, found.varbinaryCol)
  }

  @Test
  def testNullableTypes(): Unit = withConnection {
    val row = testInsert.Db2testnull(graphicCol = Some("test      "))
    assertNotNull(row)
  }

  @Test
  def testNullableTypesWithValues(): Unit = withConnection {
    val row = testInsert.Db2testnull(
      smallintCol = Some(100.toShort),
      intCol = Some(200),
      charCol = Some("test"),
      graphicCol = Some("test      ")
    )

    assertEquals(Some(100.toShort), row.smallintCol)
    assertEquals(Some(200), row.intCol)
  }

  @Test
  def testUpdate(): Unit = withConnection {
    val binary = new Array[Byte](16)
    val inserted = testInsert.Db2test(binary, binary, binary, graphicCol = "test      ")

    val updated = inserted.copy(
      varcharCol = "updated varchar",
      decimalCol = BigDecimal("9999.99")
    )

    val wasUpdated = db2testRepo.update(updated)
    assertTrue(wasUpdated)

    val found = db2testRepo.selectById(inserted.intCol).get
    assertEquals("updated varchar", found.varcharCol)
  }

  @Test
  def testDelete(): Unit = withConnection {
    val binary = new Array[Byte](16)
    val inserted = testInsert.Db2test(binary, binary, binary, graphicCol = "test      ")

    val deleted = db2testRepo.deleteById(inserted.intCol)
    assertTrue(deleted)

    val found = db2testRepo.selectById(inserted.intCol)
    assertFalse(found.isDefined)
  }

  @Test
  def testBooleanType(): Unit = withConnection {
    val binary = new Array[Byte](16)
    val rowTrue = testInsert.Db2test(
      binaryCol = binary,
      varbinaryCol = binary,
      blobCol = binary,
      boolCol = true,
      graphicCol = "test      "
    )
    val rowFalse = testInsert.Db2test(
      binaryCol = binary,
      varbinaryCol = binary,
      blobCol = binary,
      boolCol = false,
      graphicCol = "test      "
    )

    assertTrue(rowTrue.boolCol)
    assertFalse(rowFalse.boolCol)
  }

  @Test
  def testXmlType(): Unit = withConnection {
    val binary = new Array[Byte](16)
    val xml = new Xml("<root><element>value</element></root>")
    val row = testInsert.Db2test(
      binaryCol = binary,
      varbinaryCol = binary,
      blobCol = binary,
      xmlCol = xml,
      graphicCol = "test      "
    )

    assertNotNull(row.xmlCol)
    assertTrue(row.xmlCol.value.contains("element"))
  }
}
