package typr.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import typr.runtime.DbType;

/** Parameters for an UPDATE query including WHERE clauses and SET operations. */
public record UpdateParams<Fields, Row>(
    List<Function<Fields, SqlExpr<Boolean>>> where, List<Setter<Fields, ?, Row>> setters) {

  public UpdateParams<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate) {
    List<Function<Fields, SqlExpr<Boolean>>> newWhere = new ArrayList<>(where);
    newWhere.add(predicate);
    return new UpdateParams<>(newWhere, setters);
  }

  public <T> UpdateParams<Fields, Row> set(
      Function<Fields, SqlExpr.FieldLikeNotId<T, Row>> column, Function<Fields, SqlExpr<T>> value) {
    List<Setter<Fields, ?, Row>> newSetters = new ArrayList<>(setters);
    newSetters.add(new Setter<>(column, value));
    return new UpdateParams<>(where, newSetters);
  }

  /**
   * Convenience method to set a field to a constant value. The DbType parameter ensures type safety
   * for the value.
   */
  public <T> UpdateParams<Fields, Row> set(
      Function<Fields, SqlExpr.FieldLikeNotId<T, Row>> column, T value, DbType<T> pgType) {
    return set(column, fields -> new SqlExpr.ConstReq<>(value, pgType));
  }

  public static <Fields, Row> UpdateParams<Fields, Row> empty() {
    return new UpdateParams<>(new ArrayList<>(), new ArrayList<>());
  }

  /** Represents a setter operation in an UPDATE statement. */
  public record Setter<Fields, T, Row>(
      Function<Fields, SqlExpr.FieldLikeNotId<T, Row>> column,
      Function<Fields, SqlExpr<T>> value) {}
}
