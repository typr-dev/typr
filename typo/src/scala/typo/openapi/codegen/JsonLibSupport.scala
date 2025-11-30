package typo.openapi.codegen

import typo.jvm
import typo.internal.codegen._
import typo.openapi.SumType

/** Support for JSON library annotations in OpenAPI code generation */
trait JsonLibSupport {

  /** Annotations for a model class */
  def modelAnnotations: List[jvm.Annotation]

  /** Annotations for a property */
  def propertyAnnotations(originalName: String): List[jvm.Annotation]

  /** Annotations for a value field (in wrapper types) */
  def valueAnnotations: List[jvm.Annotation]

  /** Annotations for a wrapper type */
  def wrapperAnnotations(tpe: jvm.Type.Qualified): List[jvm.Annotation]

  /** Annotations for a sum type (sealed interface) */
  def sumTypeAnnotations(sumType: SumType): List[jvm.Annotation]
}

/** No JSON annotations - for manual codec derivation */
object NoJsonLibSupport extends JsonLibSupport {
  override def modelAnnotations: List[jvm.Annotation] = Nil
  override def propertyAnnotations(originalName: String): List[jvm.Annotation] = Nil
  override def valueAnnotations: List[jvm.Annotation] = Nil
  override def wrapperAnnotations(tpe: jvm.Type.Qualified): List[jvm.Annotation] = Nil
  override def sumTypeAnnotations(sumType: SumType): List[jvm.Annotation] = Nil
}

/** Jackson annotations for OpenAPI code generation */
object JacksonSupport extends JsonLibSupport {

  override def modelAnnotations: List[jvm.Annotation] = Nil

  override def propertyAnnotations(originalName: String): List[jvm.Annotation] = {
    List(
      jvm.Annotation(
        Types.Jackson.JsonProperty,
        List(jvm.Annotation.Arg.Positional(jvm.StrLit(originalName).code))
      )
    )
  }

  override def valueAnnotations: List[jvm.Annotation] = {
    List(
      jvm.Annotation(Types.Jackson.JsonValue, Nil),
      jvm.Annotation(Types.Jackson.JsonCreator, Nil)
    )
  }

  override def wrapperAnnotations(tpe: jvm.Type.Qualified): List[jvm.Annotation] = {
    // Wrapper types use custom serializers/deserializers
    Nil
  }

  override def sumTypeAnnotations(sumType: SumType): List[jvm.Annotation] = {
    val typeInfoAnnotation = jvm.Annotation(
      Types.Jackson.JsonTypeInfo,
      List(
        jvm.Annotation.Arg.Named(jvm.Ident("use"), code"${Types.Jackson.JsonTypeInfo}.Id.NAME"),
        jvm.Annotation.Arg.Named(jvm.Ident("include"), code"${Types.Jackson.JsonTypeInfo}.As.EXISTING_PROPERTY"),
        jvm.Annotation.Arg.Named(jvm.Ident("property"), jvm.StrLit(sumType.discriminator.propertyName).code)
      )
    )

    val subTypesArgs = sumType.subtypeNames.map { subName =>
      val discValue = sumType.discriminator.mapping.getOrElse(subName, subName)
      code"@${Types.Jackson.JsonSubTypes}.Type(value = $subName.class, name = ${jvm.StrLit(discValue)})"
    }

    val subTypesAnnotation = jvm.Annotation(
      Types.Jackson.JsonSubTypes,
      List(jvm.Annotation.Arg.Positional(code"{ ${jvm.Code.Combined(subTypesArgs.flatMap(c => List(c, code", ")).dropRight(1))} }"))
    )

    List(typeInfoAnnotation, subTypesAnnotation)
  }
}
