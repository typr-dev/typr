package typr.cli.config

import io.circe.Json
import io.circe.syntax.*
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import typr.config.generated.DbMatch
import typr.config.generated.FeatureMatcherString
import typr.config.generated.Matchers
import typr.config.generated.Output
import typr.config.generated.FieldType
import typr.config.generated.StringOrArrayString

class ConfigRoundtripTest extends AnyFunSuite with TypeCheckedTripleEquals {

  test("config roundtrip preserves sources") {
    val original = TyprConfig(
      version = Some(1),
      include = None,
      sources = Some(
        Map(
          "postgres" -> Json.obj(
            "type" -> Json.fromString("postgresql"),
            "host" -> Json.fromString("localhost"),
            "port" -> Json.fromInt(5432),
            "database" -> Json.fromString("testdb"),
            "username" -> Json.fromString("user"),
            "password" -> Json.fromString("pass")
          ),
          "duckdb" -> Json.obj(
            "type" -> Json.fromString("duckdb"),
            "path" -> Json.fromString(":memory:")
          )
        )
      ),
      types = None,
      outputs = None
    )

    val yaml = ConfigWriter.toYaml(original)
    val parsed = ConfigParser.parse(yaml)

    assert(parsed.isRight, s"Failed to parse: ${parsed.left.getOrElse("")}")
    val roundtripped = parsed.toOption.get

    assert(roundtripped.sources.isDefined, "sources should be defined")
    assert(roundtripped.sources.get.size === 2, "should have 2 sources")
    assert(roundtripped.sources.get.contains("postgres"), "should have postgres source")
    assert(roundtripped.sources.get.contains("duckdb"), "should have duckdb source")

    val pgSource = roundtripped.sources.get("postgres")
    assert(pgSource.hcursor.get[String]("type").toOption === Some("postgresql"))
    assert(pgSource.hcursor.get[String]("host").toOption === Some("localhost"))
    assert(pgSource.hcursor.get[Int]("port").toOption === Some(5432))
  }

  test("config roundtrip preserves outputs") {
    val original = TyprConfig(
      version = Some(1),
      include = None,
      sources = None,
      types = None,
      outputs = Some(
        Map(
          "scala-output" -> Output(
            bridge = None,
            db_lib = Some("foundations"),
            effect_type = None,
            framework = None,
            json = Some("jackson"),
            language = "scala",
            matchers = Some(
              Matchers(
                field_values = None,
                mock_repos = Some(FeatureMatcherString("all")),
                primary_key_types = Some(FeatureMatcherString("all")),
                readonly = None,
                test_inserts = Some(FeatureMatcherString("all"))
              )
            ),
            `package` = "myapp",
            path = "generated",
            scala = None,
            sources = Some(StringOrArrayString("postgres"))
          ),
          "java-output" -> Output(
            bridge = None,
            db_lib = Some("foundations"),
            effect_type = None,
            framework = None,
            json = Some("jackson"),
            language = "java",
            matchers = None,
            `package` = "myapp.java",
            path = "generated-java",
            scala = None,
            sources = Some(StringOrArrayString("postgres"))
          )
        )
      )
    )

    val yaml = ConfigWriter.toYaml(original)
    val parsed = ConfigParser.parse(yaml)

    assert(parsed.isRight, s"Failed to parse: ${parsed.left.getOrElse("")}")
    val roundtripped = parsed.toOption.get

    assert(roundtripped.outputs.isDefined, "outputs should be defined")
    assert(roundtripped.outputs.get.size === 2, "should have 2 outputs")
    assert(roundtripped.outputs.get.contains("scala-output"), "should have scala-output")
    assert(roundtripped.outputs.get.contains("java-output"), "should have java-output")

    val scalaOutput = roundtripped.outputs.get("scala-output")
    assert(scalaOutput.language === "scala")
    assert(scalaOutput.`package` === "myapp")
    assert(scalaOutput.path === "generated")
    assert(scalaOutput.db_lib === Some("foundations"))
    assert(scalaOutput.json === Some("jackson"))
  }

  test("config roundtrip preserves types") {
    val original = TyprConfig(
      version = Some(1),
      include = None,
      sources = None,
      types = Some(
        Map(
          "Email" -> FieldType(
            api = None,
            db = Some(
              DbMatch(
                annotation = None,
                column = Some(StringOrArrayString("email")),
                comment = None,
                db_type = None,
                domain = None,
                nullable = None,
                primary_key = None,
                references = None,
                schema = None,
                source = None,
                table = None
              )
            ),
            model = None,
            underlying = None,
            validation = None
          )
        )
      ),
      outputs = None
    )

    val yaml = ConfigWriter.toYaml(original)
    val parsed = ConfigParser.parse(yaml)

    assert(parsed.isRight, s"Failed to parse: ${parsed.left.getOrElse("")}")
    val roundtripped = parsed.toOption.get

    assert(roundtripped.types.isDefined, "types should be defined")
    assert(roundtripped.types.get.size === 1, "should have 1 type")
    assert(roundtripped.types.get.contains("Email"), "should have Email type")
  }

  test("config roundtrip preserves full config") {
    val original = TyprConfig(
      version = Some(1),
      include = Some(List("other.yaml")),
      sources = Some(
        Map(
          "postgres" -> Json.obj(
            "type" -> Json.fromString("postgresql"),
            "host" -> Json.fromString("localhost"),
            "port" -> Json.fromInt(5432)
          )
        )
      ),
      types = Some(
        Map(
          "Email" -> FieldType(
            api = None,
            db = Some(
              DbMatch(
                annotation = None,
                column = Some(StringOrArrayString("email")),
                comment = None,
                db_type = None,
                domain = None,
                nullable = None,
                primary_key = None,
                references = None,
                schema = None,
                source = None,
                table = None
              )
            ),
            model = None,
            underlying = None,
            validation = None
          )
        )
      ),
      outputs = Some(
        Map(
          "main" -> Output(
            bridge = None,
            db_lib = Some("foundations"),
            effect_type = None,
            framework = None,
            json = Some("jackson"),
            language = "scala",
            matchers = None,
            `package` = "myapp",
            path = "generated",
            scala = None,
            sources = Some(StringOrArrayString("postgres"))
          )
        )
      )
    )

    val yaml = ConfigWriter.toYaml(original)
    val parsed = ConfigParser.parse(yaml)

    assert(parsed.isRight, s"Failed to parse: ${parsed.left.getOrElse("")}")
    val roundtripped = parsed.toOption.get

    assert(roundtripped.version === Some(1))
    assert(roundtripped.include === Some(List("other.yaml")))
    assert(roundtripped.sources.get.size === 1)
    assert(roundtripped.types.get.size === 1)
    assert(roundtripped.outputs.get.size === 1)
  }

  test("actual typr.yaml roundtrip") {
    val yamlPath = java.nio.file.Paths.get("typr.yaml")
    if (java.nio.file.Files.exists(yamlPath)) {
      val originalYaml = java.nio.file.Files.readString(yamlPath)
      val parsed = ConfigParser.parse(originalYaml)

      assert(parsed.isRight, s"Failed to parse original typr.yaml: ${parsed.left.getOrElse("")}")
      val config = parsed.toOption.get

      val regeneratedYaml = ConfigWriter.toYaml(config)
      val reparsed = ConfigParser.parse(regeneratedYaml)

      assert(reparsed.isRight, s"Failed to parse regenerated yaml: ${reparsed.left.getOrElse("")}")
      val roundtripped = reparsed.toOption.get

      assert(roundtripped.sources.map(_.size) === config.sources.map(_.size), "sources count mismatch")
      assert(roundtripped.outputs.map(_.size) === config.outputs.map(_.size), "outputs count mismatch")
      assert(roundtripped.types.map(_.size) === config.types.map(_.size), "types count mismatch")
      assert(roundtripped.version === config.version, "version mismatch")

      config.sources.foreach { originalSources =>
        roundtripped.sources.foreach { rtSources =>
          originalSources.keys.foreach { key =>
            assert(rtSources.contains(key), s"Missing source: $key")
          }
        }
      }

      config.outputs.foreach { originalOutputs =>
        roundtripped.outputs.foreach { rtOutputs =>
          originalOutputs.keys.foreach { key =>
            assert(rtOutputs.contains(key), s"Missing output: $key")
          }
        }
      }
    }
  }
}
