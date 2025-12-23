package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.JsonEquals.assertJsonEquals
import testdb.mariatest.MariatestId
import testdb.mariatest.MariatestRepoImpl
import testdb.mariatest.MariatestRow
import testdb.mariatestnull.MariatestnullRepoImpl
import testdb.mariatestnull.MariatestnullRow
import typo.data.maria.Inet4
import typo.data.maria.Inet6
import typo.data.maria.MariaSet
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year

/**
 * Tests for all MariaDB data types - equivalent to PostgreSQL ArrayTest for MariaDB.
 */
class MariaDbTypeTest {
    private val mariatestRepo = MariatestRepoImpl()
    private val mariatestnullRepo = MariatestnullRepoImpl()

    companion object {
        fun mariaTestRow(): MariatestRow = MariatestRow(
            // Integer types
            tinyintCol = 127.toByte(),
            smallintCol = 32767.toShort(),
            mediumintCol = 8388607,
            intCol = MariatestId(42),
            bigintCol = 9223372036854775807L,
            // Unsigned integer types
            tinyintUCol = 255.toShort(),
            smallintUCol = 65535,
            mediumintUCol = 16777215,
            intUCol = 4294967295L,
            bigintUCol = BigInteger("18446744073709551615"),
            // Decimal types
            decimalCol = BigDecimal("12345.6789"),
            numericCol = BigDecimal("98765.4321"),
            floatCol = 3.14f,
            doubleCol = 2.718281828459045,
            // Boolean
            boolCol = true,
            // Bit types
            bitCol = byteArrayOf(0xF0.toByte()),
            bit1Col = byteArrayOf(1.toByte()),
            // String types
            charCol = "fixed     ",
            varcharCol = "variable",
            tinytextCol = "tiny text",
            textCol = "text content",
            mediumtextCol = "medium text",
            longtextCol = "long text",
            // Binary types
            binaryCol = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A),
            varbinaryCol = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            tinyblobCol = byteArrayOf(0x01),
            blobCol = byteArrayOf(0x02, 0x03),
            mediumblobCol = byteArrayOf(0x04, 0x05, 0x06),
            longblobCol = byteArrayOf(0x07, 0x08, 0x09, 0x0A),
            // Date/time types
            dateCol = LocalDate.of(2024, 12, 2),
            timeCol = LocalTime.of(12, 30, 45),
            timeFspCol = LocalTime.of(12, 30, 45, 123456000),
            datetimeCol = LocalDateTime.of(2024, 12, 2, 14, 30, 45),
            datetimeFspCol = LocalDateTime.of(2024, 12, 2, 14, 30, 45, 123456000),
            timestampCol = LocalDateTime.of(2024, 12, 2, 10, 0, 0),
            timestampFspCol = LocalDateTime.of(2024, 12, 2, 10, 0, 0, 123456000),
            yearCol = Year.of(2024),
            // Enum and set
            enumCol = "a",
            setCol = MariaSet.of("x", "y"),
            // JSON
            jsonCol = "{\"key\": \"value\"}",
            // Network types
            inet4Col = Inet4("192.168.1.1"),
            inet6Col = Inet6("::1")
        )

        fun mariatestnullRowEmpty(): MariatestnullRow = MariatestnullRow(
            tinyintCol = null,
            smallintCol = null,
            mediumintCol = null,
            intCol = null,
            bigintCol = null,
            tinyintUCol = null,
            smallintUCol = null,
            mediumintUCol = null,
            intUCol = null,
            bigintUCol = null,
            decimalCol = null,
            numericCol = null,
            floatCol = null,
            doubleCol = null,
            boolCol = null,
            bitCol = null,
            bit1Col = null,
            charCol = null,
            varcharCol = null,
            tinytextCol = null,
            textCol = null,
            mediumtextCol = null,
            longtextCol = null,
            binaryCol = null,
            varbinaryCol = null,
            tinyblobCol = null,
            blobCol = null,
            mediumblobCol = null,
            longblobCol = null,
            dateCol = null,
            timeCol = null,
            timeFspCol = null,
            datetimeCol = null,
            datetimeFspCol = null,
            timestampCol = null,
            timestampFspCol = null,
            yearCol = null,
            enumCol = null,
            setCol = null,
            jsonCol = null,
            inet4Col = null,
            inet6Col = null
        )

        fun mariatestnullRowWithValues(): MariatestnullRow = MariatestnullRow(
            // Integer types
            tinyintCol = 127.toByte(),
            smallintCol = 32767.toShort(),
            mediumintCol = 8388607,
            intCol = 42,
            bigintCol = 9223372036854775807L,
            // Unsigned integer types
            tinyintUCol = 255.toShort(),
            smallintUCol = 65535,
            mediumintUCol = 16777215,
            intUCol = 4294967295L,
            bigintUCol = BigInteger("18446744073709551615"),
            // Decimal types
            decimalCol = BigDecimal("12345.6789"),
            numericCol = BigDecimal("98765.4321"),
            floatCol = 3.14f,
            doubleCol = 2.718281828459045,
            // Boolean
            boolCol = true,
            // Bit types
            bitCol = byteArrayOf(0xF0.toByte()),
            bit1Col = byteArrayOf(1.toByte()),
            // String types
            charCol = "fixed     ",
            varcharCol = "variable",
            tinytextCol = "tiny text",
            textCol = "text content",
            mediumtextCol = "medium text",
            longtextCol = "long text",
            // Binary types
            binaryCol = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A),
            varbinaryCol = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            tinyblobCol = byteArrayOf(0x01),
            blobCol = byteArrayOf(0x02, 0x03),
            mediumblobCol = byteArrayOf(0x04, 0x05, 0x06),
            longblobCol = byteArrayOf(0x07, 0x08, 0x09, 0x0A),
            // Date/time types
            dateCol = LocalDate.of(2024, 12, 2),
            timeCol = LocalTime.of(12, 30, 45),
            timeFspCol = LocalTime.of(12, 30, 45, 123456000),
            datetimeCol = LocalDateTime.of(2024, 12, 2, 14, 30, 45),
            datetimeFspCol = LocalDateTime.of(2024, 12, 2, 14, 30, 45, 123456000),
            timestampCol = LocalDateTime.of(2024, 12, 2, 10, 0, 0),
            timestampFspCol = LocalDateTime.of(2024, 12, 2, 10, 0, 0, 123456000),
            yearCol = Year.of(2024),
            // Enum and set
            enumCol = "a",
            setCol = MariaSet.of("x", "y"),
            // JSON
            jsonCol = "{\"key\": \"value\"}",
            // Network types
            inet4Col = Inet4("192.168.1.1"),
            inet6Col = Inet6("::1")
        )
    }

    @Test
    fun canInsertMariatestRows() {
        WithConnection.run { c ->
            val before = mariaTestRow()
            val after = mariatestRepo.insert(before, c)
            // Compare key scalar fields - timestamps will differ due to defaults
            assertEquals(before.tinyintCol, after.tinyintCol)
            assertEquals(before.smallintCol, after.smallintCol)
            assertEquals(before.mediumintCol, after.mediumintCol)
            assertEquals(before.intCol, after.intCol)
            assertEquals(before.bigintCol, after.bigintCol)
            assertEquals(before.boolCol, after.boolCol)
            assertEquals(before.varcharCol, after.varcharCol)
            assertEquals(before.textCol, after.textCol)
            assertEquals(before.dateCol, after.dateCol)
            assertEquals(before.timeCol, after.timeCol)
            assertEquals(before.yearCol, after.yearCol)
            assertEquals(before.enumCol, after.enumCol)
            assertEquals(before.jsonCol, after.jsonCol)
            assertEquals(before.inet4Col, after.inet4Col)
            assertEquals(before.inet6Col, after.inet6Col)
        }
    }

    @Test
    fun canInsertNullMariatestnullRows() {
        WithConnection.run { c ->
            val before = mariatestnullRowEmpty()
            val after = mariatestnullRepo.insert(before, c)
            assertJsonEquals(before, after)
        }
    }

    @Test
    fun canInsertNonNullMariatestnullRows() {
        WithConnection.run { c ->
            val before = mariatestnullRowWithValues()
            val after = mariatestnullRepo.insert(before, c)
            // Verify key fields match
            assertEquals(before.tinyintCol, after.tinyintCol)
            assertEquals(before.smallintCol, after.smallintCol)
            assertEquals(before.intCol, after.intCol)
            assertEquals(before.bigintCol, after.bigintCol)
            assertEquals(before.boolCol, after.boolCol)
            assertEquals(before.varcharCol, after.varcharCol)
            assertEquals(before.dateCol, after.dateCol)
            assertEquals(before.yearCol, after.yearCol)
            assertEquals(before.enumCol, after.enumCol)
            assertEquals(before.inet4Col, after.inet4Col)
            assertEquals(before.inet6Col, after.inet6Col)
        }
    }

    @Test
    fun canSelectAllMariatestRows() {
        WithConnection.run { c ->
            val row1 = mariaTestRow()
            val row2 = mariaTestRow().copy(intCol = MariatestId(43))
            mariatestRepo.insert(row1, c)
            mariatestRepo.insert(row2, c)
            val all = mariatestRepo.selectAll(c)
            assertEquals(2, all.size)
        }
    }

    @Test
    fun canSelectByIdMariatestRows() {
        WithConnection.run { c ->
            val inserted = mariatestRepo.insert(mariaTestRow(), c)
            val selected = mariatestRepo.selectById(inserted.intCol, c)
            assertNotNull(selected)
            assertEquals(inserted.intCol, selected!!.intCol)
        }
    }

    @Test
    fun canSelectByIdsMariatestRows() {
        WithConnection.run { c ->
            val row1 = mariatestRepo.insert(mariaTestRow(), c)
            val row2 = mariatestRepo.insert(mariaTestRow().copy(intCol = MariatestId(43)), c)
            val ids = arrayOf(row1.intCol, row2.intCol)
            val selected = mariatestRepo.selectByIds(ids, c)
            assertEquals(2, selected.size)
        }
    }

    @Test
    fun canUpdateMariatestRows() {
        WithConnection.run { c ->
            val inserted = mariatestRepo.insert(mariaTestRow(), c)
            val updated = inserted.copy(varcharCol = "updated")
            mariatestRepo.update(updated, c)
            val selected = mariatestRepo.selectById(inserted.intCol, c)
            assertNotNull(selected)
            assertEquals("updated", selected!!.varcharCol)
        }
    }

    @Test
    fun canDeleteMariatestRows() {
        WithConnection.run { c ->
            val inserted = mariatestRepo.insert(mariaTestRow(), c)
            val deleted = mariatestRepo.deleteById(inserted.intCol, c)
            assertTrue(deleted)
            val selected = mariatestRepo.selectById(inserted.intCol, c)
            assertNull(selected)
        }
    }

    @Test
    fun canDeleteByIdsMariatestRows() {
        WithConnection.run { c ->
            val row1 = mariatestRepo.insert(mariaTestRow(), c)
            val row2 = mariatestRepo.insert(mariaTestRow().copy(intCol = MariatestId(43)), c)
            val ids = arrayOf(row1.intCol, row2.intCol)
            val count = mariatestRepo.deleteByIds(ids, c)
            assertEquals(2, count)
            val all = mariatestRepo.selectAll(c)
            assertEquals(0, all.size)
        }
    }

    @Test
    fun canUpsertMariatestRows() {
        WithConnection.run { c ->
            val row = mariaTestRow()
            val inserted = mariatestRepo.upsert(row, c)
            assertEquals(row.intCol, inserted.intCol)

            val updated = inserted.copy(varcharCol = "upserted")
            val upserted = mariatestRepo.upsert(updated, c)
            assertEquals("upserted", upserted.varcharCol)
        }
    }

    @Test
    fun canQueryMariatestnullWithDSL() {
        WithConnection.run { c ->
            val row = mariatestnullRepo.insert(mariatestnullRowWithValues(), c)

            // Test DSL select.where() for various types (use !! since we know the test data has non-null values)
            assertEquals(row.boolCol, mariatestnullRepo.select().where { p -> p.boolCol().isEqual(row.boolCol!!) }.toList(c)[0].boolCol)
            assertEquals(row.varcharCol, mariatestnullRepo.select().where { p -> p.varcharCol().isEqual(row.varcharCol!!) }.toList(c)[0].varcharCol)
            assertEquals(row.intCol, mariatestnullRepo.select().where { p -> p.intCol().isEqual(row.intCol!!) }.toList(c)[0].intCol)
            assertEquals(row.bigintCol, mariatestnullRepo.select().where { p -> p.bigintCol().isEqual(row.bigintCol!!) }.toList(c)[0].bigintCol)
            assertEquals(row.dateCol, mariatestnullRepo.select().where { p -> p.dateCol().isEqual(row.dateCol!!) }.toList(c)[0].dateCol)
            assertEquals(row.yearCol, mariatestnullRepo.select().where { p -> p.yearCol().isEqual(row.yearCol!!) }.toList(c)[0].yearCol)
            assertEquals(row.enumCol, mariatestnullRepo.select().where { p -> p.enumCol().isEqual(row.enumCol!!) }.toList(c)[0].enumCol)
        }
    }

    @Test
    fun canQueryMariatestWithDSL() {
        WithConnection.run { c ->
            val row = mariatestRepo.insert(mariaTestRow(), c)

            // Test DSL update.setValue() for various types
            mariatestRepo.update().setValue({ p -> p.boolCol() }, row.boolCol).where { p -> p.intCol().isEqual(row.intCol) }.execute(c)
            mariatestRepo.update().setValue({ p -> p.varcharCol() }, row.varcharCol).where { p -> p.intCol().isEqual(row.intCol) }.execute(c)
            mariatestRepo.update().setValue({ p -> p.textCol() }, row.textCol).where { p -> p.intCol().isEqual(row.intCol) }.execute(c)
            mariatestRepo.update().setValue({ p -> p.bigintCol() }, row.bigintCol).where { p -> p.intCol().isEqual(row.intCol) }.execute(c)
            mariatestRepo.update().setValue({ p -> p.dateCol() }, row.dateCol).where { p -> p.intCol().isEqual(row.intCol) }.execute(c)
            mariatestRepo.update().setValue({ p -> p.timeCol() }, row.timeCol).where { p -> p.intCol().isEqual(row.intCol) }.execute(c)
            mariatestRepo.update().setValue({ p -> p.yearCol() }, row.yearCol).where { p -> p.intCol().isEqual(row.intCol) }.execute(c)
            mariatestRepo.update().setValue({ p -> p.enumCol() }, row.enumCol).where { p -> p.intCol().isEqual(row.intCol) }.execute(c)
            mariatestRepo.update().setValue({ p -> p.jsonCol() }, row.jsonCol).where { p -> p.intCol().isEqual(row.intCol) }.execute(c)
        }
    }
}
