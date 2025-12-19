package adventureworks.person.businessentityaddress

import adventureworks.{DbNow, WithConnection}
import adventureworks.customtypes.Defaulted
import adventureworks.person.address.{AddressRepoImpl, AddressRowUnsaved}
import adventureworks.person.addresstype.{AddresstypeRepoImpl, AddresstypeRowUnsaved}
import adventureworks.person.businessentity.{BusinessentityRepoImpl, BusinessentityRowUnsaved}
import adventureworks.person.countryregion.{CountryregionId, CountryregionRepoImpl, CountryregionRowUnsaved}
import adventureworks.person.stateprovince.{StateprovinceRepoImpl, StateprovinceRowUnsaved}
import adventureworks.public.Name
import adventureworks.sales.salesterritory.{SalesterritoryRepoImpl, SalesterritoryRowUnsaved}
import org.junit.Assert.*
import org.junit.Test

import java.util.{Optional, UUID}

class BusinessentityaddressTest {
  private val businessentityaddressRepo = new BusinessentityaddressRepoImpl
  private val businessentityRepo = new BusinessentityRepoImpl
  private val countryregionRepo = new CountryregionRepoImpl
  private val salesterritoryRepo = new SalesterritoryRepoImpl
  private val stateprovinceRepo = new StateprovinceRepoImpl
  private val addressRepo = new AddressRepoImpl
  private val addresstypeRepo = new AddresstypeRepoImpl

  @Test
  def works(): Unit = {
    WithConnection {
      val businessentityRow = businessentityRepo.insert(
        BusinessentityRowUnsaved()
      )

      val countryregion = countryregionRepo.insert(
        CountryregionRowUnsaved(countryregioncode = CountryregionId("max"), name = Name("max"))
      )

      val salesTerritory = salesterritoryRepo.insert(
        SalesterritoryRowUnsaved(name = Name("name"), countryregioncode = countryregion.countryregioncode, group = "flaff")
          .copy(salesytd = Defaulted.Provided(java.math.BigDecimal.ONE))
      )

      val stateProvidence = stateprovinceRepo.insert(
        StateprovinceRowUnsaved(
          stateprovincecode = "cde",
          countryregioncode = countryregion.countryregioncode,
          name = Name("name"),
          territoryid = salesTerritory.territoryid
        )
      )

      val address = addressRepo.insert(
        AddressRowUnsaved(
          addressline1 = "addressline1",
          city = "city",
          stateprovinceid = stateProvidence.stateprovinceid,
          postalcode = "postalcode"
        ).copy(addressline2 = Optional.of("addressline2"))
      )

      val addressType = addresstypeRepo.insert(AddresstypeRowUnsaved(name = Name("name")))

      val unsaved1 = BusinessentityaddressRowUnsaved(
        businessentityid = businessentityRow.businessentityid,
        addressid = address.addressid,
        addresstypeid = addressType.addresstypeid
      ).copy(
        rowguid = Defaulted.Provided(UUID.randomUUID()),
        modifieddate = Defaulted.Provided(DbNow.localDateTime())
      )

      val saved1 = businessentityaddressRepo.insert(unsaved1)
      val saved2 = unsaved1.toRow(saved1.rowguid, saved1.modifieddate)
      assertEquals(saved1, saved2)

      val newModifiedDate = saved1.modifieddate.minusDays(1)
      val _ = businessentityaddressRepo.update(saved1.copy(modifieddate = newModifiedDate))
      val all = businessentityaddressRepo.selectAll
      assertEquals(1, all.size)
      assertEquals(newModifiedDate, all.get(0).modifieddate)

      val _ = businessentityaddressRepo.deleteById(saved1.compositeId)
      val afterDelete = businessentityaddressRepo.selectAll
      assertTrue(afterDelete.isEmpty)
    }
  }
}
