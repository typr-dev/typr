package typo
package internal

/** Database-agnostic type mapper from database metadata to db.Type */
trait TypeMapperDb {

  /** Map a type name from JDBC ResultSetMetaData to db.Type
    *
    * @param typeName
    *   The type name (from ResultSetMetaData.getColumnTypeName() or similar)
    * @param characterMaximumLength
    *   Optional character length or precision
    * @param logWarning
    *   Callback to log a warning if the type cannot be mapped
    */
  def dbTypeFrom(typeName: String, characterMaximumLength: Option[Int])(logWarning: () => Unit): db.Type
}
