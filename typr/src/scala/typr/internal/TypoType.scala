package typr
package internal

/** Represents the structural information needed to look up a runtime type instance. This captures whether a type is nullable, an array, generated within the package, etc. Also carries the JVM type
  * for code generation.
  */
sealed trait TypoType {

  /** The full JVM type including wrappers */
  def jvmType: jvm.Type

  /** The underlying database type (after unwrapping Optional/Array) */
  def underlyingDbType: db.Type

  /** The innermost JVM type (unwrapped from Optional/Array wrappers) - used for ClassOf in lambdas */
  def innerJvmType: jvm.Type

  /** Create a copy of this TypoType with a new jvmType. Used for wrapping with Defaulted[_] etc. */
  def withJvmType(newJvmType: jvm.Type): TypoType
}

object TypoType {

  /** A standard database type - lookup directly from db.Type */
  case class Standard(jvmType: jvm.Type, underlyingDbType: db.Type) extends TypoType {
    def innerJvmType: jvm.Type = jvmType
    def withJvmType(newJvmType: jvm.Type): Standard = copy(jvmType = newJvmType)
  }

  /** A nullable wrapper around another TypoType */
  case class Nullable(jvmType: jvm.Type, inner: TypoType) extends TypoType {
    def underlyingDbType: db.Type = inner.underlyingDbType
    def innerJvmType: jvm.Type = inner.innerJvmType
    def withJvmType(newJvmType: jvm.Type): Nullable = copy(jvmType = newJvmType)
  }

  /** A generated type within our package (domain, enum, ID wrapper) */
  case class Generated(jvmType: jvm.Type, underlyingDbType: db.Type, qualifiedType: jvm.Type.Qualified) extends TypoType {
    def innerJvmType: jvm.Type = qualifiedType
    def withJvmType(newJvmType: jvm.Type): Generated = copy(jvmType = newJvmType)
  }

  /** A user-defined type override.
    *   - Left: qualified type that must provide its own pgType/mariaType field
    *   - Right: well-known primitive (String, Int, Boolean, etc.)
    */
  case class UserDefined(jvmType: jvm.Type, underlyingDbType: db.Type, userType: Either[jvm.Type.Qualified, analysis.WellKnownPrimitive]) extends TypoType {
    def innerJvmType: jvm.Type = jvmType
    def withJvmType(newJvmType: jvm.Type): UserDefined = copy(jvmType = newJvmType)
  }

  /** An array of another TypoType */
  case class Array(jvmType: jvm.Type, element: TypoType) extends TypoType {
    def underlyingDbType: db.Type = element.underlyingDbType match {
      case pgType: db.PgType         => db.PgType.Array(pgType)
      case duckDbType: db.DuckDbType => db.DuckDbType.ListType(duckDbType)
      case other                     => other // MariaDB doesn't support arrays
    }
    def innerJvmType: jvm.Type = element.innerJvmType
    def withJvmType(newJvmType: jvm.Type): Array = copy(jvmType = newJvmType)
  }

  /** Compute TypoType from JVM type and database type */
  def fromJvmAndDb(jvmType: jvm.Type, dbType: db.Type, pkg: jvm.QIdent, lang: Lang): TypoType = {
    def compute(jvmType: jvm.Type, dbType: db.Type): TypoType = jvmType match {
      // Handle Commented/Annotated wrappers by unwrapping
      case jvm.Type.Commented(underlying, _) =>
        compute(underlying, dbType)
      case jvm.Type.Annotated(underlying, _) =>
        compute(underlying, dbType)

      // Handle KotlinNullable
      case jvm.Type.KotlinNullable(inner) =>
        Nullable(jvmType, compute(inner, dbType))

      // Handle Optional wrappers (language-specific)
      case lang.Optional(inner) =>
        Nullable(jvmType, compute(inner, dbType))

      // Handle PostgreSQL/DuckDB Array wrappers - only if db.Type is also an array
      // This distinguishes PostgreSQL/DuckDB arrays from Java/Scala byte arrays (bytea/blob)
      case jvm.Type.ArrayOf(inner) =>
        dbType match {
          case db.PgType.Array(innerDb) =>
            Array(jvmType, compute(inner, innerDb))
          case db.DuckDbType.ListType(innerDb) =>
            Array(jvmType, compute(inner, innerDb))
          case db.DuckDbType.ArrayType(innerDb, _) =>
            Array(jvmType, compute(inner, innerDb))
          case _ =>
            // bytea/blob and other non-array types that map to Array[Byte] in JVM
            Standard(jvmType, dbType)
        }

      // Handle UserDefined type overrides - qualified types must provide their own pgType field
      case jvm.Type.UserDefined(q: jvm.Type.Qualified) =>
        UserDefined(jvmType, dbType, Left(q))

      // Handle TApply by checking the outer type but preserving full jvmType
      case jvm.Type.TApply(outer, _) =>
        compute(outer, dbType).withJvmType(jvmType)

      // Handle generated types (within our package)
      case q: jvm.Type.Qualified if q.value.idents.startsWith(pkg.idents) =>
        Generated(jvmType, dbType, q)

      // Standard types
      case _ =>
        Standard(jvmType, dbType)
    }
    compute(jvmType, dbType)
  }
}
