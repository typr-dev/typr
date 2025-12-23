package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import typr.data.*;

/**
 * DuckDB-specific JSON codec implementations. DuckDB has native JSON support and can output results
 * as JSON.
 */
public interface DuckDbJson<A> extends DbJson<A> {

  @Override
  default DuckDbJson<Optional<A>> opt() {
    DuckDbJson<A> self = this;
    return new DuckDbJson<>() {
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

  default DuckDbJson<A[]> array(IntFunction<A[]> arrayFactory) {
    DuckDbJson<A> self = this;
    return new DuckDbJson<>() {
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
        if (!(json instanceof JsonValue.JArray(List<JsonValue> values))) {
          throw new IllegalArgumentException(
              "Expected JSON array, got: " + json.getClass().getSimpleName());
        }
        A[] result = arrayFactory.apply(values.size());
        for (int i = 0; i < values.size(); i++) {
          result[i] = self.fromJson(values.get(i));
        }
        return result;
      }
    };
  }

  default DuckDbJson<List<A>> list() {
    DuckDbJson<A> self = this;
    return new DuckDbJson<>() {
      @Override
      public JsonValue toJson(List<A> value) {
        List<JsonValue> elements = new ArrayList<>(value.size());
        for (A elem : value) {
          elements.add(self.toJson(elem));
        }
        return new JsonValue.JArray(elements);
      }

      @Override
      public List<A> fromJson(JsonValue json) {
        if (!(json instanceof JsonValue.JArray(List<JsonValue> values))) {
          throw new IllegalArgumentException(
              "Expected JSON array, got: " + json.getClass().getSimpleName());
        }
        List<A> result = new ArrayList<>(values.size());
        for (JsonValue elem : values) {
          result.add(self.fromJson(elem));
        }
        return result;
      }
    };
  }

  default <B> DuckDbJson<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    DuckDbJson<A> self = this;
    return new DuckDbJson<>() {
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
  DuckDbJson<Boolean> bool =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(Boolean value) {
          return JsonValue.JBool.of(value);
        }

        @Override
        public Boolean fromJson(JsonValue json) {
          if (json instanceof JsonValue.JBool(boolean value)) return value;
          if (json instanceof JsonValue.JNumber(String value)) return Integer.parseInt(value) != 0;
          throw new IllegalArgumentException(
              "Expected boolean or number, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<Byte> int1 =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(Byte value) {
          return JsonValue.JNumber.of(value.longValue());
        }

        @Override
        public Byte fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber(String value)) return Byte.parseByte(value);
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<Short> int2 =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(Short value) {
          return JsonValue.JNumber.of(value.longValue());
        }

        @Override
        public Short fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber(String value)) return Short.parseShort(value);
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<Integer> int4 =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(Integer value) {
          return JsonValue.JNumber.of(value.longValue());
        }

        @Override
        public Integer fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber(String value)) return Integer.parseInt(value);
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<Long> int8 =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(Long value) {
          return JsonValue.JNumber.of(value);
        }

        @Override
        public Long fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber(String value)) return Long.parseLong(value);
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  // HUGEINT (128-bit) - represented as BigInteger, serialized as string in JSON to avoid precision
  // loss
  DuckDbJson<BigInteger> hugeint =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(BigInteger value) {
          // For very large integers, use string representation to avoid precision loss
          return new JsonValue.JString(value.toString());
        }

        @Override
        public BigInteger fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) return new BigInteger(value);
          if (json instanceof JsonValue.JNumber(String value)) return new BigInteger(value);
          throw new IllegalArgumentException(
              "Expected string or number for hugeint, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<Float> float4 =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(Float value) {
          return JsonValue.JNumber.of(value.doubleValue());
        }

        @Override
        public Float fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber(String value)) return Float.parseFloat(value);
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<Double> float8 =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(Double value) {
          return JsonValue.JNumber.of(value);
        }

        @Override
        public Double fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber(String value)) return Double.parseDouble(value);
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<BigDecimal> numeric =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(BigDecimal value) {
          return JsonValue.JNumber.of(value.toPlainString());
        }

        @Override
        public BigDecimal fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber(String value)) return new BigDecimal(value);
          if (json instanceof JsonValue.JString(String value)) return new BigDecimal(value);
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<String> text =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(String value) {
          return new JsonValue.JString(value);
        }

        @Override
        public String fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) return value;
          throw new IllegalArgumentException(
              "Expected string, got: " + json.getClass().getSimpleName());
        }
      };

  // DuckDB encodes BLOB as base64 in JSON
  // Note: For JSON COPY import, BLOB is not supported as JSON is a textual format
  DuckDbJson<byte[]> blob =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(byte[] value) {
          // Use base64 encoding for JSON
          return new JsonValue.JString(Base64.getEncoder().encodeToString(value));
        }

        @Override
        public byte[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JString(String value))) {
            throw new IllegalArgumentException(
                "Expected string for blob, got: " + json.getClass().getSimpleName());
          }
          // Try base64 first
          try {
            return Base64.getDecoder().decode(value);
          } catch (IllegalArgumentException e) {
            // Handle hex format: \xAABBCC...
            if (value.startsWith("\\x")) {
              String hexStr = value.substring(2);
              byte[] bytes = new byte[hexStr.length() / 2];
              for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hexStr.substring(i * 2, i * 2 + 2), 16);
              }
              return bytes;
            }
            // If not base64 or hex, assume raw string (DuckDB may encode as escaped string)
            return value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
          }
        }
      };

  // Date/Time types
  DuckDbJson<LocalDate> date =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(LocalDate value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public LocalDate fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) return LocalDate.parse(value);
          throw new IllegalArgumentException(
              "Expected string for date, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<LocalTime> time =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(LocalTime value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public LocalTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) return LocalTime.parse(value);
          throw new IllegalArgumentException(
              "Expected string for time, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<LocalDateTime> timestamp =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(LocalDateTime value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public LocalDateTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            String normalized = s.value().replace(' ', 'T');
            return LocalDateTime.parse(normalized);
          }
          throw new IllegalArgumentException(
              "Expected string for timestamp, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<OffsetDateTime> timestamptz =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(OffsetDateTime value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public OffsetDateTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            String normalized = s.value().replace(' ', 'T');
            return OffsetDateTime.parse(normalized);
          }
          throw new IllegalArgumentException(
              "Expected string for timestamptz, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<Duration> interval =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(Duration value) {
          // Use DuckDB's interval format: HH:MM:SS
          // This format works in both JSON COPY and regular JDBC operations
          long hours = value.toHours();
          long minutes = value.toMinutesPart();
          long seconds = value.toSecondsPart();
          return new JsonValue.JString(String.format("%d:%02d:%02d", hours, minutes, seconds));
        }

        @Override
        public Duration fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            // Try to parse as ISO-8601 duration
            if (value.startsWith("PT") || value.startsWith("P")) {
              return Duration.parse(value);
            }
            // DuckDB format: "HH:MM:SS" or "HH:MM:SS.nnnnnn"
            String[] parts = value.split(":");
            if (parts.length >= 2) {
              long hours = Long.parseLong(parts[0]);
              long minutes = Long.parseLong(parts[1]);
              long seconds = parts.length > 2 ? Long.parseLong(parts[2].split("\\.")[0]) : 0;
              return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
            }
            throw new IllegalArgumentException("Cannot parse interval: " + value);
          }
          throw new IllegalArgumentException(
              "Expected string for interval, got: " + json.getClass().getSimpleName());
        }
      };

  DuckDbJson<UUID> uuid =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(UUID value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public UUID fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) return UUID.fromString(value);
          throw new IllegalArgumentException(
              "Expected string for uuid, got: " + json.getClass().getSimpleName());
        }
      };

  // JSON type (pass-through)
  DuckDbJson<Json> json =
      new DuckDbJson<>() {
        @Override
        public JsonValue toJson(Json value) {
          return JsonValue.parse(value.value());
        }

        @Override
        public Json fromJson(JsonValue json) {
          return new Json(json.encode());
        }
      };

  // BIT type - stored as string of 0s and 1s
  DuckDbJson<String> bit = text;
}
