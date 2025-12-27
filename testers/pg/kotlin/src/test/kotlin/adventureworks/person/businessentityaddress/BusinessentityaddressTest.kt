package adventureworks.person.businessentityaddress

import adventureworks.DomainInsert
import adventureworks.TestInsert
import adventureworks.WithConnection
import adventureworks.person.countryregion.CountryregionId
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class BusinessentityaddressTest {
    private val testInsert = TestInsert(Random(0), DomainInsert)
    private val businessentityaddressRepo = BusinessentityaddressRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // Setup all dependencies using TestInsert
            val businessentity = testInsert.personBusinessentity(c = c)
            val countryregion = testInsert.personCountryregion(
                countryregioncode = CountryregionId("max"),
                c = c
            )
            val salesTerritory = testInsert.salesSalesterritory(
                countryregioncode = countryregion.countryregioncode,
                group = "Europe",
                c = c
            )
            val stateProvince = testInsert.personStateprovince(
                stateprovincecode = "cde",
                countryregioncode = countryregion.countryregioncode,
                territoryid = salesTerritory.territoryid,
                c = c
            )
            val address = testInsert.personAddress(
                addressline1 = "123 Main St",
                city = "Oslo",
                postalcode = "0001",
                stateprovinceid = stateProvince.stateprovinceid,
                c = c
            )
            val addressType = testInsert.personAddresstype(c = c)

            // Insert businessentityaddress
            val saved1 = testInsert.personBusinessentityaddress(
                businessentityid = businessentity.businessentityid,
                addressid = address.addressid,
                addresstypeid = addressType.addresstypeid,
                c = c
            )

            // Check update works
            val newModifiedDate = saved1.modifieddate.minusDays(1)
            val updated = businessentityaddressRepo.update(saved1.copy(modifieddate = newModifiedDate), c)
            assertTrue(updated)

            val allRecords = businessentityaddressRepo.selectAll(c)
            assertEquals(1, allRecords.size)
            assertEquals(newModifiedDate, allRecords[0].modifieddate)

            // Check delete works
            businessentityaddressRepo.deleteById(saved1.compositeId(), c)
            assertTrue(businessentityaddressRepo.selectAll(c).isEmpty())
        }
    }
}
