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
import java.util.Optional

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
            tinyintCol = Optional.empty(),
            smallintCol = Optional.empty(),
            mediumintCol = Optional.empty(),
            intCol = Optional.empty(),
            bigintCol = Optional.empty(),
            tinyintUCol = Optional.empty(),
            smallintUCol = Optional.empty(),
            mediumintUCol = Optional.empty(),
            intUCol = Optional.empty(),
            bigintUCol = Optional.empty(),
            decimalCol = Optional.empty(),
            numericCol = Optional.empty(),
            floatCol = Optional.empty(),
            doubleCol = Optional.empty(),
            boolCol = Optional.empty(),
            bitCol = Optional.empty(),
            bit1Col = Optional.empty(),
            charCol = Optional.empty(),
            varcharCol = Optional.empty(),
            tinytextCol = Optional.empty(),
            textCol = Optional.empty(),
            mediumtextCol = Optional.empty(),
            longtextCol = Optional.empty(),
            binaryCol = Optional.empty(),
            varbinaryCol = Optional.empty(),
            tinyblobCol = Optional.empty(),
            blobCol = Optional.empty(),
            mediumblobCol = Optional.empty(),
            longblobCol = Optional.empty(),
            dateCol = Optional.empty(),
            timeCol = Optional.empty(),
            timeFspCol = Optional.empty(),
            datetimeCol = Optional.empty(),
            datetimeFspCol = Optional.empty(),
            timestampCol = Optional.empty(),
            timestampFspCol = Optional.empty(),
            yearCol = Optional.empty(),
            enumCol = Optional.empty(),
            setCol = Optional.empty(),
            jsonCol = Optional.empty(),
            inet4Col = Optional.empty(),
            inet6Col = Optional.empty()
        )

        fun mariatestnullRowWithValues(): MariatestnullRow = MariatestnullRow(
            // Integer types
            tinyintCol = Optional.of(127.toByte()),
            smallintCol = Optional.of(32767.toShort()),
            mediumintCol = Optional.of(8388607),
            intCol = Optional.of(42),
            bigintCol = Optional.of(9223372036854775807L),
            // Unsigned integer types
            tinyintUCol = Optional.of(255.toShort()),
            smallintUCol = Optional.of(65535),
            mediumintUCol = Optional.of(16777215),
            intUCol = Optional.of(4294967295L),
            bigintUCol = Optional.of(BigInteger("18446744073709551615")),
            // Decimal types
            decimalCol = Optional.of(BigDecimal("12345.6789")),
            numericCol = Optional.of(BigDecimal("98765.4321")),
            floatCol = Optional.of(3.14f),
            doubleCol = Optional.of(2.718281828459045),
            // Boolean
            boolCol = Optional.of(true),
            // Bit types
            bitCol = Optional.of(byteArrayOf(0xF0.toByte())),
            bit1Col = Optional.of(byteArrayOf(1.toByte())),
            // String types
            charCol = Optional.of("fixed     "),
            varcharCol = Optional.of("variable"),
            tinytextCol = Optional.of("tiny text"),
            textCol = Optional.of("text content"),
            mediumtextCol = Optional.of("medium text"),
            longtextCol = Optional.of("long text"),
            // Binary types
            binaryCol = Optional.of(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A)),
            varbinaryCol = Optional.of(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())),
            tinyblobCol = Optional.of(byteArrayOf(0x01)),
            blobCol = Optional.of(byteArrayOf(0x02, 0x03)),
            mediumblobCol = Optional.of(byteArrayOf(0x04, 0x05, 0x06)),
            longblobCol = Optional.of(byteArrayOf(0x07, 0x08, 0x09, 0x0A)),
            // Date/time types
            dateCol = Optional.of(LocalDate.of(2024, 12, 2)),
            timeCol = Optional.of(LocalTime.of(12, 30, 45)),
            timeFspCol = Optional.of(LocalTime.of(12, 30, 45, 123456000)),
            datetimeCol = Optional.of(LocalDateTime.of(2024, 12, 2, 14, 30, 45)),
            datetimeFspCol = Optional.of(LocalDateTime.of(2024, 12, 2, 14, 30, 45, 123456000)),
            timestampCol = Optional.of(LocalDateTime.of(2024, 12, 2, 10, 0, 0)),
            timestampFspCol = Optional.of(LocalDateTime.of(2024, 12, 2, 10, 0, 0, 123456000)),
            yearCol = Optional.of(Year.of(2024)),
            // Enum and set
            enumCol = Optional.of("a"),
            setCol = Optional.of(MariaSet.of("x", "y")),
            // JSON
            jsonCol = Optional.of("{\"key\": \"value\"}"),
            // Network types
            inet4Col = Optional.of(Inet4("192.168.1.1")),
            inet6Col = Optional.of(Inet6("::1"))
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
            assertTrue(selected.isPresent)
            assertEquals(inserted.intCol, selected.get().intCol)
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
            assertTrue(selected.isPresent)
            assertEquals("updated", selected.get().varcharCol)
        }
    }

    @Test
    fun canDeleteMariatestRows() {
        WithConnection.run { c ->
            val inserted = mariatestRepo.insert(mariaTestRow(), c)
            val deleted = mariatestRepo.deleteById(inserted.intCol, c)
            assertTrue(deleted)
            val selected = mariatestRepo.selectById(inserted.intCol, c)
            assertFalse(selected.isPresent)
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

            // Test DSL select.where() for various types
            assertEquals(row.boolCol, mariatestnullRepo.select().where { p -> p.boolCol().isEqual(row.boolCol.orElse(null)) }.toList(c)[0].boolCol)
            assertEquals(row.varcharCol, mariatestnullRepo.select().where { p -> p.varcharCol().isEqual(row.varcharCol.orElse(null)) }.toList(c)[0].varcharCol)
            assertEquals(row.intCol, mariatestnullRepo.select().where { p -> p.intCol().isEqual(row.intCol.orElse(null)) }.toList(c)[0].intCol)
            assertEquals(row.bigintCol, mariatestnullRepo.select().where { p -> p.bigintCol().isEqual(row.bigintCol.orElse(null)) }.toList(c)[0].bigintCol)
            assertEquals(row.dateCol, mariatestnullRepo.select().where { p -> p.dateCol().isEqual(row.dateCol.orElse(null)) }.toList(c)[0].dateCol)
            assertEquals(row.yearCol, mariatestnullRepo.select().where { p -> p.yearCol().isEqual(row.yearCol.orElse(null)) }.toList(c)[0].yearCol)
            assertEquals(row.enumCol, mariatestnullRepo.select().where { p -> p.enumCol().isEqual(row.enumCol.orElse(null)) }.toList(c)[0].enumCol)
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
