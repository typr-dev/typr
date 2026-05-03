package typr
package internal
package codegen

trait FileFields {
  def lang: Lang
  def naming: Naming
  def names: ComputedNames
  def options: InternalOptions
  def fks: List[db.ForeignKey]
  def maybeFkAnalysis: Option[FkAnalysis]
  def maybeCols: Option[NonEmptyList[ComputedColumn]]

  def generate(fieldsName: jvm.Type.Qualified, cols: NonEmptyList[ComputedColumn]): jvm.File

  protected def sqlCast: SqlCast

  protected def compositeIdMethods(dbLib: DbLib): List[jvm.Method] =
    names.maybeId
      .collect {
        case x: IdComputed.Composite if x.cols.forall(_.dbCol.nullability == Nullability.NoNulls) =>
          val predicateType = lang.dsl.SqlExpr.of(lang.Boolean)
          val idParam = jvm.Param(x.paramName, x.tpe)
          val idsParam = jvm.Param(x.paramName.appended("s"), lang.ListType.tpe.of(x.tpe))

          val isMethod = {
            val equalityExprs: NonEmptyList[jvm.Code] = x.cols.map { col =>
              val thisField = jvm.SelfNullary(col.name)
              val paramField = lang.prop(idParam.name.code, col.name)
              code"$thisField.isEqual($paramField)"
            }
            jvm.Method(
              annotations = Nil,
              comments = jvm.Comments.Empty,
              tparams = Nil,
              name = jvm.Ident("compositeIdIs"),
              params = List(idParam),
              implicitParams = Nil,
              tpe = predicateType,
              throws = Nil,
              body = jvm.Body.Expr(booleanAndChain(dbLib, equalityExprs)),
              isOverride = false,
              isDefault = false
            )
          }

          val inMethod = {
            val fieldExprs = x.cols.map(col => jvm.SelfNullary(col.name).code).toList
            val fieldNames = x.cols.map(_.name).toList
            val constAsExprs = if (dbLib.needsConstAsForCompositeIn) {
              x.cols.map { col =>
                val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(col.tpe), col.typoType)
                dbLib.resolveConstAs(arrayTypoType)
              }.toList
            } else Nil
            val body = dbLib.generateCompositeIn(idsParam.name.code, x.tpe, fieldExprs, fieldNames, constAsExprs)
            jvm.Method(
              annotations = Nil,
              comments = jvm.Comments.Empty,
              tparams = Nil,
              name = jvm.Ident("compositeIdIn"),
              params = List(idsParam),
              implicitParams = Nil,
              tpe = predicateType,
              throws = Nil,
              body = jvm.Body.Expr(body),
              isOverride = false,
              isDefault = false
            )
          }

          List(isMethod, inMethod)
      }
      .getOrElse(Nil)

  protected def extractFkMethods(dbLib: DbLib): List[jvm.Method] =
    maybeFkAnalysis.toList.flatMap(_.extractFksIdsFromRow).flatMap { (x: FkAnalysis.ExtractFkId) =>
      val predicateType = lang.dsl.SqlExpr.of(lang.Boolean)
      val idParam = jvm.Param(jvm.Ident("id"), x.otherCompositeIdType)
      val idsParam = jvm.Param(jvm.Ident("ids"), lang.ListType.tpe.of(x.otherCompositeIdType))

      val isMethod = {
        val equalityExprsList = x.colPairs.map { case (otherCol, thisCol) =>
          val thisField = jvm.SelfNullary(thisCol.name)
          val otherField = lang.prop(idParam.name.code, otherCol.name)
          code"$thisField.isEqual($otherField)"
        }
        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident(s"extract${x.name}Is"),
          params = List(idParam),
          implicitParams = Nil,
          tpe = predicateType,
          throws = Nil,
          body = jvm.Body.Expr(booleanAndChain(dbLib, NonEmptyList(equalityExprsList.head, equalityExprsList.tail))),
          isOverride = false,
          isDefault = false
        )
      }

      val inMethod = {
        val fieldExprs = x.colPairs.map { case (_, thisCol) => jvm.SelfNullary(thisCol.name).code }.toList
        val fieldNames = x.colPairs.map { case (otherCol, _) => otherCol.name }.toList
        val constAsExprs = if (dbLib.needsConstAsForCompositeIn) {
          x.colPairs.map { case (_, thisCol) =>
            val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(thisCol.tpe), thisCol.typoType)
            dbLib.resolveConstAs(arrayTypoType)
          }.toList
        } else Nil
        val body = dbLib.generateCompositeIn(idsParam.name.code, x.otherCompositeIdType, fieldExprs, fieldNames, constAsExprs)
        jvm.Method(
          annotations = Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident(s"extract${x.name}In"),
          params = List(idsParam),
          implicitParams = Nil,
          tpe = predicateType,
          throws = Nil,
          body = jvm.Body.Expr(body),
          isOverride = false,
          isDefault = false
        )
      }

      List(isMethod, inMethod)
    }

  protected def booleanAndChain(dbLib: DbLib, exprs: NonEmptyList[jvm.Code]): jvm.Code =
    dbLib.booleanAndChain(exprs)
}

object FileFields {
  def apply(
      lang: Lang,
      naming: Naming,
      names: ComputedNames,
      options: InternalOptions,
      fks: List[db.ForeignKey],
      maybeFkAnalysis: Option[FkAnalysis],
      maybeCols: Option[NonEmptyList[ComputedColumn]],
      dbLib: DbLib
  ): FileFields = dbLib match {
    case foundationsLib: DbLibFoundations =>
      new FileFieldsFoundations(lang, naming, names, options, fks, maybeFkAnalysis, maybeCols, foundationsLib)
    case legacyLib =>
      new FileFieldsLegacy(lang, naming, names, options, fks, maybeFkAnalysis, maybeCols, legacyLib)
  }
}
