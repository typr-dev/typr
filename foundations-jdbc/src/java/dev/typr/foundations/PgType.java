package dev.typr.foundations;

import dev.typr.foundations.dsl.Bijection;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

public record PgType<A>(
    PgTypename<A> typename,
    PgRead<A> read,
    PgWrite<A> write,
    PgText<A> pgText,
    PgCompositeText<A> pgCompositeText,
    PgJson<A> pgJson)
    implements DbType<A> {
  @Override
  public DbText<A> text() {
    return pgText;
  }

  @Override
  public DbJson<A> json() {
    return pgJson;
  }

  public Fragment.Value<A> encode(A value) {
    return new Fragment.Value<>(value, this);
  }

  public PgType<A> withTypename(PgTypename<A> typename) {
    return new PgType<>(typename, read, write, pgText, pgCompositeText, pgJson);
  }

  public PgType<A> withTypename(String sqlType) {
    return withTypename(PgTypename.of(sqlType));
  }

  public PgType<A> renamed(String value) {
    return withTypename(typename.renamed(value));
  }

  public PgType<A> renamedDropPrecision(String value) {
    return withTypename(typename.renamedDropPrecision(value));
  }

  public PgType<A> withRead(PgRead<A> read) {
    return new PgType<>(typename, read, write, pgText, pgCompositeText, pgJson);
  }

  public PgType<A> withWrite(PgWrite<A> write) {
    return new PgType<>(typename, read, write, pgText, pgCompositeText, pgJson);
  }

  public PgType<A> withText(PgText<A> text) {
    return new PgType<>(typename, read, write, text, pgCompositeText, pgJson);
  }

  public PgType<A> withCompositeText(PgCompositeText<A> compositeText) {
    return new PgType<>(typename, read, write, pgText, compositeText, pgJson);
  }

  public PgType<A> withJson(PgJson<A> json) {
    return new PgType<>(typename, read, write, pgText, pgCompositeText, json);
  }

  public PgType<Optional<A>> opt() {
    return new PgType<>(
        typename.opt(),
        read.opt(),
        write.opt(typename),
        pgText.opt(),
        pgCompositeText.opt(),
        pgJson.opt());
  }

  public PgType<A[]> array(PgRead<A[]> read, IntFunction<A[]> arrayFactory) {
    return new PgType<>(
        typename.array(),
        read,
        write.array(typename),
        pgText.array(),
        pgCompositeText.array(arrayFactory),
        pgJson.array(arrayFactory));
  }

  /**
   * Create an array type with a custom delimiter for composite text encoding/decoding.
   *
   * <p>PostgreSQL uses semicolon (;) as the delimiter for geometric type arrays (box[], circle[],
   * line[], lseg[], path[], point[], polygon[]) because their elements contain commas.
   */
  public PgType<A[]> array(
      PgRead<A[]> read, IntFunction<A[]> arrayFactory, char compositeTextDelimiter) {
    return new PgType<>(
        typename.array(),
        read,
        write.array(typename),
        pgText.array(),
        pgCompositeText.array(arrayFactory, compositeTextDelimiter),
        pgJson.array(arrayFactory));
  }

  public PgType<A[]> array(PgRead<A[]> read, PgWrite<A[]> write, IntFunction<A[]> arrayFactory) {
    return new PgType<>(
        typename.array(),
        read,
        write,
        pgText.array(),
        pgCompositeText.array(arrayFactory),
        pgJson.array(arrayFactory));
  }

  public <B> PgType<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    return new PgType<>(
        typename.as(),
        read.map(f),
        write.contramap(g),
        pgText.contramap(g),
        pgCompositeText.bimap(
            a -> {
              try {
                return f.apply(a);
              } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
              }
            },
            g),
        pgJson.bimap(f, g));
  }

  public <B> PgType<B> to(Bijection<A, B> bijection) {
    return new PgType<>(
        typename.as(),
        read.map(bijection::underlying),
        write.contramap(bijection::from),
        pgText.contramap(bijection::from),
        pgCompositeText.bimap(bijection::underlying, bijection::from),
        pgJson.bimap(bijection::underlying, bijection::from));
  }

  public static <A> PgType<A> of(
      String tpe, PgRead<A> r, PgWrite<A> w, PgText<A> t, PgCompositeText<A> ct, PgJson<A> j) {
    return new PgType<>(PgTypename.of(tpe), r, w, t, ct, j);
  }

  public static <A> PgType<A> of(
      PgTypename<A> typename,
      PgRead<A> r,
      PgWrite<A> w,
      PgText<A> t,
      PgCompositeText<A> ct,
      PgJson<A> j) {
    return new PgType<>(typename, r, w, t, ct, j);
  }
}
