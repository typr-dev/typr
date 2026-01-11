---
title: "Patterns: The multi-repo"
---

There has been some comments about how [the generated repositories](../what-is/relations.md) do not match with peoples preferences of what a repository should be.
For instance you may prefer that your repositories coordinate multiple tables.

And that's more than fair - Often you need to coordinate multiple tables in a transaction.
The only snag is that Typo does not have the knowledge to write that code for you.

### So you write code yourself

Enter the multi-repo pattern! 

Here you take low-level Typo repositories as parameters, and you write the higher-level flow yourself. 

You still get huge benefits from using Typo in this case:

- All of this is typesafe
- You get perfect auto-complete from your IDE
- Strongly typed [Id types](../type-safety/id-types.md) and [type flow](../type-safety/type-flow.md) ensure that you have to follow foreign keys correctly  
- It's fairly readable. 
- It's testable! You can even wire in [stub repositories](../other-features/testing-with-stubs.md) and test it all without a running database.

Just have a look at the example and think how long it would take you to write this without Typo.

With Typo, this example worked *the first time it was ran*.

### Example

The example repo below exposes one method, which coordinates updates to four tables. 

The details of what is done is probably not too important, but I tried to comment it anyway.


```scala mdoc
import adventureworks.person.address.*
import adventureworks.person.addresstype.*
import adventureworks.person.businessentityaddress.*
import adventureworks.person.countryregion.CountryregionId
import adventureworks.person.person.*
import adventureworks.public.Name
import java.sql.Connection

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
    personRepo.update(pa.person)
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
```

```scala mdoc:invisible
import java.sql.{Connection, DriverManager}
implicit val c: Connection = DriverManager.getConnection("jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password")
c.setAutoCommit(false)
```

Here is example usage:

Note that we can easily create a deep dependency graph with random data due to [testInsert](../other-features/testing-with-random-values.md).
```scala mdoc:silent
import adventureworks.{TestInsert, TestDomainInsert, withConnection}
import adventureworks.userdefined.FirstName
import scala.util.Random
import adventureworks.public.*

import scala.util.Random

object DomainInsert extends TestDomainInsert {
  override def publicFlag(random: Random): Flag = Flag(random.nextBoolean())
  override def publicMydomain(random: Random): Mydomain = Mydomain(random.nextString(10))
  override def publicName(random: Random): Name = Name(random.nextString(10))
  override def publicNameStyle(random: Random): NameStyle = NameStyle(random.nextBoolean())
  override def publicShortText(random: Random): ShortText = ShortText(random.nextString(10))
}

// set a fixed seed to get consistent values
val testInsert = new TestInsert(new Random(1), DomainInsert)

val businessentityRow = testInsert.personBusinessentity()
val personRow = testInsert.personPerson(businessentityRow.businessentityid, persontype = "SC", firstname = FirstName(Name("name")))
val countryregionRow = testInsert.personCountryregion(CountryregionId("NOR"))
val salesterritoryRow = testInsert.salesSalesterritory(countryregionRow.countryregioncode)
val stateprovinceRow = testInsert.personStateprovince(countryregionRow.countryregioncode, salesterritoryRow.territoryid)
val addressRow1 = testInsert.personAddress(stateprovinceRow.stateprovinceid)
val addressRow2 = testInsert.personAddress(stateprovinceRow.stateprovinceid)
val addressRow3 = testInsert.personAddress(stateprovinceRow.stateprovinceid)

val repo = new PersonWithAddressesRepo(
personRepo = new PersonRepoImpl,
businessentityAddressRepo = new BusinessentityaddressRepoImpl,
addresstypeRepo = new AddresstypeRepoImpl,
addressRepo = new AddressRepoImpl
)
```

```scala mdoc
repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1, Name("OFFICE") -> addressRow2)))

// check that it's idempotent
repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1, Name("OFFICE") -> addressRow2)))

// remove one
repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1)))

// add one
repo.syncAddresses(PersonWithAddresses(personRow, Map(Name("HOME") -> addressRow1, Name("VACATION") -> addressRow3)))
```

```scala mdoc:invisible
c.rollback()
c.close()
```

## Isn't this a service at this point?

Maybe! You likely shouldn't use the generated `Row` types at the service level, and there should likely be a transaction boundary.
You get to decide that, however.
