package typo

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

  def idName(source: Source, cols: List[db.Col]): jvm.QIdent = {
    ((), cols)._1 // allow implementors to use this
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

  def objectTypeName(name: db.RelationName): jvm.QIdent =
    tpe(name, "")

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
