package typr
package internal

/** Basically a mapping from whatever the postgres JDBC driver accepts or returns to a newtype we control
  */
case class CustomType(
    comment: String,
    sqlType: String,
    typoType: jvm.Type.Qualified,
    params: NonEmptyList[jvm.Param[jvm.Type]],
    toTypo: CustomType.ToTypo,
    fromTypo: CustomType.FromTypo,
    // some types is just very difficult to get right inside arrays using jdbc
    forbidArray: Boolean = false,
    toText: CustomType.Text,
    toTypoInArray: Option[CustomType.ToTypo] = None,
    fromTypoInArray: Option[CustomType.FromTypo] = None,
    objBody: jvm.Type.Qualified => List[jvm.ClassMember] = _ => Nil,
    pgTypeAnnotations: List[jvm.Annotation] = Nil
) {
  def withComment(newComment: String): CustomType = copy(comment = comment + newComment)
  def objBody0 = objBody(typoType)
  def toTypo0(expr: jvm.Code): jvm.Code = toTypo.toTypo(expr, typoType)
  def fromTypo0(expr: jvm.Code): jvm.Code = fromTypo.fromTypo0(expr)
}

object CustomType {
  case class Text(
      /* delegates to `Text` instance for this type */
      textType: jvm.Type,
      toTextType: jvm.Code => jvm.Code
  )
  object Text {
    def string(toTextType: jvm.Code => jvm.Code): Text =
      Text(TypesJava.String, toTextType)
  }

  case class ToTypo(
      jdbcType: jvm.Type,
      toTypo: (jvm.Code, jvm.Type.Qualified) => jvm.Code
  )

  case class FromTypo(
      jdbcType: jvm.Type,
      fromTypo: (jvm.Code, jvm.Type) => jvm.Code
  ) {
    def fromTypo0(expr: jvm.Code): jvm.Code = fromTypo(expr, jdbcType)
  }
}
