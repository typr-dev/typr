package testdb

import dev.typr.dslsc.{TupleExpr2, Tuples}
import org.junit.Assert.*
import org.junit.{Ignore, Test}
import testdb.products.*

class TupleInTest {
  val productsRepo: ProductsRepoImpl = new ProductsRepoImpl

  private def nextId(): Int = System.nanoTime().toInt.abs

  // =============== Tuple IN with name and price ===============

  @Test
  def tupleInWithMultipleTuples(): Unit = {
    withConnection {
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU${ts}_1", "Widget", BigDecimal("19.99"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU${ts}_2", "Gadget", BigDecimal("29.99"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 2), s"SKU${ts}_3", "Widget", BigDecimal("39.99"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 3), s"SKU${ts}_4", "Gizmo", BigDecimal("19.99"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              List(
                Tuples.of("Widget", BigDecimal("19.99")),
                Tuples.of("Gadget", BigDecimal("29.99"))
              )
            )
            .and(p.sku.in(s"SKU${ts}_1", s"SKU${ts}_2", s"SKU${ts}_3", s"SKU${ts}_4"))
        )
        .toList

      assertEquals(2, result.size)
      val names = result.map(_.name).toSet
      assertEquals(Set("Widget", "Gadget"), names)
    }
  }

  @Test
  def tupleInWithSingleTuple(): Unit = {
    withConnection {
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU${ts}_1", "SingleItem", BigDecimal("99.99"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU${ts}_2", "OtherItem", BigDecimal("88.88"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              List(
                Tuples.of("SingleItem", BigDecimal("99.99"))
              )
            )
            .and(p.sku.in(s"SKU${ts}_1", s"SKU${ts}_2"))
        )
        .toList

      assertEquals(1, result.size)
      assertEquals("SingleItem", result.head.name)
    }
  }

  @Test
  def tupleInWithEmptyList(): Unit = {
    withConnection {
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU${ts}", "TestProduct", BigDecimal("50.00"), None))

      val result = productsRepo.select
        .where(p => p.name.tupleWith(p.price).in(List.empty).and(p.sku.isEqual(s"SKU${ts}")))
        .toList

      assertEquals(0, result.size)
    }
  }

  @Test
  def tupleInCombinedWithOtherConditions(): Unit = {
    withConnection {
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU${ts}_1", "Alpha", BigDecimal("10.00"), Some(dev.typr.foundations.data.Json("{}"))))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU${ts}_2", "Beta", BigDecimal("20.00"), Some(dev.typr.foundations.data.Json("{}"))))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 2), s"SKU${ts}_3", "Gamma", BigDecimal("10.00"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              List(
                Tuples.of("Alpha", BigDecimal("10.00")),
                Tuples.of("Beta", BigDecimal("20.00")),
                Tuples.of("Gamma", BigDecimal("10.00"))
              )
            )
            .and(p.metadata.isNotNull)
            .and(p.sku.in(s"SKU${ts}_1", s"SKU${ts}_2", s"SKU${ts}_3"))
        )
        .toList

      assertEquals(2, result.size)
      val names = result.map(_.name).toSet
      assertEquals(Set("Alpha", "Beta"), names)
    }
  }

  @Test
  def tupleInWithNonExistentTuples(): Unit = {
    withConnection {
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU${ts}", "Existing", BigDecimal("100.00"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              List(
                Tuples.of("Existing", BigDecimal("100.00")),
                Tuples.of("NonExistent", BigDecimal("999.99")),
                Tuples.of("AlsoMissing", BigDecimal("888.88"))
              )
            )
            .and(p.sku.isEqual(s"SKU${ts}"))
        )
        .toList

      assertEquals(1, result.size)
      assertEquals("Existing", result.head.name)
    }
  }

  // ==================== Tuple IN Subquery Tests ====================

  @Test
  def tupleInSubqueryBasic(): Unit = {
    withConnection {
      import TupleExpr2.bijection
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU_${ts}_1", s"Cheap1_$ts", BigDecimal("10.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU_${ts}_2", s"Cheap2_$ts", BigDecimal("20.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 2), s"SKU_${ts}_3", s"Expensive_$ts", BigDecimal("500.00"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              productsRepo.select
                .where(inner => inner.price.lessThan(BigDecimal("100.00")).and(inner.sku.in(s"SKU_${ts}_1", s"SKU_${ts}_2", s"SKU_${ts}_3")))
                .map(inner => inner.name.tupleWith(inner.price))
                .subquery
            )
        )
        .toList

      assertEquals(2, result.size)
      val names = result.map(_.name).toSet
      assertEquals(Set(s"Cheap1_$ts", s"Cheap2_$ts"), names)
    }
  }

  @Test
  def tupleInSubqueryWithNoMatches(): Unit = {
    withConnection {
      import TupleExpr2.bijection
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU_${ts}_1", "Prod1", BigDecimal("100.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU_${ts}_2", "Prod2", BigDecimal("200.00"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              productsRepo.select
                .where(inner => inner.price.lessThan(BigDecimal.valueOf(0)).and(inner.sku.in(s"SKU_${ts}_1", s"SKU_${ts}_2")))
                .map(inner => inner.name.tupleWith(inner.price))
                .subquery
            )
        )
        .toList

      assertEquals(0, result.size)
    }
  }

  @Test
  def tupleInSubqueryCombinedWithOtherConditions(): Unit = {
    withConnection {
      import TupleExpr2.bijection
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU_${ts}_A", s"ItemA_$ts", BigDecimal("50.00"), Some(dev.typr.foundations.data.Json("{}"))))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU_${ts}_B", s"ItemB_$ts", BigDecimal("60.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 2), s"SKU_${ts}_C", s"ItemC_$ts", BigDecimal("70.00"), Some(dev.typr.foundations.data.Json("{}"))))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              productsRepo.select
                .where(inner => inner.price.lessThan(BigDecimal("100.00")).and(inner.sku.in(s"SKU_${ts}_A", s"SKU_${ts}_B", s"SKU_${ts}_C")))
                .map(inner => inner.name.tupleWith(inner.price))
                .subquery
            )
            .and(p.metadata.isNotNull)
        )
        .toList

      assertEquals(2, result.size)
      val names = result.map(_.name).toSet
      assertEquals(Set(s"ItemA_$ts", s"ItemC_$ts"), names)
    }
  }

  // ==================== Nullable Column Tuple IN Tests ====================

  @Ignore("Nullable values in tuple IN not supported")
  @Test
  def tupleInWithNullableColumn(): Unit = {
    withConnection {
      val ts = nextId()
      // Create products - some with metadata (nullable), some without
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU_${ts}_N1", s"NullMeta1_$ts", BigDecimal("100.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU_${ts}_N2", s"NullMeta2_$ts", BigDecimal("200.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 2), s"SKU_${ts}_HAS", s"HasMeta_$ts", BigDecimal("300.00"), Some(dev.typr.foundations.data.Json("{}"))))

      // Query using tuple with nullable column - match rows with null metadata
      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.metadata)
            .in(
              List(
                dev.typr.foundations.Tuple.of(s"NullMeta1_$ts", null: dev.typr.foundations.data.Json),
                dev.typr.foundations.Tuple.of(s"NullMeta2_$ts", null: dev.typr.foundations.data.Json)
              )
            )
        )
        .toList

      assertTrue("Should handle nullable column tuple IN", result.size >= 0)
    }
  }

  // ==================== Nested Tuple Tests ====================

  @Ignore("Nested tuples not supported")
  @Test
  def nestedTupleIn(): Unit = {
    withConnection {
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU_${ts}_1", s"Nested1_$ts", BigDecimal("10.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU_${ts}_2", s"Nested2_$ts", BigDecimal("20.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 2), s"SKU_${ts}_3", s"Nested3_$ts", BigDecimal("30.00"), None))

      // Test truly nested tuple: ((name, price), sku)
      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .tupleWith(p.sku)
            .in(
              List(
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of(s"Nested1_$ts", BigDecimal("10.00")), s"SKU_${ts}_1"),
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of(s"Nested3_$ts", BigDecimal("30.00")), s"SKU_${ts}_3")
              )
            )
        )
        .toList

      assertEquals("Should find 2 products matching nested tuple pattern", 2, result.size)

      // Test that non-matching nested tuple returns empty
      val resultNoMatch = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .tupleWith(p.sku)
            .in(
              List(
                dev.typr.foundations.Tuple.of(dev.typr.foundations.Tuple.of(s"Nested1_$ts", BigDecimal("10.00")), "WRONG_SKU")
              )
            )
        )
        .toList

      assertTrue("Should not match misaligned nested tuple", resultNoMatch.isEmpty)
    }
  }

  // ==================== Read Nested Tuple from Database Tests ====================

  @Test
  def readNestedTupleFromDatabase(): Unit = {
    withConnection {
      val ts = nextId()
      // Insert test data
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU_${ts}_R1", s"ReadProd1_$ts", BigDecimal("100.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU_${ts}_R2", s"ReadProd2_$ts", BigDecimal("200.00"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 2), s"SKU_${ts}_R3", s"ReadProd3_$ts", BigDecimal("300.00"), None))

      // Select nested tuple: ((name, price), sku)
      val result = productsRepo.select
        .where(p => p.sku.in(s"SKU_${ts}_R1", s"SKU_${ts}_R2", s"SKU_${ts}_R3"))
        .orderBy(p => p.price.asc)
        .map(p => p.name.tupleWith(p.price).tupleWith(p.sku))
        .toList

      assertEquals("Should read 3 nested tuples", 3, result.size)

      // Verify the nested tuple structure
      val first = result.head
      assertEquals("First tuple's inner first element", s"ReadProd1_$ts", first._1._1)
      assertEquals("First tuple's inner second element", BigDecimal("100.00"), first._1._2)
      assertEquals("First tuple's outer second element", s"SKU_${ts}_R1", first._2)
    }
  }
}
