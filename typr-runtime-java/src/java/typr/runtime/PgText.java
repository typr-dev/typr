package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.postgresql.util.PGobject;

/**
 * This is `Text` ported from doobie.
 *
 * <p>It is used to encode rows in string format for the COPY command.
 *
 * <p>
 */
public abstract class PgText<A> implements DbText<A> {
  public abstract void unsafeEncode(A a, StringBuilder sb);

  public abstract void unsafeArrayEncode(A a, StringBuilder sb);

  public <B> PgText<B> contramap(Function<B, A> f) {
    var self = this;
    return instance(
        (b, sb) -> self.unsafeEncode(f.apply(b), sb),
        (b, sb) -> self.unsafeArrayEncode(f.apply(b), sb));
  }

  public PgText<Optional<A>> opt() {
    var self = this;
    return instance(
        (a, sb) -> {
          if (a.isPresent()) self.unsafeEncode(a.get(), sb);
          else sb.append(PgText.NULL);
        },
        (a, sb) -> {
          if (a.isPresent()) self.unsafeArrayEncode(a.get(), sb);
          else sb.append(PgText.NULL);
        });
  }

  public PgText<A[]> array() {
    var self = this;
    return PgText.instance(
        (as, sb) -> {
          var first = true;
          sb.append("{");
          for (var a : as) {
            if (first) first = false;
            else sb.append(',');
            self.unsafeArrayEncode(a, sb);
          }
          sb.append('}');
        });
  }

  public static char DELIMETER = '\t';
  public static String NULL = "\\N";

  public static <A> PgText<A> instance(BiConsumer<A, StringBuilder> f) {
    return instance(f, f);
  }

  public static <A> PgText<A> instance(
      BiConsumer<A, StringBuilder> f, BiConsumer<A, StringBuilder> arrayF) {
    return new PgText<>() {
      @Override
      public void unsafeEncode(A a, StringBuilder sb) {
        f.accept(a, sb);
      }

      @Override
      public void unsafeArrayEncode(A a, StringBuilder sb) {
        arrayF.accept(a, sb);
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static <A> PgText<A> from(RowParser<A> rowParser) {
    return instance(
        (row, sb) -> {
          var encoded = rowParser.encode().apply(row);
          for (int i = 0; i < encoded.length; i++) {
            if (i > 0) {
              sb.append(PgText.DELIMETER);
            }
            DbText<Object> text = (DbText<Object>) rowParser.columns().get(i).text();
            text.unsafeEncode(encoded[i], sb);
          }
        });
  }

  public static <A> PgText<A> instanceToString() {
    return textString.contramap(Object::toString);
  }

  public static final PgText<String> textString =
      instance(StringImpl::unsafeEncode, StringImpl::unsafeArrayEncode);
  public static final PgText<Integer> textInteger = PgText.instance((n, sb) -> sb.append(n));
  public static final PgText<Short> textShort = PgText.instance((n, sb) -> sb.append(n));
  public static final PgText<Long> textLong = PgText.instance((n, sb) -> sb.append(n));
  public static final PgText<Float> textFloat = PgText.instance((n, sb) -> sb.append(n));
  public static final PgText<Double> textDouble = PgText.instance((n, sb) -> sb.append(n));
  public static final PgText<BigDecimal> textBigDecimal = PgText.instance((n, sb) -> sb.append(n));
  public static final PgText<Boolean> textBoolean = PgText.instance((n, sb) -> sb.append(n));
  public static final PgText<UUID> textUuid = PgText.instance((n, sb) -> sb.append(n));
  public static final PgText<byte[]> textByteArray =
      PgText.instance(
          (bs, sb) -> {
            sb.append("\\\\x");
            if (bs.length > 0) {
              var hex = new BigInteger(1, bs).toString(16);
              var pad = bs.length * 2 - hex.length();
              sb.append("0".repeat(Math.max(0, pad)));
              sb.append(hex);
            }
          });

  public static <T extends PGobject> PgText<T> textPGobject() {
    return PgText.textString.contramap(
        x -> {
          // let's be defensive since it seems there are so many possibilities for nulls
          if (x == null || x.isNull()) return "null";
          else return x.toString();
        });
  }

  public static final PgText<Map<String, String>> textMapStringString =
      PgText.instance(
          (m, sb) -> {
            var first = true;
            for (var e : m.entrySet()) {
              if (first) first = false;
              else sb.append(',');
              StringImpl.unsafeEncode(e.getKey(), sb);
              sb.append("=>");
              StringImpl.unsafeEncode(e.getValue(), sb);
            }
          });

  private interface StringImpl {
    // Standard char encodings that don't differ in array context
    static void stdChar(char c, StringBuilder sb) {
      switch (c) {
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
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
        case 0x0b:
          sb.append("\\v");
          break;
        default:
          sb.append(c);
          break;
      }
    }

    static void unsafeEncode(String s, StringBuilder sb) {
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '\\') {
          sb.append("\\\\"); // backslash must be doubled
        } else {
          stdChar(c, sb);
        }
      }
    }

    // I am not confident about this encoder. Postgres seems not to be able to cope with low
    // control characters or high whitespace characters so these are simply filtered out in the
    // tests. It should accommodate arrays of non-pathological strings but it would be nice to
    // have a complete specification of what's actually happening.
    static void unsafeArrayEncode(String s, StringBuilder sb) {
      sb.append('"');
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
          case '\"':
            sb.append("\\\\\\\"");
            break;
          case '\\':
            sb.append("\\\\\\\\\\\\\\\\"); // srsly
            break;
          default:
            stdChar(c, sb);
            break;
        }
      }
      sb.append('"');
    }
  }

  public static final PgText<Object> NotWorking =
      new PgText<>() {
        @Override
        public void unsafeEncode(Object t, StringBuilder sb) {
          throw new UnsupportedOperationException("streaming COPY is not supported for this type");
        }

        @Override
        public void unsafeArrayEncode(Object t, StringBuilder sb) {
          throw new UnsupportedOperationException("streaming COPY is not supported for this type");
        }
      };

  @Deprecated
  public static <T> PgText<T> NotWorking() {
    return (PgText<T>) NotWorking;
  }

  // Unboxed primitive array text encoders
  public static final PgText<boolean[]> boolArrayUnboxed =
      instance(
          (arr, sb) -> {
            sb.append('{');
            for (int i = 0; i < arr.length; i++) {
              if (i > 0) sb.append(',');
              sb.append(arr[i] ? 't' : 'f');
            }
            sb.append('}');
          });

  public static final PgText<short[]> shortArrayUnboxed =
      instance(
          (arr, sb) -> {
            sb.append('{');
            for (int i = 0; i < arr.length; i++) {
              if (i > 0) sb.append(',');
              sb.append(arr[i]);
            }
            sb.append('}');
          });

  public static final PgText<int[]> intArrayUnboxed =
      instance(
          (arr, sb) -> {
            sb.append('{');
            for (int i = 0; i < arr.length; i++) {
              if (i > 0) sb.append(',');
              sb.append(arr[i]);
            }
            sb.append('}');
          });

  public static final PgText<long[]> longArrayUnboxed =
      instance(
          (arr, sb) -> {
            sb.append('{');
            for (int i = 0; i < arr.length; i++) {
              if (i > 0) sb.append(',');
              sb.append(arr[i]);
            }
            sb.append('}');
          });

  public static final PgText<float[]> floatArrayUnboxed =
      instance(
          (arr, sb) -> {
            sb.append('{');
            for (int i = 0; i < arr.length; i++) {
              if (i > 0) sb.append(',');
              sb.append(arr[i]);
            }
            sb.append('}');
          });

  public static final PgText<double[]> doubleArrayUnboxed =
      instance(
          (arr, sb) -> {
            sb.append('{');
            for (int i = 0; i < arr.length; i++) {
              if (i > 0) sb.append(',');
              sb.append(arr[i]);
            }
            sb.append('}');
          });
}
