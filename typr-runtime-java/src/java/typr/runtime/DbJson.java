package typr.runtime;

import typr.data.JsonValue;

/**
 * Interface for JSON serialization/deserialization of database values. Each DbType provides an
 * implementation that converts values to/from JSON in the format that the database
 * produces/consumes.
 *
 * @param <A> The Java type being serialized/deserialized
 */
public interface DbJson<A> {
  /**
   * Convert a value to its JSON representation.
   *
   * @param value The value to convert (may be null for nullable types)
   * @return The JSON representation
   */
  JsonValue toJson(A value);

  /**
   * Convert a JSON representation back to the value.
   *
   * @param json The JSON to parse
   * @return The parsed value
   * @throws IllegalArgumentException if the JSON cannot be parsed
   */
  A fromJson(JsonValue json);

  /** Create an optional version of this JSON codec. */
  default DbJson<java.util.Optional<A>> opt() {
    DbJson<A> self = this;
    return new DbJson<>() {
      @Override
      public JsonValue toJson(java.util.Optional<A> value) {
        return value.map(self::toJson).orElse(JsonValue.JNull.INSTANCE);
      }

      @Override
      public java.util.Optional<A> fromJson(JsonValue json) {
        if (json instanceof JsonValue.JNull) {
          return java.util.Optional.empty();
        }
        return java.util.Optional.of(self.fromJson(json));
      }
    };
  }

  /** Create an array version of this JSON codec. */
  default DbJson<A[]> array(java.util.function.IntFunction<A[]> arrayFactory) {
    DbJson<A> self = this;
    return new DbJson<>() {
      @Override
      public JsonValue toJson(A[] value) {
        java.util.List<JsonValue> elements = new java.util.ArrayList<>(value.length);
        for (A elem : value) {
          elements.add(self.toJson(elem));
        }
        return new JsonValue.JArray(elements);
      }

      @Override
      public A[] fromJson(JsonValue json) {
        if (!(json instanceof JsonValue.JArray arr)) {
          throw new IllegalArgumentException(
              "Expected JSON array, got: " + json.getClass().getSimpleName());
        }
        A[] result = arrayFactory.apply(arr.values().size());
        for (int i = 0; i < arr.values().size(); i++) {
          result[i] = self.fromJson(arr.values().get(i));
        }
        return result;
      }
    };
  }

  /** Transform this codec using bidirectional mapping. */
  default <B> DbJson<B> bimap(SqlFunction<A, B> f, java.util.function.Function<B, A> g) {
    DbJson<A> self = this;
    return new DbJson<>() {
      @Override
      public JsonValue toJson(B value) {
        return self.toJson(g.apply(value));
      }

      @Override
      public B fromJson(JsonValue json) {
        try {
          return f.apply(self.fromJson(json));
        } catch (java.sql.SQLException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
}
