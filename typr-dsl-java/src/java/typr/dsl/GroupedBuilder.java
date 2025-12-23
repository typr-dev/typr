package typr.dsl;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import typr.runtime.Fragment;

/**
 * Builder for queries with GROUP BY.
 *
 * <p>Use SqlExpr.count(), SqlExpr.sum(), etc. for aggregate functions. Non-aggregated columns in
 * the select should be from the GROUP BY key.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // Count persons per department
 * var results = personRepo.select()
 *     .where(p -> p.active())
 *     .groupBy(p -> p.department())
 *     .select(p -> Tuples.of(
 *         p.department(),
 *         SqlExpr.count()
 *     ))
 *     .toList(conn);
 * // Type: List<Tuple2<Department, Long>>
 *
 * // Department stats with HAVING
 * var largeDepts = personRepo.select()
 *     .groupBy(p -> p.department())
 *     .having(p -> SqlExpr.count().greaterThan(5L))
 *     .select(p -> Tuples.of(
 *         p.department(),
 *         SqlExpr.count(),
 *         SqlExpr.avg(p.salary())
 *     ))
 *     .toList(conn);
 * }</pre>
 *
 * @param <Fields> The field accessor type from the original query
 * @param <Row> The row type from the original query
 */
public interface GroupedBuilder<Fields, Row> {

  /**
   * Add a HAVING predicate to filter groups. The predicate can use aggregate functions.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .having(p -> SqlExpr.count().greaterThan(5L))
   * .having(p -> SqlExpr.avg(p.salary()).greaterThan(50000.0))
   * }</pre>
   *
   * @param predicate a function that takes the fields and returns a boolean expression
   * @return a new GroupedBuilder with the HAVING predicate added
   */
  GroupedBuilder<Fields, Row> having(Function<Fields, SqlExpr<Boolean>> predicate);

  /**
   * Project the grouped result to specific columns. Use SqlExpr.count(), SqlExpr.sum(), etc. for
   * aggregates. Non-aggregated columns should be from the GROUP BY key.
   *
   * <p>Example:
   *
   * <pre>{@code
   * .select(p -> Tuples.of(
   *     p.department(),    // GROUP BY column
   *     SqlExpr.count(),   // Aggregate
   *     SqlExpr.max(p.salary())  // Aggregate
   * ))
   * }</pre>
   *
   * @param projection function that creates the projection expressions
   * @param <NewFields> the type of the projected expressions (a TupleExpr)
   * @param <NewRow> the type of the projected row (a Tuple)
   * @return a SelectBuilder for the grouped and projected query
   */
  <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
      SelectBuilder<NewFields, NewRow> select(Function<Fields, NewFields> projection);

  /**
   * Execute the grouped query and return results. Shorthand for .select(projection).toList(conn)
   *
   * @param conn database connection
   * @param projection function that creates the projection expressions
   * @param <NewFields> the type of the projected expressions
   * @param <NewRow> the type of the projected row
   * @return list of grouped results
   */
  default <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
      List<NewRow> toList(Connection conn, Function<Fields, NewFields> projection) {
    return select(projection).toList(conn);
  }

  /** Return the SQL for debugging. Empty if backed by a mock repository. */
  Optional<Fragment> sql();
}
