package typo
package internal
package pg

import typo.generated.information_schema.key_column_usage.KeyColumnUsageViewRow
import typo.generated.information_schema.table_constraints.TableConstraintsViewRow

object PrimaryKeys {
  def apply(tableConstraints: List[TableConstraintsViewRow], keyColumnUsage: List[KeyColumnUsageViewRow]): Map[db.RelationName, db.PrimaryKey] = {
    tableConstraints
      .filter(_.constraintType.contains("PRIMARY KEY"))
      .flatMap { tc =>
        val columns = keyColumnUsage
          .filter(kcu =>
            tc.constraintCatalog == kcu.constraintCatalog
              && tc.constraintSchema == kcu.constraintSchema
              && tc.constraintName == kcu.constraintName
          )
          .sortBy(c => c.positionInUniqueConstraint.orElse(c.ordinalPosition))
          .map(kcu => db.ColName(kcu.columnName.get))
        val relName = db.RelationName(tc.tableSchema, tc.tableName.get)
        val constraintName = db.RelationName(tc.constraintSchema, tc.constraintName.get)
        for {
          columns <- NonEmptyList.fromList(columns)
        } yield (relName, db.PrimaryKey(colNames = columns, constraintName = constraintName))
      }
      .toMap
  }
}
