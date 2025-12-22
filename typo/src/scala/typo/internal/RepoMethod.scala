package typo
package internal

import typo.jvm.Comments

/** Strategy for returning data after INSERT operations. Different databases require different JDBC mechanisms.
  */
sealed trait ReturningStrategy {
  def returnType: jvm.Type
}

object ReturningStrategy {

  /** Use SQL-level RETURNING clause (PostgreSQL, MariaDB). Returns full row via ResultSet from execute().
    */
  case class SqlReturning(rowType: jvm.Type) extends ReturningStrategy {
    def returnType: jvm.Type = rowType
  }

  /** Use JDBC getGeneratedKeys() with ALL column names. Returns full row (Oracle for tables without STRUCT/ARRAY).
    */
  case class GeneratedKeysAllColumns(rowType: jvm.Type, columns: NonEmptyList[ComputedColumn]) extends ReturningStrategy {
    def returnType: jvm.Type = rowType
  }

  /** Use JDBC getGeneratedKeys() with ID column names only. Returns just the ID (Oracle for tables with STRUCT/ARRAY).
    */
  case class GeneratedKeysIdOnly(id: IdComputed) extends ReturningStrategy {
    def returnType: jvm.Type = id.tpe
  }
}

sealed abstract class RepoMethod(val methodName: String, val tiebreaker: Int) {
  val comment: jvm.Comments = jvm.Comments.Empty

  /** Whether this method requires PostgreSQL COPY/streaming support */
  def requiresStreamingSupport: Boolean = false
}

object RepoMethod {
  sealed abstract class Mutator(methodName: String, tiebreaker: Int = 0) extends RepoMethod(methodName, tiebreaker)
  sealed abstract class Selector(methodName: String, tiebreaker: Int = 0) extends RepoMethod(methodName, tiebreaker)

  case class SelectAll(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      rowType: jvm.Type
  ) extends Selector("selectAll")

  case class SelectBuilder(
      relName: db.RelationName,
      fieldsType: jvm.Type,
      rowType: jvm.Type
  ) extends Selector("select")

  case class SelectById(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      id: IdComputed,
      rowType: jvm.Type
  ) extends Selector("selectById")

  case class SelectByIds(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      idComputed: IdComputed,
      idsParam: jvm.Param[jvm.Type],
      rowType: jvm.Type
  ) extends Selector("selectByIds")

  case class SelectByIdsTracked(
      selectByIds: SelectByIds
  ) extends Selector("selectByIdsTracked")

  case class SelectByUnique(
      relName: db.RelationName,
      keyColumns: NonEmptyList[ComputedColumn],
      allColumns: NonEmptyList[ComputedColumn],
      rowType: jvm.Type
  ) extends Selector(s"selectByUnique${keyColumns.map(x => Naming.titleCase(x.name.value)).mkString("And")}")

  case class SelectByFieldValues(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      fieldValueType: jvm.Type.Qualified,
      fieldValueOrIdsParam: jvm.Param[jvm.Type],
      rowType: jvm.Type
  ) extends Selector("selectByFieldValues")

  case class UpdateBuilder(
      relName: db.RelationName,
      fieldsType: jvm.Type,
      rowType: jvm.Type
  ) extends Mutator("update", 2)

  case class UpdateFieldValues(
      relName: db.RelationName,
      id: IdComputed,
      varargs: jvm.Param[jvm.Type],
      fieldValueType: jvm.Type.Qualified,
      cases: NonEmptyList[ComputedColumn],
      rowType: jvm.Type
  ) extends Mutator("updateFieldValues")

  case class Update(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      id: IdComputed,
      param: jvm.Param[jvm.Type],
      writeableColumnsNotId: NonEmptyList[ComputedColumn]
  ) extends Mutator("update", 1)

  case class Upsert(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      id: IdComputed,
      unsavedParam: jvm.Param[jvm.Type],
      rowType: jvm.Type,
      writeableColumnsWithId: NonEmptyList[ComputedColumn]
  ) extends Mutator("upsert")

  case class UpsertBatch(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      id: IdComputed,
      rowType: jvm.Type,
      writeableColumnsWithId: NonEmptyList[ComputedColumn]
  ) extends Mutator("upsertBatch")

  case class UpsertStreaming(
      relName: db.RelationName,
      id: IdComputed,
      rowType: jvm.Type,
      writeableColumnsWithId: NonEmptyList[ComputedColumn]
  ) extends Mutator("upsertStreaming") {
    override val comment = Comments(List("NOTE: this functionality is not safe if you use auto-commit mode! it runs 3 SQL statements"))
    override def requiresStreamingSupport: Boolean = true // Uses PostgreSQL-specific temp table + COPY syntax
  }

  case class Insert(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      maybeId: Option[IdComputed],
      unsavedParam: jvm.Param[jvm.Type],
      writeableColumnsWithId: NonEmptyList[ComputedColumn],
      returningStrategy: ReturningStrategy
  ) extends Mutator("insert", 2)

  case class InsertUnsaved(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      unsaved: ComputedRowUnsaved,
      unsavedParam: jvm.Param[jvm.Type],
      maybeId: Option[IdComputed],
      default: ComputedDefault,
      returningStrategy: ReturningStrategy
  ) extends Mutator("insert", 1)

  case class InsertStreaming(
      relName: db.RelationName,
      rowType: jvm.Type,
      writeableColumnsWithId: NonEmptyList[ComputedColumn]
  ) extends Mutator("insertStreaming") {
    override def requiresStreamingSupport: Boolean = true // Uses PostgreSQL COPY command
  }

  case class InsertUnsavedStreaming(
      relName: db.RelationName,
      unsaved: ComputedRowUnsaved
  ) extends Mutator("insertUnsavedStreaming") {
    override val comment = Comments(List("NOTE: this functionality requires PostgreSQL 16 or later!"))
    override def requiresStreamingSupport: Boolean = true // Uses PostgreSQL COPY command with DEFAULT
  }

  case class Delete(
      relName: db.RelationName,
      id: IdComputed
  ) extends Mutator("deleteById")

  case class DeleteByIds(
      relName: db.RelationName,
      id: IdComputed,
      idsParam: jvm.Param[jvm.Type]
  ) extends Mutator("deleteByIds")

  case class DeleteBuilder(
      relName: db.RelationName,
      fieldsType: jvm.Type,
      rowType: jvm.Type
  ) extends Mutator("delete")

  case class SqlFile(sqlFile: ComputedSqlFile) extends RepoMethod("apply", 0)

  implicit val ordering: Ordering[RepoMethod] = Ordering.by(x => (x.methodName, -x.tiebreaker))
}
