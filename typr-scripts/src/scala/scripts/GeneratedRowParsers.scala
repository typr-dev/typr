package scripts
import bleep.internal.FileUtils
import bleep.{Commands, Started}

object GeneratedRowParsers extends bleep.BleepCodegenScript("GeneratedRowParsers") {
  val N = 100

  override def run(started: Started, commands: Commands, targets: List[GeneratedRowParsers.Target], args: List[String]): Unit = {
    targets.foreach { target =>
      target.project.value match {
        case "typr-runtime-java" =>
          // Generate Java version
          val functions = 1
            .until(N)
            .map { n =>
              s"""|    @FunctionalInterface
                  |    interface Function$n<${0.until(n).map(nn => s"T$nn").mkString(", ")}, R> {
                  |        R apply(${0.until(n).map(nn => s"T$nn t$nn").mkString(", ")});
                  |    }""".stripMargin
            }

          val constructorMethods = 1
            .until(N)
            .map { n =>
              val range = 0.until(n)
              val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
              val tparams = range.map(nn => s"T$nn").mkString(", ")
              val params = range.map(nn => s"DbType<T$nn> t$nn").mkString(", ")
              val decodeFunction = s"Function$n<$tparams, Row>"
              val decodeParams = range.map(nn => s"(T$nn) a[$nn]").mkString(", ")
              s"""|    @SuppressWarnings("unchecked")
                  |    static <$tparamsDecl, Row> RowParser<Row> of($params, $decodeFunction decode, java.util.function.Function<Row, Object[]> encode) {
                  |        return new RowParser<>(unmodifiableList(asList(${range.map(nn => s"t$nn").mkString(", ")})), a -> decode.apply($decodeParams), encode);
                  |    }""".stripMargin
            }

          val javaContents =
            s"""|package typr.runtime;
                |
                |import static java.util.Arrays.asList;
                |import static java.util.Collections.unmodifiableList;
                |
                |public interface RowParsers {
                |${constructorMethods.mkString("\n\n")}
                |${functions.mkString("\n\n")}
                |}""".stripMargin

          FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("typr/runtime/RowParsers.java"), javaContents)

        case "typr-dsl-kotlin" =>
          // Generate Kotlin version
          val kotlinConstructorMethods = 1
            .until(N)
            .map { n =>
              val range = 0.until(n)
              val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
              val params = range.map(nn => s"t$nn: DbType<T$nn>").mkString(", ")
              val decodeParams = range.map(nn => s"T$nn").mkString(", ")
              val arrayIndices = range.map(nn => s"a[$nn] as T$nn").mkString(", ")
              val typeList = range.map(nn => s"t$nn").mkString(", ")

              s"""|    @Suppress("UNCHECKED_CAST")
                  |    fun <$tparamsDecl, Row> of($params, decode: ($decodeParams) -> Row, encode: (Row) -> Array<Any?>): RowParser<Row> {
                  |        val javaParser = JavaRowParser(listOf($typeList), { a -> decode($arrayIndices) }, encode)
                  |        return RowParser(javaParser)
                  |    }""".stripMargin
            }

          val kotlinContents =
            s"""|package typr.kotlindsl
                |
                |import typr.runtime.DbType
                |import typr.runtime.RowParser as JavaRowParser
                |
                |/**
                | * Kotlin-friendly factory methods for creating RowParsers.
                | * Generated code - do not edit manually.
                | */
                |object RowParsers {
                |${kotlinConstructorMethods.mkString("\n\n")}
                |}""".stripMargin

          FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("typr/kotlindsl/RowParsers.kt"), kotlinContents)

        case "typr-dsl-scala" =>
          // Generate Scala version with curried parameters
          val scalaConstructorMethods = 1
            .until(N)
            .map { n =>
              val range = 0.until(n)
              val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
              val params = range.map(nn => s"t$nn: DbType[T$nn]").mkString(", ")
              val decodeParams = range.map(nn => s"T$nn").mkString(", ")
              val arrayIndices = range.map(nn => s"a($nn).asInstanceOf[T$nn]").mkString(", ")
              val typeList = range.map(nn => s"t$nn").mkString(", ")

              s"""|  inline def of[$tparamsDecl, Row]($params)(decode: ($decodeParams) => Row)(encode: Row => Array[Any]): typr.scaladsl.RowParser[Row] = {
                  |    val javaParser = new typr.runtime.RowParser(java.util.List.of($typeList), a => decode($arrayIndices), r => encode(r))
                  |    new typr.scaladsl.RowParser(javaParser)
                  |  }""".stripMargin
            }

          val scalaContents =
            s"""|package typr.scaladsl
                |
                |import typr.runtime.DbType
                |
                |/** Scala-friendly factory methods for creating RowParsers.
                |  *
                |  * Generated code - do not edit manually.
                |  */
                |object RowParsers {
                |${scalaConstructorMethods.mkString("\n\n")}
                |}""".stripMargin

          FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("typr/scaladsl/RowParsers.scala"), scalaContents)

        case other =>
          started.logger.error(s"Unknown target project: $other")
      }
    }
  }
}
