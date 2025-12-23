package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Stringifies values to DuckDB SQL literal format.
 *
 * <p>This is used for writing complex nested structures (STRUCT, MAP, LIST) via PreparedStatement
 * by converting them to SQL literal strings that DuckDB can parse.
 *
 * <p>Examples: - VARCHAR: 'hello' (quoted, escaped) or hello (unquoted for arrays) - INTEGER: 42
 * (always unquoted) - STRUCT: {'name': 'Alice', 'age': 30} - LIST: [1, 2, 3] - MAP: {'key1':
 * 'value1', 'key2': 'value2'}
 *
 * <p>The quoted parameter controls whether string-like types include quotes. Use quoted=true for
 * STRUCT field values, quoted=false for array/map elements.
 */
public abstract class DuckDbStringifier<A> {
  public abstract void unsafeEncode(A a, StringBuilder sb, boolean quoted);

  /** Encode a value to a String. Convenience method that creates a StringBuilder internally. */
  public String encode(A a, boolean quoted) {
    StringBuilder sb = new StringBuilder();
    unsafeEncode(a, sb, quoted);
    return sb.toString();
  }

  public <B> DuckDbStringifier<B> contramap(Function<B, A> f) {
    var self = this;
    return instance((b, sb, quoted) -> self.unsafeEncode(f.apply(b), sb, quoted));
  }

  public DuckDbStringifier<Optional<A>> opt() {
    var self = this;
    return instance(
        (a, sb, quoted) -> {
          if (a.isPresent()) self.unsafeEncode(a.get(), sb, quoted);
          else sb.append("NULL");
        });
  }

  @FunctionalInterface
  public interface StringifierFunction<A> {
    void accept(A a, StringBuilder sb, boolean quoted);
  }

  public static <A> DuckDbStringifier<A> instance(StringifierFunction<A> f) {
    return new DuckDbStringifier<>() {
      @Override
      public void unsafeEncode(A a, StringBuilder sb, boolean quoted) {
        f.accept(a, sb, quoted);
      }
    };
  }

  // ==================== String types (quoted or unquoted) ====================

  public static final DuckDbStringifier<String> string =
      instance(
          (s, sb, quoted) -> {
            if (quoted) {
              sb.append("'");
              sb.append(s.replace("'", "''"));
              sb.append("'");
            } else {
              sb.append(s);
            }
          });

  // ==================== Numeric types (always unquoted) ====================

  public static final DuckDbStringifier<Boolean> bool =
      instance((b, sb, quoted) -> sb.append(b ? "true" : "false"));
  public static final DuckDbStringifier<Byte> tinyint = instance((n, sb, quoted) -> sb.append(n));
  public static final DuckDbStringifier<Short> smallint = instance((n, sb, quoted) -> sb.append(n));
  public static final DuckDbStringifier<Integer> integer =
      instance((n, sb, quoted) -> sb.append(n));
  public static final DuckDbStringifier<Long> bigint = instance((n, sb, quoted) -> sb.append(n));
  public static final DuckDbStringifier<BigInteger> hugeint =
      instance((n, sb, quoted) -> sb.append(n.toString()));
  public static final DuckDbStringifier<Float> float4 = instance((n, sb, quoted) -> sb.append(n));
  public static final DuckDbStringifier<Double> float8 = instance((n, sb, quoted) -> sb.append(n));
  public static final DuckDbStringifier<BigDecimal> numeric =
      instance((n, sb, quoted) -> sb.append(n.toPlainString()));

  // ==================== Date/Time types (quoted or unquoted) ====================

  public static final DuckDbStringifier<LocalDate> date =
      instance(
          (d, sb, quoted) -> {
            if (quoted) sb.append("'");
            sb.append(d);
            if (quoted) sb.append("'");
          });

  public static final DuckDbStringifier<LocalTime> time =
      instance(
          (t, sb, quoted) -> {
            if (quoted) sb.append("'");
            sb.append(t);
            if (quoted) sb.append("'");
          });

  public static final DuckDbStringifier<LocalDateTime> timestamp =
      instance(
          (ts, sb, quoted) -> {
            if (quoted) sb.append("'");
            sb.append(ts);
            if (quoted) sb.append("'");
          });

  public static final DuckDbStringifier<OffsetDateTime> timestamptz =
      instance(
          (ts, sb, quoted) -> {
            if (quoted) sb.append("'");
            sb.append(ts);
            if (quoted) sb.append("'");
          });

  public static final DuckDbStringifier<Duration> interval =
      instance(
          (d, sb, quoted) -> {
            if (quoted) sb.append("'");
            long hours = d.toHours();
            long minutes = d.toMinutesPart();
            long seconds = d.toSecondsPart();
            sb.append(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            if (quoted) sb.append("'");
          });

  // ==================== UUID (quoted or unquoted) ====================

  public static final DuckDbStringifier<UUID> uuid =
      instance(
          (u, sb, quoted) -> {
            if (quoted) sb.append("'");
            sb.append(u);
            if (quoted) sb.append("'");
          });

  // ==================== Binary (hex literal, always quoted) ====================

  public static final DuckDbStringifier<byte[]> blob =
      instance(
          (bytes, sb, quoted) -> {
            sb.append("'\\x");
            for (byte b : bytes) {
              sb.append(String.format("%02x", b));
            }
            sb.append("'");
          });
}
