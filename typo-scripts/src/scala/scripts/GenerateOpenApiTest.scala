package scripts

import typo.openapi.{OpenApiCodegen, OpenApiClientLib, OpenApiOptions, OpenApiServerLib}
import typo.jvm
import typo.internal.codegen.{addPackageAndImports, LangJava, LangScala}
import typo.{Dialect, Lang, TypeSupportScala}

import java.nio.file.{Files, Path}

object GenerateOpenApiTest {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  def main(args: Array[String]): Unit = {
    val specPath = buildDir.resolve("typo/src/scala/typo/openapi/testdata/test-features.yaml")

    println(s"Generating code from: $specPath")

    // Java with JAX-RS server only (blocking)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = Some(OpenApiServerLib.JaxRsSync),
      clientLib = None,
      lang = LangJava,
      extension = ".java",
      generateValidation = true
    )

    // Java with Spring server only (blocking)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = Some(OpenApiServerLib.SpringMvc),
      clientLib = None,
      lang = LangJava,
      extension = ".java",
      generateValidation = true
    )

    // Java with Quarkus server + MicroProfile client (reactive)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = Some(OpenApiServerLib.QuarkusReactive),
      clientLib = Some(OpenApiClientLib.MicroProfileReactive),
      lang = LangJava,
      extension = ".java",
      generateValidation = true
    )

    // Java with MicroProfile client only (blocking)
    generateCode(
      specPath = specPath,
      language = "java",
      serverLib = None,
      clientLib = Some(OpenApiClientLib.MicroProfileBlocking),
      lang = LangJava,
      extension = ".java",
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
      extension = ".scala",
      generateValidation = false
    )

    // Scala with HTTP4s server + client
    generateCode(
      specPath = specPath,
      language = "scala",
      serverLib = Some(OpenApiServerLib.Http4s),
      clientLib = Some(OpenApiClientLib.Http4s),
      lang = langScala,
      extension = ".scala",
      generateValidation = false
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
      extension: String,
      generateValidation: Boolean
  ): Unit = {
    val outputDirName = buildOutputDirName(language, serverLib, clientLib)
    val outputDir = buildDir.resolve(outputDirName)

    println(s"Output directory: $outputDir")

    // Clean output directory
    if (Files.exists(outputDir)) {
      Files.walk(outputDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
    }
    Files.createDirectories(outputDir)

    val options = OpenApiOptions
      .default(jvm.QIdent(List(jvm.Ident("testapi"))))
      .copy(
        serverLib = serverLib,
        clientLib = clientLib,
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
