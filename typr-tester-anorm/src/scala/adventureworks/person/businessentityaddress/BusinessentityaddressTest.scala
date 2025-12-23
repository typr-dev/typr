package adventureworks.person.businessentityaddress

import adventureworks.customtypes.{Defaulted, TypoLocalDateTime, TypoUUID}
import adventureworks.person.address.{AddressRepoImpl, AddressRowUnsaved}
import adventureworks.person.addresstype.{AddresstypeRepoImpl, AddresstypeRowUnsaved}
import adventureworks.person.businessentity.{BusinessentityRepoImpl, BusinessentityRow, BusinessentityRowUnsaved}
import adventureworks.person.countryregion.{CountryregionId, CountryregionRepoImpl, CountryregionRowUnsaved}
import adventureworks.person.stateprovince.{StateprovinceRepoImpl, StateprovinceRowUnsaved}
import adventureworks.public.Name
import adventureworks.sales.salesterritory.{SalesterritoryRepoImpl, SalesterritoryRowUnsaved}
import adventureworks.withConnection
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

import scala.annotation.nowarn

class BusinessentityaddressTest extends AnyFunSuite with TypeCheckedTripleEquals {
  val businessentityaddressRepo = new BusinessentityaddressRepoImpl
  val businessentityRepo = new BusinessentityRepoImpl
  val countryregionRepo = new CountryregionRepoImpl
  val salesterritoryRepo = new SalesterritoryRepoImpl
  val stateprovinceRepo = new StateprovinceRepoImpl
  val addressRepo = new AddressRepoImpl
  val addresstypeRepo = new AddresstypeRepoImpl

  test("works") {
    withConnection { implicit c =>
      // setup
      val businessentityRow: BusinessentityRow =
        businessentityRepo.insert(
          BusinessentityRowUnsaved()
        )

      val countryregion =
        countryregionRepo.insert(
          CountryregionRowUnsaved(
            countryregioncode = CountryregionId("max"),
            name = Name("max")
          )
        )
      val salesTerritory = salesterritoryRepo.insert(
        SalesterritoryRowUnsaved(
          name = Name("name"),
          countryregioncode = countryregion.countryregioncode,
          group = "flaff",
          salesytd = Defaulted.Provided(1)
        )
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
          addressline2 = Some("addressline2"),
          city = "city",
          stateprovinceid = stateProvidence.stateprovinceid,
          postalcode = "postalcode",
          spatiallocation = None
        )
      )
      val addressType = addresstypeRepo.insert(
        new AddresstypeRowUnsaved(
          name = Name("name")
        )
      )
      val unsaved1 = BusinessentityaddressRowUnsaved(
        businessentityid = businessentityRow.businessentityid,
        addressid = address.addressid,
        addresstypeid = addressType.addresstypeid,
        rowguid = Defaulted.Provided(TypoUUID.randomUUID),
        modifieddate = Defaulted.Provided(TypoLocalDateTime.now)
      )

      // insert and round trip check
      val saved1 = businessentityaddressRepo.insert(unsaved1)
      val saved2 = unsaved1.toRow(???, ???)
      assert(saved1 === saved2): @nowarn

      // check field values
      val newModifiedDate = TypoLocalDateTime(saved1.modifieddate.value.minusDays(1))
      val updatedOpt = businessentityaddressRepo.update(saved1.copy(modifieddate = newModifiedDate))
      assert(updatedOpt.isDefined): @nowarn
      assert(updatedOpt.get.modifieddate == newModifiedDate): @nowarn

      val List(saved3) = businessentityaddressRepo.selectAll: @unchecked
      assert(saved3.modifieddate == newModifiedDate): @nowarn

      // delete
      businessentityaddressRepo.deleteById(saved1.compositeId): @nowarn

      val List() = businessentityaddressRepo.selectAll: @unchecked

      succeed
    }
  }
}
