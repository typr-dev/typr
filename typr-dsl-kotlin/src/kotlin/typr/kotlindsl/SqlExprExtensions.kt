package typr.kotlindsl

import typr.dsl.Bijection
import typr.dsl.SqlExpr

/**
 * Kotlin extension methods for SqlExpr that eliminate the need for explicit Bijection parameters
 * in common cases. These extensions use inline reified types to provide type safety while
 * maintaining a clean API.
 */

// ================================
// Boolean Operations
// ================================

/**
 * Logical AND for boolean expressions.
 * Extension method that eliminates the need to pass Bijection.asBool() explicitly.
 */
infix fun SqlExpr<Boolean>.and(other: SqlExpr<Boolean>): SqlExpr<Boolean> {
    return this.and(other, Bijection.asBool())
}

/**
 * Logical OR for boolean expressions.
 * Extension method that eliminates the need to pass Bijection.asBool() explicitly.
 */
infix fun SqlExpr<Boolean>.or(other: SqlExpr<Boolean>): SqlExpr<Boolean> {
    return this.or(other, Bijection.asBool())
}

/**
 * Logical NOT for boolean expressions.
 * Extension method that eliminates the need to pass Bijection.asBool() explicitly.
 */
fun SqlExpr<Boolean>.not(): SqlExpr<Boolean> {
    return this.not(Bijection.asBool())
}

// ================================
// String Operations
// ================================

/**
 * SQL LIKE pattern matching for string expressions.
 * Extension method that eliminates the need to pass Bijection.identity() explicitly.
 */
fun SqlExpr<String>.like(pattern: String): SqlExpr<Boolean> {
    return this.like(pattern, Bijection.identity())
}

/**
 * String concatenation (SQL || operator).
 * Extension method that eliminates the need to pass Bijection.identity() explicitly.
 */
infix fun SqlExpr<String>.stringAppend(other: SqlExpr<String>): SqlExpr<String> {
    return this.stringAppend(other, Bijection.identity())
}

/**
 * Convert string to lowercase.
 * Extension method that eliminates the need to pass Bijection.identity() explicitly.
 */
fun SqlExpr<String>.lower(): SqlExpr<String> {
    return this.lower(Bijection.identity())
}

/**
 * Convert string to uppercase.
 * Extension method that eliminates the need to pass Bijection.identity() explicitly.
 */
fun SqlExpr<String>.upper(): SqlExpr<String> {
    return this.upper(Bijection.identity())
}

/**
 * Reverse a string.
 * Extension method that eliminates the need to pass Bijection.identity() explicitly.
 */
fun SqlExpr<String>.reverse(): SqlExpr<String> {
    return this.reverse(Bijection.identity())
}

/**
 * Find position of substring in string (1-based, returns 0 if not found).
 * Extension method that eliminates the need to pass Bijection.identity() explicitly.
 */
fun SqlExpr<String>.strpos(substring: SqlExpr<String>): SqlExpr<Int> {
    return this.strpos(substring, Bijection.identity())
}

/**
 * Get length of string.
 * Extension method that eliminates the need to pass Bijection.identity() explicitly.
 */
fun SqlExpr<String>.strLength(): SqlExpr<Int> {
    return this.strLength(Bijection.identity())
}

/**
 * Extract substring from string.
 * Extension method that eliminates the need to pass Bijection.identity() explicitly.
 * @param from 1-based start position
 * @param count Number of characters to extract
 */
fun SqlExpr<String>.substring(from: SqlExpr<Int>, count: SqlExpr<Int>): SqlExpr<String> {
    return this.substring(from, count, Bijection.identity())
}

