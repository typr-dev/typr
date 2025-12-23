package adventureworks.person

import adventureworks.person.address.*
import adventureworks.person.addresstype.*
import adventureworks.person.businessentityaddress.*
import adventureworks.person.countryregion.CountryregionId
import adventureworks.person.person.*
import adventureworks.public.Name
import adventureworks.userdefined.FirstName
import adventureworks.{DomainInsert, TestInsert, withConnection}
import cats.syntax.applicative.*
import cats.syntax.traverse.*
import doobie.ConnectionIO
import doobie.free.connection.delay
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Random

case class PersonWithAddresses(person: PersonRow, addresses: Map[Name, AddressRow])

case class PersonWithAddressesRepo(
    personRepo: PersonRepo,
    businessentityAddressRepo: BusinessentityaddressRepo,
    addresstypeRepo: AddresstypeRepo,
    addressRepo: AddressRepo
) {

  /** A person can have a bunch of addresses registered, and they each have their own type.
    *
    * This method syncs [[PersonWithAddresses#addresses]] to postgres, so that old attached addresses are removed,
    *
    * and the given addresses are attached with the chosen type
    */
  def syncAddresses(pa: PersonWithAddresses): ConnectionIO[List[BusinessentityaddressRow]] = {
    for {
      // update person
      _ <- personRepo.update(pa.person)
      // update stored addresses
      _ <- pa.addresses.values.toList.traverse { address => addressRepo.update(address).map(_ => ()) }
      // addresses are stored in `PersonWithAddress` by a `Name` which means what type of address it is.
      // this address type is stored in addresstypeRepo.
      // In order for foreign keys to align, we need to translate from names to ids, and create rows as necessary
      oldStoredAddressTypes <- addresstypeRepo.select.where(r => r.name in pa.addresses.keys.toArray).toList
      currentAddressesWithAddresstype <- pa.addresses.toList.traverse { case (addressTypeName, wanted) =>
        oldStoredAddressTypes.find(_.name == addressTypeName) match {
          case Some(found) => (found.addresstypeid, wanted).pure[ConnectionIO]
          case None        => addresstypeRepo.insert(AddresstypeRowUnsaved(name = addressTypeName)).map(row => (row.addresstypeid, wanted))
        }
      }
      currentAddressesByAddresstype = currentAddressesWithAddresstype.toMap
      // discover existing addresses attached to person
      oldAttachedAddresses <- businessentityAddressRepo.select.where(x => x.businessentityid === pa.person.businessentityid).toList
      // unattach old attached rows
      _ <- oldAttachedAddresses.traverse { ba =>
        currentAddressesByAddresstype.get(ba.addresstypeid) match {
          case Some(address) if address.addressid == ba.addressid => false.pure[ConnectionIO]
          case _                                                  => businessentityAddressRepo.deleteById(ba.compositeId)
        }
      }
      currentAttachedAddresses <- currentAddressesWithAddresstype.traverse { case (addresstypeId, address) =>
        oldAttachedAddresses.find(x => x.addressid == address.addressid && x.addresstypeid == addresstypeId) match {
          case Some(bea) => bea.pure[ConnectionIO]
          case None =>
            businessentityAddressRepo.insert(
              BusinessentityaddressRowUnsaved(pa.person.businessentityid, address.addressid, addresstypeId)
            )
        }
      }
    } yield currentAttachedAddresses
  }
}

class PersonWithAddressesTest extends AnyFunSuite with TypeCheckedTripleEquals {
  test("works") {
    withConnection {
      val testInsert = new TestInsert(new Random(1), DomainInsert)
      for {
        businessentityRow <- testInsert.personBusinessentity()
        personRow <- testInsert.personPerson(businessentityRow.businessentityid, persontype = "SC", FirstName("name"))
        countryregionRow <- testInsert.personCountryregion(CountryregionId("NOR"))
        salesterritoryRow <- testInsert.salesSalesterritory(countryregionRow.countryregioncode)
        stateprovinceRow <- testInsert.personStateprovince(countryregionRow.countryregioncode, salesterritoryRow.territoryid)
        addressRow1 <- testInsert.personAddress(stateprovinceRow.stateprovinceid)
        addressRow2 <- testInsert.personAddress(stateprovinceRow.stateprovinceid)
        addressRow3 <- testInsert.personAddress(stateprovinceRow.stateprovinceid)

        businessentityaddressRepo = new BusinessentityaddressRepoImpl
        repo = PersonWithAddressesRepo(
          personRepo = new PersonRepoImpl,
          businessentityAddressRepo = businessentityaddressRepo,
          addresstypeRepo = new AddresstypeRepoImpl,
          addressRepo = new AddressRepoImpl
        )

        _ <- repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1, Name("OFFICE") -> addressRow2)))

        fetchBAs = businessentityaddressRepo.select.where(p => p.addressid in Array(addressRow1.addressid, addressRow2.addressid, addressRow3.addressid)).orderBy(_.addressid.asc).toList

        bas1 <- fetchBAs
        _ <- delay {
          val List(
            BusinessentityaddressRow(personRow.businessentityid, addressRow1.addressid, _, _, _),
            BusinessentityaddressRow(personRow.businessentityid, addressRow2.addressid, _, _, _)
          ) = bas1: @unchecked
        }

        // check that it's idempotent
        _ <- repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1, Name("OFFICE") -> addressRow2)))
        bas2 <- fetchBAs
        _ <- delay(assert(bas2.size == 2))

        // remove one
        _ <- repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1)))
        bas3 <- fetchBAs
        _ <- delay(assert(bas3.size == 1))

        // add one
        _ <- repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1, Name("VACATION") -> addressRow3)))
        bas4 <- fetchBAs
        _ <- delay(assert(bas4.size == 2))
      } yield ()
    }
  }
}
