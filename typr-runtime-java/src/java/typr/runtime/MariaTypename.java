package typr.runtime;

import java.util.Optional;
import typr.dsl.Bijection;

/**
 * Represents a MariaDB SQL type name with optional precision. Similar to PgTypename but without
 * array support (MariaDB doesn't have array types).
 */
public sealed interface MariaTypename<A> extends DbTypename<A> {
  String sqlType();

  /** MariaDB doesn't use PostgreSQL-style type casts in SQL. */
  @Override
  default boolean renderTypeCast() {
    return false;
  }

  String sqlTypeNoPrecision();

  MariaTypename<A> renamed(String value);

  MariaTypename<A> renamedDropPrecision(String value);

  default MariaTypename<Optional<A>> opt() {
    return new Opt<>(this);
  }

  default <B> MariaTypename<B> as() {
    return (MariaTypename<B>) this;
  }

  /**
   * Type-safe conversion using a bijection as proof of type relationship. Overrides DbTypename.to()
   * to return MariaTypename for better type refinement.
   */
  @Override
  default <B> MariaTypename<B> to(Bijection<A, B> bijection) {
    return (MariaTypename<B>) this;
  }

  record Base<A>(String sqlType) implements MariaTypename<A> {
    @Override
    public String sqlTypeNoPrecision() {
      return sqlType;
    }

    @Override
    public Base<A> renamed(String value) {
      return new Base<>(value);
    }

    @Override
    public Base<A> renamedDropPrecision(String value) {
      return new Base<>(value);
    }
  }

  record WithPrec<A>(Base<A> of, int precision) implements MariaTypename<A> {
    public String sqlType() {
      return of.sqlType + "(" + precision + ")";
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public MariaTypename<A> renamed(String value) {
      return new WithPrec<>(of.renamed(value), precision);
    }

    @Override
    public MariaTypename<A> renamedDropPrecision(String value) {
      return of.renamed(value);
    }
  }

  record WithPrecScale<A>(Base<A> of, int precision, int scale) implements MariaTypename<A> {
    public String sqlType() {
      return of.sqlType + "(" + precision + "," + scale + ")";
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public MariaTypename<A> renamed(String value) {
      return new WithPrecScale<>(of.renamed(value), precision, scale);
    }

    @Override
    public MariaTypename<A> renamedDropPrecision(String value) {
      return of.renamed(value);
    }
  }

  record Opt<A>(MariaTypename<A> of) implements MariaTypename<Optional<A>> {
    @Override
    public String sqlType() {
      return of.sqlType();
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public MariaTypename<Optional<A>> renamed(String value) {
      return new Opt<>(of.renamed(value));
    }

    @Override
    public MariaTypename<Optional<A>> renamedDropPrecision(String value) {
      return new Opt<>(of.renamedDropPrecision(value));
    }
  }

  static <T> MariaTypename<T> of(String sqlType) {
    return new Base<>(sqlType);
  }

  static <T> MariaTypename<T> of(String sqlType, int precision) {
    return new WithPrec<>(new Base<>(sqlType), precision);
  }

  static <T> MariaTypename<T> of(String sqlType, int precision, int scale) {
    return new WithPrecScale<>(new Base<>(sqlType), precision, scale);
  }
}
