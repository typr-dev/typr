package typr.runtime;

import java.util.Optional;
import java.util.function.Function;

/**
 * Combines DuckDB type name, read, write, stringification, and JSON encoding for a type. Similar to
 * PgType but for DuckDB.
 */
public record DuckDbType<A>(
    DuckDbTypename<A> typename,
    DuckDbRead<A> read,
    DuckDbWrite<A> write,
    DuckDbStringifier<A> stringifier,
    DuckDbJson<A> duckDbJson,
    DuckDbText<A> duckDbText)
    implements DbType<A> {
  /** Constructor for backwards compatibility - uses stringifier-based text encoding. */
  public DuckDbType(
      DuckDbTypename<A> typename,
      DuckDbRead<A> read,
      DuckDbWrite<A> write,
      DuckDbStringifier<A> stringifier,
      DuckDbJson<A> duckDbJson) {
    this(
        typename,
        read,
        write,
        stringifier,
        duckDbJson,
        DuckDbText.instance((a, sb) -> stringifier.unsafeEncode(a, sb, false)));
  }

  @Override
  public DbText<A> text() {
    return duckDbText;
  }

  @Override
  public DbJson<A> json() {
    return duckDbJson;
  }

  public Fragment.Value<A> encode(A value) {
    return new Fragment.Value<>(value, this);
  }

  public DuckDbType<A> withTypename(DuckDbTypename<A> typename) {
    return new DuckDbType<>(typename, read, write, stringifier, duckDbJson);
  }

  public DuckDbType<A> withTypename(String sqlType) {
    return withTypename(DuckDbTypename.of(sqlType));
  }

  public DuckDbType<A> renamed(String value) {
    return withTypename(typename.renamed(value));
  }

  public DuckDbType<A> renamedDropPrecision(String value) {
    return withTypename(typename.renamedDropPrecision(value));
  }

  public DuckDbType<A> withRead(DuckDbRead<A> read) {
    return new DuckDbType<>(typename, read, write, stringifier, duckDbJson);
  }

  public DuckDbType<A> withWrite(DuckDbWrite<A> write) {
    return new DuckDbType<>(typename, read, write, stringifier, duckDbJson);
  }

  public DuckDbType<A> withStringifier(DuckDbStringifier<A> stringifier) {
    return new DuckDbType<>(typename, read, write, stringifier, duckDbJson);
  }

  public DuckDbType<A> withJson(DuckDbJson<A> json) {
    return new DuckDbType<>(typename, read, write, stringifier, json);
  }

  public DuckDbType<Optional<A>> opt() {
    return new DuckDbType<>(
        typename.opt(), read.opt(), write.opt(typename), stringifier.opt(), duckDbJson.opt());
  }

  /**
   * Create an array type from this element type. Uses SQL literal conversion for writing, which
   * works for all types. The resulting type can be used with bimap() for custom wrapper types.
   *
   * <p>Note: DuckDB internally uses LIST, but we expose it as Java arrays for consistency with
   * PostgreSQL.
   *
   * @return DuckDbType for array of this element type
   */
  @SuppressWarnings("unchecked")
  public DuckDbType<A[]> array() {
    DuckDbTypename<A[]> arrayTypename = typename.array();
    // Read: DuckDB JDBC returns Object[], cast elements
    DuckDbRead<A[]> arrayRead =
        DuckDbRead.of(
            (rs, idx) -> {
              java.sql.Array arr = rs.getArray(idx);
              if (arr == null) return null;
              Object[] elements = (Object[]) arr.getArray();
              A[] result = (A[]) new Object[elements.length];
              for (int i = 0; i < elements.length; i++) {
                result[i] = (A) elements[i];
              }
              return result;
            });
    // Write: convert array to List, use existing list writer
    DuckDbWrite<A[]> arrayWrite =
        DuckDbWrite.writeListViaSqlLiteral(typename.sqlType(), stringifier)
            .contramap(arr -> java.util.Arrays.asList(arr));
    DuckDbStringifier<A[]> arrayStringifier =
        DuckDbStringifier.instance(
            (arr, sb, quoted) -> {
              if (arr.length == 0) {
                sb.append("[]");
                return;
              }
              sb.append("[");
              boolean first = true;
              for (A elem : arr) {
                if (!first) sb.append(", ");
                first = false;
                stringifier.unsafeEncode(elem, sb, true);
              }
              sb.append("]");
            });
    // JSON: reuse list codec and convert
    DuckDbJson<A[]> arrayJson =
        new DuckDbJson<>() {
          private final DuckDbJson<java.util.List<A>> listJson = duckDbJson.list();

          @Override
          public typr.data.JsonValue toJson(A[] value) {
            return listJson.toJson(java.util.Arrays.asList(value));
          }

          @Override
          @SuppressWarnings("unchecked")
          public A[] fromJson(typr.data.JsonValue json) {
            java.util.List<A> list = listJson.fromJson(json);
            return (A[]) list.toArray();
          }
        };
    return new DuckDbType<>(arrayTypename, arrayRead, arrayWrite, arrayStringifier, arrayJson);
  }

  /**
   * Simple mapTo() method for DSL use - returns this type's info for use in MAP type references.
   * This is used by generated DSL code for type signatures only (not actual JDBC operations).
   */
  public <V> DuckDbType<A> mapTo(DuckDbType<V> valueType) {
    return this;
  }

  /**
   * Create a variable-length LIST type from this element type using native JNI array writing. LIST
   * is ALWAYS variable-length - each row can have a different number of elements. Use this for
   * types that DuckDB JNI handles natively: Boolean, Byte, Short, Integer, Long, Float, Double,
   * String.
   *
   * <p>For fixed-length arrays (e.g., embedding vectors), use arrayNative() instead.
   *
   * @param elementClass the Java class of elements (needed for JDBC reading)
   * @param toArray function to create typed array for DuckDBUserArray
   * @return DuckDbType for variable-length List of this element type
   */
  public DuckDbType<java.util.List<A>> listNative(
      Class<A> elementClass, java.util.function.IntFunction<A[]> toArray) {
    DuckDbTypename<java.util.List<A>> listTypename = typename.list();
    DuckDbRead<java.util.List<A>> listRead = DuckDbRead.readList(elementClass);
    DuckDbWrite<java.util.List<A>> listWrite = DuckDbWrite.writeList(typename.sqlType(), toArray);
    DuckDbStringifier<java.util.List<A>> listStringifier =
        DuckDbStringifier.instance(
            (list, sb, quoted) -> {
              if (list.isEmpty()) {
                sb.append("[]");
                return;
              }
              sb.append("[");
              boolean first = true;
              for (A elem : list) {
                if (!first) sb.append(", ");
                first = false;
                stringifier.unsafeEncode(elem, sb, true);
              }
              sb.append("]");
            });
    return new DuckDbType<>(listTypename, listRead, listWrite, listStringifier, duckDbJson.list());
  }

  /**
   * Create a variable-length LIST type from this element type using SQL literal string conversion
   * for writing. LIST is ALWAYS variable-length - each row can have a different number of elements.
   * Use this for types that DuckDB JNI doesn't handle natively: UUID, LocalTime, LocalDate,
   * LocalDateTime, OffsetDateTime, BigDecimal, BigInteger, Duration.
   *
   * <p>For fixed-length arrays (e.g., embedding vectors), use arrayViaSqlLiteral() instead.
   *
   * @param elementClass the Java class of elements (what DuckDB JDBC returns)
   * @param elementStringifier how to format elements as SQL literals
   * @return DuckDbType for variable-length List of this element type
   */
  public DuckDbType<java.util.List<A>> listViaSqlLiteral(
      Class<A> elementClass, DuckDbStringifier<A> elementStringifier) {
    DuckDbTypename<java.util.List<A>> listTypename = typename.list();
    DuckDbRead<java.util.List<A>> listRead = DuckDbRead.readList(elementClass);
    DuckDbWrite<java.util.List<A>> listWrite =
        DuckDbWrite.writeListViaSqlLiteral(typename.sqlType(), elementStringifier);
    DuckDbStringifier<java.util.List<A>> listStringifier =
        DuckDbStringifier.instance(
            (list, sb, quoted) -> {
              if (list.isEmpty()) {
                sb.append("[]");
                return;
              }
              sb.append("[");
              boolean first = true;
              for (A elem : list) {
                if (!first) sb.append(", ");
                first = false;
                elementStringifier.unsafeEncode(elem, sb, true);
              }
              sb.append("]");
            });
    return new DuckDbType<>(listTypename, listRead, listWrite, listStringifier, duckDbJson.list());
  }

  /**
   * Create a LIST type from this element type using SQL literal string conversion for writing, with
   * a converter for reading when DuckDB JDBC returns a different type than expected.
   *
   * <p>Use this when the wire type (what DuckDB JDBC returns in arrays) differs from the target
   * type. For example, TIMESTAMP[] returns java.sql.Timestamp elements, not LocalDateTime.
   *
   * @param wireClass the Java class that DuckDB JDBC actually returns in arrays
   * @param wireToElement function to convert wire type to target element type
   * @param elementStringifier how to format elements as SQL literals
   * @param <W> wire type
   * @return DuckDbType for List of this element type
   */
  public <W> DuckDbType<java.util.List<A>> listViaSqlLiteral(
      Class<W> wireClass,
      java.util.function.Function<W, A> wireToElement,
      DuckDbStringifier<A> elementStringifier) {
    DuckDbTypename<java.util.List<A>> listTypename = typename.list();
    DuckDbRead<java.util.List<A>> listRead = DuckDbRead.readListConverted(wireToElement);
    DuckDbWrite<java.util.List<A>> listWrite =
        DuckDbWrite.writeListViaSqlLiteral(typename.sqlType(), elementStringifier);
    DuckDbStringifier<java.util.List<A>> listStringifier =
        DuckDbStringifier.instance(
            (list, sb, quoted) -> {
              if (list.isEmpty()) {
                sb.append("[]");
                return;
              }
              sb.append("[");
              boolean first = true;
              for (A elem : list) {
                if (!first) sb.append(", ");
                first = false;
                elementStringifier.unsafeEncode(elem, sb, true);
              }
              sb.append("]");
            });
    return new DuckDbType<>(listTypename, listRead, listWrite, listStringifier, duckDbJson.list());
  }

  /**
   * Create a fixed-size ARRAY type from this element type using native JNI array writing. ARRAY is
   * ALWAYS fixed-length - every row must have exactly 'size' elements. Use this for embedding
   * vectors (word embeddings, image embeddings) and other fixed-size arrays.
   *
   * <p>For variable-length lists, use listNative() instead.
   *
   * <p>From JDBC perspective, ARRAY and LIST are identical - both return DuckDBArray. The
   * difference is only in the typename (includes size) and database-side validation.
   *
   * @param size the fixed size of the array (every row must have exactly this many elements)
   * @param elementClass the Java class of elements (needed for JDBC reading)
   * @param toArray function to create typed array for DuckDBUserArray
   * @return DuckDbType for fixed-size array of this element type
   */
  public DuckDbType<java.util.List<A>> arrayNative(
      int size, Class<A> elementClass, java.util.function.IntFunction<A[]> toArray) {
    DuckDbTypename<java.util.List<A>> arrayTypename = new DuckDbTypename.ArrayOf<>(typename, size);
    DuckDbRead<java.util.List<A>> arrayRead = DuckDbRead.readList(elementClass);
    DuckDbWrite<java.util.List<A>> arrayWrite = DuckDbWrite.writeList(typename.sqlType(), toArray);
    DuckDbStringifier<java.util.List<A>> arrayStringifier =
        DuckDbStringifier.instance(
            (list, sb, quoted) -> {
              if (list.isEmpty()) {
                sb.append("[]");
                return;
              }
              sb.append("[");
              boolean first = true;
              for (A elem : list) {
                if (!first) sb.append(", ");
                first = false;
                stringifier.unsafeEncode(elem, sb, true);
              }
              sb.append("]");
            });
    return new DuckDbType<>(
        arrayTypename, arrayRead, arrayWrite, arrayStringifier, duckDbJson.list());
  }

  public DuckDbType<java.util.List<A>> arrayViaSqlLiteral(
      int size, Class<A> elementClass, DuckDbStringifier<A> elementStringifier) {
    DuckDbTypename<java.util.List<A>> arrayTypename = new DuckDbTypename.ArrayOf<>(typename, size);
    DuckDbRead<java.util.List<A>> arrayRead = DuckDbRead.readList(elementClass);
    DuckDbWrite<java.util.List<A>> arrayWrite =
        DuckDbWrite.writeListViaSqlLiteral(typename.sqlType(), elementStringifier);
    DuckDbStringifier<java.util.List<A>> arrayStringifier =
        DuckDbStringifier.instance(
            (list, sb, quoted) -> {
              if (list.isEmpty()) {
                sb.append("[]");
                return;
              }
              sb.append("[");
              boolean first = true;
              for (A elem : list) {
                if (!first) sb.append(", ");
                first = false;
                elementStringifier.unsafeEncode(elem, sb, true);
              }
              sb.append("]");
            });
    return new DuckDbType<>(
        arrayTypename, arrayRead, arrayWrite, arrayStringifier, duckDbJson.list());
  }

  public <V> DuckDbType<java.util.Map<A, V>> mapToNative(
      DuckDbType<V> valueType, Class<A> keyClass, Class<V> valueClass) {
    DuckDbTypename<java.util.Map<A, V>> mapTypename = typename.mapTo(valueType.typename);
    String sqlType = mapTypename.sqlType();
    DuckDbRead<java.util.Map<A, V>> mapRead =
        DuckDbRead.readMap(read, keyClass, valueType.read, valueClass);
    DuckDbWrite<java.util.Map<A, V>> mapWrite = DuckDbWrite.writeMap(sqlType);
    DuckDbStringifier<java.util.Map<A, V>> mapStringifier =
        DuckDbStringifier.instance(
            (map, sb, quoted) -> {
              if (map.isEmpty()) {
                sb.append("{}");
                return;
              }
              sb.append("{");
              boolean first = true;
              for (var entry : map.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                stringifier.unsafeEncode(entry.getKey(), sb, true);
                sb.append(": ");
                valueType.stringifier.unsafeEncode(entry.getValue(), sb, true);
              }
              sb.append("}");
            });
    return new DuckDbType<>(
        mapTypename,
        mapRead,
        mapWrite,
        mapStringifier,
        DuckDbTypes.mapJson(duckDbJson, valueType.duckDbJson));
  }

  public <V> DuckDbType<java.util.Map<A, V>> mapToViaSqlLiteral(
      DuckDbType<V> valueType,
      Class<A> keyClass,
      Class<V> valueClass,
      DuckDbStringifier<A> keyStringifier,
      DuckDbStringifier<V> valueStringifier) {
    DuckDbTypename<java.util.Map<A, V>> mapTypename = typename.mapTo(valueType.typename);
    String sqlType = mapTypename.sqlType();
    DuckDbRead<java.util.Map<A, V>> mapRead =
        DuckDbRead.readMap(read, keyClass, valueType.read, valueClass);
    DuckDbWrite<java.util.Map<A, V>> mapWrite =
        DuckDbWrite.writeMapViaSqlLiteral(sqlType, keyStringifier, valueStringifier);
    DuckDbStringifier<java.util.Map<A, V>> mapStringifier =
        DuckDbStringifier.instance(
            (map, sb, quoted) -> {
              if (map.isEmpty()) {
                sb.append("{}");
                return;
              }
              sb.append("{");
              boolean first = true;
              for (var entry : map.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                keyStringifier.unsafeEncode(entry.getKey(), sb, true);
                sb.append(": ");
                valueStringifier.unsafeEncode(entry.getValue(), sb, true);
              }
              sb.append("}");
            });
    return new DuckDbType<>(
        mapTypename,
        mapRead,
        mapWrite,
        mapStringifier,
        DuckDbTypes.mapJson(duckDbJson, valueType.duckDbJson));
  }

  public <B> DuckDbType<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
    return new DuckDbType<>(
        typename.as(),
        read.map(f),
        write.contramap(g),
        stringifier.contramap(g),
        duckDbJson.bimap(f, g));
  }

  @Override
  public <B> DuckDbType<B> to(typr.dsl.Bijection<A, B> bijection) {
    return new DuckDbType<>(
        typename.as(),
        read.map(bijection::underlying),
        write.contramap(bijection::from),
        stringifier.contramap(bijection::from),
        duckDbJson.bimap(bijection::underlying, bijection::from));
  }

  public static <A> DuckDbType<A> of(
      String tpe, DuckDbRead<A> r, DuckDbWrite<A> w, DuckDbStringifier<A> s, DuckDbJson<A> j) {
    return new DuckDbType<>(DuckDbTypename.of(tpe), r, w, s, j);
  }

  public static <A> DuckDbType<A> of(
      DuckDbTypename<A> typename,
      DuckDbRead<A> r,
      DuckDbWrite<A> w,
      DuckDbStringifier<A> s,
      DuckDbJson<A> j) {
    return new DuckDbType<>(typename, r, w, s, j);
  }
}
