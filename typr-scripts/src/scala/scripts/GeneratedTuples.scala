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
      val ofParams = range.map(nn => s"T$nn v$nn").mkString(", ")
      val ofArgs = range.map(nn => s"v$nn").mkString(", ")

      s"""    /**
         |     * Tuple with $n element${if (n > 1) "s" else ""}.
         |     * Use {@link #of} to create instances, or have your Row/ID records implement this interface.
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
         |
         |        /** Create a Tuple$n with the given values. */
         |        static <$tparamsDecl> Tuple$n<$tparamsDecl> of($ofParams) {
         |            return new Impl<>($ofArgs);
         |        }
         |    }""".stripMargin
    }

    // Generate TupleExprN interfaces
    // Note: construct() is NOT included here - it's generated in each Fields.Impl class
    // because the return type (Row) has different Optional handling than TupleN type params
    val tupleExprInterfaces = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val abstractMethods = range.map(nn => s"        SqlExpr<T$nn> _${nn + 1}();").mkString("\n")
      val toListBody = range.map(nn => s"_${nn + 1}()").mkString(", ")
      val renderArgs = range.map(nn => s"_${nn + 1}().render(ctx, counter)").mkString(", ")
      val implFields = range.map(nn => s"SqlExpr<T$nn> _${nn + 1}").mkString(", ")

      s"""    /**
         |     * Tuple expression with $n element${if (n > 1) "s" else ""}.
         |     * Use {@link Tuples#of} factory methods to create instances,
         |     * or have your Fields classes implement this interface directly.
         |     */
         |    interface TupleExpr$n<$tparamsDecl> extends TupleExpr<Tuple$n<$tparamsDecl>> {
         |$abstractMethods
         |
         |        @Override
         |        default java.util.List<SqlExpr<?>> exprs() {
         |            return java.util.List.of($toListBody);
         |        }
         |
         |        @Override
         |        @SuppressWarnings("unchecked")
         |        default dev.typr.foundations.DbType<Tuple$n<$tparamsDecl>> dbType() {
         |            throw new UnsupportedOperationException("TupleExpr$n.dbType() - use exprs() to get individual column types");
         |        }
         |
         |        @Override
         |        default dev.typr.foundations.Fragment render(dev.typr.foundations.dsl.RenderCtx ctx, java.util.concurrent.atomic.AtomicInteger counter) {
         |            java.util.List<dev.typr.foundations.Fragment> fragments = java.util.List.of($renderArgs);
         |            return dev.typr.foundations.Fragment.comma(fragments);
         |        }
         |
         |        /** Default implementation record for TupleExpr$n. */
         |        record Impl<$tparamsDecl>($implFields) implements TupleExpr$n<$tparamsDecl> {}
         |    }""".stripMargin
    }

    // Factory methods for TupleExpr - create Impl records
    val ofMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"SqlExpr<T$nn> e$nn").mkString(", ")
      val args = range.map(nn => s"e$nn").mkString(", ")

      s"""    /** Create a TupleExpr$n from $n expressions. */
         |    static <$tparamsDecl> TupleExpr$n<$tparamsDecl> of($params) {
         |        return new TupleExpr$n.Impl<>($args);
         |    }""".stripMargin
    }

    // Generate the createTuple switch cases - use the static of() methods
    val createTupleCases = 1.to(N).map { n =>
      val range = 0.until(n)
      val args = range.map(nn => s"values[$nn]").mkString(", ")
      s"            case $n -> Tuple$n.of($args);"
    }

    val tuplesContents =
      s"""|package dev.typr.foundations.dsl;
          |
          |/**
          | * Generated tuple types for the DSL.
          | * <p>
          | * {@link Tuple} is the value type (used as Row type).
          | * {@link TupleExpr} is the expression type (used as Fields type), containing {@link SqlExpr} for each element.
          | * <p>
          | * Each {@link TupleExpr} also implements {@link SqlExpr} of its corresponding {@link Tuple} type,
          | * allowing tuple expressions to be used anywhere a SqlExpr is expected.
          | */
          |public interface Tuples {
          |
          |    /**
          |     * Marker interface for all tuple value types.
          |     */
          |    sealed interface Tuple {
          |        /** Returns all elements as an Object array. */
          |        Object[] asArray();
          |    }
          |
          |    /**
          |     * Marker interface for all tuple expression types.
          |     * Use {@link #of} methods to create instances.
          |     * <p>
          |     * Each TupleExpr also implements SqlExpr of its corresponding Tuple type,
          |     * e.g., TupleExpr2&lt;String, Integer&gt; implements SqlExpr&lt;Tuple2&lt;String, Integer&gt;&gt;.
          |     * This is non-sealed to allow the generated TupleExprN records to implement it.
          |     */
          |    non-sealed interface TupleExpr<T extends Tuple> extends SqlExpr<T> {
          |        /** Returns all SqlExpr elements in this tuple expression. */
          |        java.util.List<SqlExpr<?>> exprs();
          |
          |        /**
          |         * Returns the total number of columns this tuple expression produces.
          |         * Recursively counts columns from nested multi-column expressions.
          |         */
          |        @Override
          |        default int columnCount() {
          |            int count = 0;
          |            for (SqlExpr<?> expr : exprs()) {
          |                count += expr.columnCount();
          |            }
          |            return count;
          |        }
          |
          |        /**
          |         * Returns a flattened list of DbTypes for all columns.
          |         * Recursively flattens nested multi-column expressions.
          |         */
          |        @Override
          |        default java.util.List<dev.typr.foundations.DbType<?>> flattenedDbTypes() {
          |            java.util.List<dev.typr.foundations.DbType<?>> result = new java.util.ArrayList<>();
          |            for (SqlExpr<?> expr : exprs()) {
          |                result.addAll(expr.flattenedDbTypes());
          |            }
          |            return result;
          |        }
          |
          |        /**
          |         * Check if this tuple is IN a list of values.
          |         * Uses this TupleExpr as the template for type information.
          |         * <pre>{@code
          |         * d.code().tupleWith(d.region()).in(idList)
          |         * }</pre>
          |         */
          |        @SuppressWarnings("unchecked")
          |        default <V extends T> SqlExpr<Boolean> in(java.util.List<V> values) {
          |            return new SqlExpr.In<>(this, SqlExpr.Rows.ofTuples((TupleExpr<T>) this, values));
          |        }
          |
          |        /**
          |         * Check if this tuple is among a list of values.
          |         * Kotlin-friendly alternative to {@link #in(java.util.List)} since 'in' is a reserved keyword.
          |         * <pre>{@code
          |         * d.code().tupleWith(d.region()).among(idList)
          |         * }</pre>
          |         */
          |        default <V extends T> SqlExpr<Boolean> among(java.util.List<V> values) {
          |            return in(values);
          |        }
          |
          |        /**
          |         * Check if this tuple is among a set of values (vararg version).
          |         * <pre>{@code
          |         * d.code().tupleWith(d.region()).among(id1, id2, id3)
          |         * }</pre>
          |         */
          |        @SuppressWarnings("unchecked")
          |        default <V extends T> SqlExpr<Boolean> among(V first, V... rest) {
          |            java.util.List<V> values = new java.util.ArrayList<>();
          |            values.add(first);
          |            if (rest != null) {
          |                java.util.Collections.addAll(values, rest);
          |            }
          |            return in(values);
          |        }
          |
          |        // For subqueries, use: d.code().tupleWith(d.region()).among(repo.select().map(...).subquery())
          |    }
          |
          |    // Tuple value types (interfaces with Impl records)
          |${tupleInterfaces.mkString("\n\n")}
          |
          |    // Tuple expression types (interfaces with Impl records)
          |${tupleExprInterfaces.mkString("\n\n")}
          |
          |    // Factory methods for TupleExpr
          |${ofMethods.mkString("\n\n")}
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

    val selectBuilderMapContents =
      s"""|package dev.typr.foundations.dsl;
          |
          |/**
          | * Map method interface for {@link SelectBuilder}.
          | * <p>
          | * Usage:
          | * <pre>
          | * // Project to tuple using tupleWith():
          | * builder.map(p -&gt; p.name().tupleWith(p.age()))
          | *
          | * // Or using Tuples.of():
          | * builder.map(p -&gt; Tuples.of(p.name(), p.age()))
          | * </pre>
          | */
          |public interface SelectBuilderMap<Fields, Row> {
          |
          |    /**
          |     * Project to a new tuple expression.
          |     * Use {@code tupleWith()} methods on SqlExpr or {@code Tuples.of()} to create tuple expressions.
          |     */
          |    <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
          |        SelectBuilder<NewFields, NewRow> map(java.util.function.Function<Fields, NewFields> projection);
          |}
          |""".stripMargin

    // Generate Kotlin Tuples factory object
    val kotlinOfMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"v$nn: T$nn").mkString(", ")
      val args = range.map(nn => s"v$nn").mkString(", ")

      s"""    @JvmStatic
         |    fun <$tparamsDecl> of($params): JavaTuples.Tuple$n<$tparamsDecl> =
         |        JavaTuples.Tuple$n.of($args)""".stripMargin
    }

    val kotlinTuplesContents =
      s"""|package dev.typr.foundations.kotlin
          |
          |import dev.typr.foundations.dsl.Tuples as JavaTuples
          |
          |/**
          | * Kotlin-friendly factory methods for creating tuple values.
          | * Delegates to the Java Tuples.TupleN.of() methods.
          | *
          | * Usage:
          | * ```kotlin
          | * // Create tuple values to pass to among():
          | * p.name().tupleWith(p.price()).among(listOf(
          | *     Tuples.of("Widget", BigDecimal("19.99")),
          | *     Tuples.of("Gadget", BigDecimal("29.99"))
          | * ))
          | * ```
          | */
          |object Tuples {
          |${kotlinOfMethods.mkString("\n\n")}
          |}
          |""".stripMargin

    // Generate Scala Tuples factory methods for values
    val scalaOfMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"v$nn: T$nn").mkString(", ")
      val args = range.map(nn => s"v$nn").mkString(", ")

      s"""  def of[$tparamsDecl]($params): JavaTuples.Tuple$n[$tparamsDecl] =
         |    JavaTuples.Tuple$n.of($args)""".stripMargin
    }

    // Generate Scala TupleExprN wrapper case classes (only need up to 6 for tupleWith support)
    val MaxTupleExprArity = 6

    // Generate Scala Tuples factory methods for SqlExpr (returns Java TupleExprN for use in generated code)
    // Users should use tupleWith() for the Scala DSL experience
    val scalaOfExprMethods = 2.to(MaxTupleExprArity).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"e$nn: SqlExpr[T$nn]").mkString(", ")
      val underlyingArgs = range.map(nn => s"e$nn.underlying").mkString(", ")

      s"""  def of[$tparamsDecl]($params): JavaTuples.TupleExpr$n[$tparamsDecl] =
         |    JavaTuples.of($underlyingArgs)""".stripMargin
    }

    val scalaTupleExprWrappers = 2.to(MaxTupleExprArity).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val javaTupleExprType = s"JavaTuples.TupleExpr$n[$tparamsDecl]"
      val javaTupleType = s"JavaTuples.Tuple$n[$tparamsDecl]"

      s"""/** Scala wrapper for TupleExpr$n with in()/among() returning Scala SqlExpr[Boolean] */
         |final case class TupleExpr$n[$tparamsDecl](underlying: $javaTupleExprType) {
         |  def in[V <: $javaTupleType](values: java.util.List[V]): SqlExpr[Boolean] =
         |    SqlExpr.wrapBool(underlying.in(values))
         |
         |  def in(subquery: Subquery[$javaTupleExprType, $javaTupleType]): SqlExpr[Boolean] =
         |    SqlExpr.wrapBool(underlying.in(subquery.underlying))
         |
         |  def among[V <: $javaTupleType](values: java.util.List[V]): SqlExpr[Boolean] =
         |    in(values)
         |
         |  def among(subquery: Subquery[$javaTupleExprType, $javaTupleType]): SqlExpr[Boolean] =
         |    in(subquery)
         |}""".stripMargin
    }

    val scalaTuplesContents =
      s"""|package dev.typr.foundations.scala
          |
          |import dev.typr.foundations.dsl.{Tuples => JavaTuples}
          |
          |/**
          | * Scala-friendly factory methods for creating tuple values and expressions.
          | *
          | * Usage:
          | * {{{
          | * // Create tuple values to pass to in():
          | * p.name().tupleWith(p.price()).in(List(
          | *     Tuples.of("Widget", BigDecimal("19.99")),
          | *     Tuples.of("Gadget", BigDecimal("29.99"))
          | * ))
          | *
          | * // Create tuple expressions:
          | * Tuples.of(p.name(), p.price())  // returns TupleExpr2[Name, BigDecimal]
          | * }}}
          | */
          |object Tuples {
          |  // Factory methods for tuple values
          |${scalaOfMethods.mkString("\n\n")}
          |
          |  // Factory methods for tuple expressions (SqlExpr arguments)
          |${scalaOfExprMethods.mkString("\n\n")}
          |}
          |
          |/** Scala wrapper for Subquery */
          |final case class Subquery[F, R](underlying: dev.typr.foundations.dsl.SqlExpr.Subquery[F, R])
          |
          |// TupleExpr wrappers - wrap Java TupleExprN with in()/among() returning Scala SqlExpr[Boolean]
          |${scalaTupleExprWrappers.mkString("\n\n")}
          |""".stripMargin

    targets.foreach { target =>
      val projectName = target.project.value
      if (projectName == "foundations-jdbc-dsl") {
        FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/foundations/dsl/Tuples.java"), tuplesContents)
        FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/foundations/dsl/SelectBuilderMap.java"), selectBuilderMapContents)
      } else if (projectName == "foundations-jdbc-dsl-kotlin") {
        FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/foundations/kotlin/Tuples.kt"), kotlinTuplesContents)
      } else if (projectName == "foundations-jdbc-dsl-scala") {
        FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("dev/typr/foundations/scala/Tuples.scala"), scalaTuplesContents)
      }
    }
  }
}
