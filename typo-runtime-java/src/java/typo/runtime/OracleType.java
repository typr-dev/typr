package typo.runtime;

import java.util.Optional;
import java.util.function.Function;
import typo.dsl.Bijection;

/**
 * Combines Oracle type name, read, write, and JSON encoding for a type. Similar to PgType/MariaType
 * but for Oracle. Note: Oracle doesn't use text-based streaming inserts (like PostgreSQL's COPY),
 * so there is no OracleText component.
 */
public record OracleType<A>(
    OracleTypename<A> typename, OracleRead<A> read, OracleWrite<A> write, OracleJson<A> oracleJson)
    implements DbType<A> {
  @Override
  public DbText<A> text() {
    throw new UnsupportedOperationException(
        "Oracle doesn't support text-based streaming inserts. Use batch operations instead.");
  }

  @Override
  public DbJson<A> json() {
    return oracleJson;
  }

  public Fragment.Value<A> encode(A value) {
    return new Fragment.Value<>(value, this);
  }

  public OracleType<A> withTypename(OracleTypename<A> typename) {
    return new OracleType<>(typename, read, write, oracleJson);
  }

  public OracleType<A> withTypename(String sqlType) {
    return withTypename(OracleTypename.of(sqlType));
  }

  public OracleType<A> renamed(String value) {
    return withTypename(typename.renamed(value));
  }

  public OracleType<A> renamedDropPrecision(String value) {
    return withTypename(typename.renamedDropPrecision(value));
  }

  public OracleType<A> withRead(OracleRead<A> read) {
    return new OracleType<>(typename, read, write, oracleJson);
  }

  public OracleType<A> withWrite(OracleWrite<A> write) {
    return new OracleType<>(typename, read, write, oracleJson);
  }

  public OracleType<A> withJson(OracleJson<A> json) {
    return new OracleType<>(typename, read, write, json);
  }

  public OracleType<Optional<A>> opt() {
    return new OracleType<>(typename.opt(), read.opt(), write.opt(typename), oracleJson.opt());
  }

  @Override
  public <B> OracleType<B> to(Bijection<A, B> bijection) {
    return new OracleType<>(
        typename.as(),
        read.map(bijection::underlying),
        write.contramap(bijection::from),
        oracleJson.bimap(bijection::underlying, bijection::from));
  }

  public <B> OracleType<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    return new OracleType<>(typename.as(), read.map(f), write.contramap(g), oracleJson.bimap(f, g));
  }

  public static <A> OracleType<A> of(
      String tpe, OracleRead<A> r, OracleWrite<A> w, OracleJson<A> j) {
    return new OracleType<>(OracleTypename.of(tpe), r, w, j);
  }

  public static <A> OracleType<A> of(
      OracleTypename<A> typename, OracleRead<A> r, OracleWrite<A> w, OracleJson<A> j) {
    return new OracleType<>(typename, r, w, j);
  }
}
