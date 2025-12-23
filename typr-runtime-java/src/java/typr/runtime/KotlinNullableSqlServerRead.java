package typr.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * A SqlServerRead implementation that reads nullable values as Kotlin nulls instead of Optional.
 * This bridges between Java's Optional<A> (used internally) and Kotlin's A? (nullable types).
 *
 * <p>This class implements DbRead.Nullable marker interface to signal to RowParser.opt() that this
 * column is already nullable.
 */
public final class KotlinNullableSqlServerRead<A> implements SqlServerRead<A>, DbRead.Nullable {
  private final SqlServerRead<Optional<A>> underlyingReader;

  public KotlinNullableSqlServerRead(SqlServerRead<Optional<A>> underlyingReader) {
    this.underlyingReader = underlyingReader;
  }

  @Override
  public A read(ResultSet rs, int col) throws SQLException {
    Optional<A> optional = underlyingReader.read(rs, col);
    return optional.orElse(null);
  }

  @Override
  public <B> SqlServerRead<B> map(SqlFunction<A, B> f) {
    // Map the nullable value to a new type
    // The result is also nullable
    return new KotlinNullableSqlServerRead<>(
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
  public SqlServerRead<Optional<A>> opt() {
    // Wrapping a nullable in Optional - just return the underlying reader
    return underlyingReader;
  }
}
