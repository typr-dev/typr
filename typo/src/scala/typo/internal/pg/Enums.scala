package typo
package internal
package pg

import typo.generated.custom.enums.EnumsSqlRow

object Enums {
  def apply(pgEnums: List[EnumsSqlRow]): List[db.StringEnum] = {
    pgEnums
      .groupBy(row => db.RelationName(row.enumSchema, row.enumName))
      .flatMap { case (relName, values) =>
        NonEmptyList
          .fromList(values.sortBy(_.enumSortOrder))
          .map(values => db.StringEnum(relName, values.map(_.enumValue)))
      }
      .toList
  }
}
