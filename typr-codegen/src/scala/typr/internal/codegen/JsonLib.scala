package typr
package internal
package codegen

trait JsonLib {
  def defaultedInstance: JsonLib.Instances
  def stringEnumInstances(wrapperType: jvm.Type, underlying: jvm.Type, openEnum: Boolean): JsonLib.Instances
  def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, fieldName: jvm.Ident, underlying: jvm.Type): JsonLib.Instances
  def productInstances(tpe: jvm.Type, fields: NonEmptyList[JsonLib.Field]): JsonLib.Instances
  def missingInstances: List[jvm.ClassMember]

  final def customTypeInstances(ct: CustomType): JsonLib.Instances =
    ct.params match {
      case NonEmptyList(param, Nil) =>
        wrapperTypeInstances(ct.typoType, param.name, param.tpe)
      case more =>
        productInstances(ct.typoType, more.map(param => JsonLib.Field(param.name, jvm.StrLit(param.name.value), param.tpe)))
    }

  final def instances(tpe: jvm.Type, cols: NonEmptyList[ComputedColumn]): JsonLib.Instances =
    productInstances(tpe, cols.map(col => JsonLib.Field(col.name, jvm.StrLit(col.dbName.value), col.tpe)))
}

object JsonLib {
  case class Field(scalaName: jvm.Ident, jsonName: jvm.StrLit, tpe: jvm.Type)

  /** Merge field annotations preserving order */
  def mergeFieldAnnotations(maps: Seq[(jvm.Ident, List[jvm.Annotation])]): Map[jvm.Ident, List[jvm.Annotation]] = {
    val result = scala.collection.mutable.LinkedHashMap.empty[jvm.Ident, List[jvm.Annotation]]
    maps.foreach { case (k, v) =>
      result.get(k) match {
        case Some(existing) => result.put(k, existing ++ v)
        case None           => result.put(k, v)
      }
    }
    result.toMap
  }

  /** Unified return type for JSON library support - works for both typeclass and annotation-based libraries */
  case class Instances(
      givens: List[jvm.Given],
      typeAnnotations: List[jvm.Annotation],
      fieldAnnotations: Map[jvm.Ident, List[jvm.Annotation]],
      additionalFiles: List[jvm.File]
  ) {
    def ++(other: Instances): Instances =
      Instances(
        givens = this.givens ++ other.givens,
        typeAnnotations = this.typeAnnotations ++ other.typeAnnotations,
        fieldAnnotations = mergeFieldAnnotations(this.fieldAnnotations.toSeq ++ other.fieldAnnotations.toSeq),
        additionalFiles = this.additionalFiles ++ other.additionalFiles
      )
  }

  object Instances {
    val Empty: Instances = Instances(Nil, Nil, Map.empty, Nil)

    def fromGivens(givens: List[jvm.Given]): Instances = Empty.copy(givens = givens)
  }
}
