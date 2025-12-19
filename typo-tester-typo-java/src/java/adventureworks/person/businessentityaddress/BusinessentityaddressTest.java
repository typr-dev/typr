package adventureworks.person.businessentityaddress;

import static org.junit.Assert.*;

import adventureworks.DbNow;
import adventureworks.WithConnection;
import adventureworks.customtypes.Defaulted;
import adventureworks.person.address.AddressRepoImpl;
import adventureworks.person.address.AddressRowUnsaved;
import adventureworks.person.addresstype.AddresstypeRepoImpl;
import adventureworks.person.addresstype.AddresstypeRowUnsaved;
import adventureworks.person.businessentity.BusinessentityRepoImpl;
import adventureworks.person.businessentity.BusinessentityRowUnsaved;
import adventureworks.person.countryregion.CountryregionId;
import adventureworks.person.countryregion.CountryregionRepoImpl;
import adventureworks.person.countryregion.CountryregionRowUnsaved;
import adventureworks.person.stateprovince.StateprovinceRepoImpl;
import adventureworks.person.stateprovince.StateprovinceRowUnsaved;
import adventureworks.public_.Name;
import adventureworks.sales.salesterritory.SalesterritoryRepoImpl;
import adventureworks.sales.salesterritory.SalesterritoryRowUnsaved;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;

public class BusinessentityaddressTest {
  private final BusinessentityaddressRepoImpl businessentityaddressRepo =
      new BusinessentityaddressRepoImpl();
  private final BusinessentityRepoImpl businessentityRepo = new BusinessentityRepoImpl();
  private final CountryregionRepoImpl countryregionRepo = new CountryregionRepoImpl();
  private final SalesterritoryRepoImpl salesterritoryRepo = new SalesterritoryRepoImpl();
  private final StateprovinceRepoImpl stateprovinceRepo = new StateprovinceRepoImpl();
  private final AddressRepoImpl addressRepo = new AddressRepoImpl();
  private final AddresstypeRepoImpl addresstypeRepo = new AddresstypeRepoImpl();

  @Test
  public void works() {
    WithConnection.run(
        c -> {
          // setup - all fields Defaulted for businessentity, no short ctor
          var businessentityRow =
              businessentityRepo.insert(
                  new BusinessentityRowUnsaved(
                      new Defaulted.UseDefault<>(),
                      new Defaulted.UseDefault<>(),
                      new Defaulted.UseDefault<>()),
                  c);

          var countryregion =
              countryregionRepo.insert(
                  new CountryregionRowUnsaved(new CountryregionId("max"), new Name("max")), c);

          var salesTerritory =
              salesterritoryRepo.insert(
                  new SalesterritoryRowUnsaved(
                          new Name("name"), countryregion.countryregioncode(), "flaff")
                      .withSalesytd(new Defaulted.Provided<>(java.math.BigDecimal.ONE)),
                  c);

          var stateProvidence =
              stateprovinceRepo.insert(
                  new StateprovinceRowUnsaved(
                      "cde",
                      countryregion.countryregioncode(),
                      new Name("name"),
                      salesTerritory.territoryid()),
                  c);

          var address =
              addressRepo.insert(
                  new AddressRowUnsaved(
                          "addressline1", "city", stateProvidence.stateprovinceid(), "postalcode")
                      .withAddressline2(Optional.of("addressline2")),
                  c);

          var addressType = addresstypeRepo.insert(new AddresstypeRowUnsaved(new Name("name")), c);

          var unsaved1 =
              new BusinessentityaddressRowUnsaved(
                      businessentityRow.businessentityid(),
                      address.addressid(),
                      addressType.addresstypeid())
                  .withRowguid(new Defaulted.Provided<>(UUID.randomUUID()))
                  .withModifieddate(new Defaulted.Provided<>(DbNow.localDateTime()));

          // insert and round trip check
          var saved1 = businessentityaddressRepo.insert(unsaved1, c);
          var saved2 = unsaved1.toRow(saved1::rowguid, saved1::modifieddate);
          assertEquals(saved1, saved2);

          // check field values
          var newModifiedDate = saved1.modifieddate().minusDays(1);
          businessentityaddressRepo.update(saved1.withModifieddate(newModifiedDate), c);
          var all = businessentityaddressRepo.selectAll(c);
          assertEquals(1, all.size());
          assertEquals(newModifiedDate, all.get(0).modifieddate());

          // delete
          businessentityaddressRepo.deleteById(saved1.compositeId(), c);
          var afterDelete = businessentityaddressRepo.selectAll(c);
          assertTrue(afterDelete.isEmpty());
        });
  }
}
