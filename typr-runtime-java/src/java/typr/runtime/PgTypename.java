package typr.runtime;

import java.util.Optional;
import typr.dsl.Bijection;

public sealed interface PgTypename<A> extends DbTypename<A> {
  String sqlType();

  String sqlTypeNoPrecision();

  PgTypename<A[]> array();

  PgTypename<A> renamed(String value);

  PgTypename<A> renamedDropPrecision(String value);

  default PgTypename<Optional<A>> opt() {
    return new Opt<>(this);
  }

  default <B> PgTypename<B> as() {
    return (PgTypename<B>) this;
  }

  /**
   * Type-safe conversion using a bijection as proof of type relationship. Overrides DbTypename.to()
   * to return PgTypename for better type refinement.
   */
  @Override
  default <B> PgTypename<B> to(Bijection<A, B> bijection) {
    return (PgTypename<B>) this;
  }

  record Base<A>(String sqlType) implements PgTypename<A> {
    @Override
    public String sqlTypeNoPrecision() {
      return sqlType;
    }

    @Override
    public PgTypename<A[]> array() {
      return new ArrayOf<>(this);
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

  record ArrayOf<A>(PgTypename<A> of) implements PgTypename<A[]> {
    @Override
    public String sqlType() {
      return of.sqlType() + "[]";
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision() + "[]";
    }

    @Override
    public PgTypename<A[][]> array() {
      return new ArrayOf<>(this);
    }

    @Override
    public PgTypename<A[]> renamed(String value) {
      return new ArrayOf<>(of.renamed(value));
    }

    @Override
    public PgTypename<A[]> renamedDropPrecision(String value) {
      return new ArrayOf<>(of.renamedDropPrecision(value));
    }
  }

  record WithPrec<A>(Base<A> of, int precision) implements PgTypename<A> {
    public String sqlType() {
      return of.sqlType + "(" + precision + ")";
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public PgTypename<A[]> array() {
      // drops precision
      return new ArrayOf<>(this);
    }

    @Override
    public PgTypename<A> renamed(String value) {
      return new WithPrec<>(of.renamed(value), precision);
    }

    @Override
    public PgTypename<A> renamedDropPrecision(String value) {
      return of.renamed(value);
    }
  }

  record Opt<A>(PgTypename<A> of) implements PgTypename<Optional<A>> {
    @Override
    public String sqlType() {
      return of.sqlType();
    }

    @Override
    public String sqlTypeNoPrecision() {
      return of.sqlTypeNoPrecision();
    }

    @Override
    public PgTypename<Optional<A>[]> array() {
      return new ArrayOf<>(this);
    }

    @Override
    public PgTypename<Optional<A>> renamed(String value) {
      return new Opt<>(of.renamed(value));
    }

    @Override
    public PgTypename<Optional<A>> renamedDropPrecision(String value) {
      return new Opt<>(of.renamedDropPrecision(value));
    }
  }

  static <T> PgTypename<T> of(String sqlType) {
    return new Base<>(sqlType);
  }

  static <T> PgTypename<T> of(String sqlType, int precision) {
    return new WithPrec<>(new Base<>(sqlType), precision);
  }
}
