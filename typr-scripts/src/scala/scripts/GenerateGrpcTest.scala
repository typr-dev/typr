package scripts

import typr.grpc.{GrpcCodegen, GrpcOptions, GrpcFrameworkIntegration, ProtoSource}
import typr.effects.EffectType
import typr.internal.FileSync
import typr.jvm
import typr.internal.codegen.{LangJava, LangKotlin, LangScala, TypeSupportKotlin, addPackageAndImports}
import typr.{Dialect, Lang, RelPath, TypeSupportScala}

import java.nio.file.Path

object GenerateGrpcTest {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  def main(args: Array[String]): Unit = {
    val protosPath = buildDir.resolve("testers/grpc/protos")

    println(s"Generating gRPC code from: $protosPath")

    val langScala = LangScala.javaDsl(Dialect.Scala3, TypeSupportScala)
    val langKotlin = LangKotlin(TypeSupportKotlin)

    // Java - Blocking (default)
    generateCode(
      protosPath = protosPath,
      lang = LangJava,
      outputDir = "testers/grpc/java",
      effectType = EffectType.Blocking,
      frameworkIntegration = GrpcFrameworkIntegration.None
    )

    // Scala - Blocking
    generateCode(
      protosPath = protosPath,
      lang = langScala,
      outputDir = "testers/grpc/scala",
      effectType = EffectType.Blocking,
      frameworkIntegration = GrpcFrameworkIntegration.None
    )

    // Kotlin - Blocking
    generateCode(
      protosPath = protosPath,
      lang = langKotlin,
      outputDir = "testers/grpc/kotlin",
      effectType = EffectType.Blocking,
      frameworkIntegration = GrpcFrameworkIntegration.None
    )

    // Java - Spring framework integration
    generateCode(
      protosPath = protosPath,
      lang = LangJava,
      outputDir = "testers/grpc/java-spring",
      effectType = EffectType.Blocking,
      frameworkIntegration = GrpcFrameworkIntegration.Spring
    )

    // Java - Quarkus framework integration
    generateCode(
      protosPath = protosPath,
      lang = LangJava,
      outputDir = "testers/grpc/java-quarkus",
      effectType = EffectType.Blocking,
      frameworkIntegration = GrpcFrameworkIntegration.Quarkus
    )

    // Kotlin - Quarkus + Mutiny Uni
    generateCode(
      protosPath = protosPath,
      lang = langKotlin,
      outputDir = "testers/grpc/kotlin-quarkus",
      effectType = EffectType.MutinyUni,
      frameworkIntegration = GrpcFrameworkIntegration.Quarkus
    )

    println("Done!")
  }

  private def generateCode(
      protosPath: Path,
      lang: Lang,
      outputDir: String,
      effectType: EffectType,
      frameworkIntegration: GrpcFrameworkIntegration
  ): Unit = {
    val projectDir = buildDir.resolve(outputDir)
    val sourceDir = projectDir.resolve("generated-and-checked-in")

    println(s"Output directory: $sourceDir")

    val options = GrpcOptions
      .default(
        pkg = jvm.QIdent(List(jvm.Ident("com"), jvm.Ident("example"), jvm.Ident("grpc"))),
        protoSource = ProtoSource.Directory(protosPath)
      )
      .copy(
        effectType = effectType,
        frameworkIntegration = frameworkIntegration
      )

    val result = GrpcCodegen.generate(options, lang)

    if (result.errors.nonEmpty) {
      println("Errors:")
      result.errors.foreach(e => println(s"  - $e"))
      sys.exit(1)
    }

    if (result.files.isEmpty) {
      println("No files generated!")
      return
    }

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
      val pathParts = file.tpe.value.idents.map(_.value)
      val relativePath = RelPath(pathParts.init :+ s"${pathParts.last}.${lang.extension}")
      val fileWithImports = addPackageAndImports(lang, knownNamesByPkg, file)
      relativePath -> fileWithImports.contents.render(lang).asString
    }.toMap

    // Use FileSync to write files (delete old files to avoid conflicts)
    val synced = FileSync.syncStrings(
      folder = sourceDir,
      fileRelMap = fileMap,
      deleteUnknowns = FileSync.DeleteUnknowns.Yes(maxDepth = None),
      softWrite = FileSync.SoftWrite.Yes(Set.empty)
    )

    val changed = synced.filter { case (_, status) => status != FileSync.Synced.Unchanged }
    println(s"Generated ${result.files.size} files (${changed.size} changed):")
    changed.foreach { case (path, status) =>
      println(s"  - $status: ${sourceDir.relativize(path)}")
    }
  }
}
