package typr.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Function;

/**
 * Describes how to write a value to a {@link PreparedStatement} for MariaDB.
 *
 * <p>Similar to PgWrite but adapted for MariaDB-specific types. MariaDB doesn't have array types,
 * so array support is omitted.
 */
public sealed interface MariaWrite<A> extends DbWrite<A> permits MariaWrite.Instance {
  void set(PreparedStatement ps, int idx, A a) throws SQLException;

  MariaWrite<Optional<A>> opt(MariaTypename<A> typename);

  <B> MariaWrite<B> contramap(Function<B, A> f);

  @FunctionalInterface
  interface RawWriter<A> {
    void set(PreparedStatement ps, int index, A a) throws SQLException;
  }

  record Instance<A, U>(RawWriter<U> rawWriter, Function<A, U> f) implements MariaWrite<A> {
    @Override
    public void set(PreparedStatement ps, int index, A a) throws SQLException {
      rawWriter.set(ps, index, f.apply(a));
    }

    @Override
    public MariaWrite<Optional<A>> opt(MariaTypename<A> typename) {
      return new Instance<>(
          (ps, index, u) -> {
            if (u == null) ps.setNull(index, java.sql.Types.NULL);
            else set(ps, index, u);
          },
          a -> a.orElse(null));
    }

    @Override
    public <B> MariaWrite<B> contramap(Function<B, A> f) {
      return new Instance<>(rawWriter, f.andThen(this.f));
    }
  }

  static <A> MariaWrite<A> primitive(RawWriter<A> rawWriter) {
    return new Instance<>(rawWriter, Function.identity());
  }

  static <A> MariaWrite<A> passObjectToJdbc() {
    return primitive(PreparedStatement::setObject);
  }

  // Basic type writers
  MariaWrite<String> writeString = primitive(PreparedStatement::setString);
  MariaWrite<Boolean> writeBoolean = primitive(PreparedStatement::setBoolean);
  MariaWrite<Byte> writeByte = primitive(PreparedStatement::setByte);
  MariaWrite<Short> writeShort = primitive(PreparedStatement::setShort);
  MariaWrite<Integer> writeInteger = primitive(PreparedStatement::setInt);
  MariaWrite<Long> writeLong = primitive(PreparedStatement::setLong);
  MariaWrite<Float> writeFloat = primitive(PreparedStatement::setFloat);
  MariaWrite<Double> writeDouble = primitive(PreparedStatement::setDouble);
  MariaWrite<BigDecimal> writeBigDecimal = primitive(PreparedStatement::setBigDecimal);
  MariaWrite<BigInteger> writeBigInteger = writeBigDecimal.contramap(bi -> new BigDecimal(bi));
  MariaWrite<byte[]> writeByteArray = primitive(PreparedStatement::setBytes);
}
