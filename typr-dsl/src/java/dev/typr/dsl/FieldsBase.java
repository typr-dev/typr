package dev.typr.dsl;

import dev.typr.foundations.RowCodec;
import java.util.List;

/**
 * Base interface for generated Fields types that provides access to columns and row parser. This
 * interface is implemented by all Fields types, whether they extend TupleExprN or not.
 *
 * @param <Row> The corresponding row type for this fields structure
 */
public interface FieldsBase<Row> {
  /** Returns all field expressions in this fields structure. */
  List<SqlExpr.FieldLike<?, Row>> columns();

  /** Returns the row parser for this fields structure. */
  RowCodec<Row> rowCodec();
}
