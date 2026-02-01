package typr.openapi

/** Represents an error during OpenAPI parsing or validation */
sealed trait OpenApiError {
  def message: String
  def location: Option[String]
  def suggestion: Option[String]

  def format: String = {
    val base = message
    val withLocation = location.map(l => s"$base at $l").getOrElse(base)
    suggestion.map(s => s"$withLocation. $s").getOrElse(withLocation)
  }
}

object OpenApiError {

  /** Parse error from the OpenAPI parser */
  case class ParseError(
      message: String,
      location: Option[String] = None,
      suggestion: Option[String] = None
  ) extends OpenApiError

  /** Schema validation error */
  case class SchemaValidationError(
      schemaPath: String,
      issue: String,
      location: Option[String] = None,
      suggestion: Option[String] = None
  ) extends OpenApiError {
    override def message: String = s"Schema '$schemaPath': $issue"
  }

  /** Reference resolution error */
  case class UnresolvedRefError(
      ref: String,
      context: String,
      location: Option[String] = None
  ) extends OpenApiError {
    override def message: String = s"Unresolved reference '$ref' in $context"
    override def suggestion: Option[String] = Some(
      s"Check that '$ref' exists in #/components/schemas or is a valid external reference"
    )
  }

  /** Missing required field error */
  case class MissingFieldError(
      path: String,
      field: String,
      location: Option[String] = None,
      suggestion: Option[String] = None
  ) extends OpenApiError {
    override def message: String = s"Missing required field '$field' in $path"
  }

  /** Invalid discriminator configuration */
  case class InvalidDiscriminatorError(
      schemaName: String,
      issue: String,
      location: Option[String] = None,
      suggestion: Option[String] = None
  ) extends OpenApiError {
    override def message: String = s"Invalid discriminator in schema '$schemaName': $issue"
  }

  /** General validation error */
  case class ValidationError(
      message: String,
      location: Option[String] = None,
      suggestion: Option[String] = None
  ) extends OpenApiError

  /** Helper to format a list of errors */
  def formatAll(errors: List[OpenApiError]): String = {
    errors.zipWithIndex
      .map { case (err, idx) =>
        s"${idx + 1}. ${err.format}"
      }
      .mkString("\n")
  }

  /** Helper to format errors as a list of strings */
  def toStrings(errors: List[OpenApiError]): List[String] = {
    errors.map(_.format)
  }
}
