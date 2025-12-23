package scripts

import typr.openapi.{OpenApiCodegen, OpenApiOptions, OpenApiServerLib}
import typr.internal.FileSync
import typr.jvm
import typr.internal.codegen.{LangJava, LangKotlin, TypeSupportKotlin, addPackageAndImports}
import typr.{Lang, RelPath}

import java.nio.file.Path

object GenerateStripeTest {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  def main(args: Array[String]): Unit = {
    val specPath = buildDir.resolve("stripe-spec.json")

    println(s"Generating code from: $specPath")

    // Java with JAX-RS server only (blocking) - testing with real-world Stripe API
    generateStripe(
      specPath = specPath,
      projectName = "openapi-test-stripe-java",
      lang = LangJava
    )

    // Kotlin with JAX-RS server only (blocking) - testing with real-world Stripe API
    generateStripe(
      specPath = specPath,
      projectName = "openapi-test-stripe-kotlin",
      lang = LangKotlin(TypeSupportKotlin)
    )

    println("Done!")
  }

  private def generateStripe(specPath: Path, projectName: String, lang: Lang): Unit = {
    val projectDir = buildDir.resolve(projectName)
    val sourceDir = projectDir.resolve("stripe")

    println(s"Output directory: $sourceDir (${lang.extension})")

    val options = OpenApiOptions
      .default(jvm.QIdent(List(jvm.Ident("stripe"))))
      .copy(
        serverLib = Some(OpenApiServerLib.JaxRsSync),
        clientLib = None,
        generateValidation = false, // Start without validation to reduce complexity
        useGenericResponseTypes = true // Use generic response types to deduplicate
      )

    val result = OpenApiCodegen.generate(specPath, options, lang)

    if (result.errors.nonEmpty) {
      println(s"Errors (${result.errors.size}):")
      result.errors.take(20).foreach(e => println(s"  - $e"))
      if (result.errors.size > 20) {
        println(s"  ... and ${result.errors.size - 20} more errors")
      }
    }

    println(s"Generated ${result.files.size} files")

    // Build known names by package for import resolution
    val knownNamesByPkg: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]] = result.files
      .groupBy(_.pkg)
      .map { case (pkg, files) =>
        pkg -> files.flatMap { f =>
          f.secondaryTypes.map(st => st.value.name -> st) :+ (f.tpe.value.name -> f.tpe)
        }.toMap
      }

    // Convert files to RelPath -> String map for FileSync
    val fileMap: Map[RelPath, String] = result.files.map { file =>
      // Remove the "stripe" prefix from the path since we're already in that directory
      val pathParts = file.tpe.value.idents.drop(1).map(_.value)
      val relativePath = RelPath(pathParts.init :+ s"${pathParts.last}.${lang.extension}")
      val fileWithImports = addPackageAndImports(lang, knownNamesByPkg, file)
      relativePath -> fileWithImports.contents.render(lang).asString
    }.toMap

    // Use FileSync to write files - this only touches the sourceDir, not project root
    val synced = FileSync.syncStrings(
      folder = sourceDir,
      fileRelMap = fileMap,
      deleteUnknowns = FileSync.DeleteUnknowns.Yes(maxDepth = None),
      softWrite = FileSync.SoftWrite.Yes(Set.empty)
    )

    val changed = synced.filter { case (_, status) => status != FileSync.Synced.Unchanged }
    println(s"Synced ${result.files.size} files (${changed.size} changed)")
  }
}
