package typo
package internal

import typo.internal.analysis.{DecomposedSql, MaybeReturnsRows, ParsedName}
import typo.internal.rewriteDependentData.EvalMaybe
import typo.internal.sqlfiles.SqlFile
import typo.sc.Type

case class ComputedSqlFile(
    logger: TypoLogger,
    sqlFile: SqlFile,
    pkg0: sc.QIdent,
    naming: Naming,
    typeMapperDb: TypeMapperDb,
    scalaTypeMapper: TypeMapperScala,
    eval: EvalMaybe[db.RelationName, HasSource],
    dialect: Dialect
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
        val typeFromFk: Option[sc.Type] = findTypeFromFk(logger, source, col.name, pointsTo, eval, dialect)(_ => None)

        val tpe = scalaTypeMapper.sqlFile(col.parsedColumnName.overriddenType.orElse(typeFromFk), dbType, nullability)

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
        ComputedColumn(pointsTo = pointsTo, name = naming.field(col.name), tpe = tpe, dbCol = dbCol)
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
          case None             => sc.Ident(s"param${indices.head}")
          case Some(parsedName) => naming.field(parsedName.name)
        }

        val dbType = typeMapperDb.dbTypeFrom(jdbcParam.parameterTypeName, Some(jdbcParam.precision)) { () =>
          logger.warn(s"${sqlFile.relPath}: Couldn't translate type from param $maybeName with type ${jdbcParam.parameterTypeName}")
        }

        val tpe = scalaTypeMapper.sqlFile(
          maybeOverridden = maybeParsedName.flatMap(_.overriddenType),
          dbType = dbType,
          nullability = maybeParsedName.flatMap(_.nullability).getOrElse(jdbcParam.isNullable.toNullability)
        )

        ComputedSqlFile.Param(scalaName, tpe, indices, udtName = jdbcParam.parameterTypeName, dbType)
      }

  val names: ComputedNames =
    ComputedNames(naming, source, maybeId = None, enableFieldValue = false, enableDsl = false)

  val maybeRowName: MaybeReturnsRows[Type.Qualified] =
    maybeCols.map(_ => names.RowName)

  val repoMethods: NonEmptyList[RepoMethod] =
    NonEmptyList(RepoMethod.SqlFile(this))
}

object ComputedSqlFile {
  case class Param(name: sc.Ident, tpe: sc.Type, indices: List[Int], udtName: String, dbType: db.Type)
}
