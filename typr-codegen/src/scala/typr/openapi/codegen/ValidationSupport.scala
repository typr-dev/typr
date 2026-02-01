package typr.openapi.codegen

import typr.jvm
import typr.jvm.Code.TreeOps
import typr.openapi.Property

/** Abstraction for generating validation annotations */
trait ValidationSupport {

  /** Annotations to add to a property/field based on validation constraints */
  def propertyAnnotations(prop: Property): List[jvm.Annotation]
}

/** No validation annotations */
object NoValidationSupport extends ValidationSupport {
  override def propertyAnnotations(prop: Property): List[jvm.Annotation] = Nil
}

/** JSR-380 / Jakarta Validation support */
object Jsr380ValidationSupport extends ValidationSupport {

  override def propertyAnnotations(prop: Property): List[jvm.Annotation] = {
    val annotations = List.newBuilder[jvm.Annotation]
    val v = prop.validation

    // @NotNull for required non-nullable fields
    if (prop.required && !prop.nullable) {
      annotations += jvm.Annotation(Types.Validation.NotNull, Nil)
    }

    // @Size for string length constraints
    (v.minLength, v.maxLength) match {
      case (Some(min), Some(max)) =>
        annotations += jvm.Annotation(
          Types.Validation.Size,
          List(
            jvm.Annotation.Arg.Named(jvm.Ident("min"), jvm.Code.Str(min.toString)),
            jvm.Annotation.Arg.Named(jvm.Ident("max"), jvm.Code.Str(max.toString))
          )
        )
      case (Some(min), None) =>
        annotations += jvm.Annotation(
          Types.Validation.Size,
          List(jvm.Annotation.Arg.Named(jvm.Ident("min"), jvm.Code.Str(min.toString)))
        )
      case (None, Some(max)) =>
        annotations += jvm.Annotation(
          Types.Validation.Size,
          List(jvm.Annotation.Arg.Named(jvm.Ident("max"), jvm.Code.Str(max.toString)))
        )
      case (None, None) => // no size constraint
    }

    // @Size for array length constraints
    (v.minItems, v.maxItems) match {
      case (Some(min), Some(max)) =>
        annotations += jvm.Annotation(
          Types.Validation.Size,
          List(
            jvm.Annotation.Arg.Named(jvm.Ident("min"), jvm.Code.Str(min.toString)),
            jvm.Annotation.Arg.Named(jvm.Ident("max"), jvm.Code.Str(max.toString))
          )
        )
      case (Some(min), None) =>
        annotations += jvm.Annotation(
          Types.Validation.Size,
          List(jvm.Annotation.Arg.Named(jvm.Ident("min"), jvm.Code.Str(min.toString)))
        )
      case (None, Some(max)) =>
        annotations += jvm.Annotation(
          Types.Validation.Size,
          List(jvm.Annotation.Arg.Named(jvm.Ident("max"), jvm.Code.Str(max.toString)))
        )
      case (None, None) => // no size constraint
    }

    // @Pattern for regex constraints
    v.pattern.foreach { pattern =>
      annotations += jvm.Annotation(
        Types.Validation.Pattern,
        List(jvm.Annotation.Arg.Named(jvm.Ident("regexp"), jvm.StrLit(pattern).code))
      )
    }

    // @Min / @Max for integer constraints
    v.minimum.filter(_.isWhole).foreach { min =>
      val minStr = min.toLong.toString + "L"
      annotations += jvm.Annotation(
        Types.Validation.Min,
        List(jvm.Annotation.Arg.Positional(jvm.Code.Str(minStr)))
      )
    }

    v.maximum.filter(_.isWhole).foreach { max =>
      val maxStr = max.toLong.toString + "L"
      annotations += jvm.Annotation(
        Types.Validation.Max,
        List(jvm.Annotation.Arg.Positional(jvm.Code.Str(maxStr)))
      )
    }

    // @DecimalMin / @DecimalMax for decimal constraints
    v.minimum.filterNot(_.isWhole).foreach { min =>
      annotations += jvm.Annotation(
        Types.Validation.DecimalMin,
        List(jvm.Annotation.Arg.Positional(jvm.StrLit(min.toString).code))
      )
    }

    v.maximum.filterNot(_.isWhole).foreach { max =>
      annotations += jvm.Annotation(
        Types.Validation.DecimalMax,
        List(jvm.Annotation.Arg.Positional(jvm.StrLit(max.toString).code))
      )
    }

    // Exclusive bounds using DecimalMin/DecimalMax with exclusive=true
    v.exclusiveMinimum.foreach { min =>
      annotations += jvm.Annotation(
        Types.Validation.DecimalMin,
        List(
          jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.StrLit(min.toString).code),
          jvm.Annotation.Arg.Named(jvm.Ident("inclusive"), jvm.Code.Str("false"))
        )
      )
    }

    v.exclusiveMaximum.foreach { max =>
      annotations += jvm.Annotation(
        Types.Validation.DecimalMax,
        List(
          jvm.Annotation.Arg.Named(jvm.Ident("value"), jvm.StrLit(max.toString).code),
          jvm.Annotation.Arg.Named(jvm.Ident("inclusive"), jvm.Code.Str("false"))
        )
      )
    }

    // @Email for email format
    if (v.email) {
      annotations += jvm.Annotation(Types.Validation.Email, Nil)
    }

    annotations.result()
  }
}
