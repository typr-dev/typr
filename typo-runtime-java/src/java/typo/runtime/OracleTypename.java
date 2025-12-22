package typo.runtime;

import java.util.Optional;

/**
 * Represents an Oracle SQL type name with optional precision and scale. Similar to MariaTypename
 * but for Oracle.
 */
public sealed interface OracleTypename<A> extends DbTypename<A> {
  String sqlType();

  /** Oracle doesn't use PostgreSQL-style type casts in SQL. */
  @Override
  default boolean renderTypeCast() {
    return false;
  }

  String sqlTypeNoPrecision();

  OracleTypename<A> renamed(String value);

  OracleTypename<A> renamedDropPrecision(String value);

  default OracleTypename<Optional<A>> opt() {
    return new Opt<>(this);
  }

  default <B> OracleTypename<B> as() {
    return (OracleTypename<B>) this;
  }

  record Base<A>(String sqlType) implements OracleTypename<A> {
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

  record WithPrec<A>(Base<A> of, int precision) implements OracleTypename<A> {
    public String sqlType() {
      return of.sqlType + "(" + precision + ")";
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public OracleTypename<A> renamed(String value) {
      return new WithPrec<>(of.renamed(value), precision);
    }

    @Override
    public OracleTypename<A> renamedDropPrecision(String value) {
      return of.renamed(value);
    }
  }

  record WithPrecScale<A>(Base<A> of, int precision, int scale) implements OracleTypename<A> {
    public String sqlType() {
      return of.sqlType + "(" + precision + "," + scale + ")";
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public OracleTypename<A> renamed(String value) {
      return new WithPrecScale<>(of.renamed(value), precision, scale);
    }

    @Override
    public OracleTypename<A> renamedDropPrecision(String value) {
      return of.renamed(value);
    }
  }

  record Opt<A>(OracleTypename<A> of) implements OracleTypename<Optional<A>> {
    @Override
    public String sqlType() {
      return of.sqlType();
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public OracleTypename<Optional<A>> renamed(String value) {
      return new Opt<>(of.renamed(value));
    }

    @Override
    public OracleTypename<Optional<A>> renamedDropPrecision(String value) {
      return new Opt<>(of.renamedDropPrecision(value));
    }
  }

  /** Typename for Oracle OBJECT types. */
  record ObjectOf<A>(String sqlType) implements OracleTypename<A> {
    @Override
    public String sqlTypeNoPrecision() {
      return sqlType;
    }

    @Override
    public ObjectOf<A> renamed(String value) {
      return new ObjectOf<>(value);
    }

    @Override
    public ObjectOf<A> renamedDropPrecision(String value) {
      return new ObjectOf<>(value);
    }

    public String sqlName() {
      return sqlType;
    }

    public OracleTypename<A> asGeneric() {
      return new Base<>(sqlType);
    }
  }

  static <T> OracleTypename<T> of(String sqlType) {
    return new Base<>(sqlType);
  }

  static <T> ObjectOf<T> objectOf(String sqlType) {
    return new ObjectOf<>(sqlType);
  }

  static <T> OracleTypename<T> of(String sqlType, int precision) {
    return new WithPrec<>(new Base<>(sqlType), precision);
  }

  static <T> OracleTypename<T> of(String sqlType, int precision, int scale) {
    return new WithPrecScale<>(new Base<>(sqlType), precision, scale);
  }
}
