package typo.runtime;

import java.util.Optional;
import java.util.function.Function;

public record PgType<A>(
        PgTypename<A> typename,
        PgRead<A> read,
        PgWrite<A> write,
        PgText<A> pgText
) implements DbType<A> {
    @Override
    public DbText<A> text() {
        return pgText;
    }

    public Fragment.Value<A> encode(A value) {
        return new Fragment.Value<>(value, this);
    }

    public PgType<A> withTypename(PgTypename<A> typename) {
        return new PgType<>(typename, read, write, pgText);
    }

    public PgType<A> withTypename(String sqlType) {
        return withTypename(PgTypename.of(sqlType));
    }

    public PgType<A> renamed(String value) {
        return withTypename(typename.renamed(value));
    }

    public PgType<A> renamedDropPrecision(String value) {
        return withTypename(typename.renamedDropPrecision(value));
    }

    public PgType<A> withRead(PgRead<A> read) {
        return new PgType<>(typename, read, write, pgText);
    }

    public PgType<A> withWrite(PgWrite<A> write) {
        return new PgType<>(typename, read, write, pgText);
    }

    public PgType<A> withText(PgText<A> text) {
        return new PgType<>(typename, read, write, text);
    }

    public PgType<Optional<A>> opt() {
        return new PgType<>(typename.opt(), read.opt(), write.opt(typename), pgText.opt());
    }

    public PgType<A[]> array(PgRead<A[]> read) {
        return new PgType<>(typename.array(), read, write.array(typename), pgText.array());
    }

    public PgType<A[]> array(PgRead<A[]> read, PgWrite<A[]> write) {
        return new PgType<>(typename.array(), read, write, pgText.array());
    }

    public <B> PgType<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
        return new PgType<>(typename.as(), read.map(f), write.contramap(g), pgText.contramap(g));
    }

    public static <A> PgType<A> of(String tpe, PgRead<A> r, PgWrite<A> w, PgText<A> t) {
        return new PgType<>(PgTypename.of(tpe), r, w, t);
    }

    public static <A> PgType<A> of(PgTypename<A> typename, PgRead<A> r, PgWrite<A> w, PgText<A> t) {
        return new PgType<>(typename, r, w, t);
    }
}
