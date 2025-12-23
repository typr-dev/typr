package adventureworks.person

import adventureworks.{DomainInsert, TestInsert, withConnection}
import adventureworks.person.address.*
import adventureworks.person.addresstype.*
import adventureworks.person.businessentityaddress.*
import adventureworks.person.countryregion.CountryregionId
import adventureworks.person.person.*
import adventureworks.public.Name
import adventureworks.userdefined.FirstName
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

import java.sql.Connection
import scala.annotation.nowarn
import scala.util.Random

case class PersonWithAddresses(person: PersonRow, addresses: Map[Name, AddressRow])

class PersonWithAddressesRepo(
    personRepo: PersonRepo,
    businessentityAddressRepo: BusinessentityaddressRepo,
    addresstypeRepo: AddresstypeRepo,
    addressRepo: AddressRepo
) {

  /* A person can have a bunch of addresses registered,
   * and they each have an address type (BILLING, HOME, etc).
   *
   * This method syncs `PersonWithAddresses#addresses` to postgres,
   * so that old attached addresses are removed,
   * and the given addresses are attached with the chosen type
   */
  def syncAddresses(pa: PersonWithAddresses)(implicit c: Connection): List[BusinessentityaddressRow] = {
    // update person
    personRepo.update(pa.person): @scala.annotation.nowarn
    // update stored addresses
    pa.addresses.toList.foreach { case (_, address) => addressRepo.update(address) }

    // addresses are stored in `PersonWithAddress` by a `Name` which means what type of address it is.
    // this address type is stored in addresstypeRepo.
    // In order for foreign keys to align, we need to translate from names to ids, and create rows as necessary
    val oldStoredAddressTypes: Map[Name, AddresstypeId] =
      addresstypeRepo.select
        .where(r => r.name in pa.addresses.keys.toArray)
        .toList
        .map(x => (x.name, x.addresstypeid))
        .toMap

    val currentAddressesByType: Map[AddresstypeId, AddressRow] =
      pa.addresses.map { case (addressTypeName, wanted) =>
        oldStoredAddressTypes.get(addressTypeName) match {
          case Some(addresstypeId) => (addresstypeId, wanted)
          case None =>
            val inserted = addresstypeRepo.insert(AddresstypeRowUnsaved(name = addressTypeName))
            (inserted.addresstypeid, wanted)
        }
      }

    // discover existing addresses attached to person
    val oldAttachedAddresses: Map[(AddressId, AddresstypeId), BusinessentityaddressRow] =
      businessentityAddressRepo.select
        .where(x => x.businessentityid === pa.person.businessentityid)
        .toList
        .map(x => ((x.addressid, x.addresstypeid), x))
        .toMap

    // unattach old attached addresses
    oldAttachedAddresses.foreach { case (_, ba) =>
      currentAddressesByType.get(ba.addresstypeid) match {
        case Some(address) if address.addressid == ba.addressid =>
        case _ =>
          businessentityAddressRepo.deleteById(ba.compositeId)
      }
    }
    // attach new addresses
    currentAddressesByType.map { case (addresstypeId, address) =>
      oldAttachedAddresses.get((address.addressid, addresstypeId)) match {
        case Some(bea) => bea
        case None =>
          val newRow = BusinessentityaddressRowUnsaved(pa.person.businessentityid, address.addressid, addresstypeId)
          businessentityAddressRepo.insert(newRow)
      }
    }.toList
  }
}

class PersonWithAddressesTest extends AnyFunSuite with TypeCheckedTripleEquals {
  test("works") {
    withConnection { implicit c =>
      // insert randomly generated rows (with a fixed seed) we base the test on
      val testInsert = new TestInsert(new Random(1), DomainInsert)
      val businessentityRow = testInsert.personBusinessentity()
      val personRow = testInsert.personPerson(businessentityRow.businessentityid, persontype = "SC", FirstName("name"))
      val countryregionRow = testInsert.personCountryregion(CountryregionId("NOR"))
      val salesterritoryRow = testInsert.salesSalesterritory(countryregionRow.countryregioncode)
      val stateprovinceRow = testInsert.personStateprovince(countryregionRow.countryregioncode, salesterritoryRow.territoryid)
      val addressRow1 = testInsert.personAddress(stateprovinceRow.stateprovinceid)
      val addressRow2 = testInsert.personAddress(stateprovinceRow.stateprovinceid)
      val addressRow3 = testInsert.personAddress(stateprovinceRow.stateprovinceid)

      val businessentityaddressRepo = new BusinessentityaddressRepoImpl
      val repo = new PersonWithAddressesRepo(
        personRepo = new PersonRepoImpl,
        businessentityAddressRepo = businessentityaddressRepo,
        addresstypeRepo = new AddresstypeRepoImpl,
        addressRepo = new AddressRepoImpl
      )

      repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1, Name("OFFICE") -> addressRow2))): @nowarn

      def fetchBAs() = businessentityaddressRepo.select.where(p => p.addressid in Array(addressRow1.addressid, addressRow2.addressid, addressRow3.addressid)).orderBy(_.addressid.asc).toList

      val List(
        BusinessentityaddressRow(personRow.businessentityid, addressRow1.addressid, homeId, _, _),
        BusinessentityaddressRow(personRow.businessentityid, addressRow2.addressid, officeId, _, _)
      ) = fetchBAs(): @unchecked

      // check that it's idempotent
      repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1, Name("OFFICE") -> addressRow2))): @nowarn

      val List(
        BusinessentityaddressRow(personRow.businessentityid, addressRow1.addressid, `homeId`, _, _),
        BusinessentityaddressRow(personRow.businessentityid, addressRow2.addressid, `officeId`, _, _)
      ) = fetchBAs(): @unchecked

      // remove one
      repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1))): @nowarn
      val List(
        BusinessentityaddressRow(personRow.businessentityid, addressRow1.addressid, `homeId`, _, _)
      ) = fetchBAs(): @unchecked

      // add one
      repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1, Name("VACATION") -> addressRow3))): @nowarn
      val List(
        BusinessentityaddressRow(personRow.businessentityid, addressRow1.addressid, `homeId`, _, _),
        BusinessentityaddressRow(personRow.businessentityid, addressRow3.addressid, _, _, _)
      ) = fetchBAs(): @unchecked
    }
  }
}
