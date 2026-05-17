package typr.cli.app

import typr.MetaDb
import typr.config.generated.{FieldType, StringOrArray, StringOrArrayArray, StringOrArrayString}
import typr.internal.TypeMatcher
import typr.{db, ApiMatch, DbMatch, ModelMatch, TypeDefinitions, TypeEntry}

/** Runs a [[FieldType]]'s db-match patterns against an already-introspected [[MetaDb]] and reports which columns match. Companion utility for the FieldType deep editor — lets the user see, at a
  * glance, what their patterns actually catch in real data without running a full generate.
  *
  * Stays cheap: no I/O, no introspection. The caller passes in a MetaDb that was already loaded (typically from `AppApi.metaDbCache`, populated by the schema browser).
  */
object TypePreview {

  /** One match: fully-qualified column name and the database type we discovered there. */
  final case class Match(qualifiedColumn: String, dbType: String)

  /** Per-source preview result. `matches` is sorted lexically; the caller usually truncates before displaying.
    */
  final case class Result(sourceName: String, matches: List[Match])

  /** Run all the db-match patterns from `ft` against the relations in `metaDb`. Skips columns inside views (typr's TypeMatcher.findDbMatches only matches tables) and any FieldType whose `db` block is
    * empty (no patterns = nothing to match).
    */
  def runDb(typeName: String, ft: FieldType, sourceName: String, metaDb: MetaDb): Result = {
    val entry = TypeEntry(
      name = typeName,
      db = toDbMatch(ft),
      model = ModelMatch.Empty,
      api = ApiMatch.Empty
    )
    if (entry.db == DbMatch.Empty) return Result(sourceName, Nil)

    val defs = TypeDefinitions(List(entry))
    val builder = List.newBuilder[Match]

    metaDb.relations.foreach { case (_, lazyRel) =>
      lazyRel.forceGet match {
        case table: db.Table =>
          table.cols.toList.foreach { col =>
            val ctx = TypeMatcher.DbColumnContext.from(sourceName, table, col)
            if (TypeMatcher.findDbMatches(defs, ctx).nonEmpty) {
              val qualified = table.name.schema match {
                case Some(s) => s"$s.${table.name.name}.${col.name.value}"
                case None    => s"${table.name.name}.${col.name.value}"
              }
              builder += Match(qualified, dbTypeLabel(col.tpe))
            }
          }
        case _: db.View => ()
      }
    }

    Result(sourceName, builder.result().sortBy(_.qualifiedColumn))
  }

  /** Maps the editor's [[typr.config.generated.DbMatch]] (string-or-array fields) onto the matcher's [[typr.DbMatch]] (flat List[String] fields).
    */
  private def toDbMatch(ft: FieldType): DbMatch = ft.db match {
    case None    => DbMatch.Empty
    case Some(m) =>
      DbMatch(
        database = toList(m.source),
        schema = toList(m.schema),
        table = toList(m.table),
        column = toList(m.column),
        dbType = toList(m.db_type),
        domain = toList(m.domain),
        primaryKey = m.primary_key,
        nullable = m.nullable,
        references = toList(m.references),
        comment = toList(m.comment),
        annotation = toList(m.annotation)
      )
  }

  private def toList(opt: Option[StringOrArray]): List[String] = opt match {
    case Some(StringOrArrayString(s))  => List(s)
    case Some(StringOrArrayArray(arr)) => arr
    case _                             => Nil
  }

  private def dbTypeLabel(tpe: db.Type): String = tpe.toString
}
