package testdb

import org.scalatest.funsuite.AnyFunSuite
import testdb.brands.*
import testdb.categories.*
import testdb.customtypes.Defaulted.Provided
import testdb.product_categories.*
import testdb.products.*

/** Tests for composite primary keys using the product_categories table. */
class CompositeIdTest extends AnyFunSuite {
  val productCategoriesRepo: ProductCategoriesRepoImpl = new ProductCategoriesRepoImpl
  val productsRepo: ProductsRepoImpl = new ProductsRepoImpl
  val categoriesRepo: CategoriesRepoImpl = new CategoriesRepoImpl
  val brandsRepo: BrandsRepoImpl = new BrandsRepoImpl

  test("insertWithCompositeId") {
    withConnection { c =>
      given java.sql.Connection = c

      val brand = brandsRepo.insert(BrandsRowUnsaved("TestBrand", "test-brand"))

      val product = productsRepo.insert(
        ProductsRowUnsaved(
          sku = "SKU001",
          name = "Test Product",
          basePrice = BigDecimal("99.99"),
          brandId = Provided(Some(brand.brandId))
        )
      )

      val category = categoriesRepo.insert(CategoriesRowUnsaved("Electronics", "electronics"))

      val productCategory = productCategoriesRepo.insert(
        ProductCategoriesRowUnsaved(product.productId, category.categoryId)
      )

      val _ = assert(productCategory.productId == product.productId)
      val _ = assert(productCategory.categoryId == category.categoryId)

      val compositeId = productCategory.compositeId
      val _ = assert(compositeId.productId == product.productId)
      assert(compositeId.categoryId == category.categoryId)
    }
  }

  test("selectByCompositeId") {
    withConnection { c =>
      given java.sql.Connection = c

      val product = productsRepo.insert(ProductsRowUnsaved("SKU002", "Product", BigDecimal("50.00")))
      val category = categoriesRepo.insert(CategoriesRowUnsaved("Category", "cat-slug"))

      productCategoriesRepo.insert(
        ProductCategoriesRowUnsaved(
          productId = product.productId,
          categoryId = category.categoryId,
          isPrimary = Provided(true),
          sortOrder = Provided(1.toShort)
        )
      )

      val compositeId = ProductCategoriesId(product.productId, category.categoryId)
      val selected = productCategoriesRepo.selectById(compositeId)

      val _ = assert(selected.isDefined)
      val _ = assert(selected.get.productId == product.productId)
      val _ = assert(selected.get.categoryId == category.categoryId)
      val _ = assert(selected.get.isPrimary)
      assert(selected.get.sortOrder == 1.toShort)
    }
  }

  test("selectByCompositeIds") {
    withConnection { c =>
      given java.sql.Connection = c

      val product = productsRepo.insert(ProductsRowUnsaved("SKU003", "Product", BigDecimal("75.00")))
      val category1 = categoriesRepo.insert(CategoriesRowUnsaved("Category1", "cat1-slug"))
      val category2 = categoriesRepo.insert(CategoriesRowUnsaved("Category2", "cat2-slug"))
      val category3 = categoriesRepo.insert(CategoriesRowUnsaved("Category3", "cat3-slug"))

      productCategoriesRepo.insert(ProductCategoriesRowUnsaved(product.productId, category1.categoryId))
      productCategoriesRepo.insert(ProductCategoriesRowUnsaved(product.productId, category2.categoryId))
      productCategoriesRepo.insert(ProductCategoriesRowUnsaved(product.productId, category3.categoryId))

      val ids = Array(
        ProductCategoriesId(product.productId, category1.categoryId),
        ProductCategoriesId(product.productId, category3.categoryId)
      )

      val selected = productCategoriesRepo.selectByIds(ids)
      assert(selected.size == 2)
    }
  }

  test("selectByIdsTracked") {
    withConnection { c =>
      given java.sql.Connection = c

      val product = productsRepo.insert(ProductsRowUnsaved("SKU004", "Product", BigDecimal("100.00")))
      val category1 = categoriesRepo.insert(CategoriesRowUnsaved("Cat1", "cat1"))
      val category2 = categoriesRepo.insert(CategoriesRowUnsaved("Cat2", "cat2"))

      productCategoriesRepo.insert(
        ProductCategoriesRowUnsaved(
          productId = product.productId,
          categoryId = category1.categoryId,
          isPrimary = Provided(true),
          sortOrder = Provided(1.toShort)
        )
      )
      productCategoriesRepo.insert(
        ProductCategoriesRowUnsaved(
          productId = product.productId,
          categoryId = category2.categoryId,
          isPrimary = Provided(false),
          sortOrder = Provided(2.toShort)
        )
      )

      val id1 = ProductCategoriesId(product.productId, category1.categoryId)
      val id2 = ProductCategoriesId(product.productId, category2.categoryId)
      val ids = Array(id1, id2)

      val tracked = productCategoriesRepo.selectByIdsTracked(ids)

      val _ = assert(tracked.size == 2)
      val _ = assert(tracked(id1).isPrimary)
      assert(!tracked(id2).isPrimary)
    }
  }

  test("updateWithCompositeId") {
    withConnection { c =>
      given java.sql.Connection = c

      val product = productsRepo.insert(ProductsRowUnsaved("SKU005", "Product", BigDecimal("25.00")))
      val category = categoriesRepo.insert(CategoriesRowUnsaved("Category", "cat-slug"))

      val inserted = productCategoriesRepo.insert(
        ProductCategoriesRowUnsaved(
          productId = product.productId,
          categoryId = category.categoryId,
          isPrimary = Provided(false),
          sortOrder = Provided(10.toShort)
        )
      )

      val updated = inserted.copy(isPrimary = true, sortOrder = 5.toShort)
      val success = productCategoriesRepo.update(updated)
      val _ = assert(success)

      val selected = productCategoriesRepo.selectById(inserted.compositeId)
      val _ = assert(selected.isDefined)
      val _ = assert(selected.get.isPrimary)
      assert(selected.get.sortOrder == 5.toShort)
    }
  }

  test("deleteByCompositeId") {
    withConnection { c =>
      given java.sql.Connection = c

      val product = productsRepo.insert(ProductsRowUnsaved("SKU006", "Product", BigDecimal("15.00")))
      val category = categoriesRepo.insert(CategoriesRowUnsaved("ToDelete", "delete-cat"))

      val inserted = productCategoriesRepo.insert(
        ProductCategoriesRowUnsaved(product.productId, category.categoryId)
      )

      val deleted = productCategoriesRepo.deleteById(inserted.compositeId)
      val _ = assert(deleted)

      val selected = productCategoriesRepo.selectById(inserted.compositeId)
      assert(selected.isEmpty)
    }
  }

  test("deleteByCompositeIds") {
    withConnection { c =>
      given java.sql.Connection = c

      val product = productsRepo.insert(ProductsRowUnsaved("SKU007", "Product", BigDecimal("200.00")))
      val category1 = categoriesRepo.insert(CategoriesRowUnsaved("Del1", "del1"))
      val category2 = categoriesRepo.insert(CategoriesRowUnsaved("Del2", "del2"))
      val category3 = categoriesRepo.insert(CategoriesRowUnsaved("Keep", "keep"))

      productCategoriesRepo.insert(ProductCategoriesRowUnsaved(product.productId, category1.categoryId))
      productCategoriesRepo.insert(ProductCategoriesRowUnsaved(product.productId, category2.categoryId))
      productCategoriesRepo.insert(ProductCategoriesRowUnsaved(product.productId, category3.categoryId))

      val idsToDelete = Array(
        ProductCategoriesId(product.productId, category1.categoryId),
        ProductCategoriesId(product.productId, category2.categoryId)
      )

      val count = productCategoriesRepo.deleteByIds(idsToDelete)
      val _ = assert(count == 2)

      val remaining = productCategoriesRepo.selectAll
      val _ = assert(remaining.size == 1)
      assert(remaining.head.categoryId == category3.categoryId)
    }
  }

  test("upsertWithCompositeId") {
    withConnection { c =>
      given java.sql.Connection = c

      val product = productsRepo.insert(ProductsRowUnsaved("SKU008", "Product", BigDecimal("300.00")))
      val category = categoriesRepo.insert(CategoriesRowUnsaved("Upsert", "upsert"))

      val row = ProductCategoriesRow(
        productId = product.productId,
        categoryId = category.categoryId,
        isPrimary = false,
        sortOrder = 1.toShort
      )
      val inserted = productCategoriesRepo.upsert(row)
      val _ = assert(!inserted.isPrimary)
      val _ = assert(inserted.sortOrder == 1.toShort)

      val updatedRow = row.copy(isPrimary = true, sortOrder = 99.toShort)
      val updated = productCategoriesRepo.upsert(updatedRow)
      val _ = assert(updated.isPrimary)
      val _ = assert(updated.sortOrder == 99.toShort)

      val all = productCategoriesRepo.selectAll
      assert(all.size == 1)
    }
  }

  test("dslWithCompositeId") {
    withConnection { c =>
      given java.sql.Connection = c

      val product1 = productsRepo.insert(ProductsRowUnsaved("SKU-DSL1", "Product1", BigDecimal("10.00")))
      val product2 = productsRepo.insert(ProductsRowUnsaved("SKU-DSL2", "Product2", BigDecimal("20.00")))
      val category = categoriesRepo.insert(CategoriesRowUnsaved("DSL-Cat", "dsl-cat"))

      productCategoriesRepo.insert(
        ProductCategoriesRowUnsaved(
          productId = product1.productId,
          categoryId = category.categoryId,
          isPrimary = Provided(true),
          sortOrder = Provided(1.toShort)
        )
      )
      productCategoriesRepo.insert(
        ProductCategoriesRowUnsaved(
          productId = product2.productId,
          categoryId = category.categoryId,
          isPrimary = Provided(false),
          sortOrder = Provided(2.toShort)
        )
      )

      val primaries = productCategoriesRepo.select
        .where(f => f.isPrimary.isEqual(true))
        .toList
      val _ = assert(primaries.size == 1)
      val _ = assert(primaries.head.productId == product1.productId)

      productCategoriesRepo.update
        .setValue(_.sortOrder, 100.toShort)
        .where(f => f.productId.isEqual(product2.productId))
        .execute

      val updated = productCategoriesRepo.selectById(ProductCategoriesId(product2.productId, category.categoryId))
      val _ = assert(updated.get.sortOrder == 100.toShort)

      productCategoriesRepo.delete
        .where(f => f.isPrimary.isEqual(false))
        .execute

      val remaining = productCategoriesRepo.selectAll
      val _ = assert(remaining.size == 1)
      assert(remaining.head.isPrimary)
    }
  }
}
