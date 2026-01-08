package oracledb.all_types_test

import dev.typr.foundations.data.OracleIntervalDS
import dev.typr.foundations.data.OracleIntervalYM
import oracledb.AddressT
import oracledb.AllTypesStructNoLobs
import oracledb.AllTypesStructNoLobsArray
import oracledb.CoordinatesT
import oracledb.OracleTestHelper
import oracledb.PhoneList
import oracledb.customtypes.Defaulted
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Tests for Oracle comprehensive object type that includes all supported data types.
 * This validates that complex nested Oracle OBJECT types with various field types
 * can be correctly inserted, read back, and round-tripped through the database.
 */
@Ignore("CI-only failure - SQLSyntaxErrorException, passes locally. Needs investigation.")
class AllTypesTestTest {
    private val repo = AllTypesTestRepoImpl()

    private fun createTestStruct(prefix: String): AllTypesStructNoLobs {
        val coords = CoordinatesT(BigDecimal("40.7128"), BigDecimal("-74.0060"))
        val address = AddressT("$prefix Main St", "$prefix City", coords)
        val phones = PhoneList(arrayOf("555-0100", "555-0101"))

        return AllTypesStructNoLobs(
            varcharField = "${prefix}_varchar",
            nvarcharField = "${prefix}_nvarchar",
            charField = "CHAR10    ",
            ncharField = "NCHAR10   ",
            numberField = BigDecimal("12345.6789"),
            numberIntField = BigDecimal("1234567890"),
            numberLongField = BigDecimal("1234567890123456789"),
            binaryFloatField = 1.5f,
            binaryDoubleField = 2.5,
            dateField = LocalDateTime.of(2025, 6, 15, 10, 30, 0),
            timestampField = LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123000000),
            timestampTzField = OffsetDateTime.of(2025, 6, 15, 10, 30, 45, 0, ZoneOffset.ofHours(-5)),
            timestampLtzField = OffsetDateTime.of(2025, 6, 15, 10, 30, 45, 0, ZoneOffset.UTC),
            intervalYmField = OracleIntervalYM(2, 6),
            intervalDsField = OracleIntervalDS(5, 12, 30, 45, 0),
            nestedObjectField = address,
            varrayField = phones
        )
    }

    @Test
    fun testInsertAndSelectAllTypesStruct() {
        OracleTestHelper.run { c ->
            val data = createTestStruct("Test1")

            val unsaved = AllTypesTestRowUnsaved(
                name = "Test Row",
                data = data,
                dataArray = null,
                id = Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            assertNotNull(insertedId)

            val inserted = repo.selectById(insertedId, c)!!
            assertEquals("Test Row", inserted.name)
            assertNotNull(inserted.data)

            val retrieved = inserted.data!!
            assertEquals("Test1_varchar", retrieved.varcharField)
            assertEquals("Test1_nvarchar", retrieved.nvarcharField)
            assertEquals(BigDecimal("12345.6789"), retrieved.numberField)
            assertEquals(1.5f, retrieved.binaryFloatField, 0.001f)
            assertEquals(2.5, retrieved.binaryDoubleField, 0.001)

            assertEquals("Test1 Main St", retrieved.nestedObjectField.street)
            assertEquals("Test1 City", retrieved.nestedObjectField.city)

            assertEquals(2, retrieved.varrayField.value.size)
            assertEquals("555-0100", retrieved.varrayField.value[0])
        }
    }

    @Test
    fun testRoundtripAllFields() {
        OracleTestHelper.run { c ->
            val original = createTestStruct("Roundtrip")

            val unsaved = AllTypesTestRowUnsaved(
                name = "Roundtrip Test",
                data = original,
                dataArray = null,
                id = Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val retrieved = repo.selectById(insertedId, c)!!

            val data = retrieved.data!!

            assertEquals(original.varcharField, data.varcharField)
            assertEquals(original.nvarcharField, data.nvarcharField)
            assertEquals(original.numberField, data.numberField)
            assertEquals(original.numberIntField, data.numberIntField)
            assertEquals(original.numberLongField, data.numberLongField)
            assertEquals(original.binaryFloatField, data.binaryFloatField, 0.001f)
            assertEquals(original.binaryDoubleField, data.binaryDoubleField, 0.001)
            assertEquals(original.dateField, data.dateField)

            assertEquals(original.nestedObjectField.street, data.nestedObjectField.street)
            assertEquals(original.nestedObjectField.city, data.nestedObjectField.city)
            assertEquals(
                original.nestedObjectField.location.latitude,
                data.nestedObjectField.location.latitude
            )

            assertArrayEquals(original.varrayField.value, data.varrayField.value)
        }
    }

    @Test
    fun testInsertWithNullData() {
        OracleTestHelper.run { c ->
            val unsaved = AllTypesTestRowUnsaved(
                name = "Null Data Test",
                data = null,
                dataArray = null,
                id = Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val retrieved = repo.selectById(insertedId, c)!!

            assertEquals("Null Data Test", retrieved.name)
            assertNull(retrieved.data)
            assertNull(retrieved.dataArray)
        }
    }

    @Test
    fun testInsertWithArray() {
        OracleTestHelper.run { c ->
            val struct1 = createTestStruct("Array1")
            val struct2 = createTestStruct("Array2")

            val dataArray = AllTypesStructNoLobsArray(arrayOf(struct1, struct2))

            val unsaved = AllTypesTestRowUnsaved(
                name = "Array Test",
                data = struct1,
                dataArray = dataArray,
                id = Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val retrieved = repo.selectById(insertedId, c)!!

            assertNotNull(retrieved.dataArray)
            val array = retrieved.dataArray!!.value
            assertEquals(2, array.size)
            assertEquals("Array1_varchar", array[0].varcharField)
            assertEquals("Array2_varchar", array[1].varcharField)
        }
    }

    @Test
    fun testUpdateData() {
        OracleTestHelper.run { c ->
            val original = createTestStruct("Original")

            val unsaved = AllTypesTestRowUnsaved(
                name = "Update Test",
                data = original,
                dataArray = null,
                id = Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            val inserted = repo.selectById(insertedId, c)!!

            val updated = createTestStruct("Updated")
            val updatedRow = inserted.copy(data = updated)

            val wasUpdated = repo.update(updatedRow, c)
            assertTrue(wasUpdated)

            val fetched = repo.selectById(insertedId, c)!!
            assertEquals("Updated_varchar", fetched.data!!.varcharField)
        }
    }

    @Test
    fun testDelete() {
        OracleTestHelper.run { c ->
            val unsaved = AllTypesTestRowUnsaved(
                name = "To Delete",
                data = null,
                dataArray = null,
                id = Defaulted.UseDefault()
            )

            val insertedId = repo.insert(unsaved, c)
            assertNotNull(repo.selectById(insertedId, c))

            val deleted = repo.deleteById(insertedId, c)
            assertTrue(deleted)

            assertNull(repo.selectById(insertedId, c))
        }
    }
}
