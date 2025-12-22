package oracledb.all_scalar_types;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import oracledb.OracleTestHelper;
import oracledb.customtypes.Defaulted;
import org.junit.Test;

public class AllScalarTypesTest {
  private final AllScalarTypesRepoImpl repo = new AllScalarTypesRepoImpl();

  @Test
  public void testInsertAndSelectAllScalarTypes() {
    OracleTestHelper.run(
        c -> {
          AllScalarTypesRowUnsaved unsaved =
              new AllScalarTypesRowUnsaved(
                  Optional.of("test varchar2"),
                  Optional.of(new BigDecimal("123.45")),
                  Optional.of(LocalDateTime.of(2025, 1, 1, 12, 0)),
                  Optional.of(LocalDateTime.of(2025, 1, 2, 13, 30)),
                  Optional.of("test clob content"),
                  "required not null field",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRow inserted = repo.insert(unsaved, c);

          assertNotNull(inserted);
          assertNotNull(inserted.id());
          assertEquals(Optional.of("test varchar2"), inserted.colVarchar2());
          assertEquals(Optional.of(new BigDecimal("123.45")), inserted.colNumber());
          assertTrue(inserted.colDate().isPresent());
          assertTrue(inserted.colTimestamp().isPresent());
          assertEquals(Optional.of("test clob content"), inserted.colClob());
          assertEquals("required not null field", inserted.colNotNull());

          Optional<AllScalarTypesRow> found = repo.selectById(inserted.id(), c);
          assertTrue(found.isPresent());
          assertEquals(inserted, found.get());
        });
  }

  @Test
  public void testInsertWithNullValues() {
    OracleTestHelper.run(
        c -> {
          AllScalarTypesRowUnsaved unsaved =
              new AllScalarTypesRowUnsaved(
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  "only required field",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRow inserted = repo.insert(unsaved, c);

          assertNotNull(inserted);
          assertNotNull(inserted.id());
          assertEquals(Optional.empty(), inserted.colVarchar2());
          assertEquals(Optional.empty(), inserted.colNumber());
          assertEquals(Optional.empty(), inserted.colDate());
          assertEquals(Optional.empty(), inserted.colTimestamp());
          assertEquals(Optional.empty(), inserted.colClob());
          assertEquals("only required field", inserted.colNotNull());
        });
  }

  @Test
  public void testUpdateAllScalarTypes() {
    OracleTestHelper.run(
        c -> {
          AllScalarTypesRowUnsaved unsaved =
              new AllScalarTypesRowUnsaved(
                  Optional.of("original"),
                  Optional.of(new BigDecimal("100.00")),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  "required",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRow inserted = repo.insert(unsaved, c);

          AllScalarTypesRow updatedRow =
              inserted
                  .withColVarchar2(Optional.of("updated"))
                  .withColNumber(Optional.of(new BigDecimal("200.01")));

          Boolean wasUpdated = repo.update(updatedRow, c);
          assertTrue(wasUpdated);
          AllScalarTypesRow fetched = repo.selectById(inserted.id(), c).orElseThrow();
          assertEquals(Optional.of("updated"), fetched.colVarchar2());
          assertEquals(Optional.of(new BigDecimal("200.01")), fetched.colNumber());
        });
  }

  @Test
  public void testDeleteAllScalarTypes() {
    OracleTestHelper.run(
        c -> {
          AllScalarTypesRowUnsaved unsaved =
              new AllScalarTypesRowUnsaved(
                  Optional.of("to delete"),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  "required",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRow inserted = repo.insert(unsaved, c);

          boolean deleted = repo.deleteById(inserted.id(), c);
          assertTrue(deleted);

          Optional<AllScalarTypesRow> found = repo.selectById(inserted.id(), c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testSelectAll() {
    OracleTestHelper.run(
        c -> {
          AllScalarTypesRowUnsaved unsaved1 =
              new AllScalarTypesRowUnsaved(
                  Optional.of("row 1"),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  "required 1",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRowUnsaved unsaved2 =
              new AllScalarTypesRowUnsaved(
                  Optional.of("row 2"),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  "required 2",
                  new Defaulted.UseDefault<>());

          repo.insert(unsaved1, c);
          repo.insert(unsaved2, c);

          List<AllScalarTypesRow> all = repo.selectAll(c);
          assertTrue(all.size() >= 2);
        });
  }

  @Test
  public void testScalarTypeVarchar2() {
    OracleTestHelper.run(
        c -> {
          String longString = "A".repeat(100);
          AllScalarTypesRowUnsaved unsaved =
              new AllScalarTypesRowUnsaved(
                  Optional.of(longString),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  "required",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRow inserted = repo.insert(unsaved, c);
          assertEquals(Optional.of(longString), inserted.colVarchar2());
        });
  }

  @Test
  public void testScalarTypeNumber() {
    OracleTestHelper.run(
        c -> {
          AllScalarTypesRowUnsaved unsaved =
              new AllScalarTypesRowUnsaved(
                  Optional.empty(),
                  Optional.of(new BigDecimal("999999.99")),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  "required",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRow inserted = repo.insert(unsaved, c);
          assertEquals(Optional.of(new BigDecimal("999999.99")), inserted.colNumber());
        });
  }

  @Test
  public void testScalarTypeDate() {
    OracleTestHelper.run(
        c -> {
          LocalDateTime testDate = LocalDateTime.of(2025, 12, 31, 23, 59, 59);
          AllScalarTypesRowUnsaved unsaved =
              new AllScalarTypesRowUnsaved(
                  Optional.empty(),
                  Optional.empty(),
                  Optional.of(testDate),
                  Optional.empty(),
                  Optional.empty(),
                  "required",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRow inserted = repo.insert(unsaved, c);
          assertTrue(inserted.colDate().isPresent());
          assertEquals(2025, inserted.colDate().get().getYear());
          assertEquals(12, inserted.colDate().get().getMonthValue());
          assertEquals(31, inserted.colDate().get().getDayOfMonth());
        });
  }

  @Test
  public void testScalarTypeTimestamp() {
    OracleTestHelper.run(
        c -> {
          LocalDateTime testTimestamp = LocalDateTime.of(2025, 6, 15, 14, 30, 45);
          AllScalarTypesRowUnsaved unsaved =
              new AllScalarTypesRowUnsaved(
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.of(testTimestamp),
                  Optional.empty(),
                  "required",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRow inserted = repo.insert(unsaved, c);
          assertTrue(inserted.colTimestamp().isPresent());
          assertEquals(testTimestamp.getYear(), inserted.colTimestamp().get().getYear());
          assertEquals(
              testTimestamp.getMonthValue(), inserted.colTimestamp().get().getMonthValue());
          assertEquals(
              testTimestamp.getDayOfMonth(), inserted.colTimestamp().get().getDayOfMonth());
        });
  }

  @Test
  public void testScalarTypeClob() {
    OracleTestHelper.run(
        c -> {
          String largeClobContent = "Clob content ".repeat(1000);
          AllScalarTypesRowUnsaved unsaved =
              new AllScalarTypesRowUnsaved(
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.of(largeClobContent),
                  "required",
                  new Defaulted.UseDefault<>());

          AllScalarTypesRow inserted = repo.insert(unsaved, c);
          assertEquals(Optional.of(largeClobContent), inserted.colClob());
        });
  }
}
