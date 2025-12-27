package adventureworks.person

import adventureworks.DomainInsert
import adventureworks.TestInsert
import adventureworks.WithConnection
import adventureworks.person.address.*
import adventureworks.person.addresstype.*
import adventureworks.person.businessentityaddress.*
import adventureworks.person.countryregion.CountryregionId
import adventureworks.person.person.*
import adventureworks.public.Name
import adventureworks.userdefined.FirstName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Connection
import java.util.Random

data class PersonWithAddresses(val person: PersonRow, val addresses: Map<Name, AddressRow>)

class PersonWithAddressesRepo(
    private val personRepo: PersonRepo,
    private val businessentityAddressRepo: BusinessentityaddressRepo,
    private val addresstypeRepo: AddresstypeRepo,
    private val addressRepo: AddressRepo
) {

    /* A person can have a bunch of addresses registered,
     * and they each have an address type (BILLING, HOME, etc).
     *
     * This method syncs `PersonWithAddresses#addresses` to postgres,
     * so that old attached addresses are removed,
     * and the given addresses are attached with the chosen type
     */
    fun syncAddresses(pa: PersonWithAddresses, c: Connection): List<BusinessentityaddressRow> {
        // update person
        personRepo.update(pa.person, c)
        // update stored addresses
        pa.addresses.forEach { (_, address) -> addressRepo.update(address, c) }

        // addresses are stored in `PersonWithAddress` by a `Name` which means what type of address it is.
        // this address type is stored in addresstypeRepo.
        // In order for foreign keys to align, we need to translate from names to ids, and create rows as necessary
        val oldStoredAddressTypes: Map<Name, AddresstypeId> =
            addresstypeRepo.select()
                .where { r -> r.name().`in`(pa.addresses.keys.toTypedArray(), Name.pgType) }
                .toList(c)
                .associate { x -> x.name to x.addresstypeid }

        val currentAddressesByType: Map<AddresstypeId, AddressRow> =
            pa.addresses.map { (addressTypeName, wanted) ->
                val addresstypeId = oldStoredAddressTypes[addressTypeName]
                    ?: run {
                        val inserted = addresstypeRepo.insert(AddresstypeRowUnsaved(name = addressTypeName), c)
                        inserted.addresstypeid
                    }
                addresstypeId to wanted
            }.toMap()

        // discover existing addresses attached to person
        val oldAttachedAddresses: Map<Pair<AddressId, AddresstypeId>, BusinessentityaddressRow> =
            businessentityAddressRepo.select()
                .where { x -> x.businessentityid().isEqual(pa.person.businessentityid) }
                .toList(c)
                .associate { x -> (x.addressid to x.addresstypeid) to x }

        // unattach old attached addresses
        oldAttachedAddresses.forEach { (_, ba) ->
            val address = currentAddressesByType[ba.addresstypeid]
            if (address == null || address.addressid != ba.addressid) {
                businessentityAddressRepo.deleteById(ba.compositeId(), c)
            }
        }

        // attach new addresses
        return currentAddressesByType.map { (addresstypeId, address) ->
            oldAttachedAddresses[address.addressid to addresstypeId]
                ?: run {
                    val newRow = BusinessentityaddressRowUnsaved(pa.person.businessentityid, address.addressid, addresstypeId)
                    businessentityAddressRepo.insert(newRow, c)
                }
        }
    }
}

class PersonWithAddressesTest {
    private val testInsert = TestInsert(Random(0), DomainInsert)

    @Test
    fun works() {
        WithConnection.run { c ->
            val businessentityRow = testInsert.personBusinessentity(c = c)
            val personRow = testInsert.personPerson(
                businessentityid = businessentityRow.businessentityid,
                persontype = "SC",
                firstname = FirstName("name"),
                c = c
            )
            val countryregionRow = testInsert.personCountryregion(
                countryregioncode = CountryregionId("NOR"),
                c = c
            )
            val salesterritoryRow = testInsert.salesSalesterritory(
                countryregioncode = countryregionRow.countryregioncode,
                group = "Europe",
                c = c
            )
            val stateprovinceRow = testInsert.personStateprovince(
                stateprovincecode = "SC",
                countryregioncode = countryregionRow.countryregioncode,
                territoryid = salesterritoryRow.territoryid,
                c = c
            )
            val addressRow1 = testInsert.personAddress(
                addressline1 = "line1",
                city = "city",
                postalcode = "12345",
                stateprovinceid = stateprovinceRow.stateprovinceid,
                c = c
            )
            val addressRow2 = testInsert.personAddress(
                addressline1 = "line2",
                city = "city",
                postalcode = "12345",
                stateprovinceid = stateprovinceRow.stateprovinceid,
                c = c
            )
            val addressRow3 = testInsert.personAddress(
                addressline1 = "line3",
                city = "city",
                postalcode = "12345",
                stateprovinceid = stateprovinceRow.stateprovinceid,
                c = c
            )

            val businessentityaddressRepo = BusinessentityaddressRepoImpl()
            val repo = PersonWithAddressesRepo(
                personRepo = PersonRepoImpl(),
                businessentityAddressRepo = businessentityaddressRepo,
                addresstypeRepo = AddresstypeRepoImpl(),
                addressRepo = AddressRepoImpl()
            )

            repo.syncAddresses(PersonWithAddresses(personRow, mapOf(Name("HOME") to addressRow1, Name("OFFICE") to addressRow2)), c)

            fun fetchBAs() = businessentityaddressRepo.select()
                .where { p -> p.addressid().`in`(arrayOf(addressRow1.addressid, addressRow2.addressid, addressRow3.addressid), AddressId.pgType) }
                .orderBy { it.addressid().asc() }
                .toList(c)

            val firstFetch = fetchBAs()
            assertEquals(2, firstFetch.size)
            val homeId = firstFetch[0].addresstypeid
            val officeId = firstFetch[1].addresstypeid
            assertEquals(personRow.businessentityid, firstFetch[0].businessentityid)
            assertEquals(addressRow1.addressid, firstFetch[0].addressid)
            assertEquals(personRow.businessentityid, firstFetch[1].businessentityid)
            assertEquals(addressRow2.addressid, firstFetch[1].addressid)

            // check that it's idempotent
            repo.syncAddresses(PersonWithAddresses(personRow, mapOf(Name("HOME") to addressRow1, Name("OFFICE") to addressRow2)), c)

            val secondFetch = fetchBAs()
            assertEquals(2, secondFetch.size)
            assertEquals(homeId, secondFetch[0].addresstypeid)
            assertEquals(officeId, secondFetch[1].addresstypeid)

            // remove one
            repo.syncAddresses(PersonWithAddresses(personRow, mapOf(Name("HOME") to addressRow1)), c)
            val thirdFetch = fetchBAs()
            assertEquals(1, thirdFetch.size)
            assertEquals(homeId, thirdFetch[0].addresstypeid)

            // add one
            repo.syncAddresses(PersonWithAddresses(personRow, mapOf(Name("HOME") to addressRow1, Name("VACATION") to addressRow3)), c)
            val fourthFetch = fetchBAs()
            assertEquals(2, fourthFetch.size)
            assertEquals(homeId, fourthFetch[0].addresstypeid)
        }
    }
}
