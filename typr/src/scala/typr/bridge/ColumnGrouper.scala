package typr.bridge

/** A column from a database source with its analysis */
case class ColumnMember(
    source: String,
    schema: String,
    table: String,
    column: String,
    dbType: typr.db.Type,
    interpretation: InterpretationTree,
    normalized: NormalizedName
)

/** A property from a spec source (OpenAPI/JSON Schema) with its analysis */
case class PropertyMember(
    source: String,
    model: String,
    property: String,
    propertyType: String,
    required: Boolean,
    interpretation: InterpretationTree,
    normalized: NormalizedName
)

/** A group of columns with similar canonical names */
case class ColumnGroup(
    canonical: String,
    members: List[ColumnMember],
    suggestedTypeName: String,
    frequency: Int,
    dbTypes: Set[String],
    sources: Set[String]
)

/** Groups columns by their canonical (stemmed, joined) names */
object ColumnGrouper {

  /** Get a readable name for a database type */
  private def getTypeName(tpe: typr.db.Type): String = {
    tpe.getClass.getSimpleName.stripSuffix("$")
  }

  /** Group all columns by their canonical name */
  def group(allColumns: List[ColumnMember]): List[ColumnGroup] = {
    val byCanonical = allColumns.groupBy(_.normalized.canonical)

    byCanonical
      .map { case (canonical, members) =>
        ColumnGroup(
          canonical = canonical,
          members = members,
          suggestedTypeName = suggestTypeName(members),
          frequency = members.size,
          dbTypes = members.map(m => getTypeName(m.dbType)).toSet,
          sources = members.map(_.source).toSet
        )
      }
      .toList
      .sortBy(-_.frequency)
  }

  /** Extract all columns from a MetaDb into ColumnMembers */
  def extractColumns(sourceName: String, metaDb: typr.MetaDb): List[ColumnMember] = {
    metaDb.relations.toList.flatMap { case (relName, lazyRel) =>
      val rel = lazyRel.forceGet
      val cols = rel match {
        case t: typr.db.Table => t.cols.toList.map(c => (c.name, c.tpe))
        case v: typr.db.View  => v.cols.toList.map { case (c, _) => (c.name, c.tpe) }
      }

      cols.map { case (colName, colType) =>
        val interpretation = ColumnTokenizer.tokenize(colName.value)
        val normalized = ColumnStemmer.normalize(interpretation.bestInterpretation.tokens)

        ColumnMember(
          source = sourceName,
          schema = relName.schema.getOrElse(""),
          table = relName.name,
          column = colName.value,
          dbType = colType,
          interpretation = interpretation,
          normalized = normalized
        )
      }
    }
  }

  /** Suggest a type name based on the most common interpretation */
  private def suggestTypeName(members: List[ColumnMember]): String = {
    if (members.isEmpty) return "Unknown"

    val interpretationCounts = members
      .groupBy(_.interpretation.bestInterpretation.tokens)
      .view
      .mapValues(_.size)
      .toMap

    val mostCommon = interpretationCounts.maxBy(_._2)._1

    mostCommon.map(_.capitalize).mkString("")
  }

  /** Group columns from multiple sources */
  def groupFromSources(sources: Map[String, typr.MetaDb]): List[ColumnGroup] = {
    val allColumns = sources.toList.flatMap { case (name, metaDb) =>
      extractColumns(name, metaDb)
    }
    group(allColumns)
  }

  /** Extract all properties from a ParsedSpec into PropertyMembers */
  def extractProperties(sourceName: String, spec: typr.openapi.ParsedSpec): List[PropertyMember] = {
    spec.models.flatMap {
      case obj: typr.openapi.ModelClass.ObjectType =>
        obj.properties.map { prop =>
          val interpretation = ColumnTokenizer.tokenize(prop.name)
          val normalized = ColumnStemmer.normalize(interpretation.bestInterpretation.tokens)

          PropertyMember(
            source = sourceName,
            model = obj.name,
            property = prop.name,
            propertyType = prop.typeInfo.toString,
            required = prop.required,
            interpretation = interpretation,
            normalized = normalized
          )
        }
      case _ => Nil
    }
  }

  /** Group properties from spec sources by their canonical name */
  def groupProperties(allProperties: List[PropertyMember]): List[PropertyGroup] = {
    val byCanonical = allProperties.groupBy(_.normalized.canonical)

    byCanonical
      .map { case (canonical, members) =>
        PropertyGroup(
          canonical = canonical,
          members = members,
          suggestedTypeName = suggestTypeNameFromProperties(members),
          frequency = members.size,
          propertyTypes = members.map(_.propertyType).toSet,
          sources = members.map(_.source).toSet
        )
      }
      .toList
      .sortBy(-_.frequency)
  }

  /** Suggest a type name based on the most common interpretation of properties */
  private def suggestTypeNameFromProperties(members: List[PropertyMember]): String = {
    if (members.isEmpty) return "Unknown"

    val interpretationCounts = members
      .groupBy(_.interpretation.bestInterpretation.tokens)
      .view
      .mapValues(_.size)
      .toMap

    val mostCommon = interpretationCounts.maxBy(_._2)._1

    mostCommon.map(_.capitalize).mkString("")
  }

  /** Group properties from multiple spec sources */
  def groupPropertiesFromSpecs(sources: Map[String, typr.openapi.ParsedSpec]): List[PropertyGroup] = {
    val allProperties = sources.toList.flatMap { case (name, spec) =>
      extractProperties(name, spec)
    }
    groupProperties(allProperties)
  }
}

/** A group of properties with similar canonical names */
case class PropertyGroup(
    canonical: String,
    members: List[PropertyMember],
    suggestedTypeName: String,
    frequency: Int,
    propertyTypes: Set[String],
    sources: Set[String]
)
