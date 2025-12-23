package typr.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Represents either an ORDER BY or a SEEK operation for cursor-based pagination. */
public sealed interface OrderByOrSeek<Fields, T> {
  Function<Fields, SortOrder<T>> f();

  record OrderBy<Fields, T>(Function<Fields, SortOrder<T>> f) implements OrderByOrSeek<Fields, T> {}

  sealed interface SeekNoHkt<Fields, T> extends OrderByOrSeek<Fields, T> {}

  record Seek<Fields, T>(Function<Fields, SortOrder<T>> f, SqlExpr.Const<T> value)
      implements SeekNoHkt<Fields, T> {}

  /** Expands the order by and seek parameters into filters and order by clauses. */
  static <Fields, Row> ExpandResult expand(Fields fields, SelectParams<Fields, Row> params) {
    List<SeekNoHkt<Fields, ?>> seeks =
        params.orderBy().stream()
            .filter(o -> o instanceof SeekNoHkt<?, ?>)
            .map(o -> (SeekNoHkt<Fields, ?>) o)
            .collect(Collectors.toList());

    Optional<SqlExpr<Boolean>> maybeSeekPredicate = Optional.empty();

    if (!seeks.isEmpty()) {
      List<SortOrder<?>> seekOrderBys =
          seeks.stream()
              .map(seek -> ((Seek<Fields, ?>) seek).f().apply(fields))
              .collect(Collectors.toList());

      List<Boolean> ascendings =
          seekOrderBys.stream().map(SortOrder::ascending).distinct().toList();

      if (ascendings.size() == 1) {
        // Uniform ascending/descending - use simple tuple comparison
        boolean uniformIsAscending = ascendings.getFirst();

        List<SqlExpr<?>> dbExprs =
            seekOrderBys.stream().map(SortOrder::expr).collect(Collectors.toList());
        SqlExpr.RowExpr dbTuple = new SqlExpr.RowExpr(dbExprs);

        List<SqlExpr<?>> valueExprs =
            seeks.stream()
                .map(seek -> (SqlExpr<?>) ((Seek<Fields, ?>) seek).value())
                .collect(Collectors.toList());
        SqlExpr.RowExpr valueTuple = new SqlExpr.RowExpr(valueExprs);

        maybeSeekPredicate =
            Optional.of(
                uniformIsAscending
                    ? dbTuple.greaterThan(valueTuple)
                    : dbTuple.lessThan(valueTuple));
      } else {
        // Mixed ascending/descending - build complex OR conditions
        List<SqlExpr<Boolean>> orConditions = new ArrayList<>();

        for (int i = 0; i < seeks.size(); i++) {
          List<SqlExpr<Boolean>> equals = new ArrayList<>();

          // Add equality conditions for all previous columns
          for (int j = 0; j < i; j++) {
            SeekNoHkt<Fields, ?> seek = seeks.get(j);
            equals.add(buildEqualityForWildcard(seek, fields));
          }

          // Add the comparison for the current column
          SeekNoHkt<Fields, ?> currentSeek = seeks.get(i);
          SqlExpr<Boolean> predicate = buildComparisonForWildcard(currentSeek, fields);

          equals.add(predicate);

          // Combine with AND
          SqlExpr<Boolean> combined = equals.getFirst();
          for (int k = 1; k < equals.size(); k++) {
            combined = combined.and(equals.get(k), Bijection.asBool());
          }
          orConditions.add(combined);
        }

        // Combine with OR
        SqlExpr<Boolean> result = orConditions.getFirst();
        for (int i = 1; i < orConditions.size(); i++) {
          result = result.or(orConditions.get(i), Bijection.asBool());
        }
        maybeSeekPredicate = Optional.of(result);
      }
    }

    // Combine WHERE predicates with SEEK predicate
    List<SqlExpr<Boolean>> allFilters = new ArrayList<>();
    for (Function<Fields, SqlExpr<Boolean>> whereFunc : params.where()) {
      allFilters.add(whereFunc.apply(fields));
    }
    maybeSeekPredicate.ifPresent(allFilters::add);

    // Get all ORDER BY clauses
    List<SortOrder<?>> orderBys =
        params.orderBy().stream().map(o -> o.f().apply(fields)).collect(Collectors.toList());

    return new ExpandResult(allFilters, orderBys);
  }

  record ExpandResult(List<SqlExpr<Boolean>> filters, List<SortOrder<?>> orderBys) {
    /**
     * Combine all filters into a single expression using AND. Returns Optional.empty() if there are
     * no filters.
     */
    public Optional<SqlExpr<Boolean>> combinedFilter() {
      if (filters.isEmpty()) {
        return Optional.empty();
      }
      SqlExpr<Boolean> combined = filters.getFirst();
      for (int i = 1; i < filters.size(); i++) {
        combined = combined.and(filters.get(i), Bijection.asBool());
      }
      return Optional.of(combined);
    }
  }

  // Helper methods that work with wildcards by delegating to properly typed methods
  @SuppressWarnings("unchecked")
  private static <Fields> SqlExpr<Boolean> buildEqualityForWildcard(
      SeekNoHkt<Fields, ?> seek, Fields fields) {
    // The cast is safe because we're just comparing for equality, which works for any type
    return buildEqualityCondition((Seek<Fields, Object>) seek, fields);
  }

  @SuppressWarnings("unchecked")
  private static <Fields> SqlExpr<Boolean> buildComparisonForWildcard(
      SeekNoHkt<Fields, ?> seek, Fields fields) {
    // The cast is safe because we're comparing values of the same type from the same Seek
    return buildComparisonCondition((Seek<Fields, Object>) seek, fields);
  }

  private static <T, Fields> SqlExpr<Boolean> buildEqualityCondition(
      Seek<Fields, T> seek, Fields fields) {
    SortOrder<T> so = seek.f().apply(fields);
    SqlExpr<T> expr = so.expr();
    SqlExpr<T> value = seek.value();
    return expr.isEqual(value);
  }

  private static <T, Fields> SqlExpr<Boolean> buildComparisonCondition(
      Seek<Fields, T> seek, Fields fields) {
    SortOrder<T> so = seek.f().apply(fields);
    SqlExpr<T> expr = so.expr();
    SqlExpr<T> value = seek.value();
    return so.ascending() ? expr.greaterThan(value) : expr.lessThan(value);
  }
}
