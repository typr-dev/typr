package testdb

import dev.typr.foundations.scala.Tuples
import org.junit.Assert.*
import org.junit.Test
import testdb.products.*

class TupleInTest {
  val productsRepo: ProductsRepoImpl = new ProductsRepoImpl

  // =============== Tuple IN with name and price ===============

  @Test
  def tupleInWithMultipleTuples(): Unit = {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("Widget", BigDecimal("19.99"), None))
      val _ = productsRepo.insert(ProductsRowUnsaved("Gadget", BigDecimal("29.99"), None))
      val _ = productsRepo.insert(ProductsRowUnsaved("Widget", BigDecimal("39.99"), None))
      val _ = productsRepo.insert(ProductsRowUnsaved("Gizmo", BigDecimal("19.99"), None))

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
      val _ = productsRepo.insert(ProductsRowUnsaved("SingleItem", BigDecimal("99.99"), None))
      val _ = productsRepo.insert(ProductsRowUnsaved("OtherItem", BigDecimal("88.88"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              java.util.List.of(
                Tuples.of("SingleItem", BigDecimal("99.99"))
              )
            )
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
      val _ = productsRepo.insert(ProductsRowUnsaved("TestProduct", BigDecimal("50.00"), None))

      val result = productsRepo.select
        .where(p => p.name.tupleWith(p.price).in(java.util.List.of()))
        .toList

      assertEquals(0, result.size)
    }
  }

  @Test
  def tupleInCombinedWithOtherConditions(): Unit = {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("Alpha", BigDecimal("10.00"), Some("First product")))
      val _ = productsRepo.insert(ProductsRowUnsaved("Beta", BigDecimal("20.00"), Some("Second product")))
      val _ = productsRepo.insert(ProductsRowUnsaved("Gamma", BigDecimal("10.00"), None))

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
            .and(p.description.isNotNull)
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
      val _ = productsRepo.insert(ProductsRowUnsaved("Existing", BigDecimal("100.00"), None))

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
      val _ = productsRepo.insert(ProductsRowUnsaved("SubqCheap1", BigDecimal("10.00"), None))
      val _ = productsRepo.insert(ProductsRowUnsaved("SubqCheap2", BigDecimal("20.00"), None))
      val _ = productsRepo.insert(ProductsRowUnsaved("SubqExpensive", BigDecimal("500.00"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              productsRepo.select
                .where(inner => inner.price.lessThan(BigDecimal("100.00")).and(inner.name.in("SubqCheap1", "SubqCheap2", "SubqExpensive")))
                .map(inner => Tuples.of(inner.name, inner.price))
                .subquery
            )
        )
        .toList

      assertEquals(2, result.size)
      val names = result.map(_.name).toSet
      assertEquals(Set("SubqCheap1", "SubqCheap2"), names)
    }
  }

  @Test
  def tupleInSubqueryWithNoMatches(): Unit = {
    withConnection { c =>
      given java.sql.Connection = c
      val _ = productsRepo.insert(ProductsRowUnsaved("Prod1", BigDecimal("100.00"), None))
      val _ = productsRepo.insert(ProductsRowUnsaved("Prod2", BigDecimal("200.00"), None))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              productsRepo.select
                .where(inner => inner.price.lessThan(BigDecimal.valueOf(0)))
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
      val _ = productsRepo.insert(ProductsRowUnsaved("CombItemA", BigDecimal("50.00"), Some("Has desc")))
      val _ = productsRepo.insert(ProductsRowUnsaved("CombItemB", BigDecimal("60.00"), None))
      val _ = productsRepo.insert(ProductsRowUnsaved("CombItemC", BigDecimal("70.00"), Some("Also has")))

      val result = productsRepo.select
        .where(p =>
          p.name
            .tupleWith(p.price)
            .in(
              productsRepo.select
                .where(inner => inner.price.lessThan(BigDecimal("100.00")).and(inner.name.in("CombItemA", "CombItemB", "CombItemC")))
                .map(inner => Tuples.of(inner.name, inner.price))
                .subquery
            )
            .and(p.description.isNotNull)
        )
        .toList

      assertEquals(2, result.size)
      val names = result.map(_.name).toSet
      assertEquals(Set("CombItemA", "CombItemC"), names)
    }
  }
}
