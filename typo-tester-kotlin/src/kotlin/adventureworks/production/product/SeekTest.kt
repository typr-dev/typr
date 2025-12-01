package adventureworks.production.product

import adventureworks.SnapshotTest
import adventureworks.public.Name
import org.junit.jupiter.api.Test
import typo.dsl.SqlExpr
import typo.runtime.PgTypes
import java.math.BigDecimal
import java.util.Optional

/**
 * Tests for DSL seek functionality.
 */
class SeekTest : SnapshotTest() {
    private val productRepo = ProductRepoImpl()

    @Test
    fun uniformAscending() {
        val query = productRepo.select()
            .seek({ f -> f.name().asc() }, SqlExpr.ConstReq(Name("foo"), Name.pgType))
            .seek({ f -> f.weight().asc() }, SqlExpr.ConstOpt(Optional.of(BigDecimal("22.2")), PgTypes.numeric))
            .seek({ f -> f.listprice().asc() }, SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric))
        compareFragment("uniform-ascending", query.sql())
    }

    @Test
    fun uniformDescending() {
        val query = productRepo.select()
            .seek({ f -> f.name().desc() }, SqlExpr.ConstReq(Name("foo"), Name.pgType))
            .seek({ f -> f.weight().desc() }, SqlExpr.ConstOpt(Optional.of(BigDecimal("22.2")), PgTypes.numeric))
            .seek({ f -> f.listprice().desc() }, SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric))
        compareFragment("uniform-descending", query.sql())
    }

    @Test
    fun complex() {
        val query = productRepo.select()
            .seek({ f -> f.name().asc() }, SqlExpr.ConstReq(Name("foo"), Name.pgType))
            .seek({ f -> f.weight().desc() }, SqlExpr.ConstOpt(Optional.of(BigDecimal("22.2")), PgTypes.numeric))
            .seek({ f -> f.listprice().desc() }, SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric))
        compareFragment("complex", query.sql())
    }
}
