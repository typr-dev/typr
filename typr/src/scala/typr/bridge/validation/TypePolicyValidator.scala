package typr.bridge.validation

import typr.bridge.TypeNarrower
import typr.bridge.model.TypePolicy

object TypePolicyValidator {

  private val integerFamily: Set[String] = Set("SMALLINT", "INTEGER", "BIGINT")
  private val floatFamily: Set[String] = Set("REAL", "DOUBLE", "DECIMAL")
  private val stringFamily: Set[String] = Set("VARCHAR")
  private val timestampFamily: Set[String] = Set("TIMESTAMP", "TIMESTAMPTZ")
  private val timeFamily: Set[String] = Set("TIME", "TIMETZ")

  private val integerOrder: Map[String, Int] = Map("SMALLINT" -> 0, "INTEGER" -> 1, "BIGINT" -> 2)
  private val floatOrder: Map[String, Int] = Map("REAL" -> 0, "DOUBLE" -> 1, "DECIMAL" -> 2)
  private val timestampOrder: Map[String, Int] = Map("TIMESTAMP" -> 0, "TIMESTAMPTZ" -> 1)
  private val timeOrder: Map[String, Int] = Map("TIME" -> 0, "TIMETZ" -> 1)

  private def sameFamily(a: String, b: String): Boolean =
    (integerFamily.contains(a) && integerFamily.contains(b)) ||
      (floatFamily.contains(a) && floatFamily.contains(b)) ||
      (stringFamily.contains(a) && stringFamily.contains(b)) ||
      (timestampFamily.contains(a) && timestampFamily.contains(b)) ||
      (timeFamily.contains(a) && timeFamily.contains(b))

  private def ordering(t: String): Option[Int] =
    integerOrder
      .get(t)
      .orElse(floatOrder.get(t))
      .orElse(timestampOrder.get(t))
      .orElse(timeOrder.get(t))

  def validate(
      domainType: String,
      sourceType: String,
      policy: TypePolicy
  ): Either[String, Unit] = {
    val domainNorm = TypeNarrower.normalizeDbType(domainType)
    val sourceNorm = TypeNarrower.normalizeDbType(sourceType)

    policy match {
      case TypePolicy.Exact =>
        if (domainNorm == sourceNorm) Right(())
        else Left(s"Exact policy requires matching types but got domain=$domainNorm, source=$sourceNorm")

      case TypePolicy.AllowWidening =>
        if (domainNorm == sourceNorm) Right(())
        else if (!sameFamily(domainNorm, sourceNorm))
          Left(s"Cannot widen across type families: domain=$domainNorm, source=$sourceNorm")
        else {
          val domainOrd = ordering(domainNorm).getOrElse(0)
          val sourceOrd = ordering(sourceNorm).getOrElse(0)
          if (sourceOrd <= domainOrd) Right(())
          else Left(s"Widening policy violated: source $sourceNorm is wider than domain $domainNorm")
        }

      case TypePolicy.AllowNarrowing =>
        if (domainNorm == sourceNorm) Right(())
        else if (!sameFamily(domainNorm, sourceNorm))
          Left(s"Cannot narrow across type families: domain=$domainNorm, source=$sourceNorm")
        else {
          val domainOrd = ordering(domainNorm).getOrElse(0)
          val sourceOrd = ordering(sourceNorm).getOrElse(0)
          if (sourceOrd >= domainOrd) Right(())
          else Left(s"Narrowing policy violated: source $sourceNorm is narrower than domain $domainNorm")
        }

      case TypePolicy.AllowPrecisionLoss =>
        if (domainNorm == sourceNorm) Right(())
        else if (floatFamily.contains(domainNorm) && floatFamily.contains(sourceNorm)) Right(())
        else if (integerFamily.contains(domainNorm) && floatFamily.contains(sourceNorm)) Right(())
        else if (floatFamily.contains(domainNorm) && integerFamily.contains(sourceNorm)) Right(())
        else Left(s"Precision loss policy only applies to numeric types: domain=$domainNorm, source=$sourceNorm")

      case TypePolicy.AllowTruncation =>
        if (domainNorm == sourceNorm) Right(())
        else if (stringFamily.contains(domainNorm) && stringFamily.contains(sourceNorm)) Right(())
        else Left(s"Truncation policy only applies to string types: domain=$domainNorm, source=$sourceNorm")

      case TypePolicy.AllowNullableToRequired =>
        Right(())
    }
  }

  def validateWithCanonical(
      domainCanonicalType: String,
      sourceDbType: String,
      policy: TypePolicy
  ): Either[String, Unit] = {
    val domainNorm = TypeNarrower.mapCanonicalToNormalized(domainCanonicalType)
    val sourceNorm = TypeNarrower.normalizeDbType(sourceDbType)
    validate(domainNorm, sourceNorm, policy)
  }
}
