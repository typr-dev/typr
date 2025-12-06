package typo.runtime;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

public record PgType<A>(
        PgTypename<A> typename,
        PgRead<A> read,
        PgWrite<A> write,
        PgText<A> pgText,
        PgJson<A> pgJson
) implements DbType<A> {
    @Override
    public DbText<A> text() {
        return pgText;
    }

    @Override
    public DbJson<A> json() {
        return pgJson;
    }

    public Fragment.Value<A> encode(A value) {
        return new Fragment.Value<>(value, this);
    }

    public PgType<A> withTypename(PgTypename<A> typename) {
        return new PgType<>(typename, read, write, pgText, pgJson);
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
        return new PgType<>(typename, read, write, pgText, pgJson);
    }

    public PgType<A> withWrite(PgWrite<A> write) {
        return new PgType<>(typename, read, write, pgText, pgJson);
    }

    public PgType<A> withText(PgText<A> text) {
        return new PgType<>(typename, read, write, text, pgJson);
    }

    public PgType<A> withJson(PgJson<A> json) {
        return new PgType<>(typename, read, write, pgText, json);
    }

    public PgType<Optional<A>> opt() {
        return new PgType<>(typename.opt(), read.opt(), write.opt(typename), pgText.opt(), pgJson.opt());
    }

    public PgType<A[]> array(PgRead<A[]> read, IntFunction<A[]> arrayFactory) {
        return new PgType<>(typename.array(), read, write.array(typename), pgText.array(), pgJson.array(arrayFactory));
    }

    public PgType<A[]> array(PgRead<A[]> read, PgWrite<A[]> write, IntFunction<A[]> arrayFactory) {
        return new PgType<>(typename.array(), read, write, pgText.array(), pgJson.array(arrayFactory));
    }

    // Backward-compatible array method without arrayFactory - uses Object[] cast
    @SuppressWarnings("unchecked")
    public PgType<A[]> array(PgRead<A[]> read, PgWrite<A[]> write) {
        return new PgType<>(typename.array(), read, write, pgText.array(), pgJson.array(n -> (A[]) new Object[n]));
    }

    public <B> PgType<B> bimap(SqlFunction<A, B> f, Function<B, A> g) {
        return new PgType<>(typename.as(), read.map(f), write.contramap(g), pgText.contramap(g), pgJson.bimap(f, g));
    }

    public static <A> PgType<A> of(String tpe, PgRead<A> r, PgWrite<A> w, PgText<A> t, PgJson<A> j) {
        return new PgType<>(PgTypename.of(tpe), r, w, t, j);
    }

    public static <A> PgType<A> of(PgTypename<A> typename, PgRead<A> r, PgWrite<A> w, PgText<A> t, PgJson<A> j) {
        return new PgType<>(typename, r, w, t, j);
    }

    // Backward-compatible factory methods without explicit JSON codec
    // These use PgJson.text as a fallback (works for string-representable types)
    @SuppressWarnings("unchecked")
    public static <A> PgType<A> of(String tpe, PgRead<A> r, PgWrite<A> w, PgText<A> t) {
        return new PgType<>(PgTypename.of(tpe), r, w, t, (PgJson<A>) PgJson.text);
    }

    @SuppressWarnings("unchecked")
    public static <A> PgType<A> of(PgTypename<A> typename, PgRead<A> r, PgWrite<A> w, PgText<A> t) {
        return new PgType<>(typename, r, w, t, (PgJson<A>) PgJson.text);
    }
}
