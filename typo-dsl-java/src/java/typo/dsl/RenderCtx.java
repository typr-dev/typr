package typo.dsl;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Context for rendering SQL expressions to fragments.
 * Calculates aliases for all unique list of Paths in a select query.
 * This is used to evaluate expressions from an SqlExpr when we have joined relations.
 */
public class RenderCtx {

    // Map from path to alias
    private final Map<List<Path>, String> aliasMap;
    // Dialect for quoting identifiers and type casts
    private final Dialect dialect;
    // Whether we're rendering in a join context (referencing CTEs vs actual tables)
    private final boolean inJoinContext;
    // When in join context, maps base aliases to the CTE name that contains them
    // e.g., if join_cte1 contains columns from publictitledperson0, maps publictitledperson0 -> join_cte1
    private final Map<String, String> aliasToCteMap;

    private RenderCtx(Map<List<Path>, String> aliasMap, Dialect dialect, boolean inJoinContext, Map<String, String> aliasToCteMap) {
        this.aliasMap = aliasMap;
        this.dialect = dialect;
        this.inJoinContext = inJoinContext;
        this.aliasToCteMap = aliasToCteMap;
    }

    /**
     * Get the dialect for this context.
     */
    public Dialect dialect() {
        return dialect;
    }

    /**
     * Check if we're rendering in a join context.
     * In join context, column references should use unique alias format (alias.alias_column)
     * to reference CTE outputs. In base context, column references should use
     * (alias)."column" to reference actual table columns.
     */
    public boolean inJoinContext() {
        return inJoinContext;
    }

    /**
     * Create a copy of this context with join context flag set.
     */
    public RenderCtx withJoinContext(boolean joinContext) {
        return new RenderCtx(aliasMap, dialect, joinContext, aliasToCteMap);
    }

    /**
     * Create a copy of this context with alias to CTE mapping for joins.
     * This maps base table aliases to the CTE name that actually contains those columns.
     */
    public RenderCtx withAliasToCteMap(Map<String, String> aliasToCteMap) {
        return new RenderCtx(aliasMap, dialect, inJoinContext, aliasToCteMap);
    }

    /**
     * Get the CTE name that contains the given alias's columns.
     * Used in join context to resolve the correct table reference.
     */
    public String resolveCte(String alias) {
        return aliasToCteMap.getOrDefault(alias, alias);
    }

    /**
     * Create a simple RenderCtx with just a dialect (no alias map).
     */
    public static RenderCtx of(Dialect dialect) {
        return new RenderCtx(Map.of(), dialect, false, Map.of());
    }

    /**
     * Create context from a SelectBuilder.
     */
    public static RenderCtx from(SelectBuilder<?, ?> builder, Dialect dialect) {
        if (!(builder instanceof SelectBuilderSql<?, ?> sqlBuilder)) {
            return new RenderCtx(Map.of(), dialect, false, Map.of());
        }
        return fromSql(sqlBuilder, dialect);
    }

    private static RenderCtx fromSql(SelectBuilderSql<?, ?> builder, Dialect dialect) {
        List<PathAndName> pathsAndNames = findPathsAndTableNames(builder);

        // Group by name and assign unique indexed aliases
        Map<String, List<List<Path>>> byName = pathsAndNames.stream()
            .collect(Collectors.groupingBy(
                PathAndName::name,
                LinkedHashMap::new,
                Collectors.mapping(PathAndName::path, Collectors.toList())
            ));

        Map<List<Path>, String> aliasMap = new HashMap<>();

        for (Map.Entry<String, List<List<Path>>> entry : byName.entrySet()) {
            String baseName = entry.getKey();
            List<List<Path>> paths = entry.getValue();

            // Sort paths for deterministic alias assignment
            paths.sort(RenderCtx::comparePaths);

            for (int i = 0; i < paths.size(); i++) {
                aliasMap.put(paths.get(i), baseName + i);
            }
        }

        return new RenderCtx(aliasMap, dialect, false, Map.of());
    }

    private static List<PathAndName> findPathsAndTableNames(SelectBuilderSql<?, ?> builder) {
        List<PathAndName> result = new ArrayList<>();
        findPathsAndTableNamesRecursive(builder, result);
        return result;
    }

    private static void findPathsAndTableNamesRecursive(SelectBuilderSql<?, ?> builder, List<PathAndName> result) {
        if (builder instanceof SelectBuilderSql.Relation<?, ?> relation) {
            // Extract table name and filter to alphanumeric chars
            String tableName = filterAlphanumeric(relation.name());
            result.add(new PathAndName(relation.structure().path(), tableName));
        } else if (builder instanceof SelectBuilderSql.TableJoin<?, ?, ?, ?> join) {
            // Add entry for the join itself
            result.add(new PathAndName(join.structure().path(), "join_cte"));
            // Recursively process left and right
            findPathsAndTableNamesRecursive(join.left(), result);
            findPathsAndTableNamesRecursive(join.right(), result);
        } else if (builder instanceof SelectBuilderSql.TableLeftJoin<?, ?, ?, ?> leftJoin) {
            // Add entry for the left join itself
            result.add(new PathAndName(leftJoin.structure().path(), "left_join_cte"));
            // Recursively process left and right
            findPathsAndTableNamesRecursive(leftJoin.left(), result);
            findPathsAndTableNamesRecursive(leftJoin.right(), result);
        }
    }

    private static String filterAlphanumeric(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int comparePaths(List<Path> a, List<Path> b) {
        int minLen = Math.min(a.size(), b.size());
        for (int i = 0; i < minLen; i++) {
            int cmp = comparePath(a.get(i), b.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.size(), b.size());
    }

    private static int comparePath(Path a, Path b) {
        // Define ordering: LeftInJoin < Named < RightInJoin
        int aOrd = pathOrdinal(a);
        int bOrd = pathOrdinal(b);
        if (aOrd != bOrd) return Integer.compare(aOrd, bOrd);

        // If both are Named, compare by value
        if (a instanceof Path.Named(String value) && b instanceof Path.Named(String value1)) {
            return value.compareTo(value1);
        }
        return 0;
    }

    private static int pathOrdinal(Path p) {
        return switch (p) {
            case Path.LeftInJoin l -> 0;
            case Path.Named n -> 1;
            case Path.RightInJoin r -> 2;
        };
    }

    /**
     * Get alias for a path. Returns Optional.empty() if path not found.
     */
    public Optional<String> alias(List<Path> path) {
        return Optional.ofNullable(aliasMap.get(path));
    }

    /**
     * Get alias for a single path element.
     */
    public Optional<String> alias(Path path) {
        return alias(List.of(path));
    }

    // Internal record to hold path and table name pairs
    private record PathAndName(List<Path> path, String name) {}
}
