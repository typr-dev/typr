package oracledb.products

import oracledb.{MoneyT, TagVarrayT}
import oracledb.customtypes.Defaulted
import oracledb.withConnection
import org.scalatest.funsuite.AnyFunSuite

class ProductsTest extends AnyFunSuite {
  val repo: ProductsRepoImpl = new ProductsRepoImpl

  test("insert product with varray tags") {
    withConnection { c =>
      given java.sql.Connection = c
      val price = new MoneyT(new java.math.BigDecimal("99.99"), "USD")
      val tags = new TagVarrayT(Array("electronics", "gadget", "new"))
      val uniqueSku = s"PROD-${System.currentTimeMillis()}"

      val unsaved = new ProductsRowUnsaved(
        uniqueSku,
        "Test Product",
        price,
        java.util.Optional.of(tags),
        Defaulted.UseDefault[ProductsId]()
      )

      val insertedId = repo.insert(unsaved)

      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.sku == uniqueSku)
      val _ = assert(inserted.name == "Test Product")
      val _ = assert(inserted.price == price)
      val _ = assert(inserted.tags.isPresent())
      val _ = assert(inserted.tags.get().value.sameElements(Array("electronics", "gadget", "new")))
    }
  }

  test("insert product without tags") {
    withConnection { c =>
      given java.sql.Connection = c
      val price = new MoneyT(new java.math.BigDecimal("49.99"), "EUR")

      val unsaved = new ProductsRowUnsaved(
        "PROD-002",
        "Product Without Tags",
        price,
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ProductsId]()
      )

      val insertedId = repo.insert(unsaved)

      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.sku == "PROD-002")
      val _ = assert(inserted.tags.isEmpty())
    }
  }

  test("varray roundtrip") {
    withConnection { c =>
      given java.sql.Connection = c
      val tagArray = Array("tag1", "tag2", "tag3", "tag4", "tag5")
      val tags = new TagVarrayT(tagArray)
      val price = new MoneyT(new java.math.BigDecimal("199.99"), "USD")

      val unsaved = new ProductsRowUnsaved(
        "PROD-VARRAY",
        "Varray Test Product",
        price,
        java.util.Optional.of(tags),
        Defaulted.UseDefault[ProductsId]()
      )

      val insertedId = repo.insert(unsaved)

      val found = repo.selectById(insertedId)
      val _ = assert(found.isPresent())
      val _ = assert(found.get().tags.isPresent())
      val _ = assert(found.get().tags.get().value.sameElements(tagArray))
    }
  }

  test("update tags") {
    withConnection { c =>
      given java.sql.Connection = c
      val originalTags = new TagVarrayT(Array("old", "tags"))
      val price = new MoneyT(new java.math.BigDecimal("99.99"), "USD")

      val unsaved = new ProductsRowUnsaved(
        "PROD-UPDATE",
        "Update Tags Test",
        price,
        java.util.Optional.of(originalTags),
        Defaulted.UseDefault[ProductsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()

      val newTags = new TagVarrayT(Array("new", "updated", "tags"))
      val updatedRow = inserted.copy(tags = java.util.Optional.of(newTags))

      val wasUpdated = repo.update(updatedRow)
      val _ = assert(wasUpdated)
      val fetched = repo.selectById(insertedId).orElseThrow()
      val _ = assert(fetched.tags.isPresent())
      val _ = assert(fetched.tags.get().value.sameElements(Array("new", "updated", "tags")))
    }
  }

  test("update price") {
    withConnection { c =>
      given java.sql.Connection = c
      val originalPrice = new MoneyT(new java.math.BigDecimal("100.00"), "USD")
      val unsaved = new ProductsRowUnsaved(
        "PROD-PRICE",
        "Price Update Test",
        originalPrice,
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ProductsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()

      val newPrice = new MoneyT(new java.math.BigDecimal("150.01"), "EUR")
      val updatedRow = inserted.copy(price = newPrice)

      val wasUpdated = repo.update(updatedRow)
      val _ = assert(wasUpdated)
      val fetched = repo.selectById(insertedId).orElseThrow()
      val _ = assert(fetched.price.amount == new java.math.BigDecimal("150.01"))
      val _ = assert(fetched.price.currency == "EUR")
    }
  }

  test("varray with single element") {
    withConnection { c =>
      given java.sql.Connection = c
      val tags = new TagVarrayT(Array("single"))
      val price = new MoneyT(new java.math.BigDecimal("10.00"), "USD")

      val unsaved = new ProductsRowUnsaved(
        "PROD-SINGLE",
        "Single Tag Product",
        price,
        java.util.Optional.of(tags),
        Defaulted.UseDefault[ProductsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.tags.isPresent())
      val _ = assert(inserted.tags.get().value.length == 1)
      val _ = assert(inserted.tags.get().value(0) == "single")
    }
  }

  test("varray with max size") {
    withConnection { c =>
      given java.sql.Connection = c
      val maxTags = Array(
        "tag1",
        "tag2",
        "tag3",
        "tag4",
        "tag5",
        "tag6",
        "tag7",
        "tag8",
        "tag9",
        "tag10"
      )
      val tags = new TagVarrayT(maxTags)
      val price = new MoneyT(new java.math.BigDecimal("299.99"), "USD")

      val unsaved = new ProductsRowUnsaved(
        "PROD-MAX",
        "Max Tags Product",
        price,
        java.util.Optional.of(tags),
        Defaulted.UseDefault[ProductsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.tags.isPresent())
      val _ = assert(inserted.tags.get().value.length == 10)
      val _ = assert(inserted.tags.get().value.sameElements(maxTags))
    }
  }

  test("varray equality") {
    val tags1 = new TagVarrayT(Array("a", "b", "c"))
    val tags2 = new TagVarrayT(Array("a", "b", "c"))

    val _ = assert(tags1 != tags2)
    val _ = assert(tags1.value.sameElements(tags2.value))
  }

  test("delete product") {
    withConnection { c =>
      given java.sql.Connection = c
      val price = new MoneyT(new java.math.BigDecimal("99.99"), "USD")
      val unsaved = new ProductsRowUnsaved(
        "PROD-DELETE",
        "To Delete",
        price,
        java.util.Optional.of(new TagVarrayT(Array("delete"))),
        Defaulted.UseDefault[ProductsId]()
      )

      val insertedId = repo.insert(unsaved)

      val deleted = repo.deleteById(insertedId)
      val _ = assert(deleted)

      val found = repo.selectById(insertedId)
      val _ = assert(found.isEmpty())
    }
  }

  test("select all") {
    withConnection { c =>
      given java.sql.Connection = c
      val price1 = new MoneyT(new java.math.BigDecimal("10.00"), "USD")
      val price2 = new MoneyT(new java.math.BigDecimal("20.00"), "EUR")

      val unsaved1 = new ProductsRowUnsaved(
        "PROD-ALL-1",
        "Product 1",
        price1,
        java.util.Optional.of(new TagVarrayT(Array("tag1"))),
        Defaulted.UseDefault[ProductsId]()
      )

      val unsaved2 = new ProductsRowUnsaved(
        "PROD-ALL-2",
        "Product 2",
        price2,
        java.util.Optional.empty[TagVarrayT](),
        Defaulted.UseDefault[ProductsId]()
      )

      val _ = repo.insert(unsaved1)
      val _ = repo.insert(unsaved2)

      val all = repo.selectAll
      val _ = assert(all.size() >= 2)
    }
  }

  test("clear tags") {
    withConnection { c =>
      given java.sql.Connection = c
      val originalTags = new TagVarrayT(Array("tag1", "tag2"))
      val price = new MoneyT(new java.math.BigDecimal("50.00"), "USD")

      val unsaved = new ProductsRowUnsaved(
        "PROD-CLEAR",
        "Clear Tags Test",
        price,
        java.util.Optional.of(originalTags),
        Defaulted.UseDefault[ProductsId]()
      )

      val insertedId = repo.insert(unsaved)
      val inserted = repo.selectById(insertedId).orElseThrow()
      val _ = assert(inserted.tags.isPresent())

      val cleared = inserted.copy(tags = java.util.Optional.empty[TagVarrayT]())
      val wasUpdated = repo.update(cleared)

      val _ = assert(wasUpdated)
      val _ = assert(cleared.tags.isEmpty())
    }
  }
}
