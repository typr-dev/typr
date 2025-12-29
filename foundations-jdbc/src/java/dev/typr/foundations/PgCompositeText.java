package dev.typr.foundations;

import dev.typr.foundations.data.Money;
import java.math.BigDecimal;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.postgresql.geometric.*;
import org.postgresql.util.PGInterval;

/**
 * Simple text encoding/decoding for values within PostgreSQL composite types.
 *
 * <p>This provides simple encoding (toString-like) and decoding (parse) for field values. The
 * encoding does NOT include any escaping - that is handled by {@link PgRecordParser} which applies
 * the composite type format (quoting, quote-doubling).
 *
 * <p>This is separate from {@link PgText} which handles COPY format with backslash escaping.
 */
public abstract class PgCompositeText<A> {

  /**
   * Encode a value to its simple text representation.
   *
   * @return Optional.empty() to represent SQL NULL, Optional.of(string) for the encoded value
   */
  public abstract Optional<String> encode(A value);

  /** Decode a value from its text representation. */
  public abstract A decode(String text);

  /** Create an array version of this codec with comma delimiter. */
  public PgCompositeText<A[]> array(IntFunction<A[]> arrayFactory) {
    return array(arrayFactory, ',');
  }

  /**
   * Create an array version of this codec with a custom delimiter.
   *
   * <p>PostgreSQL uses semicolon (;) as the delimiter for geometric type arrays (box[], circle[],
   * line[], lseg[], path[], point[], polygon[]) because their elements contain commas.
   *
   * @param arrayFactory factory to create arrays of the element type
   * @param delimiter the array element delimiter character
   */
  public PgCompositeText<A[]> array(IntFunction<A[]> arrayFactory, char delimiter) {
    var self = this;
    return new PgCompositeText<>() {
      @Override
      public Optional<String> encode(A[] values) {
        List<A> list = java.util.Arrays.asList(values);
        return Optional.of(
            PgRecordParser.encodeArray(list, v -> self.encode(v).orElse(null), delimiter));
      }

      @Override
      public A[] decode(String text) {
        List<String> elements = PgRecordParser.parseArray(text, delimiter);
        A[] result = arrayFactory.apply(elements.size());
        for (int i = 0; i < elements.size(); i++) {
          String elem = elements.get(i);
          result[i] = elem == null ? null : self.decode(elem);
        }
        return result;
      }
    };
  }

  /** Transform this codec to work with a different type. */
  public <B> PgCompositeText<B> bimap(Function<A, B> f, Function<B, A> g) {
    var self = this;
    return new PgCompositeText<>() {
      @Override
      public Optional<String> encode(B value) {
        return self.encode(g.apply(value));
      }

      @Override
      public B decode(String text) {
        return f.apply(self.decode(text));
      }
    };
  }

  /** Create an optional version of this codec. */
  public PgCompositeText<Optional<A>> opt() {
    var self = this;
    return new PgCompositeText<>() {
      @Override
      public Optional<String> encode(Optional<A> value) {
        return value.flatMap(self::encode);
      }

      @Override
      public Optional<A> decode(String text) {
        if (text == null) {
          return Optional.empty();
        }
        return Optional.of(self.decode(text));
      }
    };
  }

  /** Create a PgCompositeText from encode and decode functions. */
  public static <A> PgCompositeText<A> of(
      Function<A, String> encoder, Function<String, A> decoder) {
    return new PgCompositeText<>() {
      @Override
      public Optional<String> encode(A value) {
        return Optional.of(encoder.apply(value));
      }

      @Override
      public A decode(String text) {
        return decoder.apply(text);
      }
    };
  }

  // ========================================================================
  // Standard instances
  // ========================================================================

  /** String: identity encoding/decoding. */
  public static final PgCompositeText<String> text = of(Function.identity(), Function.identity());

  /** Integer: toString/parseInt. */
  public static final PgCompositeText<Integer> int4 = of(Object::toString, Integer::parseInt);

  /** Short: toString/parseShort. */
  public static final PgCompositeText<Short> int2 = of(Object::toString, Short::parseShort);

  /** Long: toString/parseLong. */
  public static final PgCompositeText<Long> int8 = of(Object::toString, Long::parseLong);

  /** Float: toString/parseFloat. */
  public static final PgCompositeText<Float> float4 = of(Object::toString, Float::parseFloat);

  /** Double: toString/parseDouble. */
  public static final PgCompositeText<Double> float8 = of(Object::toString, Double::parseDouble);

  /** BigDecimal: toString/new BigDecimal. */
  public static final PgCompositeText<BigDecimal> numeric = of(Object::toString, BigDecimal::new);

  /** Boolean: t/f format. */
  public static final PgCompositeText<Boolean> bool =
      of(b -> b ? "t" : "f", text -> text.equals("t") || text.equals("true") || text.equals("1"));

  /** UUID: toString/fromString. */
  public static final PgCompositeText<UUID> uuid = of(Object::toString, UUID::fromString);

  /**
   * Money: PostgreSQL returns money with currency symbol (e.g., "$42.22"). We encode as plain
   * number and decode handling the currency symbol.
   */
  public static final PgCompositeText<Money> money = of(m -> String.valueOf(m.value()), Money::new);

  /**
   * OffsetTime (timetz): handles both standard format and PostgreSQL's short offset format.
   * PostgreSQL may return "16:30:00+03" instead of "16:30:00+03:00".
   */
  public static final PgCompositeText<OffsetTime> timetz =
      new PgCompositeText<>() {
        // Standard format with optional fractional seconds and full offset
        private static final DateTimeFormatter STANDARD =
            new DateTimeFormatterBuilder()
                .appendPattern("HH:mm:ss")
                .optionalStart()
                .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                .optionalEnd()
                .appendOffset("+HH:MM", "Z")
                .toFormatter();

        // Short offset format (e.g., +03 instead of +03:00)
        private static final DateTimeFormatter SHORT_OFFSET =
            new DateTimeFormatterBuilder()
                .appendPattern("HH:mm:ss")
                .optionalStart()
                .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                .optionalEnd()
                .appendOffset("+HH", "Z")
                .toFormatter();

        @Override
        public Optional<String> encode(OffsetTime value) {
          return Optional.of(value.toString());
        }

        @Override
        public OffsetTime decode(String text) {
          try {
            return OffsetTime.parse(text, STANDARD);
          } catch (java.time.format.DateTimeParseException e) {
            return OffsetTime.parse(text, SHORT_OFFSET);
          }
        }
      };

  // ========================================================================
  // Geometric types - use PGobject's getValue/constructor
  // ========================================================================

  /** PGpoint: format (x,y). */
  public static final PgCompositeText<PGpoint> point =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(PGpoint value) {
          return Optional.of(value.getValue());
        }

        @Override
        public PGpoint decode(String text) {
          try {
            return new PGpoint(text);
          } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to parse PGpoint: " + text, e);
          }
        }
      };

  /** PGbox: format (x1,y1),(x2,y2). */
  public static final PgCompositeText<PGbox> box =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(PGbox value) {
          return Optional.of(value.getValue());
        }

        @Override
        public PGbox decode(String text) {
          try {
            return new PGbox(text);
          } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to parse PGbox: " + text, e);
          }
        }
      };

  /** PGcircle: format <(x,y),r>. */
  public static final PgCompositeText<PGcircle> circle =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(PGcircle value) {
          return Optional.of(value.getValue());
        }

        @Override
        public PGcircle decode(String text) {
          try {
            return new PGcircle(text);
          } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to parse PGcircle: " + text, e);
          }
        }
      };

  /** PGline: format {A,B,C}. */
  public static final PgCompositeText<PGline> line =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(PGline value) {
          return Optional.of(value.getValue());
        }

        @Override
        public PGline decode(String text) {
          try {
            return new PGline(text);
          } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to parse PGline: " + text, e);
          }
        }
      };

  /** PGlseg: format [(x1,y1),(x2,y2)]. */
  public static final PgCompositeText<PGlseg> lseg =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(PGlseg value) {
          return Optional.of(value.getValue());
        }

        @Override
        public PGlseg decode(String text) {
          try {
            return new PGlseg(text);
          } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to parse PGlseg: " + text, e);
          }
        }
      };

  /** PGpath: format [(x1,y1),...] or ((x1,y1),...). */
  public static final PgCompositeText<PGpath> path =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(PGpath value) {
          return Optional.of(value.getValue());
        }

        @Override
        public PGpath decode(String text) {
          try {
            return new PGpath(text);
          } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to parse PGpath: " + text, e);
          }
        }
      };

  /** PGpolygon: format ((x1,y1),...). */
  public static final PgCompositeText<PGpolygon> polygon =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(PGpolygon value) {
          return Optional.of(value.getValue());
        }

        @Override
        public PGpolygon decode(String text) {
          try {
            return new PGpolygon(text);
          } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to parse PGpolygon: " + text, e);
          }
        }
      };

  // ========================================================================
  // Other complex types
  // ========================================================================

  /** PGInterval: text format like "1 year 2 mons 3 days 04:05:06.666". */
  public static final PgCompositeText<PGInterval> interval =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(PGInterval value) {
          return Optional.of(value.getValue());
        }

        @Override
        public PGInterval decode(String text) {
          try {
            return new PGInterval(text);
          } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to parse PGInterval: " + text, e);
          }
        }
      };

  /**
   * bytea: PostgreSQL hex format \x followed by hex digits. Note: inside composite types,
   * PostgreSQL uses single backslash (\x), not the COPY format double backslash (\\x).
   */
  public static final PgCompositeText<byte[]> bytea =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(byte[] value) {
          StringBuilder sb = new StringBuilder(2 + value.length * 2);
          sb.append("\\x");
          for (byte b : value) {
            sb.append(String.format("%02x", b & 0xff));
          }
          return Optional.of(sb.toString());
        }

        @Override
        public byte[] decode(String text) {
          // Handle both \x and \\x prefixes (PostgreSQL may return either)
          String hex;
          if (text.startsWith("\\x")) {
            hex = text.substring(2);
          } else if (text.startsWith("\\\\x")) {
            hex = text.substring(3);
          } else {
            throw new IllegalArgumentException("Invalid bytea format: " + text);
          }
          if (hex.isEmpty()) {
            return new byte[0];
          }
          byte[] result = new byte[hex.length() / 2];
          for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
          }
          return result;
        }
      };

  /**
   * hstore: key=>value pairs. Inside composite types, the format uses double-quoted keys and
   * values.
   */
  public static final PgCompositeText<Map<String, String>> hstore =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(Map<String, String> value) {
          StringBuilder sb = new StringBuilder();
          boolean first = true;
          for (Map.Entry<String, String> entry : value.entrySet()) {
            if (first) {
              first = false;
            } else {
              sb.append(", ");
            }
            appendQuoted(sb, entry.getKey());
            sb.append("=>");
            if (entry.getValue() == null) {
              sb.append("NULL");
            } else {
              appendQuoted(sb, entry.getValue());
            }
          }
          return Optional.of(sb.toString());
        }

        private void appendQuoted(StringBuilder sb, String s) {
          sb.append('"');
          for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
              sb.append("\\\"");
            } else if (c == '\\') {
              sb.append("\\\\");
            } else {
              sb.append(c);
            }
          }
          sb.append('"');
        }

        @Override
        public Map<String, String> decode(String text) {
          Map<String, String> result = new LinkedHashMap<>();
          if (text.isEmpty()) {
            return result;
          }
          // Parse hstore format: "key"=>"value", "key2"=>"value2"
          int pos = 0;
          while (pos < text.length()) {
            // Skip whitespace
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
              pos++;
            }
            if (pos >= text.length()) break;

            // Parse key
            String key = parseQuotedString(text, pos);
            pos += key.length() + 2; // +2 for quotes

            // Skip =>
            while (pos < text.length() && (text.charAt(pos) == '=' || text.charAt(pos) == '>')) {
              pos++;
            }

            // Skip whitespace
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
              pos++;
            }

            // Parse value (could be NULL or quoted string)
            String value;
            if (text.regionMatches(true, pos, "NULL", 0, 4)) {
              value = null;
              pos += 4;
            } else {
              value = parseQuotedString(text, pos);
              pos += value.length() + 2; // +2 for quotes
            }

            result.put(
                unescapeHstoreString(key), value == null ? null : unescapeHstoreString(value));

            // Skip comma and whitespace
            while (pos < text.length()
                && (text.charAt(pos) == ',' || Character.isWhitespace(text.charAt(pos)))) {
              pos++;
            }
          }
          return result;
        }

        private String parseQuotedString(String text, int start) {
          if (text.charAt(start) != '"') {
            throw new IllegalArgumentException("Expected quoted string at position " + start);
          }
          StringBuilder sb = new StringBuilder();
          int i = start + 1;
          while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
              // Check for doubled quote (escaped)
              if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                sb.append('"');
                i += 2;
              } else {
                break; // End of string
              }
            } else if (c == '\\' && i + 1 < text.length()) {
              char next = text.charAt(i + 1);
              if (next == '"' || next == '\\') {
                sb.append(next);
                i += 2;
              } else {
                sb.append(c);
                i++;
              }
            } else {
              sb.append(c);
              i++;
            }
          }
          return sb.toString();
        }

        private String unescapeHstoreString(String s) {
          // The parseQuotedString already handles escaping
          return s;
        }
      };

  // ========================================================================
  // Unboxed primitive arrays
  // ========================================================================

  /** Unboxed boolean array: format {t,f,t}. */
  public static final PgCompositeText<boolean[]> boolArrayUnboxed =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(boolean[] value) {
          StringBuilder sb = new StringBuilder();
          sb.append('{');
          for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(value[i] ? 't' : 'f');
          }
          sb.append('}');
          return Optional.of(sb.toString());
        }

        @Override
        public boolean[] decode(String text) {
          List<String> elements = PgRecordParser.parseArray(text);
          boolean[] result = new boolean[elements.size()];
          for (int i = 0; i < elements.size(); i++) {
            String elem = elements.get(i);
            result[i] =
                elem != null && (elem.equals("t") || elem.equals("true") || elem.equals("1"));
          }
          return result;
        }
      };

  /** Unboxed short array: format {1,2,3}. */
  public static final PgCompositeText<short[]> shortArrayUnboxed =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(short[] value) {
          StringBuilder sb = new StringBuilder();
          sb.append('{');
          for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(value[i]);
          }
          sb.append('}');
          return Optional.of(sb.toString());
        }

        @Override
        public short[] decode(String text) {
          List<String> elements = PgRecordParser.parseArray(text);
          short[] result = new short[elements.size()];
          for (int i = 0; i < elements.size(); i++) {
            String elem = elements.get(i);
            result[i] = elem == null ? 0 : Short.parseShort(elem);
          }
          return result;
        }
      };

  /** Unboxed int array: format {1,2,3}. */
  public static final PgCompositeText<int[]> intArrayUnboxed =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(int[] value) {
          StringBuilder sb = new StringBuilder();
          sb.append('{');
          for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(value[i]);
          }
          sb.append('}');
          return Optional.of(sb.toString());
        }

        @Override
        public int[] decode(String text) {
          List<String> elements = PgRecordParser.parseArray(text);
          int[] result = new int[elements.size()];
          for (int i = 0; i < elements.size(); i++) {
            String elem = elements.get(i);
            result[i] = elem == null ? 0 : Integer.parseInt(elem);
          }
          return result;
        }
      };

  /** Unboxed long array: format {1,2,3}. */
  public static final PgCompositeText<long[]> longArrayUnboxed =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(long[] value) {
          StringBuilder sb = new StringBuilder();
          sb.append('{');
          for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(value[i]);
          }
          sb.append('}');
          return Optional.of(sb.toString());
        }

        @Override
        public long[] decode(String text) {
          List<String> elements = PgRecordParser.parseArray(text);
          long[] result = new long[elements.size()];
          for (int i = 0; i < elements.size(); i++) {
            String elem = elements.get(i);
            result[i] = elem == null ? 0L : Long.parseLong(elem);
          }
          return result;
        }
      };

  /** Unboxed float array: format {1.0,2.0,3.0}. */
  public static final PgCompositeText<float[]> floatArrayUnboxed =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(float[] value) {
          StringBuilder sb = new StringBuilder();
          sb.append('{');
          for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(value[i]);
          }
          sb.append('}');
          return Optional.of(sb.toString());
        }

        @Override
        public float[] decode(String text) {
          List<String> elements = PgRecordParser.parseArray(text);
          float[] result = new float[elements.size()];
          for (int i = 0; i < elements.size(); i++) {
            String elem = elements.get(i);
            result[i] = elem == null ? 0.0f : Float.parseFloat(elem);
          }
          return result;
        }
      };

  /** Unboxed double array: format {1.0,2.0,3.0}. */
  public static final PgCompositeText<double[]> doubleArrayUnboxed =
      new PgCompositeText<>() {
        @Override
        public Optional<String> encode(double[] value) {
          StringBuilder sb = new StringBuilder();
          sb.append('{');
          for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(value[i]);
          }
          sb.append('}');
          return Optional.of(sb.toString());
        }

        @Override
        public double[] decode(String text) {
          List<String> elements = PgRecordParser.parseArray(text);
          double[] result = new double[elements.size()];
          for (int i = 0; i < elements.size(); i++) {
            String elem = elements.get(i);
            result[i] = elem == null ? 0.0 : Double.parseDouble(elem);
          }
          return result;
        }
      };

  /** Codec that throws on encode/decode - for unsupported types. */
  public static <A> PgCompositeText<A> notSupported() {
    return new PgCompositeText<>() {
      @Override
      public Optional<String> encode(A value) {
        throw new UnsupportedOperationException(
            "Composite type encoding not supported for this type");
      }

      @Override
      public A decode(String text) {
        throw new UnsupportedOperationException(
            "Composite type decoding not supported for this type");
      }
    };
  }
}
