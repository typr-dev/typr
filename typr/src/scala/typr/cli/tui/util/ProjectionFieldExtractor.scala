package typr.cli.tui.util

import typr.cli.tui.SourceName
import typr.cli.tui.SourceStatus
import typr.cli.tui.TuiState
import typr.db.RelationName

case class ExtractedField(
    name: String,
    typeName: String,
    nullable: Boolean,
    isPrimaryKey: Boolean,
    comment: Option[String]
)

case class ExtractedEntity(
    sourceName: String,
    entityPath: String,
    fields: List[ExtractedField]
)

object ProjectionFieldExtractor {

  def extractFieldsFromSource(
      state: TuiState,
      sourceName: SourceName,
      entityPath: String
  ): Either[String, ExtractedEntity] = {
    state.sourceStatuses.get(sourceName) match {
      case Some(ready: SourceStatus.Ready) =>
        extractFromDb(ready.metaDb, sourceName.value, entityPath)

      case Some(readySpec: SourceStatus.ReadySpec) =>
        extractFromSpec(readySpec.spec, sourceName.value, entityPath)

      case Some(_: SourceStatus.LoadingMetaDb | _: SourceStatus.LoadingSpec | _: SourceStatus.RunningSqlGlot) =>
        Left(s"Source '${sourceName.value}' is still loading")

      case Some(_: SourceStatus.Failed) =>
        Left(s"Source '${sourceName.value}' failed to load")

      case Some(SourceStatus.Pending) =>
        Left(s"Source '${sourceName.value}' has not started loading")

      case None =>
        Left(s"Source '${sourceName.value}' not found")
    }
  }

  private def extractFromDb(
      metaDb: typr.MetaDb,
      sourceName: String,
      entityPath: String
  ): Either[String, ExtractedEntity] = {
    val (schema, table) = parseEntityPath(entityPath)
    val relName = RelationName(schema, table)

    metaDb.relations.get(relName) match {
      case Some(lazyRel) =>
        val relation = lazyRel.forceGet

        val cols: List[typr.db.Col] = relation match {
          case t: typr.db.Table => t.cols.toList
          case v: typr.db.View  => v.cols.toList.map(_._1)
        }

        val pks: Set[typr.db.ColName] = relation match {
          case t: typr.db.Table => t.primaryKey.map(_.colNames.toList.toSet).getOrElse(Set.empty)
          case _: typr.db.View  => Set.empty
        }

        val fields = cols.map { col =>
          ExtractedField(
            name = col.name.value,
            typeName = formatDbType(col.tpe),
            nullable = col.nullability == typr.Nullability.Nullable,
            isPrimaryKey = pks.contains(col.name),
            comment = col.comment
          )
        }

        Right(ExtractedEntity(sourceName, entityPath, fields))

      case None =>
        Left(s"Entity '$entityPath' not found in source '$sourceName'")
    }
  }

  private def extractFromSpec(
      spec: typr.openapi.ParsedSpec,
      sourceName: String,
      entityPath: String
  ): Either[String, ExtractedEntity] = {
    spec.models.collectFirst {
      case obj: typr.openapi.ModelClass.ObjectType if obj.name == entityPath =>
        obj
    } match {
      case Some(objectType) =>
        val fields = objectType.properties.map { prop =>
          ExtractedField(
            name = prop.name,
            typeName = formatTypeInfo(prop.typeInfo),
            nullable = prop.nullable || !prop.required,
            isPrimaryKey = false,
            comment = prop.description
          )
        }
        Right(ExtractedEntity(sourceName, entityPath, fields))

      case None =>
        val modelNames = spec.models.map(_.name).sorted
        Left(s"Schema '$entityPath' not found in spec. Available: ${modelNames.take(5).mkString(", ")}${if (modelNames.length > 5) "..." else ""}")
    }
  }

  private def formatTypeInfo(typeInfo: typr.openapi.TypeInfo): String = {
    typeInfo match {
      case typr.openapi.TypeInfo.Primitive(pt)       => pt.toString.toLowerCase
      case typr.openapi.TypeInfo.Ref(name)           => name
      case typr.openapi.TypeInfo.ListOf(inner)       => s"[${formatTypeInfo(inner)}]"
      case typr.openapi.TypeInfo.MapOf(_, valueType) => s"Map[${formatTypeInfo(valueType)}]"
      case typr.openapi.TypeInfo.Optional(inner)     => s"${formatTypeInfo(inner)}?"
      case typr.openapi.TypeInfo.Any                 => "any"
      case typr.openapi.TypeInfo.InlineEnum(values)  => s"enum(${values.take(3).mkString(",")}${if (values.length > 3) "..." else ""})"
    }
  }

  private def formatDbType(tpe: typr.db.Type): String = {
    tpe.toString
  }

  private def parseEntityPath(entityPath: String): (Option[String], String) = {
    val parts = entityPath.split("\\.", 2)
    if (parts.length == 2) (Some(parts(0)), parts(1))
    else (None, entityPath)
  }

  def getAvailableEntities(state: TuiState, sourceName: SourceName): List[String] = {
    state.sourceStatuses.get(sourceName) match {
      case Some(ready: SourceStatus.Ready) =>
        ready.metaDb.relations.keys.toList.map { relName =>
          relName.schema match {
            case Some(s) => s"$s.${relName.name}"
            case None    => relName.name
          }
        }.sorted

      case Some(readySpec: SourceStatus.ReadySpec) =>
        readySpec.spec.models.collect { case obj: typr.openapi.ModelClass.ObjectType =>
          obj.name
        }.sorted

      case _ => Nil
    }
  }

  def computeAutoMapping(
      canonicalFields: List[String],
      sourceFields: List[ExtractedField]
  ): Map[String, String] = {
    val sourceFieldNames = sourceFields.map(_.name).toSet
    val sourceFieldNameLower = sourceFields.map(f => f.name.toLowerCase -> f.name).toMap

    canonicalFields.flatMap { canonicalField =>
      if (sourceFieldNames.contains(canonicalField)) {
        Some(canonicalField -> canonicalField)
      } else {
        sourceFieldNameLower
          .get(canonicalField.toLowerCase)
          .map { sourceField =>
            canonicalField -> sourceField
          }
          .orElse {
            val snakeCase = toSnakeCase(canonicalField)
            sourceFieldNameLower.get(snakeCase.toLowerCase).map { sourceField =>
              canonicalField -> sourceField
            }
          }
          .orElse {
            val camelCase = toCamelCase(canonicalField)
            sourceFieldNameLower.get(camelCase.toLowerCase).map { sourceField =>
              canonicalField -> sourceField
            }
          }
      }
    }.toMap
  }

  private def toSnakeCase(s: String): String = {
    s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase
  }

  private def toCamelCase(s: String): String = {
    val parts = s.split("_")
    if (parts.length == 1) s
    else parts.head + parts.tail.map(_.capitalize).mkString
  }
}
