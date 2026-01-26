package typr

import scala.annotation.unused

class Naming(val pkg: jvm.QIdent, lang: Lang) {
  protected def fragments(source: Source): (jvm.QIdent, String) = {
    def forRelPath(relPath: RelPath): (jvm.QIdent, String) =
      (
        pkg / relPath.segments.init.map(pkgSafe),
        relPath.segments.last.replace(".sql", "")
      )

    source match {
      case relation: Source.Relation =>
        (pkg / relation.name.schema.toList.map(pkgSafe), normalizeOracleName(relation.name.name))
      case Source.SqlFile(relPath) => forRelPath(relPath)
    }
  }

  // Normalize Oracle SCREAMING_CASE identifiers to lowercase for package/directory names
  private def normalizeOracleName(str: String): String =
    if (str.nonEmpty && str.forall(c => c.isUpper || c.isDigit || c == '_' || c == '-' || c == '.')) {
      str.toLowerCase
    } else {
      str
    }

  // not enough, but covers a common case
  def pkgSafe(str: String): jvm.Ident = jvm.Ident(lang.escapedIdent(normalizeOracleName(str)))

  def suffixFor(source: Source): String =
    source match {
      case Source.Table(_)       => ""
      case Source.View(_, false) => "View"
      case Source.View(_, true)  => "MV"
      case Source.SqlFile(_)     => "Sql"
    }

  protected def relation(source: Source, suffix: String): jvm.QIdent = {
    val (init, name) = fragments(source)
    val suffix0 = suffixFor(source)
    init / pkgSafe(name) / jvm.Ident(Naming.titleCase(name)).appended(suffix0 + suffix)
  }

  protected def tpe(name: db.RelationName, suffix: String): jvm.QIdent =
    pkg / name.schema.map(pkgSafe).toList / jvm.Ident(Naming.titleCase(name.name)).appended(suffix)

  def idName(source: Source, @unused cols: List[db.Col]): jvm.QIdent = {
    relation(source, "Id")
  }
  def repoName(source: Source): jvm.QIdent = relation(source, "Repo")
  def repoImplName(source: Source): jvm.QIdent = relation(source, "RepoImpl")
  def repoMockName(source: Source): jvm.QIdent = relation(source, "RepoMock")
  def rowName(source: Source): jvm.QIdent = relation(source, "Row")
  def fieldsName(source: Source): jvm.QIdent = relation(source, "Fields")
  def fieldOrIdValueName(source: Source): jvm.QIdent = relation(source, "FieldValue")
  def rowUnsaved(source: Source): jvm.QIdent = relation(source, "RowUnsaved")

  def className(names: List[jvm.Ident]): jvm.QIdent = pkg / names

  def enumName(name: db.RelationName): jvm.QIdent =
    tpe(name, "")

  def domainName(name: db.RelationName): jvm.QIdent =
    tpe(name, "")

  def compositeTypeName(name: db.RelationName): jvm.QIdent =
    tpe(name, "")

  def objectTypeName(name: db.RelationName): jvm.QIdent =
    tpe(name, "")

  /** Name for a DuckDB STRUCT type. The name is pre-computed (e.g., "EmployeeContactInfo"). */
  def structTypeName(name: String): jvm.QIdent =
    pkg / jvm.Ident(name)

  /** Name for a MariaDB SET type derived from its sorted values. Example: SET('read', 'write', 'admin') -> sorted: admin, read, write -> AdminReadWriteSet Limits to first 20 characters to avoid
    * extremely long names.
    */
  def setTypeName(values: scala.collection.immutable.SortedSet[String]): jvm.QIdent = {
    val name = values.toList.map(Naming.titleCase).mkString("").take(20)
    pkg / jvm.Ident(name + "Set")
  }

  private val preciseTypesPackage: jvm.QIdent = pkg / jvm.Ident("precisetypes")

  def preciseStringNName(maxLength: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"String$maxLength")

  def precisePaddedStringNName(length: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"PaddedString$length")

  def preciseNonEmptyPaddedStringNName(length: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"NonEmptyPaddedString$length")

  def preciseNonEmptyStringName: jvm.QIdent =
    preciseTypesPackage / jvm.Ident("NonEmptyString")

  def preciseNonEmptyStringNName(maxLength: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"NonEmptyString$maxLength")

  def preciseBinaryNName(maxLength: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"Binary$maxLength")

  def preciseDecimalNName(precision: Int, scale: Int): jvm.QIdent =
    if (scale == 0) preciseTypesPackage / jvm.Ident(s"Int$precision")
    else preciseTypesPackage / jvm.Ident(s"Decimal${precision}_$scale")

  def preciseLocalDateTimeNName(fsp: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"LocalDateTime$fsp")

  def preciseInstantNName(fsp: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"Instant$fsp")

  def preciseLocalTimeNName(fsp: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"LocalTime$fsp")

  def preciseOffsetDateTimeNName(fsp: Int): jvm.QIdent =
    preciseTypesPackage / jvm.Ident(s"OffsetDateTime$fsp")

  def enumValue(name: String): jvm.Ident = jvm.Ident(name)

  // field names
  def field(name: db.ColName): jvm.Ident =
    Naming.camelCaseIdent(name.value.split('_'))

  def fk(baseTable: db.RelationName, fk: db.ForeignKey, includeCols: Boolean): jvm.Ident = {
    val parts = Array[Iterable[String]](
      List("fk"),
      fk.otherTable.schema.filterNot(baseTable.schema.contains),
      List(fk.otherTable.name),
      if (includeCols) fk.cols.toList.flatMap(_.value.split("_")) else Nil
    )
    Naming.camelCaseIdent(parts.flatten)
  }

  // multiple field names together into one name
  def field(colNames: NonEmptyList[db.ColName]): jvm.Ident =
    Naming.camelCaseIdent(colNames.map(field).map(_.value).toArray)
}

object Naming {
  def camelCaseIdent(strings: Array[String]): jvm.Ident =
    jvm.Ident(camelCase(strings))

  def splitOnSymbol(str: String): Array[String] = {
    // Normalize Oracle SCREAMING_CASE to lowercase before splitting
    val normalized = if (str.nonEmpty && str.forall(c => c.isUpper || c.isDigit || c == '_' || c == '-' || c == '.')) {
      str.toLowerCase
    } else {
      str
    }
    normalized.split("[\\-_.]")
  }

  def camelCase(strings: Array[String]): String =
    strings
      .flatMap(splitOnSymbol)
      .zipWithIndex
      .map {
        case (s, 0) => s.updated(0, s.head.toLower)
        case (s, _) => s.capitalize
      }
      .mkString("")

  def camelCase(string: String): String =
    camelCase(string.split('_'))

  def titleCase(name: String): String =
    titleCase(name.split('_'))

  def titleCase(strings: Array[String]): String =
    strings.flatMap(splitOnSymbol).map(_.capitalize).mkString("")
}
