package typr.bridge

/** Normalized representation of a column name after stemming and expansion */
case class NormalizedName(
    original: List[String],
    stemmed: List[String],
    expanded: List[String],
    canonical: String
)

/** Stemmer for column name tokens with abbreviation expansion */
object ColumnStemmer {

  private val abbreviations: Map[String, List[String]] = Map(
    "id" -> List("identifier", "ident"),
    "addr" -> List("address"),
    "usr" -> List("user"),
    "cust" -> List("customer"),
    "prod" -> List("product"),
    "qty" -> List("quantity"),
    "num" -> List("number"),
    "amt" -> List("amount"),
    "desc" -> List("description"),
    "dt" -> List("date"),
    "ts" -> List("timestamp"),
    "msg" -> List("message"),
    "pwd" -> List("password"),
    "cfg" -> List("config", "configuration"),
    "org" -> List("organization"),
    "dept" -> List("department"),
    "emp" -> List("employee"),
    "mgr" -> List("manager"),
    "acct" -> List("account"),
    "txn" -> List("transaction"),
    "ref" -> List("reference"),
    "val" -> List("value"),
    "src" -> List("source"),
    "dst" -> List("destination"),
    "tgt" -> List("target"),
    "sz" -> List("size"),
    "len" -> List("length"),
    "cnt" -> List("count"),
    "idx" -> List("index"),
    "pos" -> List("position"),
    "seq" -> List("sequence"),
    "ver" -> List("version"),
    "rev" -> List("revision"),
    "pct" -> List("percent", "percentage"),
    "avg" -> List("average"),
    "min" -> List("minimum"),
    "max" -> List("maximum"),
    "tmp" -> List("temporary"),
    "perm" -> List("permanent"),
    "orig" -> List("original"),
    "prev" -> List("previous"),
    "curr" -> List("current"),
    "fk" -> List("foreignkey"),
    "pk" -> List("primarykey")
  )

  /** Apply Porter stemmer algorithm (simplified) */
  def stem(word: String): String = {
    var result = word.toLowerCase

    if (result.length < 3) return result

    result = step1a(result)
    result = step1b(result)
    result = step1c(result)
    result = step2(result)
    result = step3(result)
    result = step4(result)
    result = step5(result)

    result
  }

  private def step1a(word: String): String = {
    if (word.endsWith("sses")) word.dropRight(2)
    else if (word.endsWith("ies") && word.length > 4) word.dropRight(2)
    else if (word.endsWith("ss")) word
    else if (word.endsWith("s") && word.length > 3) word.dropRight(1)
    else word
  }

  private def step1b(word: String): String = {
    if (word.endsWith("eed") && measure(word.dropRight(3)) > 0) {
      word.dropRight(1)
    } else if (word.endsWith("ed") && hasVowel(word.dropRight(2))) {
      step1bHelper(word.dropRight(2))
    } else if (word.endsWith("ing") && hasVowel(word.dropRight(3))) {
      step1bHelper(word.dropRight(3))
    } else {
      word
    }
  }

  private def step1bHelper(stem: String): String = {
    if (stem.endsWith("at") || stem.endsWith("bl") || stem.endsWith("iz")) {
      stem + "e"
    } else if (endsWithDoubleConsonant(stem) && !stem.endsWith("l") && !stem.endsWith("s") && !stem.endsWith("z")) {
      stem.dropRight(1)
    } else if (measure(stem) == 1 && endsWithCvc(stem)) {
      stem + "e"
    } else {
      stem
    }
  }

  private def step1c(word: String): String = {
    if (word.endsWith("y") && hasVowel(word.dropRight(1))) {
      word.dropRight(1) + "i"
    } else {
      word
    }
  }

  private val step2Suffixes = Map(
    "ational" -> "ate",
    "tional" -> "tion",
    "enci" -> "ence",
    "anci" -> "ance",
    "izer" -> "ize",
    "abli" -> "able",
    "alli" -> "al",
    "entli" -> "ent",
    "eli" -> "e",
    "ousli" -> "ous",
    "ization" -> "ize",
    "ation" -> "ate",
    "ator" -> "ate",
    "alism" -> "al",
    "iveness" -> "ive",
    "fulness" -> "ful",
    "ousness" -> "ous",
    "aliti" -> "al",
    "iviti" -> "ive",
    "biliti" -> "ble"
  )

  private def step2(word: String): String = {
    step2Suffixes.find { case (suffix, _) => word.endsWith(suffix) } match {
      case Some((suffix, replacement)) =>
        val stem = word.dropRight(suffix.length)
        if (measure(stem) > 0) stem + replacement else word
      case None => word
    }
  }

  private val step3Suffixes = Map(
    "icate" -> "ic",
    "ative" -> "",
    "alize" -> "al",
    "iciti" -> "ic",
    "ical" -> "ic",
    "ful" -> "",
    "ness" -> ""
  )

  private def step3(word: String): String = {
    step3Suffixes.find { case (suffix, _) => word.endsWith(suffix) } match {
      case Some((suffix, replacement)) =>
        val stem = word.dropRight(suffix.length)
        if (measure(stem) > 0) stem + replacement else word
      case None => word
    }
  }

  private val step4Suffixes = List(
    "al",
    "ance",
    "ence",
    "er",
    "ic",
    "able",
    "ible",
    "ant",
    "ement",
    "ment",
    "ent",
    "ion",
    "ou",
    "ism",
    "ate",
    "iti",
    "ous",
    "ive",
    "ize"
  )

  private def step4(word: String): String = {
    step4Suffixes.find(word.endsWith) match {
      case Some(suffix) =>
        val stem = word.dropRight(suffix.length)
        if (suffix == "ion" && stem.nonEmpty && (stem.endsWith("s") || stem.endsWith("t"))) {
          if (measure(stem) > 1) stem else word
        } else if (measure(stem) > 1) {
          stem
        } else {
          word
        }
      case None => word
    }
  }

  private def step5(word: String): String = {
    val step5a = if (word.endsWith("e")) {
      val stem = word.dropRight(1)
      if (measure(stem) > 1 || (measure(stem) == 1 && !endsWithCvc(stem))) stem else word
    } else word

    if (measure(step5a) > 1 && endsWithDoubleConsonant(step5a) && step5a.endsWith("l")) {
      step5a.dropRight(1)
    } else {
      step5a
    }
  }

  private def isConsonant(word: String, i: Int): Boolean = {
    if (i < 0 || i >= word.length) return false
    word(i) match {
      case 'a' | 'e' | 'i' | 'o' | 'u' => false
      case 'y'                         => i == 0 || !isConsonant(word, i - 1)
      case _                           => true
    }
  }

  private def measure(word: String): Int = {
    if (word.isEmpty) return 0
    var count = 0
    var i = 0

    while (i < word.length && !isConsonant(word, i)) i += 1

    while (i < word.length) {
      while (i < word.length && isConsonant(word, i)) i += 1
      if (i < word.length) {
        count += 1
        while (i < word.length && !isConsonant(word, i)) i += 1
      }
    }
    count
  }

  private def hasVowel(word: String): Boolean =
    word.indices.exists(i => !isConsonant(word, i))

  private def endsWithDoubleConsonant(word: String): Boolean =
    word.length >= 2 &&
      word(word.length - 1) == word(word.length - 2) &&
      isConsonant(word, word.length - 1)

  private def endsWithCvc(word: String): Boolean = {
    if (word.length < 3) false
    else {
      val c1 = isConsonant(word, word.length - 3)
      val v = !isConsonant(word, word.length - 2)
      val c2 = isConsonant(word, word.length - 1)
      val lastChar = word.last
      c1 && v && c2 && lastChar != 'w' && lastChar != 'x' && lastChar != 'y'
    }
  }

  /** Normalize a list of tokens by stemming and expanding abbreviations */
  def normalize(tokens: List[String]): NormalizedName = {
    val stemmed = tokens.map(stem)

    val expanded = tokens.flatMap { t =>
      val lower = t.toLowerCase
      abbreviations.get(lower) match {
        case Some(expansions) => expansions
        case None             => List(lower)
      }
    }

    val expandedStemmed = expanded.map(stem)

    NormalizedName(
      original = tokens,
      stemmed = stemmed,
      expanded = expandedStemmed,
      canonical = stemmed.mkString("")
    )
  }
}
