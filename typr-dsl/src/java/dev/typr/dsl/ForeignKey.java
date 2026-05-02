package dev.typr.dsl;

import dev.typr.foundations.Bijection;
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

  /**
   * Convert this ForeignKey to use different type parameters via bijections. For FK joins where
   * ScalaFields/ScalaRow differ from JavaFields/JavaRow, this converts the FK's type parameters
   * while preserving the column mappings.
   *
   * @param fieldsBij Bijection from NewFields to Fields2
   * @param rowBij Bijection from NewRow to Row2 (unused, for type conversion only)
   * @return ForeignKey with new type parameters
   */
  public <NewFields, NewRow> ForeignKey<NewFields, NewRow> withBijections(
      Bijection<NewFields, Fields2> fieldsBij, Bijection<NewRow, Row2> rowBij) {
    List<ColumnPair<?, NewFields>> newPairs = new ArrayList<>();
    for (ColumnPair<?, Fields2> pair : columnPairs) {
      ColumnPair<?, NewFields> e = convertColumnPair(pair, fieldsBij);
      newPairs.add(e);
    }
    return new ForeignKey<>(name, newPairs);
  }

  /** Static helper to capture the wildcard type T and convert the column pair. */
  private static <T, OldFields, NewFields> ColumnPair<T, NewFields> convertColumnPair(
      ColumnPair<T, OldFields> pair, Bijection<NewFields, OldFields> fieldsBij) {
    return new ColumnPair<>(
        pair.thisField(), newFields -> pair.thatField().apply(fieldsBij.underlying(newFields)));
  }

  /** Represents a pair of columns in a foreign key relationship. */
  public record ColumnPair<T, Fields2>(
      SqlExpr<T> thisField, Function<Fields2, SqlExpr<T>> thatField) {}
}
