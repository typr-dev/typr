package oracledb.customers;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import oracledb.AddressT;
import oracledb.CoordinatesT;
import oracledb.MoneyT;
import oracledb.OracleTestHelper;
import oracledb.customtypes.Defaulted;
import org.junit.Test;

public class CustomersTest {
  private final CustomersRepoImpl repo = new CustomersRepoImpl();

  @Test
  public void testInsertCustomerWithAddressAndMoney() {
    OracleTestHelper.run(
        c -> {
          CoordinatesT coords =
              new CoordinatesT(new BigDecimal("40.7128"), new BigDecimal("-74.0061"));

          AddressT address = new AddressT("123 Main St", "New York", coords);

          MoneyT creditLimit = new MoneyT(new BigDecimal("10000.01"), "USD");

          CustomersRowUnsaved unsaved =
              new CustomersRowUnsaved(
                  "John Doe",
                  address,
                  Optional.of(creditLimit),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>());

          CustomersId insertedId = repo.insert(unsaved, c);

          assertNotNull(insertedId);
          CustomersRow inserted = repo.selectById(insertedId, c).orElseThrow();
          assertNotNull(inserted.customerId());
          assertEquals("John Doe", inserted.name());
          assertEquals(address, inserted.billingAddress());
          assertTrue(inserted.creditLimit().isPresent());
          assertEquals(creditLimit, inserted.creditLimit().get());
          assertNotNull(inserted.createdAt());
        });
  }

  @Test
  public void testNestedObjectTypeRoundtrip() {
    OracleTestHelper.run(
        c -> {
          CoordinatesT coords =
              new CoordinatesT(new BigDecimal("37.7749"), new BigDecimal("-122.4194"));

          AddressT address = new AddressT("456 Market St", "San Francisco", coords);

          CustomersRowUnsaved unsaved =
              new CustomersRowUnsaved(
                  "Jane Smith",
                  address,
                  Optional.empty(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>());

          CustomersId insertedId = repo.insert(unsaved, c);
          CustomersRow inserted = repo.selectById(insertedId, c).orElseThrow();

          assertEquals("456 Market St", inserted.billingAddress().street());
          assertEquals("San Francisco", inserted.billingAddress().city());
          assertEquals(new BigDecimal("37.7749"), inserted.billingAddress().location().latitude());
          assertEquals(
              new BigDecimal("-122.4194"), inserted.billingAddress().location().longitude());

          Optional<CustomersRow> found = repo.selectById(insertedId, c);
          assertTrue(found.isPresent());
          assertEquals(inserted, found.get());
        });
  }

  @Test
  public void testUpdateAddress() {
    OracleTestHelper.run(
        c -> {
          CoordinatesT originalCoords =
              new CoordinatesT(new BigDecimal("40.7128"), new BigDecimal("-74.0061"));

          AddressT originalAddress = new AddressT("123 Main St", "New York", originalCoords);

          CustomersRowUnsaved unsaved =
              new CustomersRowUnsaved(
                  "Update Test",
                  originalAddress,
                  Optional.empty(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>());

          CustomersId insertedId = repo.insert(unsaved, c);
          CustomersRow inserted = repo.selectById(insertedId, c).orElseThrow();

          CoordinatesT newCoords =
              new CoordinatesT(new BigDecimal("34.0522"), new BigDecimal("-118.2437"));

          AddressT newAddress = new AddressT("789 Sunset Blvd", "Los Angeles", newCoords);

          CustomersRow updatedRow = inserted.withBillingAddress(newAddress);
          Boolean wasUpdated = repo.update(updatedRow, c);

          assertTrue(wasUpdated);
          CustomersRow fetched = repo.selectById(insertedId, c).orElseThrow();
          assertEquals("789 Sunset Blvd", fetched.billingAddress().street());
          assertEquals("Los Angeles", fetched.billingAddress().city());
          assertEquals(new BigDecimal("34.0522"), fetched.billingAddress().location().latitude());
        });
  }

  @Test
  public void testUpdateCreditLimit() {
    OracleTestHelper.run(
        c -> {
          AddressT address =
              new AddressT(
                  "123 Test St", "Test City", new CoordinatesT(BigDecimal.ZERO, BigDecimal.ZERO));

          MoneyT originalLimit = new MoneyT(new BigDecimal("5000.00"), "USD");

          CustomersRowUnsaved unsaved =
              new CustomersRowUnsaved(
                  "Credit Limit Test",
                  address,
                  Optional.of(originalLimit),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>());

          CustomersId insertedId = repo.insert(unsaved, c);
          CustomersRow inserted = repo.selectById(insertedId, c).orElseThrow();

          MoneyT newLimit = new MoneyT(new BigDecimal("15000.01"), "EUR");

          CustomersRow updatedRow = inserted.withCreditLimit(Optional.of(newLimit));
          Boolean wasUpdated = repo.update(updatedRow, c);

          assertTrue(wasUpdated);
          CustomersRow fetched = repo.selectById(insertedId, c).orElseThrow();
          assertTrue(fetched.creditLimit().isPresent());
          assertEquals(new BigDecimal("15000.01"), fetched.creditLimit().get().amount());
          assertEquals("EUR", fetched.creditLimit().get().currency());
        });
  }

  @Test
  public void testDeleteCustomer() {
    OracleTestHelper.run(
        c -> {
          AddressT address =
              new AddressT(
                  "Delete St", "Delete City", new CoordinatesT(BigDecimal.ONE, BigDecimal.ONE));

          CustomersRowUnsaved unsaved =
              new CustomersRowUnsaved(
                  "To Delete",
                  address,
                  Optional.empty(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>());

          CustomersId insertedId = repo.insert(unsaved, c);

          boolean deleted = repo.deleteById(insertedId, c);
          assertTrue(deleted);

          Optional<CustomersRow> found = repo.selectById(insertedId, c);
          assertFalse(found.isPresent());
        });
  }

  @Test
  public void testSelectAll() {
    OracleTestHelper.run(
        c -> {
          AddressT address1 =
              new AddressT(
                  "Address 1",
                  "City 1",
                  new CoordinatesT(new BigDecimal("10.0"), new BigDecimal("20.0")));

          AddressT address2 =
              new AddressT(
                  "Address 2",
                  "City 2",
                  new CoordinatesT(new BigDecimal("30.0"), new BigDecimal("40.0")));

          CustomersRowUnsaved unsaved1 =
              new CustomersRowUnsaved(
                  "Customer 1",
                  address1,
                  Optional.empty(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>());

          CustomersRowUnsaved unsaved2 =
              new CustomersRowUnsaved(
                  "Customer 2",
                  address2,
                  Optional.of(new MoneyT(new BigDecimal("1000"), "USD")),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>());

          repo.insert(unsaved1, c);
          repo.insert(unsaved2, c);

          List<CustomersRow> all = repo.selectAll(c);
          assertTrue(all.size() >= 2);
        });
  }

  @Test
  public void testObjectTypeEquality() {
    CoordinatesT coords1 = new CoordinatesT(new BigDecimal("40.7128"), new BigDecimal("-74.0061"));

    CoordinatesT coords2 = new CoordinatesT(new BigDecimal("40.7128"), new BigDecimal("-74.0061"));

    assertEquals(coords1, coords2);

    AddressT address1 = new AddressT("123 Main St", "New York", coords1);
    AddressT address2 = new AddressT("123 Main St", "New York", coords2);

    assertEquals(address1, address2);

    MoneyT money1 = new MoneyT(new BigDecimal("100.00"), "USD");
    MoneyT money2 = new MoneyT(new BigDecimal("100.00"), "USD");

    assertEquals(money1, money2);
  }

  @Test
  public void testObjectTypeWithMethods() {
    CoordinatesT coords = new CoordinatesT(new BigDecimal("40.7128"), new BigDecimal("-74.0061"));

    CoordinatesT modified = coords.withLatitude(new BigDecimal("50.0"));
    assertEquals(new BigDecimal("50.0"), modified.latitude());
    assertEquals(new BigDecimal("-74.0061"), modified.longitude());

    AddressT address = new AddressT("123 Main St", "New York", coords);
    AddressT modifiedAddress = address.withCity("Boston");
    assertEquals("123 Main St", modifiedAddress.street());
    assertEquals("Boston", modifiedAddress.city());

    MoneyT money = new MoneyT(new BigDecimal("100.00"), "USD");
    MoneyT modifiedMoney = money.withCurrency("EUR");
    assertEquals(new BigDecimal("100.00"), modifiedMoney.amount());
    assertEquals("EUR", modifiedMoney.currency());
  }
}
