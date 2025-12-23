package typr.runtime;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import typr.data.JsonValue;

/**
 * DuckDB UNION type support.
 *
 * <p>A UNION is a tagged union (sum type) that can hold one of several alternative values. Each
 * alternative has a tag name and a type. The current tag is tracked internally. Example: UNION(num
 * INTEGER, str VARCHAR)
 *
 * <p>In Java, we represent a UNION as a sealed interface with record implementations for each
 * alternative. This class provides the machinery to read/write UNIONs via JDBC.
 *
 * <p>Key insight from DuckDB JDBC: UNION values are returned as the actual value (unwrapped), not
 * as a Struct. The tag can be queried via union_tag(value) function.
 *
 * @param <A> the Java type representing this UNION (typically a sealed interface)
 */
public record DuckDbUnion<A>(
    DuckDbTypename.UnionOf<A> typename,
    List<Member<A, ?>> members,
    UnionReader<A> reader,
    UnionWriter<A> writer,
    DuckDbJson<A> json) {
  /**
   * A single member (alternative) in a UNION with wrapper/unwrapper functions.
   *
   * <p>The wrapper converts from the raw value to the union variant. The unwrapper extracts the raw
   * value from a union variant (returns null if wrong variant).
   *
   * @param <A> the union type (sealed interface)
   * @param <M> the member's value type
   */
  public record Member<A, M>(
      String tag,
      DuckDbType<M> type,
      Class<M> javaClass,
      Function<M, A> wrapper,
      Function<A, M> unwrapper) {
    /**
     * Try to unwrap this member from a union value. Returns null if the union value is not of this
     * member's variant.
     */
    public TaggedValue<M> tryUnwrap(A value) {
      M extracted = unwrapper.apply(value);
      return extracted != null ? new TaggedValue<>(tag, extracted) : null;
    }
  }

  /**
   * Functional interface for reading a UNION from tag + value. The reader receives the tag name and
   * the raw value (already converted by JDBC).
   */
  @FunctionalInterface
  public interface UnionReader<A> {
    A read(String tag, Object value) throws SQLException;
  }

  /** Result of extracting tag + value from a UNION instance. */
  public record TaggedValue<V>(String tag, V value) {}

  /** Functional interface for writing a UNION to tag + value. */
  @FunctionalInterface
  public interface UnionWriter<A> {
    TaggedValue<?> write(A value);
  }

  /** Create a DuckDbType for this UNION. */
  public DuckDbType<A> asType() {
    DuckDbRead<A> duckDbRead =
        DuckDbRead.of(
            (rs, idx) -> {
              Object obj = rs.getObject(idx);
              if (obj == null) return null;

              // DuckDB JDBC returns the actual value, not a wrapper.
              // We need to query the tag separately.
              // Unfortunately, we can't get the tag from the ResultSet directly.
              // The tag is part of the column metadata or needs to be queried via union_tag().

              // Strategy: infer tag from Java type
              String inferredTag = inferTag(obj);
              if (inferredTag != null) {
                return reader.read(inferredTag, obj);
              }

              throw new SQLException(
                  "Cannot determine UNION tag for value: " + obj + " (" + obj.getClass() + ")");
            });

    DuckDbWrite<A> duckDbWrite =
        new DuckDbWrite.Instance<>(DuckDbUnion::setTaggedValue, writer::write);

    DuckDbStringifier<A> stringifier =
        DuckDbStringifier.instance(
            (value, sb, quoted) -> {
              throw new UnsupportedOperationException("UNION stringification not yet implemented");
            });

    return new DuckDbType<>(typename.asGeneric(), duckDbRead, duckDbWrite, stringifier, json);
  }

  /** Create an optional version of this UNION type. */
  public DuckDbType<Optional<A>> asOptType() {
    return asType().opt();
  }

  /** Infer the tag from the Java type of the value. */
  private String inferTag(Object value) {
    for (Member<A, ?> member : members) {
      if (member.javaClass().isInstance(value)) {
        return member.tag();
      }
    }
    return null;
  }

  /** Set a tagged value on a PreparedStatement. */
  @SuppressWarnings("unchecked")
  private static <V> void setTaggedValue(java.sql.PreparedStatement ps, int idx, TaggedValue<V> tv)
      throws java.sql.SQLException {
    // We can't directly write a UNION value via JDBC.
    // The SQL must use union_value(tag := ?) syntax.
    // For now, just set the raw value - the SQL layer must handle tagging.
    Object value = tv.value();
    if (value == null) {
      ps.setNull(idx, java.sql.Types.OTHER);
    } else if (value instanceof String s) {
      ps.setString(idx, s);
    } else if (value instanceof Integer i) {
      ps.setInt(idx, i);
    } else if (value instanceof Long l) {
      ps.setLong(idx, l);
    } else if (value instanceof Double d) {
      ps.setDouble(idx, d);
    } else if (value instanceof Boolean b) {
      ps.setBoolean(idx, b);
    } else {
      ps.setObject(idx, value);
    }
  }

  // ========================================================================
  // Builder API for creating UNION types
  // ========================================================================

  /**
   * Create a UNION type builder.
   *
   * @param <A> the union type (sealed interface)
   */
  public static <A> Builder<A> builder(String unionName) {
    return new Builder<>(unionName);
  }

  public static class Builder<A> {
    private final String unionName;
    private final java.util.List<Member<A, ?>> members = new java.util.ArrayList<>();

    Builder(String unionName) {
      this.unionName = unionName;
    }

    /**
     * Add a member with wrapper and unwrapper functions.
     *
     * <p>The wrapper creates the union variant from a raw value. The unwrapper extracts the raw
     * value (or null if wrong variant).
     *
     * @param tag the tag name in SQL (e.g., "num")
     * @param type the DuckDbType for the member's value
     * @param javaClass the Java class of the raw value (for type inference)
     * @param wrapper function to create union variant from raw value
     * @param unwrapper function to extract raw value (returns null if wrong variant)
     */
    public <M> Builder<A> member(
        String tag,
        DuckDbType<M> type,
        Class<M> javaClass,
        Function<M, A> wrapper,
        Function<A, M> unwrapper) {
      members.add(new Member<>(tag, type, javaClass, wrapper, unwrapper));
      return this;
    }

    /**
     * Build the DuckDbUnion with auto-derived reader, writer, and JSON codec.
     *
     * <p>This method auto-generates: - Reader: infers tag from Java type, applies appropriate
     * wrapper - Writer: tries each unwrapper until one succeeds - JSON: standard format {"tag":
     * "name", "value": ...}
     */
    public DuckDbUnion<A> build() {
      List<DuckDbTypename.UnionOf.UnionMember> typenameMembers =
          members.stream()
              .map(m -> new DuckDbTypename.UnionOf.UnionMember(m.tag(), m.type().typename()))
              .toList();

      DuckDbTypename.UnionOf<A> typename = new DuckDbTypename.UnionOf<>(unionName, typenameMembers);

      // Auto-derive reader from members
      UnionReader<A> reader =
          (tag, value) -> {
            for (Member<A, ?> member : members) {
              if (member.tag().equals(tag)) {
                return applyWrapper(member, value);
              }
            }
            throw new SQLException("Unknown tag: " + tag);
          };

      // Auto-derive writer from members
      UnionWriter<A> writer =
          unionValue -> {
            for (Member<A, ?> member : members) {
              TaggedValue<?> tv = member.tryUnwrap(unionValue);
              if (tv != null) {
                return tv;
              }
            }
            throw new IllegalStateException("No member matched for union value: " + unionValue);
          };

      // Auto-derive JSON codec from members
      DuckDbJson<A> json =
          new DuckDbJson<>() {
            @Override
            public JsonValue toJson(A value) {
              for (Member<A, ?> member : members) {
                TaggedValue<?> tv = member.tryUnwrap(value);
                if (tv != null) {
                  return memberToJson(member, tv.value());
                }
              }
              throw new IllegalStateException("No member matched for JSON encoding: " + value);
            }

            @Override
            public A fromJson(JsonValue json) {
              if (json instanceof JsonValue.JObject obj) {
                String tag = ((JsonValue.JString) obj.fields().get("tag")).value();
                JsonValue valueJson = obj.fields().get("value");
                for (Member<A, ?> member : members) {
                  if (member.tag().equals(tag)) {
                    return memberFromJson(member, valueJson);
                  }
                }
                throw new IllegalArgumentException("Unknown tag: " + tag);
              }
              throw new IllegalArgumentException(
                  "Expected JSON object with 'tag' and 'value' fields");
            }
          };

      return new DuckDbUnion<>(typename, List.copyOf(members), reader, writer, json);
    }

    @SuppressWarnings("unchecked")
    private <M> A applyWrapper(Member<A, M> member, Object value) {
      return member.wrapper().apply((M) value);
    }

    @SuppressWarnings("unchecked")
    private <M> JsonValue memberToJson(Member<A, M> member, Object value) {
      JsonValue innerJson = member.type().duckDbJson().toJson((M) value);
      return new JsonValue.JObject(
          java.util.Map.of("tag", new JsonValue.JString(member.tag()), "value", innerJson));
    }

    @SuppressWarnings("unchecked")
    private <M> A memberFromJson(Member<A, M> member, JsonValue valueJson) {
      M rawValue = member.type().duckDbJson().fromJson(valueJson);
      return member.wrapper().apply(rawValue);
    }

    /**
     * Build with custom reader, writer, and JSON codec. Use this when auto-derivation doesn't work
     * for your use case.
     */
    public DuckDbUnion<A> build(UnionReader<A> reader, UnionWriter<A> writer, DuckDbJson<A> json) {
      List<DuckDbTypename.UnionOf.UnionMember> typenameMembers =
          members.stream()
              .map(m -> new DuckDbTypename.UnionOf.UnionMember(m.tag(), m.type().typename()))
              .toList();

      DuckDbTypename.UnionOf<A> typename = new DuckDbTypename.UnionOf<>(unionName, typenameMembers);

      return new DuckDbUnion<>(typename, List.copyOf(members), reader, writer, json);
    }
  }
}
