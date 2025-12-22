package oracledb

import oracledb.customers.{CustomersRepoImpl, CustomersRowUnsaved}
import oracledb.customtypes.Defaulted
import oracledb.products.{ProductsRepoImpl, ProductsRowUnsaved}
import org.scalatest.funsuite.AnyFunSuite
import typo.dsl.Bijection

class OracleDSLTest extends AnyFunSuite {
  val customersRepo: CustomersRepoImpl = new CustomersRepoImpl
  val productsRepo: ProductsRepoImpl = new ProductsRepoImpl

  test("select with where clause") {
    withConnection { c =>
      given java.sql.Connection = c
      val price = new MoneyT(new java.math.BigDecimal("199.99"), "USD")
      val unsaved = new ProductsRowUnsaved(
        "DSL-001",
        "DSL Test Product",
        price,
        java.util.Optional.of(new TagVarrayT(Array("dsl"))),
        Defaulted.UseDefault()
      )

      val _ = productsRepo.insert(unsaved)

      val query = productsRepo.select.where(p => p.sku.isEqual("DSL-001"))

      val results = query.toList(c)
      val _ = assert(results.size() > 0)
      val _ = assert(results.get(0).sku == "DSL-001")
    }
  }

  test("order by clause") {
    withConnection { c =>
      given java.sql.Connection = c
      val price1 = new MoneyT(new java.math.BigDecimal("300.00"), "USD")
      val price2 = new MoneyT(new java.math.BigDecimal("100.00"), "USD")
      val price3 = new MoneyT(new java.math.BigDecimal("200.00"), "USD")

      val _ = productsRepo.insert(
        new ProductsRowUnsaved("ORDER-3", "Product C", price1, java.util.Optional.empty(), Defaulted.UseDefault())
      )
      val _ = productsRepo.insert(
        new ProductsRowUnsaved("ORDER-1", "Product A", price2, java.util.Optional.empty(), Defaulted.UseDefault())
      )
      val _ = productsRepo.insert(
        new ProductsRowUnsaved("ORDER-2", "Product B", price3, java.util.Optional.empty(), Defaulted.UseDefault())
      )

      val query = productsRepo.select
        .where(p =>
          p.sku
            .isEqual("ORDER-1")
            .or(p.sku.isEqual("ORDER-2"), Bijection.identity())
            .or(p.sku.isEqual("ORDER-3"), Bijection.identity())
        )
        .orderBy(p => p.sku.asc())

      val results = query.toList(c)
      val _ = assert(results.size() >= 3)
    }
  }

  test("limit clause") {
    withConnection { c =>
      given java.sql.Connection = c
      val price = new MoneyT(new java.math.BigDecimal("10.00"), "USD")

      for (i <- 1 to 5) {
        val _ = productsRepo.insert(
          new ProductsRowUnsaved(
            s"LIMIT-$i",
            s"Limit Product $i",
            price,
            java.util.Optional.empty(),
            Defaulted.UseDefault()
          )
        )
      }

      val query = productsRepo.select.where(p => p.sku.isEqual("LIMIT-1")).limit(3)

      val results = query.toList(c)
      val _ = assert(results.size() <= 3)
    }
  }

  test("count query") {
    withConnection { c =>
      given java.sql.Connection = c
      val price = new MoneyT(new java.math.BigDecimal("50.00"), "USD")

      for (i <- 1 to 7) {
        val _ = productsRepo.insert(
          new ProductsRowUnsaved(
            s"COUNT-$i",
            s"Count Product $i",
            price,
            java.util.Optional.empty(),
            Defaulted.UseDefault()
          )
        )
      }

      val query = productsRepo.select.where(p => p.sku.isEqual("COUNT-1"))

      val count = query.count(c)
      val _ = assert(count >= 1)
    }
  }

  test("map projection") {
    withConnection { c =>
      given java.sql.Connection = c
      val price = new MoneyT(new java.math.BigDecimal("99.99"), "USD")
      val _ = productsRepo.insert(
        new ProductsRowUnsaved("MAP-001", "Map Test", price, java.util.Optional.empty(), Defaulted.UseDefault())
      )

      val query = productsRepo.select.where(p => p.sku.isEqual("MAP-001")).map(p => p.name)

      val results = query.toList(c)
      val _ = assert(results.size() > 0)
      val _ = assert(results.get(0)._1() == "Map Test")
    }
  }

  test("complex where with oracle object types") {
    withConnection { c =>
      given java.sql.Connection = c
      val nycCoords = new CoordinatesT(new java.math.BigDecimal("40.7128"), new java.math.BigDecimal("-74.0061"))
      val nycAddress = new AddressT("NYC Street", "New York", nycCoords)

      val _ = customersRepo.insert(
        new CustomersRowUnsaved(
          "NYC Customer",
          nycAddress,
          java.util.Optional.empty(),
          Defaulted.UseDefault(),
          Defaulted.UseDefault()
        )
      )

      val query = customersRepo.select.where(cust => cust.name.isEqual("NYC Customer"))

      val results = query.toList(c)
      val _ = assert(results.size() > 0)
      val _ = assert(results.get(0).name == "NYC Customer")
    }
  }

  test("delete with DSL") {
    withConnection { c =>
      given java.sql.Connection = c
      val price = new MoneyT(new java.math.BigDecimal("10.00"), "USD")
      val inserted = productsRepo.insert(
        new ProductsRowUnsaved(
          "DELETE-DSL",
          "To Delete via DSL",
          price,
          java.util.Optional.empty(),
          Defaulted.UseDefault()
        )
      )

      val deleteQuery = productsRepo.delete.where(p => p.sku.isEqual("DELETE-DSL"))

      val deleted = deleteQuery.execute(c)
      val _ = assert(deleted > 0)

      val found = productsRepo.selectById(inserted)
      val _ = assert(found.isEmpty())
    }
  }
}
