package scripts

import typo.openapi.{OpenApiCodegen, OpenApiClientLib, OpenApiOptions, OpenApiServerLib}
import typo.internal.FileSync
import typo.jvm
import typo.internal.codegen.{addPackageAndImports, LangJava, LangKotlin, LangScala}
import typo.{Dialect, Lang, RelPath, TypeSupportScala}

import java.nio.file.Path

object GenerateOpenApiTest {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  def main(args: Array[String]): Unit = {
    val specPath = buildDir.resolve("typo/src/scala/typo/openapi/testdata/test-features.yaml")

    println(s"Generating code from: $specPath")

    // Java with JAX-RS server + MicroProfile client (blocking)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = Some(OpenApiServerLib.JaxRsSync),
      clientLib = Some(OpenApiClientLib.MicroProfileBlocking),
      lang = LangJava,
      generateValidation = true
    )

    // Java with Spring server + MicroProfile client (blocking)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = Some(OpenApiServerLib.SpringMvc),
      clientLib = Some(OpenApiClientLib.MicroProfileBlocking),
      lang = LangJava,
      generateValidation = true
    )

    // Java with Quarkus server + MicroProfile client (reactive)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = Some(OpenApiServerLib.QuarkusReactive),
      clientLib = Some(OpenApiClientLib.MicroProfileReactive),
      lang = LangJava,
      generateValidation = true
    )

    // Java with MicroProfile client only (blocking)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = None,
      clientLib = Some(OpenApiClientLib.MicroProfileBlocking),
      lang = LangJava,
      generateValidation = true
    )

    // Scala base only (no server or client)
    val langScala = LangScala(Dialect.Scala3, TypeSupportScala)
    generateCode(
      specPath = specPath,
      language = "scala",
      serverLib = None,
      clientLib = None,
      lang = langScala,
      generateValidation = false
    )

    // Scala with HTTP4s server + client
    generateCode(
      specPath = specPath,
      language = "scala",
      serverLib = Some(OpenApiServerLib.Http4s),
      clientLib = Some(OpenApiClientLib.Http4s),
      lang = langScala,
      generateValidation = false
    )

    // Kotlin with JAX-RS server + MicroProfile client (blocking)
    generateCode(
      specPath = specPath,
      language = "kotlin",
      serverLib = Some(OpenApiServerLib.JaxRsSync),
      clientLib = Some(OpenApiClientLib.MicroProfileBlocking),
      lang = LangKotlin,
      generateValidation = true
    )

    // Kotlin with Spring server + MicroProfile client (blocking)
    generateCode(
      specPath = specPath,
      language = "kotlin",
      serverLib = Some(OpenApiServerLib.SpringMvc),
      clientLib = Some(OpenApiClientLib.MicroProfileBlocking),
      lang = LangKotlin,
      generateValidation = true
    )

    // Kotlin with Quarkus server + MicroProfile client (reactive)
    generateCode(
      specPath = specPath,
      language = "kotlin",
      serverLib = Some(OpenApiServerLib.QuarkusReactive),
      clientLib = Some(OpenApiClientLib.MicroProfileReactive),
      lang = LangKotlin,
      generateValidation = true
    )

    println("Done!")
  }

  /** Build output directory name based on language and optional server/client */
  private def buildOutputDirName(language: String, serverLib: Option[OpenApiServerLib], clientLib: Option[OpenApiClientLib]): String = {
    val parts = List.newBuilder[String]
    parts += "openapi-test"
    parts += language

    serverLib.foreach { server =>
      val name = server match {
        case OpenApiServerLib.QuarkusReactive => "quarkus-reactive"
        case OpenApiServerLib.QuarkusBlocking => "quarkus"
        case OpenApiServerLib.SpringWebFlux   => "spring-webflux"
        case OpenApiServerLib.SpringMvc       => "spring"
        case OpenApiServerLib.JaxRsAsync      => "jaxrs-async"
        case OpenApiServerLib.JaxRsSync       => "jaxrs"
        case OpenApiServerLib.Http4s          => "http4s"
        case OpenApiServerLib.ZioHttp         => "zio-http"
      }
      parts += s"server-$name"
    }

    clientLib.foreach { client =>
      val name = client match {
        case OpenApiClientLib.MicroProfileReactive => "mp-reactive"
        case OpenApiClientLib.MicroProfileBlocking => "mp"
        case OpenApiClientLib.SpringWebClient      => "spring-webclient"
        case OpenApiClientLib.SpringRestTemplate   => "spring-rest"
        case OpenApiClientLib.VertxMutiny          => "vertx"
        case OpenApiClientLib.Http4s               => "http4s"
        case OpenApiClientLib.Sttp                 => "sttp"
        case OpenApiClientLib.ZioHttp              => "zio-http"
      }
      parts += s"client-$name"
    }

    parts.result().mkString("-")
  }

  private def generateCode(
      specPath: Path,
      language: String,
      serverLib: Option[OpenApiServerLib],
      clientLib: Option[OpenApiClientLib],
      lang: Lang,
      generateValidation: Boolean
  ): Unit = {
    val outputDirName = buildOutputDirName(language, serverLib, clientLib)
    val projectDir = buildDir.resolve(outputDirName)
    // Use the package name as source directory - FileSync will manage only this folder
    val sourceDir = projectDir.resolve("testapi")

    println(s"Output directory: $sourceDir")

    val options = OpenApiOptions
      .default(jvm.QIdent(List(jvm.Ident("testapi"))))
      .copy(
        serverLib = serverLib,
        clientLib = clientLib,
        generateValidation = generateValidation,
        useGenericResponseTypes = true
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

    // Convert files to RelPath -> String map for FileSync
    val fileMap: Map[RelPath, String] = result.files.map { file =>
      // Remove the "testapi" prefix from the path since we're already in that directory
      val pathParts = file.tpe.value.idents.drop(1).map(_.value)
      val relativePath = RelPath(pathParts.init :+ s"${pathParts.last}.${lang.extension}")
      val fileWithImports = addPackageAndImports(lang, knownNamesByPkg, file)
      relativePath -> fileWithImports.contents.render(lang).asString
    }.toMap

    // Use FileSync to write files - this only touches the sourceDir, not project root
    // Preserve test files by excluding them from deletion
    val synced = FileSync.syncStrings(
      folder = sourceDir,
      fileRelMap = fileMap,
      deleteUnknowns = FileSync.DeleteUnknowns.No,
      softWrite = FileSync.SoftWrite.Yes(Set.empty)
    )

    val changed = synced.filter { case (_, status) => status != FileSync.Synced.Unchanged }
    println(s"Generated ${result.files.size} files (${changed.size} changed):")
    changed.foreach { case (path, status) =>
      println(s"  - $status: ${sourceDir.relativize(path)}")
    }
  }
}
