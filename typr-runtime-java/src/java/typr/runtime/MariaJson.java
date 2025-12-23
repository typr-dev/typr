package typr.runtime;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import typr.data.*;

/**
 * MariaDB-specific JSON codec implementations. Handles conversion to/from JSON in MariaDB's
 * expected format.
 */
public interface MariaJson<A> extends DbJson<A> {

  @Override
  default MariaJson<Optional<A>> opt() {
    MariaJson<A> self = this;
    return new MariaJson<>() {
      @Override
      public JsonValue toJson(Optional<A> value) {
        return value.map(self::toJson).orElse(JsonValue.JNull.INSTANCE);
      }

      @Override
      public Optional<A> fromJson(JsonValue json) {
        if (json instanceof JsonValue.JNull) {
          return Optional.empty();
        }
        return Optional.of(self.fromJson(json));
      }
    };
  }

  default MariaJson<A[]> array(IntFunction<A[]> arrayFactory) {
    MariaJson<A> self = this;
    return new MariaJson<>() {
      @Override
      public JsonValue toJson(A[] value) {
        List<JsonValue> elements = new ArrayList<>(value.length);
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

  default <B> MariaJson<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    MariaJson<A> self = this;
    return new MariaJson<>() {
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

  // Primitive type codecs
  // MariaDB returns BOOLEAN as 1/0 in JSON when coming from a column, not true/false
  MariaJson<Boolean> bool =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(Boolean value) {
          return JsonValue.JBool.of(value);
        }

        @Override
        public Boolean fromJson(JsonValue json) {
          if (json instanceof JsonValue.JBool b) return b.value();
          // MariaDB returns BOOLEAN columns as 1/0 in JSON
          if (json instanceof JsonValue.JNumber n) return Integer.parseInt(n.value()) != 0;
          throw new IllegalArgumentException(
              "Expected boolean or number, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<Short> int2 =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(Short value) {
          return JsonValue.JNumber.of(value.longValue());
        }

        @Override
        public Short fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber n) return Short.parseShort(n.value());
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<Integer> int4 =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(Integer value) {
          return JsonValue.JNumber.of(value.longValue());
        }

        @Override
        public Integer fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber n) return Integer.parseInt(n.value());
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<Long> int8 =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(Long value) {
          return JsonValue.JNumber.of(value);
        }

        @Override
        public Long fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber n) return Long.parseLong(n.value());
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<Float> float4 =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(Float value) {
          return JsonValue.JNumber.of(value.doubleValue());
        }

        @Override
        public Float fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber n) return Float.parseFloat(n.value());
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<Double> float8 =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(Double value) {
          return JsonValue.JNumber.of(value);
        }

        @Override
        public Double fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber n) return Double.parseDouble(n.value());
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<BigDecimal> numeric =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(BigDecimal value) {
          return JsonValue.JNumber.of(value.toPlainString());
        }

        @Override
        public BigDecimal fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber n) return new BigDecimal(n.value());
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<String> text =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(String value) {
          return new JsonValue.JString(value);
        }

        @Override
        public String fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) return s.value();
          throw new IllegalArgumentException(
              "Expected string, got: " + json.getClass().getSimpleName());
        }
      };

  // MariaDB returns binary data as raw bytes with unicode escapes for unprintable chars
  // e.g., "�\u0001\u0000\u0000" - the JSON parser handles unicode escapes
  MariaJson<byte[]> bytea =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(byte[] value) {
          // Encode as raw string - the JSON encoder will escape as needed
          return new JsonValue.JString(
              new String(value, java.nio.charset.StandardCharsets.ISO_8859_1));
        }

        @Override
        public byte[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JString s)) {
            throw new IllegalArgumentException(
                "Expected string for bytea, got: " + json.getClass().getSimpleName());
          }
          // The JSON parser already decoded unicode escapes, so we get the raw bytes
          return s.value().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        }
      };

  // Date/Time types
  MariaJson<LocalDate> date =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(LocalDate value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public LocalDate fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) return LocalDate.parse(s.value());
          throw new IllegalArgumentException(
              "Expected string for date, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<LocalTime> time =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(LocalTime value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public LocalTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) return LocalTime.parse(s.value());
          throw new IllegalArgumentException(
              "Expected string for time, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<LocalDateTime> timestamp =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(LocalDateTime value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public LocalDateTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            // MariaDB returns "2024-06-15 14:30:45.123456" with space, ISO format uses 'T'
            String value = s.value().replace(' ', 'T');
            return LocalDateTime.parse(value);
          }
          throw new IllegalArgumentException(
              "Expected string for timestamp, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<Instant> timestamptz =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(Instant value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public Instant fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) return Instant.parse(s.value());
          throw new IllegalArgumentException(
              "Expected string for timestamptz, got: " + json.getClass().getSimpleName());
        }
      };

  MariaJson<UUID> uuid =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(UUID value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public UUID fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) return UUID.fromString(s.value());
          throw new IllegalArgumentException(
              "Expected string for uuid, got: " + json.getClass().getSimpleName());
        }
      };

  // JSON types (pass-through)
  MariaJson<Json> json =
      new MariaJson<>() {
        @Override
        public JsonValue toJson(Json value) {
          return JsonValue.parse(value.value());
        }

        @Override
        public Json fromJson(JsonValue json) {
          return new Json(json.encode());
        }
      };
}
