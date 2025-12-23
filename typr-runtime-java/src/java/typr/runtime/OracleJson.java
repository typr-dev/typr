package typr.runtime;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import typr.data.*;

/**
 * Oracle-specific JSON codec implementations. Handles conversion to/from JSON in Oracle's expected
 * format.
 */
public interface OracleJson<A> extends DbJson<A> {

  @Override
  default OracleJson<Optional<A>> opt() {
    OracleJson<A> self = this;
    return new OracleJson<>() {
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

  default OracleJson<A[]> array(IntFunction<A[]> arrayFactory) {
    OracleJson<A> self = this;
    return new OracleJson<>() {
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

  default <B> OracleJson<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    OracleJson<A> self = this;
    return new OracleJson<>() {
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
  OracleJson<Boolean> bool =
      new OracleJson<>() {
        @Override
        public JsonValue toJson(Boolean value) {
          return JsonValue.JBool.of(value);
        }

        @Override
        public Boolean fromJson(JsonValue json) {
          if (json instanceof JsonValue.JBool b) return b.value();
          // Oracle might return NUMBER(1) as 0/1 for boolean-like columns
          if (json instanceof JsonValue.JNumber n) return Integer.parseInt(n.value()) != 0;
          throw new IllegalArgumentException(
              "Expected boolean or number, got: " + json.getClass().getSimpleName());
        }
      };

  OracleJson<Short> int2 =
      new OracleJson<>() {
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

  OracleJson<Integer> int4 =
      new OracleJson<>() {
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

  OracleJson<Long> int8 =
      new OracleJson<>() {
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

  OracleJson<Float> float4 =
      new OracleJson<>() {
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

  OracleJson<Double> float8 =
      new OracleJson<>() {
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

  OracleJson<BigDecimal> numeric =
      new OracleJson<>() {
        @Override
        public JsonValue toJson(BigDecimal value) {
          return JsonValue.JNumber.of(value.toString());
        }

        @Override
        public BigDecimal fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber n) return new BigDecimal(n.value());
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  OracleJson<String> text =
      new OracleJson<>() {
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

  OracleJson<NonEmptyString> nonEmptyString =
      new OracleJson<>() {
        @Override
        public JsonValue toJson(NonEmptyString value) {
          return new JsonValue.JString(value.value());
        }

        @Override
        public NonEmptyString fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            return NonEmptyString.force(s.value());
          }
          throw new IllegalArgumentException(
              "Expected string, got: " + json.getClass().getSimpleName());
        }
      };

  static OracleJson<PaddedString> paddedString(int length) {
    return new OracleJson<>() {
      @Override
      public JsonValue toJson(PaddedString value) {
        return new JsonValue.JString(value.value());
      }

      @Override
      public PaddedString fromJson(JsonValue json) {
        if (json instanceof JsonValue.JString s) {
          return PaddedString.force(s.value(), length);
        }
        throw new IllegalArgumentException(
            "Expected string, got: " + json.getClass().getSimpleName());
      }
    };
  }

  OracleJson<NonEmptyBlob> nonEmptyBlob =
      new OracleJson<>() {
        @Override
        public JsonValue toJson(NonEmptyBlob value) {
          StringBuilder sb = new StringBuilder();
          for (byte b : value.value()) {
            sb.append(String.format("%02X", b & 0xff));
          }
          return new JsonValue.JString(sb.toString());
        }

        @Override
        public NonEmptyBlob fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            String hexString = s.value();
            if (hexString.length() % 2 != 0) {
              throw new IllegalArgumentException("Hex string must have even length");
            }
            byte[] bytes = new byte[hexString.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
              bytes[i] = (byte) Integer.parseInt(hexString.substring(i * 2, i * 2 + 2), 16);
            }
            return NonEmptyBlob.force(bytes);
          }
          throw new IllegalArgumentException(
              "Expected string for bytea, got: " + json.getClass().getSimpleName());
        }
      };

  // Oracle RAW/BLOB data - encode as hex string
  OracleJson<byte[]> bytea =
      new OracleJson<>() {
        @Override
        public JsonValue toJson(byte[] value) {
          StringBuilder sb = new StringBuilder();
          for (byte b : value) {
            sb.append(String.format("%02X", b & 0xff));
          }
          return new JsonValue.JString(sb.toString());
        }

        @Override
        public byte[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JString s)) {
            throw new IllegalArgumentException(
                "Expected string for bytea, got: " + json.getClass().getSimpleName());
          }
          String hex = s.value();
          byte[] result = new byte[hex.length() / 2];
          for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
          }
          return result;
        }
      };

  // Date/Time types
  // Oracle DATE includes time component
  OracleJson<LocalDateTime> date =
      new OracleJson<>() {
        // Oracle JSON_OBJECT returns dates as "YYYY-MM-DD"T"HH:MI:SS" format
        private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd")
                .optionalStart()
                .appendLiteral('T')
                .appendPattern("HH:mm:ss")
                .optionalEnd()
                .toFormatter();

        @Override
        public JsonValue toJson(LocalDateTime value) {
          return new JsonValue.JString(value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        @Override
        public LocalDateTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            String value = s.value();
            // Handle both date-only and datetime formats
            if (value.length() <= 10) {
              // Date only - assume midnight
              return LocalDate.parse(value).atStartOfDay();
            }
            // Replace space with T if present (Oracle might use space)
            value = value.replace(' ', 'T');
            return LocalDateTime.parse(value);
          }
          throw new IllegalArgumentException(
              "Expected string for date, got: " + json.getClass().getSimpleName());
        }
      };

  OracleJson<LocalDateTime> timestamp =
      new OracleJson<>() {
        private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd")
                .optionalStart()
                .appendLiteral('T')
                .appendPattern("HH:mm:ss")
                .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                .optionalEnd()
                .toFormatter();

        @Override
        public JsonValue toJson(LocalDateTime value) {
          return new JsonValue.JString(value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        @Override
        public LocalDateTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            String value = s.value().replace(' ', 'T');
            // Oracle TEXT format may include timezone offset for TIMESTAMP WITH LOCAL TIME ZONE
            // e.g., "2024-06-15T14:30:45.000000+02:00"
            // Parse as OffsetDateTime if it contains timezone, otherwise as LocalDateTime
            if (value.contains("+") || value.endsWith("Z") || value.matches(".*-\\d{2}:\\d{2}$")) {
              return OffsetDateTime.parse(value).toLocalDateTime();
            }
            return LocalDateTime.parse(value);
          }
          throw new IllegalArgumentException(
              "Expected string for timestamp, got: " + json.getClass().getSimpleName());
        }
      };

  OracleJson<OffsetDateTime> timestampWithTimeZone =
      new OracleJson<>() {
        @Override
        public JsonValue toJson(OffsetDateTime value) {
          return new JsonValue.JString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }

        @Override
        public OffsetDateTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            return OffsetDateTime.parse(s.value());
          }
          throw new IllegalArgumentException(
              "Expected string for timestamp with time zone, got: "
                  + json.getClass().getSimpleName());
        }
      };

  // INTERVAL YEAR TO MONTH - Oracle JSON returns ISO-8601 format (P2Y5M)
  OracleJson<OracleIntervalYM> intervalYearToMonth =
      new OracleJson<>() {
        @Override
        public JsonValue toJson(OracleIntervalYM value) {
          return new JsonValue.JString(value.toIso8601());
        }

        @Override
        public OracleIntervalYM fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            return OracleIntervalYM.parse(s.value());
          }
          throw new IllegalArgumentException(
              "Expected string for interval, got: " + json.getClass().getSimpleName());
        }
      };

  // INTERVAL DAY TO SECOND - Oracle JSON returns ISO-8601 format (P3DT14H30M45.123456S)
  OracleJson<OracleIntervalDS> intervalDayToSecond =
      new OracleJson<>() {
        @Override
        public JsonValue toJson(OracleIntervalDS value) {
          return new JsonValue.JString(value.toIso8601());
        }

        @Override
        public OracleIntervalDS fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString s) {
            return OracleIntervalDS.parse(s.value());
          }
          throw new IllegalArgumentException(
              "Expected string for interval, got: " + json.getClass().getSimpleName());
        }
      };

  // ROWID types
  OracleJson<String> rowId = text;

  // JSON types (pass-through)
  OracleJson<Json> json =
      new OracleJson<>() {
        @Override
        public JsonValue toJson(Json value) {
          return JsonValue.parse(value.value());
        }

        @Override
        public Json fromJson(JsonValue json) {
          return new Json(json.encode());
        }
      };

  // XMLTYPE - stored as string
  OracleJson<String> xmlType = text;
}
