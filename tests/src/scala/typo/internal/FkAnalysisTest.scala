package typo.internal

import typo.{NonEmptyList, Dialect, Nullability, db, sc}
import typo.internal.analysis.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.annotation.nowarn

class FkAnalysisTest extends AnyFunSuite with Matchers {
  val dialect = Dialect.Scala3

  test("FK column mapping for issue #148 - different column names") {
    // Based on debug output: test_utdanningstilbud FK with different column names
    // FK: (organisasjonskode_tilbyder, utdanningsmulighet_kode) -> (organisasjonskode, utdanningsmulighet_kode)

    val thisFkCols = NonEmptyList(db.ColName("organisasjonskode_tilbyder"), List(db.ColName("utdanningsmulighet_kode")))
    val thisFkOtherCols = NonEmptyList(db.ColName("organisasjonskode"), List(db.ColName("utdanningsmulighet_kode")))

    val organisasjonskodeCol = createComputedColumn("organisasjonskode", "organisasjonskode")
    val utdanningsmulighetKodeCol = createComputedColumn("utdanningsmulighetKode", "utdanningsmulighet_kode")

    val utdanningsmulighetKodeThisCol = createComputedColumn("utdanningsmulighetKode", "utdanningsmulighet_kode")

    val originalOtherIdCols = NonEmptyList(organisasjonskodeCol, List(utdanningsmulighetKodeCol))
    val thisColumns = List(utdanningsmulighetKodeThisCol)

    val fk = db.ForeignKey(
      cols = thisFkCols,
      otherTable = db.RelationName(Some("public"), "test_utdanningstilbud"),
      otherCols = thisFkOtherCols,
      constraintName = db.RelationName(Some("public"), "test_sak_soknadsalternativ_organisasjonskode_tilbyder_utda_fkey")
    )

    val candidateFk = FkAnalysis.CandidateFk(
      thisFk = fk,
      otherTable = null,
      otherId = IdComputed.Composite(
        originalOtherIdCols,
        createQualifiedType("TestUtdanningstilbudId"),
        createIdent("testUtdanningstilbudId")
      )
    )

    val colsFromFk = FkAnalysis.ColsFromFk(candidateFk.otherId, thisColumns, candidateFk)

    val expr = colsFromFk.expr
    assert(expr.size == 1): @nowarn
    assert(expr.keys.head.value == "utdanningsmulighetKode"): @nowarn

    // The key fix: should map to utdanningsmulighetKode, not organisasjonskode
    val expectedExpr = s"${colsFromFk.param.name.value}.utdanningsmulighetKode"
    assert(expr.values.head.render(dialect).asString == expectedExpr)
  }

  test("FK column mapping for both columns - different column names") {
    // Test case where both columns are mapped (organisasjonskodeTilbyder, utdanningsmulighetKode)

    val thisFkCols = NonEmptyList(db.ColName("organisasjonskode_tilbyder"), List(db.ColName("utdanningsmulighet_kode")))
    val thisFkOtherCols = NonEmptyList(db.ColName("organisasjonskode"), List(db.ColName("utdanningsmulighet_kode")))

    val organisasjonskodeCol = createComputedColumn("organisasjonskode", "organisasjonskode")
    val utdanningsmulighetKodeCol = createComputedColumn("utdanningsmulighetKode", "utdanningsmulighet_kode")

    val organisasjonskodeTilbyderCol = createComputedColumn("organisasjonskodeTilbyder", "organisasjonskode_tilbyder")
    val utdanningsmulighetKodeThisCol = createComputedColumn("utdanningsmulighetKode", "utdanningsmulighet_kode")

    val originalOtherIdCols = NonEmptyList(organisasjonskodeCol, List(utdanningsmulighetKodeCol))
    val thisColumns = List(organisasjonskodeTilbyderCol, utdanningsmulighetKodeThisCol)

    val fk = db.ForeignKey(
      cols = thisFkCols,
      otherTable = db.RelationName(Some("public"), "test_utdanningstilbud"),
      otherCols = thisFkOtherCols,
      constraintName = db.RelationName(Some("public"), "test_sak_soknadsalternativ_organisasjonskode_tilbyder_utda_fkey")
    )

    val candidateFk = FkAnalysis.CandidateFk(
      thisFk = fk,
      otherTable = null,
      otherId = IdComputed.Composite(
        originalOtherIdCols,
        createQualifiedType("TestUtdanningstilbudId"),
        createIdent("testUtdanningstilbudId")
      )
    )

    val colsFromFk = FkAnalysis.ColsFromFk(candidateFk.otherId, thisColumns, candidateFk)

    val expr = colsFromFk.expr
    assert(expr.size == 2): @nowarn

    // Both columns should map correctly
    assert(expr(createIdent("organisasjonskodeTilbyder")).render(dialect).asString == s"${colsFromFk.param.name.value}.organisasjonskode"): @nowarn
    assert(expr(createIdent("utdanningsmulighetKode")).render(dialect).asString == s"${colsFromFk.param.name.value}.utdanningsmulighetKode")
  }

  test("FK column mapping for identical column names") {
    // Test case from specialofferproduct - columns have same names
    // FK: (specialofferid, productid) -> (specialofferid, productid)

    val thisFkCols = NonEmptyList(db.ColName("specialofferid"), List(db.ColName("productid")))
    val thisFkOtherCols = NonEmptyList(db.ColName("specialofferid"), List(db.ColName("productid")))

    val specialofferidCol = createComputedColumn("specialofferid", "specialofferid")
    val productidCol = createComputedColumn("productid", "productid")

    val specialofferidThisCol = createComputedColumn("specialofferid", "specialofferid")
    val productidThisCol = createComputedColumn("productid", "productid")

    val originalOtherIdCols = NonEmptyList(specialofferidCol, List(productidCol))
    val thisColumns = List(specialofferidThisCol, productidThisCol)

    val fk = db.ForeignKey(
      cols = thisFkCols,
      otherTable = db.RelationName(Some("sales"), "specialofferproduct"),
      otherCols = thisFkOtherCols,
      constraintName = db.RelationName(Some("sales"), "FK_SalesOrderDetail_SpecialOfferProduct_SpecialOfferIDProductID")
    )

    val candidateFk = FkAnalysis.CandidateFk(
      thisFk = fk,
      otherTable = null,
      otherId = IdComputed.Composite(
        originalOtherIdCols,
        createQualifiedType("SpecialofferproductId"),
        createIdent("specialofferproductId")
      )
    )

    val colsFromFk = FkAnalysis.ColsFromFk(candidateFk.otherId, thisColumns, candidateFk)

    val expr = colsFromFk.expr
    assert(expr.size == 2): @nowarn

    // Should map correctly even with identical names
    assert(expr(createIdent("specialofferid")).render(dialect).asString == s"${colsFromFk.param.name.value}.specialofferid"): @nowarn
    assert(expr(createIdent("productid")).render(dialect).asString == s"${colsFromFk.param.name.value}.productid")
  }

  test("FK column mapping with reordered columns") {
    // Test case where FK columns are in different order than PK columns

    val thisFkCols = NonEmptyList(db.ColName("col_b"), List(db.ColName("col_a")))
    val thisFkOtherCols = NonEmptyList(db.ColName("pk_b"), List(db.ColName("pk_a")))

    val pkACol = createComputedColumn("pkA", "pk_a")
    val pkBCol = createComputedColumn("pkB", "pk_b")

    val colAThisCol = createComputedColumn("colA", "col_a")
    val colBThisCol = createComputedColumn("colB", "col_b")

    // Other table has PK in (pk_a, pk_b) order
    val originalOtherIdCols = NonEmptyList(pkACol, List(pkBCol))
    // This table has FK in (col_b, col_a) order
    val thisColumns = List(colBThisCol, colAThisCol)

    val fk = db.ForeignKey(
      cols = thisFkCols,
      otherTable = db.RelationName(Some("public"), "other_table"),
      otherCols = thisFkOtherCols,
      constraintName = db.RelationName(Some("public"), "fk_reordered")
    )

    val candidateFk = FkAnalysis.CandidateFk(
      thisFk = fk,
      otherTable = null,
      otherId = IdComputed.Composite(
        originalOtherIdCols,
        createQualifiedType("OtherTableId"),
        createIdent("otherTableId")
      )
    )

    val colsFromFk = FkAnalysis.ColsFromFk(candidateFk.otherId, thisColumns, candidateFk)

    val expr = colsFromFk.expr
    assert(expr.size == 2): @nowarn

    // Should map correctly despite reordering
    assert(expr(createIdent("colB")).render(dialect).asString == s"${colsFromFk.param.name.value}.pkB"): @nowarn
    assert(expr(createIdent("colA")).render(dialect).asString == s"${colsFromFk.param.name.value}.pkA")
  }

  // Helper methods for creating test objects
  private def createComputedColumn(name: String, dbName: String): ComputedColumn = {
    ComputedColumn(
      pointsTo = List.empty,
      name = createIdent(name),
      tpe = createQualifiedType("String"),
      dbCol = createDbCol(dbName)
    )
  }

  private def createIdent(name: String): sc.Ident = {
    sc.Ident(name)
  }

  private def createQualifiedType(name: String): sc.Type.Qualified = {
    sc.Type.Qualified(sc.QIdent(List(sc.Ident(name))))
  }

  private def createDbCol(name: String): db.Col = {
    db.Col(
      parsedName = ParsedName(db.ColName(name), db.ColName(name), None, None),
      tpe = db.Type.Text,
      udtName = None,
      nullability = Nullability.NoNulls,
      columnDefault = None,
      maybeGenerated = None,
      comment = None,
      constraints = Nil,
      jsonDescription = typo.internal.DebugJson.Empty
    )
  }
}
