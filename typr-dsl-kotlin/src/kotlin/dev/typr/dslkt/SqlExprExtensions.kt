package dev.typr.dslkt

import dev.typr.foundations.Bijection

/**
 * Interface providing Kotlin extension methods for SqlExpr that eliminate the need for
 * explicit Bijection parameters. Used as the receiver type for DSL lambdas (where, map, etc.)
 * making these extensions available without explicit imports.
 */
interface SqlExprExtensions {

    // ================================
    // Boolean Operations
    // ================================

    /**
     * Logical AND for boolean expressions.
     */
    infix fun SqlExpr<Boolean>.and(other: SqlExpr<Boolean>): SqlExpr<Boolean> =
        SqlExpr.wrapBool(this.underlying.and(other.underlying, Bijection.asBool()))

    /**
     * Logical OR for boolean expressions.
     */
    infix fun SqlExpr<Boolean>.or(other: SqlExpr<Boolean>): SqlExpr<Boolean> =
        SqlExpr.wrapBool(this.underlying.or(other.underlying, Bijection.asBool()))

    /**
     * Logical NOT for boolean expressions.
     */
    fun SqlExpr<Boolean>.not(): SqlExpr<Boolean> =
        SqlExpr.wrapBool(this.underlying.not(Bijection.asBool()))

    // ================================
    // String Operations
    // ================================

    /**
     * SQL LIKE pattern matching for string expressions.
     */
    fun SqlExpr<String>.like(pattern: String): SqlExpr<Boolean> =
        SqlExpr.wrapBool(this.underlying.like(pattern, Bijection.identity()))

    /**
     * String concatenation (SQL || operator).
     */
    infix fun SqlExpr<String>.stringAppend(other: SqlExpr<String>): SqlExpr<String> =
        SqlExpr.wrap(this.underlying.stringAppend(other.underlying, Bijection.identity()))

    /**
     * Convert string to lowercase.
     */
    fun SqlExpr<String>.lower(): SqlExpr<String> =
        SqlExpr.wrap(this.underlying.lower(Bijection.identity()))

    /**
     * Convert string to uppercase.
     */
    fun SqlExpr<String>.upper(): SqlExpr<String> =
        SqlExpr.wrap(this.underlying.upper(Bijection.identity()))

    /**
     * Reverse a string.
     */
    fun SqlExpr<String>.reverse(): SqlExpr<String> =
        SqlExpr.wrap(this.underlying.reverse(Bijection.identity()))

    /**
     * Find position of substring in string (1-based, returns 0 if not found).
     */
    fun SqlExpr<String>.strpos(substring: SqlExpr<String>): SqlExpr<Int> =
        SqlExpr.wrapInt(this.underlying.strpos(substring.underlying, Bijection.identity()))

    /**
     * Get length of string.
     */
    fun SqlExpr<String>.strLength(): SqlExpr<Int> =
        SqlExpr.wrapInt(this.underlying.strLength(Bijection.identity()))

    /**
     * Extract substring from string.
     * @param from 1-based start position
     * @param count Number of characters to extract
     */
    fun SqlExpr<String>.substring(from: SqlExpr<Int>, count: SqlExpr<Int>): SqlExpr<String> =
        SqlExpr.wrap(this.underlying.substring(from.underlying, count.underlying, Bijection.identity()))
}

/**
 * Singleton instance of SqlExprExtensions for use as lambda receiver.
 */
object SqlExprExtensionsInstance : SqlExprExtensions
