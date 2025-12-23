package typr.runtime;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import oracle.sql.ARRAY;
import oracle.sql.ArrayDescriptor;
import typr.data.JsonValue;

/**
 * Oracle NESTED TABLE type support.
 *
 * <p>A nested table is a variable-length collection (unlike VARRAY which has a max size). Example:
 * CREATE TYPE order_items_t AS TABLE OF order_item_t
 *
 * <p>Nested tables are stored in a separate storage table and can grow to any size.
 */
public class OracleNestedTable {
  /**
   * Create an OracleType for a NESTED TABLE type.
   *
   * @param nestedTableTypeName The SQL type name (e.g., "SCHEMA.ORDER_ITEMS_T")
   * @param elementType The type of elements in the nested table
   * @param <T> The Java type of elements
   * @return An OracleType that can read/write NESTED TABLE values as Lists
   */
  public static <T> OracleType<List<T>> of(String nestedTableTypeName, OracleType<T> elementType) {
    OracleRead<List<T>> read =
        new OracleRead.NonNullable<>(
            (rs, idx) -> rs.getObject(idx),
            obj -> {
              if (!(obj instanceof ARRAY array)) {
                throw new SQLException(
                    "Expected ARRAY, got: " + (obj == null ? "null" : obj.getClass().getName()));
              }

              try {
                Object[] rawArray = (Object[]) array.getArray();

                // Convert Object[] to List<T> using element type's fromOracleValue()
                List<T> result = new ArrayList<>(rawArray.length);
                for (Object element : rawArray) {
                  if (element == null) {
                    result.add(null);
                  } else {
                    // Use fromOracleValue() to handle both primitive and Oracle-native types
                    @SuppressWarnings("unchecked")
                    T converted = (T) elementType.read().fromOracleValue(element);
                    result.add(converted);
                  }
                }

                return result;
              } catch (Exception e) {
                throw new SQLException("Failed to read Oracle NESTED TABLE: " + e.getMessage(), e);
              }
            });

    // Use structured() instead of primitive() to support STRUCT context
    // toOracleValue() will convert List<T> → oracle.sql.ARRAY
    OracleWrite<List<T>> write =
        OracleWrite.structured(
            (list, conn) -> {
              if (list == null) return null;
              try {
                // Convert each element using elementType's toOracleValue()
                // For OBJECT types: OrderItem → oracle.sql.STRUCT
                // For primitive types: value passes through unchanged
                Object[] elements = new Object[list.size()];
                for (int i = 0; i < list.size(); i++) {
                  elements[i] = elementType.write().toOracleValue(list.get(i), conn);
                }

                ArrayDescriptor desc = ArrayDescriptor.createDescriptor(nestedTableTypeName, conn);
                return new ARRAY(desc, conn, elements);
              } catch (Exception e) {
                throw new SQLException("Failed to write Oracle NESTED TABLE: " + e.getMessage(), e);
              }
            },
            nestedTableTypeName,
            Types.ARRAY);

    // Generate OracleJson codec
    OracleJson<List<T>> json = json(elementType);

    return new OracleType<>(OracleTypename.of(nestedTableTypeName), read, write, json);
  }

  /** Generate JSON codec for nested table type. */
  private static <T> OracleJson<List<T>> json(OracleType<T> elementType) {
    return new OracleJson<List<T>>() {
      @Override
      public JsonValue toJson(List<T> list) {
        if (list == null) return JsonValue.JNull.INSTANCE;

        List<JsonValue> elements = new ArrayList<>();
        for (T element : list) {
          elements.add(elementType.oracleJson().toJson(element));
        }
        return new JsonValue.JArray(elements);
      }

      @Override
      public List<T> fromJson(JsonValue json) {
        if (json instanceof JsonValue.JNull) return null;
        if (!(json instanceof JsonValue.JArray arr)) {
          throw new IllegalArgumentException(
              "Expected JSON array for NESTED TABLE type, got: " + json.getClass().getSimpleName());
        }

        List<JsonValue> elements = arr.values();
        List<T> result = new ArrayList<>(elements.size());

        for (JsonValue element : elements) {
          result.add(elementType.oracleJson().fromJson(element));
        }

        return result;
      }
    };
  }
}
