package testdb

import dev.typr.foundations.scala.Tuples
import org.junit.Assert.*
import org.junit.Test
import testdb.products.*

class TupleInTest {
  val productsRepo: ProductsRepoImpl = new ProductsRepoImpl

  private def nextId(): Int = System.nanoTime().toInt.abs

  // =============== Tuple IN with name and price ===============

  @Test
  def tupleInWithMultipleTuples(): Unit = {
    withConnection { c =>
      given java.sql.Connection = c
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
              java.util.List.of(
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
    withConnection { c =>
      given java.sql.Connection = c
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU${ts}_1", "SingleItem", BigDecimal("99.99"), None))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU${ts}_2", "OtherItem", BigDecimal("88.88"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              java.util.List.of(
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
    withConnection { c =>
      given java.sql.Connection = c
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU${ts}", "TestProduct", BigDecimal("50.00"), None))

      val result = productsRepo.select
        .where(p => p.name.tupleWith(p.price).in(java.util.List.of()).and(p.sku.isEqual(s"SKU${ts}")))
        .toList

      assertEquals(0, result.size)
    }
  }

  @Test
  def tupleInCombinedWithOtherConditions(): Unit = {
    withConnection { c =>
      given java.sql.Connection = c
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU${ts}_1", "Alpha", BigDecimal("10.00"), Some(dev.typr.foundations.data.Json("{}"))))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 1), s"SKU${ts}_2", "Beta", BigDecimal("20.00"), Some(dev.typr.foundations.data.Json("{}"))))
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts + 2), s"SKU${ts}_3", "Gamma", BigDecimal("10.00"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              java.util.List.of(
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
    withConnection { c =>
      given java.sql.Connection = c
      val ts = nextId()
      val _ = productsRepo.insert(ProductsRow(ProductsId(ts), s"SKU${ts}", "Existing", BigDecimal("100.00"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              java.util.List.of(
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
    withConnection { c =>
      given java.sql.Connection = c
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
                .map(inner => Tuples.of(inner.name, inner.price))
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
    withConnection { c =>
      given java.sql.Connection = c
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
                .map(inner => Tuples.of(inner.name, inner.price))
                .subquery
            )
        )
        .toList

      assertEquals(0, result.size)
    }
  }

  @Test
  def tupleInSubqueryCombinedWithOtherConditions(): Unit = {
    withConnection { c =>
      given java.sql.Connection = c
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
                .map(inner => Tuples.of(inner.name, inner.price))
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
}
