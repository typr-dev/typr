package scripts

import bleep.model

object projectsToPublish {
  // will publish these with dependencies
  def include(crossName: model.CrossProjectName): Boolean =
    crossName.name.value match {
      // CLI app
      case "typr"              => true

      // typr's upstream deps — needed so the published POM resolves
      case "typr-codegen"      => true
      case "typr-dsl"          => true
      case "typr-dsl-scala"    => true
      case "typr-dsl-kotlin"   => true

      // legacy DSL integrations (still published for backwards-compat consumers)
      case "typr-dsl-anorm"    => true
      case "typr-dsl-doobie"   => true
      case "typr-dsl-zio-jdbc" => true

      // legacy runtime libs paired with the legacy DSLs
      case "typr-runtime-anorm"    => true
      case "typr-runtime-doobie"   => true
      case "typr-runtime-zio-jdbc" => true

      case _                   => false
    }
}
