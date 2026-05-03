package testdb;

import static org.junit.Assert.*;

import dev.typr.foundations.data.Xml;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Random;
import org.junit.Test;
import testdb.db2test.*;
import testdb.db2testnull.*;

/**
 * Comprehensive tests for all DB2 scalar data types. Tests the db2test table covering DB2 numeric,
 * string, binary, and date/time types.
 */
public class AllTypesTest {
  private final TestInsert testInsert = new TestInsert(new Random(1682197193));
  private final Db2testRepoImpl db2testRepo = new Db2testRepoImpl();
  private final Db2testnullRepoImpl db2testnullRepo = new Db2testnullRepoImpl();

  @Test
  public void testInsertAndSelectAllTypes() {
    Db2TestHelper.run(
        c -> {
          byte[] binary = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
          byte[] varbinary = new byte[] {6, 7, 8};
          byte[] blob = new byte[] {9, 10, 11, 12};

          // Override graphicCol to fit GRAPHIC(10) column size limit
          var row =
              testInsert
                  .Db2test(binary, varbinary, blob)
                  .with(r -> r.withGraphicCol("test      "))
                  .insert(c);

          assertNotNull(row);
          assertNotNull(row.id());

          var found = db2testRepo.selectById(row.id(), c);
          assertTrue(found.isPresent());
          assertEquals(row.id(), found.get().id());
        });
  }

  @Test
  public void testIntegerTypes() {
    Db2TestHelper.run(
        c -> {
          byte[] binary = new byte[16];
          var row =
              testInsert
                  .Db2test(binary, binary, binary)
                  .with(
                      r ->
                          r.withSmallintCol((short) 123)
                              .withIntCol(new Db2testId(456))
                              .withBigintCol(789L)
                              .withGraphicCol("test      "))
                  .insert(c);

          assertEquals(Short.valueOf((short) 123), row.smallintCol());
          assertEquals(new Db2testId(456), row.id());
          assertEquals(Long.valueOf(789L), row.bigintCol());
        });
  }

  @Test
  public void testDecimalTypes() {
    Db2TestHelper.run(
        c -> {
          byte[] binary = new byte[16];
          var row =
              testInsert
                  .Db2test(binary, binary, binary)
                  .with(
                      r ->
                          r.withDecimalCol(new BigDecimal("1234.56"))
                              .withNumericCol(new BigDecimal("7890.12"))
                              .withGraphicCol("test      "))
                  .insert(c);

          assertEquals(0, new BigDecimal("1234.56").compareTo(row.decimalCol()));
        });
  }

  @Test
  public void testStringTypes() {
    Db2TestHelper.run(
        c -> {
          byte[] binary = new byte[16];
          var row =
              testInsert
                  .Db2test(binary, binary, binary)
                  .with(
                      r ->
                          r.withCharCol("char val  ")
                              .withVarcharCol("varchar value")
                              .withClobCol("clob content")
                              .withGraphicCol("test      "))
                  .insert(c);

          assertEquals("char val  ", row.charCol());
          assertEquals("varchar value", row.varcharCol());
          assertEquals("clob content", row.clobCol());
        });
  }

  @Test
  public void testDateTimeTypes() {
    Db2TestHelper.run(
        c -> {
          byte[] binary = new byte[16];
          LocalDate date = LocalDate.of(2025, 6, 15);
          LocalTime time = LocalTime.of(14, 30, 45);
          LocalDateTime timestamp = LocalDateTime.of(2025, 6, 15, 14, 30, 45);

          var row =
              testInsert
                  .Db2test(binary, binary, binary)
                  .with(
                      r ->
                          r.withDateCol(date)
                              .withTimeCol(time)
                              .withTimestampCol(timestamp)
                              .withGraphicCol("test      "))
                  .insert(c);

          assertEquals(date, row.dateCol());
          assertEquals(time, row.timeCol());
          assertEquals(timestamp, row.timestampCol());
        });
  }

  @Test
  public void testBinaryTypes() {
    Db2TestHelper.run(
        c -> {
          byte[] binary =
              new byte[] {0x00, 0x01, (byte) 0xFF, (byte) 0xFE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
          byte[] varbinary = new byte[] {0x10, 0x20, 0x30};
          byte[] blob = new byte[] {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF};

          var row =
              testInsert
                  .Db2test(binary, varbinary, blob)
                  .with(r -> r.withGraphicCol("test      "))
                  .insert(c);

          var found = db2testRepo.selectById(row.id(), c).orElseThrow();
          assertArrayEquals(varbinary, found.varbinaryCol());
        });
  }

  @Test
  public void testNullableTypes() {
    Db2TestHelper.run(
        c -> {
          var row =
              testInsert
                  .Db2testnull()
                  .with(r -> r.withGraphicCol(Optional.of("test      ")))
                  .insert(c);
          assertNotNull(row);
        });
  }

  @Test
  public void testNullableTypesWithValues() {
    Db2TestHelper.run(
        c -> {
          var row =
              testInsert
                  .Db2testnull()
                  .with(
                      r ->
                          r.withSmallintCol(Optional.of((short) 100))
                              .withIntCol(Optional.of(200))
                              .withCharCol(Optional.of("test"))
                              .withGraphicCol(Optional.of("test      ")))
                  .insert(c);

          assertEquals(Optional.of((short) 100), row.smallintCol());
          assertEquals(Optional.of(200), row.intCol());
        });
  }

  @Test
  public void testUpdate() {
    Db2TestHelper.run(
        c -> {
          byte[] binary = new byte[16];
          var inserted =
              testInsert
                  .Db2test(binary, binary, binary)
                  .with(r -> r.withGraphicCol("test      "))
                  .insert(c);

          var updated =
              inserted.withVarcharCol("updated varchar").withDecimalCol(new BigDecimal("9999.99"));

          boolean wasUpdated = db2testRepo.update(updated, c);
          assertTrue(wasUpdated);

          var found = db2testRepo.selectById(inserted.id(), c).orElseThrow();
          assertEquals("updated varchar", found.varcharCol());
        });
  }

  @Test
  public void testDelete() {
    Db2TestHelper.run(
        c -> {
          byte[] binary = new byte[16];
          var inserted =
              testInsert
                  .Db2test(binary, binary, binary)
                  .with(r -> r.withGraphicCol("test      "))
                  .insert(c);

          boolean deleted = db2testRepo.deleteById(inserted.id(), c);
          assertTrue(deleted);

          var found = db2testRepo.selectById(inserted.id(), c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testBooleanType() {
    Db2TestHelper.run(
        c -> {
          byte[] binary = new byte[16];
          var rowTrue =
              testInsert
                  .Db2test(binary, binary, binary)
                  .with(r -> r.withBoolCol(true).withGraphicCol("test      "))
                  .insert(c);
          var rowFalse =
              testInsert
                  .Db2test(binary, binary, binary)
                  .with(r -> r.withBoolCol(false).withGraphicCol("test      "))
                  .insert(c);

          assertTrue(rowTrue.boolCol());
          assertFalse(rowFalse.boolCol());
        });
  }

  @Test
  public void testXmlType() {
    Db2TestHelper.run(
        c -> {
          byte[] binary = new byte[16];
          var xml = new Xml("<root><element>value</element></root>");
          var row =
              testInsert
                  .Db2test(binary, binary, binary)
                  .with(r -> r.withXmlCol(xml).withGraphicCol("test      "))
                  .insert(c);

          assertNotNull(row.xmlCol());
          assertTrue(row.xmlCol().value().contains("element"));
        });
  }
}
