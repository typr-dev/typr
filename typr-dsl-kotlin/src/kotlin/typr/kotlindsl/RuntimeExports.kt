package typr.kotlindsl

// ================================
// Simple Type Aliases (no interop issues)
// ================================

// Core utility types
// Fragment wrapper is defined in Fragment.kt
typealias Either<L, R> = typr.runtime.Either<L, R>
typealias And<T1, T2> = typr.runtime.And<T1, T2>

// PostgreSQL data wrapper types (simple records)
typealias Json = typr.data.Json
typealias Jsonb = typr.data.Jsonb
typealias Money = typr.data.Money
typealias Xml = typr.data.Xml
typealias Vector = typr.data.Vector
typealias Record = typr.data.Record
typealias Unknown = typr.data.Unknown
typealias Xid = typr.data.Xid
typealias Inet = typr.data.Inet
typealias AclItem = typr.data.AclItem
typealias AnyArray = typr.data.AnyArray
typealias Int2Vector = typr.data.Int2Vector
typealias OidVector = typr.data.OidVector
typealias PgNodeTree = typr.data.PgNodeTree

// Regclass types
typealias Regclass = typr.data.Regclass
typealias Regconfig = typr.data.Regconfig
typealias Regdictionary = typr.data.Regdictionary
typealias Regnamespace = typr.data.Regnamespace
typealias Regoper = typr.data.Regoper
typealias Regoperator = typr.data.Regoperator
typealias Regproc = typr.data.Regproc
typealias Regprocedure = typr.data.Regprocedure
typealias Regrole = typr.data.Regrole
typealias Regtype = typr.data.Regtype

// Range types (need extension methods but can be aliased)
typealias Range<T> = typr.data.Range<T>
typealias RangeBound<T> = typr.data.RangeBound<T>
typealias RangeFinite<T> = typr.data.RangeFinite<T>

// Array type (needs extension methods but can be aliased)
typealias Arr<A> = typr.data.Arr<A>

// Core type system (will add extension methods)
typealias PgType<A> = typr.runtime.PgType<A>
typealias PgTypename<A> = typr.runtime.PgTypename<A>
typealias PgRead<A> = typr.runtime.PgRead<A>
typealias PgWrite<A> = typr.runtime.PgWrite<A>
typealias PgText<A> = typr.runtime.PgText<A>

// Database access types
// RowParser, ResultSetParser, and Operation are wrapper classes (see RowParser.kt, ResultSetParser.kt, and Operation.kt)
// Operation wrapper is defined in Operation.kt
typealias Transactor = typr.runtime.Transactor

// Functional interfaces for SQL exceptions
typealias SqlFunction<T, R> = typr.runtime.SqlFunction<T, R>
typealias SqlConsumer<T> = typr.runtime.SqlConsumer<T>
typealias SqlBiConsumer<T1, T2> = typr.runtime.SqlBiConsumer<T1, T2>

// Utility
typealias ByteArrays = typr.runtime.internal.ByteArrays
typealias ArrParser = typr.runtime.ArrParser

// PgTypes registry (static access)
typealias PgTypes = typr.runtime.PgTypes
