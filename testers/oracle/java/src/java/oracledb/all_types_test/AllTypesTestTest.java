package oracledb.all_types_test;

import static org.junit.Assert.*;

import dev.typr.foundations.data.OracleIntervalDS;
import dev.typr.foundations.data.OracleIntervalYM;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import oracledb.AddressT;
import oracledb.AllTypesStructNoLobs;
import oracledb.AllTypesStructNoLobsArray;
import oracledb.CoordinatesT;
import oracledb.OracleTestHelper;
import oracledb.PhoneList;
import oracledb.customtypes.Defaulted;
import org.junit.Test;

/**
 * Tests for Oracle comprehensive object type that includes all supported data types. This validates
 * that complex nested Oracle OBJECT types with various field types can be correctly inserted, read
 * back, and round-tripped through the database.
 */
public class AllTypesTestTest {
  private final AllTypesTestRepoImpl repo = new AllTypesTestRepoImpl();

  private AllTypesStructNoLobs createTestStruct(String prefix) {
    CoordinatesT coords = new CoordinatesT(new BigDecimal("40.7128"), new BigDecimal("-74.0060"));
    AddressT address = new AddressT(prefix + " Main St", prefix + " City", coords);
    PhoneList phones = new PhoneList(new String[] {"555-0100", "555-0101"});

    return new AllTypesStructNoLobs(
        prefix + "_varchar",
        prefix + "_nvarchar",
        "CHAR10    ",
        "NCHAR10   ",
        new BigDecimal("12345.6789"),
        new BigDecimal("1234567890"),
        new BigDecimal("1234567890123456789"),
        1.5f,
        2.5,
        LocalDateTime.of(2025, 6, 15, 10, 30, 0),
        LocalDateTime.of(2025, 6, 15, 10, 30, 45, 123000000),
        OffsetDateTime.of(2025, 6, 15, 10, 30, 45, 0, ZoneOffset.ofHours(-5)),
        OffsetDateTime.of(2025, 6, 15, 10, 30, 45, 0, ZoneOffset.UTC),
        new OracleIntervalYM(2, 6),
        new OracleIntervalDS(5, 12, 30, 45, 0),
        address,
        phones);
  }

  @Test
  public void testInsertAndSelectAllTypesStruct() {
    OracleTestHelper.run(
        c -> {
          AllTypesStructNoLobs data = createTestStruct("Test1");

          AllTypesTestRowUnsaved unsaved =
              new AllTypesTestRowUnsaved(
                  "Test Row", Optional.of(data), Optional.empty(), new Defaulted.UseDefault<>());

          AllTypesTestId insertedId = repo.insert(unsaved, c);
          assertNotNull(insertedId);

          AllTypesTestRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertEquals("Test Row", inserted.name());
          assertTrue(inserted.data().isPresent());

          AllTypesStructNoLobs retrieved = inserted.data().get();
          assertEquals("Test1_varchar", retrieved.varcharField());
          assertEquals("Test1_nvarchar", retrieved.nvarcharField());
          assertEquals(new BigDecimal("12345.6789"), retrieved.numberField());
          assertEquals(1.5f, retrieved.binaryFloatField(), 0.001f);
          assertEquals(2.5, retrieved.binaryDoubleField(), 0.001);

          assertEquals("Test1 Main St", retrieved.nestedObjectField().street());
          assertEquals("Test1 City", retrieved.nestedObjectField().city());

          assertEquals(2, retrieved.varrayField().value().length);
          assertEquals("555-0100", retrieved.varrayField().value()[0]);
        });
  }

  @Test
  public void testRoundtripAllFields() {
    OracleTestHelper.run(
        c -> {
          AllTypesStructNoLobs original = createTestStruct("Roundtrip");

          AllTypesTestRowUnsaved unsaved =
              new AllTypesTestRowUnsaved(
                  "Roundtrip Test",
                  Optional.of(original),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          AllTypesTestId insertedId = repo.insert(unsaved, c);
          AllTypesTestRow retrieved = repo.selectById(insertedId, c).orElseThrow();

          AllTypesStructNoLobs data = retrieved.data().orElseThrow();

          assertEquals(original.varcharField(), data.varcharField());
          assertEquals(original.nvarcharField(), data.nvarcharField());
          assertEquals(original.numberField(), data.numberField());
          assertEquals(original.numberIntField(), data.numberIntField());
          assertEquals(original.numberLongField(), data.numberLongField());
          assertEquals(original.binaryFloatField(), data.binaryFloatField(), 0.001f);
          assertEquals(original.binaryDoubleField(), data.binaryDoubleField(), 0.001);
          assertEquals(original.dateField(), data.dateField());

          assertEquals(original.nestedObjectField().street(), data.nestedObjectField().street());
          assertEquals(original.nestedObjectField().city(), data.nestedObjectField().city());
          assertEquals(
              original.nestedObjectField().location().latitude(),
              data.nestedObjectField().location().latitude());

          assertArrayEquals(original.varrayField().value(), data.varrayField().value());
        });
  }

  @Test
  public void testInsertWithNullData() {
    OracleTestHelper.run(
        c -> {
          AllTypesTestRowUnsaved unsaved =
              new AllTypesTestRowUnsaved(
                  "Null Data Test",
                  Optional.empty(),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          AllTypesTestId insertedId = repo.insert(unsaved, c);
          AllTypesTestRow retrieved = repo.selectById(insertedId, c).orElseThrow();

          assertEquals("Null Data Test", retrieved.name());
          assertFalse(retrieved.data().isPresent());
          assertFalse(retrieved.dataArray().isPresent());
        });
  }

  @Test
  public void testInsertWithArray() {
    OracleTestHelper.run(
        c -> {
          AllTypesStructNoLobs struct1 = createTestStruct("Array1");
          AllTypesStructNoLobs struct2 = createTestStruct("Array2");

          AllTypesStructNoLobsArray dataArray =
              new AllTypesStructNoLobsArray(new AllTypesStructNoLobs[] {struct1, struct2});

          AllTypesTestRowUnsaved unsaved =
              new AllTypesTestRowUnsaved(
                  "Array Test",
                  Optional.of(struct1),
                  Optional.of(dataArray),
                  new Defaulted.UseDefault<>());

          AllTypesTestId insertedId = repo.insert(unsaved, c);
          AllTypesTestRow retrieved = repo.selectById(insertedId, c).orElseThrow();

          assertTrue(retrieved.dataArray().isPresent());
          AllTypesStructNoLobs[] array = retrieved.dataArray().get().value();
          assertEquals(2, array.length);
          assertEquals("Array1_varchar", array[0].varcharField());
          assertEquals("Array2_varchar", array[1].varcharField());
        });
  }

  @Test
  public void testUpdateData() {
    OracleTestHelper.run(
        c -> {
          AllTypesStructNoLobs original = createTestStruct("Original");

          AllTypesTestRowUnsaved unsaved =
              new AllTypesTestRowUnsaved(
                  "Update Test",
                  Optional.of(original),
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          AllTypesTestId insertedId = repo.insert(unsaved, c);
          AllTypesTestRow inserted = repo.selectById(insertedId, c).orElseThrow();

          AllTypesStructNoLobs updated = createTestStruct("Updated");
          AllTypesTestRow updatedRow = inserted.withData(Optional.of(updated));

          Boolean wasUpdated = repo.update(updatedRow, c);
          assertTrue(wasUpdated);

          AllTypesTestRow fetched = repo.selectById(insertedId, c).orElseThrow();
          assertEquals("Updated_varchar", fetched.data().orElseThrow().varcharField());
        });
  }

  @Test
  public void testDelete() {
    OracleTestHelper.run(
        c -> {
          AllTypesTestRowUnsaved unsaved =
              new AllTypesTestRowUnsaved(
                  "To Delete", Optional.empty(), Optional.empty(), new Defaulted.UseDefault<>());

          AllTypesTestId insertedId = repo.insert(unsaved, c);
          assertTrue(repo.selectById(insertedId, c).isPresent());

          boolean deleted = repo.deleteById(insertedId, c);
          assertTrue(deleted);

          assertFalse(repo.selectById(insertedId, c).isPresent());
        });
  }
}
