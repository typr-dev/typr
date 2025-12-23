package typr.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Represents a foreign key relationship between two tables. */
public record ForeignKey<Fields2, Row2>(String name, List<ColumnPair<?, Fields2>> columnPairs) {

  public <T> ForeignKey<Fields2, Row2> withColumnPair(
      SqlExpr.FieldLike<T, ?> field, Function<Fields2, SqlExpr.FieldLike<T, Row2>> thatField) {
    List<ColumnPair<?, Fields2>> newPairs = new ArrayList<>(columnPairs);
    newPairs.add(new ColumnPair<>(field, thatField::apply));
    return new ForeignKey<>(name, newPairs);
  }

  public static <Fields2, Row2> ForeignKey<Fields2, Row2> of(String name) {
    return new ForeignKey<>(name, new ArrayList<>());
  }

  /** Represents a pair of columns in a foreign key relationship. */
  public record ColumnPair<T, Fields2>(
      SqlExpr<T> thisField, Function<Fields2, SqlExpr<T>> thatField) {}
}
