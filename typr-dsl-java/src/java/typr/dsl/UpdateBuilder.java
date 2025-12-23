package typr.dsl;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import typr.runtime.DbType;
import typr.runtime.Fragment;
import typr.runtime.RowParser;

/** Builder for SQL UPDATE queries with type-safe operations. */
public interface UpdateBuilder<Fields, Row> {

  /** Create an UpdateBuilder for a table. */
  static <Fields, Row> UpdateBuilder<Fields, Row> of(
      String tableName,
      RelationStructure<Fields, Row> structure,
      RowParser<Row> parser,
      Dialect dialect) {
    return new UpdateBuilderSql<>(
        tableName, RenderCtx.of(dialect), structure, UpdateParams.empty(), parser);
  }

  /**
   * Set a field to a new value.
   *
   * @param field The field to update
   * @param value The value to set
   * @param dbType The database type of the value
   */
  <T> UpdateBuilder<Fields, Row> set(
      Function<Fields, SqlExpr.FieldLike<T, Row>> field, T value, DbType<T> dbType);

  /**
   * Set a field to a new value. The dbType is extracted from the field. Convenience method
   * equivalent to setComputedValue with a constant expression.
   *
   * @param field The field to update
   * @param value The value to set
   */
  default <T> UpdateBuilder<Fields, Row> setValue(
      Function<Fields, SqlExpr.FieldLike<T, Row>> field, T value) {
    return setComputedValue(field, f -> new SqlExpr.ConstReq<>(value, f.dbType()));
  }

  /** Set a field using an expression. */
  <T> UpdateBuilder<Fields, Row> setExpr(
      Function<Fields, SqlExpr.FieldLike<T, Row>> field, SqlExpr<T> expr);

  /**
   * Set a field using a computed value based on the current field value. The compute function
   * receives the field expression and returns the new value expression. Example: setComputedValue(p
   * -> p.name(), name -> name.upper(Name.bijection))
   */
  <T> UpdateBuilder<Fields, Row> setComputedValue(
      Function<Fields, SqlExpr.FieldLike<T, Row>> field,
      Function<SqlExpr.FieldLike<T, Row>, SqlExpr<T>> compute);

  /** Add a WHERE clause to the update. Consecutive calls will be combined with AND. */
  UpdateBuilder<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate);

  /** Execute the update and return the number of affected rows. */
  int execute(Connection connection);

  /** Execute the update and return the updated rows (using RETURNING clause). */
  List<Row> executeReturning(Connection connection);

  /** Get the SQL for debugging purposes. Returns empty if backed by a mock repository. */
  Optional<Fragment> sql();
}
