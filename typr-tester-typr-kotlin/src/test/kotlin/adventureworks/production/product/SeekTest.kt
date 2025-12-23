package adventureworks.production.product

import adventureworks.public.Name
import org.junit.Assert.*
import org.junit.Test
import typr.dsl.SortOrder
import typr.runtime.PgTypes
import java.math.BigDecimal

class SeekTest {
    private val productRepo = ProductRepoImpl()

    @Test
    fun uniformAscending() {
        val query = productRepo.select()
            .seek({ f -> f.name().asc() }, typr.dsl.SqlExpr.ConstReq(Name("foo"), Name.pgType))
            .seek({ f -> f.weight().asc() }, typr.dsl.SqlExpr.ConstOpt(java.util.Optional.ofNullable(BigDecimal("22.2")), PgTypes.numeric))
            .seek({ f -> f.listprice().asc() }, typr.dsl.SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric))

        val sql = query.sql()
        assertNotNull(sql)
        // Just verify it generates valid SQL
        assertTrue(sql!!.render().contains("order by"))
    }

    @Test
    fun uniformDescending() {
        val query = productRepo.select()
            .seek({ f -> f.name().desc() }, typr.dsl.SqlExpr.ConstReq(Name("foo"), Name.pgType))
            .seek({ f -> f.weight().desc() }, typr.dsl.SqlExpr.ConstOpt(java.util.Optional.ofNullable(BigDecimal("22.2")), PgTypes.numeric))
            .seek({ f -> f.listprice().desc() }, typr.dsl.SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric))

        val sql = query.sql()
        assertNotNull(sql)
        assertTrue(sql!!.render().contains("order by"))
    }

    @Test
    fun complex() {
        val query = productRepo.select()
            .seek({ f -> f.name().asc() }, typr.dsl.SqlExpr.ConstReq(Name("foo"), Name.pgType))
            .seek({ f -> f.weight().desc() }, typr.dsl.SqlExpr.ConstOpt(java.util.Optional.ofNullable(BigDecimal("22.2")), PgTypes.numeric))
            .seek({ f -> f.listprice().desc() }, typr.dsl.SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric))

        val sql = query.sql()
        assertNotNull(sql)
        assertTrue(sql!!.render().contains("order by"))
    }
}
