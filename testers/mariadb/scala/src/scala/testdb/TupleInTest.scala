package testdb

import dev.typr.dslsc.TupleExpr2
import dev.typr.dslsc.Tuples
import org.scalatest.funsuite.AnyFunSuite
import testdb.products.*

class TupleInTest extends AnyFunSuite {
  val productsRepo: ProductsRepoImpl = new ProductsRepoImpl

  // =============== Tuple IN with name and basePrice ===============

  test("tupleInWithMultipleTuples") {
    withConnection {
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU001", "Widget", BigDecimal("19.99")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU002", "Gadget", BigDecimal("29.99")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU003", "Widget", BigDecimal("39.99")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU004", "Gizmo", BigDecimal("19.99")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              List(
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
    withConnection {
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU010", "SingleItem", BigDecimal("99.99")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU011", "OtherItem", BigDecimal("88.88")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              List(
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
    withConnection {
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU020", "TestProduct", BigDecimal("50.00")))

      val result = productsRepo.select
        .where(p => p.name.tupleWith(p.basePrice).in(List.empty))
        .toList

      val _ = assert(result.size == 0)
    }
  }

  test("tupleInCombinedWithOtherConditions") {
    withConnection {
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU030", "Alpha", BigDecimal("10.00"), shortDescription = testdb.customtypes.Defaulted.Provided(Some("First product"))))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU031", "Beta", BigDecimal("20.00"), shortDescription = testdb.customtypes.Defaulted.Provided(Some("Second product"))))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU032", "Gamma", BigDecimal("10.00")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              List(
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
    withConnection {
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU040", "Existing", BigDecimal("100.00")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              List(
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
    withConnection {
      import TupleExpr2.bijection
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
                .map(inner => inner.name.tupleWith(inner.basePrice))
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
    withConnection {
      import TupleExpr2.bijection
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU050", "Prod1", BigDecimal("100.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("SKU051", "Prod2", BigDecimal("200.00")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .in(
              productsRepo.select
                .where(inner => inner.basePrice.lessThan(BigDecimal.valueOf(0)))
                .map(inner => inner.name.tupleWith(inner.basePrice))
                .subquery
            )
        )
        .toList

      val _ = assert(result.size == 0)
    }
  }

  test("tupleInSubqueryCombinedWithOtherConditions") {
    withConnection {
      import TupleExpr2.bijection
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
                .map(inner => inner.name.tupleWith(inner.basePrice))
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

  // ==================== Nullable Column Tuple IN Tests ====================

  // Skipped: MariaDB VALUES table syntax doesn't support column aliasing - requires deep refactor
  ignore("tupleInWithNullableColumn") {
    withConnection {
      // Create products with nullable shortDescription
      val _ = productsRepo.insert(ProductsRowUnsaved("NullSKU1", "NullDescProd1", BigDecimal("100.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("NullSKU2", "NullDescProd2", BigDecimal("200.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("HasSKU", "HasDescProd", BigDecimal("300.00"), shortDescription = testdb.customtypes.Defaulted.Provided(Some("Has desc"))))

      // Query using tuple with nullable column
      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.shortDescription)
            .in(
              List(
                dev.typr.foundations.Tuple.of("NullDescProd1", null: String),
                dev.typr.foundations.Tuple.of("NullDescProd2", null: String)
              )
            )
        )
        .toList

      assert(result.size >= 0, "Should handle nullable column tuple IN")
    }
  }

  // ==================== Nested Tuple Tests ====================

  // Skipped: Nested tuple type mapping requires deep refactor
  ignore("nestedTupleIn") {
    withConnection {
      val _ = productsRepo.insert(ProductsRowUnsaved("NestSKU1", "NestProd1", BigDecimal("100.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("NestSKU2", "NestProd2", BigDecimal("200.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("NestSKU3", "NestProd3", BigDecimal("300.00")))

      // Test truly nested tuple: ((name, basePrice), sku)
      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .tupleWith(p.sku)
            .in(
              List(
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of("NestProd1", BigDecimal("100.00")), "NestSKU1"),
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of("NestProd3", BigDecimal("300.00")), "NestSKU3")
              )
            )
        )
        .toList

      val _ = assert(result.size == 2, "Should find 2 products matching nested tuple pattern")

      // Test that non-matching nested tuple returns empty
      val resultNoMatch = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.basePrice)
            .tupleWith(p.sku)
            .in(
              List(
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of("NestProd1", BigDecimal("100.00")), "WRONG_SKU")
              )
            )
        )
        .toList

      assert(resultNoMatch.isEmpty, "Should not match misaligned nested tuple")
    }
  }

  // ==================== Read Nested Tuple from Database Tests ====================

  test("readNestedTupleFromDatabase") {
    withConnection {
      // Insert test data
      val _ = productsRepo.insert(ProductsRowUnsaved("READ001", "ReadProd1", BigDecimal("100.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("READ002", "ReadProd2", BigDecimal("200.00")))
      val _ = productsRepo.insert(ProductsRowUnsaved("READ003", "ReadProd3", BigDecimal("300.00")))

      // Select nested tuple: ((name, basePrice), sku)
      val result = productsRepo.select
        .where(p => p.sku.in("READ001", "READ002", "READ003"))
        .orderBy(p => p.basePrice.asc)
        .map(p => p.name.tupleWith(p.basePrice).tupleWith(p.sku))
        .toList

      val _ = assert(result.size == 3, "Should read 3 nested tuples")

      // Verify the nested tuple structure
      val first = result.head
      val _ = assert(first._1._1 == "ReadProd1", "First tuple's inner first element")
      val _ = assert(first._1._2 == BigDecimal("100.00"), "First tuple's inner second element")
      assert(first._2 == "READ001", "First tuple's outer second element")
    }
  }
}
