package scripts

import bleep.internal.FileUtils
import bleep.{Commands, Started}

object GeneratedTuples extends bleep.BleepCodegenScript("GeneratedTuples") {
  val N = 22 // Match Scala's tuple arity

  override def run(started: Started, commands: Commands, targets: List[GeneratedTuples.Target], args: List[String]): Unit = {
    val tupleRecords = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val fields = range.map(nn => s"T$nn _${nn + 1}").mkString(", ")
      val asArrayBody = range.map(nn => s"_${nn + 1}").mkString(", ")

      s"""    record Tuple$n<$tparamsDecl>($fields) implements Tuple {
         |        @Override
         |        public Object[] asArray() {
         |            return new Object[] { $asArrayBody };
         |        }
         |    }""".stripMargin
    }

    val tupleExprRecords = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val fields = range.map(nn => s"SqlExpr<T$nn> _${nn + 1}").mkString(", ")
      val toListBody = range.map(nn => s"_${nn + 1}").mkString(", ")
      val renderArgs = range.map(nn => s"_${nn + 1}().render(ctx, counter)").mkString(", ")

      s"""    record TupleExpr$n<$tparamsDecl>($fields) implements TupleExpr<Tuple$n<$tparamsDecl>> {
         |        @Override
         |        public java.util.List<SqlExpr<?>> exprs() {
         |            return java.util.List.of($toListBody);
         |        }
         |
         |        @Override
         |        @SuppressWarnings("unchecked")
         |        public typr.runtime.DbType<Tuple$n<$tparamsDecl>> dbType() {
         |            // TupleExpr is used in projections where columns are rendered individually via exprs().
         |            // For now, throw since we don't have a proper DbType for tuples.
         |            throw new UnsupportedOperationException("TupleExpr$n.dbType() - use exprs() to get individual column types");
         |        }
         |
         |        @Override
         |        public typr.runtime.Fragment render(typr.dsl.RenderCtx ctx, java.util.concurrent.atomic.AtomicInteger counter) {
         |            // Render all sub-expressions as a comma-separated list
         |            java.util.List<typr.runtime.Fragment> fragments = java.util.List.of($renderArgs);
         |            return typr.runtime.Fragment.comma(fragments);
         |        }
         |    }""".stripMargin
    }

    val ofMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"SqlExpr<T$nn> e$nn").mkString(", ")
      val args = range.map(nn => s"e$nn").mkString(", ")

      s"""    static <$tparamsDecl> TupleExpr$n<$tparamsDecl> of($params) {
         |        return new TupleExpr$n<>($args);
         |    }""".stripMargin
    }

    // Generate the createTuple switch cases
    val createTupleCases = 1.to(N).map { n =>
      val range = 0.until(n)
      val args = range.map(nn => s"values[$nn]").mkString(", ")
      s"            case $n -> new Tuple$n<>($args);"
    }

    val tuplesContents =
      s"""|package typr.dsl;
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
          |        default java.util.List<typr.runtime.DbType<?>> flattenedDbTypes() {
          |            java.util.List<typr.runtime.DbType<?>> result = new java.util.ArrayList<>();
          |            for (SqlExpr<?> expr : exprs()) {
          |                result.addAll(expr.flattenedDbTypes());
          |            }
          |            return result;
          |        }
          |    }
          |
          |    // Tuple value types
          |${tupleRecords.mkString("\n\n")}
          |
          |    // Tuple expression types
          |${tupleExprRecords.mkString("\n\n")}
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

    // Generate typed map methods for SelectBuilder
    val mapMethods = 1.to(N).map { n =>
      val range = 0.until(n)
      val tparamsDecl = range.map(nn => s"T$nn").mkString(", ")
      val params = range.map(nn => s"java.util.function.Function<Fields, SqlExpr<T$nn>> f$nn").mkString(",\n            ")
      val tupleArgs = range.map(nn => s"f$nn.apply(fields)").mkString(", ")

      s"""    /**
         |     * Project to $n column${if (n > 1) "s" else ""}.
         |     */
         |    default <$tparamsDecl> SelectBuilder<Tuples.TupleExpr$n<$tparamsDecl>, Tuples.Tuple$n<$tparamsDecl>>
         |            map(
         |            $params) {
         |        return mapExpr(fields -> Tuples.of($tupleArgs));
         |    }""".stripMargin
    }

    val selectBuilderMapContents =
      s"""|package typr.dsl;
          |
          |/**
          | * Generated typed map method overloads for {@link SelectBuilder}.
          | * <p>
          | * These methods provide better type inference than the generic {@code mapExpr} method
          | * by having specific return types for each tuple arity.
          | * <p>
          | * Usage:
          | * <pre>
          | * // Project specific columns:
          | * builder.map(p -&gt; p.name(), p -&gt; p.age())
          | *
          | * // Or use the low-level generic mapExpr:
          | * builder.mapExpr(p -&gt; Tuples.of(p.name(), p.age()))
          | * </pre>
          | */
          |public interface SelectBuilderMap<Fields, Row> {
          |
          |    /**
          |     * The generic map method that implementations must provide.
          |     * Prefer using the overloaded {@code map} methods with individual column functions.
          |     */
          |    <NewFields extends Tuples.TupleExpr<NewRow>, NewRow extends Tuples.Tuple>
          |        SelectBuilder<NewFields, NewRow> mapExpr(java.util.function.Function<Fields, NewFields> projection);
          |
          |${mapMethods.mkString("\n\n")}
          |}
          |""".stripMargin

    targets.foreach { target =>
      FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("typr/dsl/Tuples.java"), tuplesContents)
      FileUtils.writeString(started.logger, Some("writing"), target.sources.resolve("typr/dsl/SelectBuilderMap.java"), selectBuilderMapContents)
    }
  }
}
