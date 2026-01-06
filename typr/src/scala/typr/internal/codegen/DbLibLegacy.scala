package typr
package internal
package codegen

/** Shared functionality for legacy Scala DbLib implementations (Anorm, Doobie, ZioJdbc). These use the old typr.dsl package which still has CompositeIn.
  */
trait DbLibLegacy extends DbLib {
  def lang: LangScala
  def resolveConstAs(typoType: TypoType): jvm.Code

  /** Legacy DSLs need constAs expressions for generateCompositeIn */
  override def needsConstAsForCompositeIn: Boolean = true

  private val CompositeIn = jvm.Type.Qualified("typr.dsl.SqlExpr.CompositeIn")
  private val CompositeTuplePart = jvm.Type.Qualified("typr.dsl.SqlExpr.CompositeIn.TuplePart")

  /** Generate a composite IN expression using the legacy CompositeIn pattern. */
  override def generateCompositeIn(
      idsExpr: jvm.Code,
      idType: jvm.Type,
      fieldExprs: List[jvm.Code],
      fieldNames: List[jvm.Ident],
      constAsExprs: List[jvm.Code]
  ): jvm.Code = {
    val parts = fieldExprs.zip(fieldNames).zip(constAsExprs).map { case ((fieldExpr, fieldName), constAs) =>
      val getterRef = jvm.FieldGetterRef(idType, fieldName)
      code"${CompositeTuplePart.of(idType)}($fieldExpr)($getterRef)(using $constAs, implicitly)"
    }
    code"new $CompositeIn($idsExpr)(${parts.mkCode(", ")})"
  }

  override def booleanAndChain(exprs: NonEmptyList[jvm.Code]): jvm.Code =
    exprs.toList.reduceLeft[jvm.Code] { case (acc, current) => code"$acc.and($current)" }

  /** Legacy DbLibs don't support Aligned types - they're PostgreSQL-only and don't do multi-source generation. */
  override def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, typoType: TypoType, overrideDbType: Option[String]): List[jvm.ClassMember] =
    typoType match {
      case TypoType.Aligned(_, _, _, _) =>
        sys.error("Legacy DbLibs (Anorm, Doobie, ZioJdbc) do not support Aligned types. Use DbLibFoundations for multi-source generation.")
      case _ =>
        wrapperTypeInstances(wrapperType, typoType.jvmType, typoType.underlyingDbType, overrideDbType)
    }
}
