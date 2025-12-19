package adventureworks.production.product

import adventureworks.public.Name
import org.junit.Assert.*
import org.junit.Test
import typo.dsl.SortOrder
import typo.runtime.PgTypes
import java.math.BigDecimal

class SeekTest {
    private val productRepo = ProductRepoImpl()

    @Test
    fun uniformAscending() {
        val query = productRepo.select()
            .seek({ f -> SortOrder.asc(f.name().underlying) }, typo.dsl.SqlExpr.ConstReq(Name("foo"), Name.pgType))
            .seek({ f -> SortOrder.asc(f.weight().underlying) }, typo.dsl.SqlExpr.ConstOpt(java.util.Optional.ofNullable(BigDecimal("22.2")), PgTypes.numeric))
            .seek({ f -> SortOrder.asc(f.listprice().underlying) }, typo.dsl.SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric))

        val sql = query.sql()
        assertNotNull(sql)
        // Just verify it generates valid SQL
        assertTrue(sql!!.render().contains("ORDER BY"))
    }

    @Test
    fun uniformDescending() {
        val query = productRepo.select()
            .seek({ f -> SortOrder.desc(f.name().underlying) }, typo.dsl.SqlExpr.ConstReq(Name("foo"), Name.pgType))
            .seek({ f -> SortOrder.desc(f.weight().underlying) }, typo.dsl.SqlExpr.ConstOpt(java.util.Optional.ofNullable(BigDecimal("22.2")), PgTypes.numeric))
            .seek({ f -> SortOrder.desc(f.listprice().underlying) }, typo.dsl.SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric))

        val sql = query.sql()
        assertNotNull(sql)
        assertTrue(sql!!.render().contains("ORDER BY"))
    }

    @Test
    fun complex() {
        val query = productRepo.select()
            .seek({ f -> SortOrder.asc(f.name().underlying) }, typo.dsl.SqlExpr.ConstReq(Name("foo"), Name.pgType))
            .seek({ f -> SortOrder.desc(f.weight().underlying) }, typo.dsl.SqlExpr.ConstOpt(java.util.Optional.ofNullable(BigDecimal("22.2")), PgTypes.numeric))
            .seek({ f -> SortOrder.desc(f.listprice().underlying) }, typo.dsl.SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric))

        val sql = query.sql()
        assertNotNull(sql)
        assertTrue(sql!!.render().contains("ORDER BY"))
    }
}
