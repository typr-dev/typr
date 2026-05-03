package typr.bridge.model

sealed trait Severity {
  def ordinal: Int
}
object Severity {
  case object Error extends Severity { val ordinal: Int = 0 }
  case object Warning extends Severity { val ordinal: Int = 1 }
  case object Info extends Severity { val ordinal: Int = 2 }
}

case class CheckFinding(
    entityName: String,
    sourceKey: Option[String],
    fieldName: Option[String],
    severity: Severity,
    code: CheckCode,
    message: String,
    suggestion: Option[String]
)

sealed trait CheckCode
object CheckCode {
  case object UnannotatedField extends CheckCode
  case object MissingRequiredField extends CheckCode
  case object TypeIncompatible extends CheckCode
  case object TypePolicyViolation extends CheckCode
  case object NullabilityMismatch extends CheckCode
  case object InvalidMergeFromRef extends CheckCode
  case object InvalidSplitFromRef extends CheckCode
  case object InvalidComputedFromRef extends CheckCode
  case object SourceEntityNotFound extends CheckCode
  case object NoPrimarySource extends CheckCode
  case object NoFields extends CheckCode
}

case class CheckReport(
    findings: List[CheckFinding],
    entitySummaries: List[EntitySummary],
    timestamp: Long
) {
  def errors: List[CheckFinding] = findings.filter(_.severity == Severity.Error)
  def warnings: List[CheckFinding] = findings.filter(_.severity == Severity.Warning)
  def infos: List[CheckFinding] = findings.filter(_.severity == Severity.Info)
  def hasErrors: Boolean = errors.nonEmpty
  def exitCode: Int = if (hasErrors) 1 else 0
}

case class EntitySummary(
    name: String,
    fieldCount: Int,
    sourceCount: Int,
    forwardCount: Int,
    dropCount: Int,
    customCount: Int,
    errorCount: Int,
    warningCount: Int
)
