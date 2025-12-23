package typr.dsl.internal;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import typr.dsl.SqlExpr;

/**
 * Internal representation of a query as a tree structure. This allows optimizations (predicate
 * pushdown, join elision) before SQL rendering.
 */
public sealed interface LogicalPlan {

  /** All source IDs reachable from this plan node. */
  Set<SourceId> allSources();

  /** Output schema: source ID to list of column metadata. */
  Map<SourceId, List<ColumnMeta>> outputSchema();

  /** Unique identifier for a source (table or subquery) within a query. */
  record SourceId(String id) {
    private static final AtomicInteger counter = new AtomicInteger();

    public static SourceId next() {
      return new SourceId("s" + counter.getAndIncrement());
    }
  }

  /** Column metadata. */
  record ColumnMeta(
      String name,
      SqlExpr.FieldLike<?, ?> field,
      Optional<String> readCast,
      Optional<String> writeCast) {}

  /** Reference to a column in a source. */
  record ColRef(SourceId source, String column) {}

  /** Join type. */
  enum JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL
  }

  /** Untyped expression tree for internal processing. */
  sealed interface Expr {
    /** Which sources does this expression reference? */
    Set<SourceId> sources();

    record Col(ColRef ref) implements Expr {
      @Override
      public Set<SourceId> sources() {
        return Set.of(ref.source());
      }
    }

    record Lit(Object value, Object dbType) implements Expr {
      @Override
      public Set<SourceId> sources() {
        return Set.of();
      }
    }

    record BinOp(Expr left, String op, Expr right) implements Expr {
      @Override
      public Set<SourceId> sources() {
        Set<SourceId> result = new HashSet<>(left.sources());
        result.addAll(right.sources());
        return result;
      }
    }

    record UnaryOp(String op, Expr arg) implements Expr {
      @Override
      public Set<SourceId> sources() {
        return arg.sources();
      }
    }

    record Call(String fn, List<Expr> args) implements Expr {
      @Override
      public Set<SourceId> sources() {
        Set<SourceId> result = new HashSet<>();
        for (Expr arg : args) {
          result.addAll(arg.sources());
        }
        return result;
      }
    }

    record IsNull(Expr arg) implements Expr {
      @Override
      public Set<SourceId> sources() {
        return arg.sources();
      }
    }

    /** Wraps a typed SqlExpr for cases where we don't need to decompose it. */
    record Wrapped(SqlExpr<?> expr, Set<SourceId> sources) implements Expr {}
  }

  /** A scan of a single table. */
  record Scan(SourceId id, String schemaName, String tableName, List<ColumnMeta> columns)
      implements LogicalPlan {
    @Override
    public Set<SourceId> allSources() {
      return Set.of(id);
    }

    @Override
    public Map<SourceId, List<ColumnMeta>> outputSchema() {
      return Map.of(id, columns);
    }
  }

  /** A join of two plans. */
  record Join(LogicalPlan left, LogicalPlan right, JoinType type, Expr on) implements LogicalPlan {
    @Override
    public Set<SourceId> allSources() {
      Set<SourceId> result = new HashSet<>(left.allSources());
      result.addAll(right.allSources());
      return result;
    }

    @Override
    public Map<SourceId, List<ColumnMeta>> outputSchema() {
      Map<SourceId, List<ColumnMeta>> result = new HashMap<>(left.outputSchema());
      result.putAll(right.outputSchema());
      return result;
    }
  }

  /** A multiset aggregation (one-to-many join that produces JSON array). */
  record Multiset(LogicalPlan parent, LogicalPlan child, Expr correlation, String outputColumnName)
      implements LogicalPlan {
    @Override
    public Set<SourceId> allSources() {
      // Multiset's child sources are "hidden" inside the aggregation
      return parent.allSources();
    }

    @Override
    public Map<SourceId, List<ColumnMeta>> outputSchema() {
      // Parent schema plus a synthetic JSON column
      return parent.outputSchema();
    }
  }

  /** A filter (WHERE clause). */
  record Filter(LogicalPlan input, Expr predicate) implements LogicalPlan {
    @Override
    public Set<SourceId> allSources() {
      return input.allSources();
    }

    @Override
    public Map<SourceId, List<ColumnMeta>> outputSchema() {
      return input.outputSchema();
    }
  }

  /** A projection (SELECT specific columns/expressions). */
  record Project(LogicalPlan input, List<Projection> projections) implements LogicalPlan {
    @Override
    public Set<SourceId> allSources() {
      return input.allSources();
    }

    @Override
    public Map<SourceId, List<ColumnMeta>> outputSchema() {
      // After projection, the schema is the projections themselves
      // We use a synthetic source ID for the projection output
      return input.outputSchema(); // TODO: Should this be different?
    }
  }

  /** A single projected column or expression. */
  record Projection(String outputName, Expr expr, Object dbType) {}

  /** A sort operation. */
  record Sort(LogicalPlan input, List<SortSpec> specs) implements LogicalPlan {
    @Override
    public Set<SourceId> allSources() {
      return input.allSources();
    }

    @Override
    public Map<SourceId, List<ColumnMeta>> outputSchema() {
      return input.outputSchema();
    }
  }

  /** Sort specification. */
  record SortSpec(Expr expr, boolean ascending, boolean nullsFirst) {}

  /** Limit and offset. */
  record Limit(LogicalPlan input, OptionalInt limit, OptionalInt offset) implements LogicalPlan {
    @Override
    public Set<SourceId> allSources() {
      return input.allSources();
    }

    @Override
    public Map<SourceId, List<ColumnMeta>> outputSchema() {
      return input.outputSchema();
    }
  }
}
