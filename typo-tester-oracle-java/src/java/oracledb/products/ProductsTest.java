package oracledb.products;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import oracledb.MoneyT;
import oracledb.OracleTestHelper;
import oracledb.TagVarrayT;
import oracledb.customtypes.Defaulted;
import org.junit.Test;

public class ProductsTest {
  private final ProductsRepoImpl repo = new ProductsRepoImpl();

  @Test
  public void testInsertProductWithVarrayTags() {
    OracleTestHelper.run(
        c -> {
          MoneyT price = new MoneyT(new BigDecimal("99.99"), "USD");
          TagVarrayT tags = new TagVarrayT(new String[] {"electronics", "gadget", "new"});
          String uniqueSku = "PROD-" + System.currentTimeMillis();

          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  uniqueSku,
                  "Test Product",
                  price,
                  Optional.of(tags),
                  new Defaulted.UseDefault<>());

          ProductsId insertedId = repo.insert(unsaved, c);

          assertNotNull(insertedId);
          ProductsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertEquals(uniqueSku, inserted.sku());
          assertEquals("Test Product", inserted.name());
          assertEquals(price, inserted.price());
          assertTrue(inserted.tags().isPresent());
          assertArrayEquals(
              new String[] {"electronics", "gadget", "new"}, inserted.tags().get().value());
        });
  }

  @Test
  public void testInsertProductWithoutTags() {
    OracleTestHelper.run(
        c -> {
          MoneyT price = new MoneyT(new BigDecimal("49.99"), "EUR");

          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  "PROD-002",
                  "Product Without Tags",
                  price,
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ProductsId insertedId = repo.insert(unsaved, c);

          assertNotNull(insertedId);
          ProductsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertEquals("PROD-002", inserted.sku());
          assertFalse(inserted.tags().isPresent());
        });
  }

  @Test
  public void testVarrayRoundtrip() {
    OracleTestHelper.run(
        c -> {
          String[] tagArray = new String[] {"tag1", "tag2", "tag3", "tag4", "tag5"};
          TagVarrayT tags = new TagVarrayT(tagArray);

          MoneyT price = new MoneyT(new BigDecimal("199.99"), "USD");

          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  "PROD-VARRAY",
                  "Varray Test Product",
                  price,
                  Optional.of(tags),
                  new Defaulted.UseDefault<>());

          ProductsId insertedId = repo.insert(unsaved, c);

          Optional<ProductsRow> found = repo.selectById(insertedId, c);
          assertTrue(found.isPresent());
          assertTrue(found.get().tags().isPresent());
          assertArrayEquals(tagArray, found.get().tags().get().value());
        });
  }

  @Test
  public void testUpdateTags() {
    OracleTestHelper.run(
        c -> {
          TagVarrayT originalTags = new TagVarrayT(new String[] {"old", "tags"});
          MoneyT price = new MoneyT(new BigDecimal("99.99"), "USD");

          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  "PROD-UPDATE",
                  "Update Tags Test",
                  price,
                  Optional.of(originalTags),
                  new Defaulted.UseDefault<>());

          ProductsId insertedId = repo.insert(unsaved, c);
          ProductsRow inserted = repo.selectById(insertedId, c).orElseThrow();

          TagVarrayT newTags = new TagVarrayT(new String[] {"new", "updated", "tags"});
          ProductsRow updatedRow = inserted.withTags(Optional.of(newTags));

          Boolean wasUpdated = repo.update(updatedRow, c);
          assertTrue(wasUpdated);
          ProductsRow fetched = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(fetched.tags().isPresent());
          assertArrayEquals(new String[] {"new", "updated", "tags"}, fetched.tags().get().value());
        });
  }

  @Test
  public void testUpdatePrice() {
    OracleTestHelper.run(
        c -> {
          MoneyT originalPrice = new MoneyT(new BigDecimal("100.00"), "USD");
          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  "PROD-PRICE",
                  "Price Update Test",
                  originalPrice,
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          ProductsId insertedId = repo.insert(unsaved, c);
          ProductsRow inserted = repo.selectById(insertedId, c).orElseThrow();

          MoneyT newPrice = new MoneyT(new BigDecimal("150.01"), "EUR");
          ProductsRow updatedRow = inserted.withPrice(newPrice);

          Boolean wasUpdated = repo.update(updatedRow, c);
          assertTrue(wasUpdated);
          ProductsRow fetched = repo.selectById(insertedId, c).orElseThrow();
          assertEquals(new BigDecimal("150.01"), fetched.price().amount());
          assertEquals("EUR", fetched.price().currency());
        });
  }

  @Test
  public void testVarrayWithSingleElement() {
    OracleTestHelper.run(
        c -> {
          TagVarrayT tags = new TagVarrayT(new String[] {"single"});
          MoneyT price = new MoneyT(new BigDecimal("10.00"), "USD");

          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  "PROD-SINGLE",
                  "Single Tag Product",
                  price,
                  Optional.of(tags),
                  new Defaulted.UseDefault<>());

          ProductsId insertedId = repo.insert(unsaved, c);
          ProductsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(inserted.tags().isPresent());
          assertEquals(1, inserted.tags().get().value().length);
          assertEquals("single", inserted.tags().get().value()[0]);
        });
  }

  @Test
  public void testVarrayWithMaxSize() {
    OracleTestHelper.run(
        c -> {
          String[] maxTags =
              new String[] {
                "tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9", "tag10"
              };
          TagVarrayT tags = new TagVarrayT(maxTags);
          MoneyT price = new MoneyT(new BigDecimal("299.99"), "USD");

          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  "PROD-MAX",
                  "Max Tags Product",
                  price,
                  Optional.of(tags),
                  new Defaulted.UseDefault<>());

          ProductsId insertedId = repo.insert(unsaved, c);
          ProductsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(inserted.tags().isPresent());
          assertEquals(10, inserted.tags().get().value().length);
          assertArrayEquals(maxTags, inserted.tags().get().value());
        });
  }

  @Test
  public void testVarrayEquality() {
    TagVarrayT tags1 = new TagVarrayT(new String[] {"a", "b", "c"});
    TagVarrayT tags2 = new TagVarrayT(new String[] {"a", "b", "c"});

    assertFalse(tags1.equals(tags2));
    assertArrayEquals(tags1.value(), tags2.value());
  }

  @Test
  public void testDeleteProduct() {
    OracleTestHelper.run(
        c -> {
          MoneyT price = new MoneyT(new BigDecimal("99.99"), "USD");
          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  "PROD-DELETE",
                  "To Delete",
                  price,
                  Optional.of(new TagVarrayT(new String[] {"delete"})),
                  new Defaulted.UseDefault<>());

          ProductsId insertedId = repo.insert(unsaved, c);

          boolean deleted = repo.deleteById(insertedId, c);
          assertTrue(deleted);

          Optional<ProductsRow> found = repo.selectById(insertedId, c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testSelectAll() {
    OracleTestHelper.run(
        c -> {
          MoneyT price1 = new MoneyT(new BigDecimal("10.00"), "USD");
          MoneyT price2 = new MoneyT(new BigDecimal("20.00"), "EUR");

          ProductsRowUnsaved unsaved1 =
              new ProductsRowUnsaved(
                  "PROD-ALL-1",
                  "Product 1",
                  price1,
                  Optional.of(new TagVarrayT(new String[] {"tag1"})),
                  new Defaulted.UseDefault<>());

          ProductsRowUnsaved unsaved2 =
              new ProductsRowUnsaved(
                  "PROD-ALL-2",
                  "Product 2",
                  price2,
                  Optional.empty(),
                  new Defaulted.UseDefault<>());

          repo.insert(unsaved1, c);
          repo.insert(unsaved2, c);

          List<ProductsRow> all = repo.selectAll(c);
          assertTrue(all.size() >= 2);
        });
  }

  @Test
  public void testClearTags() {
    OracleTestHelper.run(
        c -> {
          TagVarrayT originalTags = new TagVarrayT(new String[] {"tag1", "tag2"});
          MoneyT price = new MoneyT(new BigDecimal("50.00"), "USD");

          ProductsRowUnsaved unsaved =
              new ProductsRowUnsaved(
                  "PROD-CLEAR",
                  "Clear Tags Test",
                  price,
                  Optional.of(originalTags),
                  new Defaulted.UseDefault<>());

          ProductsId insertedId = repo.insert(unsaved, c);
          ProductsRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(inserted.tags().isPresent());

          ProductsRow cleared = inserted.withTags(Optional.empty());
          Boolean wasUpdated = repo.update(cleared, c);

          assertTrue(wasUpdated);
          assertFalse(cleared.tags().isPresent());
        });
  }
}
