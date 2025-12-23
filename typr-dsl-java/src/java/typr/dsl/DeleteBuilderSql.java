package typr.dsl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import typr.runtime.Fragment;
import typr.runtime.ResultSetParser;

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
    Fragment query = mkSql();
    try (PreparedStatement ps = connection.prepareStatement(query.render())) {
      query.set(ps);
      return ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to execute delete: " + query.render(), e);
    }
  }

  @Override
  public List<Row> executeReturning(Connection connection, ResultSetParser<List<Row>> parser) {
    Fragment query = mkSql().append(Fragment.lit(" RETURNING *"));
    try (PreparedStatement ps = connection.prepareStatement(query.render())) {
      query.set(ps);
      try (ResultSet rs = ps.executeQuery()) {
        return parser.apply(rs);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to execute delete returning: " + query.render(), e);
    }
  }

  @Override
  public Optional<Fragment> sql() {
    return Optional.of(mkSql());
  }

  private Fragment mkSql() {
    AtomicInteger counter = new AtomicInteger(0);
    Fields fields = structure.fields();

    // Build DELETE clause
    Fragment delete = Fragment.lit("DELETE FROM " + tableName);

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

      delete = delete.append(Fragment.lit(" WHERE ")).append(combined.render(renderCtx, counter));
    }

    return delete;
  }
}
