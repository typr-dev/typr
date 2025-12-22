package typo
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
