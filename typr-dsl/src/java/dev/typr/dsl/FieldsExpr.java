package dev.typr.dsl;

import dev.typr.foundations.DbType;
import dev.typr.foundations.Fragment;
import dev.typr.foundations.RowCodec;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Interface for generated Fields expressions that can be used in SQL queries. This interface is
 * used for Scala/Kotlin and for tables with >100 columns (beyond TupleExprN limits).
 *
 * @param <Row> The corresponding row type for this fields structure
 */
public non-sealed interface FieldsExpr<Row> extends SqlExpr<Row>, FieldsBase<Row> {

  /**
   * Returns the row parser for this fields structure. Contains column types, decode, and encode
   * functions.
   */
  RowCodec<Row> rowCodec();

  /**
   * Returns the database type for this row. Delegates to the first column's dbType - the actual
   * parsing is handled by RowCodec.
   */
  @Override
  default RowCodecDbType<Row> dbType() {
    return new RowCodecDbType<>(rowCodec());
  }

  /** Render all columns as a comma-separated list. */
  @Override
  default Fragment render(RenderCtx ctx, AtomicInteger counter) {
    List<Fragment> fragments =
        columns().stream().map(col -> col.render(ctx, counter)).collect(Collectors.toList());
    return Fragment.join(fragments, Fragment.of(", "));
  }

  /**
   * Returns the total number of columns this fields expression produces. Recursively counts columns
   * from nested multi-column expressions.
   */
  @Override
  default int columnCount() {
    return columns().stream().mapToInt(SqlExpr::columnCount).sum();
  }

  /**
   * Returns a flattened list of DbTypes for all columns. Recursively flattens nested multi-column
   * expressions.
   */
  @Override
  default List<DbType<?>> flattenedDbTypes() {
    return columns().stream().flatMap(c -> c.flattenedDbTypes().stream()).toList();
  }

  /** Returns the child expressions of this fields structure. */
  @Override
  default List<SqlExpr<?>> children() {
    return new java.util.ArrayList<>(columns());
  }
}
