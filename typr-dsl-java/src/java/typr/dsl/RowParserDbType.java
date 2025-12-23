package typr.dsl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import typr.data.JsonValue;
import typr.runtime.DbJson;
import typr.runtime.DbRead;
import typr.runtime.DbText;
import typr.runtime.DbType;
import typr.runtime.DbTypename;
import typr.runtime.DbWrite;
import typr.runtime.RowParser;
import typr.runtime.SqlFunction;

/**
 * A DbType that wraps a RowParser, allowing multi-column row types to be used as SqlExpr. This
 * enables FieldsExpr to participate in the type system while delegating actual parsing to the
 * existing RowParser infrastructure.
 *
 * @param <Row> The row type
 */
public record RowParserDbType<Row>(RowParser<Row> rowParser) implements DbType<Row> {

  @Override
  public DbTypename<Row> typename() {
    return () -> "ROW";
  }

  @Override
  public DbRead<Row> read() {
    return new RowParserRead<>(rowParser);
  }

  @Override
  public DbWrite<Row> write() {
    return new RowParserWrite<>(rowParser);
  }

  @Override
  public DbText<Row> text() {
    return (value, sb) -> {
      throw new UnsupportedOperationException("Text encoding not supported for row types");
    };
  }

  @Override
  public DbJson<Row> json() {
    return new DbJson<>() {
      @Override
      public JsonValue toJson(Row value) {
        throw new UnsupportedOperationException(
            "JSON encoding not supported for row types via RowParserDbType");
      }

      @Override
      public Row fromJson(JsonValue json) {
        throw new UnsupportedOperationException(
            "JSON decoding not supported for row types via RowParserDbType");
      }
    };
  }

  @Override
  public DbType<Optional<Row>> opt() {
    return new RowParserDbType<>(rowParser.opt());
  }

  @Override
  public <B> DbType<B> to(typr.dsl.Bijection<Row, B> bijection) {
    RowParser<B> transformedParser =
        new RowParser<>(
            rowParser.columns(),
            arr -> bijection.underlying(rowParser.decode().apply(arr)),
            b -> rowParser.encode().apply(bijection.from(b)));
    return new RowParserDbType<>(transformedParser);
  }

  /** DbRead implementation for row parser types. */
  record RowParserRead<Row>(RowParser<Row> rowParser) implements DbRead<Row> {
    @Override
    public Row read(ResultSet rs, int col) throws SQLException {
      var columns = rowParser.columns();
      Object[] values = new Object[columns.size()];
      for (int i = 0; i < columns.size(); i++) {
        values[i] = columns.get(i).read().read(rs, col + i);
      }
      return rowParser.decode().apply(values);
    }

    @Override
    public <B> DbRead<B> map(SqlFunction<Row, B> f) {
      RowParserRead<Row> self = this;
      return new DbRead<B>() {
        @Override
        public B read(ResultSet rs, int col) throws SQLException {
          return f.apply(self.read(rs, col));
        }

        @Override
        public <C> DbRead<C> map(SqlFunction<B, C> g) {
          return self.map((Row r) -> g.apply(f.apply(r)));
        }

        @Override
        public DbRead<Optional<B>> opt() {
          return self.opt()
              .map(
                  opt -> {
                    if (opt.isEmpty()) return Optional.empty();
                    try {
                      return Optional.of(f.apply(opt.get()));
                    } catch (SQLException e) {
                      throw new RuntimeException(e);
                    }
                  });
        }
      };
    }

    @Override
    public DbRead<Optional<Row>> opt() {
      return new RowParserDbType<>(rowParser.opt()).read();
    }
  }

  /** DbWrite implementation for row parser types. */
  @SuppressWarnings("unchecked")
  record RowParserWrite<Row>(RowParser<Row> rowParser) implements DbWrite<Row> {
    @Override
    public void set(PreparedStatement stmt, int col, Row value) throws SQLException {
      var columns = rowParser.columns();
      Object[] values = rowParser.encode().apply(value);
      for (int i = 0; i < columns.size(); i++) {
        ((DbWrite<Object>) columns.get(i).write()).set(stmt, col + i, values[i]);
      }
    }
  }
}
