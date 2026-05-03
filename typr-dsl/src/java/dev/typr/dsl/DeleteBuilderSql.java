package dev.typr.dsl;

import dev.typr.foundations.Bijection;
import dev.typr.foundations.Connection;
import dev.typr.foundations.Fragment;
import dev.typr.foundations.ResultSetParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** SQL implementation of DeleteBuilder that generates and executes DELETE queries. */
public record DeleteBuilderSql<Fields, Row>(
    String tableName,
    RenderCtx renderCtx,
    Structure<Fields, Row> structure,
    DeleteParams<Fields> params)
    implements DeleteBuilder<Fields, Row> {

  @Override
  public DeleteBuilder<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate) {
    DeleteParams<Fields> newParams = params.where(predicate);
    return new DeleteBuilderSql<>(tableName, renderCtx, structure, newParams);
  }

  @Override
  public int execute(Connection connection) {
    return mkSql().update().run(connection);
  }

  @Override
  public List<Row> executeReturning(Connection connection, ResultSetParser<List<Row>> parser) {
    return mkSql().append(Fragment.of(" RETURNING *")).updateReturning(parser).run(connection);
  }

  @Override
  public Optional<Fragment> sql() {
    return Optional.of(mkSql());
  }

  private Fragment mkSql() {
    AtomicInteger counter = new AtomicInteger(0);
    Fields fields = structure.fields();

    // Build DELETE clause
    Fragment delete = Fragment.of("DELETE FROM " + tableName);

    // Build WHERE clause
    if (!params.where().isEmpty()) {
      List<SqlExpr<Boolean>> filters = new ArrayList<>();
      for (Function<Fields, SqlExpr<Boolean>> whereFunc : params.where()) {
        filters.add(whereFunc.apply(fields));
      }

      SqlExpr<Boolean> combined = filters.get(0);
      for (int i = 1; i < filters.size(); i++) {
        combined = combined.and(filters.get(i), Bijection.asBool());
      }

      delete = delete.append(Fragment.of(" WHERE ")).append(combined.render(renderCtx, counter));
    }

    return delete;
  }
}
