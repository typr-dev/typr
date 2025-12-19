package adventureworks.person.businessentityaddress

import adventureworks.DbNow
import adventureworks.WithConnection
import adventureworks.customtypes.Defaulted
import java.time.LocalDateTime
import java.util.UUID
import adventureworks.person.address.AddressRepoImpl
import adventureworks.person.address.AddressRowUnsaved
import adventureworks.person.addresstype.AddresstypeRepoImpl
import adventureworks.person.addresstype.AddresstypeRowUnsaved
import adventureworks.person.businessentity.BusinessentityRepoImpl
import adventureworks.person.businessentity.BusinessentityRow
import adventureworks.person.businessentity.BusinessentityRowUnsaved
import adventureworks.person.countryregion.CountryregionId
import adventureworks.person.countryregion.CountryregionRepoImpl
import adventureworks.person.countryregion.CountryregionRowUnsaved
import adventureworks.person.stateprovince.StateprovinceRepoImpl
import adventureworks.person.stateprovince.StateprovinceRowUnsaved
import adventureworks.public.Name
import adventureworks.sales.salesterritory.SalesterritoryRepoImpl
import adventureworks.sales.salesterritory.SalesterritoryRowUnsaved
import org.junit.Assert.*
import org.junit.Test

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
            val businessentityRow: BusinessentityRow =
                businessentityRepo.insert(
                    BusinessentityRowUnsaved(),
                    c
                )

            val countryregion =
                countryregionRepo.insert(
                    CountryregionRowUnsaved(
                        countryregioncode = CountryregionId("max"),
                        name = Name("max")
                    ),
                    c
                )
            val salesTerritory = salesterritoryRepo.insert(
                SalesterritoryRowUnsaved(
                    name = Name("name"),
                    countryregioncode = countryregion.countryregioncode,
                    group = "flaff",
                    salesytd = Defaulted.Provided(java.math.BigDecimal.ONE)
                ),
                c
            )
            val stateProvidence = stateprovinceRepo.insert(
                StateprovinceRowUnsaved(
                    stateprovincecode = "cde",
                    countryregioncode = countryregion.countryregioncode,
                    name = Name("name"),
                    territoryid = salesTerritory.territoryid
                ),
                c
            )
            val address = addressRepo.insert(
                AddressRowUnsaved(
                    addressline1 = "addressline1",
                    addressline2 = "addressline2",
                    city = "city",
                    stateprovinceid = stateProvidence.stateprovinceid,
                    postalcode = "postalcode",
                    spatiallocation = null
                ),
                c
            )
            val addressType = addresstypeRepo.insert(
                AddresstypeRowUnsaved(
                    name = Name("name")
                ),
                c
            )
            val unsaved1 = BusinessentityaddressRowUnsaved(
                businessentityid = businessentityRow.businessentityid,
                addressid = address.addressid,
                addresstypeid = addressType.addresstypeid,
                rowguid = Defaulted.Provided(UUID.randomUUID()),
                modifieddate = Defaulted.Provided(DbNow.localDateTime())
            )

            // insert and round trip check
            val saved1 = businessentityaddressRepo.insert(unsaved1, c)
            val saved2 = unsaved1.toRow(
                rowguidDefault = { saved1.rowguid },
                modifieddateDefault = { saved1.modifieddate }
            )
            assertEquals(saved1, saved2)

            // check field values
            val newModifiedDate = saved1.modifieddate.minusDays(1)
            val updated = businessentityaddressRepo.update(saved1.copy(modifieddate = newModifiedDate), c)
            assertTrue(updated)

            val allRecords = businessentityaddressRepo.selectAll(c)
            assertEquals(1, allRecords.size)
            val saved3 = allRecords[0]
            assertEquals(newModifiedDate, saved3.modifieddate)

            // delete
            businessentityaddressRepo.deleteById(saved1.compositeId(), c)

            val emptyList = businessentityaddressRepo.selectAll(c)
            assertTrue(emptyList.isEmpty())
        }
    }
}
