package typo.runtime;

import java.sql.SQLException;

@FunctionalInterface
public interface SqlBiFunction<T, U, R> {
  R apply(T t, U u) throws SQLException;
}
