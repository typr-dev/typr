package typo.openapi.codegen

import typo.internal.codegen.{CodeInterpolator, toCode}
import typo.jvm
import typo.jvm.Code.TreeOps
import typo.openapi.SumType

/** Support for JSON library annotations in OpenAPI code generation */
trait JsonLibSupport {

  /** Annotations for a model class */
  def modelAnnotations: List[jvm.Annotation]

  /** Annotations for a property */
  def propertyAnnotations(originalName: String): List[jvm.Annotation]

  /** Annotations for a value field (in wrapper types) - placed on parameter */
  def valueAnnotations: List[jvm.Annotation]

  /** Annotations for the constructor of a wrapper type - placed before constructor keyword in Kotlin */
  def constructorAnnotations: List[jvm.Annotation]

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
  override def constructorAnnotations: List[jvm.Annotation] = Nil
  override def wrapperAnnotations(tpe: jvm.Type.Qualified): List[jvm.Annotation] = Nil
  override def sumTypeAnnotations(sumType: SumType): List[jvm.Annotation] = Nil
}

/** Jackson annotations for OpenAPI code generation.
  *
  * Emits the same AST for all languages - each language renderer handles language-specific output:
  *   - Use-site targets (@get:) are rendered by Kotlin, ignored by Java/Scala
  *   - Annotation arrays render as [ ] in Kotlin, { } in Java
  *   - ClassOf renders as ::class in Kotlin, .class in Java (Kotlin auto-converts for annotation args)
  */
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
    // Use-site target is rendered by Kotlin (as @get:JsonValue), ignored by Java/Scala
    List(
      jvm.Annotation(Types.Jackson.JsonValue, Nil, useTarget = Some(jvm.Annotation.UseTarget.Get))
    )
  }

  override def constructorAnnotations: List[jvm.Annotation] = {
    // @JsonCreator goes on the constructor (Kotlin: `@JsonCreator constructor(...)`)
    // For Java, this annotation is placed before the constructor
    List(
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

    // Build nested annotations using proper AST - AnnotationArray renders as [ ] in Kotlin, { } in Java
    // Use ClassOf for annotation arguments - Kotlin auto-converts ::class (KClass) to Java Class for annotations
    val subTypesArgs = sumType.subtypeNames.map { subName =>
      val discValue = sumType.discriminator.mapping.getOrElse(subName, subName)
      val subtypeTpe = jvm.Type.Qualified(jvm.QIdent(List(jvm.Ident(subName))))
      jvm.Annotation(
        Types.Jackson.JsonSubTypesType,
        List(
          jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.ClassOf(subtypeTpe).code),
          jvm.Annotation.Arg.Named(jvm.Ident("name"), jvm.StrLit(discValue).code)
        )
      )
    }

    val subTypesAnnotation = jvm.Annotation(
      Types.Jackson.JsonSubTypes,
      List(jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.AnnotationArray(subTypesArgs.map(_.code)).code))
    )

    List(typeInfoAnnotation, subTypesAnnotation)
  }
}
