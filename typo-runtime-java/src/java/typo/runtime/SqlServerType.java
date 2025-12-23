package typo.runtime;

import java.util.Optional;
import java.util.function.Function;
import typo.dsl.Bijection;

/**
 * Combines SQL Server type name, read, write, text encoding, and JSON encoding for a type. Similar
 * to MariaType but for SQL Server.
 */
public record SqlServerType<A>(
    SqlServerTypename<A> typename,
    SqlServerRead<A> read,
    SqlServerWrite<A> write,
    SqlServerText<A> sqlServerText,
    SqlServerJson<A> sqlServerJson)
    implements DbType<A> {

  @Override
  public DbText<A> text() {
    return sqlServerText;
  }

  @Override
  public DbJson<A> json() {
    return sqlServerJson;
  }

  public SqlServerType<A> withTypename(SqlServerTypename<A> typename) {
    return new SqlServerType<>(typename, read, write, sqlServerText, sqlServerJson);
  }

  public SqlServerType<A> withTypename(String sqlType) {
    return withTypename(SqlServerTypename.of(sqlType));
  }

  public SqlServerType<A> renamed(String value) {
    return withTypename(typename.renamed(value));
  }

  public SqlServerType<A> renamedDropPrecision(String value) {
    return withTypename(typename.renamedDropPrecision(value));
  }

  public SqlServerType<A> withRead(SqlServerRead<A> read) {
    return new SqlServerType<>(typename, read, write, sqlServerText, sqlServerJson);
  }

  public SqlServerType<A> withWrite(SqlServerWrite<A> write) {
    return new SqlServerType<>(typename, read, write, sqlServerText, sqlServerJson);
  }

  public SqlServerType<A> withText(SqlServerText<A> text) {
    return new SqlServerType<>(typename, read, write, text, sqlServerJson);
  }

  public SqlServerType<A> withJson(SqlServerJson<A> json) {
    return new SqlServerType<>(typename, read, write, sqlServerText, json);
  }

  public SqlServerType<Optional<A>> opt() {
    return new SqlServerType<>(
        typename.opt(), read.opt(), write.opt(typename), sqlServerText.opt(), sqlServerJson.opt());
  }

  public <B> SqlServerType<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    return new SqlServerType<>(
        typename.as(),
        read.map(f),
        write.contramap(g),
        sqlServerText.contramap(g),
        sqlServerJson.bimap(f, g));
  }

  public <B> SqlServerType<B> to(Bijection<A, B> bijection) {
    return new SqlServerType<>(
        typename.as(),
        read.map(bijection::underlying),
        write.contramap(bijection::from),
        sqlServerText.contramap(bijection::from),
        sqlServerJson.bimap(bijection::underlying, bijection::from));
  }

  public static <A> SqlServerType<A> of(
      String tpe, SqlServerRead<A> r, SqlServerWrite<A> w, SqlServerText<A> t, SqlServerJson<A> j) {
    return new SqlServerType<>(SqlServerTypename.of(tpe), r, w, t, j);
  }

  public static <A> SqlServerType<A> of(
      SqlServerTypename<A> typename,
      SqlServerRead<A> r,
      SqlServerWrite<A> w,
      SqlServerText<A> t,
      SqlServerJson<A> j) {
    return new SqlServerType<>(typename, r, w, t, j);
  }
}
