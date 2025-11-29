package typo
package internal
package codegen

object FileStringEnum {
  def apply(options: InternalOptions, enm: ComputedStringEnum): jvm.File = {

    val instances: List[jvm.ClassMember] = List(
      options.dbLib.toList.flatMap(_.stringEnumInstances(enm.tpe, TypesJava.String, enm.dbEnum.name.value, openEnum = false)),
      options.jsonLibs.flatMap(_.stringEnumInstances(enm.tpe, TypesJava.String, openEnum = false).givens)
    ).flatten
    val comments = scaladoc(s"Enum `${enm.dbEnum.name.value}`" +: enm.members.toList.map { case (_, v) => " - " + v })

    val memberExpresions = enm.members.map { case (name, value) => (name, jvm.StrLit(value).code) }
    jvm.File(enm.tpe, jvm.Enum(Nil, comments, enm.tpe, memberExpresions, instances), secondaryTypes = Nil, scope = Scope.Main)
  }
}
