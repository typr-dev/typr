package typr.runtime;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Encodes values to text format for SQL Server BULK INSERT command.
 *
 * <p>Similar to MariaText but adapted for SQL Server's text format.
 */
public abstract class SqlServerText<A> implements DbText<A> {
  public abstract void unsafeEncode(A a, StringBuilder sb);

  public <B> SqlServerText<B> contramap(Function<B, A> f) {
    var self = this;
    return instance((b, sb) -> self.unsafeEncode(f.apply(b), sb));
  }

  public SqlServerText<Optional<A>> opt() {
    var self = this;
    return instance(
        (a, sb) -> {
          if (a.isPresent()) self.unsafeEncode(a.get(), sb);
          else sb.append(SqlServerText.NULL);
        });
  }

  public static char DELIMETER = '\t';
  public static String NULL = "\\N";

  public static <A> SqlServerText<A> instance(BiConsumer<A, StringBuilder> f) {
    return new SqlServerText<>() {
      @Override
      public void unsafeEncode(A a, StringBuilder sb) {
        f.accept(a, sb);
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static <A> SqlServerText<A> from(RowParser<A> rowParser) {
    return instance(
        (row, sb) -> {
          var encoded = rowParser.encode().apply(row);
          for (int i = 0; i < encoded.length; i++) {
            if (i > 0) {
              sb.append(SqlServerText.DELIMETER);
            }
            DbText<Object> text = (DbText<Object>) rowParser.columns().get(i).text();
            text.unsafeEncode(encoded[i], sb);
          }
        });
  }

  public static <A> SqlServerText<A> instanceToString() {
    return textString.contramap(Object::toString);
  }

  private static void escapeString(String s, StringBuilder sb) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\0':
          sb.append("\\0");
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
        case '\\':
          sb.append("\\\\");
          break;
        default:
          sb.append(c);
      }
    }
  }

  // Basic type text encoders
  public static final SqlServerText<String> textString = instance((s, sb) -> escapeString(s, sb));
  public static final SqlServerText<Boolean> textBoolean = instanceToString();
  public static final SqlServerText<Short> textShort = instanceToString();
  public static final SqlServerText<Integer> textInteger = instanceToString();
  public static final SqlServerText<Long> textLong = instanceToString();
  public static final SqlServerText<Float> textFloat = instanceToString();
  public static final SqlServerText<Double> textDouble = instanceToString();
  public static final SqlServerText<BigDecimal> textBigDecimal = instanceToString();
  public static final SqlServerText<byte[]> textByteArray =
      instance((bytes, sb) -> sb.append(java.util.Base64.getEncoder().encodeToString(bytes)));
  public static final SqlServerText<UUID> textUUID = instanceToString();
  public static final SqlServerText<Object> textObject = instanceToString();

  // Spatial types
  public static final SqlServerText<com.microsoft.sqlserver.jdbc.Geography> textGeography =
      instanceToString(); // Uses toString() which returns WKT
  public static final SqlServerText<com.microsoft.sqlserver.jdbc.Geometry> textGeometry =
      instanceToString(); // Uses toString() which returns WKT
}
