package typr

import scala.concurrent.{ExecutionContext, Future}

trait TypoLogger {
  def warn(str: String): Unit
  def info(str: String): Unit

  final def timed[T](name: String)(f: Future[T])(implicit ec: ExecutionContext): Future[T] =
    for {
      start <- Future.successful(System.currentTimeMillis())
      res <- f
      _ <- Future.successful(info(s"finished $name in ${System.currentTimeMillis() - start}ms"))
    } yield res
}

object TypoLogger {
  object Console extends TypoLogger {
    override def warn(str: String): Unit = System.err.println(s"typo: $str")
    override def info(str: String): Unit = System.out.println(s"typo: $str")
  }

  object Noop extends TypoLogger {
    override def warn(str: String): Unit = ()
    override def info(str: String): Unit = ()
  }
}
