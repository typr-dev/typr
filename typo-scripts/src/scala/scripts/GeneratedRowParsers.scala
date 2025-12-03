package scripts
import bleep.internal.FileUtils
import bleep.{Commands, Started}

object GeneratedRowParsers extends bleep.BleepCodegenScript("GeneratedRowParsers") {
  val N = 100

  override def run(started: Started, commands: Commands, targets: List[GeneratedRowParsers.Target], args: List[String]): Unit = {
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
        // Use java.util.function.Function<Row, Object[]> for encode - no complex generic inference needed
        s"""|    @SuppressWarnings("unchecked")
            |    static <$tparamsDecl, Row> RowParser<Row> of($params, $decodeFunction decode, java.util.function.Function<Row, Object[]> encode) {
            |        return new RowParser<>(unmodifiableList(asList(${range.map(nn => s"t$nn").mkString(", ")})), a -> decode.apply($decodeParams), encode);
            |    }""".stripMargin
      }

    val contents =
      s"""|package typo.runtime;
          |
          |import static java.util.Arrays.asList;
          |import static java.util.Collections.unmodifiableList;
          |
          |public interface RowParsers {
          |${constructorMethods.mkString("\n\n")}
          |${functions.mkString("\n\n")}
          |}""".stripMargin

    targets.foreach { target =>
      FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("typo/runtime/RowParsers.java"), contents)
    }
  }
}
