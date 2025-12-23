package scripts

import bleep.*
import bleep.commands.PublishLocal.{LocalIvy, Options}
import bleep.packaging.ManifestCreator
import bleep.plugin.dynver.DynVerPlugin

object PublishLocal extends BleepScript("PublishLocal") {
  def run(started: Started, commands: Commands, args: List[String]): Unit = {
    val dynVer = new DynVerPlugin(baseDirectory = started.buildPaths.buildDir.toFile, dynverSonatypeSnapshots = true)
    val projects = started.build.explodedProjects.keys.toArray.filter(projectsToPublish.include)

    commands.publishLocal(
      Options(
        groupId = "dev.typr",
        version = dynVer.version,
        publishTarget = LocalIvy,
        projects = projects,
        manifestCreator = ManifestCreator.default
      )
    )
  }
}
