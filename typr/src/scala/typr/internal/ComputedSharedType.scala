package typr
package internal

/** Computed representation of a shared type from TypeDefinitions.
  *
  * A shared type is a wrapper type (like UserId or FirstName) that is generated from TypeEntry definitions.
  */
case class ComputedSharedType(
    /** The qualified type name for the wrapper (e.g., myapp.userdefined.FirstName) */
    tpe: jvm.Type.Qualified,
    /** The underlying JVM type for the value field (e.g., String, Long) */
    underlyingJvmType: jvm.Type,
    /** The canonical database type from compatibility checking */
    underlyingDbType: db.Type,
    /** The original TypeEntry definition */
    entry: TypeEntry,
    /** The compatibility check result with all matches */
    compatResult: TypeCompatibilityChecker.CheckResult.Compatible
)

object ComputedSharedType {

  /** Create computed shared types from scan results.
    *
    * @param scanResult
    *   The result from TypeMatcher.scanTables
    * @param sharedTypesPackage
    *   Package where shared types will be generated
    * @param typeMapper
    *   The type mapper to convert db.Type to jvm.Type
    * @return
    *   List of computed shared types for compatible entries
    */
  def fromScanResult(
      scanResult: TypeMatcher.ScanResult,
      sharedTypesPackage: jvm.QIdent,
      typeMapper: TypeMapperJvm
  ): List[ComputedSharedType] =
    scanResult.compatibilityResults.collect { case c: TypeCompatibilityChecker.CheckResult.Compatible =>
      val tpe = jvm.Type.Qualified(sharedTypesPackage / jvm.Ident(c.entry.name))
      val underlyingJvmType = mapCanonicalType(c.canonicalType, typeMapper)
      ComputedSharedType(
        tpe = tpe,
        underlyingJvmType = underlyingJvmType,
        underlyingDbType = c.canonicalType,
        entry = c.entry,
        compatResult = c
      )
    }

  /** Map a canonical db.Type to jvm.Type, handling arrays */
  private def mapCanonicalType(dbType: db.Type, typeMapper: TypeMapperJvm): jvm.Type =
    dbType match {
      case db.PgType.Array(inner) =>
        jvm.Type.ArrayOf(typeMapper.baseType(inner))
      case db.DuckDbType.ListType(inner) =>
        jvm.Type.ArrayOf(typeMapper.baseType(inner))
      case db.DuckDbType.ArrayType(inner, _) =>
        jvm.Type.ArrayOf(typeMapper.baseType(inner))
      case other =>
        typeMapper.baseType(other)
    }
}
