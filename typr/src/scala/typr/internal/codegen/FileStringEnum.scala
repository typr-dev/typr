package typr
package internal
package codegen

object FileStringEnum {
  def apply(options: InternalOptions, enm: ComputedStringEnum, adapter: DbAdapter): jvm.File = {
    val underlyingJvmType = options.lang.String
    val underlyingTypoType = TypoType.Standard(underlyingJvmType, adapter.textType)
    val instances: List[jvm.ClassMember] = List(
      options.dbLib.toList.flatMap(_.stringEnumInstances(enm.tpe, underlyingTypoType, enm.dbEnum.name.value, openEnum = false)),
      options.jsonLibs.flatMap(_.stringEnumInstances(enm.tpe, underlyingJvmType, openEnum = false).givens)
    ).flatten
    val comments = scaladoc(s"Enum `${enm.dbEnum.name.value}`" +: enm.members.toList.map { case (_, v) => " - " + v })

    val memberExpresions = enm.members.map { case (name, value) => (name, jvm.StrLit(value).code) }
    jvm.File(enm.tpe, jvm.Enum(Nil, comments, enm.tpe, memberExpresions, instances), secondaryTypes = Nil, scope = Scope.Main)
  }
}
