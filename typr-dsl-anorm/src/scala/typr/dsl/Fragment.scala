package typr.dsl

import anorm.NamedParameter

case class Fragment(params: Seq[NamedParameter], sql: String) {
  def ++(other: Fragment): Fragment =
    Fragment(params ++ other.params, sql + other.sql)
  def stripMargin: Fragment =
    Fragment(params, sql.stripMargin)
  def indent(n: Int): Fragment =
    Fragment(params, sql.linesIterator.map(str => (" " * n) + str).mkString("\n"))
}

object Fragment {
  def apply(sql: String): Fragment = Fragment(Nil, sql)

  val empty: Fragment = Fragment("")

  implicit class FragmentListSyntax(private val fs: Seq[Fragment]) extends AnyVal {
    def mkFragment(sep: String): Fragment = {
      val sb = new StringBuilder
      val params = fs.flatMap(_.params)
      fs.zipWithIndex.foreach { case (f, i) =>
        if (i != 0) sb ++= sep
        sb ++= f.sql
      }
      Fragment(params, sb.result())
    }
  }

  implicit class FragmentStringInterpolator(val sc: StringContext) extends AnyVal {
    def frag(frags: Fragment*): Fragment = {
      val sb = new StringBuilder
      val params = List.newBuilder[NamedParameter]
      val partsIt = sc.parts.iterator
      val fragsIt = frags.iterator
      sb.append(partsIt.next())
      while (partsIt.hasNext) {
        val fragment = fragsIt.next()
        params ++= fragment.params
        sb.append(fragment.sql)
        sb.append(partsIt.next())
      }
      Fragment(params.result(), sb.toString)
    }
  }
}
