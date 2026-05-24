package testdb

import org.junit.Assert.*
import org.junit.Test
import testdb.customers.*
import testdb.customtypes.Defaulted
import testdb.products.*

class BasicCrudTest {
  private val customers = new CustomersRepoImpl
  private val products = new ProductsRepoImpl

  @Test def selectAllCustomersFromSeedData(): Unit = withConnection {
    val rows = customers.selectAll
    assertEquals(2, rows.size)
  }

  @Test def findCustomerById(): Unit = withConnection {
    val row = customers.selectById(new CustomersId(1L))
    assertTrue(row.isDefined)
    assertEquals("John Doe", row.get.name)
  }

  @Test def deleteCustomerById(): Unit = withConnection {
    val inserted = customers.insert(
      CustomersRowUnsaved(CustomersId(999L), "Disposable", None, Defaulted.UseDefault())
    )
    assertTrue(customers.deleteById(inserted.customerId))
  }

  @Test def productsHaveExpectedPrices(): Unit = withConnection {
    val ps = products.selectAll
    assertEquals(2, ps.size)
    val skus = ps.iterator.toList.map(_.sku).sorted
    assertEquals(List("PROD-001", "PROD-002"), skus)
  }
}
