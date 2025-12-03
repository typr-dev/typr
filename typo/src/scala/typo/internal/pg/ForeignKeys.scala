package typo
package internal
package pg

import typo.generated.information_schema.key_column_usage.KeyColumnUsageViewRow
import typo.generated.information_schema.referential_constraints.ReferentialConstraintsViewRow
import typo.generated.information_schema.table_constraints.TableConstraintsViewRow

object ForeignKeys {
  def apply(
      tableConstraints: List[TableConstraintsViewRow],
      keyColumnUsage: List[KeyColumnUsageViewRow],
      referentialConstraints: List[ReferentialConstraintsViewRow]
  ): Map[db.RelationName, List[db.ForeignKey]] = {

    def getReferringColumns(fk: TableConstraintsViewRow): List[db.ColName] = {
      val kcus =
        keyColumnUsage.filter { kcu =>
          fk.constraintCatalog == kcu.constraintCatalog &&
          fk.constraintSchema == kcu.constraintSchema &&
          fk.constraintName == kcu.constraintName
        }

      kcus
        .sortBy(_.positionInUniqueConstraint)
        .map(kcu => db.ColName(kcu.columnName.get))
    }

    def getReferredTable(fk: TableConstraintsViewRow): Option[db.RelationName] = {
      referentialConstraints
        .find { rc =>
          fk.constraintCatalog == rc.constraintCatalog &&
          fk.constraintSchema == rc.constraintSchema &&
          fk.constraintName == rc.constraintName
        }
        .flatMap { rc =>
          tableConstraints.find { tc =>
            tc.constraintCatalog == rc.uniqueConstraintCatalog &&
            tc.constraintSchema == rc.uniqueConstraintSchema &&
            tc.constraintName == rc.uniqueConstraintName
          }
        }
        .map { tc =>
          db.RelationName(tc.tableSchema, tc.tableName.get)
        }
    }

    def getReferredColumns(fk: TableConstraintsViewRow): List[db.ColName] = {
      referentialConstraints
        .filter { rc =>
          fk.constraintCatalog == rc.constraintCatalog &&
          fk.constraintSchema == rc.constraintSchema &&
          fk.constraintName == rc.constraintName
        }
        .flatMap { rc =>
          keyColumnUsage
            .filter { kcu =>
              kcu.constraintCatalog == rc.uniqueConstraintCatalog &&
              kcu.constraintSchema == rc.uniqueConstraintSchema &&
              kcu.constraintName == rc.uniqueConstraintName
            }
            .sortBy(_.positionInUniqueConstraint)
            .map(kcu => db.ColName(kcu.columnName.get))
        }
    }

    tableConstraints
      .filter(_.constraintType.contains("FOREIGN KEY"))
      .map { fk =>
        (
          db.RelationName(fk.tableSchema, fk.tableName.get),
          getReferringColumns(fk),
          getReferredTable(fk),
          getReferredColumns(fk),
          db.RelationName(fk.constraintSchema, fk.constraintName.get)
        )
      }
      .collect { case (tableName, referringColumns, Some(referredTable), referredColumns, constraintName) =>
        for {
          referringColumns <- NonEmptyList.fromList(referringColumns)
          referredColumns <- NonEmptyList.fromList(referredColumns)
        } yield tableName -> db.ForeignKey(referringColumns, referredTable, referredColumns, constraintName)
      }
      .flatten
      .groupBy { case (k, _) => k }
      .map { case (k, v) => k -> v.map(_._2) }

  }

}
