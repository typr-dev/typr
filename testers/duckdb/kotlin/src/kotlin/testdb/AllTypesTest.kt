package testdb

import dev.typr.foundations.data.Json
import dev.typr.foundations.data.Uint1
import dev.typr.foundations.data.Uint2
import dev.typr.foundations.data.Uint4
import dev.typr.foundations.data.Uint8
import org.junit.Assert.*
import org.junit.Test
import testdb.all_scalar_types.*
import java.math.BigDecimal
import java.math.BigInteger
import java.time.*
import java.util.UUID

class AllTypesTest {
    private val repo = AllScalarTypesRepoImpl()

    private fun createSampleRow(id: Int): AllScalarTypesRow = AllScalarTypesRow(
        AllScalarTypesId(id),
        42.toByte(),
        1000.toShort(),
        100000,
        10000000000L,
        BigInteger("123456789012345678901234567890"),
        Uint1(200),
        Uint2(50000),
        Uint4(3000000000L),
        Uint8(BigInteger("18446744073709551615")),
        3.14f,
        2.718281828,
        BigDecimal("12345.67"),
        true,
        "varchar_value",
        "text content",
        byteArrayOf(1, 2, 3, 4, 5),
        LocalDate.of(2025, 1, 15),
        LocalTime.of(14, 30, 45),
        LocalDateTime.of(2025, 1, 15, 14, 30, 45),
        OffsetDateTime.of(2025, 1, 15, 14, 30, 45, 0, ZoneOffset.UTC).toInstant(),
        Duration.ofHours(2).plusMinutes(30),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
        Json("{\"key\": \"value\"}"),
        Mood.happy,
        "required_value"
    )

    @Test
    fun testInsertAndSelectAllTypes() {
        DuckDbTestHelper.run { c ->
            val row = createSampleRow(1001)
            val inserted = repo.insert(row, c)

            assertNotNull(inserted)
            assertEquals(row.id, inserted.id)
            assertEquals(row.colTinyint, inserted.colTinyint)
            assertEquals(row.colSmallint, inserted.colSmallint)
            assertEquals(row.colBoolean, inserted.colBoolean)
            assertEquals(row.colVarchar, inserted.colVarchar)
            assertEquals(row.colDate, inserted.colDate)
            assertEquals(row.colMood, inserted.colMood)
            assertEquals(row.colNotNull, inserted.colNotNull)

            val found = repo.selectById(inserted.id, c)
            assertNotNull(found)
            assertEquals(inserted.id, found!!.id)
        }
    }

    @Test
    fun testUpdateAllTypes() {
        DuckDbTestHelper.run { c ->
            val row = createSampleRow(1002)
            val inserted = repo.insert(row, c)

            val updated = inserted.copy(
                colVarchar = "updated_varchar",
                colDecimal = BigDecimal("999.99"),
                colBoolean = false,
                colMood = Mood.sad
            )

            val wasUpdated = repo.update(updated, c)
            assertTrue(wasUpdated)

            val found = repo.selectById(updated.id, c)
            assertNotNull(found)
            assertEquals("updated_varchar", found!!.colVarchar)
            assertEquals(BigDecimal("999.99"), found.colDecimal)
            assertEquals(false, found.colBoolean)
            assertEquals(Mood.sad, found.colMood)
        }
    }

    @Test
    fun testDeleteAllTypes() {
        DuckDbTestHelper.run { c ->
            val row = createSampleRow(1003)
            val inserted = repo.insert(row, c)
            assertNotNull(repo.selectById(inserted.id, c))

            val wasDeleted = repo.deleteById(inserted.id, c)
            assertTrue(wasDeleted)
            assertNull(repo.selectById(inserted.id, c))
        }
    }

    @Test
    fun testNullableColumns() {
        DuckDbTestHelper.run { c ->
            // Create a row with nullable columns set to null
            val row = AllScalarTypesRow(
                AllScalarTypesId(1004),
                null, // colTinyint
                null, // colSmallint
                null, // colInteger
                null, // colBigint
                null, // colHugeint
                null, // colUtinyint
                null, // colUsmallint
                null, // colUinteger
                null, // colUbigint
                null, // colReal
                null, // colDouble
                null, // colDecimal
                null, // colBoolean
                null, // colVarchar
                null, // colText
                null, // colBlob
                null, // colDate
                null, // colTime
                null, // colTimestamp
                null, // colTimestamptz
                null, // colInterval
                null, // colUuid
                null, // colJson
                null, // colMood
                "required_not_null"
            )
            val inserted = repo.insert(row, c)

            assertNotNull(inserted)
            assertNull(inserted.colTinyint)
            assertNull(inserted.colVarchar)
            assertNull(inserted.colDate)
            assertEquals("required_not_null", inserted.colNotNull)
        }
    }

    @Test
    fun testEnumColumn() {
        DuckDbTestHelper.run { c ->
            for (mood in Mood.entries) {
                val row = createSampleRow(2000 + mood.ordinal).copy(colMood = mood)
                val inserted = repo.insert(row, c)
                assertEquals(mood, inserted.colMood)
            }
        }
    }

    @Test
    fun testSelectAll() {
        DuckDbTestHelper.run { c ->
            val row1 = createSampleRow(3001)
            val row2 = createSampleRow(3002)
            repo.insert(row1, c)
            repo.insert(row2, c)

            val all = repo.selectAll(c)
            assertTrue(all.size >= 2)
            assertTrue(all.any { it.id == row1.id })
            assertTrue(all.any { it.id == row2.id })
        }
    }
}
