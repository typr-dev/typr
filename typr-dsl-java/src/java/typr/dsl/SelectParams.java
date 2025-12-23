package typr.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import typr.runtime.Fragment;

/** Parameters for a SELECT query including WHERE, ORDER BY, OFFSET, and LIMIT clauses. */
public record SelectParams<Fields, Row>(
    List<Function<Fields, SqlExpr<Boolean>>> where,
    List<OrderByOrSeek<Fields, ?>> orderBy,
    Optional<Integer> offset,
    Optional<Integer> limit) {

  public SelectParams<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate) {
    List<Function<Fields, SqlExpr<Boolean>>> newWhere = new ArrayList<>(where);
    newWhere.add(predicate);
    return new SelectParams<>(newWhere, orderBy, offset, limit);
  }

  public <T> SelectParams<Fields, Row> orderBy(Function<Fields, SortOrder<T>> orderFunc) {
    List<OrderByOrSeek<Fields, ?>> newOrderBy = new ArrayList<>(orderBy);
    newOrderBy.add(new OrderByOrSeek.OrderBy<>(orderFunc));
    return new SelectParams<>(where, newOrderBy, offset, limit);
  }

  public <T> SelectParams<Fields, Row> seek(
      Function<Fields, SortOrder<T>> orderFunc, SqlExpr.Const<T> value) {
    List<OrderByOrSeek<Fields, ?>> newOrderBy = new ArrayList<>(orderBy);
    newOrderBy.add(new OrderByOrSeek.Seek<>(orderFunc, value));
    return new SelectParams<>(where, newOrderBy, offset, limit);
  }

  public SelectParams<Fields, Row> offset(int value) {
    return new SelectParams<>(where, orderBy, Optional.of(value), limit);
  }

  public SelectParams<Fields, Row> limit(int value) {
    return new SelectParams<>(where, orderBy, offset, Optional.of(value));
  }

  public static <Fields, Row> SelectParams<Fields, Row> empty() {
    return new SelectParams<>(
        new ArrayList<>(), new ArrayList<>(), Optional.empty(), Optional.empty());
  }

  public static <Fields, Row> Optional<Fragment> render(
      Fields fields, RenderCtx ctx, AtomicInteger counter, SelectParams<Fields, Row> params) {

    var expanded = OrderByOrSeek.expand(fields, params);
    List<SqlExpr<Boolean>> filters = expanded.filters();
    List<SortOrder<?>> orderBys = expanded.orderBys();

    List<Fragment> fragments = new ArrayList<>();

    // WHERE clause
    if (!filters.isEmpty()) {
      SqlExpr<Boolean> combined = filters.getFirst();
      for (int i = 1; i < filters.size(); i++) {
        combined = combined.and(filters.get(i), Bijection.asBool());
      }
      fragments.add(Fragment.lit("where ").append(combined.render(ctx, counter)));
    }

    // ORDER BY clause
    if (!orderBys.isEmpty()) {
      Fragment orderByFrag = Fragment.lit("order by ");
      List<Fragment> orderFragments = new ArrayList<>();
      for (SortOrder<?> order : orderBys) {
        orderFragments.add(order.render(ctx, counter));
      }
      orderByFrag = orderByFrag.append(Fragment.comma(orderFragments));
      fragments.add(orderByFrag);
    }

    // OFFSET clause
    params.offset.ifPresent(value -> fragments.add(Fragment.lit("offset " + value)));

    // LIMIT clause
    params.limit.ifPresent(value -> fragments.add(Fragment.lit("limit " + value)));

    if (fragments.isEmpty()) {
      return Optional.empty();
    }

    Fragment result = fragments.get(0);
    for (int i = 1; i < fragments.size(); i++) {
      result = result.append(Fragment.lit(" ")).append(fragments.get(i));
    }

    return Optional.of(result);
  }
}
