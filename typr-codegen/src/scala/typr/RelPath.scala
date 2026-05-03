package typr

import java.nio.file.Path

case class RelPath(segments: List[String]) {
  def asString: String = segments.mkString("/")

  def /(str: String): RelPath = new RelPath(segments :+ str)

  def prefixed(str: String): RelPath =
    RelPath(str :: segments)

  def filter(f: String => Boolean): RelPath =
    RelPath(segments.filter(f))

  def mapSegments(f: List[String] => List[String]): RelPath =
    RelPath(f(segments))

  def startsWith(segments: List[String]): Boolean =
    this.segments.startsWith(segments)

  def withLast(f: String => String): RelPath =
    segments match {
      case init :+ last => new RelPath(init :+ f(last))
      case _            => this
    }

  override def toString: String = asString
}

object RelPath {
  val empty = new RelPath(Nil)

  def force(str: String): RelPath = apply(str) match {
    case Left(errMsg) => sys.error(errMsg)
    case Right(value) => value
  }

  def apply(str: String): Either[String, RelPath] =
    if (str.startsWith("/")) Left(s"$str should be a relative path")
    else Right(RelPath(str.split("/").filterNot(seg => seg.isEmpty || seg == ".").toList))

  def relativeTo(shorter: Path, longer: Path): RelPath =
    force(shorter.relativize(longer).toString)

  implicit val ordering: Ordering[RelPath] =
    Ordering.by(_.segments.mkString(""))

  implicit class PathOps(private val path: Path) extends AnyVal {
    def /(relPath: RelPath): Path =
      relPath.segments.foldLeft(path) {
        case (acc, "..")    => acc.getParent
        case (acc, segment) => acc.resolve(segment)
      }

    def /(str: String): Path =
      RelPath(str) match {
        case Left(err)      => sys.error(err)
        case Right(relPath) => path / relPath
      }
  }

}
