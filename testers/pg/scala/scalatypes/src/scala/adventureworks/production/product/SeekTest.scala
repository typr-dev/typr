package adventureworks.production.product

import adventureworks.SnapshotTest
import adventureworks.public.Name
import org.junit.Test
import dev.typr.dslsc.SqlExpr
import dev.typr.foundationssc.PgTypes

class SeekTest extends SnapshotTest {
  private val productRepo = ProductRepoImpl()

  @Test
  def uniformAscending(): Unit = {
    val query = productRepo.select
      .seek(f => f.name.asc, SqlExpr.ConstReq(Name("foo"), Name.pgType.underlying))
      .seek(f => f.weight.asc, SqlExpr.ConstOpt(Some(BigDecimal("22.2")), PgTypes.numeric.underlying))
      .seek(f => f.listprice.asc, SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric.underlying))
    compareFragment("uniform-ascending", query.sql())
  }

  @Test
  def uniformDescending(): Unit = {
    val query = productRepo.select
      .seek(f => f.name.desc, SqlExpr.ConstReq(Name("foo"), Name.pgType.underlying))
      .seek(f => f.weight.desc, SqlExpr.ConstOpt(Some(BigDecimal("22.2")), PgTypes.numeric.underlying))
      .seek(f => f.listprice.desc, SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric.underlying))
    compareFragment("uniform-descending", query.sql())
  }

  @Test
  def complex(): Unit = {
    val query = productRepo.select
      .seek(f => f.name.asc, SqlExpr.ConstReq(Name("foo"), Name.pgType.underlying))
      .seek(f => f.weight.desc, SqlExpr.ConstOpt(Some(BigDecimal("22.2")), PgTypes.numeric.underlying))
      .seek(f => f.listprice.desc, SqlExpr.ConstReq(BigDecimal("33.3"), PgTypes.numeric.underlying))
    compareFragment("complex", query.sql())
  }
}
