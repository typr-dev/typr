package typr.dsl;

import java.util.concurrent.atomic.AtomicInteger;
import typr.runtime.Fragment;

/** Represents a sort order for a SQL expression. */
public record SortOrder<T>(SqlExpr<T> expr, boolean ascending, boolean nullsFirst) {

  public SortOrder<T> withNullsFirst() {
    return new SortOrder<>(expr, ascending, true);
  }

  public SortOrder<T> withNullsLast() {
    return new SortOrder<>(expr, ascending, false);
  }

  public SortOrder<T> asc() {
    return new SortOrder<>(expr, true, nullsFirst);
  }

  public SortOrder<T> desc() {
    return new SortOrder<>(expr, false, nullsFirst);
  }

  public Fragment render(RenderCtx ctx, AtomicInteger counter) {
    Fragment result = expr.render(ctx, counter);
    result = result.append(Fragment.lit(" ")).append(Fragment.lit(ascending ? "ASC" : "DESC"));
    if (nullsFirst) {
      result = result.append(Fragment.lit(" NULLS FIRST"));
    } else if (!ascending) {
      // PostgreSQL default is NULLS LAST for ASC, NULLS FIRST for DESC
      // So we need to explicitly specify NULLS LAST for DESC if nullsFirst is false
      result = result.append(Fragment.lit(" NULLS LAST"));
    }
    return result;
  }

  // Factory methods for common use cases
  public static <T> SortOrder<T> asc(SqlExpr<T> expr) {
    return new SortOrder<>(expr, true, false);
  }

  public static <T> SortOrder<T> desc(SqlExpr<T> expr) {
    return new SortOrder<>(expr, false, true);
  }
}
