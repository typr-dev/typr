package testdb

import dev.typr.foundations.scala.Tuples
import org.scalatest.funsuite.AnyFunSuite
import testdb.products.*

class TupleInTest extends AnyFunSuite {
  val productsRepo: ProductsRepoImpl = new ProductsRepoImpl

  // =============== Tuple IN with name and basePrice ===============

  test("tupleInWithMultipleTuples") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU001", "Widget", BigDecimal("19.99")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU002", "Gadget", BigDecimal("29.99")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU003", "Widget", BigDecimal("39.99")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU004", "Gizmo", BigDecimal("19.99")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              java.util.List.of(
                Tuples.of("Widget", BigDecimal("19.99")),
                Tuples.of("Gadget", BigDecimal("29.99"))
              )
            )
        )
        .toList

      val _ = assert(result.size == 2)
      val names = result.map(_.name).toSet
      assert(names == Set("Widget", "Gadget"))
    }
  }

  test("tupleInWithSingleTuple") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU010", "SingleItem", BigDecimal("99.99")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU011", "OtherItem", BigDecimal("88.88")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              java.util.List.of(
                Tuples.of("SingleItem", BigDecimal("99.99"))
              )
            )
        )
        .toList

      val _ = assert(result.size == 1)
      assert(result.head.name == "SingleItem")
    }
  }

  test("tupleInWithEmptyList") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU020", "TestProduct", BigDecimal("50.00")))

      val result = productsRepo.select
        .where(p => p.name.tupleWith(p.basePrice).in(java.util.List.of()))
        .toList

      val _ = assert(result.size == 0)
    }
  }

  test("tupleInCombinedWithOtherConditions") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU030", "Alpha", BigDecimal("10.00"), shortDescription = testdb.customtypes.Defaulted.Provided(Some("First product"))))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU031", "Beta", BigDecimal("20.00"), shortDescription = testdb.customtypes.Defaulted.Provided(Some("Second product"))))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU032", "Gamma", BigDecimal("10.00")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              java.util.List.of(
                Tuples.of("Alpha", BigDecimal("10.00")),
                Tuples.of("Beta", BigDecimal("20.00")),
                Tuples.of("Gamma", BigDecimal("10.00"))
              )
            )
            .and(p.shortDescription.isNotNull)
        )
        .toList

      val _ = assert(result.size == 2)
      val names = result.map(_.name).toSet
      assert(names == Set("Alpha", "Beta"))
    }
  }

  test("tupleInWithNonExistentTuples") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU040", "Existing", BigDecimal("100.00")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              java.util.List.of(
                Tuples.of("Existing", BigDecimal("100.00")),
                Tuples.of("NonExistent", BigDecimal("999.99")),
                Tuples.of("AlsoMissing", BigDecimal("888.88"))
              )
            )
        )
        .toList

      val _ = assert(result.size == 1)
      assert(result.head.name == "Existing")
    }
  }

  // ==================== Tuple IN Subquery Tests ====================

  test("tupleInSubqueryBasic") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("SubqSKU1", "SubqCheap1", BigDecimal("10.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SubqSKU2", "SubqCheap2", BigDecimal("20.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SubqSKU3", "SubqExpensive", BigDecimal("500.00")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              productsRepo.select
                .where(inner => inner.basePrice.lessThan(BigDecimal("100.00")).and(inner.name.in("SubqCheap1", "SubqCheap2", "SubqExpensive")))
                .map(inner => Tuples.of(inner.name, inner.basePrice))
                .subquery
            )
        )
        .toList

      val _ = assert(result.size == 2)
      val names = result.map(_.name).toSet
      assert(names == Set("SubqCheap1", "SubqCheap2"))
    }
  }

  test("tupleInSubqueryWithNoMatches") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU050", "Prod1", BigDecimal("100.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU051", "Prod2", BigDecimal("200.00")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              productsRepo.select
                .where(inner => inner.basePrice.lessThan(BigDecimal.valueOf(0)))
                .map(inner => Tuples.of(inner.name, inner.basePrice))
                .subquery
            )
        )
        .toList

      val _ = assert(result.size == 0)
    }
  }

  test("tupleInSubqueryCombinedWithOtherConditions") {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("CombSKU1", "CombItemA", BigDecimal("50.00"), shortDescription = testdb.customtypes.Defaulted.Provided(Some("Has desc"))))
      val _ = productsRepo.insert(ProductsRowUnsaved("CombSKU2", "CombItemB", BigDecimal("60.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("CombSKU3", "CombItemC", BigDecimal("70.00"), shortDescription = testdb.customtypes.Defaulted.Provided(Some("Also has"))))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              productsRepo.select
                .where(inner => inner.basePrice.lessThan(BigDecimal("100.00")).and(inner.name.in("CombItemA", "CombItemB", "CombItemC")))
                .map(inner => Tuples.of(inner.name, inner.basePrice))
                .subquery
            )
            .and(p.shortDescription.isNotNull)
        )
        .toList

      val _ = assert(result.size == 2)
      val names = result.map(_.name).toSet
      assert(names == Set("CombItemA", "CombItemC"))
    }
  }
}
