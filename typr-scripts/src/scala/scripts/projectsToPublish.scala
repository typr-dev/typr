package scripts

import bleep.model

object projectsToPublish {
  // will publish these with dependencies
  def include(crossName: model.CrossProjectName): Boolean =
    crossName.name.value match {
      case "typr"              => true
      case "typr-dsl-anorm"    => true
      case "typr-dsl-doobie"   => true
      case "typr-dsl-zio-jdbc" => true
      case _                   => false
    }
}
