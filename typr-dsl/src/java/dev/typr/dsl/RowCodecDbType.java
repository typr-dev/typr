package dev.typr.dsl;

import dev.typr.foundations.AnalysisOptions;
import dev.typr.foundations.Bijection;
import dev.typr.foundations.DbJson;
import dev.typr.foundations.DbOutParam;
import dev.typr.foundations.DbRead;
import dev.typr.foundations.DbText;
import dev.typr.foundations.DbType;
import dev.typr.foundations.DbTypename;
import dev.typr.foundations.DbWrite;
import dev.typr.foundations.RowCodec;
import dev.typr.foundations.SqlFunction;
import dev.typr.foundations.data.JsonValue;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * A DbType that wraps a RowCodec, allowing multi-column row types to be used as SqlExpr. This
 * enables FieldsExpr to participate in the type system while delegating actual parsing to the
 * existing RowCodec infrastructure.
 *
 * @param <Row> The row type
 */
public record RowCodecDbType<Row>(RowCodec<Row> rowCodec) implements DbType<Row> {

  @Override
  public DbTypename<Row> typename() {
    return () -> "ROW";
  }

  @Override
  public DbRead<Row> read() {
    return new RowCodecRead<>(rowCodec);
  }

  @Override
  public DbWrite<Row> write() {
    return new RowCodecWrite<>(rowCodec);
  }

  @Override
  public Optional<DbText<Row>> text() {
    return Optional.empty();
  }

  @Override
  public DbJson<Row> json() {
    return new DbJson<>() {
      @Override
      public JsonValue toJson(Row value) {
        throw new UnsupportedOperationException(
            "JSON encoding not supported for row types via RowCodecDbType");
      }

      @Override
      public Row fromJson(JsonValue json) {
        throw new UnsupportedOperationException(
            "JSON decoding not supported for row types via RowCodecDbType");
      }
    };
  }

  @Override
  public Optional<DbOutParam<Row>> outParam() {
    return Optional.empty();
  }

  @Override
  public DbType<Optional<Row>> opt() {
    return new RowCodecDbType<>(rowCodec.opt());
  }

  @Override
  public AnalysisOptions analysisOptions() {
    return AnalysisOptions.EMPTY;
  }

  @Override
  public <B> DbType<B> to(Bijection<Row, B> bijection) {
    RowCodec<B> transformedParser =
        RowCodec.create(
            rowCodec.columns(),
            arr -> bijection.underlying(rowCodec.decode().apply(arr)),
            b -> rowCodec.encode().apply(bijection.from(b)));
    return new RowCodecDbType<>(transformedParser);
  }

  /** DbRead implementation for row codec types. */
  record RowCodecRead<Row>(RowCodec<Row> rowCodec) implements DbRead<Row> {
    @Override
    public Row read(ResultSet rs, int col) throws SQLException {
      var columns = rowCodec.columns();
      Object[] values = new Object[columns.size()];
      for (int i = 0; i < columns.size(); i++) {
        values[i] = columns.get(i).read().read(rs, col + i);
      }
      return rowCodec.decode().apply(values);
    }

    @Override
    public <B> DbRead<B> map(SqlFunction<Row, B> f) {
      RowCodecRead<Row> self = this;
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
      return new RowCodecDbType<>(rowCodec.opt()).read();
    }
  }

  /** DbWrite implementation for row codec types. */
  @SuppressWarnings("unchecked")
  record RowCodecWrite<Row>(RowCodec<Row> rowCodec) implements DbWrite<Row> {
    @Override
    public void set(PreparedStatement stmt, int col, Row value) throws SQLException {
      var columns = rowCodec.columns();
      Object[] values = rowCodec.encode().apply(value);
      for (int i = 0; i < columns.size(); i++) {
        ((DbWrite<Object>) columns.get(i).write()).set(stmt, col + i, values[i]);
      }
    }
  }
}
