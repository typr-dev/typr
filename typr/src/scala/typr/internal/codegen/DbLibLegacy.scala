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
    // Create TuplePart for each field
    val parts = fieldExprs.zip(fieldNames).zip(constAsExprs).map { case ((fieldExpr, fieldName), constAs) =>
      val getterRef = jvm.FieldGetterRef(idType, fieldName)
      code"${CompositeTuplePart.of(idType)}($fieldExpr)($getterRef)(using $constAs, implicitly)"
    }
    // Create CompositeIn expression
    code"new $CompositeIn($idsExpr)(${parts.mkCode(", ")})"
  }

  /** Legacy DSLs don't use the new compositeInPart pattern - they use generateCompositeIn instead. This is implemented to satisfy the DbLib trait but should not be called for legacy DSLs.
    */
  override def compositeInPart(fieldType: jvm.Type, idType: jvm.Type, rowType: jvm.Type, fieldExpr: jvm.Code, fieldName: jvm.Ident, pgType: jvm.Code): jvm.Code =
    throw new UnsupportedOperationException("Legacy DSLs should use generateCompositeIn, not compositeInPart")

  /** Legacy DSLs don't use the new compositeInConstruct pattern - they use generateCompositeIn instead. This is implemented to satisfy the DbLib trait but should not be called for legacy DSLs.
    */
  override def compositeInConstruct(partsExpr: jvm.Code, idsExpr: jvm.Code): jvm.Code =
    throw new UnsupportedOperationException("Legacy DSLs should use generateCompositeIn, not compositeInConstruct")
}
