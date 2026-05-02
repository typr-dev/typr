package typr.bridge

import typr.MetaDb

/** Suggests composite types based on database schema analysis. Groups tables by normalized name across sources and finds common fields.
  */
object CompositeTypeSuggester {

  case class CompositeSuggestion(
      name: String,
      sourceCount: Int,
      projections: List[SourceProjection],
      fields: List[SuggestedField],
      score: Int,
      reason: String
  ) {
    def sourceName: String = projections.headOption.map(_.sourceName).getOrElse("")
    def entityPath: String = projections.headOption.map(_.entityPath).getOrElse("")
  }

  case class SourceProjection(
      sourceName: String,
      entityPath: String,
      allFields: List[SuggestedField]
  )

  case class SuggestedField(
      name: String,
      typeName: String,
      nullable: Boolean,
      isPrimaryKey: Boolean
  )

  private case class TableInfo(
      sourceName: String,
      entityPath: String,
      normalizedName: String,
      fields: List[SuggestedField],
      hasPK: Boolean,
      fkCount: Int
  )

  def suggest(
      metaDbs: Map[String, MetaDb],
      existingTypes: Set[String],
      limit: Int
  ): List[CompositeSuggestion] = {
    val allTables = extractAllTables(metaDbs)

    val groupedByName = allTables.groupBy(_.normalizedName)

    val suggestions = groupedByName.toList.flatMap { case (normalizedName, tables) =>
      val sourceCount = tables.map(_.sourceName).distinct.size
      val typeName = generateTypeName(normalizedName)

      if (existingTypes.contains(typeName)) {
        None
      } else {
        val commonFields = findCommonFields(tables)

        if (commonFields.size >= 2) {
          val projections = tables.map { t =>
            SourceProjection(
              sourceName = t.sourceName,
              entityPath = t.entityPath,
              allFields = t.fields
            )
          }

          val score = computeScore(sourceCount, commonFields.size, tables)
          val reason = computeReason(sourceCount, commonFields.size, tables.head.fields.size)

          Some(
            CompositeSuggestion(
              name = typeName,
              sourceCount = sourceCount,
              projections = projections,
              fields = commonFields,
              score = score,
              reason = reason
            )
          )
        } else {
          None
        }
      }
    }

    suggestions
      .sortBy(s => (-s.sourceCount, -s.score))
      .take(limit)
  }

  private def extractAllTables(metaDbs: Map[String, MetaDb]): List[TableInfo] = {
    for {
      (sourceName, metaDb) <- metaDbs.toList
      (relName, lazyRel) <- metaDb.relations.toList
      relation <- lazyRel.get.toList
      table <- relation match {
        case t: typr.db.Table => Some(t)
        case _                => None
      }
      if table.cols.toList.size >= 3
    } yield {
      val cols = table.cols.toList
      val pks = table.primaryKey.map(_.colNames.toList.toSet).getOrElse(Set.empty[typr.db.ColName])
      val fkCount = table.foreignKeys.size

      val fields = cols.map { col =>
        SuggestedField(
          name = normalizeFieldName(col.name.value),
          typeName = formatType(col.tpe),
          nullable = col.nullability == typr.Nullability.Nullable,
          isPrimaryKey = pks.contains(col.name)
        )
      }

      val entityPath = relName.schema.map(s => s"$s.${relName.name}").getOrElse(relName.name)

      TableInfo(
        sourceName = sourceName,
        entityPath = entityPath,
        normalizedName = normalizeTableName(relName.name),
        fields = fields,
        hasPK = pks.nonEmpty,
        fkCount = fkCount
      )
    }
  }

  private def findCommonFields(tables: List[TableInfo]): List[SuggestedField] = {
    if (tables.isEmpty) return Nil
    if (tables.size == 1) return tables.head.fields

    val fieldsByName = tables.flatMap(_.fields).groupBy(_.name)

    fieldsByName.toList
      .filter { case (_, fields) =>
        fields.size == tables.size
      }
      .map { case (name, fields) =>
        val types = fields.map(_.typeName).distinct
        val typeName = if (types.size == 1) types.head else "String"
        val nullable = fields.exists(_.nullable)
        val isPK = fields.forall(_.isPrimaryKey)

        SuggestedField(
          name = name,
          typeName = typeName,
          nullable = nullable,
          isPrimaryKey = isPK
        )
      }
      .sortBy(f => (!f.isPrimaryKey, f.name))
  }

  private def computeScore(sourceCount: Int, commonFieldCount: Int, tables: List[TableInfo]): Int = {
    var score = 0

    score += sourceCount * 50

    score += math.min(commonFieldCount * 5, 50)

    if (tables.exists(_.hasPK)) score += 20

    val avgFkCount = tables.map(_.fkCount).sum.toDouble / tables.size
    score += math.min((avgFkCount * 10).toInt, 20)

    val tableName = tables.head.normalizedName.toLowerCase
    val entityPatterns =
      List("customer", "user", "order", "product", "account", "item", "person", "employee", "company", "organization")
    if (entityPatterns.exists(p => tableName.contains(p))) score += 15

    val junctionPatterns = List("_to_", "_x_", "link", "mapping", "relation")
    if (junctionPatterns.exists(p => tableName.contains(p))) score -= 30

    score
  }

  private def computeReason(sourceCount: Int, commonFieldCount: Int, totalFieldCount: Int): String = {
    val parts = List.newBuilder[String]

    parts += s"$sourceCount sources"
    parts += s"$commonFieldCount common fields"
    if (commonFieldCount < totalFieldCount) {
      parts += s"(of $totalFieldCount)"
    }

    parts.result().mkString(", ")
  }

  private def normalizeTableName(name: String): String = {
    name.toLowerCase
      .replaceAll("^(tbl_|t_)", "")
      .replaceAll("_+$", "")
  }

  private def normalizeFieldName(name: String): String = {
    name.toLowerCase
  }

  private def generateTypeName(tableName: String): String = {
    val cleaned = tableName
      .split("[_\\s]+")
      .map(_.toLowerCase.capitalize)
      .mkString

    if (cleaned.endsWith("s") && cleaned.length > 3) {
      cleaned.dropRight(1)
    } else {
      cleaned
    }
  }

  private def formatType(tpe: typr.db.Type): String = {
    import typr.db.{MariaType, PgType}
    tpe match {
      case PgType.Text | _: PgType.VarChar | PgType.Char | MariaType.Text | _: MariaType.VarChar | _: MariaType.Char =>
        "String"
      case PgType.Int4 | MariaType.Int =>
        "Int"
      case PgType.Int8 | MariaType.BigInt =>
        "Long"
      case PgType.Int2 | MariaType.SmallInt =>
        "Short"
      case PgType.Float4 | MariaType.Float =>
        "Float"
      case PgType.Float8 | MariaType.Double =>
        "Double"
      case PgType.Numeric | _: MariaType.Decimal =>
        "BigDecimal"
      case PgType.Boolean | MariaType.TinyInt =>
        "Boolean"
      case PgType.Timestamp | PgType.TimestampTz | _: MariaType.Timestamp | _: MariaType.DateTime =>
        "Instant"
      case PgType.Date | MariaType.Date =>
        "LocalDate"
      case PgType.Time | PgType.TimeTz | _: MariaType.Time =>
        "LocalTime"
      case PgType.UUID =>
        "UUID"
      case PgType.Bytea | MariaType.Blob =>
        "ByteArray"
      case PgType.Json | PgType.Jsonb | MariaType.Json =>
        "Json"
      case _ =>
        "String"
    }
  }
}
