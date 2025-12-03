package typo.openapi.codegen

import typo.{TypesJava, TypesScala}
import typo.internal.codegen.{CodeInterpolator, CodeOps, toCode}
import typo.jvm
import typo.jvm.Code.TreeOps
import typo.openapi.SumType

/** Support for JSON library annotations in OpenAPI code generation */
trait JsonLibSupport {

  /** Annotations for a model class */
  def modelAnnotations: List[jvm.Annotation]

  /** Annotations for a data class field property (uses @field: target in Kotlin) */
  def propertyAnnotations(originalName: String): List[jvm.Annotation]

  /** Annotations for a method property (no use-site target) */
  def methodPropertyAnnotations(originalName: String): List[jvm.Annotation]

  /** Annotations for a value field (in wrapper types) - placed on parameter */
  def valueAnnotations: List[jvm.Annotation]

  /** Annotations for the constructor of a wrapper type - placed before constructor keyword in Kotlin */
  def constructorAnnotations: List[jvm.Annotation]

  /** Annotations for a wrapper type */
  def wrapperAnnotations(tpe: jvm.Type.Qualified): List[jvm.Annotation]

  /** Annotations for a sum type (sealed interface) */
  def sumTypeAnnotations(sumType: SumType): List[jvm.Annotation]

  /** Static members to add to object type companion objects (e.g., Circe codecs) */
  def objectTypeStaticMembers(tpe: jvm.Type.Qualified): List[jvm.ClassMember]

  /** Static members to add to wrapper type companion objects */
  def wrapperTypeStaticMembers(tpe: jvm.Type.Qualified, underlyingType: jvm.Type): List[jvm.ClassMember]

  /** Static members to add to enum type companion objects */
  def enumTypeStaticMembers(tpe: jvm.Type.Qualified): List[jvm.ClassMember]

  /** Static members to add to sum type companion objects */
  def sumTypeStaticMembers(tpe: jvm.Type.Qualified, sumType: SumType): List[jvm.ClassMember]
}

/** No JSON annotations - for manual codec derivation */
object NoJsonLibSupport extends JsonLibSupport {
  override def modelAnnotations: List[jvm.Annotation] = Nil
  override def propertyAnnotations(originalName: String): List[jvm.Annotation] = Nil
  override def methodPropertyAnnotations(originalName: String): List[jvm.Annotation] = Nil
  override def valueAnnotations: List[jvm.Annotation] = Nil
  override def constructorAnnotations: List[jvm.Annotation] = Nil
  override def wrapperAnnotations(tpe: jvm.Type.Qualified): List[jvm.Annotation] = Nil
  override def sumTypeAnnotations(sumType: SumType): List[jvm.Annotation] = Nil
  override def objectTypeStaticMembers(tpe: jvm.Type.Qualified): List[jvm.ClassMember] = Nil
  override def wrapperTypeStaticMembers(tpe: jvm.Type.Qualified, underlyingType: jvm.Type): List[jvm.ClassMember] = Nil
  override def enumTypeStaticMembers(tpe: jvm.Type.Qualified): List[jvm.ClassMember] = Nil
  override def sumTypeStaticMembers(tpe: jvm.Type.Qualified, sumType: SumType): List[jvm.ClassMember] = Nil
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
        List(jvm.Annotation.Arg.Positional(jvm.StrLit(originalName).code)),
        useTarget = Some(jvm.Annotation.UseTarget.Field)
      )
    )
  }

  override def methodPropertyAnnotations(originalName: String): List[jvm.Annotation] = {
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

  override def objectTypeStaticMembers(tpe: jvm.Type.Qualified): List[jvm.ClassMember] = Nil
  override def wrapperTypeStaticMembers(tpe: jvm.Type.Qualified, underlyingType: jvm.Type): List[jvm.ClassMember] = Nil
  override def enumTypeStaticMembers(tpe: jvm.Type.Qualified): List[jvm.ClassMember] = Nil
  override def sumTypeStaticMembers(tpe: jvm.Type.Qualified, sumType: SumType): List[jvm.ClassMember] = Nil
}

/** Circe JSON support for Scala - generates encoder/decoder derivation in companion objects */
object CirceSupport extends JsonLibSupport {
  override def modelAnnotations: List[jvm.Annotation] = Nil
  override def propertyAnnotations(originalName: String): List[jvm.Annotation] = Nil
  override def methodPropertyAnnotations(originalName: String): List[jvm.Annotation] = Nil
  override def valueAnnotations: List[jvm.Annotation] = Nil
  override def constructorAnnotations: List[jvm.Annotation] = Nil
  override def wrapperAnnotations(tpe: jvm.Type.Qualified): List[jvm.Annotation] = Nil
  override def sumTypeAnnotations(sumType: SumType): List[jvm.Annotation] = Nil

  override def objectTypeStaticMembers(tpe: jvm.Type.Qualified): List[jvm.ClassMember] = {
    // Generate: implicit val encoder: Encoder[T] = deriveEncoder[T]
    //           implicit val decoder: Decoder[T] = deriveDecoder[T]
    val encoderVal = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("encoder"),
      tpe = jvm.Type.TApply(Types.Circe.Encoder, List(tpe)),
      body = Some(code"${Types.Circe.deriveEncoder}[$tpe]"),
      isLazy = false,
      isOverride = false,
      isImplicit = true
    )
    val decoderVal = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("decoder"),
      tpe = jvm.Type.TApply(Types.Circe.Decoder, List(tpe)),
      body = Some(code"${Types.Circe.deriveDecoder}[$tpe]"),
      isLazy = false,
      isOverride = false,
      isImplicit = true
    )
    List(encoderVal, decoderVal)
  }

  override def wrapperTypeStaticMembers(tpe: jvm.Type.Qualified, underlyingType: jvm.Type): List[jvm.ClassMember] = {
    // For wrapper types, derive from underlying type:
    // implicit val encoder: Encoder[T] = Encoder[U].contramap(_.value)
    // implicit val decoder: Decoder[T] = Decoder[U].map(T.apply)
    val encoderVal = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("encoder"),
      tpe = jvm.Type.TApply(Types.Circe.Encoder, List(tpe)),
      body = Some(code"${Types.Circe.Encoder}[$underlyingType].contramap(_.value)"),
      isLazy = false,
      isOverride = false,
      isImplicit = true
    )
    val decoderVal = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("decoder"),
      tpe = jvm.Type.TApply(Types.Circe.Decoder, List(tpe)),
      body = Some(code"${Types.Circe.Decoder}[$underlyingType].map(${tpe.value.name}.apply)"),
      isLazy = false,
      isOverride = false,
      isImplicit = true
    )
    List(encoderVal, decoderVal)
  }

  override def enumTypeStaticMembers(tpe: jvm.Type.Qualified): List[jvm.ClassMember] = {
    // For enums: encode/decode as the string value
    // implicit val encoder: Encoder[T] = Encoder.encodeString.contramap(_.value)
    // implicit val decoder: Decoder[T] = Decoder.decodeString.emap(apply)
    // Note: apply(str) returns Either[String, T] which is what emap expects
    val encoderVal = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("encoder"),
      tpe = jvm.Type.TApply(Types.Circe.Encoder, List(tpe)),
      body = Some(code"${Types.Circe.Encoder}.encodeString.contramap(_.value)"),
      isLazy = false,
      isOverride = false,
      isImplicit = true
    )
    val decoderVal = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("decoder"),
      tpe = jvm.Type.TApply(Types.Circe.Decoder, List(tpe)),
      body = Some(code"${Types.Circe.Decoder}.decodeString.emap(apply)"),
      isLazy = false,
      isOverride = false,
      isImplicit = true
    )
    List(encoderVal, decoderVal)
  }

  override def sumTypeStaticMembers(tpe: jvm.Type.Qualified, sumType: SumType): List[jvm.ClassMember] = {
    // For sum types, we generate discriminator-based encoder/decoder
    // The encoder delegates to the subtype encoder and adds the discriminator field
    // The decoder reads the discriminator and delegates to the appropriate subtype decoder
    val discriminatorProp = sumType.discriminator.propertyName
    val subtypeNames = sumType.subtypeNames

    // Generate encoder: pattern match on each subtype and encode using its encoder
    // Encoder[Animal] = Encoder.instance { case x: Cat => Encoder[Cat].apply(x) case x: Dog => Encoder[Dog].apply(x) }
    val encoderCases = subtypeNames
      .map { name =>
        code"case x: $name => ${Types.Circe.Encoder}[$name].apply(x)"
      }
      .mkCode("\n      ")
    val encoderVal = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("encoder"),
      tpe = jvm.Type.TApply(Types.Circe.Encoder, List(tpe)),
      body = Some(code"${Types.Circe.Encoder}.instance {\n      $encoderCases\n    }"),
      isLazy = false,
      isOverride = false,
      isImplicit = true
    )

    // Generate decoder: read discriminator, then delegate to subtype decoder
    // Decoder[Animal] = Decoder.instance { cursor =>
    //   cursor.get[String]("animal_type").flatMap {
    //     case "Cat" => cursor.as[Cat]
    //     case "Dog" => cursor.as[Dog]
    //     case other => Left(DecodingFailure(s"Unknown discriminator: $other", cursor.history))
    //   }
    // }
    val decoderCases = subtypeNames
      .map { name =>
        val discValue = sumType.discriminator.mapping.getOrElse(name, name)
        code"""case "$discValue" => cursor.as[$name]"""
      }
      .mkCode("\n        ")
    val decoderVal = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("decoder"),
      tpe = jvm.Type.TApply(Types.Circe.Decoder, List(tpe)),
      body = Some(code"""${Types.Circe.Decoder}.instance { cursor =>
      cursor.get[${TypesJava.String}]("$discriminatorProp").flatMap {
        $decoderCases
        case other => ${TypesScala.Left}(${Types.Circe.DecodingFailure}(s"Unknown discriminator value: $$other", cursor.history))
      }
    }"""),
      isLazy = false,
      isOverride = false,
      isImplicit = true
    )
    List(encoderVal, decoderVal)
  }
}
