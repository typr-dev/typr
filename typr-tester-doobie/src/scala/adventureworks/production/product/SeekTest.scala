package adventureworks.production.product

import adventureworks.SnapshotTest
import adventureworks.public.Name

class SeekTest extends SnapshotTest {
  val productRepo = new ProductRepoImpl

  test("uniform ascending") {
    val query = productRepo.select
      .seek(_.name.asc)(Name("foo"))
      .seek(_.weight.asc)(Option(BigDecimal(22.2)))
      .seek(_.listprice.asc)(BigDecimal(33.3))
    compareFragment("uniform-ascending")(query.sql)
  }

  test("uniform descending") {
    val query = productRepo.select
      .seek(_.name.desc)(Name("foo"))
      .seek(_.weight.desc)(Option(BigDecimal(22.2)))
      .seek(_.listprice.desc)(BigDecimal(33.3))
    compareFragment("uniform-descending")(query.sql)
  }

  test("complex") {
    val query = productRepo.select
      .seek(_.name.asc)(Name("foo"))
      .seek(_.weight.desc)(Option(BigDecimal(22.2)))
      .seek(_.listprice.desc)(BigDecimal(33.3))
    compareFragment("complex")(query.sql)
  }
}
