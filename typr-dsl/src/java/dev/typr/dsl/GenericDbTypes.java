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
import dev.typr.foundations.data.Json;
import dev.typr.foundations.data.JsonValue;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Database-agnostic DbType implementations for primitive types. These use standard JDBC methods
 * that work identically across all databases, avoiding the need to load database-specific JDBC
 * drivers.
 *
 * <p>Used by aggregate expressions (COUNT, AVG, etc.) and comparison operators that need to return
 * a DbType without knowing which specific database is being used.
 */
public final class GenericDbTypes {

  private GenericDbTypes() {}

  /** Generic boolean type using standard JDBC getBoolean/setBoolean. */
  public static final DbType<Boolean> bool =
      new GenericDbType<>(
          "BOOLEAN",
          (rs, col) -> {
            boolean value = rs.getBoolean(col);
            return rs.wasNull() ? null : value;
          },
          (stmt, col, value) -> stmt.setBoolean(col, value),
          (value, sb) -> sb.append(value ? "true" : "false"),
          value -> JsonValue.JBool.of(value),
          json -> {
            if (json instanceof JsonValue.JBool b) return b.value();
            throw new IllegalArgumentException("Expected boolean JSON value");
          });

  /** Generic 64-bit integer type using standard JDBC getLong/setLong. */
  public static final DbType<Long> int8 =
      new GenericDbType<>(
          "BIGINT",
          (rs, col) -> {
            long value = rs.getLong(col);
            return rs.wasNull() ? null : value;
          },
          (stmt, col, value) -> stmt.setLong(col, value),
          (value, sb) -> sb.append(value),
          value -> JsonValue.JNumber.of(value),
          json -> {
            if (json instanceof JsonValue.JNumber n) return Long.parseLong(n.value());
            throw new IllegalArgumentException("Expected number JSON value");
          });

  /** Generic double precision type using standard JDBC getDouble/setDouble. */
  public static final DbType<Double> float8 =
      new GenericDbType<>(
          "DOUBLE PRECISION",
          (rs, col) -> {
            double value = rs.getDouble(col);
            return rs.wasNull() ? null : value;
          },
          (stmt, col, value) -> stmt.setDouble(col, value),
          (value, sb) -> sb.append(value),
          value -> JsonValue.JNumber.of(value),
          json -> {
            if (json instanceof JsonValue.JNumber n) return Double.parseDouble(n.value());
            throw new IllegalArgumentException("Expected number JSON value");
          });

  /** Generic numeric/decimal type using standard JDBC getBigDecimal/setBigDecimal. */
  public static final DbType<BigDecimal> numeric =
      new GenericDbType<>(
          "NUMERIC",
          ResultSet::getBigDecimal,
          (stmt, col, value) -> stmt.setBigDecimal(col, value),
          (value, sb) -> sb.append(value.toPlainString()),
          value -> JsonValue.JNumber.of(value.toPlainString()),
          json -> {
            if (json instanceof JsonValue.JNumber n) return new BigDecimal(n.value());
            throw new IllegalArgumentException("Expected number JSON value");
          });

  /** Generic text/string type using standard JDBC getString/setString. */
  public static final DbType<String> text =
      new GenericDbType<>(
          "TEXT",
          ResultSet::getString,
          (stmt, col, value) -> stmt.setString(col, value),
          (value, sb) -> {
            // Simple text encoding - escape single quotes
            sb.append('\'');
            for (int i = 0; i < value.length(); i++) {
              char c = value.charAt(i);
              if (c == '\'') sb.append('\'');
              sb.append(c);
            }
            sb.append('\'');
          },
          JsonValue.JString::new,
          json -> {
            if (json instanceof JsonValue.JString s) return s.value();
            throw new IllegalArgumentException("Expected string JSON value");
          });

  /** Generic JSON type - stores as string, parses on read. */
  public static final DbType<Json> json =
      new GenericDbType<>(
          "JSON",
          (rs, col) -> {
            String value = rs.getString(col);
            return value == null ? null : new Json(value);
          },
          (stmt, col, value) -> stmt.setString(col, value.value()),
          (value, sb) -> sb.append(value.value()),
          value -> JsonValue.parse(value.value()),
          jsonValue -> new Json(jsonValue.encode()));

  /** Internal record implementing DbType for generic/database-agnostic types. */
  private record GenericDbType<A>(
      String typeName,
      GenericRead<A> readFn,
      GenericWrite<A> writeFn,
      GenericText<A> textFn,
      GenericToJson<A> toJsonFn,
      GenericFromJson<A> fromJsonFn)
      implements DbType<A> {

    @Override
    public DbTypename<A> typename() {
      return () -> typeName;
    }

    @Override
    public DbRead<A> read() {
      return new DbRead<>() {
        @Override
        public A read(ResultSet rs, int col) throws SQLException {
          return readFn.read(rs, col);
        }

        @Override
        public <B> DbRead<B> map(dev.typr.foundations.SqlFunction<A, B> f) {
          return new DbRead<>() {
            @Override
            public B read(ResultSet rs, int col) throws SQLException {
              A value = readFn.read(rs, col);
              return value == null ? null : f.apply(value);
            }

            @Override
            public <C> DbRead<C> map(dev.typr.foundations.SqlFunction<B, C> g) {
              return GenericDbType.this
                  .read()
                  .map(
                      a -> {
                        B b = f.apply(a);
                        return b == null ? null : g.apply(b);
                      });
            }

            @Override
            public DbRead<Optional<B>> opt() {
              return new DbRead<>() {
                @Override
                public Optional<B> read(ResultSet rs, int col) throws SQLException {
                  A value = readFn.read(rs, col);
                  if (value == null) return Optional.empty();
                  return Optional.ofNullable(f.apply(value));
                }

                @Override
                public <C> DbRead<C> map(dev.typr.foundations.SqlFunction<Optional<B>, C> g) {
                  throw new UnsupportedOperationException();
                }

                @Override
                public DbRead<Optional<Optional<B>>> opt() {
                  throw new UnsupportedOperationException();
                }
              };
            }
          };
        }

        @Override
        public DbRead<Optional<A>> opt() {
          return new DbRead<>() {
            @Override
            public Optional<A> read(ResultSet rs, int col) throws SQLException {
              return Optional.ofNullable(readFn.read(rs, col));
            }

            @Override
            public <B> DbRead<B> map(dev.typr.foundations.SqlFunction<Optional<A>, B> f) {
              throw new UnsupportedOperationException();
            }

            @Override
            public DbRead<Optional<Optional<A>>> opt() {
              throw new UnsupportedOperationException();
            }
          };
        }
      };
    }

    @Override
    public DbWrite<A> write() {
      return (stmt, col, value) -> writeFn.write(stmt, col, value);
    }

    @Override
    public Optional<DbText<A>> text() {
      return Optional.of((value, sb) -> textFn.encode(value, sb));
    }

    @Override
    public DbJson<A> json() {
      return new DbJson<>() {
        @Override
        public JsonValue toJson(A value) {
          return toJsonFn.toJson(value);
        }

        @Override
        public A fromJson(JsonValue json) {
          return fromJsonFn.fromJson(json);
        }
      };
    }

    @Override
    public DbType<Optional<A>> opt() {
      return new GenericDbType<>(
          typeName,
          (rs, col) -> Optional.ofNullable(readFn.read(rs, col)),
          (stmt, col, value) -> {
            if (value.isPresent()) {
              writeFn.write(stmt, col, value.get());
            } else {
              stmt.setNull(col, java.sql.Types.NULL);
            }
          },
          (value, sb) -> {
            if (value.isPresent()) {
              textFn.encode(value.get(), sb);
            } else {
              sb.append("NULL");
            }
          },
          value -> value.map(toJsonFn::toJson).orElse(JsonValue.JNull.INSTANCE),
          json -> {
            if (json instanceof JsonValue.JNull) return Optional.empty();
            return Optional.of(fromJsonFn.fromJson(json));
          });
    }

    @Override
    public Optional<DbOutParam<A>> outParam() {
      return Optional.empty();
    }

    @Override
    public AnalysisOptions analysisOptions() {
      return AnalysisOptions.EMPTY;
    }

    @Override
    public <B> DbType<B> to(Bijection<A, B> bijection) {
      return new GenericDbType<>(
          typeName,
          (rs, col) -> {
            A value = readFn.read(rs, col);
            return value == null ? null : bijection.underlying(value);
          },
          (stmt, col, value) -> writeFn.write(stmt, col, bijection.from(value)),
          (value, sb) -> textFn.encode(bijection.from(value), sb),
          value -> toJsonFn.toJson(bijection.from(value)),
          json -> bijection.underlying(fromJsonFn.fromJson(json)));
    }
  }

  @FunctionalInterface
  private interface GenericRead<A> {
    A read(ResultSet rs, int col) throws SQLException;
  }

  @FunctionalInterface
  private interface GenericWrite<A> {
    void write(PreparedStatement stmt, int col, A value) throws SQLException;
  }

  @FunctionalInterface
  private interface GenericText<A> {
    void encode(A value, StringBuilder sb);
  }

  @FunctionalInterface
  private interface GenericToJson<A> {
    JsonValue toJson(A value);
  }

  @FunctionalInterface
  private interface GenericFromJson<A> {
    A fromJson(JsonValue json);
  }
}
