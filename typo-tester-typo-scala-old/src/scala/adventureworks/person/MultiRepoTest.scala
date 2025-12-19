package adventureworks.person

import adventureworks.customtypes.Defaulted
import adventureworks.person.address.*
import adventureworks.person.addresstype.*
import adventureworks.person.businessentityaddress.*
import adventureworks.person.countryregion.CountryregionId
import adventureworks.person.person.*
import adventureworks.public.Name
import adventureworks.userdefined.FirstName
import adventureworks.{DomainInsertImpl, TestInsert, WithConnection}
import org.junit.Assert.*
import org.junit.Test

import java.sql.Connection
import java.util.{Optional, Random}
import scala.jdk.CollectionConverters.*

case class PersonWithAddresses(person: PersonRow, addresses: Map[Name, AddressRow])

case class AddressIdAddresstypeIdKey(addressId: AddressId, addresstypeId: AddresstypeId)

class PersonWithAddressesRepo(
    personRepo: PersonRepoImpl,
    businessentityAddressRepo: BusinessentityaddressRepoImpl,
    addresstypeRepo: AddresstypeRepoImpl,
    addressRepo: AddressRepoImpl
) {
  def syncAddresses(pa: PersonWithAddresses)(using c: Connection): List[BusinessentityaddressRow] = {
    val _ = personRepo.update(pa.person)
    pa.addresses.foreach { case (_, address) => val _ = addressRepo.update(address) }

    val addressTypeNames = pa.addresses.keys.toArray
    val oldStoredAddressTypes = addresstypeRepo.select
      .where(r => r.name.in(addressTypeNames*))
      .toList(c)
      .asScala
      .map(at => at.name -> at.addresstypeid)
      .toMap

    val currentAddressesByType = scala.collection.mutable.Map[AddresstypeId, AddressRow]()
    pa.addresses.foreach { case (addressTypeName, wanted) =>
      oldStoredAddressTypes.get(addressTypeName) match {
        case Some(addresstypeId) =>
          currentAddressesByType(addresstypeId) = wanted
        case None =>
          val inserted = addresstypeRepo.insert(AddresstypeRowUnsaved(addressTypeName))
          currentAddressesByType(inserted.addresstypeid) = wanted
      }
    }

    val oldAttachedAddresses = businessentityAddressRepo.select
      .where(x => x.businessentityid.isEqual(pa.person.businessentityid))
      .toList(c)
      .asScala
      .map(x => AddressIdAddresstypeIdKey(x.addressid, x.addresstypeid) -> x)
      .toMap

    oldAttachedAddresses.foreach { case (key, ba) =>
      currentAddressesByType.get(ba.addresstypeid) match {
        case Some(matching) if matching.addressid == ba.addressid => // keep
        case _                                                    => businessentityAddressRepo.deleteById(ba.compositeId)
      }
    }

    currentAddressesByType.map { case (addresstypeId, address) =>
      val key = AddressIdAddresstypeIdKey(address.addressid, addresstypeId)
      oldAttachedAddresses.get(key) match {
        case Some(existing) => existing
        case None =>
          val newRow = BusinessentityaddressRowUnsaved(
            pa.person.businessentityid,
            address.addressid,
            addresstypeId
          )
          businessentityAddressRepo.insert(newRow)
      }
    }.toList
  }
}

class MultiRepoTest {
  @Test
  def works(): Unit = {
    WithConnection {
      val testInsert = new TestInsert(new Random(1), new DomainInsertImpl)

      val businessentityRow = testInsert.personBusinessentity(
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault()
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
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault()
      )
      val countryregionRow = testInsert.personCountryregion(
        CountryregionId("NOR"),
        Name("Norway"),
        new Defaulted.UseDefault()
      )
      val salesterritoryRow = testInsert.salesSalesterritory(
        countryregionRow.countryregioncode,
        Name("Territory"),
        "Europe",
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault()
      )
      val stateprovinceRow = testInsert.personStateprovince(
        countryregionRow.countryregioncode,
        salesterritoryRow.territoryid,
        "OSL",
        Name("Oslo"),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault()
      )
      val addressRow1 = testInsert.personAddress(
        stateprovinceRow.stateprovinceid,
        "Street 1",
        Optional.empty(),
        "Oslo",
        "0001",
        Optional.empty(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault()
      )
      val addressRow2 = testInsert.personAddress(
        stateprovinceRow.stateprovinceid,
        "Street 2",
        Optional.empty(),
        "Oslo",
        "0002",
        Optional.empty(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault()
      )
      val addressRow3 = testInsert.personAddress(
        stateprovinceRow.stateprovinceid,
        "Street 3",
        Optional.empty(),
        "Oslo",
        "0003",
        Optional.empty(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault(),
        new Defaulted.UseDefault()
      )

      val businessentityaddressRepo = new BusinessentityaddressRepoImpl
      val repo = new PersonWithAddressesRepo(
        new PersonRepoImpl,
        businessentityaddressRepo,
        new AddresstypeRepoImpl,
        new AddressRepoImpl
      )

      val _ = repo.syncAddresses(
        PersonWithAddresses(
          personRow,
          Map(
            Name("HOME") -> addressRow1,
            Name("OFFICE") -> addressRow2
          )
        )
      )

      val allAddressIds = Array(addressRow1.addressid, addressRow2.addressid, addressRow3.addressid)
      def fetchBAs(): List[BusinessentityaddressRow] = {
        businessentityaddressRepo.select
          .where(p => p.addressid.in(allAddressIds*))
          .orderBy(ba => ba.addressid.asc)
          .toList(summon[Connection])
          .asScala
          .toList
      }

      val bas1 = fetchBAs()
      assertEquals(2, bas1.size)
      assertEquals(personRow.businessentityid, bas1(0).businessentityid)
      assertEquals(addressRow1.addressid, bas1(0).addressid)
      val homeId = bas1(0).addresstypeid
      assertEquals(personRow.businessentityid, bas1(1).businessentityid)
      assertEquals(addressRow2.addressid, bas1(1).addressid)
      val officeId = bas1(1).addresstypeid

      val _ = repo.syncAddresses(
        PersonWithAddresses(
          personRow,
          Map(
            Name("HOME") -> addressRow1,
            Name("OFFICE") -> addressRow2
          )
        )
      )

      val bas2 = fetchBAs()
      assertEquals(2, bas2.size)
      assertEquals(homeId, bas2(0).addresstypeid)
      assertEquals(officeId, bas2(1).addresstypeid)

      val _ = repo.syncAddresses(
        PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1))
      )

      val bas3 = fetchBAs()
      assertEquals(1, bas3.size)
      assertEquals(homeId, bas3(0).addresstypeid)

      val _ = repo.syncAddresses(
        PersonWithAddresses(
          personRow,
          Map(
            Name("HOME") -> addressRow1,
            Name("VACATION") -> addressRow3
          )
        )
      )

      val bas4 = fetchBAs()
      assertEquals(2, bas4.size)
      assertEquals(homeId, bas4(0).addresstypeid)
      assertEquals(addressRow3.addressid, bas4(1).addressid)
    }
  }
}
