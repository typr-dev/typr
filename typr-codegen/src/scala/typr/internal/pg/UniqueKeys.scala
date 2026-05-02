package typr
package internal
package pg

import typr.generated.information_schema.key_column_usage.KeyColumnUsageViewRow
import typr.generated.information_schema.table_constraints.TableConstraintsViewRow

object UniqueKeys {
  def apply(tableConstraints: List[TableConstraintsViewRow], keyColumnUsage: List[KeyColumnUsageViewRow]): Map[db.RelationName, List[db.UniqueKey]] = {

    def toUniqueKey(tc: TableConstraintsViewRow): Option[db.UniqueKey] = {
      val columnsInKey: List[db.ColName] =
        keyColumnUsage
          .filter { kcu =>
            kcu.constraintCatalog == tc.constraintCatalog && kcu.constraintSchema == tc.constraintSchema && kcu.constraintName == tc.constraintName
          }
          .sortBy(_.positionInUniqueConstraint)
          .map(kcu => db.ColName(kcu.columnName.get))

      for {
        columnsInKey <- NonEmptyList.fromList(columnsInKey)
      } yield db.UniqueKey(cols = columnsInKey, constraintName = db.RelationName(tc.constraintSchema, tc.constraintName.get))
    }

    val allUniqueConstraintsByTable: Map[db.RelationName, List[TableConstraintsViewRow]] =
      tableConstraints
        .filter(_.constraintType.contains("UNIQUE"))
        .groupBy(uc => db.RelationName(uc.tableSchema, uc.tableName.get))

    allUniqueConstraintsByTable
      .map { case (tableName, tcs) => (tableName, tcs.flatMap(toUniqueKey)) }
  }
}
