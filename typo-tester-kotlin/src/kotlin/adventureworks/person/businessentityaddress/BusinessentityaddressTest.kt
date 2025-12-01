package adventureworks.person.businessentityaddress

import adventureworks.WithConnection
import adventureworks.customtypes.Defaulted
import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.customtypes.TypoUUID
import adventureworks.person.address.AddressRepoImpl
import adventureworks.person.address.AddressRowUnsaved
import adventureworks.person.addresstype.AddresstypeRepoImpl
import adventureworks.person.addresstype.AddresstypeRowUnsaved
import adventureworks.person.businessentity.BusinessentityRepoImpl
import adventureworks.person.businessentity.BusinessentityRowUnsaved
import adventureworks.person.countryregion.CountryregionId
import adventureworks.person.countryregion.CountryregionRepoImpl
import adventureworks.person.countryregion.CountryregionRowUnsaved
import adventureworks.person.stateprovince.StateprovinceRepoImpl
import adventureworks.person.stateprovince.StateprovinceRowUnsaved
import adventureworks.public.Name
import adventureworks.sales.salesterritory.SalesterritoryRepoImpl
import adventureworks.sales.salesterritory.SalesterritoryRowUnsaved
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.util.Optional

class BusinessentityaddressTest {
    private val businessentityaddressRepo = BusinessentityaddressRepoImpl()
    private val businessentityRepo = BusinessentityRepoImpl()
    private val countryregionRepo = CountryregionRepoImpl()
    private val salesterritoryRepo = SalesterritoryRepoImpl()
    private val stateprovinceRepo = StateprovinceRepoImpl()
    private val addressRepo = AddressRepoImpl()
    private val addresstypeRepo = AddresstypeRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // setup
            val businessentityRow = businessentityRepo.insert(
                BusinessentityRowUnsaved(
                    Defaulted.UseDefault(),
                    Defaulted.UseDefault(),
                    Defaulted.UseDefault()
                ),
                c
            )

            val countryregion = countryregionRepo.insert(
                CountryregionRowUnsaved(CountryregionId("max"), Name("max")), c
            )

            val salesTerritory = salesterritoryRepo.insert(
                SalesterritoryRowUnsaved(Name("name"), countryregion.countryregioncode, "flaff")
                    .copy(salesytd = Defaulted.Provided(BigDecimal.ONE)), c
            )

            val stateProvidence = stateprovinceRepo.insert(
                StateprovinceRowUnsaved("cde", countryregion.countryregioncode, Name("name"), salesTerritory.territoryid), c
            )

            val address = addressRepo.insert(
                AddressRowUnsaved(
                    addressline1 = "addressline1",
                    addressline2 = Optional.of("addressline2"),
                    city = "city",
                    stateprovinceid = stateProvidence.stateprovinceid,
                    postalcode = "postalcode",
                    spatiallocation = Optional.empty()
                ), c
            )

            val addressType = addresstypeRepo.insert(
                AddresstypeRowUnsaved(Name("name")), c
            )

            val unsaved1 = BusinessentityaddressRowUnsaved(
                businessentityid = businessentityRow.businessentityid,
                addressid = address.addressid,
                addresstypeid = addressType.addresstypeid,
                rowguid = Defaulted.Provided(TypoUUID.randomUUID()),
                modifieddate = Defaulted.Provided(TypoLocalDateTime.now())
            )

            // insert and round trip check
            val saved1 = businessentityaddressRepo.insert(unsaved1, c)
            // Note: In Scala test this is ??? - we use actual values from saved1
            val saved2 = unsaved1.toRow({ saved1.rowguid }, { saved1.modifieddate })
            assertEquals(saved1, saved2)

            // check field values
            val newModifiedDate = TypoLocalDateTime.apply(saved1.modifieddate.value.minusDays(1))
            val updatedOpt = businessentityaddressRepo.update(saved1.copy(modifieddate = newModifiedDate), c)
            assertTrue(updatedOpt)

            val all = businessentityaddressRepo.selectAll(c)
            assertEquals(1, all.size)
            assertEquals(newModifiedDate, all[0].modifieddate)

            // delete
            businessentityaddressRepo.deleteById(saved1.compositeId(), c)
            val afterDelete = businessentityaddressRepo.selectAll(c)
            assertTrue(afterDelete.isEmpty())
        }
    }
}
