package typr.dsl;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import typr.runtime.Fragment;
import typr.runtime.ResultSetParser;

/** Builder for SQL DELETE queries with type-safe operations. */
public interface DeleteBuilder<Fields, Row> {

  /** Create a DeleteBuilder for a table. */
  static <Fields, Row> DeleteBuilder<Fields, Row> of(
      String tableName, RelationStructure<Fields, Row> structure, Dialect dialect) {
    return new DeleteBuilderSql<>(
        tableName, RenderCtx.of(dialect), structure, DeleteParams.empty());
  }

  /** Add a WHERE clause to the delete. Consecutive calls will be combined with AND. */
  DeleteBuilder<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate);

  /** Execute the delete and return the number of affected rows. */
  int execute(Connection connection);

  /** Execute the delete and return the deleted rows (using RETURNING clause). */
  List<Row> executeReturning(Connection connection, ResultSetParser<List<Row>> parser);

  /** Get the SQL for debugging purposes. Returns empty if backed by a mock repository. */
  Optional<Fragment> sql();
}
