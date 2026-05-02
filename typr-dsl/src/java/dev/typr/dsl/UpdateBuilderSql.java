package dev.typr.dsl;

import dev.typr.foundations.Bijection;
import dev.typr.foundations.Connection;
import dev.typr.foundations.DbType;
import dev.typr.foundations.Fragment;
import dev.typr.foundations.RowCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** SQL implementation of UpdateBuilder that generates and executes UPDATE queries. */
public record UpdateBuilderSql<Fields, Row>(
    String tableName,
    RenderCtx renderCtx,
    Structure<Fields, Row> structure,
    UpdateParams<Fields, Row> params,
    RowCodec<Row> rowCodec)
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
    return new UpdateBuilderSql<>(tableName, renderCtx, structure, newParams, rowCodec);
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
    return new UpdateBuilderSql<>(tableName, renderCtx, structure, newParams, rowCodec);
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
    return new UpdateBuilderSql<>(tableName, renderCtx, structure, newParams, rowCodec);
  }

  @Override
  public UpdateBuilder<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate) {
    UpdateParams<Fields, Row> newParams = params.where(predicate);
    return new UpdateBuilderSql<>(tableName, renderCtx, structure, newParams, rowCodec);
  }

  @Override
  public int execute(Connection connection) {
    return mkSql().update().run(connection);
  }

  @Override
  public List<Row> executeReturning(Connection connection) {
    return mkSql()
        .append(Fragment.of(" RETURNING *"))
        .updateReturning(rowCodec.all())
        .run(connection);
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
    Fragment update = Fragment.of("UPDATE " + tableName + " SET ");

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
          column.render(renderCtx, counter).append(Fragment.of(" = ")).append(castedValue);
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

      update = update.append(Fragment.of(" WHERE ")).append(combined.render(renderCtx, counter));
    }

    return update;
  }
}
