package typr.kotlindsl

import typr.dsl.Bijection
import java.util.Optional

/**
 * Generic extension methods for converting between Java Optional and Kotlin nullable types.
 * These work at the VALUE level, not the type parameter level.
 */

// ================================
// Optional<T> → T?
// ================================

/**
 * Convert Optional<T> to T? (nullable).
 * Usage: val user: User? = optionalUser.orNull()
 */
fun <T> Optional<T>.orNull(): T? = this.orElse(null)

// ================================
// T? → Optional<T>
// ================================

/**
 * Convert T? (nullable) to Optional<T>.
 *
 * Note: Type hint needed because Kotlin infers Optional<T & Any> (non-null inside Optional)
 * but Java expects Optional<T> (platform type). This is safe - just helping type inference.
 *
 * Usage: val optional: Optional<User> = nullableUser.toOptional()
 */
fun <T> T?.toOptional(): Optional<T> {
    @Suppress("UNCHECKED_CAST")
    return Optional.ofNullable(this) as Optional<T>
}

// ================================
// Bijection: Optional<T> ↔ T?
// ================================

/**
 * Bijection between Java Optional<T> and Kotlin nullable T?.
 * Used for type-safe phantom type conversion in PgTypename/MariaTypename.
 *
 * Usage:
 *   val typename: PgTypename<String?> = pgType.opt().typename().to(optionalToNullable())
 */
fun <T> optionalToNullable(): Bijection<Optional<T>, T?> {
    @Suppress("UNCHECKED_CAST")
    return Bijection.of(
        { opt: Optional<T> -> opt.orElse(null) },
        { nullable: T? -> Optional.ofNullable(nullable) as Optional<T> }
    )
}

/**
 * Bijection between Kotlin nullable T? and Java Optional<T>.
 * Inverse of optionalToNullable().
 */
fun <T> nullableToOptional(): Bijection<T?, Optional<T>> = optionalToNullable<T>().inverse()
