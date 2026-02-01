package typr
package internal

import typr.internal.analysis.ParsedName
import typr.internal.rewriteDependentData.Eval

case class ComputedView(
    lang: Lang,
    logger: TypoLogger,
    view: db.View,
    naming: Naming,
    typeMapperDb: TypeMapperDb,
    scalaTypeMapper: TypeMapperJvm,
    eval: Eval[db.RelationName, HasSource],
    enableFieldValue: Boolean,
    enableDsl: Boolean
) extends HasSource {
  val source: Source.View = Source.View(view.name, view.isMaterialized)

  val pointsToByColName: Map[db.ColName, List[(Source.Relation, db.ColName)]] =
    view.cols.map { case (col, _) =>
      col.name -> view.deps.getOrElse(col.name, Nil).flatMap { case (relName, colName) => eval(relName).get.map(_.source -> colName) }
    }.toMap

  val colsByName: Map[db.ColName, (db.Col, ParsedName)] =
    view.cols.map { case t @ (col, _) => col.name -> t }.toMap

  val cols: NonEmptyList[ComputedColumn] = {
    def mkComputedColumn(pointsTo: List[(Source.Relation, db.ColName)], name: jvm.Ident, tpe: jvm.Type, dbCol: db.Col, parsedName: ParsedName): ComputedColumn = {
      // If user specified an override, create UserDefined with the proper Either; otherwise use standard inference
      val typoType = parsedName.overriddenType match {
        case Some(overridden) =>
          val innerTpe = lang.Optional.unapply(tpe).getOrElse(tpe)
          val base = TypoType.UserDefined(innerTpe, dbCol.tpe, overridden.toEither)
          if (dbCol.nullability == Nullability.Nullable) TypoType.Nullable(tpe, base) else base
        case None =>
          TypoType.fromJvmAndDb(tpe, dbCol.tpe, naming.pkg, lang)
      }
      ComputedColumn(pointsTo = pointsTo, name = name, dbCol = dbCol, typoType = typoType)
    }

    view.cols.map { case (col, parsedName) =>
      mkComputedColumn(pointsToByColName(col.name), naming.field(col.name), inferType(col.name), col, parsedName)
    }
  }

  def inferType(colName: db.ColName): jvm.Type = {
    val (col, parsedName) = colsByName(colName)
    val typeFromFk: Option[jvm.Type] =
      findTypeFromFk(logger, source, col.name, pointsToByColName(col.name), eval.asMaybe, lang)(otherColName => Some(inferType(otherColName)))
    scalaTypeMapper.sqlFile(parsedName.overriddenJvmType(lang).orElse(typeFromFk), col.tpe, col.nullability)
  }

  val names = ComputedNames(naming, source, maybeId = None, enableFieldValue, enableDsl = enableDsl)

  val repoMethods: NonEmptyList[RepoMethod] = {
    val maybeSelectByFieldValues = for {
      fieldValueName <- names.FieldOrIdValueName
    } yield {
      val fieldValuesParam = jvm.Param(jvm.Ident("fieldValues"), lang.ListType.tpe.of(fieldValueName.of(jvm.Type.Wildcard)))
      RepoMethod.SelectByFieldValues(view.name, cols, fieldValueName, fieldValuesParam, names.RowName)
    }
    val maybeSelectBuilder = for {
      fieldsName <- names.FieldsName
    } yield {
      RepoMethod.SelectBuilder(view.name, fieldsName, names.RowName)
    }
    NonEmptyList[RepoMethod](
      RepoMethod.SelectAll(view.name, cols, names.RowName),
      maybeSelectBuilder.toList ++ maybeSelectByFieldValues
    ).sorted
  }
}
