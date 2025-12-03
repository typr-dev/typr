package testdb;

import org.junit.Test;
import testdb.mariatest.MariatestId;
import testdb.mariatest.MariatestRepoImpl;
import testdb.mariatest.MariatestRow;
import testdb.mariatestnull.MariatestnullRepoImpl;
import testdb.mariatestnull.MariatestnullRow;
import typo.data.maria.Inet4;
import typo.data.maria.Inet6;
import typo.data.maria.MariaSet;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static testdb.JsonEquals.assertJsonEquals;
import static org.junit.Assert.*;

/**
 * Tests for all MariaDB data types - equivalent to PostgreSQL ArrayTest for MariaDB.
 */
public class MariaDbTypeTest {
    private final MariatestRepoImpl mariatestRepo = new MariatestRepoImpl();
    private final MariatestnullRepoImpl mariatestnullRepo = new MariatestnullRepoImpl();

    static MariatestRow mariaTestRow() {
        return new MariatestRow(
            // Integer types
            (byte) 127,                                      // tinyint_col
            (short) 32767,                                   // smallint_col
            8388607,                                         // mediumint_col
            new MariatestId(42),                             // int_col (PK)
            9223372036854775807L,                            // bigint_col
            // Unsigned integer types
            (short) 255,                                     // tinyint_u_col
            65535,                                           // smallint_u_col
            16777215,                                        // mediumint_u_col
            4294967295L,                                     // int_u_col
            new BigInteger("18446744073709551615"),          // bigint_u_col
            // Decimal types
            new BigDecimal("12345.6789"),                    // decimal_col
            new BigDecimal("98765.4321"),                    // numeric_col
            3.14f,                                           // float_col
            2.718281828459045,                               // double_col
            // Boolean
            true,                                            // bool_col
            // Bit types
            new byte[]{(byte) 0xF0},                          // bit_col (BIT(8))
            new byte[]{(byte) 1},                             // bit1_col (BIT(1))
            // String types
            "fixed     ",                                    // char_col (CHAR(10))
            "variable",                                      // varchar_col
            "tiny text",                                     // tinytext_col
            "text content",                                  // text_col
            "medium text",                                   // mediumtext_col
            "long text",                                     // longtext_col
            // Binary types
            new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A}, // binary_col
            new byte[]{(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF}, // varbinary_col
            new byte[]{0x01},                                // tinyblob_col
            new byte[]{0x02, 0x03},                          // blob_col
            new byte[]{0x04, 0x05, 0x06},                    // mediumblob_col
            new byte[]{0x07, 0x08, 0x09, 0x0A},              // longblob_col
            // Date/time types
            LocalDate.of(2024, 12, 2),                       // date_col
            LocalTime.of(12, 30, 45),                        // time_col
            LocalTime.of(12, 30, 45, 123456000),             // time_fsp_col
            LocalDateTime.of(2024, 12, 2, 14, 30, 45),       // datetime_col
            LocalDateTime.of(2024, 12, 2, 14, 30, 45, 123456000), // datetime_fsp_col
            LocalDateTime.of(2024, 12, 2, 10, 0, 0),         // timestamp_col (default)
            LocalDateTime.of(2024, 12, 2, 10, 0, 0, 123456000), // timestamp_fsp_col (default)
            Year.of(2024),                                   // year_col
            // Enum and set
            "a",                                             // enum_col (ENUM values: a, b, c, d)
            MariaSet.of("x", "y"),                           // set_col (SET values: x, y, z)
            // JSON
            "{\"key\": \"value\"}",                          // json_col
            // Network types
            new Inet4("192.168.1.1"),                        // inet4_col
            new Inet6("::1")                                 // inet6_col
        );
    }

    static MariatestnullRow mariatestnullRowEmpty() {
        return new MariatestnullRow(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty()
        );
    }

    static MariatestnullRow mariatestnullRowWithValues() {
        return new MariatestnullRow(
            // Integer types
            Optional.of((byte) 127),                          // tinyint_col
            Optional.of((short) 32767),                       // smallint_col
            Optional.of(8388607),                             // mediumint_col
            Optional.of(42),                                  // int_col
            Optional.of(9223372036854775807L),                // bigint_col
            // Unsigned integer types
            Optional.of((short) 255),                         // tinyint_u_col
            Optional.of(65535),                               // smallint_u_col
            Optional.of(16777215),                            // mediumint_u_col
            Optional.of(4294967295L),                         // int_u_col
            Optional.of(new BigInteger("18446744073709551615")), // bigint_u_col
            // Decimal types
            Optional.of(new BigDecimal("12345.6789")),        // decimal_col
            Optional.of(new BigDecimal("98765.4321")),        // numeric_col
            Optional.of(3.14f),                               // float_col
            Optional.of(2.718281828459045),                   // double_col
            // Boolean
            Optional.of(true),                                // bool_col
            // Bit types
            Optional.of(new byte[]{(byte) 0xF0}),             // bit_col (BIT(8))
            Optional.of(new byte[]{(byte) 1}),                // bit1_col
            // String types
            Optional.of("fixed     "),                        // char_col
            Optional.of("variable"),                          // varchar_col
            Optional.of("tiny text"),                         // tinytext_col
            Optional.of("text content"),                      // text_col
            Optional.of("medium text"),                       // mediumtext_col
            Optional.of("long text"),                         // longtext_col
            // Binary types
            Optional.of(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A}), // binary_col
            Optional.of(new byte[]{(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF}), // varbinary_col
            Optional.of(new byte[]{0x01}),                    // tinyblob_col
            Optional.of(new byte[]{0x02, 0x03}),              // blob_col
            Optional.of(new byte[]{0x04, 0x05, 0x06}),        // mediumblob_col
            Optional.of(new byte[]{0x07, 0x08, 0x09, 0x0A}),  // longblob_col
            // Date/time types
            Optional.of(LocalDate.of(2024, 12, 2)),           // date_col
            Optional.of(LocalTime.of(12, 30, 45)),            // time_col
            Optional.of(LocalTime.of(12, 30, 45, 123456000)), // time_fsp_col
            Optional.of(LocalDateTime.of(2024, 12, 2, 14, 30, 45)), // datetime_col
            Optional.of(LocalDateTime.of(2024, 12, 2, 14, 30, 45, 123456000)), // datetime_fsp_col
            Optional.of(LocalDateTime.of(2024, 12, 2, 10, 0, 0)), // timestamp_col
            Optional.of(LocalDateTime.of(2024, 12, 2, 10, 0, 0, 123456000)), // timestamp_fsp_col
            Optional.of(Year.of(2024)),                       // year_col
            // Enum and set
            Optional.of("a"),                                 // enum_col (ENUM values: a, b, c, d)
            Optional.of(MariaSet.of("x", "y")),               // set_col (SET values: x, y, z)
            // JSON
            Optional.of("{\"key\": \"value\"}"),              // json_col
            // Network types
            Optional.of(new Inet4("192.168.1.1")),            // inet4_col
            Optional.of(new Inet6("::1"))                     // inet6_col
        );
    }

    @Test
    public void canInsertMariatestRows() {
        WithConnection.run(c -> {
            var before = mariaTestRow();
            var after = mariatestRepo.insert(before, c);
            // Compare key scalar fields - timestamps will differ due to defaults
            assertEquals(before.tinyintCol(), after.tinyintCol());
            assertEquals(before.smallintCol(), after.smallintCol());
            assertEquals(before.mediumintCol(), after.mediumintCol());
            assertEquals(before.intCol(), after.intCol());
            assertEquals(before.bigintCol(), after.bigintCol());
            assertEquals(before.boolCol(), after.boolCol());
            assertEquals(before.varcharCol(), after.varcharCol());
            assertEquals(before.textCol(), after.textCol());
            assertEquals(before.dateCol(), after.dateCol());
            assertEquals(before.timeCol(), after.timeCol());
            assertEquals(before.yearCol(), after.yearCol());
            assertEquals(before.enumCol(), after.enumCol());
            assertEquals(before.jsonCol(), after.jsonCol());
            assertEquals(before.inet4Col(), after.inet4Col());
            assertEquals(before.inet6Col(), after.inet6Col());
        });
    }

    @Test
    public void canInsertNullMariatestnullRows() {
        WithConnection.run(c -> {
            var before = mariatestnullRowEmpty();
            var after = mariatestnullRepo.insert(before, c);
            assertJsonEquals(before, after);
        });
    }

    @Test
    public void canInsertNonNullMariatestnullRows() {
        WithConnection.run(c -> {
            var before = mariatestnullRowWithValues();
            var after = mariatestnullRepo.insert(before, c);
            // Verify key fields match
            assertEquals(before.tinyintCol(), after.tinyintCol());
            assertEquals(before.smallintCol(), after.smallintCol());
            assertEquals(before.intCol(), after.intCol());
            assertEquals(before.bigintCol(), after.bigintCol());
            assertEquals(before.boolCol(), after.boolCol());
            assertEquals(before.varcharCol(), after.varcharCol());
            assertEquals(before.dateCol(), after.dateCol());
            assertEquals(before.yearCol(), after.yearCol());
            assertEquals(before.enumCol(), after.enumCol());
            assertEquals(before.inet4Col(), after.inet4Col());
            assertEquals(before.inet6Col(), after.inet6Col());
        });
    }

    @Test
    public void canSelectAllMariatestRows() {
        WithConnection.run(c -> {
            var row1 = mariaTestRow();
            var row2 = mariaTestRow().withIntCol(new MariatestId(43));
            mariatestRepo.insert(row1, c);
            mariatestRepo.insert(row2, c);
            var all = mariatestRepo.selectAll(c);
            assertEquals(2, all.size());
        });
    }

    @Test
    public void canSelectByIdMariatestRows() {
        WithConnection.run(c -> {
            var inserted = mariatestRepo.insert(mariaTestRow(), c);
            var selected = mariatestRepo.selectById(inserted.intCol(), c);
            assertTrue(selected.isPresent());
            assertEquals(inserted.intCol(), selected.get().intCol());
        });
    }

    @Test
    public void canSelectByIdsMariatestRows() {
        WithConnection.run(c -> {
            var row1 = mariatestRepo.insert(mariaTestRow(), c);
            var row2 = mariatestRepo.insert(mariaTestRow().withIntCol(new MariatestId(43)), c);
            var ids = new MariatestId[]{row1.intCol(), row2.intCol()};
            var selected = mariatestRepo.selectByIds(ids, c);
            assertEquals(2, selected.size());
        });
    }

    @Test
    public void canUpdateMariatestRows() {
        WithConnection.run(c -> {
            var inserted = mariatestRepo.insert(mariaTestRow(), c);
            var updated = inserted.withVarcharCol("updated");
            mariatestRepo.update(updated, c);
            var selected = mariatestRepo.selectById(inserted.intCol(), c);
            assertTrue(selected.isPresent());
            assertEquals("updated", selected.get().varcharCol());
        });
    }

    @Test
    public void canDeleteMariatestRows() {
        WithConnection.run(c -> {
            var inserted = mariatestRepo.insert(mariaTestRow(), c);
            var deleted = mariatestRepo.deleteById(inserted.intCol(), c);
            assertTrue(deleted);
            var selected = mariatestRepo.selectById(inserted.intCol(), c);
            assertFalse(selected.isPresent());
        });
    }

    @Test
    public void canDeleteByIdsMariatestRows() {
        WithConnection.run(c -> {
            var row1 = mariatestRepo.insert(mariaTestRow(), c);
            var row2 = mariatestRepo.insert(mariaTestRow().withIntCol(new MariatestId(43)), c);
            var ids = new MariatestId[]{row1.intCol(), row2.intCol()};
            var count = mariatestRepo.deleteByIds(ids, c);
            assertEquals(Integer.valueOf(2), count);
            var all = mariatestRepo.selectAll(c);
            assertEquals(0, all.size());
        });
    }

    @Test
    public void canUpsertMariatestRows() {
        WithConnection.run(c -> {
            var row = mariaTestRow();
            var inserted = mariatestRepo.upsert(row, c);
            assertEquals(row.intCol(), inserted.intCol());

            var updated = inserted.withVarcharCol("upserted");
            var upserted = mariatestRepo.upsert(updated, c);
            assertEquals("upserted", upserted.varcharCol());
        });
    }

    @Test
    public void canQueryMariatestnullWithDSL() {
        WithConnection.run(c -> {
            var row = mariatestnullRepo.insert(mariatestnullRowWithValues(), c);

            // Test DSL select.where() for various types
            assertEquals(row.boolCol(), mariatestnullRepo.select().where(p -> p.boolCol().isEqual(row.boolCol().orElse(null))).toList(c).get(0).boolCol());
            assertEquals(row.varcharCol(), mariatestnullRepo.select().where(p -> p.varcharCol().isEqual(row.varcharCol().orElse(null))).toList(c).get(0).varcharCol());
            assertEquals(row.intCol(), mariatestnullRepo.select().where(p -> p.intCol().isEqual(row.intCol().orElse(null))).toList(c).get(0).intCol());
            assertEquals(row.bigintCol(), mariatestnullRepo.select().where(p -> p.bigintCol().isEqual(row.bigintCol().orElse(null))).toList(c).get(0).bigintCol());
            assertEquals(row.dateCol(), mariatestnullRepo.select().where(p -> p.dateCol().isEqual(row.dateCol().orElse(null))).toList(c).get(0).dateCol());
            assertEquals(row.yearCol(), mariatestnullRepo.select().where(p -> p.yearCol().isEqual(row.yearCol().orElse(null))).toList(c).get(0).yearCol());
            assertEquals(row.enumCol(), mariatestnullRepo.select().where(p -> p.enumCol().isEqual(row.enumCol().orElse(null))).toList(c).get(0).enumCol());
        });
    }

    @Test
    public void canQueryMariatestWithDSL() {
        WithConnection.run(c -> {
            var row = mariatestRepo.insert(mariaTestRow(), c);

            // Test DSL update.setValue() for various types
            mariatestRepo.update().setValue(p -> p.boolCol(), row.boolCol()).where(p -> p.intCol().isEqual(row.intCol())).execute(c);
            mariatestRepo.update().setValue(p -> p.varcharCol(), row.varcharCol()).where(p -> p.intCol().isEqual(row.intCol())).execute(c);
            mariatestRepo.update().setValue(p -> p.textCol(), row.textCol()).where(p -> p.intCol().isEqual(row.intCol())).execute(c);
            mariatestRepo.update().setValue(p -> p.bigintCol(), row.bigintCol()).where(p -> p.intCol().isEqual(row.intCol())).execute(c);
            mariatestRepo.update().setValue(p -> p.dateCol(), row.dateCol()).where(p -> p.intCol().isEqual(row.intCol())).execute(c);
            mariatestRepo.update().setValue(p -> p.timeCol(), row.timeCol()).where(p -> p.intCol().isEqual(row.intCol())).execute(c);
            mariatestRepo.update().setValue(p -> p.yearCol(), row.yearCol()).where(p -> p.intCol().isEqual(row.intCol())).execute(c);
            mariatestRepo.update().setValue(p -> p.enumCol(), row.enumCol()).where(p -> p.intCol().isEqual(row.intCol())).execute(c);
            mariatestRepo.update().setValue(p -> p.jsonCol(), row.jsonCol()).where(p -> p.intCol().isEqual(row.intCol())).execute(c);
        });
    }
}
