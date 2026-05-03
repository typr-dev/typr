package typr.bridge

import typr.TypeDefinitions

/** A suggested type rule based on column analysis */
case class TypeSuggestion(
    name: String,
    dbColumnPatterns: List[String],
    dbTypePatterns: List[String],
    modelNamePatterns: List[String],
    matchingColumns: List[ColumnMember],
    incompatibleColumns: List[ColumnMember],
    confidence: Double,
    reason: String,
    frequency: Int,
    sources: Set[String]
)

/** Generates type suggestions from column groups */
object TypeSuggester {

  /** Generate type suggestions from column groups */
  def suggest(
      groups: List[ColumnGroup],
      existingTypes: TypeDefinitions,
      minFrequency: Int
  ): List[TypeSuggestion] = {
    groups
      .filter(_.frequency >= minFrequency)
      .filterNot(g => existingTypes.entries.exists(_.name.equalsIgnoreCase(g.suggestedTypeName)))
      .flatMap { group =>
        val (compatible, incompatible) = partitionByTypeCompatibility(group.members)

        if (compatible.isEmpty) None
        else {
          Some(
            TypeSuggestion(
              name = group.suggestedTypeName,
              dbColumnPatterns = generateDbColumnPatterns(group),
              dbTypePatterns = generateDbTypePatterns(compatible),
              modelNamePatterns = generateModelNamePatterns(group),
              matchingColumns = compatible,
              incompatibleColumns = incompatible,
              confidence = calculateConfidence(group, compatible, incompatible),
              reason = generateReason(group),
              frequency = group.frequency,
              sources = group.sources
            )
          )
        }
      }
      .filter(_.confidence > 0.5)
      .sortBy(-_.confidence)
  }

  /** Generate patterns that match all column names in the group */
  private def generateDbColumnPatterns(group: ColumnGroup): List[String] = {
    val columnNames = group.members.map(_.column).distinct

    val patterns = if (columnNames.size <= 3) {
      columnNames
    } else {
      val commonTokens = group.members
        .map(_.interpretation.bestInterpretation.tokens)
        .reduceOption((a, b) => a.intersect(b))
        .getOrElse(Nil)

      if (commonTokens.nonEmpty) {
        val corePattern = commonTokens.mkString("*", "*", "*")
        List(corePattern)
      } else {
        columnNames.take(3)
      }
    }

    patterns.distinct.sorted
  }

  /** Generate patterns for matching database types */
  private def generateDbTypePatterns(members: List[ColumnMember]): List[String] = {
    members.map(m => getTypeName(m.dbType)).distinct.sorted
  }

  /** Get a readable name for a database type */
  private def getTypeName(tpe: typr.db.Type): String = {
    tpe.getClass.getSimpleName.stripSuffix("$")
  }

  /** Generate patterns for model names */
  private def generateModelNamePatterns(group: ColumnGroup): List[String] = {
    val names = group.members.map(_.column).distinct

    val hasSnake = names.exists(_.contains("_"))
    val hasCamel = names.exists(n => n.exists(_.isLower) && n.exists(_.isUpper))

    val patterns = List.newBuilder[String]

    if (hasSnake) {
      val snakePattern = group.canonical.toLowerCase
      patterns += s"*$snakePattern*"
    }

    if (hasCamel || !hasSnake) {
      val camelPattern = group.suggestedTypeName.head.toLower + group.suggestedTypeName.tail
      patterns += s"*$camelPattern*"
    }

    patterns.result().distinct
  }

  /** Partition columns into compatible and incompatible based on type */
  def partitionByTypeCompatibility(
      members: List[ColumnMember]
  ): (List[ColumnMember], List[ColumnMember]) = {
    if (members.isEmpty) return (Nil, Nil)

    val byCompatClass = members.groupBy(m => compatibilityClass(m.dbType))

    if (byCompatClass.size == 1) {
      (members, Nil)
    } else {
      val largest = byCompatClass.maxBy(_._2.size)
      val compatible = largest._2
      val incompatible = members.filterNot(compatible.contains)
      (compatible, incompatible)
    }
  }

  /** Get the compatibility class for a database type */
  private def compatibilityClass(tpe: typr.db.Type): String = {
    tpe match {
      // PostgreSQL types
      case _: typr.db.PgType.VarChar | typr.db.PgType.Text | typr.db.PgType.Char | _: typr.db.PgType.Bpchar =>
        "text"
      case typr.db.PgType.Int2 | typr.db.PgType.Int4 | typr.db.PgType.Int8 =>
        "integer"
      case typr.db.PgType.Float4 | typr.db.PgType.Float8 | typr.db.PgType.Numeric =>
        "numeric"
      case typr.db.PgType.Boolean =>
        "boolean"
      case typr.db.PgType.Date | typr.db.PgType.Time | typr.db.PgType.Timestamp | typr.db.PgType.TimestampTz =>
        "temporal"
      case typr.db.PgType.UUID =>
        "uuid"
      case typr.db.PgType.Json | typr.db.PgType.Jsonb =>
        "json"

      // MariaDB types
      case _: typr.db.MariaType.VarChar | typr.db.MariaType.Text | _: typr.db.MariaType.Char | typr.db.MariaType.TinyText | typr.db.MariaType.MediumText | typr.db.MariaType.LongText =>
        "text"
      case typr.db.MariaType.TinyInt | typr.db.MariaType.SmallInt | typr.db.MariaType.MediumInt | typr.db.MariaType.Int | typr.db.MariaType.BigInt | typr.db.MariaType.TinyIntUnsigned |
          typr.db.MariaType.SmallIntUnsigned | typr.db.MariaType.MediumIntUnsigned | typr.db.MariaType.IntUnsigned | typr.db.MariaType.BigIntUnsigned =>
        "integer"
      case typr.db.MariaType.Float | typr.db.MariaType.Double | _: typr.db.MariaType.Decimal =>
        "numeric"
      case typr.db.MariaType.Boolean =>
        "boolean"
      case typr.db.MariaType.Date | _: typr.db.MariaType.Time | _: typr.db.MariaType.DateTime | _: typr.db.MariaType.Timestamp =>
        "temporal"
      case typr.db.MariaType.Json =>
        "json"

      // DuckDB types
      case _: typr.db.DuckDbType.VarChar | typr.db.DuckDbType.Text =>
        "text"
      case typr.db.DuckDbType.TinyInt | typr.db.DuckDbType.SmallInt | typr.db.DuckDbType.Integer | typr.db.DuckDbType.BigInt | typr.db.DuckDbType.HugeInt | typr.db.DuckDbType.UTinyInt |
          typr.db.DuckDbType.USmallInt | typr.db.DuckDbType.UInteger | typr.db.DuckDbType.UBigInt | typr.db.DuckDbType.UHugeInt =>
        "integer"
      case typr.db.DuckDbType.Float | typr.db.DuckDbType.Double | _: typr.db.DuckDbType.Decimal =>
        "numeric"
      case typr.db.DuckDbType.Boolean =>
        "boolean"
      case typr.db.DuckDbType.Date | typr.db.DuckDbType.Time | typr.db.DuckDbType.Timestamp | typr.db.DuckDbType.TimestampTz =>
        "temporal"
      case typr.db.DuckDbType.UUID =>
        "uuid"

      // Default: use type name as its own class
      case other =>
        getTypeName(other)
    }
  }

  /** Calculate confidence score for a suggestion */
  private def calculateConfidence(
      group: ColumnGroup,
      compatible: List[ColumnMember],
      incompatible: List[ColumnMember]
  ): Double = {
    val frequencyScore = math.min(1.0, group.frequency / 10.0)

    val compatibilityScore =
      if (incompatible.isEmpty) 1.0
      else compatible.size.toDouble / (compatible.size + incompatible.size)

    val sourceScore = math.min(1.0, group.sources.size / 3.0)

    val nameConsistencyScore = {
      val patterns = group.members.map(_.interpretation.bestInterpretation.pattern).distinct
      if (patterns.size == 1) 1.0
      else 1.0 / patterns.size
    }

    (frequencyScore * 0.3) + (compatibilityScore * 0.3) + (sourceScore * 0.2) + (nameConsistencyScore * 0.2)
  }

  /** Generate a human-readable reason for the suggestion */
  private def generateReason(group: ColumnGroup): String = {
    val sources = group.sources.size match {
      case 1 => "1 source"
      case n => s"$n sources"
    }
    val columns = group.frequency match {
      case 1 => "1 column"
      case n => s"$n columns"
    }
    val types = group.dbTypes.size match {
      case 1 => group.dbTypes.head
      case n => s"$n types"
    }

    s"Found $columns across $sources with $types"
  }

  /** Filter suggestions that conflict with existing type names */
  def filterExisting(
      suggestions: List[TypeSuggestion],
      existingTypes: TypeDefinitions
  ): List[TypeSuggestion] = {
    val existingNames = existingTypes.entries.map(_.name.toLowerCase).toSet
    suggestions.filterNot(s => existingNames.contains(s.name.toLowerCase))
  }
}
