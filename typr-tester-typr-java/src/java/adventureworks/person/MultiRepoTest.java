package adventureworks.person;

import static org.junit.Assert.*;

import adventureworks.DomainInsertImpl;
import adventureworks.TestInsert;
import adventureworks.WithConnection;
import adventureworks.customtypes.Defaulted;
import adventureworks.person.address.*;
import adventureworks.person.addresstype.*;
import adventureworks.person.businessentityaddress.*;
import adventureworks.person.countryregion.CountryregionId;
import adventureworks.person.person.*;
import adventureworks.public_.Name;
import adventureworks.userdefined.FirstName;
import java.sql.Connection;
import java.util.*;
import org.junit.Test;

/** PersonWithAddresses domain object - a person with their addresses keyed by address type name. */
record PersonWithAddresses(PersonRow person, Map<Name, AddressRow> addresses) {}

/** Multi-repo that syncs PersonWithAddresses to the database. */
class PersonWithAddressesRepo {
  private final PersonRepoImpl personRepo;
  private final BusinessentityaddressRepoImpl businessentityAddressRepo;
  private final AddresstypeRepoImpl addresstypeRepo;
  private final AddressRepoImpl addressRepo;

  PersonWithAddressesRepo(
      PersonRepoImpl personRepo,
      BusinessentityaddressRepoImpl businessentityAddressRepo,
      AddresstypeRepoImpl addresstypeRepo,
      AddressRepoImpl addressRepo) {
    this.personRepo = personRepo;
    this.businessentityAddressRepo = businessentityAddressRepo;
    this.addresstypeRepo = addresstypeRepo;
    this.addressRepo = addressRepo;
  }

  /**
   * A person can have a bunch of addresses registered, and they each have an address type (BILLING,
   * HOME, etc).
   *
   * <p>This method syncs PersonWithAddresses#addresses to postgres, so that old attached addresses
   * are removed, and the given addresses are attached with the chosen type
   */
  List<BusinessentityaddressRow> syncAddresses(PersonWithAddresses pa, Connection c) {
    // update person
    personRepo.update(pa.person(), c);
    // update stored addresses
    pa.addresses().forEach((name, address) -> addressRepo.update(address, c));

    // addresses are stored in PersonWithAddress by a Name which means what type of address it is.
    // this address type is stored in addresstypeRepo.
    // In order for foreign keys to align, we need to translate from names to ids, and create rows
    // as necessary
    Name[] addressTypeNames = pa.addresses().keySet().toArray(new Name[0]);
    Map<Name, AddresstypeId> oldStoredAddressTypes = new HashMap<>();
    addresstypeRepo
        .select()
        .where(r -> r.name().in(addressTypeNames))
        .toList(c)
        .forEach(at -> oldStoredAddressTypes.put(at.name(), at.addresstypeid()));

    Map<AddresstypeId, AddressRow> currentAddressesByType = new HashMap<>();
    pa.addresses()
        .forEach(
            (addressTypeName, wanted) -> {
              AddresstypeId addresstypeId = oldStoredAddressTypes.get(addressTypeName);
              if (addresstypeId != null) {
                currentAddressesByType.put(addresstypeId, wanted);
              } else {
                var inserted =
                    addresstypeRepo.insert(new AddresstypeRowUnsaved(addressTypeName), c);
                currentAddressesByType.put(inserted.addresstypeid(), wanted);
              }
            });

    // discover existing addresses attached to person
    Map<AddressIdAddresstypeIdKey, BusinessentityaddressRow> oldAttachedAddresses = new HashMap<>();
    businessentityAddressRepo
        .select()
        .where(x -> x.businessentityid().isEqual(pa.person().businessentityid()))
        .toList(c)
        .forEach(
            x ->
                oldAttachedAddresses.put(
                    new AddressIdAddresstypeIdKey(x.addressid(), x.addresstypeid()), x));

    // unattach old attached addresses
    oldAttachedAddresses.forEach(
        (key, ba) -> {
          var matching = currentAddressesByType.get(ba.addresstypeid());
          if (matching == null || !matching.addressid().equals(ba.addressid())) {
            businessentityAddressRepo.deleteById(ba.compositeId(), c);
          }
        });

    // attach new addresses
    List<BusinessentityaddressRow> result = new ArrayList<>();
    currentAddressesByType.forEach(
        (addresstypeId, address) -> {
          var key = new AddressIdAddresstypeIdKey(address.addressid(), addresstypeId);
          var existing = oldAttachedAddresses.get(key);
          if (existing != null) {
            result.add(existing);
          } else {
            var newRow =
                new BusinessentityaddressRowUnsaved(
                    pa.person().businessentityid(), address.addressid(), addresstypeId);
            result.add(businessentityAddressRepo.insert(newRow, c));
          }
        });
    return result;
  }
}

record AddressIdAddresstypeIdKey(AddressId addressId, AddresstypeId addresstypeId) {}

/** Tests for multi-repo patterns - equivalent to Scala MultiRepoTest. */
public class MultiRepoTest {
  @Test
  public void works() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new Random(1), new DomainInsertImpl());

          // Create test data
          var businessentityRow =
              testInsert.personBusinessentity(
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  c);
          var personRow =
              testInsert.personPerson(
                  businessentityRow.businessentityid(),
                  "SC",
                  new FirstName("name"),
                  Optional.empty(),
                  Optional.empty(),
                  new Name("lastname"),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  c);
          var countryregionRow =
              testInsert.personCountryregion(
                  new CountryregionId("NOR"), new Name("Norway"), new Defaulted.UseDefault<>(), c);
          var salesterritoryRow =
              testInsert.salesSalesterritory(
                  countryregionRow.countryregioncode(),
                  new Name("Territory"),
                  "Europe",
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  c);
          var stateprovinceRow =
              testInsert.personStateprovince(
                  countryregionRow.countryregioncode(),
                  salesterritoryRow.territoryid(),
                  "OSL",
                  new Name("Oslo"),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  c);
          var addressRow1 =
              testInsert.personAddress(
                  stateprovinceRow.stateprovinceid(),
                  "Street 1",
                  Optional.empty(),
                  "Oslo",
                  "0001",
                  Optional.empty(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  c);
          var addressRow2 =
              testInsert.personAddress(
                  stateprovinceRow.stateprovinceid(),
                  "Street 2",
                  Optional.empty(),
                  "Oslo",
                  "0002",
                  Optional.empty(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  c);
          var addressRow3 =
              testInsert.personAddress(
                  stateprovinceRow.stateprovinceid(),
                  "Street 3",
                  Optional.empty(),
                  "Oslo",
                  "0003",
                  Optional.empty(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  new Defaulted.UseDefault<>(),
                  c);

          var businessentityaddressRepo = new BusinessentityaddressRepoImpl();
          var repo =
              new PersonWithAddressesRepo(
                  new PersonRepoImpl(),
                  businessentityaddressRepo,
                  new AddresstypeRepoImpl(),
                  new AddressRepoImpl());

          // Initial sync with HOME and OFFICE
          repo.syncAddresses(
              new PersonWithAddresses(
                  personRow,
                  Map.of(
                      new Name("HOME"), addressRow1,
                      new Name("OFFICE"), addressRow2)),
              c);

          // Helper to fetch business entity addresses
          var allAddressIds =
              new AddressId[] {
                addressRow1.addressid(), addressRow2.addressid(), addressRow3.addressid()
              };
          var fetchBAs =
              (java.util.function.Supplier<List<BusinessentityaddressRow>>)
                  () ->
                      businessentityaddressRepo
                          .select()
                          .where(p -> p.addressid().in(allAddressIds))
                          .orderBy(ba -> ba.addressid().asc())
                          .toList(c);

          var bas1 = fetchBAs.get();
          assertEquals(2, bas1.size());
          assertEquals(personRow.businessentityid(), bas1.get(0).businessentityid());
          assertEquals(addressRow1.addressid(), bas1.get(0).addressid());
          var homeId = bas1.get(0).addresstypeid();
          assertEquals(personRow.businessentityid(), bas1.get(1).businessentityid());
          assertEquals(addressRow2.addressid(), bas1.get(1).addressid());
          var officeId = bas1.get(1).addresstypeid();

          // Check idempotency - sync again with same data
          repo.syncAddresses(
              new PersonWithAddresses(
                  personRow,
                  Map.of(
                      new Name("HOME"), addressRow1,
                      new Name("OFFICE"), addressRow2)),
              c);

          var bas2 = fetchBAs.get();
          assertEquals(2, bas2.size());
          assertEquals(homeId, bas2.get(0).addresstypeid());
          assertEquals(officeId, bas2.get(1).addresstypeid());

          // Remove OFFICE
          repo.syncAddresses(
              new PersonWithAddresses(personRow, Map.of(new Name("HOME"), addressRow1)), c);

          var bas3 = fetchBAs.get();
          assertEquals(1, bas3.size());
          assertEquals(homeId, bas3.get(0).addresstypeid());

          // Add VACATION
          repo.syncAddresses(
              new PersonWithAddresses(
                  personRow,
                  Map.of(
                      new Name("HOME"), addressRow1,
                      new Name("VACATION"), addressRow3)),
              c);

          var bas4 = fetchBAs.get();
          assertEquals(2, bas4.size());
          assertEquals(homeId, bas4.get(0).addresstypeid());
          assertEquals(addressRow3.addressid(), bas4.get(1).addressid());
        });
  }
}
