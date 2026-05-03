package scripts
import bleep.internal.FileUtils
import bleep.{Commands, Started}

object GeneratedRowParsers extends bleep.BleepCodegenScript("GeneratedRowParsers") {
  val N = 100

  override def run(started: Started, commands: Commands, targets: List[GeneratedRowParsers.Target], args: List[String]): Unit = {
    targets.foreach { target =>
      target.project.value match {
        case "typr-dsl" =>
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
                  |    static <$tparamsDecl, Row> RowCodec<Row> of($params, $decodeFunction decode, java.util.function.Function<Row, Object[]> encode) {
                  |        return RowCodec.create(java.util.List.of(${range.map(nn => s"t$nn").mkString(", ")}), a -> decode.apply($decodeParams), encode);
                  |    }""".stripMargin
            }

          val javaContents =
            s"""|package dev.typr.dsl;
                |
                |import dev.typr.foundations.RowCodec;
                |import dev.typr.foundations.DbType;
                |
                |public interface RowCodecs {
                |${constructorMethods.mkString("\n\n")}
                |${functions.mkString("\n\n")}
                |}""".stripMargin

          FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/dsl/RowCodecs.java"), javaContents)

        case "typr-dsl-kotlin" =>
          // Generate Kotlin version (limited to 22 params due to Kotlin Function type limit)
          val kotlinConstructorMethods = 1
            .until(math.min(N, 23))
            .map { n =>
              val range = 0.until(n)
              val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
              val params = range.map(nn => s"t$nn: DbType<T$nn>").mkString(", ")
              val decodeParams = range.map(nn => s"T$nn").mkString(", ")
              val arrayIndices = range.map(nn => s"a[$nn] as T$nn").mkString(", ")
              val typeList = range.map(nn => s"t$nn.underlying").mkString(", ")

              s"""|    @Suppress("UNCHECKED_CAST")
                  |    fun <$tparamsDecl, Row : Any> of($params, decode: ($decodeParams) -> Row, encode: (Row) -> Array<Any?>): dev.typr.foundationskt.RowCodec<Row> {
                  |        val javaCodec = dev.typr.foundations.RowCodec.create(listOf($typeList), { a -> decode($arrayIndices) }, encode)
                  |        return dev.typr.foundationskt.RowCodec(javaCodec)
                  |    }""".stripMargin
            }

          val kotlinContents =
            s"""|package dev.typr.dslkt
                |
                |import dev.typr.foundationskt.DbType
                |
                |/**
                | * Kotlin-friendly factory methods for creating RowCodecs.
                | * Generated code - do not edit manually.
                | */
                |object RowCodecs {
                |${kotlinConstructorMethods.mkString("\n\n")}
                |}""".stripMargin

          FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/dslkt/RowCodecs.kt"), kotlinContents)

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
              val typeList = range.map(nn => s"t$nn.underlying").mkString(", ")

              s"""|  inline def of[$tparamsDecl, Row]($params)(decode: ($decodeParams) => Row)(encode: Row => Array[Any]): dev.typr.foundationssc.RowCodec[Row] = {
                  |    val javaCodec = dev.typr.foundations.RowCodec.create(java.util.List.of($typeList), a => decode($arrayIndices), r => encode(r))
                  |    new dev.typr.foundationssc.RowCodec(javaCodec)
                  |  }""".stripMargin
            }

          val scalaContents =
            s"""|package dev.typr.dslsc
                |
                |import dev.typr.foundationssc.DbType
                |
                |/** Scala-friendly factory methods for creating RowCodecs.
                |  *
                |  * Generated code - do not edit manually.
                |  */
                |object RowCodecs {
                |${scalaConstructorMethods.mkString("\n\n")}
                |}""".stripMargin

          FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/dslsc/RowCodecs.scala"), scalaContents)

        case other =>
          started.logger.error(s"Unknown target project: $other")
      }
    }
  }
}
