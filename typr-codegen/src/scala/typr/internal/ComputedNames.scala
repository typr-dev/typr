package typr
package internal

case class ComputedNames(naming: Naming, source: Source, maybeId: Option[IdComputed], enableFieldValue: Boolean, enableDsl: Boolean) {
  val RepoName: jvm.Type.Qualified = jvm.Type.Qualified(naming.repoName(source))
  val RepoImplName: jvm.Type.Qualified = jvm.Type.Qualified(naming.repoImplName(source))
  val RepoMockName: jvm.Type.Qualified = jvm.Type.Qualified(naming.repoMockName(source))
  val RowName: jvm.Type.Qualified = jvm.Type.Qualified(naming.rowName(source))
  val FieldOrIdValueName: Option[jvm.Type.Qualified] = if (enableFieldValue) Some(jvm.Type.Qualified(naming.fieldOrIdValueName(source))) else None
  val FieldsName: Option[jvm.Type.Qualified] = if (enableDsl) Some(jvm.Type.Qualified(naming.fieldsName(source))) else None

  def isIdColumn(colName: db.ColName): Boolean =
    maybeId.exists(_.cols.exists(_.dbName == colName))
}
