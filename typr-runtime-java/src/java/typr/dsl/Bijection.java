package typr.dsl;

import java.util.function.Function;

/**
 * Represents a bidirectional conversion between two types. This is used for type-safe conversions
 * in the DSL.
 */
public interface Bijection<T, TT> {
  TT underlying(T value);

  T from(TT value);

  default <U> Bijection<T, U> andThen(Bijection<TT, U> other) {
    return new Bijection<T, U>() {
      @Override
      public U underlying(T value) {
        return other.underlying(Bijection.this.underlying(value));
      }

      @Override
      public T from(U value) {
        return Bijection.this.from(other.from(value));
      }
    };
  }

  default Bijection<TT, T> inverse() {
    return new Bijection<TT, T>() {
      @Override
      public T underlying(TT value) {
        return Bijection.this.from(value);
      }

      @Override
      public TT from(T value) {
        return Bijection.this.underlying(value);
      }
    };
  }

  static <T> Bijection<T, T> identity() {
    return new Bijection<T, T>() {
      @Override
      public T underlying(T value) {
        return value;
      }

      @Override
      public T from(T value) {
        return value;
      }
    };
  }

  /**
   * Type witness proving that T is Boolean.
   *
   * <p>This is used for type-safe boolean operations on {@link typr.dsl.SqlExpr}. The method only
   * compiles when called in a context where T = Boolean, providing compile-time type safety.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * SqlExpr<Boolean> expr = field.isEqual(value);
   * SqlExpr<Boolean> negated = expr.not(Bijection.asBool());
   *
   * // This won't compile - String is not Boolean:
   * // SqlExpr<String> strExpr = ...;
   * // strExpr.not(Bijection.asBool()); // compile error!
   * }</pre>
   *
   * @return an identity bijection for Boolean
   */
  static Bijection<Boolean, Boolean> asBool() {
    return identity();
  }

  /**
   * Type witness proving that T is String.
   *
   * <p>This is used for type-safe string operations on {@link typr.dsl.SqlExpr}. The method only
   * compiles when called in a context where T = String, providing compile-time type safety.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * SqlExpr<String> nameField = ...;
   * SqlExpr<Boolean> matched = nameField.like("Jo%", Bijection.asString());
   * SqlExpr<String> upper = nameField.upper(Bijection.asString());
   *
   * // This won't compile - Integer is not String:
   * // SqlExpr<Integer> intExpr = ...;
   * // intExpr.like("Jo%", Bijection.asString()); // compile error!
   * }</pre>
   *
   * @return an identity bijection for String
   */
  static Bijection<String, String> asString() {
    return identity();
  }

  static <T, TT> Bijection<T, TT> of(Function<T, TT> to, Function<TT, T> from) {
    return new Bijection<T, TT>() {
      @Override
      public TT underlying(T value) {
        return to.apply(value);
      }

      @Override
      public T from(TT value) {
        return from.apply(value);
      }
    };
  }

  /**
   * Curried factory method for Scala interop. Scala: Bijection.apply[W, U](_.value)(SomeType.apply)
   */
  static <T, TT> Function<Function<TT, T>, Bijection<T, TT>> apply(Function<T, TT> to) {
    return from -> of(to, from);
  }

  // Common bijections
  static Bijection<Boolean, String> booleanToString() {
    return of(b -> b ? "true" : "false", "true"::equalsIgnoreCase);
  }

  static Bijection<Integer, String> integerToString() {
    return of(Object::toString, Integer::parseInt);
  }

  static Bijection<Long, String> longToString() {
    return of(Object::toString, Long::parseLong);
  }

  static Bijection<Double, String> doubleToString() {
    return of(Object::toString, Double::parseDouble);
  }

  // For use with custom value types
  default <U> Function<T, U> map(Function<TT, U> mapper) {
    return value -> mapper.apply(underlying(value));
  }
}
