package typo.kotlindsl

import typo.dsl.UpdateBuilder as JavaUpdateBuilder
import typo.dsl.Dialect
import typo.runtime.Fragment
import typo.runtime.DbType
import java.sql.Connection

/**
 * Kotlin facade for SQL UPDATE queries with type-safe operations.
 * Delegates to the Java implementation while providing Kotlin-friendly APIs.
 */
class UpdateBuilder<Fields, Row> internal constructor(
    private val javaBuilder: JavaUpdateBuilder<Fields, Row>
) {

    /**
     * Set a field to a new value.
     * @param field The field to update (returns Kotlin FieldLike wrapper)
     * @param value The value to set
     * @param pgType The PostgreSQL type of the value
     */
    fun <T> set(field: (Fields) -> typo.kotlindsl.SqlExpr.FieldLike<T, Row>, value: T, pgType: DbType<T>): UpdateBuilder<Fields, Row> {
        return UpdateBuilder(javaBuilder.set({ fields -> field(fields).underlying }, value, pgType))
    }

    /**
     * Set a field to a new value. The pgType is extracted from the field.
     * Convenience method equivalent to setComputedValue with a constant expression.
     * @param field The field to update (returns Kotlin FieldLike wrapper)
     * @param value The value to set
     */
    fun <T> setValue(field: (Fields) -> typo.kotlindsl.SqlExpr.FieldLike<T, Row>, value: T): UpdateBuilder<Fields, Row> {
        return UpdateBuilder(javaBuilder.setValue({ fields -> field(fields).underlying }, value))
    }

    /**
     * Set a field using an expression.
     */
    fun <T> setExpr(field: (Fields) -> typo.kotlindsl.SqlExpr.FieldLike<T, Row>, expr: typo.dsl.SqlExpr<T>): UpdateBuilder<Fields, Row> {
        return UpdateBuilder(javaBuilder.setExpr({ fields -> field(fields).underlying }, expr))
    }

    /**
     * Set a field using a computed value based on the current field value.
     * The compute function receives the field expression and returns the new value expression.
     * Example: setComputedValue({ p -> p.name() }) { name -> name.upper(Name.bijection) }
     */
    fun <T> setComputedValue(
        field: (Fields) -> typo.kotlindsl.SqlExpr.FieldLike<T, Row>,
        compute: (typo.dsl.SqlExpr.FieldLike<T, Row>) -> typo.dsl.SqlExpr<T>
    ): UpdateBuilder<Fields, Row> {
        return UpdateBuilder(javaBuilder.setComputedValue({ fields -> field(fields).underlying }, compute))
    }

    /**
     * Add a WHERE clause to the update.
     * Consecutive calls will be combined with AND.
     */
    fun where(predicate: (Fields) -> typo.dsl.SqlExpr<Boolean>): UpdateBuilder<Fields, Row> {
        return UpdateBuilder(javaBuilder.where(predicate))
    }

    /**
     * Execute the update and return the number of affected rows.
     */
    fun execute(connection: Connection): Int {
        return javaBuilder.execute(connection)
    }

    /**
     * Execute the update and return the updated rows (using RETURNING clause).
     */
    fun executeReturning(connection: Connection): List<Row> {
        return javaBuilder.executeReturning(connection)
    }

    /**
     * Get the SQL for debugging purposes. Returns null if backed by a mock repository.
     */
    fun sql(): Fragment? {
        return javaBuilder.sql().orElse(null)
    }

    companion object {
        /**
         * Create an UpdateBuilder for a table.
         */
        fun <Fields, Row> of(
            tableName: String,
            structure: RelationStructure<Fields, Row>,
            parser: RowParser<Row>,
            dialect: Dialect
        ): UpdateBuilder<Fields, Row> {
            return UpdateBuilder(JavaUpdateBuilder.of(tableName, structure, parser.underlying, dialect))
        }
    }
}
