package typr.data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple JSON ADT for representing JSON values that can be produced/consumed by databases. This is
 * used for type-safe JSON serialization/deserialization without external dependencies.
 */
public sealed interface JsonValue
    permits JsonValue.JNull,
        JsonValue.JBool,
        JsonValue.JNumber,
        JsonValue.JString,
        JsonValue.JArray,
        JsonValue.JObject {

  /** JSON null value */
  record JNull() implements JsonValue {
    public static final JNull INSTANCE = new JNull();

    @Override
    public String encode() {
      return "null";
    }
  }

  /** JSON boolean value */
  record JBool(boolean value) implements JsonValue {
    public static final JBool TRUE = new JBool(true);
    public static final JBool FALSE = new JBool(false);

    public static JBool of(boolean value) {
      return value ? TRUE : FALSE;
    }

    @Override
    public String encode() {
      return value ? "true" : "false";
    }
  }

  /** JSON number value (stored as String for precision) */
  record JNumber(String value) implements JsonValue {
    public static JNumber of(long value) {
      return new JNumber(String.valueOf(value));
    }

    public static JNumber of(double value) {
      return new JNumber(String.valueOf(value));
    }

    public static JNumber of(String value) {
      return new JNumber(value);
    }

    @Override
    public String encode() {
      return value;
    }
  }

  /** JSON string value */
  record JString(String value) implements JsonValue {
    public static JString of(String value) {
      return new JString(value);
    }

    @Override
    public String encode() {
      return encodeString(value);
    }
  }

  /** JSON array value */
  record JArray(List<JsonValue> values) implements JsonValue {
    public JArray {
      values = List.copyOf(values);
    }

    public static JArray of(JsonValue... values) {
      return new JArray(List.of(values));
    }

    public static JArray of(List<JsonValue> values) {
      return new JArray(values);
    }

    @Override
    public String encode() {
      return values.stream().map(JsonValue::encode).collect(Collectors.joining(",", "[", "]"));
    }
  }

  /** JSON object value */
  record JObject(Map<String, JsonValue> fields) implements JsonValue {
    public JObject {
      fields = new LinkedHashMap<>(fields);
    }

    public static JObject of(Map<String, JsonValue> fields) {
      return new JObject(fields);
    }

    public static JObject empty() {
      return new JObject(Map.of());
    }

    public JsonValue get(String key) {
      return fields.get(key);
    }

    @Override
    public String encode() {
      return fields.entrySet().stream()
          .map(e -> encodeString(e.getKey()) + ":" + e.getValue().encode())
          .collect(Collectors.joining(",", "{", "}"));
    }
  }

  /** Encode this JSON value to a string */
  String encode();

  /** Parse JSON from a string */
  static JsonValue parse(String json) {
    return JsonParser.parse(json);
  }

  // Helper for string encoding
  private static String encodeString(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append("\"");
    return sb.toString();
  }
}
