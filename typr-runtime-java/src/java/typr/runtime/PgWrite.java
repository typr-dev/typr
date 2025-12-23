package typr.runtime;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.postgresql.util.PGobject;
import typr.runtime.internal.arrayMap;

public sealed interface PgWrite<A> extends DbWrite<A> permits PgWrite.Instance {
  void set(PreparedStatement ps, int idx, A a) throws SQLException;

  // combinators
  PgWrite<Optional<A>> opt(PgTypename<A> typename);

  PgWrite<A[]> array(PgTypename<A> typename);

  <B> PgWrite<B> contramap(Function<B, A> f);

  @FunctionalInterface
  interface RawWriter<A> {
    void set(PreparedStatement ps, int index, A a) throws SQLException;
  }

  record Instance<A, U>(RawWriter<U> rawWriter, Function<A, U> f) implements PgWrite<A> {
    @Override
    public void set(PreparedStatement ps, int index, A a) throws SQLException {
      rawWriter.set(ps, index, f.apply(a));
    }

    @Override
    public PgWrite<Optional<A>> opt(PgTypename<A> typename) {
      return new Instance<>(
          (ps, index, u) -> {
            if (u == null) ps.setNull(index, 0, typename.sqlTypeNoPrecision());
            else set(ps, index, u);
          },
          a -> a.orElse(null));
    }

    @SuppressWarnings("unchecked")
    @Override
    public PgWrite<A[]> array(PgTypename<A> typename) {
      return new Instance<A[], Object[]>(
          (ps, index, us) ->
              ps.setArray(
                  index, ps.getConnection().createArrayOf(typename.sqlTypeNoPrecision(), us)),
          as -> arrayMap.map(as, f, (Class<U>) Object.class));
    }

    @Override
    public <B> PgWrite<B> contramap(Function<B, A> f) {
      return new Instance<>(rawWriter, f.andThen(this.f));
    }
  }

  static <A> PgWrite<A> primitive(RawWriter<A> rawWriter) {
    return new Instance<>(rawWriter, Function.identity());
  }

  static <A> PgWrite<A> passObjectToJdbc() {
    return primitive(PreparedStatement::setObject);
  }

  static PgWrite<String> pgObject(String sqlType) {
    return PgWrite.<PGobject>passObjectToJdbc()
        .contramap(
            str -> {
              var obj = new PGobject();
              obj.setType(sqlType);
              try {
                obj.setValue(str);
              } catch (SQLException e) {
                throw new RuntimeException(e);
              }
              return obj;
            });
  }

  PgWrite<byte[]> writeByteArray = primitive(PreparedStatement::setObject);

  // Unboxed (primitive) array writers
  PgWrite<boolean[]> writeBooleanArrayUnboxed = primitive(PreparedStatement::setObject);
  PgWrite<short[]> writeShortArrayUnboxed = primitive(PreparedStatement::setObject);
  PgWrite<int[]> writeIntArrayUnboxed = primitive(PreparedStatement::setObject);
  PgWrite<long[]> writeLongArrayUnboxed = primitive(PreparedStatement::setObject);
  PgWrite<float[]> writeFloatArrayUnboxed = primitive(PreparedStatement::setObject);
  PgWrite<double[]> writeDoubleArrayUnboxed = primitive(PreparedStatement::setObject);

  PgWrite<Boolean> writeBoolean = primitive(PreparedStatement::setBoolean);
  PgWrite<BigDecimal> writeBigDecimal = primitive(PreparedStatement::setBigDecimal);
  PgWrite<Double> writeDouble = primitive(PreparedStatement::setDouble);
  PgWrite<Float> writeFloat = primitive(PreparedStatement::setFloat);
  PgWrite<Integer> writeInteger = primitive(PreparedStatement::setInt);
  PgWrite<Long> writeLong = primitive(PreparedStatement::setLong);
  PgWrite<Short> writeShort = primitive(PreparedStatement::setShort);
  PgWrite<String> writeString = primitive(PreparedStatement::setString);
  PgWrite<UUID> writeUUID = primitive(PreparedStatement::setObject);
}
