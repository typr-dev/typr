package adventureworks.production.product

import adventureworks.SnapshotTest
import adventureworks.public.Name
import org.junit.Test
import dev.typr.foundations.scala.{ScalaDbTypes, SqlExpr}

class SeekTest extends SnapshotTest {
  private val productRepo = ProductRepoImpl()

  @Test
  def uniformAscending(): Unit = {
    val query = productRepo.select
      .seek(f => f.name.asc, SqlExpr.ConstReq(Name("foo"), Name.pgType))
      .seek(f => f.weight.asc, SqlExpr.ConstOpt(Some(BigDecimal("22.2")), ScalaDbTypes.PgTypes.numeric))
      .seek(f => f.listprice.asc, SqlExpr.ConstReq(BigDecimal("33.3"), ScalaDbTypes.PgTypes.numeric))
    compareFragment("uniform-ascending", query.sql())
  }

  @Test
  def uniformDescending(): Unit = {
    val query = productRepo.select
      .seek(f => f.name.desc, SqlExpr.ConstReq(Name("foo"), Name.pgType))
      .seek(f => f.weight.desc, SqlExpr.ConstOpt(Some(BigDecimal("22.2")), ScalaDbTypes.PgTypes.numeric))
      .seek(f => f.listprice.desc, SqlExpr.ConstReq(BigDecimal("33.3"), ScalaDbTypes.PgTypes.numeric))
    compareFragment("uniform-descending", query.sql())
  }

  @Test
  def complex(): Unit = {
    val query = productRepo.select
      .seek(f => f.name.asc, SqlExpr.ConstReq(Name("foo"), Name.pgType))
      .seek(f => f.weight.desc, SqlExpr.ConstOpt(Some(BigDecimal("22.2")), ScalaDbTypes.PgTypes.numeric))
      .seek(f => f.listprice.desc, SqlExpr.ConstReq(BigDecimal("33.3"), ScalaDbTypes.PgTypes.numeric))
    compareFragment("complex", query.sql())
  }
}
