package dev.typr.foundations;

import dev.typr.foundations.data.JsonValue;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.postgresql.util.PGobject;

/**
 * PostgreSQL composite type (record) support.
 *
 * <p>A composite type is an ordered sequence of named fields with typed values. Example: CREATE
 * TYPE address AS (street VARCHAR, city VARCHAR, zip VARCHAR)
 *
 * <p>In Java, we represent a composite type as a generated record class with typed fields. This
 * class provides the machinery to read/write composite types via JDBC using the PostgreSQL text
 * format.
 *
 * @param <A> the Java type representing this composite (typically a generated record)
 */
public record PgStruct<A>(
    PgTypename.CompositeOf<A> typename,
    List<Field<A, ?>> fields,
    StructReader<A> reader,
    StructWriter<A> writer,
    PgJson<A> json) {

  /**
   * A single field in a composite type.
   *
   * @param <A> the struct type
   * @param <F> the field value type
   */
  public record Field<A, F>(String name, PgType<F> type, Function<A, F> getter) {}

  /** Functional interface for reading a composite from parsed field values. */
  @FunctionalInterface
  public interface StructReader<A> {
    A read(Object[] fieldValues) throws SQLException;
  }

  /** Functional interface for writing a composite to field values. */
  @FunctionalInterface
  public interface StructWriter<A> {
    Object[] write(A value);
  }

  /** Create a PgType for this composite type. */
  public PgType<A> asType() {
    PgRead<A> pgRead =
        PgRead.of(
            (rs, idx) -> {
              Object obj = rs.getObject(idx);
              if (obj == null) return null;
              if (obj instanceof PGobject pgObj) {
                String textValue = pgObj.getValue();
                if (textValue == null) return null;
                return parseFromText(textValue);
              }
              throw new SQLException(
                  "Expected PGobject for composite type, got: " + obj.getClass());
            });

    PgWrite<A> pgWrite =
        new PgWrite.Instance<>(
            (ps, idx, pgObj) -> ps.setObject(idx, pgObj),
            value -> {
              if (value == null) return null;
              PGobject pgObj = new PGobject();
              pgObj.setType(typename.sqlType());
              try {
                pgObj.setValue(encodeToText(value));
              } catch (SQLException e) {
                throw new RuntimeException("Failed to encode composite type", e);
              }
              return pgObj;
            });

    var self = this;
    PgText<A> pgText =
        new PgText<>() {
          @Override
          public void unsafeEncode(A value, StringBuilder sb) {
            sb.append(encodeToText(value));
          }

          @Override
          public void unsafeArrayEncode(A value, StringBuilder sb) {
            // For array encoding, the value needs to be quoted
            unsafeEncode(value, sb);
          }
        };

    PgCompositeText<A> pgCompositeText =
        new PgCompositeText<>() {
          @Override
          public java.util.Optional<String> encode(A value) {
            return java.util.Optional.of(encodeToText(value));
          }

          @Override
          public A decode(String text) {
            try {
              return self.parseFromText(text);
            } catch (SQLException e) {
              throw new RuntimeException("Failed to parse composite type", e);
            }
          }
        };

    return new PgType<>(typename.asGeneric(), pgRead, pgWrite, pgText, pgCompositeText, json);
  }

  /** Create an optional version of this composite type. */
  public PgType<Optional<A>> asOptType() {
    return asType().opt();
  }

  /** Parse a composite value from PostgreSQL text format. */
  private A parseFromText(String text) throws SQLException {
    List<String> parsedFields = PgRecordParser.parse(text);

    if (parsedFields.size() != fields.size()) {
      throw new SQLException(
          "Field count mismatch: expected "
              + fields.size()
              + " but got "
              + parsedFields.size()
              + " in: "
              + text);
    }

    Object[] fieldValues = new Object[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      Field<A, ?> field = fields.get(i);
      String rawValue = parsedFields.get(i);
      fieldValues[i] = parseFieldValue(field, rawValue);
    }

    return reader.read(fieldValues);
  }

  /** Parse a single field value from text. */
  private <F> F parseFieldValue(Field<A, F> field, String rawValue) {
    if (rawValue == null) {
      return null;
    }
    return field.type().pgCompositeText().decode(rawValue);
  }

  /** Encode a composite value to PostgreSQL text format. */
  private String encodeToText(A value) {
    List<String> encodedFields = new ArrayList<>(fields.size());
    for (Field<A, ?> field : fields) {
      encodedFields.add(encodeFieldValue(field, value));
    }
    return PgRecordParser.encode(encodedFields);
  }

  /** Encode a single field value to text. */
  private <F> String encodeFieldValue(Field<A, F> field, A structValue) {
    F value = field.getter().apply(structValue);
    if (value == null) {
      return null;
    }
    return field.type().pgCompositeText().encode(value).orElse(null);
  }

  // ========================================================================
  // Builder API for creating composite types
  // ========================================================================

  /**
   * Create a composite type builder.
   *
   * @param <A> the struct type (typically a record)
   */
  public static <A> Builder<A> builder(String typeName) {
    return new Builder<>(typeName);
  }

  public static class Builder<A> {
    private final String typeName;
    private final List<Field<A, ?>> fields = new ArrayList<>();

    Builder(String typeName) {
      this.typeName = typeName;
    }

    /**
     * Add a field using its PgType for encode/decode.
     *
     * @param name the field name in SQL
     * @param type the PgType for the field (provides encode/decode via pgText)
     * @param getter function to extract field value from struct
     */
    public <F> Builder<A> field(String name, PgType<F> type, Function<A, F> getter) {
      fields.add(new Field<>(name, type, getter));
      return this;
    }

    /**
     * Add a string field.
     *
     * @param name the field name in SQL
     * @param type the PgType for the field
     * @param getter function to extract field value from struct
     */
    public Builder<A> stringField(String name, PgType<String> type, Function<A, String> getter) {
      return field(name, type, getter);
    }

    /**
     * Add an integer field.
     *
     * @param name the field name in SQL
     * @param type the PgType for the field
     * @param getter function to extract field value from struct
     */
    public Builder<A> intField(String name, PgType<Integer> type, Function<A, Integer> getter) {
      return field(name, type, getter);
    }

    /**
     * Add a long field.
     *
     * @param name the field name in SQL
     * @param type the PgType for the field
     * @param getter function to extract field value from struct
     */
    public Builder<A> longField(String name, PgType<Long> type, Function<A, Long> getter) {
      return field(name, type, getter);
    }

    /**
     * Add a double field.
     *
     * @param name the field name in SQL
     * @param type the PgType for the field
     * @param getter function to extract field value from struct
     */
    public Builder<A> doubleField(String name, PgType<Double> type, Function<A, Double> getter) {
      return field(name, type, getter);
    }

    /**
     * Add a boolean field.
     *
     * @param name the field name in SQL
     * @param type the PgType for the field
     * @param getter function to extract field value from struct
     */
    public Builder<A> booleanField(String name, PgType<Boolean> type, Function<A, Boolean> getter) {
      return field(name, type, getter);
    }

    /**
     * Add a nested composite field.
     *
     * @param name the field name in SQL
     * @param nestedStruct the PgStruct for the nested composite
     * @param getter function to extract field value from struct
     */
    public <F> Builder<A> nestedField(
        String name, PgStruct<F> nestedStruct, Function<A, F> getter) {
      return field(name, nestedStruct.asType(), getter);
    }

    /**
     * Add an array field of nested composites.
     *
     * @param name the field name in SQL
     * @param nestedStruct the PgStruct for array elements
     * @param getter function to extract array value from struct
     * @param arrayFactory factory to create arrays of the element type
     */
    public <F> Builder<A> nestedArrayField(
        String name,
        PgStruct<F> nestedStruct,
        Function<A, F[]> getter,
        java.util.function.IntFunction<F[]> arrayFactory) {
      // Create array type with proper text encode/decode
      PgType<F> elementType = nestedStruct.asType();
      PgType<F[]> arrayType =
          new PgType<>(
              elementType.typename().array(),
              PgRead.of(
                  (rs, idx) -> {
                    throw new UnsupportedOperationException(
                        "Direct JDBC read not supported for nested arrays");
                  }),
              elementType.write().array(elementType.typename()),
              elementType.pgText().array(),
              elementType.pgCompositeText().array(arrayFactory),
              elementType.pgJson().array(arrayFactory));
      return field(name, arrayType, getter);
    }

    /**
     * Add a nullable string field. This is a convenience method for the common case.
     *
     * @param name the field name in SQL
     * @param type the PgType for the field
     * @param getter function to extract field value from struct
     */
    public Builder<A> nullableField(String name, PgType<String> type, Function<A, String> getter) {
      return stringField(name, type, getter);
    }

    /**
     * Add an optional field where the getter returns Optional&lt;F&gt;.
     *
     * <p>This is the preferred way to handle nullable fields. The Optional is unwrapped internally,
     * so the PgType should be for the inner type F, not Optional&lt;F&gt;.
     *
     * <p>For Scala Option types, convert to Java Optional using scala.jdk.OptionConverters.
     *
     * @param name the field name in SQL
     * @param type the PgType for the inner type F (not Optional&lt;F&gt;)
     * @param getter function to extract Optional&lt;F&gt; value from struct
     */
    public <F> Builder<A> optField(String name, PgType<F> type, Function<A, Optional<F>> getter) {
      // Unwrap Optional to nullable F for internal storage
      Function<A, F> unwrappingGetter = a -> getter.apply(a).orElse(null);
      fields.add(new Field<>(name, type, unwrappingGetter));
      return this;
    }

    /**
     * Build the PgStruct with auto-derived writer and JSON codec.
     *
     * @param reader function to construct struct from field values array
     */
    public PgStruct<A> build(StructReader<A> reader) {
      List<PgTypename.CompositeOf.CompositeField> typenameFields =
          fields.stream()
              .map(f -> new PgTypename.CompositeOf.CompositeField(f.name(), f.type().typename()))
              .toList();

      PgTypename.CompositeOf<A> typename = new PgTypename.CompositeOf<>(typeName, typenameFields);

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
      PgJson<A> json =
          new PgJson<>() {
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

      return new PgStruct<>(typename, List.copyOf(fields), reader, writer, json);
    }

    @SuppressWarnings("unchecked")
    private <F> Object extractFieldValue(Field<A, F> field, A structValue) {
      return field.getter().apply(structValue);
    }

    @SuppressWarnings("unchecked")
    private <F> JsonValue fieldToJson(Field<A, F> field, A structValue) {
      F value = field.getter().apply(structValue);
      if (value == null) {
        return JsonValue.JNull.INSTANCE;
      }
      return field.type().pgJson().toJson(value);
    }

    @SuppressWarnings("unchecked")
    private <F> Object fieldFromJson(Field<A, F> field, JsonValue jsonValue) {
      if (jsonValue == null || jsonValue instanceof JsonValue.JNull) {
        return null;
      }
      return field.type().pgJson().fromJson(jsonValue);
    }
  }
}
