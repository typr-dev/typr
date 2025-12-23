package typr.runtime;

import java.sql.SQLException;

public interface SqlConsumer<T> {
  void apply(T t) throws SQLException;
}
