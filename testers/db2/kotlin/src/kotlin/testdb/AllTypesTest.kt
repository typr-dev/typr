package testdb

import dev.typr.foundations.data.Xml
import org.junit.Assert.*
import org.junit.Test
import testdb.db2test.*
import testdb.db2testnull.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Random

/**
 * Comprehensive tests for all DB2 scalar data types. Tests the db2test table covering DB2 numeric,
 * string, binary, and date/time types.
 */
class AllTypesTest {
    private val testInsert = TestInsert(Random(1088834394))
    private val db2testRepo = Db2testRepoImpl()
    private val db2testnullRepo = Db2testnullRepoImpl()

    @Test
    fun testInsertAndSelectAllTypes() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(1, 2, 3, 4, 5)
            val varbinary = byteArrayOf(6, 7, 8)
            val blob = byteArrayOf(9, 10, 11, 12)

            val row = testInsert.Db2test(
                charCol = "char val  ",
                varcharCol = "varchar",
                clobCol = "clob content",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = varbinary,
                blobCol = blob,
                xmlCol = Xml("<root/>"),
                c = c
            )

            assertNotNull(row)
            assertNotNull(row.intCol)

            val found = db2testRepo.selectById(row.intCol, c)
            assertNotNull(found)
            assertEquals(row.intCol, found!!.intCol)
        }
    }

    @Test
    fun testIntegerTypes() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(1)
            val row = testInsert.Db2test(
                charCol = "char      ",
                varcharCol = "varchar",
                clobCol = "clob",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = binary,
                blobCol = binary,
                xmlCol = Xml("<root/>"),
                smallintCol = 123.toShort(),
                intCol = Db2testId(456),
                bigintCol = 789L,
                c = c
            )

            assertEquals(123.toShort(), row.smallintCol)
            assertEquals(Db2testId(456), row.intCol)
            assertEquals(789L, row.bigintCol)
        }
    }

    @Test
    fun testDecimalTypes() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(1)
            val row = testInsert.Db2test(
                charCol = "char      ",
                varcharCol = "varchar",
                clobCol = "clob",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = binary,
                blobCol = binary,
                xmlCol = Xml("<root/>"),
                decimalCol = BigDecimal("1234.56"),
                numericCol = BigDecimal("7890.12"),
                c = c
            )

            assertEquals(0, BigDecimal("1234.56").compareTo(row.decimalCol))
        }
    }

    @Test
    fun testStringTypes() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(1)
            val row = testInsert.Db2test(
                charCol = "char val  ",
                varcharCol = "varchar value",
                clobCol = "clob content",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = binary,
                blobCol = binary,
                xmlCol = Xml("<root/>"),
                c = c
            )

            assertEquals("char val  ", row.charCol)
            assertEquals("varchar value", row.varcharCol)
            assertEquals("clob content", row.clobCol)
        }
    }

    @Test
    fun testDateTimeTypes() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(1)
            val date = LocalDate.of(2025, 6, 15)
            val time = LocalTime.of(14, 30, 45)
            val timestamp = LocalDateTime.of(2025, 6, 15, 14, 30, 45)

            val row = testInsert.Db2test(
                charCol = "char      ",
                varcharCol = "varchar",
                clobCol = "clob",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = binary,
                blobCol = binary,
                xmlCol = Xml("<root/>"),
                dateCol = date,
                timeCol = time,
                timestampCol = timestamp,
                c = c
            )

            assertEquals(date, row.dateCol)
            assertEquals(time, row.timeCol)
            assertEquals(timestamp, row.timestampCol)
        }
    }

    @Test
    fun testBinaryTypes() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0xFE.toByte())
            val varbinary = byteArrayOf(0x10, 0x20, 0x30)
            val blob = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())

            val row = testInsert.Db2test(
                charCol = "char      ",
                varcharCol = "varchar",
                clobCol = "clob",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = varbinary,
                blobCol = blob,
                xmlCol = Xml("<root/>"),
                c = c
            )

            val found = db2testRepo.selectById(row.intCol, c)!!
            assertArrayEquals(varbinary, found.varbinaryCol)
        }
    }

    @Test
    fun testNullableTypes() {
        Db2TestHelper.run { c ->
            val row = testInsert.Db2testnull(c = c)
            assertNotNull(row)
        }
    }

    @Test
    fun testNullableTypesWithValues() {
        Db2TestHelper.run { c ->
            val row = testInsert.Db2testnull(
                smallintCol = 100.toShort(),
                intCol = 200,
                charCol = "test",
                c = c
            )

            assertEquals(100.toShort(), row.smallintCol)
            assertEquals(200, row.intCol)
        }
    }

    @Test
    fun testUpdate() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(1)
            val inserted = testInsert.Db2test(
                charCol = "char      ",
                varcharCol = "varchar",
                clobCol = "clob",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = binary,
                blobCol = binary,
                xmlCol = Xml("<root/>"),
                c = c
            )

            val updated = inserted.copy(
                varcharCol = "updated varchar",
                decimalCol = BigDecimal("9999.99")
            )

            val wasUpdated = db2testRepo.update(updated, c)
            assertTrue(wasUpdated)

            val found = db2testRepo.selectById(inserted.intCol, c)!!
            assertEquals("updated varchar", found.varcharCol)
        }
    }

    @Test
    fun testDelete() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(1)
            val inserted = testInsert.Db2test(
                charCol = "char      ",
                varcharCol = "varchar",
                clobCol = "clob",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = binary,
                blobCol = binary,
                xmlCol = Xml("<root/>"),
                c = c
            )

            val deleted = db2testRepo.deleteById(inserted.intCol, c)
            assertTrue(deleted)

            val found = db2testRepo.selectById(inserted.intCol, c)
            assertNull(found)
        }
    }

    @Test
    fun testBooleanType() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(1)
            val rowTrue = testInsert.Db2test(
                charCol = "char      ",
                varcharCol = "varchar",
                clobCol = "clob",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = binary,
                blobCol = binary,
                xmlCol = Xml("<root/>"),
                boolCol = true,
                c = c
            )
            val rowFalse = testInsert.Db2test(
                charCol = "char      ",
                varcharCol = "varchar",
                clobCol = "clob",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = binary,
                blobCol = binary,
                xmlCol = Xml("<root/>"),
                boolCol = false,
                c = c
            )

            assertTrue(rowTrue.boolCol)
            assertFalse(rowFalse.boolCol)
        }
    }

    @Test
    fun testXmlType() {
        Db2TestHelper.run { c ->
            val binary = byteArrayOf(1)
            val xml = Xml("<root><element>value</element></root>")
            val row = testInsert.Db2test(
                charCol = "char      ",
                varcharCol = "varchar",
                clobCol = "clob",
                graphicCol = "graphic   ",
                vargraphicCol = "vargraphic",
                binaryCol = binary,
                varbinaryCol = binary,
                blobCol = binary,
                xmlCol = xml,
                c = c
            )

            assertNotNull(row.xmlCol)
            assertTrue(row.xmlCol.value.contains("element"))
        }
    }
}
