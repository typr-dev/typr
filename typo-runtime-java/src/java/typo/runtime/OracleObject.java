package typo.runtime;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import oracle.sql.STRUCT;
import oracle.sql.StructDescriptor;
import typo.data.JsonValue;

/**
 * Oracle OBJECT type support (similar to DuckDB STRUCT).
 *
 * <p>An OBJECT is a user-defined type with named attributes. Example: CREATE TYPE address_t AS
 * OBJECT (street VARCHAR2, city VARCHAR2)
 *
 * <p>This class handles the marshalling between Oracle's oracle.sql.STRUCT and Java records.
 */
public record OracleObject<A>(
    OracleTypename.ObjectOf<A> typename,
    List<Attribute<A, ?>> attributes,
    ObjectReader<A> reader,
    ObjectWriter<A> writer) {
  public record Attribute<A, F>(String name, OracleType<F> type, Function<A, F> getter) {}

  @FunctionalInterface
  public interface ObjectReader<A> {
    A read(Object[] attributeValues) throws SQLException;
  }

  @FunctionalInterface
  public interface ObjectWriter<A> {
    Object[] write(A value);
  }

  /** Convert this OracleObject to an OracleType for use in repositories. */
  public OracleType<A> asType() {
    OracleRead<A> read =
        new OracleRead.NonNullable<>(
            (rs, idx) -> rs.getObject(idx),
            obj -> {
              if (!(obj instanceof STRUCT struct)) {
                throw new SQLException(
                    "Expected STRUCT, got: " + (obj == null ? "null" : obj.getClass().getName()));
              }

              try {
                Object[] rawAttrs = struct.getAttributes();

                // Convert each attribute through its type's reader
                // This handles Oracle's BigDecimal → Long/Integer conversions,
                // and nested STRUCT/ARRAY objects
                Object[] typedAttrs = new Object[attributes.size()];
                for (int i = 0; i < attributes.size(); i++) {
                  Attribute<A, ?> attr = attributes.get(i);
                  // fromOracleValue() handles both primitive types and Oracle-native types
                  typedAttrs[i] = attr.type().read().fromOracleValue(rawAttrs[i]);
                }

                return reader.read(typedAttrs);
              } catch (Exception e) {
                throw new SQLException("Failed to read Oracle STRUCT: " + e.getMessage(), e);
              }
            });

    OracleWrite<A> write =
        OracleWrite.structured(
            (value, conn) -> {
              try {
                StructDescriptor desc = StructDescriptor.createDescriptor(typename.sqlName(), conn);

                // Get raw field values
                Object[] rawValues = writer.write(value);

                // Convert each field to its Oracle representation
                // For primitive types, toOracleValue returns the value unchanged
                // For structured types (e.g., TIMESTAMP WITH LOCAL TIME ZONE),
                // it converts to Oracle-native objects (e.g., oracle.sql.TIMESTAMPLTZ)
                Object[] oracleValues = new Object[rawValues.length];
                for (int i = 0; i < rawValues.length; i++) {
                  @SuppressWarnings("unchecked")
                  Attribute<A, Object> attr = (Attribute<A, Object>) attributes.get(i);
                  Object fieldValue = rawValues[i];
                  oracleValues[i] = attr.type().write().toOracleValue(fieldValue, conn);
                }

                return new STRUCT(desc, conn, oracleValues);
              } catch (Exception e) {
                throw new SQLException("Failed to create Oracle STRUCT: " + e.getMessage(), e);
              }
            },
            typename.sqlName(),
            Types.STRUCT);

    // Generate OracleJson codec
    OracleJson<A> json = generateJson();

    return new OracleType<>(typename.asGeneric(), read, write, json);
  }

  /** Generate JSON codec for this OBJECT type. */
  private OracleJson<A> generateJson() {
    return new OracleJson<A>() {
      @Override
      public JsonValue toJson(A value) {
        if (value == null) return JsonValue.JNull.INSTANCE;

        Map<String, JsonValue> fields = new LinkedHashMap<>();
        for (Attribute<A, ?> attr : attributes) {
          @SuppressWarnings("unchecked")
          Attribute<A, Object> typedAttr = (Attribute<A, Object>) attr;
          Object fieldValue = typedAttr.getter().apply(value);
          JsonValue fieldJson = typedAttr.type().oracleJson().toJson(fieldValue);
          fields.put(attr.name(), fieldJson);
        }
        return new JsonValue.JObject(fields);
      }

      @Override
      public A fromJson(JsonValue json) {
        if (json instanceof JsonValue.JNull) return null;
        if (!(json instanceof JsonValue.JObject obj)) {
          throw new IllegalArgumentException(
              "Expected JSON object for OBJECT type, got: " + json.getClass().getSimpleName());
        }

        // Extract attribute values from JSON
        Object[] attrValues = new Object[attributes.size()];
        for (int i = 0; i < attributes.size(); i++) {
          Attribute<A, ?> attr = attributes.get(i);
          JsonValue fieldJson = obj.get(attr.name());
          attrValues[i] = attr.type().oracleJson().fromJson(fieldJson);
        }

        try {
          return reader.read(attrValues);
        } catch (SQLException e) {
          throw new RuntimeException("Failed to construct object from JSON", e);
        }
      }
    };
  }

  // ═══ BUILDER API ═══

  public static <A> Builder<A> builder(String objectTypeName) {
    return new Builder<>(objectTypeName);
  }

  public static class Builder<A> {
    private final String objectTypeName;
    private final List<Attribute<A, ?>> attributes = new ArrayList<>();

    Builder(String objectTypeName) {
      this.objectTypeName = objectTypeName;
    }

    public <F> Builder<A> addAttribute(String name, OracleType<F> type, Function<A, F> getter) {
      attributes.add(new Attribute<>(name, type, getter));
      return this;
    }

    /**
     * Build OracleObject with a reader - writer is automatically derived from attribute getters.
     */
    public OracleObject<A> build(ObjectReader<A> reader) {
      // Generate writer automatically from the attribute getters
      ObjectWriter<A> writer =
          value -> {
            Object[] result = new Object[attributes.size()];
            for (int i = 0; i < attributes.size(); i++) {
              @SuppressWarnings("unchecked")
              Attribute<A, Object> attr = (Attribute<A, Object>) attributes.get(i);
              result[i] = attr.getter().apply(value);
            }
            return result;
          };

      OracleTypename.ObjectOf<A> typename = OracleTypename.objectOf(objectTypeName);
      return new OracleObject<>(typename, attributes, reader, writer);
    }
  }
}
