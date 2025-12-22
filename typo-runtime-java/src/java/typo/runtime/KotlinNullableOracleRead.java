package typo.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * An OracleRead implementation that reads nullable values as Kotlin nulls instead of Optional. This
 * bridges between Java's Optional<A> (used internally) and Kotlin's A? (nullable types).
 *
 * <p>This class implements DbRead.Nullable marker interface to signal to RowParser.opt() that this
 * column is already nullable.
 */
public final class KotlinNullableOracleRead<A> implements OracleRead<A>, DbRead.Nullable {
  private final OracleRead<Optional<A>> underlyingReader;

  public KotlinNullableOracleRead(OracleRead<Optional<A>> underlyingReader) {
    this.underlyingReader = underlyingReader;
  }

  @Override
  public Object extract(ResultSet rs, int col) throws SQLException {
    return underlyingReader.extract(rs, col);
  }

  @Override
  public A transform(Object value) throws SQLException {
    Optional<A> optional = underlyingReader.transform(value);
    return optional.orElse(null);
  }

  @Override
  public A read(ResultSet rs, int col) throws SQLException {
    Optional<A> optional = underlyingReader.read(rs, col);
    return optional.orElse(null);
  }

  @Override
  public <B> OracleRead<B> map(SqlFunction<A, B> f) {
    // Map the nullable value to a new type
    // The result is also nullable
    return new KotlinNullableOracleRead<>(
        underlyingReader.map(
            opt ->
                opt.map(
                    a -> {
                      try {
                        return f.apply(a);
                      } catch (SQLException e) {
                        throw new RuntimeException(e);
                      }
                    })));
  }

  @Override
  public OracleRead<Optional<A>> opt() {
    // Wrapping a nullable in Optional - just return the underlying reader
    return underlyingReader;
  }
}
