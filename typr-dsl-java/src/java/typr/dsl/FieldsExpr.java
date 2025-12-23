package typr.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import typr.runtime.DbType;
import typr.runtime.Fragment;
import typr.runtime.RowParser;

/**
 * Base interface for generated Fields types that can be used as SqlExpr. This allows passing entire
 * field structures through map() operations.
 *
 * <p>Generated code (e.g., PersonFields) will extend this interface, providing access to all
 * columns as a composite expression.
 *
 * <p>This interface is non-sealed to allow generated code in user packages to extend it while still
 * being part of the SqlExpr hierarchy.
 *
 * @param <Row> The corresponding row type for this fields structure
 */
public non-sealed interface FieldsExpr<Row> extends SqlExpr<Row> {
  /**
   * Returns all field expressions in this fields structure. Used for rendering and projection
   * operations.
   */
  List<SqlExpr.FieldLike<?, Row>> columns();

  /**
   * Returns the row parser for this fields structure. Contains column types, decode, and encode
   * functions.
   */
  RowParser<Row> rowParser();

  /**
   * Returns the database type for this row. Delegates to the first column's dbType - the actual
   * parsing is handled by RowParser.
   */
  @Override
  default DbType<Row> dbType() {
    // Return a composite DbType that uses the RowParser
    return new RowParserDbType<>(rowParser());
  }

  /** Render all columns as a comma-separated list. */
  @Override
  default Fragment render(RenderCtx ctx, AtomicInteger counter) {
    List<Fragment> fragments = new ArrayList<>();
    for (SqlExpr.FieldLike<?, Row> col : columns()) {
      fragments.add(col.render(ctx, counter));
    }
    return Fragment.comma(fragments);
  }

  /**
   * Returns the total number of columns this fields expression produces. Recursively counts columns
   * from nested multi-column expressions.
   */
  @Override
  default int columnCount() {
    int count = 0;
    for (SqlExpr.FieldLike<?, Row> col : columns()) {
      count += col.columnCount();
    }
    return count;
  }

  /**
   * Returns a flattened list of DbTypes for all columns. Recursively flattens nested multi-column
   * expressions.
   */
  @Override
  default List<DbType<?>> flattenedDbTypes() {
    List<DbType<?>> result = new ArrayList<>();
    for (SqlExpr.FieldLike<?, Row> col : columns()) {
      result.addAll(col.flattenedDbTypes());
    }
    return result;
  }
}
