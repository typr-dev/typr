package scripts

import typr.openapi.{OpenApiClientLib, OpenApiCodegen, OpenApiEffectType, OpenApiOptions, OpenApiServerLib}
import typr.internal.FileSync
import typr.jvm
import typr.internal.codegen.{LangJava, LangKotlin, LangScala, TypeSupportKotlin, addPackageAndImports}
import typr.{Dialect, Lang, RelPath, TypeSupportJava, TypeSupportScala}

import java.nio.file.Path

object GenerateOpenApiTest {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  def main(args: Array[String]): Unit = {
    val specPath = buildDir.resolve("typr/src/scala/typr/openapi/testdata/test-features.yaml")

    println(s"Generating code from: $specPath")

    // Java with JAX-RS server + JDK HTTP Client (blocking)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = Some(OpenApiServerLib.JaxRsSync),
      clientLib = Some(OpenApiClientLib.JdkHttpClient(OpenApiEffectType.Blocking)),
      lang = LangJava,
      generateValidation = true
    )

    // Java with Spring server + JDK HTTP Client (blocking)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = Some(OpenApiServerLib.SpringMvc),
      clientLib = Some(OpenApiClientLib.JdkHttpClient(OpenApiEffectType.Blocking)),
      lang = LangJava,
      generateValidation = true
    )

    // Java with Quarkus server + JDK HTTP Client (Mutiny Uni - async)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = Some(OpenApiServerLib.QuarkusReactive),
      clientLib = Some(OpenApiClientLib.JdkHttpClient(OpenApiEffectType.MutinyUni)),
      lang = LangJava,
      generateValidation = true
    )

    val langScala = LangScala.javaDsl(Dialect.Scala3, TypeSupportScala)
    // For Spring, use Java types (Optional, List, etc.) since Spring understands Java types natively
    val langScalaWithJavaTypes = LangScala.javaDsl(Dialect.Scala3, TypeSupportJava)

    // Scala with HTTP4s server + client (uses Circe for JSON)
    generateCode(
      specPath = specPath,
      language = "scala",
      serverLib = Some(OpenApiServerLib.Http4s),
      clientLib = Some(OpenApiClientLib.Http4s),
      lang = langScala,
      generateValidation = false,
      jsonLib = typr.openapi.OpenApiJsonLib.Circe
    )

    // Scala with Spring server + JDK HTTP Client (blocking, uses Jackson for JSON)
    // Uses Java types so Spring can handle Optional<Integer> etc. correctly
    generateCode(
      specPath = specPath,
      language = "scala",
      serverLib = Some(OpenApiServerLib.SpringMvc),
      clientLib = Some(OpenApiClientLib.JdkHttpClient(OpenApiEffectType.Blocking)),
      lang = langScalaWithJavaTypes,
      generateValidation = true,
      jsonLib = typr.openapi.OpenApiJsonLib.Jackson
    )

    // Kotlin with JAX-RS server + JDK HTTP Client (blocking)
    generateCode(
      specPath = specPath,
      language = "kotlin",
      serverLib = Some(OpenApiServerLib.JaxRsSync),
      clientLib = Some(OpenApiClientLib.JdkHttpClient(OpenApiEffectType.Blocking)),
      lang = LangKotlin(TypeSupportKotlin),
      generateValidation = true
    )

    // Kotlin with Spring server + JDK HTTP Client (blocking)
    generateCode(
      specPath = specPath,
      language = "kotlin",
      serverLib = Some(OpenApiServerLib.SpringMvc),
      clientLib = Some(OpenApiClientLib.JdkHttpClient(OpenApiEffectType.Blocking)),
      lang = LangKotlin(TypeSupportKotlin),
      generateValidation = true
    )

    // Kotlin with Quarkus server + JDK HTTP Client (Mutiny Uni - async)
    generateCode(
      specPath = specPath,
      language = "kotlin",
      serverLib = Some(OpenApiServerLib.QuarkusReactive),
      clientLib = Some(OpenApiClientLib.JdkHttpClient(OpenApiEffectType.MutinyUni)),
      lang = LangKotlin(TypeSupportKotlin),
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
        case OpenApiClientLib.JdkHttpClient(_)   => "jdk"
        case OpenApiClientLib.SpringWebClient    => "spring-webclient"
        case OpenApiClientLib.SpringRestTemplate => "spring-rest"
        case OpenApiClientLib.Http4s             => "http4s"
        case OpenApiClientLib.Sttp               => "sttp"
        case OpenApiClientLib.ZioHttp            => "zio-http"
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
      generateValidation: Boolean,
      jsonLib: typr.openapi.OpenApiJsonLib = typr.openapi.OpenApiJsonLib.Jackson
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
        useGenericResponseTypes = true,
        jsonLib = jsonLib
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
