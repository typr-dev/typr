package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Encodes values to text format for MariaDB LOAD DATA INFILE command.
 *
 * <p>Similar to PgText but adapted for MariaDB's text format. MariaDB uses different escape
 * sequences and doesn't have the PostgreSQL array syntax.
 */
public abstract class MariaText<A> implements DbText<A> {
  public abstract void unsafeEncode(A a, StringBuilder sb);

  public <B> MariaText<B> contramap(Function<B, A> f) {
    var self = this;
    return instance((b, sb) -> self.unsafeEncode(f.apply(b), sb));
  }

  public MariaText<Optional<A>> opt() {
    var self = this;
    return instance(
        (a, sb) -> {
          if (a.isPresent()) self.unsafeEncode(a.get(), sb);
          else sb.append(MariaText.NULL);
        });
  }

  public static char DELIMETER = '\t';
  public static String NULL = "\\N";

  public static <A> MariaText<A> instance(BiConsumer<A, StringBuilder> f) {
    return new MariaText<>() {
      @Override
      public void unsafeEncode(A a, StringBuilder sb) {
        f.accept(a, sb);
      }
    };
  }

  public static <A> MariaText<A> instanceToString() {
    return textString.contramap(Object::toString);
  }

  /**
   * Escape a string for MariaDB LOAD DATA INFILE format. MariaDB escape sequences: - \0 = NUL
   * (ASCII 0) - \b = backspace - \n = newline - \r = carriage return - \t = tab - \Z = Ctrl+Z
   * (Windows EOF) - \\ = backslash
   */
  private static void escapeString(String s, StringBuilder sb) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\0':
          sb.append("\\0");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case 0x1a: // Ctrl+Z
          sb.append("\\Z");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        default:
          sb.append(c);
          break;
      }
    }
  }

  public static final MariaText<String> textString = instance((s, sb) -> escapeString(s, sb));
  public static final MariaText<Boolean> textBoolean =
      instance((b, sb) -> sb.append(b ? "1" : "0"));
  public static final MariaText<Byte> textByte = instance((n, sb) -> sb.append(n));
  public static final MariaText<Short> textShort = instance((n, sb) -> sb.append(n));
  public static final MariaText<Integer> textInteger = instance((n, sb) -> sb.append(n));
  public static final MariaText<Long> textLong = instance((n, sb) -> sb.append(n));
  public static final MariaText<Float> textFloat = instance((n, sb) -> sb.append(n));
  public static final MariaText<Double> textDouble = instance((n, sb) -> sb.append(n));
  public static final MariaText<BigDecimal> textBigDecimal = instance((n, sb) -> sb.append(n));
  public static final MariaText<BigInteger> textBigInteger = instance((n, sb) -> sb.append(n));
  public static final MariaText<UUID> textUuid = instance((n, sb) -> sb.append(n));

  public static final MariaText<byte[]> textByteArray =
      instance(
          (bs, sb) -> {
            // MariaDB expects hex string for binary data in LOAD DATA
            for (byte b : bs) {
              sb.append(String.format("%02X", b));
            }
          });

  /** Text encoder for SET type values. SET values are comma-separated strings in MariaDB. */
  public static final MariaText<Set<String>> textSet =
      instance(
          (set, sb) -> {
            boolean first = true;
            for (String value : set) {
              if (first) first = false;
              else sb.append(',');
              sb.append(value);
            }
          });

  @SuppressWarnings("unchecked")
  public static <A> MariaText<A> from(RowParser<A> rowParser) {
    return instance(
        (row, sb) -> {
          var encoded = rowParser.encode().apply(row);
          for (int i = 0; i < encoded.length; i++) {
            if (i > 0) {
              sb.append(MariaText.DELIMETER);
            }
            DbText<Object> text = (DbText<Object>) rowParser.columns().get(i).text();
            text.unsafeEncode(encoded[i], sb);
          }
        });
  }

  public static final MariaText<Object> NotWorking =
      new MariaText<>() {
        @Override
        public void unsafeEncode(Object t, StringBuilder sb) {
          throw new UnsupportedOperationException(
              "LOAD DATA INFILE is not supported for this type");
        }
      };

  @Deprecated
  public static <T> MariaText<T> NotWorking() {
    return (MariaText<T>) NotWorking;
  }
}
