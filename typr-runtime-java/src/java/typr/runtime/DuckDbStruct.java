package typr.runtime;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import typr.data.JsonValue;

/**
 * DuckDB STRUCT type support.
 *
 * <p>A STRUCT is an ordered sequence of named fields with typed values. Example: STRUCT(name
 * VARCHAR, age INTEGER)
 *
 * <p>In Java, we represent a STRUCT as a generated record class with typed fields. This class
 * provides the machinery to read/write STRUCTs via JDBC.
 *
 * @param <A> the Java type representing this STRUCT (typically a generated record)
 */
public record DuckDbStruct<A>(
    DuckDbTypename.StructOf<A> typename,
    List<Field<A, ?>> fields,
    StructReader<A> reader,
    StructWriter<A> writer,
    DuckDbJson<A> json) {
  /**
   * A single field in a STRUCT with a getter function.
   *
   * @param <A> the struct type
   * @param <F> the field value type
   */
  public record Field<A, F>(String name, DuckDbType<F> type, Function<A, F> getter) {}

  /** Functional interface for reading a STRUCT from field values. */
  @FunctionalInterface
  public interface StructReader<A> {
    A read(Object[] fieldValues) throws SQLException;
  }

  /** Functional interface for writing a STRUCT to field values. */
  @FunctionalInterface
  public interface StructWriter<A> {
    Object[] write(A value);
  }

  /** Create a DuckDbType for this STRUCT. */
  public DuckDbType<A> asType() {
    DuckDbRead<A> duckDbRead =
        DuckDbRead.of(
            (rs, idx) -> {
              Object obj = rs.getObject(idx);
              if (obj == null) return null;
              if (obj instanceof java.sql.Struct struct) {
                Object[] attrs = struct.getAttributes();
                return reader.read(attrs);
              }
              throw new SQLException("Expected STRUCT, got: " + obj.getClass());
            });

    DuckDbWrite<A> duckDbWrite =
        new DuckDbWrite.Instance<>(
            (ps, idx, str) -> ps.setString(idx, str),
            value -> {
              // Write as string literal: {'field1': value1, 'field2': value2}
              StringBuilder sb = new StringBuilder("{");
              for (int i = 0; i < fields.size(); i++) {
                if (i > 0) sb.append(", ");
                Field<A, ?> field = fields.get(i);
                sb.append("'").append(field.name()).append("': ");
                appendFieldValue(sb, value, field);
              }
              sb.append("}");
              return sb.toString();
            });

    DuckDbStringifier<A> stringifier =
        DuckDbStringifier.instance(
            (value, sb, quoted) -> {
              sb.append("{");
              for (int i = 0; i < fields.size(); i++) {
                if (i > 0) sb.append(", ");
                Field<A, ?> field = fields.get(i);
                sb.append("'").append(field.name()).append("': ");
                appendFieldValue(sb, value, field);
              }
              sb.append("}");
            });

    return new DuckDbType<>(typename.asGeneric(), duckDbRead, duckDbWrite, stringifier, json);
  }

  /** Create an optional version of this STRUCT type. */
  public DuckDbType<Optional<A>> asOptType() {
    return asType().opt();
  }

  /** Append a field value in DuckDB literal format using DuckDbStringifier. */
  private <F> void appendFieldValue(StringBuilder sb, A structValue, Field<A, F> field) {
    F value = field.getter().apply(structValue);
    if (value == null) {
      sb.append("NULL");
      return;
    }
    field.type().stringifier().unsafeEncode(value, sb, true);
  }

  // ========================================================================
  // Builder API for creating STRUCT types
  // ========================================================================

  /**
   * Create a STRUCT type builder.
   *
   * @param <A> the struct type (typically a record)
   */
  public static <A> Builder<A> builder(String structName) {
    return new Builder<>(structName);
  }

  public static class Builder<A> {
    private final String structName;
    private final java.util.List<Field<A, ?>> fields = new java.util.ArrayList<>();

    Builder(String structName) {
      this.structName = structName;
    }

    /**
     * Add a field with a getter function. DuckDbStringifier is automatically derived from the
     * DuckDbType.
     *
     * @param name the field name in SQL
     * @param type the DuckDbType for the field (contains DuckDbStringifier)
     * @param getter function to extract field value from struct
     */
    public <F> Builder<A> field(String name, DuckDbType<F> type, Function<A, F> getter) {
      fields.add(new Field<>(name, type, getter));
      return this;
    }

    /**
     * Build the DuckDbStruct with auto-derived writer and JSON codec.
     *
     * <p>This method auto-generates: - Writer: uses getters to extract field values - JSON:
     * standard format {"field1": value1, "field2": value2}
     *
     * @param reader function to construct struct from field values array
     */
    public DuckDbStruct<A> build(StructReader<A> reader) {
      List<DuckDbTypename.StructOf.StructField> typenameFields =
          fields.stream()
              .map(f -> new DuckDbTypename.StructOf.StructField(f.name(), f.type().typename()))
              .toList();

      DuckDbTypename.StructOf<A> typename =
          new DuckDbTypename.StructOf<>(structName, typenameFields);

      // Auto-derive writer from getters
      StructWriter<A> writer =
          structValue -> {
            Object[] values = new Object[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
              values[i] = extractFieldValue(fields.get(i), structValue);
            }
            return values;
          };

      // Auto-derive JSON codec from fields
      DuckDbJson<A> json =
          new DuckDbJson<>() {
            @Override
            public JsonValue toJson(A value) {
              LinkedHashMap<String, JsonValue> jsonFields = new LinkedHashMap<>();
              for (Field<A, ?> field : fields) {
                jsonFields.put(field.name(), fieldToJson(field, value));
              }
              return new JsonValue.JObject(jsonFields);
            }

            @Override
            public A fromJson(JsonValue jsonValue) {
              if (jsonValue instanceof JsonValue.JObject obj) {
                Object[] values = new Object[fields.size()];
                for (int i = 0; i < fields.size(); i++) {
                  Field<A, ?> field = fields.get(i);
                  JsonValue fieldJson = obj.fields().get(field.name());
                  values[i] = fieldFromJson(field, fieldJson);
                }
                try {
                  return reader.read(values);
                } catch (SQLException e) {
                  throw new RuntimeException("Failed to construct struct from JSON", e);
                }
              }
              throw new IllegalArgumentException("Expected JSON object");
            }
          };

      return new DuckDbStruct<>(typename, List.copyOf(fields), reader, writer, json);
    }

    @SuppressWarnings("unchecked")
    private <F> Object extractFieldValue(Field<A, F> field, A structValue) {
      return field.getter().apply(structValue);
    }

    @SuppressWarnings("unchecked")
    private <F> JsonValue fieldToJson(Field<A, F> field, A structValue) {
      F value = field.getter().apply(structValue);
      return field.type().duckDbJson().toJson(value);
    }

    @SuppressWarnings("unchecked")
    private <F> Object fieldFromJson(Field<A, F> field, JsonValue jsonValue) {
      return field.type().duckDbJson().fromJson(jsonValue);
    }

    /**
     * Build with custom reader, writer, and JSON codec. Use this when auto-derivation doesn't work
     * for your use case.
     */
    public DuckDbStruct<A> build(
        StructReader<A> reader, StructWriter<A> writer, DuckDbJson<A> json) {
      List<DuckDbTypename.StructOf.StructField> typenameFields =
          fields.stream()
              .map(f -> new DuckDbTypename.StructOf.StructField(f.name(), f.type().typename()))
              .toList();

      DuckDbTypename.StructOf<A> typename =
          new DuckDbTypename.StructOf<>(structName, typenameFields);

      return new DuckDbStruct<>(typename, List.copyOf(fields), reader, writer, json);
    }
  }
}
