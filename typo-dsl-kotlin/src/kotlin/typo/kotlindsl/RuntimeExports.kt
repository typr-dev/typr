package typo.kotlindsl

// ================================
// Simple Type Aliases (no interop issues)
// ================================

// Core utility types
// Fragment wrapper is defined in Fragment.kt
typealias Either<L, R> = typo.runtime.Either<L, R>
typealias And<T1, T2> = typo.runtime.And<T1, T2>

// PostgreSQL data wrapper types (simple records)
typealias Json = typo.data.Json
typealias Jsonb = typo.data.Jsonb
typealias Money = typo.data.Money
typealias Xml = typo.data.Xml
typealias Vector = typo.data.Vector
typealias Record = typo.data.Record
typealias Unknown = typo.data.Unknown
typealias Xid = typo.data.Xid
typealias Inet = typo.data.Inet
typealias AclItem = typo.data.AclItem
typealias AnyArray = typo.data.AnyArray
typealias Int2Vector = typo.data.Int2Vector
typealias OidVector = typo.data.OidVector
typealias PgNodeTree = typo.data.PgNodeTree

// Regclass types
typealias Regclass = typo.data.Regclass
typealias Regconfig = typo.data.Regconfig
typealias Regdictionary = typo.data.Regdictionary
typealias Regnamespace = typo.data.Regnamespace
typealias Regoper = typo.data.Regoper
typealias Regoperator = typo.data.Regoperator
typealias Regproc = typo.data.Regproc
typealias Regprocedure = typo.data.Regprocedure
typealias Regrole = typo.data.Regrole
typealias Regtype = typo.data.Regtype

// Range types (need extension methods but can be aliased)
typealias Range<T> = typo.data.Range<T>
typealias RangeBound<T> = typo.data.RangeBound<T>
typealias RangeFinite<T> = typo.data.RangeFinite<T>

// Array type (needs extension methods but can be aliased)
typealias Arr<A> = typo.data.Arr<A>

// Core type system (will add extension methods)
typealias PgType<A> = typo.runtime.PgType<A>
typealias PgTypename<A> = typo.runtime.PgTypename<A>
typealias PgRead<A> = typo.runtime.PgRead<A>
typealias PgWrite<A> = typo.runtime.PgWrite<A>
typealias PgText<A> = typo.runtime.PgText<A>

// Database access types
// RowParser, ResultSetParser, and Operation are wrapper classes (see RowParser.kt, ResultSetParser.kt, and Operation.kt)
// Operation wrapper is defined in Operation.kt
typealias Transactor = typo.runtime.Transactor

// Functional interfaces for SQL exceptions
typealias SqlFunction<T, R> = typo.runtime.SqlFunction<T, R>
typealias SqlConsumer<T> = typo.runtime.SqlConsumer<T>
typealias SqlBiConsumer<T1, T2> = typo.runtime.SqlBiConsumer<T1, T2>

// Utility
typealias ByteArrays = typo.runtime.internal.ByteArrays
typealias ArrParser = typo.runtime.ArrParser

// PgTypes registry (static access)
typealias PgTypes = typo.runtime.PgTypes
