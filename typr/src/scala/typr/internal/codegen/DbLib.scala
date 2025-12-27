package typr
package internal
package codegen

trait DbLib {
  def lang: Lang
  def resolveConstAs(typoType: TypoType): jvm.Code
  def defaultedInstance: List[jvm.Given]
  def repoSig(repoMethod: RepoMethod): Either[DbLib.NotImplementedFor, jvm.Method]
  def repoImpl(repoMethod: RepoMethod): jvm.Body
  def mockRepoImpl(id: IdComputed, repoMethod: RepoMethod, maybeToRow: Option[jvm.Param[jvm.Type.Function1]]): jvm.Body
  def testInsertMethod(x: ComputedTestInserts.InsertMethod): jvm.Method
  def stringEnumInstances(wrapperType: jvm.Type, underlyingTypoType: TypoType, sqlType: String, openEnum: Boolean): List[jvm.ClassMember]
  def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, underlyingJvmType: jvm.Type, underlyingDbType: db.Type, overrideDbType: Option[String]): List[jvm.ClassMember]
  def structInstances(computed: ComputedOracleObjectType): List[jvm.ClassMember]
  def collectionInstances(computed: ComputedOracleCollectionType): List[jvm.ClassMember]
  def missingInstances: List[jvm.ClassMember]
  def rowInstances(tpe: jvm.Type, cols: NonEmptyList[ComputedColumn], rowType: DbLib.RowType): List[jvm.ClassMember]
  def customTypeInstances(ct: CustomType): List[jvm.ClassMember]
  def additionalFiles: List[jvm.File]

  /** Whether this DbLib needs constAs expressions for generateCompositeIn. Legacy DSLs need them, DbLibTypo does not.
    */
  def needsConstAsForCompositeIn: Boolean

  /** Generate a composite IN expression for checking if a tuple of fields is in a list of IDs. For new DSLs: uses In with Rows and Tuples For legacy DSLs: uses CompositeIn with TuplePart
    *
    * @param idsExpr
    *   the expression for the list of IDs (e.g., "compositeIds")
    * @param idType
    *   the type of the composite ID
    * @param fieldExprs
    *   the field expressions for this table's columns (e.g., List(deptCode(), deptRegion()))
    * @param fieldNames
    *   the field names in the ID type (for extracting values, e.g., List(deptCode, deptRegion))
    * @param constAsExprs
    *   const expressions for legacy DSL (one per field) - only used when needsConstAsForCompositeIn is true
    */
  def generateCompositeIn(
      idsExpr: jvm.Code,
      idType: jvm.Type,
      fieldExprs: List[jvm.Code],
      fieldNames: List[jvm.Ident],
      constAsExprs: List[jvm.Code]
  ): jvm.Code

  /** Generate a single CompositeIn.Part expression for one field in a composite key.
    *
    * @param fieldType
    *   the type of the field (e.g., Int, String)
    * @param idType
    *   the composite ID type
    * @param rowType
    *   the row type
    * @param fieldExpr
    *   the field expression (e.g., "empNumber()")
    * @param fieldName
    *   the field name for the getter (e.g., "empNumber")
    * @param pgType
    *   the database type expression
    */
  def compositeInPart(fieldType: jvm.Type, idType: jvm.Type, rowType: jvm.Type, fieldExpr: jvm.Code, fieldName: jvm.Ident, pgType: jvm.Code): jvm.Code

  /** Generate a complete CompositeIn expression from a list of parts and the IDs.
    *
    * @param partsExpr
    *   the expression for the list of parts
    * @param idsExpr
    *   the expression for the composite IDs
    */
  def compositeInConstruct(partsExpr: jvm.Code, idsExpr: jvm.Code): jvm.Code
}

object DbLib {
  case class NotImplementedFor(repoMethod: RepoMethod, library: String)

  sealed trait RowType
  object RowType {
    case object Readable extends RowType
    case object Writable extends RowType
    case object ReadWriteable extends RowType
  }
}
