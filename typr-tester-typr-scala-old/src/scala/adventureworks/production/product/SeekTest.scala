package adventureworks.production.product

import adventureworks.SnapshotTest
import adventureworks.public.Name
import org.junit.Test
import typr.dsl.SqlExpr
import typr.runtime.PgTypes

import java.math.BigDecimal
import java.util.Optional

class SeekTest extends SnapshotTest {
  private val productRepo = new ProductRepoImpl

  @Test
  def uniformAscending(): Unit = {
    val query = productRepo.select
      .seek(f => f.name.asc, new SqlExpr.ConstReq(Name("foo"), Name.pgType))
      .seek(f => f.weight.asc, new SqlExpr.ConstOpt(Optional.of(new BigDecimal("22.2")), PgTypes.numeric))
      .seek(f => f.listprice.asc, new SqlExpr.ConstReq(new BigDecimal("33.3"), PgTypes.numeric))
    compareFragment("uniform-ascending", query.sql())
  }

  @Test
  def uniformDescending(): Unit = {
    val query = productRepo.select
      .seek(f => f.name.desc, new SqlExpr.ConstReq(Name("foo"), Name.pgType))
      .seek(f => f.weight.desc, new SqlExpr.ConstOpt(Optional.of(new BigDecimal("22.2")), PgTypes.numeric))
      .seek(f => f.listprice.desc, new SqlExpr.ConstReq(new BigDecimal("33.3"), PgTypes.numeric))
    compareFragment("uniform-descending", query.sql())
  }

  @Test
  def complex(): Unit = {
    val query = productRepo.select
      .seek(f => f.name.asc, new SqlExpr.ConstReq(Name("foo"), Name.pgType))
      .seek(f => f.weight.desc, new SqlExpr.ConstOpt(Optional.of(new BigDecimal("22.2")), PgTypes.numeric))
      .seek(f => f.listprice.desc, new SqlExpr.ConstReq(new BigDecimal("33.3"), PgTypes.numeric))
    compareFragment("complex", query.sql())
  }
}
