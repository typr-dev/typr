package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Text encoder for DuckDB COPY command.
 *
 * <p>Similar to PgText but adapted for DuckDB's text format. DuckDB COPY uses tab-delimited format
 * similar to PostgreSQL.
 */
public abstract class DuckDbText<A> implements DbText<A> {
  public abstract void unsafeEncode(A a, StringBuilder sb);

  public abstract void unsafeArrayEncode(A a, StringBuilder sb);

  public <B> DuckDbText<B> contramap(Function<B, A> f) {
    var self = this;
    return instance(
        (b, sb) -> self.unsafeEncode(f.apply(b), sb),
        (b, sb) -> self.unsafeArrayEncode(f.apply(b), sb));
  }

  public DuckDbText<Optional<A>> opt() {
    var self = this;
    return instance(
        (a, sb) -> {
          if (a.isPresent()) self.unsafeEncode(a.get(), sb);
          else sb.append(DuckDbText.NULL);
        },
        (a, sb) -> {
          if (a.isPresent()) self.unsafeArrayEncode(a.get(), sb);
          else sb.append(DuckDbText.NULL);
        });
  }

  public DuckDbText<A[]> array() {
    var self = this;
    return DuckDbText.instance(
        (as, sb) -> {
          var first = true;
          sb.append("[");
          for (var a : as) {
            if (first) first = false;
            else sb.append(',');
            self.unsafeArrayEncode(a, sb);
          }
          sb.append(']');
        });
  }

  public static char DELIMETER = '\t';
  public static String NULL = "\\N";

  public static <A> DuckDbText<A> instance(BiConsumer<A, StringBuilder> f) {
    return instance(f, f);
  }

  public static <A> DuckDbText<A> instance(
      BiConsumer<A, StringBuilder> f, BiConsumer<A, StringBuilder> arrayF) {
    return new DuckDbText<>() {
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
  public static <A> DuckDbText<A> from(RowParser<A> rowParser) {
    return instance(
        (row, sb) -> {
          var encoded = rowParser.encode().apply(row);
          for (int i = 0; i < encoded.length; i++) {
            if (i > 0) {
              sb.append(DuckDbText.DELIMETER);
            }
            DbText<Object> text = (DbText<Object>) rowParser.columns().get(i).text();
            text.unsafeEncode(encoded[i], sb);
          }
        });
  }

  public static <A> DuckDbText<A> instanceToString() {
    return textString.contramap(Object::toString);
  }

  public static final DuckDbText<String> textString =
      instance(StringImpl::unsafeEncode, StringImpl::unsafeArrayEncode);
  public static final DuckDbText<Integer> textInteger =
      DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<Short> textShort = DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<Byte> textByte = DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<Long> textLong = DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<Float> textFloat = DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<Double> textDouble = DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<BigDecimal> textBigDecimal =
      DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<BigInteger> textBigInteger =
      DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<Boolean> textBoolean =
      DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<UUID> textUuid = DuckDbText.instance((n, sb) -> sb.append(n));
  public static final DuckDbText<byte[]> textByteArray =
      DuckDbText.instance(
          (bs, sb) -> {
            sb.append("\\\\x");
            if (bs.length > 0) {
              var hex = new BigInteger(1, bs).toString(16);
              var pad = bs.length * 2 - hex.length();
              sb.append("0".repeat(Math.max(0, pad)));
              sb.append(hex);
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

    static void unsafeArrayEncode(String s, StringBuilder sb) {
      sb.append('\'');
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
          case '\'':
            sb.append("''");
            break;
          case '\\':
            sb.append("\\\\");
            break;
          default:
            stdChar(c, sb);
            break;
        }
      }
      sb.append('\'');
    }
  }

  public static final DuckDbText<Object> NotWorking =
      new DuckDbText<>() {
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
  public static <T> DuckDbText<T> NotWorking() {
    return (DuckDbText<T>) NotWorking;
  }
}
