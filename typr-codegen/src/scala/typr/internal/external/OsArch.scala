package typr
package internal
package external

import java.util.Locale

sealed abstract class Os(val value: String)

object Os {
  case object Windows extends Os("windows")
  case object Linux extends Os("linux")
  case object Macos extends Os("macos")

  val byName: Map[String, Os] =
    List(Windows, Linux, Macos).map(os => os.value -> os).toMap

  def from(str: String): Either[String, Os] =
    byName.get(str).toRight(s"Not among ${byName.keys.mkString(", ")}: $str")
}

sealed trait Arch

object Arch {
  case object Amd64 extends Arch
  case object Arm64 extends Arch
}

sealed trait OsArch {
  val os: Os
  val arch: Arch
}

object OsArch {
  case object LinuxAmd64 extends OsArch { val os: Os = Os.Linux; val arch: Arch = Arch.Amd64 }
  case object WindowsAmd64 extends OsArch { val os: Os = Os.Windows; val arch: Arch = Arch.Amd64 }
  case object MacosAmd64 extends OsArch { val os: Os = Os.Macos; val arch: Arch = Arch.Amd64 }
  case class MacosArm64(freedFromJail: Boolean) extends OsArch { val os: Os = Os.Macos; val arch: Arch = Arch.Arm64 }
  case class Other(os: Os, arch: Arch) extends OsArch

  def shouldBeArm64(): Boolean = {
    if (sys.env.contains("TYPO_ALLOW_AMD64")) false
    else {
      import scala.sys.process._
      scala.util.Try(Seq("uname", "-a").!!.toLowerCase.contains("arm64")).getOrElse(false)
    }
  }

  lazy val current: OsArch = {
    val os: Os =
      Option(System.getProperty("os.name")).getOrElse("").toLowerCase(Locale.ROOT) match {
        case s if s.contains("windows") => Os.Windows
        case s if s.contains("linux")   => Os.Linux
        case s if s.contains("mac")     => Os.Macos
        case unrecognized               => throw new RuntimeException(s"OS $unrecognized not supported yet. PR welcome! :)")
      }

    val arch =
      Option(System.getProperty("os.arch")).getOrElse("").toLowerCase(Locale.ROOT) match {
        case "x86_64" | "amd64" => Arch.Amd64
        case "aarch64"          => Arch.Arm64
        case unrecognized       => throw new RuntimeException(s"Arch $unrecognized not supported yet. PR welcome! :)")
      }

    (os, arch) match {
      case (Os.Windows, Arch.Amd64) => WindowsAmd64
      case (Os.Linux, Arch.Amd64)   => LinuxAmd64
      case (Os.Macos, Arch.Arm64)   => MacosArm64(freedFromJail = false)
      case (Os.Macos, Arch.Amd64)   => if (shouldBeArm64()) MacosArm64(freedFromJail = true) else MacosAmd64
      case (os, arch)               => Other(os, arch)
    }
  }
}
