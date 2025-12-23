package typr.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * A MariaRead implementation that reads nullable values as Kotlin nulls instead of Optional. This
 * bridges between Java's Optional<A> (used internally) and Kotlin's A? (nullable types).
 *
 * <p>This class implements DbRead.Nullable marker interface to signal to RowParser.opt() that this
 * column is already nullable.
 */
public final class KotlinNullableMariaRead<A> implements MariaRead<A>, DbRead.Nullable {
  private final MariaRead<Optional<A>> underlyingReader;

  public KotlinNullableMariaRead(MariaRead<Optional<A>> underlyingReader) {
    this.underlyingReader = underlyingReader;
  }

  @Override
  public A read(ResultSet rs, int col) throws SQLException {
    Optional<A> optional = underlyingReader.read(rs, col);
    return optional.orElse(null);
  }

  @Override
  public <B> MariaRead<B> map(SqlFunction<A, B> f) {
    // Map the nullable value to a new type
    // The result is also nullable
    return new KotlinNullableMariaRead<>(
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
  public MariaRead<Optional<A>> opt() {
    // Wrapping a nullable in Optional - just return the underlying reader
    return underlyingReader;
  }
}
