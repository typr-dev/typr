package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.data.Json;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import testdb.all_scalar_types.*;

/**
 * Round-trip every SQLite scalar type through generated codecs.
 *
 * <p>SQLite stores values in 5 storage classes regardless of declared type; the type affinity model
 * maps declared types to storage classes via substring matching. The columns here exercise every
 * affinity (INTEGER, REAL, NUMERIC, TEXT, BLOB) plus the convenience types layered on TEXT (DATE,
 * TIME, DATETIME, TIMESTAMP, UUID, JSON).
 */
public class AllTypesTest {
  private final AllScalarTypesRepoImpl repo = new AllScalarTypesRepoImpl();

  /** Create a sample row with all optional columns populated. */
  static AllScalarTypesRow createSampleRow(long id) {
    return new AllScalarTypesRow(
        new AllScalarTypesId(id),
        Optional.of((byte) 42), // tinyint
        Optional.of((short) 1000), // smallint
        Optional.of(100000), // int
        Optional.of(10000000000L), // integer (Long in SqliteTypes.integer)
        Optional.of(9223372036854775000L), // bigint
        Optional.of(true), // boolean
        Optional.of(3.14159), // real (Double)
        Optional.of(2.718281828), // double
        Optional.of(1.5f), // float
        Optional.of(new BigDecimal("12345.67")), // decimal
        Optional.of(new BigDecimal("999.999")), // numeric
        Optional.of("text content"), // text
        Optional.of("varchar_value"), // varchar(100)
        Optional.of("char5"), // char(5)
        Optional.of("clob content"), // clob
        Optional.of(new byte[] {1, 2, 3, 4, 5}), // blob
        Optional.of(new byte[] {0x0A, 0x0B, 0x0C}), // binary
        Optional.of(LocalDate.of(2025, 1, 15)), // date
        Optional.of(LocalTime.of(14, 30, 45)), // time
        Optional.of(LocalDateTime.of(2025, 1, 15, 14, 30, 45)), // datetime
        Optional.of(LocalDateTime.of(2025, 6, 1, 9, 0, 0)), // timestamp
        Optional.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")), // uuid
        Optional.of(new Json("{\"key\": \"value\"}")), // json
        "required_value"); // not null text
  }

  @Test
  public void testInsertAndSelectAllTypes() {
    SqliteTestHelper.run(
        c -> {
          var row = createSampleRow(1001);
          var inserted = repo.insert(row, c);

          assertNotNull(inserted);
          assertEquals(row.id(), inserted.id());
          assertEquals(row.colTinyint(), inserted.colTinyint());
          assertEquals(row.colSmallint(), inserted.colSmallint());
          assertEquals(row.colInt(), inserted.colInt());
          assertEquals(row.colInteger(), inserted.colInteger());
          assertEquals(row.colBigint(), inserted.colBigint());
          assertEquals(row.colBoolean(), inserted.colBoolean());
          assertEquals(row.colVarchar(), inserted.colVarchar());
          assertEquals(row.colDate(), inserted.colDate());
          assertEquals(row.colUuid(), inserted.colUuid());
          assertEquals(row.colNotNull(), inserted.colNotNull());

          var found = repo.selectById(inserted.id(), c);
          assertTrue(found.isPresent());
          assertEquals(inserted.id(), found.get().id());
        });
  }

  @Test
  public void testUpdateAllTypes() {
    SqliteTestHelper.run(
        c -> {
          var row = createSampleRow(1002);
          var inserted = repo.insert(row, c);

          var updated =
              inserted
                  .withColVarchar(Optional.of("updated_varchar"))
                  .withColDecimal(Optional.of(new BigDecimal("999.99")))
                  .withColBoolean(Optional.of(false));

          boolean wasUpdated = repo.update(updated, c);
          assertTrue(wasUpdated);

          var found = repo.selectById(inserted.id(), c).orElseThrow();
          assertEquals(Optional.of("updated_varchar"), found.colVarchar());
          assertEquals(Optional.of(new BigDecimal("999.99")), found.colDecimal());
          assertEquals(Optional.of(false), found.colBoolean());
        });
  }

  @Test
  public void testDeleteAllTypes() {
    SqliteTestHelper.run(
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
    SqliteTestHelper.run(
        c -> {
          var row =
              new AllScalarTypesRow(
                  new AllScalarTypesId(1004L),
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

          assertTrue(inserted.colTinyint().isEmpty());
          assertTrue(inserted.colSmallint().isEmpty());
          assertTrue(inserted.colInt().isEmpty());
          assertTrue(inserted.colInteger().isEmpty());
          assertTrue(inserted.colBigint().isEmpty());
          assertTrue(inserted.colBoolean().isEmpty());
          assertTrue(inserted.colVarchar().isEmpty());
          assertTrue(inserted.colDate().isEmpty());
          assertTrue(inserted.colUuid().isEmpty());
          assertTrue(inserted.colJson().isEmpty());
          assertEquals("required_only", inserted.colNotNull());
        });
  }

  // ==================== Individual Type Tests ====================

  @Test
  public void testIntegerTypes() {
    SqliteTestHelper.run(
        c -> {
          var row = createSampleRow(1010);
          var inserted = repo.insert(row, c);

          assertEquals(Optional.of((byte) 42), inserted.colTinyint());
          assertEquals(Optional.of((short) 1000), inserted.colSmallint());
          assertEquals(Optional.of(100000), inserted.colInt());
          assertEquals(Optional.of(10000000000L), inserted.colInteger());
          assertEquals(Optional.of(9223372036854775000L), inserted.colBigint());
        });
  }

  @Test
  public void testBooleanType() {
    SqliteTestHelper.run(
        c -> {
          var row = createSampleRow(1011);
          var inserted = repo.insert(row, c);
          assertEquals(Optional.of(true), inserted.colBoolean());

          var flipped = inserted.withColBoolean(Optional.of(false));
          repo.update(flipped, c);
          var refetched = repo.selectById(inserted.id(), c).orElseThrow();
          assertEquals(Optional.of(false), refetched.colBoolean());
        });
  }

  @Test
  public void testFloatingPointTypes() {
    SqliteTestHelper.run(
        c -> {
          var row = createSampleRow(1012);
          var inserted = repo.insert(row, c);

          assertEquals(Optional.of(3.14159), inserted.colReal());
          assertEquals(Optional.of(2.718281828), inserted.colDouble());
          assertEquals(Optional.of(1.5f), inserted.colFloat());
        });
  }

  @Test
  public void testDecimalAndNumericTypes() {
    SqliteTestHelper.run(
        c -> {
          var row = createSampleRow(1013);
          var inserted = repo.insert(row, c);

          assertEquals(
              0, new BigDecimal("12345.67").compareTo(inserted.colDecimal().orElseThrow()));
          assertEquals(0, new BigDecimal("999.999").compareTo(inserted.colNumeric().orElseThrow()));
        });
  }

  @Test
  public void testTextTypes() {
    SqliteTestHelper.run(
        c -> {
          var row = createSampleRow(1014);
          var inserted = repo.insert(row, c);

          assertEquals(Optional.of("text content"), inserted.colText());
          assertEquals(Optional.of("varchar_value"), inserted.colVarchar());
          assertEquals(Optional.of("char5"), inserted.colChar());
          assertEquals(Optional.of("clob content"), inserted.colClob());
        });
  }

  @Test
  public void testBlobAndBinaryTypes() {
    SqliteTestHelper.run(
        c -> {
          var blobData = new byte[] {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
          var binaryData = new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
          var row =
              createSampleRow(1015)
                  .withColBlob(Optional.of(blobData))
                  .withColBinary(Optional.of(binaryData));
          var inserted = repo.insert(row, c);

          assertArrayEquals(blobData, inserted.colBlob().orElseThrow());
          assertArrayEquals(binaryData, inserted.colBinary().orElseThrow());
        });
  }

  @Test
  public void testDateTimeTypes() {
    SqliteTestHelper.run(
        c -> {
          var row = createSampleRow(1016);
          var inserted = repo.insert(row, c);

          assertEquals(Optional.of(LocalDate.of(2025, 1, 15)), inserted.colDate());
          assertEquals(Optional.of(LocalTime.of(14, 30, 45)), inserted.colTime());
          assertEquals(
              Optional.of(LocalDateTime.of(2025, 1, 15, 14, 30, 45)), inserted.colDatetime());
          assertEquals(Optional.of(LocalDateTime.of(2025, 6, 1, 9, 0, 0)), inserted.colTimestamp());
        });
  }

  @Test
  public void testUuidType() {
    SqliteTestHelper.run(
        c -> {
          var uuid = UUID.randomUUID();
          var row = createSampleRow(1017).withColUuid(Optional.of(uuid));
          var inserted = repo.insert(row, c);

          assertEquals(Optional.of(uuid), inserted.colUuid());
        });
  }

  @Test
  public void testJsonType() {
    SqliteTestHelper.run(
        c -> {
          var jsonValue = new Json("{\"name\":\"test\",\"values\":[1,2,3]}");
          var row = createSampleRow(1018).withColJson(Optional.of(jsonValue));
          var inserted = repo.insert(row, c);

          assertTrue(inserted.colJson().isPresent());
          assertTrue(inserted.colJson().get().value().contains("name"));
        });
  }

  @Test
  public void testSelectAllReturnsInsertedRow() {
    SqliteTestHelper.run(
        c -> {
          var row = createSampleRow(1019);
          repo.insert(row, c);

          var all = repo.selectAll(c);
          // Schema seeds one row (id=1) and we inserted id=1019, so at least 2 are present.
          assertTrue(all.size() >= 2);
          assertTrue(all.stream().anyMatch(r -> r.id().value().equals(1019L)));
        });
  }
}
