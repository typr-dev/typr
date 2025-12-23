package typr
package internal

import typr.internal.analysis.{DecomposedSql, MaybeReturnsRows, ParsedName}
import typr.internal.rewriteDependentData.EvalMaybe
import typr.internal.sqlfiles.SqlFile

case class ComputedSqlFile(
    logger: TypoLogger,
    sqlFile: SqlFile,
    pkg0: jvm.QIdent,
    naming: Naming,
    typeMapperDb: TypeMapperDb,
    scalaTypeMapper: TypeMapperJvm,
    eval: EvalMaybe[db.RelationName, HasSource],
    lang: Lang
) {
  val source: Source.SqlFile = Source.SqlFile(sqlFile.relPath)

  val deps: Map[db.ColName, List[(db.RelationName, db.ColName)]] =
    sqlFile.jdbcMetadata.columns match {
      case MaybeReturnsRows.Query(columns) =>
        columns.toList.flatMap(col => col.baseRelationName.zip(col.baseColumnName).map(t => col.name -> List(t))).toMap
      case MaybeReturnsRows.Update =>
        Map.empty
    }

  val maybeCols: MaybeReturnsRows[NonEmptyList[ComputedColumn]] =
    sqlFile.jdbcMetadata.columns.map { metadataCols =>
      metadataCols.zipWithIndex.map { case (col, idx) =>
        val nullability: Nullability =
          col.parsedColumnName.nullability.getOrElse {
            if (sqlFile.nullableColumnsFromJoins.exists(_.values(idx))) Nullability.Nullable
            else col.isNullable.toNullability
          }

        val dbType = typeMapperDb.dbTypeFrom(col.columnTypeName, Some(col.precision)) { () =>
          logger.warn(s"Couldn't translate type from file ${sqlFile.relPath} column ${col.name.value} with type ${col.columnTypeName}. Falling back to text")
        }

        val pointsTo: List[(Source.Relation, db.ColName)] =
          deps.getOrElse(col.name, Nil).flatMap { case (relName, colName) =>
            eval(relName).flatMap(_.get).map(x => x.source -> colName)
          }

        // we let types flow through constraints down to this column, the point is to reuse id types downstream
        // BUT: only for actual column references, not computed expressions (is_expression from sqlglot)
        val typeFromFk: Option[jvm.Type] =
          if (col.isExpression) None // Computed expression, don't use FK type inference
          else findTypeFromFk(logger, source, col.name, pointsTo, eval, lang)(_ => None)

        val tpe = scalaTypeMapper.sqlFile(col.parsedColumnName.overriddenJvmType(lang).orElse(typeFromFk), dbType, nullability)

        val dbCol = db.Col(
          parsedName = col.parsedColumnName,
          tpe = dbType,
          udtName = None,
          nullability = nullability,
          columnDefault = None,
          maybeGenerated = None,
          comment = None,
          constraints = Nil,
          jsonDescription = DebugJson(col)
        )
        // If user specified an override, create UserDefined with the proper Either; otherwise use standard inference
        val typoType = col.parsedColumnName.overriddenType match {
          case Some(overridden) =>
            val innerTpe = lang.Optional.unapply(tpe).getOrElse(tpe)
            val base = TypoType.UserDefined(innerTpe, dbType, overridden.toEither)
            if (nullability == Nullability.Nullable) TypoType.Nullable(tpe, base) else base
          case None =>
            TypoType.fromJvmAndDb(tpe, dbType, naming.pkg, lang)
        }
        ComputedColumn(
          pointsTo = pointsTo,
          name = naming.field(col.name),
          dbCol = dbCol,
          typoType = typoType
        )
      }
    }

  val params: List[ComputedSqlFile.Param] =
    sqlFile.decomposedSql.paramNamesWithIndices
      .map { case (maybeName, indices) =>
        val jdbcParam = sqlFile.jdbcMetadata.params(indices.head)
        val maybeParsedName: Option[ParsedName] =
          maybeName match {
            case DecomposedSql.NotNamedParam          => None
            case DecomposedSql.NamedParam(parsedName) => Some(parsedName)
          }

        val scalaName = maybeParsedName match {
          case None             => jvm.Ident(s"param${indices.head}")
          case Some(parsedName) => naming.field(parsedName.name)
        }

        val dbType = typeMapperDb.dbTypeFrom(jdbcParam.parameterTypeName, Some(jdbcParam.precision)) { () =>
          logger.warn(s"${sqlFile.relPath}: Couldn't translate type from param $maybeName with type ${jdbcParam.parameterTypeName}")
        }

        val nullability = maybeParsedName.flatMap(_.nullability).getOrElse(jdbcParam.isNullable.toNullability)

        val tpe = scalaTypeMapper.sqlFile(
          maybeOverridden = maybeParsedName.flatMap(_.overriddenJvmType(lang)),
          dbType = dbType,
          nullability = nullability
        )

        // If user specified an override, create UserDefined with the proper Either; otherwise use standard inference
        val typoType = maybeParsedName.flatMap(_.overriddenType) match {
          case Some(overridden) =>
            val innerTpe = lang.Optional.unapply(tpe).getOrElse(tpe)
            val base = TypoType.UserDefined(innerTpe, dbType, overridden.toEither)
            if (nullability == Nullability.Nullable) TypoType.Nullable(tpe, base) else base
          case None =>
            TypoType.fromJvmAndDb(tpe, dbType, naming.pkg, lang)
        }
        ComputedSqlFile.Param(scalaName, tpe, indices, udtName = jdbcParam.parameterTypeName, dbType, typoType)
      }

  val names: ComputedNames =
    ComputedNames(naming, source, maybeId = None, enableFieldValue = false, enableDsl = false)

  val maybeRowName: MaybeReturnsRows[jvm.Type.Qualified] =
    maybeCols.map(_ => names.RowName)

  val repoMethods: NonEmptyList[RepoMethod] =
    NonEmptyList(RepoMethod.SqlFile(this))
}

object ComputedSqlFile {
  case class Param(name: jvm.Ident, tpe: jvm.Type, indices: List[Int], udtName: String, dbType: db.Type, typoType: TypoType)
}
