package typr.internal.analysis

import typr.internal.codegen.CodeOps
import typr.internal.compat.*
import typr.{db, jvm}

case class DecomposedSql(frags: List[DecomposedSql.Fragment]) {
  val sqlWithQuestionMarks: String = render(_ => "?")
  val sqlWithNulls: String = render(_ => "null")

  def render(f: Int => String): String = {
    var paramNum = 0
    frags.collect {
      case DecomposedSql.SqlText(text) => text
      case _: DecomposedSql.Param =>
        val rendered = f(paramNum)
        paramNum += 1
        rendered
    }.mkString
  }

  def renderCode(f: Int => jvm.Code): jvm.Code = {
    var paramNum = 0
    frags
      .collect {
        case DecomposedSql.SqlText(text) => jvm.Code.Str(text)
        case _: DecomposedSql.Param =>
          val rendered = f(paramNum)
          paramNum += 1
          rendered
      }
      .mkCode(jvm.Code.Empty)
  }

  val params: List[DecomposedSql.Param] =
    frags.collect { case p: DecomposedSql.Param => p }

  // handle that a named parameter can be used multiple times
  val paramNamesWithIndices: List[(DecomposedSql.Param, List[Int])] = {
    val paramsWithIndex = params.zipWithIndex

    def indicesFor(name: db.ColName): List[Int] =
      paramsWithIndex.collect { case (DecomposedSql.NamedParam(parsedName), i) if parsedName.name == name => i }

    paramsWithIndex
      .distinctByCompat {
        case (DecomposedSql.NotNamedParam, idx)        => idx.toString
        case (DecomposedSql.NamedParam(parsedName), _) => parsedName.name
      }
      .map {
        case (DecomposedSql.NotNamedParam, idx)            => (DecomposedSql.NotNamedParam, List(idx))
        case (p @ DecomposedSql.NamedParam(parsedName), _) => p -> indicesFor(parsedName.name)
      }
  }
}

object DecomposedSql {
  sealed trait Fragment
  case class SqlText(value: String) extends Fragment
  sealed trait Param extends Fragment
  case object NotNamedParam extends Param
  case class NamedParam(name: ParsedName) extends Param

  /** Parse the query string containing named parameters and result a parse result, which holds the parsed sql (named parameters replaced by standard '?' parameters and an ordered list of the named
    * parameters.
    *
    * SQL parsing code borrowed from Adam Crume. Thanks Adam. See <a
    * href="http://www.javaworld.com/article/2077706/core-java/named-parameters-for-preparedstatement.html?page=2">http://www.javaworld.com/article/2077706/core-java/named-parameters-for-preparedstatement.html?page=2</a>
    *
    * @param query
    *   Query containing named parameters
    * @return
    *   ParseResult
    */
  def parse(query: String): DecomposedSql = {
    val fragments = List.newBuilder[Fragment]
    val length = query.length
    val buf = new StringBuilder(query.length)
    var inSingleQuote = false
    var inDoubleQuote = false
    var inSingleLineComment = false
    var inMultiLineComment = false
    var inDoubleСolon = false
    var encounteredSemicolon = false
    var i = 0
    while (i < length && !encounteredSemicolon) {
      var c = query.charAt(i)
      var keepC = true
      if (inSingleQuote) {
        if (c == '\'') {
          inSingleQuote = false;
        }
      } else if (inDoubleQuote) {
        if (c == '"') {
          inDoubleQuote = false;
        }
      } else if (inMultiLineComment) {
        if (c == '*' && query.charAt(i + 1) == '/') {
          inMultiLineComment = false;
        }
      } else if (inDoubleСolon) {
        if (!Character.isJavaIdentifierPart(c)) {
          inDoubleСolon = false;
        }
      } else if (inSingleLineComment) {
        if (c == '\n') {
          inSingleLineComment = false;
        }
      } else {
        if (c == ';') {
          encounteredSemicolon = true
          keepC = false
        } else if (c == '\'') {
          inSingleQuote = true;
        } else if (c == '"') {
          inDoubleQuote = true;
        } else if (c == '/' && query.charAt(i + 1) == '*') {
          inMultiLineComment = true;
        } else if (c == '-' && query.charAt(i + 1) == '-') {
          inSingleLineComment = true;
        } else if (c == ':' && query.charAt(i + 1) == ':') {
          inDoubleСolon = true;
        } else if (c == '?') {
          fragments += SqlText(buf.result())
          buf.clear()
          keepC = false
          fragments += NotNamedParam
        } else if (c == ':' && i + 1 < length && Character.isJavaIdentifierStart(query.charAt(i + 1))) {
          var j = i + 2;
          while (j < length && Character.isJavaIdentifierPart(query.charAt(j))) {
            j += 1;
          }
          val name = query.substring(i + 1, j);
          fragments += SqlText(buf.result())
          buf.clear()
          keepC = false
          fragments += NamedParam(ParsedName.of(name))
          c = '?'; // replace the parameter with a question mark
          i += name.length(); // skip past the end if the parameter
        } else if (c == ':' && i + 1 < length && query.charAt(i + 1) == '"') {
          var j = i + 3;
          while (j < length && query.charAt(j) != '"') {
            j += 1;
          }
          val name = query.substring(i + 2, j);
          fragments += SqlText(buf.result())
          buf.clear()
          keepC = false
          fragments += NamedParam(ParsedName.of(name))
          c = '?'; // replace the parameter with a question mark
          i = j; // skip past the end if the parameter
        }
      }
      if (keepC) buf.append(c)

      i += 1
    }

    buf.result() match {
      case "" =>
      case s  => fragments += SqlText(s)
    }

    DecomposedSql(fragments.result())
  }
}
