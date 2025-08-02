package typo
package internal

import typo.internal.analysis.ParsedName
import typo.internal.rewriteDependentData.Eval

case class ComputedView(
    logger: TypoLogger,
    view: db.View,
    naming: Naming,
    typeMapperDb: TypeMapperDb,
    scalaTypeMapper: TypeMapperScala,
    eval: Eval[db.RelationName, HasSource],
    enableFieldValue: Boolean,
    enableDsl: Boolean,
    dialect: Dialect
) extends HasSource {
  val source: Source.View = Source.View(view.name, view.isMaterialized)

  val pointsToByColName: Map[db.ColName, List[(Source.Relation, db.ColName)]] =
    view.cols.map { case (col, _) =>
      col.name -> view.deps.getOrElse(col.name, Nil).flatMap { case (relName, colName) => eval(relName).get.map(_.source -> colName) }
    }.toMap

  val colsByName: Map[db.ColName, (db.Col, ParsedName)] =
    view.cols.map { case t @ (col, _) => col.name -> t }.toMap

  val cols: NonEmptyList[ComputedColumn] =
    view.cols.map { case (col, _) =>
      ComputedColumn(
        pointsTo = pointsToByColName(col.name),
        name = naming.field(col.name),
        tpe = inferType(col.name),
        dbCol = col
      )
    }

  def inferType(colName: db.ColName): sc.Type = {
    val (col, parsedName) = colsByName(colName)
    val typeFromFk: Option[sc.Type] =
      findTypeFromFk(logger, source, col.name, pointsToByColName(col.name), eval.asMaybe, dialect)(otherColName => Some(inferType(otherColName)))
    scalaTypeMapper.sqlFile(parsedName.overriddenType.orElse(typeFromFk), col.tpe, col.nullability)
  }

  val names = ComputedNames(naming, source, maybeId = None, enableFieldValue, enableDsl = enableDsl)

  val repoMethods: NonEmptyList[RepoMethod] = {
    val maybeSelectByFieldValues = for {
      fieldOrIdValueName <- names.FieldOrIdValueName
      fieldValueName <- names.FieldValueName
    } yield {
      val fieldValuesParam = sc.Param(
        sc.Ident("fieldValues"),
        TypesScala.List.of(fieldOrIdValueName.of(sc.Type.Wildcard)),
        None
      )
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
