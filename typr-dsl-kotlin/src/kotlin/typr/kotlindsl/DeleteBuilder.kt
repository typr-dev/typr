package typr.kotlindsl

import typr.dsl.DeleteBuilder as JavaDeleteBuilder
import typr.dsl.Structure
import typr.dsl.SqlExpr
import typr.dsl.Dialect
import typr.runtime.Fragment
import java.sql.Connection

/**
 * Kotlin facade for SQL DELETE queries with type-safe operations.
 * Delegates to the Java implementation while providing Kotlin-friendly APIs.
 */
class DeleteBuilder<Fields, Row> internal constructor(
    private val javaBuilder: JavaDeleteBuilder<Fields, Row>
) {

    /**
     * Add a WHERE clause to the delete.
     * Consecutive calls will be combined with AND.
     */
    fun where(predicate: (Fields) -> SqlExpr<Boolean>): DeleteBuilder<Fields, Row> {
        return DeleteBuilder(javaBuilder.where(predicate))
    }

    /**
     * Execute the delete and return the number of affected rows.
     */
    fun execute(connection: Connection): Int {
        return javaBuilder.execute(connection)
    }

    /**
     * Execute the delete and return the deleted rows (using RETURNING clause).
     */
    fun executeReturning(connection: Connection, parser: ResultSetParser<List<Row>>): List<Row> {
        return javaBuilder.executeReturning(connection, parser.underlying).toList()
    }

    /**
     * Get the SQL for debugging purposes. Returns null if backed by a mock repository.
     */
    fun sql(): Fragment? {
        return javaBuilder.sql().orElse(null)
    }

    companion object {
        /**
         * Create a DeleteBuilder for a table.
         */
        fun <Fields, Row> of(
            tableName: String,
            structure: RelationStructure<Fields, Row>,
            dialect: Dialect
        ): DeleteBuilder<Fields, Row> {
            return DeleteBuilder(JavaDeleteBuilder.of(tableName, structure, dialect))
        }
    }
}
