package dev.typr.foundations.kotlin

// ================================
// Simple Type Aliases (no interop issues)
// ================================

// Core utility types
// Fragment wrapper is defined in Fragment.kt
typealias Either<L, R> = dev.typr.foundations.Either<L, R>
typealias And<T1, T2> = dev.typr.foundations.And<T1, T2>

// PostgreSQL data wrapper types (simple records)
typealias Json = dev.typr.foundations.data.Json
typealias Jsonb = dev.typr.foundations.data.Jsonb
typealias Money = dev.typr.foundations.data.Money
typealias Xml = dev.typr.foundations.data.Xml
typealias Vector = dev.typr.foundations.data.Vector
typealias Record = dev.typr.foundations.data.Record
typealias Unknown = dev.typr.foundations.data.Unknown
typealias Xid = dev.typr.foundations.data.Xid
typealias Inet = dev.typr.foundations.data.Inet
typealias AclItem = dev.typr.foundations.data.AclItem
typealias AnyArray = dev.typr.foundations.data.AnyArray
typealias Int2Vector = dev.typr.foundations.data.Int2Vector
typealias OidVector = dev.typr.foundations.data.OidVector
typealias PgNodeTree = dev.typr.foundations.data.PgNodeTree

// Regclass types
typealias Regclass = dev.typr.foundations.data.Regclass
typealias Regconfig = dev.typr.foundations.data.Regconfig
typealias Regdictionary = dev.typr.foundations.data.Regdictionary
typealias Regnamespace = dev.typr.foundations.data.Regnamespace
typealias Regoper = dev.typr.foundations.data.Regoper
typealias Regoperator = dev.typr.foundations.data.Regoperator
typealias Regproc = dev.typr.foundations.data.Regproc
typealias Regprocedure = dev.typr.foundations.data.Regprocedure
typealias Regrole = dev.typr.foundations.data.Regrole
typealias Regtype = dev.typr.foundations.data.Regtype

// Range types (need extension methods but can be aliased)
typealias Range<T> = dev.typr.foundations.data.Range<T>
typealias RangeBound<T> = dev.typr.foundations.data.RangeBound<T>
typealias RangeFinite<T> = dev.typr.foundations.data.RangeFinite<T>

// Array type (needs extension methods but can be aliased)
typealias Arr<A> = dev.typr.foundations.data.Arr<A>

// Core type system (will add extension methods)
typealias PgType<A> = dev.typr.foundations.PgType<A>
typealias PgTypename<A> = dev.typr.foundations.PgTypename<A>
typealias PgRead<A> = dev.typr.foundations.PgRead<A>
typealias PgWrite<A> = dev.typr.foundations.PgWrite<A>
typealias PgText<A> = dev.typr.foundations.PgText<A>

// Database access types
// RowParser, ResultSetParser, and Operation are wrapper classes (see RowParser.kt, ResultSetParser.kt, and Operation.kt)
// Operation wrapper is defined in Operation.kt
typealias Transactor = dev.typr.foundations.Transactor

// Functional interfaces for SQL exceptions
typealias SqlFunction<T, R> = dev.typr.foundations.SqlFunction<T, R>
typealias SqlConsumer<T> = dev.typr.foundations.SqlConsumer<T>
typealias SqlBiConsumer<T1, T2> = dev.typr.foundations.SqlBiConsumer<T1, T2>

// Utility
typealias ByteArrays = dev.typr.foundations.internal.ByteArrays
typealias ArrParser = dev.typr.foundations.ArrParser

// PgTypes registry (static access)
typealias PgTypes = dev.typr.foundations.PgTypes
