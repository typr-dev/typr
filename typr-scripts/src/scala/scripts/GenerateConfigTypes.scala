package scripts

import typr.jsonschema.{JsonSchemaCodegen, JsonSchemaOptions}
import typr.internal.FileSync
import typr.jvm
import typr.internal.codegen.{LangScala, addPackageAndImports}
import typr.{Dialect, RelPath, TypeSupportScala}
import typr.openapi.OpenApiJsonLib

import java.nio.file.Path

object GenerateConfigTypes {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  def main(args: Array[String]): Unit = {
    val schemaPath = buildDir.resolve("typr-config.schema.json")
    val outputDir = buildDir.resolve("typr-codegen/generated-and-checked-in-jsonschema")

    println(s"Generating config types from: $schemaPath")
    println(s"Output directory: $outputDir")

    val pkg = jvm.QIdent(List(jvm.Ident("typr"), jvm.Ident("config"), jvm.Ident("generated")))

    val options = JsonSchemaOptions(
      pkg = pkg,
      jsonLib = OpenApiJsonLib.Circe,
      generateWrapperTypes = false,
      typeOverrides = Map.empty,
      useOptionalForNullable = true,
      generateValidation = false
    )

    val lang = LangScala.javaDsl(Dialect.Scala3, TypeSupportScala)

    val result = JsonSchemaCodegen.generate(schemaPath, options, lang)

    if (result.errors.nonEmpty) {
      println("Errors:")
      result.errors.foreach(e => println(s"  - $e"))
      sys.exit(1)
    }

    val knownNamesByPkg: Map[jvm.QIdent, Map[jvm.Ident, jvm.Type.Qualified]] = result.files
      .groupBy(_.pkg)
      .map { case (pkg, files) =>
        pkg -> files.flatMap { f =>
          f.secondaryTypes.map(st => st.value.name -> st) :+ (f.tpe.value.name -> f.tpe)
        }.toMap
      }

    val fileMap: Map[RelPath, String] = result.files.map { file =>
      val pathParts = file.tpe.value.idents.drop(1).map(_.value)
      val relativePath = RelPath(pathParts.init :+ s"${pathParts.last}.${lang.extension}")
      val fileWithImports = addPackageAndImports(lang, knownNamesByPkg, file)
      relativePath -> fileWithImports.contents.render(lang).asString
    }.toMap

    val synced = FileSync.syncStrings(
      folder = outputDir,
      fileRelMap = fileMap,
      deleteUnknowns = FileSync.DeleteUnknowns.Yes(None),
      softWrite = FileSync.SoftWrite.Yes(Set.empty)
    )

    val changed = synced.filter { case (_, status) => status != FileSync.Synced.Unchanged }
    println(s"Generated ${result.files.size} files (${changed.size} changed):")
    changed.foreach { case (path, status) =>
      println(s"  - $status: ${outputDir.relativize(path)}")
    }
  }
}
