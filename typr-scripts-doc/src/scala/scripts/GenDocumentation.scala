package scripts

import bleep.*
import bleep.plugin.dynver.DynVerPlugin
import bleep.plugin.mdoc.{DocusaurusPlugin, MdocPlugin}

import java.io.File
import java.nio.file.Path

object GenDocumentation extends BleepScript("GenDocumentation") {
  override def run(started: Started, commands: Commands, args: List[String]): Unit = {
    val scriptsProject = model.CrossProjectName(model.ProjectName("typr-scripts-doc"), crossId = None)
    commands.compile(List(scriptsProject))

    val dynVer = new DynVerPlugin(baseDirectory = started.buildPaths.buildDir.toFile, dynverSonatypeSnapshots = true)

    val mdoc = new MdocPlugin(started, scriptsProject) {
      override def mdocIn: Path = started.buildPaths.buildDir / "site-in"
      override def mdocOut: Path = started.buildPaths.buildDir / "site" / "docs"

      override def mdocVariables: Map[String, String] = Map("VERSION" -> dynVer.dynverGitDescribeOutput.get.previousVersion)
    }

    val nodeBinPath = started.pre.fetchNode("20.5.0").getParent

    started.logger.withContext("nodeBinPath", nodeBinPath).info("Using node")

    val env = sys.env.collect {
      case x @ ("SSH_AUTH_SOCK", _) => x
      case ("PATH", value)          => "PATH" -> s"$nodeBinPath${File.pathSeparator}$value"
    }.toList

    val docusaurus = new DocusaurusPlugin(
      website = started.buildPaths.buildDir / "site",
      mdoc = mdoc,
      docusaurusProjectName = "site",
      env = env,
      logger = started.logger,
      isDocusaurus2 = true
    )

    args.headOption match {
      case Some("mdoc") =>
        mdoc.mdoc(args = Nil)
      case Some("dev") =>
        docusaurus.dev(using started.executionContext)
      case Some("deploy") =>
        docusaurus.docusaurusPublishGhpages(mdocArgs = Nil)
      case Some("surge-deploy") =>
        val siteName = "typr-docs-preview"
        val siteDir = started.buildPaths.buildDir / "site"
        val buildDir = siteDir / "build"

        mdoc.mdoc(args = Nil)

        cli(
          action = "npm install",
          cwd = siteDir,
          cmd = List("npm", "install"),
          logger = started.logger,
          out = cli.Out.ViaLogger(started.logger),
          env = env
        )

        cli(
          action = "npm run build",
          cwd = siteDir,
          cmd = List("npm", "run", "build"),
          logger = started.logger,
          out = cli.Out.ViaLogger(started.logger),
          env = env
        )

        started.logger.info(s"Built documentation, deploying to surge...")
        cli(
          action = "surge deploy",
          cwd = buildDir,
          cmd = List("npx", "surge", ".", s"$siteName.surge.sh"),
          logger = started.logger,
          out = cli.Out.ViaLogger(started.logger),
          env = env
        )
        started.logger.info(s"Deployed to https://$siteName.surge.sh")
      case Some(other) =>
        sys.error(s"Expected argument to be mdoc, dev, deploy or surge-deploy, not $other")
      case None =>
        val path = docusaurus.doc(mdocArgs = args)
        started.logger.info(s"Created documentation at $path")
    }
  }
}
