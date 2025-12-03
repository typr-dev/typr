package typo.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.Optional;
import java.util.function.Function;

/**
 * Describes how to read a column from a {@link ResultSet} for MariaDB.
 * <p>
 * Similar to PgRead but adapted for MariaDB-specific types.
 */
public sealed interface MariaRead<A> extends DbRead<A> permits MariaRead.NonNullable, MariaRead.Nullable {
    A read(ResultSet rs, int col) throws SQLException;

    <B> MariaRead<B> map(SqlFunction<A, B> f);

    /**
     * Derive a MariaRead which allows nullable values
     */
    MariaRead<Optional<A>> opt();

    @FunctionalInterface
    interface RawRead<A> {
        A apply(ResultSet rs, int column) throws SQLException;
    }

    /**
     * Create an instance of {@link MariaRead} from a function that reads a value from a result set.
     *
     * @param f Should not blow up if the value returned is `null`
     */
    static <A> NonNullable<A> of(RawRead<A> f) {
        RawRead<Optional<A>> readNullableA = (rs, col) -> {
            var a = f.apply(rs, col);
            if (rs.wasNull()) return Optional.empty();
            else return Optional.of(a);
        };
        return new NonNullable<>(readNullableA);
    }

    final class NonNullable<A> implements MariaRead<A> {
        final RawRead<Optional<A>> readNullable;

        public NonNullable(RawRead<Optional<A>> readNullable) {
            this.readNullable = readNullable;
        }

        @Override
        public A read(ResultSet rs, int col) throws SQLException {
            return readNullable.apply(rs, col).orElseThrow(() -> new SQLException("null value in column " + col));
        }

        @Override
        public <B> NonNullable<B> map(SqlFunction<A, B> f) {
            return new NonNullable<>((rs, col) -> {
                Optional<A> maybeA = readNullable.apply(rs, col);
                if (maybeA.isEmpty()) return Optional.empty();
                return Optional.of(f.apply(maybeA.get()));
            });
        }

        @Override
        public MariaRead<Optional<A>> opt() {
            return new Nullable<>(readNullable);
        }
    }

    final class Nullable<A> implements MariaRead<Optional<A>>, DbRead.Nullable {
        final RawRead<Optional<A>> readNullable;

        public Nullable(RawRead<Optional<A>> readNullable) {
            this.readNullable = readNullable;
        }

        @Override
        public Optional<A> read(ResultSet rs, int col) throws SQLException {
            return readNullable.apply(rs, col);
        }

        @Override
        public <B> MariaRead<B> map(SqlFunction<Optional<A>, B> f) {
            return new NonNullable<>((rs, col) -> Optional.of(f.apply(read(rs, col))));
        }

        @Override
        public Nullable<Optional<A>> opt() {
            return new Nullable<>((rs, col) -> {
                Optional<A> maybeA = readNullable.apply(rs, col);
                if (maybeA.isEmpty()) return Optional.empty();
                return Optional.of(maybeA);
            });
        }
    }

    static <A> NonNullable<A> castJdbcObjectTo(Class<A> cls) {
        return of((rs, i) -> cls.cast(rs.getObject(i)));
    }

    /**
     * Read a value by requesting a specific class from JDBC.
     * This uses rs.getObject(i, cls) which allows the JDBC driver to do proper type conversion,
     * including handling WKB bytes for spatial types returned from RETURNING clauses.
     */
    static <A> NonNullable<A> getObjectAs(Class<A> cls) {
        return of((rs, i) -> rs.getObject(i, cls));
    }

    // Basic type readers
    MariaRead<String> readString = of(ResultSet::getString);
    MariaRead<Boolean> readBoolean = of(ResultSet::getBoolean);
    MariaRead<Byte> readByte = of(ResultSet::getByte);
    MariaRead<Short> readShort = of(ResultSet::getShort);
    MariaRead<Integer> readInteger = of(ResultSet::getInt);
    MariaRead<Long> readLong = of(ResultSet::getLong);
    MariaRead<Float> readFloat = of(ResultSet::getFloat);
    MariaRead<Double> readDouble = of(ResultSet::getDouble);
    MariaRead<BigDecimal> readBigDecimal = of(ResultSet::getBigDecimal);
    // For BINARY/VARBINARY - reads as byte[] directly
    MariaRead<byte[]> readByteArray = of(ResultSet::getBytes);

    // For BLOB types - MariaDB returns Blob objects, need to extract bytes
    MariaRead<byte[]> readBlob = of((rs, idx) -> {
        java.sql.Blob blob = rs.getBlob(idx);
        if (blob == null) return null;
        return blob.getBytes(1, (int) blob.length());
    });

    // BigInteger for BIGINT UNSIGNED
    MariaRead<BigInteger> readBigInteger = readBigDecimal.map(bd -> bd.toBigInteger());

    // Date/Time readers
    MariaRead<LocalDate> readLocalDate = of((rs, idx) -> rs.getObject(idx, LocalDate.class));
    MariaRead<LocalTime> readLocalTime = of((rs, idx) -> rs.getObject(idx, LocalTime.class));
    MariaRead<LocalDateTime> readLocalDateTime = of((rs, idx) -> rs.getObject(idx, LocalDateTime.class));

    // Year type - MariaDB returns it as a short
    MariaRead<Year> readYear = readShort.map(s -> Year.of(s.intValue()));

    // BIT type - MariaDB returns as byte[] for BIT(n) where n > 1
    MariaRead<byte[]> readBit = of((rs, idx) -> {
        Object obj = rs.getObject(idx);
        if (obj == null) return null;
        if (obj instanceof byte[]) return (byte[]) obj;
        if (obj instanceof Boolean) return new byte[]{(byte) (((Boolean) obj) ? 1 : 0)};
        if (obj instanceof Number) return new byte[]{((Number) obj).byteValue()};
        throw new SQLException("Cannot convert " + obj.getClass() + " to byte[] for BIT type");
    });

    // BIT(1) as Boolean
    MariaRead<Boolean> readBitAsBoolean = of((rs, idx) -> {
        Object obj = rs.getObject(idx);
        if (obj == null) return null;
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof byte[]) {
            byte[] bytes = (byte[]) obj;
            return bytes.length > 0 && bytes[0] != 0;
        }
        if (obj instanceof Number) return ((Number) obj).intValue() != 0;
        throw new SQLException("Cannot convert " + obj.getClass() + " to Boolean for BIT(1) type");
    });

}
