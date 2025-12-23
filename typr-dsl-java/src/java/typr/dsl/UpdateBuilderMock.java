package typr.dsl;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import typr.runtime.DbType;
import typr.runtime.Either;
import typr.runtime.Fragment;

/** Mock implementation of UpdateBuilder for testing without a database. */
public class UpdateBuilderMock<Fields, Row> implements UpdateBuilder<Fields, Row> {
  private final Structure<Fields, Row> structure;
  private final Supplier<List<Row>> allRowsSupplier;
  private final UpdateParams<Fields, Row> params;
  private final Function<Row, Row> copyRow; // Function to create a copy of a row

  public UpdateBuilderMock(
      Structure<Fields, Row> structure,
      Supplier<List<Row>> allRowsSupplier,
      UpdateParams<Fields, Row> params,
      Function<Row, Row> copyRow) {
    this.structure = structure;
    this.allRowsSupplier = allRowsSupplier;
    this.params = params;
    this.copyRow = copyRow;
  }

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
    return new UpdateBuilderMock<>(structure, allRowsSupplier, newParams, copyRow);
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
    return new UpdateBuilderMock<>(structure, allRowsSupplier, newParams, copyRow);
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
    return new UpdateBuilderMock<>(structure, allRowsSupplier, newParams, copyRow);
  }

  @Override
  public UpdateBuilder<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate) {
    UpdateParams<Fields, Row> newParams = params.where(predicate);
    return new UpdateBuilderMock<>(structure, allRowsSupplier, newParams, copyRow);
  }

  @Override
  public int execute(Connection connection) {
    List<Row> allRows = new ArrayList<>(allRowsSupplier.get());
    Fields fields = structure.fields();
    int updated = 0;

    for (int i = 0; i < allRows.size(); i++) {
      Row row = allRows.get(i);

      // Check WHERE clauses
      boolean matches = true;
      for (Function<Fields, SqlExpr<Boolean>> whereFunc : params.where()) {
        SqlExpr<Boolean> condition = whereFunc.apply(fields);
        Boolean result = structure.untypedEval(condition, row).orElse(false);
        if (!result) {
          matches = false;
          break;
        }
      }

      if (matches) {
        // Apply updates by folding over setters
        Row updatedRow = row;
        for (UpdateParams.Setter<Fields, ?, Row> setter : params.setters()) {
          updatedRow = applySetter(setter, fields, updatedRow);
        }

        allRows.set(i, updatedRow);
        updated++;
      }
    }

    return updated;
  }

  @Override
  public List<Row> executeReturning(Connection connection) {
    List<Row> allRows = new ArrayList<>(allRowsSupplier.get());
    Fields fields = structure.fields();
    List<Row> updatedRows = new ArrayList<>();

    for (int i = 0; i < allRows.size(); i++) {
      Row row = allRows.get(i);

      // Check WHERE clauses
      boolean matches = true;
      for (Function<Fields, SqlExpr<Boolean>> whereFunc : params.where()) {
        SqlExpr<Boolean> condition = whereFunc.apply(fields);
        Boolean result = structure.untypedEval(condition, row).orElse(false);
        if (!result) {
          matches = false;
          break;
        }
      }

      if (matches) {
        // Apply updates by folding over setters
        Row updatedRow = row;
        for (UpdateParams.Setter<Fields, ?, Row> setter : params.setters()) {
          updatedRow = applySetter(setter, fields, updatedRow);
        }

        allRows.set(i, updatedRow);
        updatedRows.add(updatedRow);
      }
    }

    return updatedRows;
  }

  @Override
  public Optional<Fragment> sql() {
    return Optional.empty(); // Mock doesn't generate SQL
  }

  /**
   * Apply a setter to update a field value. Uses the generated field accessor's set method - no
   * reflection needed! This follows the same pattern as the Scala implementation.
   */
  private <T> Row applySetter(UpdateParams.Setter<Fields, T, Row> setter, Fields fields, Row row) {
    SqlExpr.FieldLikeNotId<T, Row> field = setter.column().apply(fields);
    SqlExpr<T> valueExpr = setter.value().apply(fields);

    // Evaluate the new value
    Optional<T> newValue = structure.untypedEval(valueExpr, row);

    // Use the generated field's set method - this is the key insight!
    // Each generated field has a set method that knows how to create a copy
    // of the row with the updated value
    Either<String, Row> result = field.set(row, newValue);

    return result.fold(
        error -> {
          throw new RuntimeException("Failed to set field '" + field.column() + "': " + error);
        },
        updatedRow -> updatedRow);
  }
}
