package oracledb.customers

import oracledb.{AddressT, CoordinatesT, MoneyT}
import oracledb.customtypes.Defaulted
import oracledb.withConnection
import org.scalatest.funsuite.AnyFunSuite

class CustomersTest extends AnyFunSuite {
  val repo: CustomersRepoImpl = new CustomersRepoImpl

  test("insert customer with address and money") {
    withConnection { c =>
      given java.sql.Connection = c
      val coords = new CoordinatesT(BigDecimal("40.7128"), BigDecimal("-74.0061"))
      val address = new AddressT("123 Main St", "New York", coords)
      val creditLimit = new MoneyT(BigDecimal("10000.01"), "USD")

      val unsaved = new CustomersRowUnsaved(
        "John Doe",
        address,
        Some(creditLimit),
        Defaulted.UseDefault[CustomersId](),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).get
      val _ = assert(inserted.name == "John Doe")
      val _ = assert(inserted.billingAddress == address)
      val _ = assert(inserted.creditLimit.isDefined)
      val _ = assert(inserted.creditLimit.get == creditLimit)
    }
  }

  test("nested object type roundtrip") {
    withConnection { c =>
      given java.sql.Connection = c
      val coords = new CoordinatesT(BigDecimal("37.7749"), BigDecimal("-122.4194"))
      val address = new AddressT("456 Market St", "San Francisco", coords)

      val unsaved = new CustomersRowUnsaved(
        "Jane Smith",
        address,
        None,
        Defaulted.UseDefault[CustomersId](),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).get

      val _ = assert(inserted.billingAddress.street == "456 Market St")
      val _ = assert(inserted.billingAddress.city == "San Francisco")
      val _ = assert(inserted.billingAddress.location.latitude == BigDecimal("37.7749"))
      val _ = assert(inserted.billingAddress.location.longitude == BigDecimal("-122.4194"))

      val found = repo.selectById(insertedId)
      val _ = assert(found.isDefined)
      val _ = assert(inserted == found.get)
    }
  }

  test("update address") {
    withConnection { c =>
      given java.sql.Connection = c
      val originalCoords = new CoordinatesT(BigDecimal("40.7128"), BigDecimal("-74.0061"))
      val originalAddress = new AddressT("123 Main St", "New York", originalCoords)

      val unsaved = new CustomersRowUnsaved(
        "Update Test",
        originalAddress,
        None,
        Defaulted.UseDefault[CustomersId](),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).get

      val newCoords = new CoordinatesT(BigDecimal("34.0522"), BigDecimal("-118.2437"))
      val newAddress = new AddressT("789 Sunset Blvd", "Los Angeles", newCoords)

      val updatedRow = inserted.copy(billingAddress = newAddress)
      val wasUpdated = repo.update(updatedRow)

      val _ = assert(wasUpdated)
      val fetched = repo.selectById(insertedId).get
      val _ = assert(fetched.billingAddress.street == "789 Sunset Blvd")
      val _ = assert(fetched.billingAddress.city == "Los Angeles")
      val _ = assert(fetched.billingAddress.location.latitude == BigDecimal("34.0522"))
    }
  }

  test("update credit limit") {
    withConnection { c =>
      given java.sql.Connection = c
      val address = new AddressT(
        "123 Test St",
        "Test City",
        new CoordinatesT(BigDecimal(0), BigDecimal(0))
      )
      val originalLimit = new MoneyT(BigDecimal("5000.00"), "USD")

      val unsaved = new CustomersRowUnsaved(
        "Credit Limit Test",
        address,
        Some(originalLimit),
        Defaulted.UseDefault[CustomersId](),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).get

      val newLimit = new MoneyT(BigDecimal("15000.01"), "EUR")
      val updatedRow = inserted.copy(creditLimit = Some(newLimit))
      val wasUpdated = repo.update(updatedRow)

      val _ = assert(wasUpdated)
      val fetched = repo.selectById(insertedId).get
      val _ = assert(fetched.creditLimit.isDefined)
      val _ = assert(fetched.creditLimit.get.amount == BigDecimal("15000.01"))
      val _ = assert(fetched.creditLimit.get.currency == "EUR")
    }
  }

  test("delete customer") {
    withConnection { c =>
      given java.sql.Connection = c
      val address = new AddressT(
        "Delete St",
        "Delete City",
        new CoordinatesT(BigDecimal(1), BigDecimal(1))
      )

      val unsaved = new CustomersRowUnsaved(
        "To Delete",
        address,
        None,
        Defaulted.UseDefault[CustomersId](),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )

      val insertedId = repo.insert(unsaved)

      val deleted = repo.deleteById(insertedId)
      val _ = assert(deleted)

      val found = repo.selectById(insertedId)
      val _ = assert(found.isEmpty)
    }
  }

  test("select all") {
    withConnection { c =>
      given java.sql.Connection = c
      val address1 = new AddressT(
        "Address 1",
        "City 1",
        new CoordinatesT(BigDecimal("10.0"), BigDecimal("20.0"))
      )
      val address2 = new AddressT(
        "Address 2",
        "City 2",
        new CoordinatesT(BigDecimal("30.0"), BigDecimal("40.0"))
      )

      val unsaved1 = new CustomersRowUnsaved(
        "Customer 1",
        address1,
        None,
        Defaulted.UseDefault[CustomersId](),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )
      val unsaved2 = new CustomersRowUnsaved(
        "Customer 2",
        address2,
        Some(new MoneyT(BigDecimal("1000"), "USD")),
        Defaulted.UseDefault[CustomersId](),
        Defaulted.UseDefault[java.time.LocalDateTime]()
      )

      val _ = repo.insert(unsaved1)
      val _ = repo.insert(unsaved2)

      val all = repo.selectAll
      val _ = assert(all.size >= 2)
    }
  }

  test("object type equality") {
    val coords1 = new CoordinatesT(BigDecimal("40.7128"), BigDecimal("-74.0061"))
    val coords2 = new CoordinatesT(BigDecimal("40.7128"), BigDecimal("-74.0061"))
    val _ = assert(coords1 == coords2)

    val address1 = new AddressT("123 Main St", "New York", coords1)
    val address2 = new AddressT("123 Main St", "New York", coords2)
    val _ = assert(address1 == address2)

    val money1 = new MoneyT(BigDecimal("100.00"), "USD")
    val money2 = new MoneyT(BigDecimal("100.00"), "USD")
    val _ = assert(money1 == money2)
  }

  // Commented out - with* methods are not generated for Oracle types
  // test("object type with methods") {
  //   val coords = new CoordinatesT(BigDecimal("40.7128"), BigDecimal("-74.0061"))
  //   val modified = coords.withLatitude(BigDecimal("50.0"))
  //   val _ = assert(modified.latitude == BigDecimal("50.0"))
  //   val _ = assert(modified.longitude == BigDecimal("-74.0061"))
  //
  //   val address = new AddressT("123 Main St", "New York", coords)
  //   val modifiedAddress = address.withCity("Boston")
  //   val _ = assert(modifiedAddress.street == "123 Main St")
  //   val _ = assert(modifiedAddress.city == "Boston")
  //
  //   val money = new MoneyT(BigDecimal("100.00"), "USD")
  //   val modifiedMoney = money.withCurrency("EUR")
  //   val _ = assert(modifiedMoney.amount == BigDecimal("100.00"))
  //   val _ = assert(modifiedMoney.currency == "EUR")
  // }
}
