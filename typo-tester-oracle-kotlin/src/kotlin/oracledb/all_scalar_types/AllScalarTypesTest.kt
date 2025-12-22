package oracledb.all_scalar_types

import oracledb.OracleTestHelper
import oracledb.customtypes.Defaulted
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class AllScalarTypesTest {
    private val repo = AllScalarTypesRepoImpl()

    @Test
    fun testInsertAndSelectAllScalarTypes() {
        OracleTestHelper.run { c ->
            val unsaved = AllScalarTypesRowUnsaved(
                "test varchar2",
                BigDecimal("123.45"),
                LocalDateTime.of(2025, 1, 1, 12, 0),
                LocalDateTime.of(2025, 1, 1, 12, 0, 30, 123456000),
                "test clob content",
                "not null value",
                Defaulted.UseDefault()
            )

            val insertedRow = repo.insert(unsaved, c)

            assertNotNull(insertedRow)
            val inserted = repo.selectById(insertedRow.id, c)
            assertNotNull(inserted)
            assertEquals("test varchar2", inserted!!.colVarchar2)
            assertEquals(BigDecimal("123.45"), inserted.colNumber)
            assertNotNull(inserted.colDate)
            assertNotNull(inserted.colTimestamp)
            assertEquals("test clob content", inserted.colClob)
            assertEquals("not null value", inserted.colNotNull)
        }
    }

    @Test
    fun testInsertWithNullOptionalColumns() {
        OracleTestHelper.run { c ->
            val unsaved = AllScalarTypesRowUnsaved(
                null,
                null,
                null,
                null,
                null,
                "required value",
                Defaulted.UseDefault()
            )

            val insertedRow = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedRow.id, c)!!

            assertNull(inserted.colVarchar2)
            assertNull(inserted.colNumber)
            assertNull(inserted.colDate)
            assertNull(inserted.colTimestamp)
            assertNull(inserted.colClob)
            assertEquals("required value", inserted.colNotNull)
        }
    }

    @Test
    fun testUpdateScalarTypes() {
        OracleTestHelper.run { c ->
            val unsaved = AllScalarTypesRowUnsaved(
                "original",
                BigDecimal("100.00"),
                null,
                null,
                null,
                "not null",
                Defaulted.UseDefault()
            )

            val insertedRow = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedRow.id, c)!!

            val updated = inserted.copy(
                colVarchar2 = "updated",
                colNumber = BigDecimal("200.01")
            )

            val wasUpdated = repo.update(updated, c)
            assertTrue(wasUpdated)

            val fetched = repo.selectById(insertedRow.id, c)!!
            assertEquals("updated", fetched.colVarchar2)
            assertEquals(BigDecimal("200.01"), fetched.colNumber)
        }
    }

    @Test
    fun testDeleteScalarTypes() {
        OracleTestHelper.run { c ->
            val unsaved = AllScalarTypesRowUnsaved(
                "to delete",
                null,
                null,
                null,
                null,
                "delete me",
                Defaulted.UseDefault()
            )

            val insertedRow = repo.insert(unsaved, c)

            val deleted = repo.deleteById(insertedRow.id, c)
            assertTrue(deleted)

            val found = repo.selectById(insertedRow.id, c)
            assertNull(found)
        }
    }

    @Test
    fun testSelectAll() {
        OracleTestHelper.run { c ->
            repo.insert(AllScalarTypesRowUnsaved("row1", null, null, null, null, "not null 1", Defaulted.UseDefault()), c)
            repo.insert(AllScalarTypesRowUnsaved("row2", null, null, null, null, "not null 2", Defaulted.UseDefault()), c)

            val all = repo.selectAll(c)
            assertTrue(all.size >= 2)
        }
    }

    @Test
    fun testScalarTypeVarchar2() {
        OracleTestHelper.run { c ->
            val longString = "A".repeat(100)
            val unsaved = AllScalarTypesRowUnsaved(
                longString,
                null,
                null,
                null,
                null,
                "required",
                Defaulted.UseDefault()
            )

            val insertedRow = repo.insert(unsaved, c)
            assertEquals(longString, insertedRow.colVarchar2)
        }
    }

    @Test
    fun testScalarTypeNumber() {
        OracleTestHelper.run { c ->
            val unsaved = AllScalarTypesRowUnsaved(
                null,
                BigDecimal("999999.99"),
                null,
                null,
                null,
                "required",
                Defaulted.UseDefault()
            )

            val insertedRow = repo.insert(unsaved, c)
            assertEquals(BigDecimal("999999.99"), insertedRow.colNumber)
        }
    }

    @Test
    fun testBigDecimalPrecision() {
        OracleTestHelper.run { c ->
            // Column is NUMBER(10,2) - max 8 digits before decimal, 2 after
            val preciseNumber = BigDecimal("12345678.99")
            val unsaved = AllScalarTypesRowUnsaved(
                null,
                preciseNumber,
                null,
                null,
                null,
                "precision test",
                Defaulted.UseDefault()
            )

            val insertedRow = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedRow.id, c)!!

            assertNotNull(inserted.colNumber)
            assertEquals(preciseNumber, inserted.colNumber)
        }
    }

    @Test
    fun testDateTimeColumns() {
        OracleTestHelper.run { c ->
            val dateValue = LocalDateTime.of(2025, 6, 15, 10, 30, 0)
            val timestampValue = LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123456789)

            val unsaved = AllScalarTypesRowUnsaved(
                null,
                null,
                dateValue,
                timestampValue,
                null,
                "datetime test",
                Defaulted.UseDefault()
            )

            val insertedRow = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedRow.id, c)!!

            // DATE in Oracle includes time down to seconds
            assertNotNull(inserted.colDate)
            assertEquals(2025, inserted.colDate!!.year)
            assertEquals(6, inserted.colDate!!.monthValue)
            assertEquals(15, inserted.colDate!!.dayOfMonth)

            // TIMESTAMP includes fractional seconds
            assertNotNull(inserted.colTimestamp)
        }
    }

    @Test
    fun testClobColumn() {
        OracleTestHelper.run { c ->
            val largeText = "A".repeat(10000)
            val unsaved = AllScalarTypesRowUnsaved(
                null,
                null,
                null,
                null,
                largeText,
                "clob test",
                Defaulted.UseDefault()
            )

            val insertedRow = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedRow.id, c)!!

            assertEquals(largeText, inserted.colClob)
        }
    }
}
