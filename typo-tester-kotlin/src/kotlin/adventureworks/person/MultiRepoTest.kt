package adventureworks.person

import adventureworks.DomainInsertImpl
import adventureworks.TestInsert
import adventureworks.WithConnection
import adventureworks.customtypes.Defaulted
import adventureworks.person.address.*
import adventureworks.person.addresstype.*
import adventureworks.person.businessentityaddress.*
import adventureworks.person.countryregion.CountryregionId
import adventureworks.person.person.*
import adventureworks.public.Name
import adventureworks.userdefined.FirstName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.sql.Connection
import java.util.*

/**
 * PersonWithAddresses domain object - a person with their addresses keyed by address type name.
 */
data class PersonWithAddresses(val person: PersonRow, val addresses: Map<Name, AddressRow>)

/**
 * Multi-repo that syncs PersonWithAddresses to the database.
 */
class PersonWithAddressesRepo(
    private val personRepo: PersonRepoImpl,
    private val businessentityAddressRepo: BusinessentityaddressRepoImpl,
    private val addresstypeRepo: AddresstypeRepoImpl,
    private val addressRepo: AddressRepoImpl
) {
    fun syncAddresses(pa: PersonWithAddresses, c: Connection): List<BusinessentityaddressRow> {
        personRepo.update(pa.person, c)
        pa.addresses.forEach { (_, address) -> addressRepo.update(address, c) }

        val addressTypeNames = pa.addresses.keys.toTypedArray()
        val oldStoredAddressTypes = mutableMapOf<Name, AddresstypeId>()
        addresstypeRepo.select()
            .where { r -> r.name().`in`(*addressTypeNames) }
            .toList(c)
            .forEach { at -> oldStoredAddressTypes[at.name] = at.addresstypeid }

        val currentAddressesByType = mutableMapOf<AddresstypeId, AddressRow>()
        pa.addresses.forEach { (addressTypeName, wanted) ->
            val addresstypeId = oldStoredAddressTypes[addressTypeName]
            if (addresstypeId != null) {
                currentAddressesByType[addresstypeId] = wanted
            } else {
                val inserted = addresstypeRepo.insert(AddresstypeRowUnsaved(addressTypeName), c)
                currentAddressesByType[inserted.addresstypeid] = wanted
            }
        }

        val oldAttachedAddresses = mutableMapOf<AddressIdAddresstypeIdKey, BusinessentityaddressRow>()
        businessentityAddressRepo.select()
            .where { x -> x.businessentityid().isEqual(pa.person.businessentityid) }
            .toList(c)
            .forEach { x -> oldAttachedAddresses[AddressIdAddresstypeIdKey(x.addressid, x.addresstypeid)] = x }

        oldAttachedAddresses.forEach { (_, ba) ->
            val matching = currentAddressesByType[ba.addresstypeid]
            if (matching == null || matching.addressid != ba.addressid) {
                businessentityAddressRepo.deleteById(ba.compositeId(), c)
            }
        }

        val result = mutableListOf<BusinessentityaddressRow>()
        currentAddressesByType.forEach { (addresstypeId, address) ->
            val key = AddressIdAddresstypeIdKey(address.addressid, addresstypeId)
            val existing = oldAttachedAddresses[key]
            if (existing != null) {
                result.add(existing)
            } else {
                val newRow = BusinessentityaddressRowUnsaved(pa.person.businessentityid, address.addressid, addresstypeId)
                result.add(businessentityAddressRepo.insert(newRow, c))
            }
        }
        return result
    }
}

data class AddressIdAddresstypeIdKey(val addressId: AddressId, val addresstypeId: AddresstypeId)

/**
 * Tests for multi-repo patterns.
 */
class MultiRepoTest {
    @Test
    fun works() {
        WithConnection.run { c ->
            val testInsert = TestInsert(Random(1), DomainInsertImpl())

            val businessentityRow = testInsert.personBusinessentity(
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )
            val personRow = testInsert.personPerson(
                businessentityRow.businessentityid,
                "SC",
                FirstName("name"),
                Optional.empty(),
                Optional.empty(),
                Name("lastname"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )
            val countryregionRow = testInsert.personCountryregion(
                CountryregionId("NOR"),
                Name("Norway"),
                Defaulted.UseDefault(),
                c
            )
            // salesSalesterritory(countryregioncode, group, name, ...)
            val salesterritoryRow = testInsert.salesSalesterritory(
                countryregionRow.countryregioncode,
                "Europe",
                Name("Territory"),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )
            // personStateprovince(stateprovincecode, countryregioncode, territoryid, name, ...)
            val stateprovinceRow = testInsert.personStateprovince(
                "OSL",
                countryregionRow.countryregioncode,
                salesterritoryRow.territoryid,
                Name("Oslo"),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )
            // personAddress(addressline1, city, stateprovinceid, postalcode, addressline2, spatiallocation, ...)
            val addressRow1 = testInsert.personAddress(
                "Street 1",
                "Oslo",
                stateprovinceRow.stateprovinceid,
                "0001",
                Optional.empty(),
                Optional.empty(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )
            val addressRow2 = testInsert.personAddress(
                "Street 2",
                "Oslo",
                stateprovinceRow.stateprovinceid,
                "0002",
                Optional.empty(),
                Optional.empty(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )
            val addressRow3 = testInsert.personAddress(
                "Street 3",
                "Oslo",
                stateprovinceRow.stateprovinceid,
                "0003",
                Optional.empty(),
                Optional.empty(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )

            val businessentityaddressRepo = BusinessentityaddressRepoImpl()
            val repo = PersonWithAddressesRepo(
                PersonRepoImpl(),
                businessentityaddressRepo,
                AddresstypeRepoImpl(),
                AddressRepoImpl()
            )

            repo.syncAddresses(PersonWithAddresses(personRow, mapOf(
                Name("HOME") to addressRow1,
                Name("OFFICE") to addressRow2
            )), c)

            val allAddressIds = arrayOf(addressRow1.addressid, addressRow2.addressid, addressRow3.addressid)
            val fetchBAs = {
                businessentityaddressRepo.select()
                    .where { p -> p.addressid().`in`(*allAddressIds) }
                    .orderBy { ba -> ba.addressid().asc() }
                    .toList(c)
            }

            val bas1 = fetchBAs()
            assertEquals(2, bas1.size)
            assertEquals(personRow.businessentityid, bas1[0].businessentityid)
            assertEquals(addressRow1.addressid, bas1[0].addressid)
            val homeId = bas1[0].addresstypeid
            assertEquals(personRow.businessentityid, bas1[1].businessentityid)
            assertEquals(addressRow2.addressid, bas1[1].addressid)
            val officeId = bas1[1].addresstypeid

            repo.syncAddresses(PersonWithAddresses(personRow, mapOf(
                Name("HOME") to addressRow1,
                Name("OFFICE") to addressRow2
            )), c)

            val bas2 = fetchBAs()
            assertEquals(2, bas2.size)
            assertEquals(homeId, bas2[0].addresstypeid)
            assertEquals(officeId, bas2[1].addresstypeid)

            repo.syncAddresses(PersonWithAddresses(personRow, mapOf(
                Name("HOME") to addressRow1
            )), c)

            val bas3 = fetchBAs()
            assertEquals(1, bas3.size)
            assertEquals(homeId, bas3[0].addresstypeid)

            repo.syncAddresses(PersonWithAddresses(personRow, mapOf(
                Name("HOME") to addressRow1,
                Name("VACATION") to addressRow3
            )), c)

            val bas4 = fetchBAs()
            assertEquals(2, bas4.size)
            assertEquals(homeId, bas4[0].addresstypeid)
            assertEquals(addressRow3.addressid, bas4[1].addressid)
        }
    }
}
