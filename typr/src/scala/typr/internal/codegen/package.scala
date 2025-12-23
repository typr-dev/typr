package typr
package internal

package object codegen {
  implicit class ToCodeOps[T](private val t: T) extends AnyVal {
    def code(implicit toCode: ToCode[T]): jvm.Code = toCode.toCode(t)
  }

  implicit final class CodeInterpolator(private val stringContext: StringContext) extends AnyVal {
    def code(args: jvm.Code*): jvm.Code = {
      jvm.Code.Interpolated(stringContext.parts, args)
    }
  }

  /** Mark code for runtime interpolation in string interpolators.
    *
    * When used inside `lang.s(code"...")`, this marks a value that should be evaluated at runtime rather than embedded as a literal string.
    *
    * For example, instead of:
    * {{{
    *   lang.s(code"value is $${expr}")  // WRONG: produces literal "${expr}"
    * }}}
    *
    * Use:
    * {{{
    *   lang.s(code"value is ${rt(expr)}")  // CORRECT: produces "value is " + expr
    * }}}
    */
  def rt(c: jvm.Code): jvm.Code = jvm.Code.Tree(jvm.RuntimeInterpolation(c))

  // magnet pattern
  implicit def toCode[T: ToCode](x: T): jvm.Code = ToCode[T].toCode(x)

  implicit class CodeOps[C <: jvm.Code](private val codes: List[C]) extends AnyVal {
    def mkCode(sep: jvm.Code): jvm.Code = {
      val interspersed = codes.zipWithIndex.map {
        case (c, 0) => c
        case (c, _) => jvm.Code.Combined(List(sep, c))
      }
      jvm.Code.Combined(interspersed)
    }
  }

  implicit class CodeOpsNel[C <: jvm.Code](private val codes: NonEmptyList[C]) extends AnyVal {
    def mkCode(sep: jvm.Code): jvm.Code = {
      val interspersed = codes.toList.zipWithIndex.map {
        case (c, 0) => c
        case (c, _) => jvm.Code.Combined(List(sep, c))
      }
      jvm.Code.Combined(interspersed)
    }
  }

  def scaladoc(lines: List[String]): jvm.Comments =
    lines match {
      case Nil      => jvm.Comments.Empty
      case nonEmpty => jvm.Comments(nonEmpty)
    }
}
