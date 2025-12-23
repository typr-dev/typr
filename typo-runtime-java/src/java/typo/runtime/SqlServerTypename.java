package typo.runtime;

import java.util.Optional;
import typo.dsl.Bijection;

/**
 * Represents a SQL Server SQL type name with optional precision. Similar to MariaTypename. SQL
 * Server uses bracket notation [table] for identifiers but standard type casts.
 */
public sealed interface SqlServerTypename<A> extends DbTypename<A> {
  String sqlType();

  /**
   * SQL Server uses CAST() syntax, not PostgreSQL's :: operator. Don't render :: casts in prepared
   * statements.
   */
  @Override
  default boolean renderTypeCast() {
    return false;
  }

  String sqlTypeNoPrecision();

  SqlServerTypename<A> renamed(String value);

  SqlServerTypename<A> renamedDropPrecision(String value);

  default SqlServerTypename<Optional<A>> opt() {
    return new Opt<>(this);
  }

  default <B> SqlServerTypename<B> as() {
    return (SqlServerTypename<B>) this;
  }

  /**
   * Type-safe conversion using a bijection as proof of type relationship. Overrides DbTypename.to()
   * to return SqlServerTypename for better type refinement.
   */
  @Override
  default <B> SqlServerTypename<B> to(Bijection<A, B> bijection) {
    return (SqlServerTypename<B>) this;
  }

  record Base<A>(String sqlType) implements SqlServerTypename<A> {
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

  record WithPrec<A>(Base<A> of, int precision) implements SqlServerTypename<A> {
    public String sqlType() {
      return of.sqlType + "(" + precision + ")";
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public SqlServerTypename<A> renamed(String value) {
      return new WithPrec<>(of.renamed(value), precision);
    }

    @Override
    public SqlServerTypename<A> renamedDropPrecision(String value) {
      return of.renamed(value);
    }
  }

  record WithPrecScale<A>(Base<A> of, int precision, int scale) implements SqlServerTypename<A> {
    public String sqlType() {
      return of.sqlType + "(" + precision + "," + scale + ")";
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public SqlServerTypename<A> renamed(String value) {
      return new WithPrecScale<>(of.renamed(value), precision, scale);
    }

    @Override
    public SqlServerTypename<A> renamedDropPrecision(String value) {
      return of.renamed(value);
    }
  }

  record Opt<A>(SqlServerTypename<A> of) implements SqlServerTypename<Optional<A>> {
    @Override
    public String sqlType() {
      return of.sqlType();
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public SqlServerTypename<Optional<A>> renamed(String value) {
      return new Opt<>(of.renamed(value));
    }

    @Override
    public SqlServerTypename<Optional<A>> renamedDropPrecision(String value) {
      return new Opt<>(of.renamedDropPrecision(value));
    }
  }

  static <T> SqlServerTypename<T> of(String sqlType) {
    return new Base<>(sqlType);
  }

  static <T> SqlServerTypename<T> of(String sqlType, int precision) {
    return new WithPrec<>(new Base<>(sqlType), precision);
  }

  static <T> SqlServerTypename<T> of(String sqlType, int precision, int scale) {
    return new WithPrecScale<>(new Base<>(sqlType), precision, scale);
  }
}
