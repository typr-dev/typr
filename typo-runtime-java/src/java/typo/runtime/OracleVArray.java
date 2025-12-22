package typo.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import oracle.sql.ARRAY;
import oracle.sql.ArrayDescriptor;
import typo.data.JsonValue;

/**
 * Oracle VARRAY type support.
 *
 * <p>A VARRAY is a fixed-maximum-size ordered collection. Example: CREATE TYPE phone_list AS
 * VARRAY(5) OF VARCHAR2(25)
 *
 * <p>Unlike nested tables, VARRAYs have a maximum size that is enforced.
 */
public class OracleVArray {
  /**
   * Create an OracleType for a VARRAY type.
   *
   * @param varrayTypeName The SQL type name (e.g., "SCHEMA.PHONE_LIST")
   * @param maxSize Maximum number of elements in the VARRAY
   * @param elementType The type of elements in the VARRAY
   * @param <T> The Java type of elements
   * @return An OracleType that can read/write VARRAY values as Lists
   */
  public static <T> OracleType<List<T>> of(
      String varrayTypeName, int maxSize, OracleType<T> elementType) {
    OracleRead<List<T>> read =
        new OracleRead.NonNullable<>(
            ResultSet::getObject,
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
                    T converted = elementType.read().fromOracleValue(element);
                    result.add(converted);
                  }
                }

                return result;
              } catch (Exception e) {
                throw new SQLException("Failed to read Oracle VARRAY: " + e.getMessage(), e);
              }
            });

    // Use structured() instead of primitive() to support STRUCT context
    // toOracleValue() will convert List<T> → oracle.sql.ARRAY
    OracleWrite<List<T>> write =
        OracleWrite.structured(
            (list, conn) -> {
              if (list == null) return null;
              if (list.size() > maxSize) {
                throw new IllegalArgumentException(
                    varrayTypeName
                        + " max size is "
                        + maxSize
                        + ", got "
                        + list.size()
                        + " elements");
              }
              try {
                // Convert each element using elementType's toOracleValue()
                // For OBJECT types: converts to oracle.sql.STRUCT
                // For primitive types: value passes through unchanged
                Object[] elements = new Object[list.size()];
                for (int i = 0; i < list.size(); i++) {
                  elements[i] = elementType.write().toOracleValue(list.get(i), conn);
                }

                ArrayDescriptor desc = ArrayDescriptor.createDescriptor(varrayTypeName, conn);
                return new ARRAY(desc, conn, elements);
              } catch (Exception e) {
                throw new SQLException("Failed to write Oracle VARRAY: " + e.getMessage(), e);
              }
            },
            varrayTypeName,
            Types.ARRAY);

    // Generate OracleJson codec
    OracleJson<List<T>> json = json(elementType);

    return new OracleType<>(OracleTypename.of(varrayTypeName), read, write, json);
  }

  /** Generate JSON codec for list type. */
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
        if (!(json instanceof JsonValue.JArray(List<JsonValue> elements))) {
          throw new IllegalArgumentException(
              "Expected JSON array for VARRAY type, got: " + json.getClass().getSimpleName());
        }

        List<T> result = new ArrayList<>(elements.size());
        for (JsonValue element : elements) {
          result.add(elementType.oracleJson().fromJson(element));
        }

        return result;
      }
    };
  }
}
