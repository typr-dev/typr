package typr.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Parameters for a DELETE query including WHERE clauses. */
public record DeleteParams<Fields>(List<Function<Fields, SqlExpr<Boolean>>> where) {

  public DeleteParams<Fields> where(Function<Fields, SqlExpr<Boolean>> predicate) {
    List<Function<Fields, SqlExpr<Boolean>>> newWhere = new ArrayList<>(where);
    newWhere.add(predicate);
    return new DeleteParams<>(newWhere);
  }

  public static <Fields> DeleteParams<Fields> empty() {
    return new DeleteParams<>(new ArrayList<>());
  }
}
