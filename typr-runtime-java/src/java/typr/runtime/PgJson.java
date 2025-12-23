package typr.runtime;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.postgresql.geometric.*;
import org.postgresql.util.PGInterval;
import typr.data.*;
import typr.data.Vector;

/**
 * PostgreSQL-specific JSON codec implementations. Handles conversion to/from JSON in PostgreSQL's
 * expected format.
 */
public interface PgJson<A> extends DbJson<A> {

  @Override
  default PgJson<Optional<A>> opt() {
    PgJson<A> self = this;
    return new PgJson<>() {
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

  default PgJson<A[]> array(IntFunction<A[]> arrayFactory) {
    PgJson<A> self = this;
    return new PgJson<>() {
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

  default <B> PgJson<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    PgJson<A> self = this;
    return new PgJson<>() {
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
  PgJson<Boolean> bool =
      new PgJson<>() {
        @Override
        public JsonValue toJson(Boolean value) {
          return JsonValue.JBool.of(value);
        }

        @Override
        public Boolean fromJson(JsonValue json) {
          if (json instanceof JsonValue.JBool(boolean value)) return value;
          throw new IllegalArgumentException(
              "Expected boolean, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<Short> int2 =
      new PgJson<>() {
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

  PgJson<Integer> int4 =
      new PgJson<>() {
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

  PgJson<Long> int8 =
      new PgJson<>() {
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

  PgJson<Float> float4 =
      new PgJson<>() {
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

  PgJson<Double> float8 =
      new PgJson<>() {
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

  PgJson<BigDecimal> numeric =
      new PgJson<>() {
        @Override
        public JsonValue toJson(BigDecimal value) {
          return JsonValue.JNumber.of(value.toPlainString());
        }

        @Override
        public BigDecimal fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber(String value)) return new BigDecimal(value);
          throw new IllegalArgumentException(
              "Expected number, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<String> text =
      new PgJson<>() {
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

  PgJson<byte[]> bytea =
      new PgJson<>() {
        @Override
        public JsonValue toJson(byte[] value) {
          // PostgreSQL uses hex encoding for bytea in JSON: "\\x..."
          StringBuilder sb = new StringBuilder("\\x");
          for (byte b : value) {
            sb.append(String.format("%02x", b & 0xff));
          }
          return new JsonValue.JString(sb.toString());
        }

        @Override
        public byte[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JString(String value))) {
            throw new IllegalArgumentException(
                "Expected string for bytea, got: " + json.getClass().getSimpleName());
          }
          if (value.startsWith("\\x")) {
            value = value.substring(2);
          }
          byte[] result = new byte[value.length() / 2];
          for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
          }
          return result;
        }
      };

  // Date/Time types
  PgJson<LocalDate> date =
      new PgJson<>() {
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

  PgJson<LocalTime> time =
      new PgJson<>() {
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

  PgJson<LocalDateTime> timestamp =
      new PgJson<>() {
        // PostgreSQL JSON uses ISO format with 'T', but some custom types use space delimiter
        private static final DateTimeFormatter FORMATTER_SPACE =
            new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd HH:mm:ss")
                .appendFraction(java.time.temporal.ChronoField.MICRO_OF_SECOND, 0, 6, true)
                .toFormatter();

        @Override
        public JsonValue toJson(LocalDateTime value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public LocalDateTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            // Try ISO format first (with 'T'), then space-delimited format
            if (value.contains("T")) {
              return LocalDateTime.parse(value);
            } else {
              return LocalDateTime.parse(value, FORMATTER_SPACE);
            }
          }
          throw new IllegalArgumentException(
              "Expected string for timestamp, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<Instant> timestamptz =
      new PgJson<>() {
        @Override
        public JsonValue toJson(Instant value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public Instant fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) return Instant.parse(value);
          throw new IllegalArgumentException(
              "Expected string for timestamptz, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<OffsetTime> timetz =
      new PgJson<>() {
        // PostgreSQL may return offsets like "+01" instead of "+01:00", need custom formatter
        private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder()
                .appendPattern("HH:mm:ss")
                .appendFraction(java.time.temporal.ChronoField.MICRO_OF_SECOND, 0, 6, true)
                .appendOffset("+HH:mm", "+00:00")
                .toFormatter();
        private static final DateTimeFormatter FORMATTER_SHORT_OFFSET =
            new DateTimeFormatterBuilder()
                .appendPattern("HH:mm:ss")
                .appendFraction(java.time.temporal.ChronoField.MICRO_OF_SECOND, 0, 6, true)
                .appendOffset("+HH", "+00")
                .toFormatter();

        @Override
        public JsonValue toJson(OffsetTime value) {
          return new JsonValue.JString(value.toString());
        }

        @Override
        public OffsetTime fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            // Try standard format first, then short offset format (e.g., "+01" instead of "+01:00")
            try {
              return OffsetTime.parse(value, FORMATTER);
            } catch (java.time.format.DateTimeParseException e) {
              return OffsetTime.parse(value, FORMATTER_SHORT_OFFSET);
            }
          }
          throw new IllegalArgumentException(
              "Expected string for timetz, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<UUID> uuid =
      new PgJson<>() {
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

  // JSON types (pass-through)
  PgJson<Json> json =
      new PgJson<>() {
        @Override
        public JsonValue toJson(Json value) {
          return JsonValue.parse(value.value());
        }

        @Override
        public Json fromJson(JsonValue json) {
          return new Json(json.encode());
        }
      };

  PgJson<Jsonb> jsonb =
      new PgJson<>() {
        @Override
        public JsonValue toJson(Jsonb value) {
          return JsonValue.parse(value.value());
        }

        @Override
        public Jsonb fromJson(JsonValue json) {
          return new Jsonb(json.encode());
        }
      };

  // Special types - these use string representation
  PgJson<PGInterval> interval = text.bimap(PGInterval::new, PGInterval::getValue);

  PgJson<PGpoint> point =
      new PgJson<>() {
        @Override
        public JsonValue toJson(PGpoint value) {
          return new JsonValue.JString(value.getValue());
        }

        @Override
        public PGpoint fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            try {
              return new PGpoint(value);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          throw new IllegalArgumentException(
              "Expected string for point, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<PGbox> box =
      new PgJson<>() {
        @Override
        public JsonValue toJson(PGbox value) {
          return new JsonValue.JString(value.getValue());
        }

        @Override
        public PGbox fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            try {
              return new PGbox(value);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          throw new IllegalArgumentException(
              "Expected string for box, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<PGcircle> circle =
      new PgJson<>() {
        @Override
        public JsonValue toJson(PGcircle value) {
          return new JsonValue.JString(value.getValue());
        }

        @Override
        public PGcircle fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            try {
              return new PGcircle(value);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          throw new IllegalArgumentException(
              "Expected string for circle, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<PGline> line =
      new PgJson<>() {
        @Override
        public JsonValue toJson(PGline value) {
          return new JsonValue.JString(value.getValue());
        }

        @Override
        public PGline fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            try {
              return new PGline(value);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          throw new IllegalArgumentException(
              "Expected string for line, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<PGlseg> lseg =
      new PgJson<>() {
        @Override
        public JsonValue toJson(PGlseg value) {
          return new JsonValue.JString(value.getValue());
        }

        @Override
        public PGlseg fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            try {
              return new PGlseg(value);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          throw new IllegalArgumentException(
              "Expected string for lseg, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<PGpath> path =
      new PgJson<>() {
        @Override
        public JsonValue toJson(PGpath value) {
          return new JsonValue.JString(value.getValue());
        }

        @Override
        public PGpath fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            try {
              return new PGpath(value);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          throw new IllegalArgumentException(
              "Expected string for path, got: " + json.getClass().getSimpleName());
        }
      };

  PgJson<PGpolygon> polygon =
      new PgJson<>() {
        @Override
        public JsonValue toJson(PGpolygon value) {
          return new JsonValue.JString(value.getValue());
        }

        @Override
        public PGpolygon fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            try {
              return new PGpolygon(value);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          throw new IllegalArgumentException(
              "Expected string for polygon, got: " + json.getClass().getSimpleName());
        }
      };

  // Wrapper types that use string representation
  PgJson<Inet> inet = text.bimap(Inet::new, Inet::value);
  // Money is returned as string by PostgreSQL's to_json (e.g., "$42.22")
  PgJson<Money> money =
      new PgJson<>() {
        @Override
        public JsonValue toJson(Money value) {
          return JsonValue.JNumber.of(value.value());
        }

        @Override
        public Money fromJson(JsonValue json) {
          if (json instanceof JsonValue.JNumber(String value))
            return new Money(Double.parseDouble(value));
          if (json instanceof JsonValue.JString(String value)) return new Money(value);
          throw new IllegalArgumentException(
              "Expected number or string for money, got: " + json.getClass().getSimpleName());
        }
      };
  PgJson<AclItem> aclitem = text.bimap(AclItem::new, AclItem::value);
  PgJson<Xml> xml = text.bimap(Xml::new, Xml::value);
  PgJson<Xid> xid = text.bimap(Xid::new, Xid::value);
  // PostgreSQL returns composite types as JSON objects, but Record stores the raw string
  // representation.
  // We encode as string and decode from either object (convert to string) or string directly.
  PgJson<typr.data.Record> record =
      new PgJson<>() {
        @Override
        public JsonValue toJson(typr.data.Record value) {
          return new JsonValue.JString(value.value());
        }

        @Override
        public typr.data.Record fromJson(JsonValue json) {
          if (json instanceof JsonValue.JString(String value)) {
            return new typr.data.Record(value);
          }
          if (json instanceof JsonValue.JObject obj) {
            // PostgreSQL returns composite types as JSON objects with field names
            // Convert back to tuple string format: (val1, val2, ...)
            StringBuilder sb = new StringBuilder("(");
            boolean first = true;
            for (JsonValue v : obj.fields().values()) {
              if (!first) sb.append(",");
              first = false;
              // Handle each value type
              if (v instanceof JsonValue.JNull) {
                // null values are empty in tuple representation
              } else if (v instanceof JsonValue.JString(String s)) {
                sb.append(s);
              } else if (v instanceof JsonValue.JNumber(String n)) {
                sb.append(n);
              } else if (v instanceof JsonValue.JBool(boolean b)) {
                sb.append(b);
              } else {
                // For nested objects/arrays, use JSON encoding
                sb.append(v.encode());
              }
            }
            sb.append(")");
            return new typr.data.Record(sb.toString());
          }
          throw new IllegalArgumentException(
              "Expected string or object for record, got: " + json.getClass().getSimpleName());
        }
      };
  PgJson<Vector> vector = text.bimap(Vector::parse, Vector::value);
  // PostgreSQL returns int2vector as JSON array, not string
  PgJson<Int2Vector> int2vector =
      new PgJson<>() {
        @Override
        public JsonValue toJson(Int2Vector value) {
          JsonValue[] elements = new JsonValue[value.values().length];
          for (int i = 0; i < value.values().length; i++) {
            elements[i] = JsonValue.JNumber.of(value.values()[i]);
          }
          return new JsonValue.JArray(List.of(elements));
        }

        @Override
        public Int2Vector fromJson(JsonValue json) {
          if (json instanceof JsonValue.JArray(List<JsonValue> elements)) {
            short[] values = new short[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
              if (elements.get(i) instanceof JsonValue.JNumber(String num)) {
                values[i] = Short.parseShort(num);
              } else {
                throw new IllegalArgumentException("Expected number in int2vector array");
              }
            }
            return new Int2Vector(values);
          }
          if (json instanceof JsonValue.JString(String value)) return Int2Vector.parse(value);
          throw new IllegalArgumentException(
              "Expected array or string for int2vector, got: " + json.getClass().getSimpleName());
        }
      };
  // PostgreSQL returns oidvector as JSON array, but with STRING elements (unlike int2vector which
  // uses numbers)
  PgJson<OidVector> oidvector =
      new PgJson<>() {
        @Override
        public JsonValue toJson(OidVector value) {
          JsonValue[] elements = new JsonValue[value.values().length];
          for (int i = 0; i < value.values().length; i++) {
            elements[i] = JsonValue.JNumber.of(value.values()[i]);
          }
          return new JsonValue.JArray(List.of(elements));
        }

        @Override
        public OidVector fromJson(JsonValue json) {
          if (json instanceof JsonValue.JArray(List<JsonValue> elements)) {
            int[] values = new int[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
              JsonValue elem = elements.get(i);
              if (elem instanceof JsonValue.JNumber(String num)) {
                values[i] = Integer.parseInt(num);
              } else if (elem instanceof JsonValue.JString(String s)) {
                // PostgreSQL returns oidvector elements as strings in arrays
                values[i] = Integer.parseInt(s);
              } else {
                throw new IllegalArgumentException(
                    "Expected number or string in oidvector array, got: "
                        + elem.getClass().getSimpleName());
              }
            }
            return new OidVector(values);
          }
          if (json instanceof JsonValue.JString(String value)) return OidVector.parse(value);
          throw new IllegalArgumentException(
              "Expected array or string for oidvector, got: " + json.getClass().getSimpleName());
        }
      };
  PgJson<Regclass> regclass = text.bimap(Regclass::new, Regclass::value);
  PgJson<Regconfig> regconfig = text.bimap(Regconfig::new, Regconfig::value);
  PgJson<Regdictionary> regdictionary = text.bimap(Regdictionary::new, Regdictionary::value);
  PgJson<Regnamespace> regnamespace = text.bimap(Regnamespace::new, Regnamespace::value);
  PgJson<Regoper> regoper = text.bimap(Regoper::new, Regoper::value);
  PgJson<Regoperator> regoperator = text.bimap(Regoperator::new, Regoperator::value);
  PgJson<Regproc> regproc = text.bimap(Regproc::new, Regproc::value);
  PgJson<Regprocedure> regprocedure = text.bimap(Regprocedure::new, Regprocedure::value);
  PgJson<Regrole> regrole = text.bimap(Regrole::new, Regrole::value);
  PgJson<Regtype> regtype = text.bimap(Regtype::new, Regtype::value);

  // Range types - PostgreSQL returns ranges as strings in JSON
  static <T extends Comparable<? super T>> PgJson<Range<T>> range(
      SqlFunction<String, T> valueParser,
      java.util.function.BiFunction<RangeBound<T>, RangeBound<T>, Range<T>> rangeFactory) {
    return new PgJson<>() {
      @Override
      public JsonValue toJson(Range<T> value) {
        return new JsonValue.JString(RangeParser.format(value));
      }

      @Override
      public Range<T> fromJson(JsonValue json) {
        if (json instanceof JsonValue.JString(String value)) {
          try {
            return RangeParser.parse(value, valueParser, rangeFactory);
          } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
          }
        }
        throw new IllegalArgumentException(
            "Expected string for range, got: " + json.getClass().getSimpleName());
      }
    };
  }

  PgJson<Range<Integer>> int4range = range(RangeParser.INT4_PARSER, Range.INT4);
  PgJson<Range<Long>> int8range = range(RangeParser.INT8_PARSER, Range.INT8);
  PgJson<Range<BigDecimal>> numrange = range(RangeParser.NUMERIC_PARSER, Range.NUMERIC);
  PgJson<Range<LocalDate>> daterange = range(RangeParser.DATE_PARSER, Range.DATE);
  PgJson<Range<LocalDateTime>> tsrange = range(RangeParser.TIMESTAMP_PARSER, Range.TIMESTAMP);
  PgJson<Range<Instant>> tstzrange = range(RangeParser.TIMESTAMPTZ_PARSER, Range.TIMESTAMPTZ);

  // hstore uses a JSON object representation
  PgJson<Map<String, String>> hstore =
      new PgJson<>() {
        @Override
        public JsonValue toJson(Map<String, String> value) {
          Map<String, JsonValue> fields = new LinkedHashMap<>();
          for (Map.Entry<String, String> e : value.entrySet()) {
            fields.put(
                e.getKey(),
                e.getValue() == null
                    ? JsonValue.JNull.INSTANCE
                    : new JsonValue.JString(e.getValue()));
          }
          return new JsonValue.JObject(fields);
        }

        @Override
        public Map<String, String> fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JObject(Map<String, JsonValue> fields))) {
            throw new IllegalArgumentException(
                "Expected object for hstore, got: " + json.getClass().getSimpleName());
          }
          Map<String, String> result = new LinkedHashMap<>();
          for (Map.Entry<String, JsonValue> e : fields.entrySet()) {
            if (e.getValue() instanceof JsonValue.JNull) {
              result.put(e.getKey(), null);
            } else if (e.getValue() instanceof JsonValue.JString(String value)) {
              result.put(e.getKey(), value);
            } else {
              throw new IllegalArgumentException(
                  "Expected string or null in hstore, got: "
                      + e.getValue().getClass().getSimpleName());
            }
          }
          return result;
        }
      };

  // Unboxed primitive array types - no boxing overhead
  PgJson<boolean[]> boolArrayUnboxed =
      new PgJson<>() {
        @Override
        public JsonValue toJson(boolean[] arr) {
          List<JsonValue> elements = new ArrayList<>(arr.length);
          for (boolean v : arr) {
            elements.add(JsonValue.JBool.of(v));
          }
          return new JsonValue.JArray(elements);
        }

        @Override
        public boolean[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JArray(List<JsonValue> values))) {
            throw new IllegalArgumentException("Expected JSON array for boolean[]");
          }
          boolean[] result = new boolean[values.size()];
          for (int i = 0; i < values.size(); i++) {
            if (values.get(i) instanceof JsonValue.JBool(boolean value)) {
              result[i] = value;
            } else {
              throw new IllegalArgumentException("Expected boolean in array");
            }
          }
          return result;
        }
      };

  PgJson<short[]> shortArrayUnboxed =
      new PgJson<>() {
        @Override
        public JsonValue toJson(short[] arr) {
          List<JsonValue> elements = new ArrayList<>(arr.length);
          for (short v : arr) {
            elements.add(JsonValue.JNumber.of(v));
          }
          return new JsonValue.JArray(elements);
        }

        @Override
        public short[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JArray(List<JsonValue> values))) {
            throw new IllegalArgumentException("Expected JSON array for short[]");
          }
          short[] result = new short[values.size()];
          for (int i = 0; i < values.size(); i++) {
            if (values.get(i) instanceof JsonValue.JNumber(String value)) {
              result[i] = Short.parseShort(value);
            } else {
              throw new IllegalArgumentException("Expected number in array");
            }
          }
          return result;
        }
      };

  PgJson<int[]> intArrayUnboxed =
      new PgJson<>() {
        @Override
        public JsonValue toJson(int[] arr) {
          List<JsonValue> elements = new ArrayList<>(arr.length);
          for (int v : arr) {
            elements.add(JsonValue.JNumber.of(v));
          }
          return new JsonValue.JArray(elements);
        }

        @Override
        public int[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JArray(List<JsonValue> values))) {
            throw new IllegalArgumentException("Expected JSON array for int[]");
          }
          int[] result = new int[values.size()];
          for (int i = 0; i < values.size(); i++) {
            if (values.get(i) instanceof JsonValue.JNumber(String value)) {
              result[i] = Integer.parseInt(value);
            } else {
              throw new IllegalArgumentException("Expected number in array");
            }
          }
          return result;
        }
      };

  PgJson<long[]> longArrayUnboxed =
      new PgJson<>() {
        @Override
        public JsonValue toJson(long[] arr) {
          List<JsonValue> elements = new ArrayList<>(arr.length);
          for (long v : arr) {
            elements.add(JsonValue.JNumber.of(v));
          }
          return new JsonValue.JArray(elements);
        }

        @Override
        public long[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JArray(List<JsonValue> values))) {
            throw new IllegalArgumentException("Expected JSON array for long[]");
          }
          long[] result = new long[values.size()];
          for (int i = 0; i < values.size(); i++) {
            if (values.get(i) instanceof JsonValue.JNumber(String value)) {
              result[i] = Long.parseLong(value);
            } else {
              throw new IllegalArgumentException("Expected number in array");
            }
          }
          return result;
        }
      };

  PgJson<float[]> floatArrayUnboxed =
      new PgJson<>() {
        @Override
        public JsonValue toJson(float[] arr) {
          List<JsonValue> elements = new ArrayList<>(arr.length);
          for (float v : arr) {
            elements.add(JsonValue.JNumber.of(v));
          }
          return new JsonValue.JArray(elements);
        }

        @Override
        public float[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JArray(List<JsonValue> values))) {
            throw new IllegalArgumentException("Expected JSON array for float[]");
          }
          float[] result = new float[values.size()];
          for (int i = 0; i < values.size(); i++) {
            if (values.get(i) instanceof JsonValue.JNumber(String value)) {
              result[i] = Float.parseFloat(value);
            } else {
              throw new IllegalArgumentException("Expected number in array");
            }
          }
          return result;
        }
      };

  PgJson<double[]> doubleArrayUnboxed =
      new PgJson<>() {
        @Override
        public JsonValue toJson(double[] arr) {
          List<JsonValue> elements = new ArrayList<>(arr.length);
          for (double v : arr) {
            elements.add(JsonValue.JNumber.of(v));
          }
          return new JsonValue.JArray(elements);
        }

        @Override
        public double[] fromJson(JsonValue json) {
          if (!(json instanceof JsonValue.JArray(List<JsonValue> values))) {
            throw new IllegalArgumentException("Expected JSON array for double[]");
          }
          double[] result = new double[values.size()];
          for (int i = 0; i < values.size(); i++) {
            if (values.get(i) instanceof JsonValue.JNumber(String value)) {
              result[i] = Double.parseDouble(value);
            } else {
              throw new IllegalArgumentException("Expected number in array");
            }
          }
          return result;
        }
      };
}
