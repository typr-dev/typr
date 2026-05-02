package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.data.Json;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import testdb.all_scalar_types.*;

/**
 * Comprehensive tests for all DuckDB scalar data types. Tests the all_scalar_types table which has
 * 26 columns covering all supported DuckDB types.
 */
public class AllTypesTest {
  private final AllScalarTypesRepoImpl repo = new AllScalarTypesRepoImpl();

  /** Create a sample row with all types populated */
  static AllScalarTypesRow createSampleRow(int id) {
    return new AllScalarTypesRow(
        new AllScalarTypesId(id),
        Optional.of((byte) 42), // tinyint
        Optional.of((short) 1000), // smallint
        Optional.of(100000), // integer
        Optional.of(10000000000L), // bigint
        Optional.of(new BigInteger("123456789012345678901234567890")), // hugeint
        Optional.of(new dev.typr.foundations.data.Uint1((short) 200)), // utinyint
        Optional.of(new dev.typr.foundations.data.Uint2(50000)), // usmallint
        Optional.of(new dev.typr.foundations.data.Uint4(3000000000L)), // uinteger
        Optional.of(
            dev.typr.foundations.data.Uint8.of(new BigInteger("18446744073709551615"))), // ubigint
        Optional.of(3.14f), // float
        Optional.of(2.718281828), // double
        Optional.of(new BigDecimal("12345.67")), // decimal
        Optional.of(true), // boolean
        Optional.of("varchar_value"), // varchar
        Optional.of("text content"), // text
        Optional.of(new byte[] {1, 2, 3, 4, 5}), // blob
        Optional.of(LocalDate.of(2025, 1, 15)), // date
        Optional.of(LocalTime.of(14, 30, 45)), // time
        Optional.of(LocalDateTime.of(2025, 1, 15, 14, 30, 45)), // timestamp
        Optional.of(
            OffsetDateTime.of(2025, 1, 15, 14, 30, 45, 0, ZoneOffset.UTC)
                .toInstant()), // timestamptz
        Optional.of(Duration.ofHours(2).plusMinutes(30)), // interval
        Optional.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")), // uuid
        Optional.of(new Json("{\"key\": \"value\"}")), // json
        Optional.of(Mood.happy), // mood enum
        "required_value"); // not null varchar
  }

  @Test
  public void testInsertAndSelectAllTypes() {
    DuckDbTestHelper.run(
        c -> {
          var row = createSampleRow(1001);
          var inserted = repo.insert(row, c);

          assertNotNull(inserted);
          assertEquals(row.id(), inserted.id());
          assertEquals(row.colTinyint(), inserted.colTinyint());
          assertEquals(row.colSmallint(), inserted.colSmallint());
          assertEquals(row.colInteger(), inserted.colInteger());
          assertEquals(row.colBigint(), inserted.colBigint());
          assertEquals(row.colBoolean(), inserted.colBoolean());
          assertEquals(row.colVarchar(), inserted.colVarchar());
          assertEquals(row.colDate(), inserted.colDate());
          assertEquals(row.colUuid(), inserted.colUuid());
          assertEquals(row.colMood(), inserted.colMood());
          assertEquals(row.colNotNull(), inserted.colNotNull());

          // Verify we can select it back
          var found = repo.selectById(inserted.id(), c);
          assertTrue(found.isPresent());
          assertEquals(inserted.id(), found.get().id());
        });
  }

  @Test
  public void testUpdateAllTypes() {
    DuckDbTestHelper.run(
        c -> {
          var row = createSampleRow(1002);
          var inserted = repo.insert(row, c);

          // Update some fields
          var updated =
              inserted
                  .withColVarchar(Optional.of("updated_varchar"))
                  .withColDecimal(Optional.of(new BigDecimal("999.99")))
                  .withColBoolean(Optional.of(false))
                  .withColMood(Optional.of(Mood.sad));

          boolean wasUpdated = repo.update(updated, c);
          assertTrue(wasUpdated);

          var found = repo.selectById(inserted.id(), c).orElseThrow();
          assertEquals(Optional.of("updated_varchar"), found.colVarchar());
          assertEquals(Optional.of(new BigDecimal("999.99")), found.colDecimal());
          assertEquals(Optional.of(false), found.colBoolean());
          assertEquals(Optional.of(Mood.sad), found.colMood());
        });
  }

  @Test
  public void testDeleteAllTypes() {
    DuckDbTestHelper.run(
        c -> {
          var row = createSampleRow(1003);
          var inserted = repo.insert(row, c);

          boolean deleted = repo.deleteById(inserted.id(), c);
          assertTrue(deleted);

          var found = repo.selectById(inserted.id(), c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testInsertWithNulls() {
    DuckDbTestHelper.run(
        c -> {
          // Create row with only required fields, all optional fields empty
          var row =
              new AllScalarTypesRow(
                  new AllScalarTypesId(1004),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  "required_only");

          var inserted = repo.insert(row, c);
          assertNotNull(inserted);

          // Verify all optional fields are empty
          assertTrue(inserted.colTinyint().isEmpty());
          assertTrue(inserted.colSmallint().isEmpty());
          assertTrue(inserted.colInteger().isEmpty());
          assertTrue(inserted.colBigint().isEmpty());
          assertTrue(inserted.colHugeint().isEmpty());
          assertTrue(inserted.colBoolean().isEmpty());
          assertTrue(inserted.colVarchar().isEmpty());
          assertTrue(inserted.colDate().isEmpty());
          assertTrue(inserted.colUuid().isEmpty());
          assertTrue(inserted.colMood().isEmpty());
          assertEquals("required_only", inserted.colNotNull());
        });
  }

  // ==================== Individual Type Tests ====================

  @Test
  public void testIntegerTypes() {
    DuckDbTestHelper.run(
        c -> {
          var row = createSampleRow(1010);
          var inserted = repo.insert(row, c);

          assertEquals(Optional.of((byte) 42), inserted.colTinyint());
          assertEquals(Optional.of((short) 1000), inserted.colSmallint());
          assertEquals(Optional.of(100000), inserted.colInteger());
          assertEquals(Optional.of(10000000000L), inserted.colBigint());
          assertEquals(
              Optional.of(new BigInteger("123456789012345678901234567890")), inserted.colHugeint());
        });
  }

  @Test
  public void testUnsignedTypes() {
    DuckDbTestHelper.run(
        c -> {
          var row = createSampleRow(1011);
          var inserted = repo.insert(row, c);

          assertEquals(
              Optional.of(new dev.typr.foundations.data.Uint1((short) 200)),
              inserted.colUtinyint());
          assertEquals(
              Optional.of(new dev.typr.foundations.data.Uint2(50000)), inserted.colUsmallint());
          assertEquals(
              Optional.of(new dev.typr.foundations.data.Uint4(3000000000L)),
              inserted.colUinteger());
          assertEquals(
              Optional.of(
                  dev.typr.foundations.data.Uint8.of(new BigInteger("18446744073709551615"))),
              inserted.colUbigint());
        });
  }

  @Test
  public void testFloatingPointTypes() {
    DuckDbTestHelper.run(
        c -> {
          var row = createSampleRow(1012);
          var inserted = repo.insert(row, c);

          assertEquals(Optional.of(3.14f), inserted.colFloat());
          assertEquals(Optional.of(2.718281828), inserted.colDouble());
          assertEquals(Optional.of(new BigDecimal("12345.67")), inserted.colDecimal());
        });
  }

  @Test
  public void testDateTimeTypes() {
    DuckDbTestHelper.run(
        c -> {
          var row = createSampleRow(1013);
          var inserted = repo.insert(row, c);

          assertEquals(Optional.of(LocalDate.of(2025, 1, 15)), inserted.colDate());
          assertEquals(Optional.of(LocalTime.of(14, 30, 45)), inserted.colTime());
          assertEquals(
              Optional.of(LocalDateTime.of(2025, 1, 15, 14, 30, 45)), inserted.colTimestamp());
          assertNotNull(inserted.colTimestamptz());
          assertNotNull(inserted.colInterval());
        });
  }

  @Test
  public void testUuidType() {
    DuckDbTestHelper.run(
        c -> {
          var uuid = UUID.randomUUID();
          var row = createSampleRow(1014).withColUuid(Optional.of(uuid));
          var inserted = repo.insert(row, c);

          assertEquals(Optional.of(uuid), inserted.colUuid());
        });
  }

  @Test
  public void testJsonType() {
    DuckDbTestHelper.run(
        c -> {
          var jsonValue = new Json("{\"name\": \"test\", \"values\": [1, 2, 3]}");
          var row = createSampleRow(1015).withColJson(Optional.of(jsonValue));
          var inserted = repo.insert(row, c);

          assertTrue(inserted.colJson().isPresent());
          assertTrue(inserted.colJson().get().value().contains("name"));
        });
  }

  @Test
  public void testBlobType() {
    DuckDbTestHelper.run(
        c -> {
          var blobData = new byte[] {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
          var row = createSampleRow(1016).withColBlob(Optional.of(blobData));
          var inserted = repo.insert(row, c);

          assertTrue(inserted.colBlob().isPresent());
          assertArrayEquals(blobData, inserted.colBlob().get());
        });
  }

  @Test
  public void testEnumType() {
    DuckDbTestHelper.run(
        c -> {
          // Test each mood enum value
          for (Mood mood : Mood.values()) {
            var row = createSampleRow(1020 + mood.ordinal()).withColMood(Optional.of(mood));
            var inserted = repo.insert(row, c);
            assertEquals(Optional.of(mood), inserted.colMood());
          }
        });
  }
}
