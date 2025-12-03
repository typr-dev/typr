package typo.runtime;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public record RowParser<Row>(List<DbType<?>> columns,
                             Function<Object[], Row> decode,
                             Function<Row, Object[]> encode) implements RowParsers {
    public Row readRow(ResultSet rs, int rowNum) throws SqlResultParseException {
        Object[] currentRow = new Object[columns.size()];
        for (int colNum = 0; colNum < columns.size(); colNum++) {
            DbType<?> dbType = columns.get(colNum);
            try {
                currentRow[colNum] = dbType.read().read(rs, colNum + 1);
            } catch (Exception e) {
                throw new SqlResultParseException(rowNum, colNum, dbType, e);
            }
        }
        return this.decode().apply(currentRow);
    }

    // Convenience method for compatibility with SelectBuilderSql 
    public Row parse(ResultSet rs) throws SqlResultParseException {
        try {
            return readRow(rs, rs.getRow());
        } catch (SQLException e) {
            throw new SqlResultParseException(0, 0, null, e);
        }
    }

    @SuppressWarnings("unchecked")
    public void writeRow(PreparedStatement stmt, Row row) throws SQLException {
        Object[] values = this.encode().apply(row);
        for (int colNum = 0; colNum < columns.size(); colNum++) {
            DbType<Object> dbType = (DbType<Object>) columns.get(colNum);
            dbType.write().set(stmt, colNum + 1, values[colNum]);
        }
    }

    public static class SqlResultParseException extends SQLException {
        public SqlResultParseException(int row, int column, DbType<?> tpe, Exception cause) {
            super("Error reading or parsing row " + row + ", (1-indexed) column " + column + " from ResultSet." + (tpe != null ? " Expected database type " + tpe.typename().sqlType() : ""), cause);
        }
    }

    /**
     * Returns first row (if any), ignores the rest
     */
    public ResultSetParser<Optional<Row>> first() {
        return new ResultSetParser.First<>(this);
    }

    /**
     * Returns at most one row, fails if there are more
     */
    public ResultSetParser<Optional<Row>> maxOne() {
        return new ResultSetParser.MaxOne<>(this);
    }

    /**
     * Returns exactly one row, fails if there are more or less
     */
    public ResultSetParser<Row> exactlyOne() {
        return new ResultSetParser.ExactlyOne<>(this);
    }

    public ResultSetParser<List<Row>> all() {
        return new ResultSetParser.All<>(this);
    }

    public ResultSetParser<Void> foreach(Consumer<Row> consumer) {
        return new ResultSetParser.Foreach<>(this, consumer);
    }

    /**
     * if all values are `null` / `Optional.empty()` then return empty row.
     * This is used for left joins where all columns from the joined table can be null.
     */
    public RowParser<Optional<Row>> opt() {
        // For opt(), we need to allow nullable reads for all columns
        // because in a left join, all columns from the right table can be null.
        // Track which columns were originally non-nullable so we can unwrap them later.
        List<DbType<?>> optColumns = new ArrayList<>(columns.size());
        boolean[] wasNonNullable = new boolean[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            var dbType = columns.get(i);
            if (dbType.read() instanceof DbRead.Nullable) {
                // Already nullable, keep as-is
                optColumns.add(dbType);
                wasNonNullable[i] = false;
            } else {
                // Make it nullable - this changes the type to Optional<A>
                optColumns.add(dbType.opt());
                wasNonNullable[i] = true;
            }
        }

        Function<Object[], Optional<Row>> decode = values -> {
            var allNull = true;
            for (int i = 0; i < values.length && allNull; i++) {
                switch (values[i]) {
                    case null -> {
                    }
                    case Optional<?> optional -> allNull = optional.isEmpty();
                    default -> allNull = false;
                }
            }
            if (allNull) {
                return Optional.empty();
            }
            // Unwrap Optional values that we wrapped (originally non-nullable columns)
            Object[] unwrapped = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                if (wasNonNullable[i] && values[i] instanceof Optional<?> opt) {
                    unwrapped[i] = opt.orElse(null);
                } else {
                    unwrapped[i] = values[i];
                }
            }
            var row = this.decode.apply(unwrapped);
            return Optional.of(row);
        };
        Function<Optional<Row>, Object[]> encode = row -> {
            if (row.isEmpty()) {
                var none = Optional.empty();
                Object[] ret = new Object[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    ret[i] = none;
                }
                return ret;
            }
            return this.encode.apply(row.get());
        };

        return new RowParser<>(optColumns, decode, encode);
    }

    public <Row2> RowParser<And<Row, Row2>> joined(RowParser<Row2> right) {
        var allColumns = new ArrayList<>(columns);
        allColumns.addAll(right.columns);
        var left = this;
        Function<Object[], And<Row, Row2>> decode = allValues -> {
            Object[] leftValues = new Object[left.columns.size()];
            System.arraycopy(allValues, 0, leftValues, 0, leftValues.length);
            Object[] rightValues = new Object[right.columns.size()];
            System.arraycopy(allValues, leftValues.length, rightValues, 0, right.columns.size());
            return new And<>(left.decode.apply(leftValues), right.decode.apply(rightValues));
        };
        Function<And<Row, Row2>, Object[]> encode = and -> {
            Object[] leftValues = left.encode.apply(and.left());
            Object[] rightValues = right.encode.apply(and.right());
            Object[] allValues = new Object[leftValues.length + rightValues.length];
            System.arraycopy(leftValues, 0, allValues, 0, leftValues.length);
            System.arraycopy(rightValues, 0, allValues, leftValues.length, rightValues.length);
            return allValues;
        };
        return new RowParser<>(allColumns, decode, encode);
    }

    public <Row2> RowParser<And<Row, Optional<Row2>>> leftJoined(RowParser<Row2> other) {
        return joined(other.opt());
    }

    public <Row2> RowParser<And<Optional<Row>, Row2>> rightJoined(RowParser<Row2> other) {
        return opt().joined(other);
    }

    public <Row2> RowParser<And<Optional<Row>, Optional<Row2>>> fullJoined(RowParser<Row2> other) {
        return opt().joined(other.opt());
    }
}
