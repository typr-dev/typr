package typr.runtime;

import java.util.Optional;
import java.util.function.Function;
import typr.dsl.Bijection;

/**
 * Combines MariaDB type name, read, write, text encoding, and JSON encoding for a type. Similar to
 * PgType but for MariaDB.
 */
public record MariaType<A>(
    MariaTypename<A> typename,
    MariaRead<A> read,
    MariaWrite<A> write,
    MariaText<A> mariaText,
    MariaJson<A> mariaJson)
    implements DbType<A> {
  @Override
  public DbText<A> text() {
    return mariaText;
  }

  @Override
  public DbJson<A> json() {
    return mariaJson;
  }

  public Fragment.Value<A> encode(A value) {
    return new Fragment.Value<>(value, this);
  }

  public MariaType<A> withTypename(MariaTypename<A> typename) {
    return new MariaType<>(typename, read, write, mariaText, mariaJson);
  }

  public MariaType<A> withTypename(String sqlType) {
    return withTypename(MariaTypename.of(sqlType));
  }

  public MariaType<A> renamed(String value) {
    return withTypename(typename.renamed(value));
  }

  public MariaType<A> renamedDropPrecision(String value) {
    return withTypename(typename.renamedDropPrecision(value));
  }

  public MariaType<A> withRead(MariaRead<A> read) {
    return new MariaType<>(typename, read, write, mariaText, mariaJson);
  }

  public MariaType<A> withWrite(MariaWrite<A> write) {
    return new MariaType<>(typename, read, write, mariaText, mariaJson);
  }

  public MariaType<A> withText(MariaText<A> text) {
    return new MariaType<>(typename, read, write, text, mariaJson);
  }

  public MariaType<A> withJson(MariaJson<A> json) {
    return new MariaType<>(typename, read, write, mariaText, json);
  }

  public MariaType<Optional<A>> opt() {
    return new MariaType<>(
        typename.opt(), read.opt(), write.opt(typename), mariaText.opt(), mariaJson.opt());
  }

  public <B> MariaType<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    return new MariaType<>(
        typename.as(),
        read.map(f),
        write.contramap(g),
        mariaText.contramap(g),
        mariaJson.bimap(f, g));
  }

  public <B> MariaType<B> to(Bijection<A, B> bijection) {
    return new MariaType<>(
        typename.as(),
        read.map(bijection::underlying),
        write.contramap(bijection::from),
        mariaText.contramap(bijection::from),
        mariaJson.bimap(bijection::underlying, bijection::from));
  }

  public static <A> MariaType<A> of(
      String tpe, MariaRead<A> r, MariaWrite<A> w, MariaText<A> t, MariaJson<A> j) {
    return new MariaType<>(MariaTypename.of(tpe), r, w, t, j);
  }

  public static <A> MariaType<A> of(
      MariaTypename<A> typename, MariaRead<A> r, MariaWrite<A> w, MariaText<A> t, MariaJson<A> j) {
    return new MariaType<>(typename, r, w, t, j);
  }
}
