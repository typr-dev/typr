package typr
package internal
package sqlfiles

import typr.internal.analysis.{DecomposedSql, JdbcMetadata, NullabilityFromExplain}

case class SqlFile(
    relPath: RelPath,
    decomposedSql: DecomposedSql,
    jdbcMetadata: JdbcMetadata,
    nullableColumnsFromJoins: Option[NullabilityFromExplain.NullableIndices]
)
