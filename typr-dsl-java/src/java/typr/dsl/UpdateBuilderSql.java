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
import typr.runtime.DbType;
import typr.runtime.Fragment;
import typr.runtime.RowParser;

/** SQL implementation of UpdateBuilder that generates and executes UPDATE queries. */
public record UpdateBuilderSql<Fields, Row>(
    String tableName,
    RenderCtx renderCtx,
    Structure<Fields, Row> structure,
    UpdateParams<Fields, Row> params,
    RowParser<Row> rowParser)
    implements UpdateBuilder<Fields, Row> {

  @Override
  public <T> UpdateBuilder<Fields, Row> set(
      Function<Fields, SqlExpr.FieldLike<T, Row>> field, T value, DbType<T> dbType) {
    // Wrap the function to extract the field from potentially typed field
    Function<Fields, SqlExpr.FieldLikeNotId<T, Row>> fieldNotId =
        fields -> {
          SqlExpr.FieldLike<T, Row> f = field.apply(fields);
          if (f instanceof SqlExpr.FieldLikeNotId<T, Row> notId) {
            return notId;
          }
          throw new IllegalArgumentException("Cannot update ID fields");
        };
    UpdateParams<Fields, Row> newParams = params.set(fieldNotId, value, dbType);
    return new UpdateBuilderSql<>(tableName, renderCtx, structure, newParams, rowParser);
  }

  @Override
  public <T> UpdateBuilder<Fields, Row> setExpr(
      Function<Fields, SqlExpr.FieldLike<T, Row>> field, SqlExpr<T> expr) {
    // Wrap the function to extract the field from potentially typed field
    Function<Fields, SqlExpr.FieldLikeNotId<T, Row>> fieldNotId =
        fields -> {
          SqlExpr.FieldLike<T, Row> f = field.apply(fields);
          if (f instanceof SqlExpr.FieldLikeNotId<T, Row> notId) {
            return notId;
          }
          throw new IllegalArgumentException("Cannot update ID fields");
        };
    UpdateParams<Fields, Row> newParams = params.set(fieldNotId, fields -> expr);
    return new UpdateBuilderSql<>(tableName, renderCtx, structure, newParams, rowParser);
  }

  @Override
  public <T> UpdateBuilder<Fields, Row> setComputedValue(
      Function<Fields, SqlExpr.FieldLike<T, Row>> field,
      Function<SqlExpr.FieldLike<T, Row>, SqlExpr<T>> compute) {
    // Wrap the function to extract the field from potentially typed field
    Function<Fields, SqlExpr.FieldLikeNotId<T, Row>> fieldNotId =
        fields -> {
          SqlExpr.FieldLike<T, Row> f = field.apply(fields);
          if (f instanceof SqlExpr.FieldLikeNotId<T, Row> notId) {
            return notId;
          }
          throw new IllegalArgumentException("Cannot update ID fields");
        };
    // The compute function receives the field and returns the expression
    UpdateParams<Fields, Row> newParams =
        params.set(
            fieldNotId,
            fields -> {
              SqlExpr.FieldLike<T, Row> fieldExpr = field.apply(fields);
              return compute.apply(fieldExpr);
            });
    return new UpdateBuilderSql<>(tableName, renderCtx, structure, newParams, rowParser);
  }

  @Override
  public UpdateBuilder<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate) {
    UpdateParams<Fields, Row> newParams = params.where(predicate);
    return new UpdateBuilderSql<>(tableName, renderCtx, structure, newParams, rowParser);
  }

  @Override
  public int execute(Connection connection) {
    Fragment query = mkSql();
    try (PreparedStatement ps = connection.prepareStatement(query.render())) {
      query.set(ps);
      return ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to execute update: " + query.render(), e);
    }
  }

  @Override
  public List<Row> executeReturning(Connection connection) {
    Fragment query = mkSql().append(Fragment.lit(" RETURNING *"));
    try (PreparedStatement ps = connection.prepareStatement(query.render())) {
      query.set(ps);
      try (ResultSet rs = ps.executeQuery()) {
        List<Row> results = rowParser.all().apply(rs);
        return results;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to execute update returning: " + query.render(), e);
    }
  }

  @Override
  public Optional<Fragment> sql() {
    return Optional.of(mkSql());
  }

  private Fragment mkSql() {
    if (params.setters().isEmpty()) {
      throw new IllegalStateException("Cannot update without any SET clauses");
    }

    AtomicInteger counter = new AtomicInteger(0);
    Fields fields = structure.fields();

    // Build UPDATE clause
    Fragment update = Fragment.lit("UPDATE " + tableName + " SET ");

    // Build SET clauses
    List<Fragment> setFragments = new ArrayList<>();
    Dialect dialect = renderCtx.dialect();
    for (UpdateParams.Setter<Fields, ?, Row> setter : params.setters()) {
      SqlExpr.FieldLikeNotId<?, Row> column = setter.column().apply(fields);
      SqlExpr<?> value = setter.value().apply(fields);

      // Apply sqlWriteCast if present (like Scala DSL does)
      // For PostgreSQL: value::type
      // For MariaDB: CAST(value AS type)
      Fragment valueFragment = value.render(renderCtx, counter);
      Fragment castedValue =
          column
              .sqlWriteCast()
              .<Fragment>map(cast -> dialect.typeCast(valueFragment, cast))
              .orElse(valueFragment);

      Fragment setFragment =
          column.render(renderCtx, counter).append(Fragment.lit(" = ")).append(castedValue);
      setFragments.add(setFragment);
    }

    update = update.append(Fragment.comma(setFragments));

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

      update = update.append(Fragment.lit(" WHERE ")).append(combined.render(renderCtx, counter));
    }

    return update;
  }
}
