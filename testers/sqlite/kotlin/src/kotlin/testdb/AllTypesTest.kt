package testdb

import dev.typr.foundations.data.Json
import org.junit.Assert.*
import org.junit.Test
import testdb.all_scalar_types.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class AllTypesTest {
    private val repo = AllScalarTypesRepoImpl()

    private fun createSampleRow(id: Long): AllScalarTypesRow = AllScalarTypesRow(
        AllScalarTypesId(id),
        42.toByte(),
        1000.toShort(),
        100000,
        10000000000L,
        9223372036854775000L,
        true,
        3.14159,
        2.718281828,
        1.5f,
        BigDecimal("12345.67"),
        BigDecimal("999.999"),
        "text content",
        "varchar_value",
        "char5",
        "clob content",
        byteArrayOf(1, 2, 3, 4, 5),
        byteArrayOf(0x0A, 0x0B, 0x0C),
        LocalDate.of(2025, 1, 15),
        LocalTime.of(14, 30, 45),
        LocalDateTime.of(2025, 1, 15, 14, 30, 45),
        LocalDateTime.of(2025, 6, 1, 9, 0, 0),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
        Json("{\"key\": \"value\"}"),
        "required_value"
    )

    @Test
    fun testInsertAndSelectAllTypes() {
        SqliteTestHelper.run { c ->
            val row = createSampleRow(1001L)
            val inserted = repo.insert(row, c)
            assertEquals(row.id, inserted.id)
            assertEquals(row.colTinyint, inserted.colTinyint)
            assertEquals(row.colBoolean, inserted.colBoolean)
            assertEquals(row.colVarchar, inserted.colVarchar)
            assertEquals(row.colDate, inserted.colDate)
            assertEquals(row.colUuid, inserted.colUuid)
            assertEquals(row.colNotNull, inserted.colNotNull)
            assertNotNull(repo.selectById(inserted.id, c))
        }
    }

    @Test
    fun testUpdateAllTypes() {
        SqliteTestHelper.run { c ->
            val row = createSampleRow(1002L)
            val inserted = repo.insert(row, c)
            val updated = inserted.copy(
                colVarchar = "updated_varchar",
                colDecimal = BigDecimal("999.99"),
                colBoolean = false
            )
            assertTrue(repo.update(updated, c))
            val found = repo.selectById(inserted.id, c)!!
            assertEquals("updated_varchar", found.colVarchar)
            assertEquals(false, found.colBoolean)
        }
    }

    @Test
    fun testDeleteAllTypes() {
        SqliteTestHelper.run { c ->
            val inserted = repo.insert(createSampleRow(1003L), c)
            assertTrue(repo.deleteById(inserted.id, c))
            assertNull(repo.selectById(inserted.id, c))
        }
    }

    @Test
    fun testInsertWithNulls() {
        SqliteTestHelper.run { c ->
            val row = AllScalarTypesRow(
                AllScalarTypesId(1004L),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                "required_only"
            )
            val inserted = repo.insert(row, c)
            assertEquals("required_only", inserted.colNotNull)
            assertNull(inserted.colTinyint)
            assertNull(inserted.colUuid)
            assertNull(inserted.colJson)
        }
    }

    @Test
    fun testIntegerTypes() {
        SqliteTestHelper.run { c ->
            val inserted = repo.insert(createSampleRow(1010L), c)
            assertEquals(42.toByte(), inserted.colTinyint)
            assertEquals(1000.toShort(), inserted.colSmallint)
            assertEquals(100000, inserted.colInt)
            assertEquals(10000000000L, inserted.colInteger)
            assertEquals(9223372036854775000L, inserted.colBigint)
        }
    }

    @Test
    fun testBooleanType() {
        SqliteTestHelper.run { c ->
            val inserted = repo.insert(createSampleRow(1011L), c)
            assertEquals(true, inserted.colBoolean)
            repo.update(inserted.copy(colBoolean = false), c)
            assertEquals(false, repo.selectById(inserted.id, c)!!.colBoolean)
        }
    }

    @Test
    fun testFloatingPointTypes() {
        SqliteTestHelper.run { c ->
            val inserted = repo.insert(createSampleRow(1012L), c)
            assertEquals(3.14159, inserted.colReal!!, 0.0)
            assertEquals(2.718281828, inserted.colDouble!!, 0.0)
            assertEquals(1.5f, inserted.colFloat!!, 0.0f)
        }
    }

    @Test
    fun testDecimalAndNumeric() {
        SqliteTestHelper.run { c ->
            val inserted = repo.insert(createSampleRow(1013L), c)
            assertEquals(0, BigDecimal("12345.67").compareTo(inserted.colDecimal!!))
            assertEquals(0, BigDecimal("999.999").compareTo(inserted.colNumeric!!))
        }
    }

    @Test
    fun testTextTypes() {
        SqliteTestHelper.run { c ->
            val inserted = repo.insert(createSampleRow(1014L), c)
            assertEquals("text content", inserted.colText)
            assertEquals("varchar_value", inserted.colVarchar)
            assertEquals("char5", inserted.colChar)
            assertEquals("clob content", inserted.colClob)
        }
    }

    @Test
    fun testBlobAndBinary() {
        SqliteTestHelper.run { c ->
            val blob = byteArrayOf(0, 1, 2, -1, -2)
            val bin = byteArrayOf(-54, -2, -70, -66)
            val inserted = repo.insert(createSampleRow(1015L).copy(colBlob = blob, colBinary = bin), c)
            assertArrayEquals(blob, inserted.colBlob)
            assertArrayEquals(bin, inserted.colBinary)
        }
    }

    @Test
    fun testDateTimeTypes() {
        SqliteTestHelper.run { c ->
            val inserted = repo.insert(createSampleRow(1016L), c)
            assertEquals(LocalDate.of(2025, 1, 15), inserted.colDate)
            assertEquals(LocalTime.of(14, 30, 45), inserted.colTime)
            assertEquals(LocalDateTime.of(2025, 1, 15, 14, 30, 45), inserted.colDatetime)
            assertEquals(LocalDateTime.of(2025, 6, 1, 9, 0, 0), inserted.colTimestamp)
        }
    }

    @Test
    fun testUuid() {
        SqliteTestHelper.run { c ->
            val uuid = UUID.randomUUID()
            val inserted = repo.insert(createSampleRow(1017L).copy(colUuid = uuid), c)
            assertEquals(uuid, inserted.colUuid)
        }
    }

    @Test
    fun testSelectAllReturnsInsertedRow() {
        SqliteTestHelper.run { c ->
            val row = createSampleRow(1019L)
            repo.insert(row, c)
            val all = repo.selectAll(c)
            assertTrue(all.size >= 2)
            assertTrue(all.any { it.id.value == 1019L })
        }
    }

    @Test
    fun testJson() {
        SqliteTestHelper.run { c ->
            val json = Json("{\"name\":\"test\"}")
            val inserted = repo.insert(createSampleRow(1018L).copy(colJson = json), c)
            assertTrue(inserted.colJson!!.value().contains("name"))
        }
    }
}
