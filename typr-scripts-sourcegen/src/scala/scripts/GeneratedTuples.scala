package scripts

import bleep.internal.FileUtils
import bleep.{Commands, Started}

object GeneratedTuples extends bleep.BleepCodegenScript("GeneratedTuples") {
  val N = 100 // Support tables with up to 100 columns

  override def run(started: Started, commands: Commands, targets: List[GeneratedTuples.Target], args: List[String]): Unit = {
    // Generate TupleN interfaces
    val tupleInterfaces = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val abstractMethods = range.map(nn => s"        T$nn _${nn + 1}();").mkString("\n")
      val asArrayBody = range.map(nn => s"_${nn + 1}()").mkString(", ")
      val implFields = range.map(nn => s"T$nn _${nn + 1}").mkString(", ")

      s"""    /**
         |     * Tuple with $n element${if (n > 1) "s" else ""}.
         |     * Use {@link Tuple#of} to create instances, or have your Row/ID records implement this interface.
         |     */
         |    non-sealed interface Tuple$n<$tparamsDecl> extends Tuple {
         |$abstractMethods
         |
         |        @Override
         |        default Object[] asArray() {
         |            return new Object[] { $asArrayBody };
         |        }
         |
         |        /** Default implementation record for Tuple$n. */
         |        record Impl<$tparamsDecl>($implFields) implements Tuple$n<$tparamsDecl> {}
         |    }""".stripMargin
    }

    // Factory methods for Tuple values
    val tupleOfMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val ofParams = range.map(nn => s"T$nn v$nn").mkString(", ")
      val ofArgs = range.map(nn => s"v$nn").mkString(", ")

      s"""    /** Create a Tuple$n with the given values. */
         |    static <$tparamsDecl> Tuple$n<$tparamsDecl> of($ofParams) {
         |        return new Tuple$n.Impl<>($ofArgs);
         |    }""".stripMargin
    }

    // Generate the createTuple switch cases
    val createTupleCases = 1.to(N).map { n =>
      val range = 0.until(n)
      val args = range.map(nn => s"values[$nn]").mkString(", ")
      s"            case $n -> Tuple.of($args);"
    }

    val tupleContents =
      s"""|package dev.typr.foundations;
          |
          |/**
          | * Tuple value types for the DSL.
          | * <p>
          | * Use {@link #of} factory methods to create tuple instances.
          | * These are used as Row types in queries.
          | */
          |public sealed interface Tuple {
          |    /** Returns all elements as an Object array. */
          |    Object[] asArray();
          |
          |    // Tuple value types (interfaces with Impl records)
          |${tupleInterfaces.mkString("\n\n")}
          |
          |    // Factory methods for Tuple values
          |${tupleOfMethods.mkString("\n\n")}
          |
          |    /**
          |     * Create a Tuple of the appropriate arity from an array of values.
          |     * @param values array of values (length 1-$N)
          |     * @return a Tuple of the appropriate arity
          |     * @throws IllegalArgumentException if values.length is 0 or greater than $N
          |     */
          |    @SuppressWarnings("unchecked")
          |    static Tuple createTuple(Object[] values) {
          |        return switch (values.length) {
          |${createTupleCases.mkString("\n")}
          |            default -> throw new IllegalArgumentException("Unsupported tuple arity: " + values.length);
          |        };
          |    }
          |}
          |""".stripMargin

    // Generate TupleExprN interfaces
    val tupleExprInterfaces = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val abstractMethods = range.map(nn => s"        SqlExpr<T$nn> _${nn + 1}();").mkString("\n")
      val toListBody = range.map(nn => s"_${nn + 1}()").mkString(", ")
      val implFields = range.map(nn => s"SqlExpr<T$nn> _${nn + 1}").mkString(", ")

      val abstractMethodsForClass = range.map(nn => s"        public abstract SqlExpr<T$nn> _${nn + 1}();").mkString("\n")

      s"""    /**
         |     * Tuple expression with $n element${if (n > 1) "s" else ""}.
         |     * Use {@link TupleExpr#of} factory methods to create instances,
         |     * or extend this abstract class directly for Fields classes.
         |     * Abstract class is used instead of interface to work around Scala 3 compiler bugs
         |     * with Java sealed interface hierarchies.
         |     */
         |    public static abstract class TupleExpr$n<$tparamsDecl> implements TupleExpr<Tuple.Tuple$n<$tparamsDecl>> {
         |$abstractMethodsForClass
         |
         |        @Override
         |        public java.util.List<SqlExpr<?>> children() {
         |            return java.util.List.of($toListBody);
         |        }
         |
         |        @Override
         |        @SuppressWarnings("unchecked")
         |        public dev.typr.foundations.DbType<Tuple.Tuple$n<$tparamsDecl>> dbType() {
         |            throw new UnsupportedOperationException("TupleExpr$n.dbType() - use children() to get individual column types");
         |        }
         |
         |        /** Default implementation for TupleExpr$n. */
         |        public static class Impl<$tparamsDecl> extends TupleExpr$n<$tparamsDecl> {
         |${range.map(nn => s"            private final SqlExpr<T$nn> _${nn + 1};").mkString("\n")}
         |            public Impl($implFields) {
         |${range.map(nn => s"                this._${nn + 1} = _${nn + 1};").mkString("\n")}
         |            }
         |${range.map(nn => s"            @Override public SqlExpr<T$nn> _${nn + 1}() { return _${nn + 1}; }").mkString("\n")}
         |        }
         |    }""".stripMargin
    }

    // Factory methods for TupleExpr - create Impl records
    val tupleExprOfMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"SqlExpr<T$nn> e$nn").mkString(", ")
      val args = range.map(nn => s"e$nn").mkString(", ")

      s"""    /** Create a TupleExpr$n from $n expressions. */
         |    static <$tparamsDecl> TupleExpr$n<$tparamsDecl> of($params) {
         |        return new TupleExpr$n.Impl<>($args);
         |    }""".stripMargin
    }

    val tupleExprContents =
      s"""|package dev.typr.foundations.dsl;
          |
          |import dev.typr.foundations.Tuple;
          |
          |/**
          | * Tuple expression types for the DSL.
          | * <p>
          | * Use {@link #of} factory methods to create tuple expression instances.
          | * These contain {@link SqlExpr} for each element and are used as Fields types in queries.
          | * <p>
          | * Each TupleExpr also implements SqlExpr of its corresponding {@link Tuple} type,
          | * allowing tuple expressions to be used anywhere a SqlExpr is expected.
          | */
          |public non-sealed interface TupleExpr<T extends Tuple> extends SqlExpr<T> {
          |
          |    /**
          |     * Returns the total number of columns this tuple expression produces.
          |     * Recursively counts columns from nested multi-column expressions.
          |     */
          |    @Override
          |    default int columnCount() {
          |        int count = 0;
          |        for (SqlExpr<?> expr : children()) {
          |            count += expr.columnCount();
          |        }
          |        return count;
          |    }
          |
          |    /**
          |     * Returns a flattened list of DbTypes for all columns.
          |     * Recursively flattens nested multi-column expressions.
          |     */
          |    @Override
          |    default java.util.List<dev.typr.foundations.DbType<?>> flattenedDbTypes() {
          |        java.util.List<dev.typr.foundations.DbType<?>> result = new java.util.ArrayList<>();
          |        for (SqlExpr<?> expr : children()) {
          |            result.addAll(expr.flattenedDbTypes());
          |        }
          |        return result;
          |    }
          |
          |    /**
          |     * Renders all expressions in this tuple as comma-separated SQL fragments.
          |     */
          |    @Override
          |    default dev.typr.foundations.Fragment render(dev.typr.foundations.dsl.RenderCtx ctx, java.util.concurrent.atomic.AtomicInteger counter) {
          |        java.util.List<dev.typr.foundations.Fragment> fragments = new java.util.ArrayList<>();
          |        for (SqlExpr<?> expr : children()) {
          |            fragments.add(expr.render(ctx, counter));
          |        }
          |        return dev.typr.foundations.Fragment.comma(fragments);
          |    }
          |
          |    /**
          |     * Check if this tuple is IN a list of values.
          |     * Uses this TupleExpr as the template for type information.
          |     * <pre>{@code
          |     * d.code().tupleWith(d.region()).in(idList)
          |     * }</pre>
          |     */
          |    default <V extends T> SqlExpr<Boolean> in(java.util.List<V> values) {
          |        return new SqlExpr.In<>(this, SqlExpr.Rows.of(this, values));
          |    }
          |
          |    /**
          |     * Check if this tuple is among a list of values.
          |     * Kotlin-friendly alternative to {@link #in(java.util.List)} since 'in' is a reserved keyword.
          |     * <pre>{@code
          |     * d.code().tupleWith(d.region()).among(idList)
          |     * }</pre>
          |     */
          |    default <V extends T> SqlExpr<Boolean> among(java.util.List<V> values) {
          |        return in(values);
          |    }
          |
          |    /**
          |     * Check if this tuple is among a set of values (vararg version).
          |     * <pre>{@code
          |     * d.code().tupleWith(d.region()).among(id1, id2, id3)
          |     * }</pre>
          |     */
          |    @SuppressWarnings("unchecked")
          |    default <V extends T> SqlExpr<Boolean> among(V first, V... rest) {
          |        java.util.List<V> values = new java.util.ArrayList<>();
          |        values.add(first);
          |        if (rest != null) {
          |            java.util.Collections.addAll(values, rest);
          |        }
          |        return in(values);
          |    }
          |
          |    // For subqueries, use: d.code().tupleWith(d.region()).among(repo.select().map(...).subquery())
          |
          |    // Tuple expression types (interfaces with Impl records)
          |${tupleExprInterfaces.mkString("\n\n")}
          |
          |    // Factory methods for TupleExpr
          |${tupleExprOfMethods.mkString("\n\n")}
          |}
          |""".stripMargin

    // Generate Kotlin Tuple factory object
    val kotlinTupleOfMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"v$nn: T$nn").mkString(", ")
      val args = range.map(nn => s"v$nn").mkString(", ")

      s"""    @JvmStatic
         |    fun <$tparamsDecl> of($params): JavaTuple.Tuple$n<$tparamsDecl> =
         |        JavaTuple.of($args)""".stripMargin
    }

    val kotlinTupleContents =
      s"""|package dev.typr.foundations.kotlin
          |
          |import dev.typr.foundations.Tuple as JavaTuple
          |
          |/**
          | * Kotlin-friendly factory methods for creating tuple values.
          | * Delegates to the Java Tuple.of() methods.
          | *
          | * Usage:
          | * ```kotlin
          | * // Create tuple values to pass to among():
          | * p.name().tupleWith(p.price()).among(listOf(
          | *     Tuple.of("Widget", BigDecimal("19.99")),
          | *     Tuple.of("Gadget", BigDecimal("29.99"))
          | * ))
          | * ```
          | */
          |object Tuple {
          |${kotlinTupleOfMethods.mkString("\n\n")}
          |}
          |""".stripMargin

    // Generate Kotlin TupleExpr interfaces (like Java TupleExprN) with wrapper impl classes
    val kotlinTupleExprWrappers = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val javaTupleType = s"JavaTuple.Tuple$n<$tparamsDecl>"
      val javaTupleExprType = s"dev.typr.foundations.dsl.TupleExpr.TupleExpr$n<$tparamsDecl>"
      // Abstract _N() accessor methods that return Kotlin SqlExpr
      val abstractMethods = range.map(nn => s"    fun _${nn + 1}(): SqlExpr<T$nn>").mkString("\n")
      // Impl class that wraps Java TupleExprN - use SqlExpr.wrap to convert Java SqlExpr to Kotlin SqlExpr
      val implMethods = range.map(nn => s"        override fun _${nn + 1}(): SqlExpr<T$nn> = SqlExpr.wrap(underlying._${nn + 1}())").mkString("\n")

      // Build underlying from _N() methods for Fields classes
      val underlyingArgs = range.map(nn => s"_${nn + 1}().underlying").mkString(", ")

      s"""/** Kotlin interface for TupleExpr$n - can be implemented by Fields classes */
         |interface TupleExpr$n<$tparamsDecl> : TupleExpr<$javaTupleType> {
         |$abstractMethods
         |
         |    /** Default underlying implementation that builds from _N() accessors */
         |    override val underlying: $javaTupleExprType
         |        get() = dev.typr.foundations.dsl.TupleExpr.of($underlyingArgs)
         |
         |    /** Default implementation that wraps a Java TupleExpr$n */
         |    class Impl<$tparamsDecl>(private val _underlying: $javaTupleExprType) : TupleExpr$n<$tparamsDecl> {
         |        override val underlying: $javaTupleExprType get() = _underlying
         |$implMethods
         |    }
         |}""".stripMargin
    }

    // Factory methods for Kotlin TupleExpr (takes Kotlin SqlExpr, returns Kotlin TupleExprN.Impl)
    val kotlinTupleExprOfMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"e$nn: SqlExpr<T$nn>").mkString(", ")
      val underlyingArgs = range.map(nn => s"e$nn.underlying").mkString(", ")

      s"""        /** Create a TupleExpr$n from $n expressions. */
         |        @JvmStatic
         |        fun <$tparamsDecl> of($params): TupleExpr$n<$tparamsDecl> =
         |            TupleExpr$n.Impl(dev.typr.foundations.dsl.TupleExpr.of($underlyingArgs))""".stripMargin
    }

    val kotlinTupleExprContents =
      s"""|package dev.typr.foundations.kotlin
          |
          |import dev.typr.foundations.Tuple as JavaTuple
          |
          |/**
          | * Base interface for Kotlin TupleExpr wrappers.
          | * Extends SqlExpr so tuple expressions can be used anywhere SqlExpr is expected.
          | */
          |interface TupleExpr<T : JavaTuple> : SqlExpr<T> {
          |    /** Override with more specific type for use in SelectBuilder.map */
          |    override val underlying: dev.typr.foundations.dsl.TupleExpr<T>
          |
          |    companion object {
          |${kotlinTupleExprOfMethods.mkString("\n\n")}
          |    }
          |}
          |
          |${kotlinTupleExprWrappers.mkString("\n\n")}
          |""".stripMargin

    // Generate Scala Tuple factory methods for values
    val scalaOfMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"v$nn: T$nn").mkString(", ")
      val args = range.map(nn => s"v$nn").mkString(", ")

      s"""  def of[$tparamsDecl]($params): JavaTuple.Tuple$n[$tparamsDecl] =
         |    JavaTuple.of($args)""".stripMargin
    }

    // Generate Scala Tuples factory methods for SqlExpr (returns Scala TupleExprN.Impl wrapper)
    val scalaOfExprMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"e$nn: SqlExpr[T$nn]").mkString(", ")
      val underlyingArgs = range.map(nn => s"e$nn.underlying").mkString(", ")

      s"""  def of[$tparamsDecl]($params): TupleExpr$n[$tparamsDecl] =
         |    TupleExpr$n.Impl(JavaTupleExpr.of($underlyingArgs))""".stripMargin
    }

    val scalaTupleExprWrappers = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val javaTupleType = s"JavaTuple.Tuple$n[$tparamsDecl]"
      val javaTupleExprType = s"JavaTupleExpr.TupleExpr$n[$tparamsDecl]"
      // Abstract _N accessor methods that return Scala SqlExpr
      val abstractMethods = range.map(nn => s"  def _${nn + 1}: SqlExpr[T$nn]").mkString("\n")
      // Impl case class that wraps Java TupleExprN - use SqlExpr.wrap to convert Java SqlExpr to Scala SqlExpr
      val implMethods = range.map(nn => s"    override def _${nn + 1}: SqlExpr[T$nn] = SqlExpr.wrap(underlying._${nn + 1}())").mkString("\n")

      // Build underlying from _N() methods for Fields classes
      val underlyingArgs = range.map(nn => s"_${nn + 1}.underlying").mkString(", ")

      s"""/** Scala abstract class for TupleExpr$n - can be extended by Fields classes.
         | * Uses abstract class instead of trait to work around Scala compiler bugs with Java interface inheritance.
         | */
         |abstract class TupleExpr$n[$tparamsDecl] extends TupleExpr[$javaTupleType] {
         |$abstractMethods
         |
         |  /** Default underlying implementation that builds from _N() accessors */
         |  override def underlying: $javaTupleExprType = JavaTupleExpr.of($underlyingArgs)
         |}
         |
         |object TupleExpr$n {
         |  /** Create a TupleExpr$n that wraps a Java TupleExpr$n */
         |  def apply[$tparamsDecl](underlying: $javaTupleExprType): TupleExpr$n[$tparamsDecl] = Impl(underlying)
         |
         |  /** Default implementation that wraps a Java TupleExpr$n */
         |  final case class Impl[$tparamsDecl](override val underlying: $javaTupleExprType) extends TupleExpr$n[$tparamsDecl] {
         |$implMethods
         |  }
         |
         |  /** Bijection for SelectBuilder.map to convert Scala TupleExpr$n to Java TupleExpr$n */
         |  given bijection[$tparamsDecl]: dev.typr.foundations.dsl.Bijection[TupleExpr$n[$tparamsDecl], $javaTupleExprType] =
         |    dev.typr.foundations.dsl.Bijection.of(
         |      (s: TupleExpr$n[$tparamsDecl]) => s.underlying,
         |      (j: $javaTupleExprType) => Impl(j)
         |    )
         |}""".stripMargin
    }

    val scalaTuplesContents =
      s"""|package dev.typr.foundations.scala
          |
          |import dev.typr.foundations.dsl.{TupleExpr => JavaTupleExpr}
          |import dev.typr.foundations.{Tuple => JavaTuple}
          |
          |/**
          | * Scala-friendly factory methods for creating tuple values.
          | *
          | * Usage:
          | * {{{
          | * // Create tuple values to pass to in():
          | * p.name().tupleWith(p.price()).in(List(
          | *     Tuples.of("Widget", BigDecimal("19.99")),
          | *     Tuples.of("Gadget", BigDecimal("29.99"))
          | * ))
          | * }}}
          | */
          |object Tuples {
          |  // Factory methods for tuple values
          |${scalaOfMethods.mkString("\n\n")}
          |}
          |
          |// TupleExpr base trait is defined in SqlExpr.scala
          |// TupleExpr companion object is defined in SqlExpr.scala and extends this trait
          |
          |/** Generated factory methods for TupleExpr. The companion object in SqlExpr.scala extends this. */
          |trait TupleExprCompanion {
          |${scalaOfExprMethods.mkString("\n\n")}
          |}
          |
          |// TupleExpr wrappers - extend TupleExpr base trait from SqlExpr.scala
          |${scalaTupleExprWrappers.mkString("\n\n")}
          |""".stripMargin

    targets.foreach { target =>
      val projectName = target.project.value
      if (projectName == "foundations-jdbc") {
        FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/foundations/Tuple.java"), tupleContents)
      } else if (projectName == "foundations-jdbc-dsl") {
        FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/foundations/dsl/TupleExpr.java"), tupleExprContents)
      } else if (projectName == "foundations-jdbc-dsl-kotlin") {
        FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/foundations/kotlin/Tuple.kt"), kotlinTupleContents)
        FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/foundations/kotlin/TupleExpr.kt"), kotlinTupleExprContents)
      } else if (projectName == "foundations-jdbc-dsl-scala") {
        FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/foundations/scala/Tuples.scala"), scalaTuplesContents)
      }
    }
  }
}
