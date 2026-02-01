package typr.cli.tui

import typr.TypoLogger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

sealed trait LogLevel
object LogLevel {
  case object Info extends LogLevel
  case object Warning extends LogLevel
  case object Error extends LogLevel
}

case class LogMessage(
    level: LogLevel,
    source: Option[String],
    phase: String,
    message: String,
    timestamp: Long
)

class TuiLogger extends TypoLogger {
  private val messages = new ConcurrentLinkedQueue[LogMessage]()
  private val warningsBySource = new ConcurrentHashMap[String, ConcurrentLinkedQueue[String]]()
  @volatile private var currentSource: Option[String] = None
  @volatile private var currentPhase: String = "init"

  def setContext(source: Option[String], phase: String): Unit = {
    currentSource = source
    currentPhase = phase
  }

  override def info(msg: String): Unit = {
    messages.add(LogMessage(LogLevel.Info, currentSource, currentPhase, msg, System.currentTimeMillis()))
  }

  override def warn(msg: String): Unit = {
    messages.add(LogMessage(LogLevel.Warning, currentSource, currentPhase, msg, System.currentTimeMillis()))
    currentSource.foreach { src =>
      warningsBySource.computeIfAbsent(src, _ => new ConcurrentLinkedQueue[String]()).add(msg)
    }
  }

  def error(msg: String): Unit = {
    messages.add(LogMessage(LogLevel.Error, currentSource, currentPhase, msg, System.currentTimeMillis()))
  }

  def getRecentMessages(limit: Int): List[LogMessage] = {
    val all = messages.asScala.toList
    all.takeRight(limit)
  }

  def getWarningsForSource(source: String): List[String] = {
    Option(warningsBySource.get(source)).map(_.asScala.toList).getOrElse(Nil)
  }

  def getLatestWarningForSource(source: String): Option[String] = {
    Option(warningsBySource.get(source)).flatMap { queue =>
      val iter = queue.iterator()
      var last: String = null
      while (iter.hasNext) last = iter.next()
      Option(last)
    }
  }

  def getWarningCount(source: String): Int = {
    Option(warningsBySource.get(source)).map(_.size()).getOrElse(0)
  }

  def getAllWarningCounts: Map[String, Int] = {
    warningsBySource.asScala.view.mapValues(_.size()).toMap
  }

  def getLatestMessage: Option[LogMessage] = {
    Option(messages.peek()).orElse {
      val iter = messages.iterator()
      var last: LogMessage = null
      while (iter.hasNext) last = iter.next()
      Option(last)
    }
  }

  def getRecentWarnings(limit: Int): List[LogMessage] = {
    val all = messages.asScala.toList.filter(_.level == LogLevel.Warning)
    all.takeRight(limit)
  }

  def getTotalWarningCount: Int = {
    messages.asScala.count(_.level == LogLevel.Warning)
  }

  def clear(): Unit = {
    messages.clear()
    warningsBySource.clear()
  }
}

object TuiLogger {
  def apply(): TuiLogger = new TuiLogger()
}
