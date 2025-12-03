package typo.dsl;

import typo.runtime.Fragment;
import typo.runtime.ResultSetParser;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builder for SQL DELETE queries with type-safe operations.
 */
public interface DeleteBuilder<Fields, Row> {

    /**
     * Create a DeleteBuilder for a table.
     */
    static <Fields, Row> DeleteBuilder<Fields, Row> of(
            String tableName,
            Structure.Relation<Fields, Row> structure,
            Dialect dialect) {
        return new DeleteBuilderSql<>(tableName, RenderCtx.of(dialect), structure, DeleteParams.empty());
    }

    /**
     * Add a WHERE clause to the delete.
     * Consecutive calls will be combined with AND.
     */
    DeleteBuilder<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate);

    /**
     * Execute the delete and return the number of affected rows.
     */
    int execute(Connection connection);

    /**
     * Execute the delete and return the deleted rows (using RETURNING clause).
     */
    List<Row> executeReturning(Connection connection, ResultSetParser<List<Row>> parser);

    /**
     * Get the SQL for debugging purposes. Returns empty if backed by a mock repository.
     */
    Optional<Fragment> sql();

    /**
     * Mock implementation of DeleteBuilder for testing without a database.
     * @param <Id> The type of the row's ID/key
     * @param <Fields> The fields type for the table
     * @param <Row> The row type
     */
    class DeleteBuilderMock<Id, Fields, Row> implements DeleteBuilder<Fields, Row> {
        private final Structure<Fields, Row> structure;
        private final Supplier<List<Row>> allRowsSupplier;
        private final DeleteParams<Fields> params;
        private final Function<Row, Id> idExtractor;
        private final Consumer<Id> deleteById;

        public DeleteBuilderMock(
                Structure<Fields, Row> structure,
                Supplier<List<Row>> allRowsSupplier,
                DeleteParams<Fields> params,
                Function<Row, Id> idExtractor,
                Consumer<Id> deleteById) {
            this.structure = structure;
            this.allRowsSupplier = allRowsSupplier;
            this.params = params;
            this.idExtractor = idExtractor;
            this.deleteById = deleteById;
        }

        @Override
        public DeleteBuilder<Fields, Row> where(Function<Fields, SqlExpr<Boolean>> predicate) {
            DeleteParams<Fields> newParams = params.where(predicate);
            return new DeleteBuilderMock<>(structure, allRowsSupplier, newParams, idExtractor, deleteById);
        }

        @Override
        public int execute(Connection connection) {
            return executeReturning(connection, null).size();
        }

        @Override
        public List<Row> executeReturning(Connection connection, ResultSetParser<List<Row>> parser) {
            // Note: parser is ignored in mock implementation since we evaluate predicates in memory
            List<Row> allRows = new ArrayList<>(allRowsSupplier.get());
            Fields fields = structure.fields();
            List<Row> deletedRows = new ArrayList<>();

            for (Row row : allRows) {
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
                    deletedRows.add(row);
                    deleteById.accept(idExtractor.apply(row));
                }
            }

            return deletedRows;
        }

        @Override
        public Optional<Fragment> sql() {
            return Optional.empty(); // Mock doesn't generate SQL
        }
    }
}