package typr.runtime;

import java.util.Optional;

/**
 * Describes the SQL type name for DuckDB types. DuckDB has a rich type system including composite
 * types like LIST, MAP, etc. This is a sealed interface with specific implementations for each type
 * structure.
 */
public sealed interface DuckDbTypename<A> extends DbTypename<A> {
  @Override
  String sqlType();

  /**
   * Create a LIST type from this element type. For example, VARCHAR becomes VARCHAR[] (or
   * LIST(VARCHAR)).
   */
  DuckDbTypename<java.util.List<A>> list();

  /**
   * Create an array type from this element type (for Java array compatibility). Uses the same SQL
   * representation as list() but with Java arrays.
   */
  DuckDbTypename<A[]> array();

  /**
   * Create a MAP type from this key type and another value type. For example,
   * VARCHAR.mapTo(INTEGER) becomes MAP(VARCHAR, INTEGER).
   */
  <V> DuckDbTypename<java.util.Map<A, V>> mapTo(DuckDbTypename<V> valueType);

  /** Rename the type (for aliases like TEXT -> VARCHAR). */
  DuckDbTypename<A> renamed(String newName);

  /** Rename the type, dropping any precision/scale. */
  DuckDbTypename<A> renamedDropPrecision(String newName);

  /** Create an Optional variant of this type. */
  default DuckDbTypename<Optional<A>> opt() {
    return new Opt<>(this);
  }

  /** Cast to a different Java type (unsafe but necessary for type composition). */
  @SuppressWarnings("unchecked")
  default <B> DuckDbTypename<B> as() {
    return (DuckDbTypename<B>) this;
  }

  // ==================== Implementations ====================

  /** Base type with optional precision and scale. Examples: VARCHAR, DECIMAL(10,2), INTEGER */
  record Base<A>(String baseType, Optional<Integer> precision, Optional<Integer> scale)
      implements DuckDbTypename<A> {
    public Base(String baseType) {
      this(baseType, Optional.empty(), Optional.empty());
    }

    @Override
    public String sqlType() {
      if (precision.isPresent() && scale.isPresent()) {
        return baseType + "(" + precision.get() + ", " + scale.get() + ")";
      } else if (precision.isPresent()) {
        return baseType + "(" + precision.get() + ")";
      }
      return baseType;
    }

    @Override
    public DuckDbTypename<java.util.List<A>> list() {
      return new ListOf<>(this);
    }

    @Override
    public DuckDbTypename<A[]> array() {
      return new ListOf<>(this).as();
    }

    @Override
    public <V> DuckDbTypename<java.util.Map<A, V>> mapTo(DuckDbTypename<V> valueType) {
      return new MapOf<>(this, valueType);
    }

    @Override
    public DuckDbTypename<A> renamed(String newName) {
      return new Base<>(newName, precision, scale);
    }

    @Override
    public DuckDbTypename<A> renamedDropPrecision(String newName) {
      return new Base<>(newName);
    }
  }

  /**
   * LIST type wrapping an element type. LIST is ALWAYS variable-length. Rendered as
   * "element_type[]" (e.g., VARCHAR[] or INTEGER[])
   *
   * <p>Use ARRAY for fixed-length arrays (e.g., embedding vectors).
   */
  record ListOf<A>(DuckDbTypename<A> elementType) implements DuckDbTypename<java.util.List<A>> {
    @Override
    public String sqlType() {
      return elementType.sqlType() + "[]";
    }

    @Override
    public DuckDbTypename<java.util.List<java.util.List<A>>> list() {
      // Nested list: LIST(LIST(A)) becomes A[][]
      return new ListOf<>(this);
    }

    @Override
    public DuckDbTypename<java.util.List<A>[]> array() {
      return new ListOf<>(this).as();
    }

    @Override
    public <V> DuckDbTypename<java.util.Map<java.util.List<A>, V>> mapTo(
        DuckDbTypename<V> valueType) {
      return new MapOf<>(this, valueType);
    }

    @Override
    public DuckDbTypename<java.util.List<A>> renamed(String newName) {
      return new ListOf<>(elementType.renamed(newName));
    }

    @Override
    public DuckDbTypename<java.util.List<A>> renamedDropPrecision(String newName) {
      return new ListOf<>(elementType.renamedDropPrecision(newName));
    }
  }

  /**
   * MAP type with key and value types. Rendered as "MAP(key_type, value_type)" (e.g., MAP(VARCHAR,
   * INTEGER))
   */
  record MapOf<K, V>(DuckDbTypename<K> keyType, DuckDbTypename<V> valueType)
      implements DuckDbTypename<java.util.Map<K, V>> {
    @Override
    public String sqlType() {
      return "MAP(" + keyType.sqlType() + ", " + valueType.sqlType() + ")";
    }

    @Override
    public DuckDbTypename<java.util.List<java.util.Map<K, V>>> list() {
      return new ListOf<>(this);
    }

    @Override
    public DuckDbTypename<java.util.Map<K, V>[]> array() {
      return new ListOf<>(this).as();
    }

    @Override
    public <V2> DuckDbTypename<java.util.Map<java.util.Map<K, V>, V2>> mapTo(
        DuckDbTypename<V2> valueType2) {
      // MAP as a key is unusual but valid
      return new MapOf<>(this, valueType2);
    }

    @Override
    public DuckDbTypename<java.util.Map<K, V>> renamed(String newName) {
      // For MAP, renaming affects the key type
      return new MapOf<>(keyType.renamed(newName), valueType);
    }

    @Override
    public DuckDbTypename<java.util.Map<K, V>> renamedDropPrecision(String newName) {
      return new MapOf<>(keyType.renamedDropPrecision(newName), valueType);
    }
  }

  /**
   * Fixed-size ARRAY type wrapping an element type. ARRAY is ALWAYS fixed-length. Rendered as
   * "element_type[size]" (e.g., FLOAT[3] for 3D vectors)
   *
   * <p>Use LIST for variable-length arrays.
   *
   * <p>From DuckDB docs: "All fields in the column must have the same length." Typically used for
   * embedding vectors (word embeddings, image embeddings).
   */
  record ArrayOf<A>(DuckDbTypename<A> elementType, int size)
      implements DuckDbTypename<java.util.List<A>> {
    @Override
    public String sqlType() {
      return elementType.sqlType() + "[" + size + "]";
    }

    @Override
    public DuckDbTypename<java.util.List<java.util.List<A>>> list() {
      return new ListOf<>(this);
    }

    @Override
    public DuckDbTypename<java.util.List<A>[]> array() {
      return new ListOf<>(this).as();
    }

    @Override
    public <V> DuckDbTypename<java.util.Map<java.util.List<A>, V>> mapTo(
        DuckDbTypename<V> valueType) {
      return new MapOf<>(this, valueType);
    }

    @Override
    public DuckDbTypename<java.util.List<A>> renamed(String newName) {
      return new ArrayOf<>(elementType.renamed(newName), size);
    }

    @Override
    public DuckDbTypename<java.util.List<A>> renamedDropPrecision(String newName) {
      return new ArrayOf<>(elementType.renamedDropPrecision(newName), size);
    }
  }

  /** STRUCT type with named fields. Rendered as "STRUCT(field1 type1, field2 type2, ...)" */
  record StructOf<A>(String name, java.util.List<StructField> fields) implements DuckDbTypename<A> {
    public record StructField(String name, DuckDbTypename<?> type) {}

    @Override
    public String sqlType() {
      StringBuilder sb = new StringBuilder("STRUCT(");
      for (int i = 0; i < fields.size(); i++) {
        if (i > 0) sb.append(", ");
        StructField f = fields.get(i);
        // Quote field names that need it
        if (needsQuoting(f.name())) {
          sb.append("\"").append(f.name()).append("\"");
        } else {
          sb.append(f.name());
        }
        sb.append(" ").append(f.type().sqlType());
      }
      sb.append(")");
      return sb.toString();
    }

    private static boolean needsQuoting(String name) {
      // Quote if contains special chars or starts with digit
      if (name.isEmpty()) return true;
      char first = name.charAt(0);
      if (Character.isDigit(first)) return true;
      for (char c : name.toCharArray()) {
        if (!Character.isLetterOrDigit(c) && c != '_') return true;
      }
      return false;
    }

    @Override
    public DuckDbTypename<java.util.List<A>> list() {
      return new ListOf<>(this);
    }

    @Override
    public DuckDbTypename<A[]> array() {
      return new ListOf<>(this).as();
    }

    @Override
    public <V> DuckDbTypename<java.util.Map<A, V>> mapTo(DuckDbTypename<V> valueType) {
      return new MapOf<>(this, valueType);
    }

    @Override
    public DuckDbTypename<A> renamed(String newName) {
      return new StructOf<>(newName, fields);
    }

    @Override
    public DuckDbTypename<A> renamedDropPrecision(String newName) {
      return new StructOf<>(newName, fields);
    }

    /** Get the generic form (loses the name). */
    @SuppressWarnings("unchecked")
    public <B> DuckDbTypename<B> asGeneric() {
      return (DuckDbTypename<B>) this;
    }
  }

  /** UNION type with tagged alternatives. Rendered as "UNION(tag1 type1, tag2 type2, ...)" */
  record UnionOf<A>(String name, java.util.List<UnionMember> members) implements DuckDbTypename<A> {
    public record UnionMember(String tag, DuckDbTypename<?> type) {}

    @Override
    public String sqlType() {
      StringBuilder sb = new StringBuilder("UNION(");
      for (int i = 0; i < members.size(); i++) {
        if (i > 0) sb.append(", ");
        UnionMember m = members.get(i);
        sb.append(m.tag()).append(" ").append(m.type().sqlType());
      }
      sb.append(")");
      return sb.toString();
    }

    @Override
    public DuckDbTypename<java.util.List<A>> list() {
      return new ListOf<>(this);
    }

    @Override
    public DuckDbTypename<A[]> array() {
      return new ListOf<>(this).as();
    }

    @Override
    public <V> DuckDbTypename<java.util.Map<A, V>> mapTo(DuckDbTypename<V> valueType) {
      return new MapOf<>(this, valueType);
    }

    @Override
    public DuckDbTypename<A> renamed(String newName) {
      return new UnionOf<>(newName, members);
    }

    @Override
    public DuckDbTypename<A> renamedDropPrecision(String newName) {
      return new UnionOf<>(newName, members);
    }

    /** Get the generic form (loses the name). */
    @SuppressWarnings("unchecked")
    public <B> DuckDbTypename<B> asGeneric() {
      return (DuckDbTypename<B>) this;
    }
  }

  /** Optional wrapper (nullable type). The SQL type is the same as the underlying type. */
  record Opt<A>(DuckDbTypename<A> of) implements DuckDbTypename<Optional<A>> {
    @Override
    public String sqlType() {
      return of.sqlType();
    }

    @Override
    public DuckDbTypename<java.util.List<Optional<A>>> list() {
      return new ListOf<>(this);
    }

    @Override
    public DuckDbTypename<Optional<A>[]> array() {
      return new ListOf<>(this).as();
    }

    @Override
    public <V> DuckDbTypename<java.util.Map<Optional<A>, V>> mapTo(DuckDbTypename<V> valueType) {
      return new MapOf<>(this, valueType);
    }

    @Override
    public DuckDbTypename<Optional<A>> renamed(String newName) {
      return new Opt<>(of.renamed(newName));
    }

    @Override
    public DuckDbTypename<Optional<A>> renamedDropPrecision(String newName) {
      return new Opt<>(of.renamedDropPrecision(newName));
    }
  }

  // ==================== Factory Methods ====================

  /** Create a base type with no precision/scale. */
  static <T> DuckDbTypename<T> of(String sqlType) {
    return new Base<>(sqlType);
  }

  /** Create a base type with precision (e.g., VARCHAR(255)). */
  static <T> DuckDbTypename<T> of(String sqlType, int precision) {
    return new Base<>(sqlType, Optional.of(precision), Optional.empty());
  }

  /** Create a base type with precision and scale (e.g., DECIMAL(10, 2)). */
  static <T> DuckDbTypename<T> of(String sqlType, int precision, int scale) {
    return new Base<>(sqlType, Optional.of(precision), Optional.of(scale));
  }
}
