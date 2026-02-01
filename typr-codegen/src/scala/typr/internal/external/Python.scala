package typr
package internal
package external

import java.nio.file.{Files, Path}

/** Represents a downloaded Python installation */
case class Python(binary: Path) {
  require(Files.exists(binary), s"Python binary does not exist: $binary")
}

object Python {

  /** Fetch Python from python-build-standalone releases */
  def fetch(coursier: TypoCoursier, version: String, standaloneBuildDate: String): Either[String, Python] = {
    urlFor(coursier.osArch, version, standaloneBuildDate) match {
      case Left(err) =>
        Left(err)
      case Right(url) =>
        coursier.fetchArtifact(url) match {
          case Left(err) =>
            Left(s"Failed to download Python: $err")
          case Right(folder) =>
            val binRelPath = binaryRelPathFor(coursier.osArch)
            val binPath = folder.toPath.resolve(binRelPath)
            if (!Files.exists(binPath)) {
              Left(s"Expected $folder to contain a python binary at $binRelPath")
            } else {
              Right(Python(binPath))
            }
        }
    }
  }

  /** Compute a URL for the python-build-standalone release */
  def urlFor(osArch: OsArch, version: String, standaloneBuildDate: String): Either[String, String] = {
    val versionDate = s"$version+$standaloneBuildDate"
    val prefix = s"https://github.com/indygreg/python-build-standalone/releases/download/$standaloneBuildDate"
    osArch match {
      case OsArch.LinuxAmd64 =>
        Right(s"$prefix/cpython-$versionDate-x86_64-unknown-linux-gnu-install_only_stripped.tar.gz")
      case OsArch.WindowsAmd64 =>
        Right(s"$prefix/cpython-$versionDate-x86_64-pc-windows-msvc-install_only_stripped.tar.gz")
      case OsArch.MacosAmd64 =>
        Right(s"$prefix/cpython-$versionDate-x86_64-apple-darwin-install_only_stripped.tar.gz")
      case _: OsArch.MacosArm64 =>
        Right(s"$prefix/cpython-$versionDate-aarch64-apple-darwin-install_only_stripped.tar.gz")
      case OsArch.Other(os, arch) =>
        Left(s"Python is not available for OS ${os.value} and arch $arch")
    }
  }

  def binaryRelPathFor(osArch: OsArch): String =
    osArch match {
      case OsArch.WindowsAmd64 => "python/python.exe"
      case _                   => "python/bin/python3"
    }
}
