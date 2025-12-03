package testdb.simple_customer_lookup

import org.scalatest.funsuite.AnyFunSuite
import testdb.TestInsert
import testdb.withConnection

import java.util.Random

class SimpleCustomerLookupSqlRepoTest extends AnyFunSuite {
  val repo = new SimpleCustomerLookupSqlRepoImpl

  test("lookup customer by email") {
    withConnection {
      val testInsert = TestInsert(new Random(0))

      val testEmail = "test@example.com"
      val customer = testInsert.Customers(
        email = testEmail,
        passwordHash = Array[Byte](1, 2, 3)
      )

      val results = repo(testEmail)

      assert(results.size() == 1): @annotation.nowarn
      val result = results.get(0)
      assert(result.customerId == customer.customerId): @annotation.nowarn
      assert(result.email == testEmail): @annotation.nowarn
      assert(result.firstName == customer.firstName): @annotation.nowarn
      assert(result.lastName == customer.lastName)
    }
  }

  test("lookup non-existent customer returns empty list") {
    withConnection {
      val results = repo("nonexistent@example.com")
      assert(results.isEmpty)
    }
  }
}
