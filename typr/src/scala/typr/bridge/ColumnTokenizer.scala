package typr.bridge

/** A single interpretation of how a column name can be parsed into tokens */
case class TokenInterpretation(
    tokens: List[String],
    confidence: Double,
    pattern: TokenPattern
)

/** The pattern used to tokenize a column name */
sealed trait TokenPattern
object TokenPattern {
  case object SnakeCase extends TokenPattern
  case object CamelCase extends TokenPattern
  case object PascalCase extends TokenPattern
  case object ScreamingSnake extends TokenPattern
  case object Mixed extends TokenPattern
  case object Ambiguous extends TokenPattern
  case object Single extends TokenPattern
}

/** An interpretation tree containing all possible parsings of a column name */
case class InterpretationTree(
    original: String,
    interpretations: List[TokenInterpretation]
) {
  def bestInterpretation: TokenInterpretation =
    if (interpretations.isEmpty) TokenInterpretation(List(original.toLowerCase), 0.5, TokenPattern.Single)
    else interpretations.maxBy(_.confidence)

  def isAmbiguous: Boolean = {
    val topTwo = interpretations.sortBy(-_.confidence).take(2)
    topTwo.size == 2 && (topTwo(0).confidence - topTwo(1).confidence) < 0.2
  }
}

/** Tokenizer for column names that handles various naming conventions */
object ColumnTokenizer {

  /** Parse a column name into all possible token interpretations */
  def tokenize(name: String): InterpretationTree = {
    val interpretations = List.newBuilder[TokenInterpretation]

    val snakeResult = trySnakeCase(name)
    if (snakeResult.tokens.nonEmpty) interpretations += snakeResult

    val camelResult = tryCamelCase(name)
    if (camelResult.tokens.nonEmpty) interpretations += camelResult

    val pascalResult = tryPascalCase(name)
    if (pascalResult.tokens.nonEmpty) interpretations += pascalResult

    val screamingResult = tryScreamingSnake(name)
    if (screamingResult.tokens.nonEmpty) interpretations += screamingResult

    if (hasNoDelimiters(name) && !hasCamelCaseBoundaries(name)) {
      interpretations ++= tryDictionarySplit(name)
    }

    InterpretationTree(name, interpretations.result())
  }

  private def trySnakeCase(name: String): TokenInterpretation = {
    if (name.contains("_")) {
      val tokens = name.split("_").toList.filter(_.nonEmpty).map(_.toLowerCase)
      val isCleanSnake = name == name.toLowerCase
      TokenInterpretation(
        tokens,
        confidence = if (isCleanSnake) 1.0 else 0.8,
        pattern = if (isCleanSnake) TokenPattern.SnakeCase else TokenPattern.Mixed
      )
    } else {
      TokenInterpretation(Nil, 0.0, TokenPattern.SnakeCase)
    }
  }

  private def tryCamelCase(name: String): TokenInterpretation = {
    if (name.nonEmpty && name.head.isLower && name.exists(_.isUpper)) {
      val tokens = splitCamelCase(name).map(_.toLowerCase)
      TokenInterpretation(tokens, 1.0, TokenPattern.CamelCase)
    } else {
      TokenInterpretation(Nil, 0.0, TokenPattern.CamelCase)
    }
  }

  private def tryPascalCase(name: String): TokenInterpretation = {
    if (name.nonEmpty && name.head.isUpper && name.tail.exists(_.isLower)) {
      val tokens = splitCamelCase(name).map(_.toLowerCase)
      if (tokens.size > 1) {
        TokenInterpretation(tokens, 1.0, TokenPattern.PascalCase)
      } else {
        TokenInterpretation(Nil, 0.0, TokenPattern.PascalCase)
      }
    } else {
      TokenInterpretation(Nil, 0.0, TokenPattern.PascalCase)
    }
  }

  private def tryScreamingSnake(name: String): TokenInterpretation = {
    if (name.contains("_") && name == name.toUpperCase) {
      val tokens = name.split("_").toList.filter(_.nonEmpty).map(_.toLowerCase)
      TokenInterpretation(tokens, 1.0, TokenPattern.ScreamingSnake)
    } else {
      TokenInterpretation(Nil, 0.0, TokenPattern.ScreamingSnake)
    }
  }

  private def splitCamelCase(name: String): List[String] = {
    val result = List.newBuilder[String]
    val current = new StringBuilder

    for (i <- name.indices) {
      val c = name(i)
      if (i > 0 && c.isUpper) {
        val prev = name(i - 1)
        val next = if (i + 1 < name.length) Some(name(i + 1)) else None

        val isAcronymContinuation = prev.isUpper && next.exists(_.isUpper)
        val isAcronymEnd = prev.isUpper && next.exists(_.isLower)

        if (!isAcronymContinuation && !isAcronymEnd) {
          if (current.nonEmpty) {
            result += current.toString
            current.clear()
          }
        } else if (isAcronymEnd) {
          if (current.length > 1) {
            result += current.toString.dropRight(1)
            current.clear()
            current += current.last
          }
        }
      }
      current += c
    }

    if (current.nonEmpty) result += current.toString
    result.result()
  }

  private def hasNoDelimiters(name: String): Boolean =
    !name.contains("_") && !name.contains("-")

  private def hasCamelCaseBoundaries(name: String): Boolean =
    name.exists(_.isUpper) && name.exists(_.isLower)

  private val commonWords = Set(
    "http",
    "https",
    "url",
    "api",
    "id",
    "uuid",
    "json",
    "xml",
    "sql",
    "user",
    "customer",
    "order",
    "product",
    "item",
    "price",
    "name",
    "first",
    "last",
    "email",
    "phone",
    "address",
    "city",
    "state",
    "zip",
    "date",
    "time",
    "created",
    "updated",
    "deleted",
    "modified",
    "at",
    "type",
    "status",
    "state",
    "code",
    "key",
    "value",
    "count",
    "num",
    "is",
    "has",
    "can",
    "will",
    "should",
    "by",
    "for",
    "to",
    "from",
    "min",
    "max",
    "avg",
    "sum",
    "total",
    "amount",
    "qty",
    "quantity",
    "start",
    "end",
    "begin",
    "stop",
    "open",
    "close",
    "read",
    "write",
    "get",
    "set",
    "add",
    "remove",
    "delete",
    "update",
    "old",
    "new",
    "prev",
    "next",
    "current",
    "active",
    "enabled",
    "disabled"
  )

  private def tryDictionarySplit(name: String): List[TokenInterpretation] = {
    val lower = name.toLowerCase
    val splits = findWordBoundaries(lower)

    splits.filter(_.nonEmpty).map { tokens =>
      val knownWordCount = tokens.count(commonWords.contains)
      val confidence =
        if (tokens.size <= 1) 0.3
        else 0.4 + (0.3 * (knownWordCount.toDouble / tokens.size))

      TokenInterpretation(tokens, confidence, TokenPattern.Ambiguous)
    }
  }

  private def findWordBoundaries(s: String): List[List[String]] = {
    if (s.isEmpty) return List(Nil)
    if (s.length > 30) return List(List(s))

    val results = List.newBuilder[List[String]]

    for (prefixLen <- 2 to math.min(s.length, 12)) {
      val prefix = s.take(prefixLen)
      if (commonWords.contains(prefix)) {
        val remaining = s.drop(prefixLen)
        if (remaining.isEmpty) {
          results += List(prefix)
        } else {
          for (rest <- findWordBoundaries(remaining)) {
            results += (prefix :: rest)
          }
        }
      }
    }

    if (s.length <= 4 && !commonWords.contains(s)) {
      results += List(s)
    }

    results.result().take(5)
  }
}
