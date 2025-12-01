package typo.runtime;

import org.postgresql.geometric.*;
import org.postgresql.jdbc.PgConnection;
import org.postgresql.util.PGInterval;
import typo.data.Record;
import typo.data.*;
import typo.data.Vector;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

public interface tester {

    record TestPair<A>(A t0, Optional<A> t1) {}

    record PgTypeAndExample<A>(PgType<A> type, A example, boolean hasIdentity, boolean streamingWorks) {
        public PgTypeAndExample(PgType<A> type, A example) {
            this(type, example, true, true);
        }

        public PgTypeAndExample<A> noStreaming() {
            return new PgTypeAndExample(type, example, hasIdentity, false);
        }

        public PgTypeAndExample<A> noIdentity() {
            return new PgTypeAndExample(type, example, false, streamingWorks);
        }
    }

    List<PgTypeAndExample<?>> All = List.of(
            new PgTypeAndExample<>(PgTypes.aclitem, new AclItem("postgres=r*w/postgres")),
            new PgTypeAndExample<>(PgTypes.aclitemArray, new AclItem[]{new AclItem("postgres=r*w/postgres")}),
            new PgTypeAndExample<>(PgTypes.bool, true),
            new PgTypeAndExample<>(PgTypes.boolArray, new Boolean[]{true, false}),
            new PgTypeAndExample<>(PgTypes.box, new PGbox(42, 42, 42, 42)).noIdentity(),
            new PgTypeAndExample<>(PgTypes.boxArray, new PGbox[]{new PGbox(42, 42, 42, 42)}).noIdentity(),
            new PgTypeAndExample<>(PgTypes.bpchar(5), "377  "),
            new PgTypeAndExample<>(PgTypes.bpchar, "377"),
            new PgTypeAndExample<>(PgTypes.bpcharArray(5), new String[]{"377  "}),
            new PgTypeAndExample<>(PgTypes.bpcharArray, new String[]{"10101"}),
            new PgTypeAndExample<>(PgTypes.bytea, new byte[]{-1, 1, 127}),
            new PgTypeAndExample<>(PgTypes.circle, new PGcircle(new PGpoint(0.01, 42.34), 101.2)),
            new PgTypeAndExample<>(PgTypes.circleArray, new PGcircle[]{new PGcircle(new PGpoint(0.01, 42.34), 101.2)}).noIdentity(),
            new PgTypeAndExample<>(PgTypes.date, LocalDate.now()),
            new PgTypeAndExample<>(PgTypes.dateArray, new LocalDate[]{LocalDate.now()}),
            new PgTypeAndExample<>(PgTypes.float4, 42.42f),
            new PgTypeAndExample<>(PgTypes.float4Array, new Float[]{42.42f}),
            new PgTypeAndExample<>(PgTypes.float8, 42.42),
            new PgTypeAndExample<>(PgTypes.float8Array, new Double[]{42.42}),
            new PgTypeAndExample<>(PgTypes.hstore, Map.of(",.;{}[]-//#®✅", ",.;{}[]-//#®✅")),
            new PgTypeAndExample<>(PgTypes.inet, new Inet("10.1.0.0")),
            new PgTypeAndExample<>(PgTypes.inetArray, new Inet[]{new Inet("10.1.0.0")}),
            new PgTypeAndExample<>(PgTypes.int2, (short) 42),
            new PgTypeAndExample<>(PgTypes.int2Array, new Short[]{42}),
            new PgTypeAndExample<>(PgTypes.int2vector, new Int2Vector(new short[]{1, 2, 3})),
            new PgTypeAndExample<>(PgTypes.int2vectorArray, new Int2Vector[]{new Int2Vector(new short[]{1, 2, 3})}),
            new PgTypeAndExample<>(PgTypes.int4, 42),
            new PgTypeAndExample<>(PgTypes.int4Array, new Integer[]{42}),
            new PgTypeAndExample<>(PgTypes.int8, 42L),
            new PgTypeAndExample<>(PgTypes.int8Array, new Long[]{42L}),
            new PgTypeAndExample<>(PgTypes.interval, new PGInterval(1, 2, 3, 4, 5, 6.666)),
            new PgTypeAndExample<>(PgTypes.intervalArray, new PGInterval[]{new PGInterval(1, 2, 3, 4, 5, 6.666)}).noIdentity(),
            new PgTypeAndExample<>(PgTypes.json, new Json("{\"A\": 42}")).noIdentity(),
            new PgTypeAndExample<>(PgTypes.jsonArray, new Json[]{new Json("{\"A\": 42}")}).noIdentity().noStreaming(),
            new PgTypeAndExample<>(PgTypes.jsonb, new Jsonb("{\"A\": 42}")),
            new PgTypeAndExample<>(PgTypes.jsonbArray, new Jsonb[]{new Jsonb("{\"A\": 42}")}).noStreaming(),
            new PgTypeAndExample<>(PgTypes.line, new PGline(1.1, 2.2, 3.3)).noIdentity(),
            new PgTypeAndExample<>(PgTypes.lineArray, new PGline[]{new PGline(1.1, 2.2, 3.3)}).noIdentity(),
            new PgTypeAndExample<>(PgTypes.lseg, new PGlseg(1.1, 2.2, 3.3, 4.4)).noIdentity(),
            new PgTypeAndExample<>(PgTypes.lsegArray, new PGlseg[]{new PGlseg(1.1, 2.2, 3.3, 4.4)}).noIdentity(),
            new PgTypeAndExample<>(PgTypes.lsegArray, new PGlseg[]{new PGlseg(1.1, 2.2, 3.3, 4.4)}).noIdentity(),
            new PgTypeAndExample<>(PgTypes.money, new Money("42.22")),
            new PgTypeAndExample<>(PgTypes.moneyArray, new Money[]{new Money("42.22")}),
            new PgTypeAndExample<>(PgTypes.numeric, new BigDecimal("0.002")),
            new PgTypeAndExample<>(PgTypes.numericArray, new BigDecimal[]{new BigDecimal("0.002")}),
            new PgTypeAndExample<>(PgTypes.oidvector, new OidVector(new int[]{1, 2, 3})),
            new PgTypeAndExample<>(PgTypes.oidvectorArray, new OidVector[]{new OidVector(new int[]{1, 2, 3})}),
            new PgTypeAndExample<>(PgTypes.path, new PGpath(new PGpoint[]{new PGpoint(1.1, 2.2), new PGpoint(3.3, 4.4)}, true)).noIdentity(),
            new PgTypeAndExample<>(PgTypes.pathArray, new PGpath[]{new PGpath(new PGpoint[]{new PGpoint(1.1, 2.2), new PGpoint(3.3, 4.4)}, true)}).noIdentity(),
            new PgTypeAndExample<>(PgTypes.point, new PGpoint(1.1, 2.2)).noIdentity(),
            new PgTypeAndExample<>(PgTypes.pointArray, new PGpoint[]{new PGpoint(1.1, 2.2)}).noIdentity(),
            new PgTypeAndExample<>(PgTypes.polygon, new PGpolygon(new PGpoint[]{new PGpoint(1.1, 2.2), new PGpoint(3.3, 4.4)})).noIdentity(),
            new PgTypeAndExample<>(PgTypes.polygonArray, new PGpolygon[]{new PGpolygon(new PGpoint[]{new PGpoint(1.1, 2.2), new PGpoint(3.3, 4.4)})}).noIdentity(),
            new PgTypeAndExample<>(PgTypes.record("complex"), new Record("(1,2)")),
            new PgTypeAndExample<>(PgTypes.recordArray("complex"), new Record[]{new Record("(1,2)")}),
            new PgTypeAndExample<>(PgTypes.regconfig, new Regconfig("basque")),
            new PgTypeAndExample<>(PgTypes.regconfigArray, new Regconfig[]{new Regconfig("basque")}),
            new PgTypeAndExample<>(PgTypes.regdictionary, new Regdictionary("english_stem")),
            new PgTypeAndExample<>(PgTypes.regdictionaryArray, new Regdictionary[]{new Regdictionary("english_stem")}),
            new PgTypeAndExample<>(PgTypes.regnamespace, new Regnamespace("public")),
            new PgTypeAndExample<>(PgTypes.regnamespaceArray, new Regnamespace[]{new Regnamespace("public")}),
//            new PgTypeAndExample<>(PgTypes.regoper, new Regoper("-(int8, int8)")), // ERROR: more than one
//            new PgTypeAndExample<>(PgTypes.regoperArray, new Regoper[]{new Regoper("public.-")}), // ERROR: more than one
            new PgTypeAndExample<>(PgTypes.regoperator, new Regoperator("-(bigint,bigint)")),
            new PgTypeAndExample<>(PgTypes.regoperatorArray, new Regoperator[]{new Regoperator("-(bigint,bigint)")}),
//            new PgTypeAndExample<>(PgTypes.regproc, new Regproc("sum")), // ERROR: more than one
//            new PgTypeAndExample<>(PgTypes.regprocArray, new Regproc[]{new Regproc("sum")}), // ERROR: more than one
            new PgTypeAndExample<>(PgTypes.regprocedure, new Regprocedure("sum(integer)")),
            new PgTypeAndExample<>(PgTypes.regprocedureArray, new Regprocedure[]{new Regprocedure("sum(integer)")}),
            new PgTypeAndExample<>(PgTypes.regrole, new Regrole("pg_database_owner")),
            new PgTypeAndExample<>(PgTypes.regroleArray, new Regrole[]{new Regrole("pg_database_owner")}),
            new PgTypeAndExample<>(PgTypes.regtype, new Regtype("integer")),
            new PgTypeAndExample<>(PgTypes.regtypeArray, new Regtype[]{new Regtype("integer")}),
            new PgTypeAndExample<>(PgTypes.xid, new Xid("1")),
            new PgTypeAndExample<>(PgTypes.xidArray, new Xid[]{new Xid("1")}),
            new PgTypeAndExample<>(PgTypes.smallint, (short) 42),
            new PgTypeAndExample<>(PgTypes.smallintArray, new Short[]{42}),
            new PgTypeAndExample<>(PgTypes.text, ",.;{}[]-//#®✅"),
            new PgTypeAndExample<>(PgTypes.textArray, new String[]{",.;{}[]-//#®✅"}),
            new PgTypeAndExample<>(PgTypes.time, LocalTime.now()),
            new PgTypeAndExample<>(PgTypes.timeArray, new LocalTime[]{LocalTime.now()}),
            new PgTypeAndExample<>(PgTypes.timestamp, LocalDateTime.now()),
            new PgTypeAndExample<>(PgTypes.timestampArray, new LocalDateTime[]{LocalDateTime.now()}),
            new PgTypeAndExample<>(PgTypes.timestamptz, Instant.now()),
            new PgTypeAndExample<>(PgTypes.timestamptzArray, new Instant[]{Instant.now()}),
            new PgTypeAndExample<>(PgTypes.timetz, OffsetTime.now()),
            new PgTypeAndExample<>(PgTypes.timetzArray, new OffsetTime[]{OffsetTime.now()}),
            new PgTypeAndExample<>(PgTypes.vector, new Vector(new short[]{1, 2, 3})),
            new PgTypeAndExample<>(PgTypes.vectorArray, new Vector[]{new Vector(new short[]{1, 2, 3})}),
            new PgTypeAndExample<>(PgTypes.uuid, UUID.randomUUID()),
            new PgTypeAndExample<>(PgTypes.uuidArray, new UUID[]{UUID.randomUUID()}),
            new PgTypeAndExample<>(PgTypes.xml, new Xml("<a>42</a>")).noIdentity(),
            new PgTypeAndExample<>(PgTypes.xmlArray, new Xml[]{new Xml("<a>42</a>")}).noIdentity()
    );

    // in java
    static <T> T withConnection(SqlFunction<Connection, T> f) {
        try (var conn = java.sql.DriverManager.getConnection("jdbc:postgresql://localhost:6432/Adventureworks?user=postgres&password=password")) {
            conn.setAutoCommit(false);
            try {
                return f.apply(conn);
            } finally {
                conn.rollback();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // main
    static void main(String[] args) {
        System.out.println(Arr.of(0, 1, 2, 3).reshape(2, 2));
        System.out.println(Arr.of("a", "b", "c", "d \",d").reshape(2, 2));
        System.out.println(ArrParser.parse(Arr.of(1, 2, 3, 4).encode(Object::toString)));
        System.out.println(ArrParser.parse("{{\"a\",\"b\"},{\"c\",\"d \\\",d\"}}"));
        withConnection(conn -> {
            conn.unwrap(PgConnection.class).setPrepareThreshold(0);
            for (PgTypeAndExample<?> t : All) {
                System.out.println("Testing " + t.type.typename().sqlType() + " with example '" + (format(t.example)) + "'");
                testCase(conn, t);
            }
            return null;
        });
    }

    static <A> void testCase(Connection conn, PgTypeAndExample<A> t) throws SQLException {
        conn.createStatement().execute("create temp table test (v " + t.type.typename().sqlType() + ")");
        var insert = conn.prepareStatement("insert into test (v) values (?)");
        A expected = t.example;
        t.type.write().set(insert, 1, expected);
        insert.execute();
        insert.close();
        if (t.streamingWorks) {
            streamingInsert.insert("COPY test(v) FROM STDIN", 100, Arrays.asList(t.example).iterator(), conn, t.type.pgText());
        }

        final PreparedStatement select;
        if (t.hasIdentity) {
            select = conn.prepareStatement("select v, null from test where v = ?");
            t.type.write().set(select, 1, expected);
        } else {
            select = conn.prepareStatement("select v, null from test");
        }

        select.execute();
        var rs = select.getResultSet();
        List<TestPair<A>> rows = RowParsers.of(t.type, t.type.opt(), TestPair::new, row -> new Object[]{row.t0, row.t1}).all().apply(rs);
        select.close();
        conn.createStatement().execute("drop table test;");
        assertEquals(rows.get(0).t0(), expected);
        if (t.streamingWorks) {
            assertEquals(rows.get(1).t0(), expected);
        }
    }

    static <A> void assertEquals(A actual, A expected) {
        if (!areEqual(actual, expected)) {
            throw new RuntimeException("actual: '" + format(actual) + "' != expected '" + format(expected) + "'");
        }
    }

    static <A> boolean areEqual(A actual, A expected) {
        if (expected instanceof byte[]) {
            return Arrays.equals((byte[]) actual, (byte[]) expected);
        }
        if (expected instanceof Object[]) {
            return Arrays.equals((Object[]) actual, (Object[]) expected);
        }
        return actual.equals(expected);
    }

    static <A> String format(A a) {
        if (a instanceof byte[]) {
            return Arrays.toString((byte[]) a);
        }
        if (a instanceof Object[]) {
            return Arrays.toString((Object[]) a);
        }
        return a.toString();
    }
}
