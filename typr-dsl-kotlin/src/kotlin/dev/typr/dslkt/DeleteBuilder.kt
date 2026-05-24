package dev.typr.dslkt

import dev.typr.dsl.DeleteBuilder as JavaDeleteBuilder
import dev.typr.dsl.Structure
import dev.typr.dsl.Dialect
import dev.typr.foundationskt.Fragment
import dev.typr.foundationskt.Connection

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
    fun where(predicate: SqlExprExtensions.(Fields) -> SqlExpr<Boolean>): DeleteBuilder<Fields, Row> {
        return DeleteBuilder(javaBuilder.where { fields -> SqlExprExtensionsInstance.predicate(fields).underlying })
    }

    /**
     * Execute the delete and return the number of affected rows.
     */
    fun execute(connection: Connection): Int {
        return javaBuilder.execute(connection.javaConnection)
    }

    /**
     * Execute the delete and return the deleted rows (using RETURNING clause).
     */
    fun executeReturning(connection: Connection, parser: dev.typr.foundationskt.ResultSetParser<List<Row>>): List<Row> {
        return javaBuilder.executeReturning(connection.javaConnection, parser.underlying).toList()
    }

    /**
     * Get the SQL for debugging purposes. Returns null if backed by a mock repository.
     */
    fun sql(): Fragment? {
        return javaBuilder.sql().map { Fragment(it) }.orElse(null)
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
