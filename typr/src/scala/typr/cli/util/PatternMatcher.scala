package typr.cli.util

import typr.Selector
import typr.config.generated.FeatureMatcher
import typr.config.generated.FeatureMatcherArray
import typr.config.generated.FeatureMatcherObject
import typr.config.generated.FeatureMatcherString
import typr.config.generated.MatcherValue
import typr.config.generated.MatcherValueArray
import typr.config.generated.MatcherValueObject
import typr.config.generated.MatcherValueString

object PatternMatcher {

  def toSelector(patterns: List[String]): Selector = {
    if (patterns.isEmpty) Selector.None
    else {
      val (excludes, includes) = patterns.partition(_.startsWith("!"))
      val excludePatterns = excludes.map(_.stripPrefix("!"))

      val includeSelector =
        if (includes.isEmpty) Selector.All
        else includes.map(patternToSelector).reduce(_ or _)

      val excludeSelector =
        if (excludePatterns.isEmpty) Selector.None
        else excludePatterns.map(patternToSelector).reduce(_ or _)

      includeSelector and !excludeSelector
    }
  }

  def fromFeatureMatcher(matcher: Option[FeatureMatcher]): Selector =
    fromFeatureMatcherWithDefault(matcher, Selector.All)

  def fromFeatureMatcherDefaultNone(matcher: Option[FeatureMatcher]): Selector =
    fromFeatureMatcherWithDefault(matcher, Selector.None)

  def fromFeatureMatcherWithDefault(matcher: Option[FeatureMatcher], default: Selector): Selector =
    matcher match {
      case None => default
      case Some(m: FeatureMatcherString) =>
        if (m.value == "all") Selector.All
        else patternToSelector(m.value)
      case Some(m: FeatureMatcherArray) =>
        toSelector(m.value)
      case Some(m: FeatureMatcherObject) =>
        val includeSelector = m.include match {
          case None => Selector.All
          case Some(json) =>
            json.asString match {
              case Some("all")   => Selector.All
              case Some(pattern) => patternToSelector(pattern)
              case None =>
                json.asArray.map(_.flatMap(_.asString).toList) match {
                  case Some(patterns) => toSelector(patterns)
                  case None           => Selector.All
                }
            }
        }
        val excludeSelector = m.exclude match {
          case None           => Selector.None
          case Some(patterns) => toSelector(patterns)
        }
        includeSelector and !excludeSelector
    }

  def fromMatcherValue(matcher: MatcherValue): Selector =
    fromMatcherValue(Some(matcher))

  def fromMatcherValue(matcher: Option[MatcherValue]): Selector =
    matcher match {
      case None => Selector.All
      case Some(m: MatcherValueString) =>
        if (m.value == "all") Selector.All
        else patternToSelector(m.value)
      case Some(m: MatcherValueArray) =>
        toSelector(m.value)
      case Some(m: MatcherValueObject) =>
        val includeSelector = m.include match {
          case None => Selector.All
          case Some(json) =>
            json.asString match {
              case Some("all")   => Selector.All
              case Some(pattern) => patternToSelector(pattern)
              case None =>
                json.asArray.map(_.flatMap(_.asString).toList) match {
                  case Some(patterns) => toSelector(patterns)
                  case None           => Selector.All
                }
            }
        }
        val excludeSelector = m.exclude match {
          case None           => Selector.None
          case Some(patterns) => toSelector(patterns)
        }
        includeSelector and !excludeSelector
    }

  private def patternToSelector(pattern: String): Selector = {
    val regex = globToRegex(pattern)
    Selector(rel => regex.matches(rel.value) || regex.matches(rel.name))
  }

  private def globToRegex(glob: String): scala.util.matching.Regex = {
    val escaped = glob
      .replace(".", "\\.")
      .replace("**", "\u0000")
      .replace("*", "[^.]*")
      .replace("\u0000", ".*")
      .replace("?", ".")
    s"^$escaped$$".r
  }
}
