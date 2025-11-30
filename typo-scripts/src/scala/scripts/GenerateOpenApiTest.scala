package scripts

import typo.openapi.{OpenApiCodegen, OpenApiOptions}
import typo.jvm
import typo.internal.codegen.{addPackageAndImports, LangJava, LangScala}
import typo.{Dialect, Lang, TypeSupportScala}

import java.nio.file.{Files, Path}

object GenerateOpenApiTest {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  def main(args: Array[String]): Unit = {
    val specPath = buildDir.resolve("typo/src/scala/typo/openapi/testdata/test-features.yaml")
    val javaOutputDir = buildDir.resolve("openapi-test-output")
    val scalaOutputDir = buildDir.resolve("openapi-test-output-scala")

    println(s"Generating code from: $specPath")

    // Generate Java code
    generateCode(specPath, javaOutputDir, LangJava, ".java", generateValidation = true)

    // Generate Scala code
    val langScala = LangScala(Dialect.Scala3, TypeSupportScala)
    generateCode(specPath, scalaOutputDir, langScala, ".scala", generateValidation = false)

    println("Done!")
  }

  private def generateCode(
      specPath: Path,
      outputDir: Path,
      lang: Lang,
      extension: String,
      generateValidation: Boolean
  ): Unit = {
    println(s"Output directory: $outputDir")

    // Clean output directory
    if (Files.exists(outputDir)) {
      Files.walk(outputDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
    }
    Files.createDirectories(outputDir)

    val options = OpenApiOptions
      .default(jvm.QIdent(List(jvm.Ident("testapi"))))
      .copy(
        generateValidation = generateValidation
      )

    val result = OpenApiCodegen.generate(specPath, options, lang)

    if (result.errors.nonEmpty) {
      println("Errors:")
      result.errors.foreach(e => println(s"  - $e"))
      sys.exit(1)
    }

    // Build known names by package for import resolution
    val knownNamesByPkg: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]] = result.files
      .groupBy(_.pkg)
      .map { case (pkg, files) =>
        pkg -> files.flatMap { f =>
          f.secondaryTypes.map(st => st.value.name -> st) :+ (f.tpe.value.name -> f.tpe)
        }.toMap
      }

    println(s"Generated ${result.files.size} files:")
    result.files.foreach { file =>
      val relativePath = file.tpe.value.idents.map(_.value).mkString("/") + extension
      val fullPath = outputDir.resolve(relativePath)
      Files.createDirectories(fullPath.getParent)
      val fileWithImports = addPackageAndImports(lang, knownNamesByPkg, file)
      Files.writeString(fullPath, fileWithImports.contents.render(lang).asString)
      println(s"  - $relativePath")
    }
  }
}
