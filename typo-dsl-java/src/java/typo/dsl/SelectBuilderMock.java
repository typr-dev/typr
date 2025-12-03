package typo.dsl;

import typo.dsl.internal.RowComparator;
import typo.runtime.Fragment;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Mock implementation of SelectBuilder for testing without a database.
 */
public class SelectBuilderMock<Fields, Row> implements SelectBuilder<Fields, Row> {
    private final Structure<Fields, Row> structure;
    private final Supplier<List<Row>> allRowsSupplier;
    private final SelectParams<Fields, Row> params;

    public SelectBuilderMock(
            Structure<Fields, Row> structure,
            Supplier<List<Row>> allRowsSupplier,
            SelectParams<Fields, Row> params) {
        this.structure = structure;
        this.allRowsSupplier = allRowsSupplier;
        this.params = params;
    }

    public SelectBuilderMock<Fields, Row> withPath(Path path) {
        return new SelectBuilderMock<>(structure.withPath(path), allRowsSupplier, params);
    }

    @Override
    public RenderCtx renderCtx() {
        // Mock doesn't generate SQL, but RenderCtx is needed for structure operations
        return RenderCtx.of(Dialect.POSTGRESQL);
    }
    
    @Override
    public Structure<Fields, Row> structure() {
        return structure;
    }
    
    @Override
    public SelectParams<Fields, Row> params() {
        return params;
    }
    
    @Override
    public SelectBuilder<Fields, Row> withParams(SelectParams<Fields, Row> newParams) {
        return new SelectBuilderMock<>(structure, allRowsSupplier, newParams);
    }
    
    @Override
    public List<Row> toList(Connection connection) {
        return applyParams(structure, allRowsSupplier.get(), params);
    }
    
    @Override
    public int count(Connection connection) {
        return toList(connection).size();
    }
    
    @Override
    public Optional<Fragment> sql() {
        return Optional.empty(); // Mock doesn't generate SQL
    }
    
    @Override
    public <Fields2, Row2> SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Row2>> 
            joinOn(SelectBuilder<Fields2, Row2> other, Function<Structure.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
        
        if (!(other instanceof SelectBuilderMock<Fields2, Row2> otherMock)) {
            throw new IllegalArgumentException("Cannot mix mock and SQL repos");
        }
        
        SelectBuilderMock<Fields, Row> self = this.withPath(Path.of("left"));
        SelectBuilderMock<Fields2, Row2> right = otherMock.withPath(Path.of("right"));
        
        Structure<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Row2>> newStructure = 
            self.structure.join(right.structure);
        
        Supplier<List<Structure.Tuple2<Row, Row2>>> newRowsSupplier = () -> {
            List<Structure.Tuple2<Row, Row2>> result = new ArrayList<>();
            List<Row> leftRows = self.toList(null);
            List<Row2> rightRows = right.toList(null);
            
            for (Row left : leftRows) {
                for (Row2 rightRow : rightRows) {
                    Structure.Tuple2<Row, Row2> tuple = Structure.Tuple2.of(left, rightRow);
                    Boolean matches = newStructure.untypedEval(pred.apply(newStructure.fields()), tuple)
                        .orElse(false);
                    if (matches) {
                        result.add(tuple);
                    }
                }
            }
            return result;
        };
        
        return new SelectBuilderMock<>(newStructure, newRowsSupplier, SelectParams.empty());
    }
    
    @Override
    public <Fields2, Row2> SelectBuilder<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Optional<Row2>>> 
            leftJoinOn(SelectBuilder<Fields2, Row2> other, Function<Structure.Tuple2<Fields, Fields2>, SqlExpr<Boolean>> pred) {
        
        if (!(other instanceof SelectBuilderMock<Fields2, Row2> otherMock)) {
            throw new IllegalArgumentException("Cannot mix mock and SQL repos");
        }
        
        SelectBuilderMock<Fields, Row> self = this.withPath(Path.of("left"));
        SelectBuilderMock<Fields2, Row2> right = otherMock.withPath(Path.of("right"));
        
        Structure<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Optional<Row2>>> newStructure = 
            self.structure.leftJoin(right.structure);
        
        Supplier<List<Structure.Tuple2<Row, Optional<Row2>>>> newRowsSupplier = () -> {
            List<Structure.Tuple2<Row, Optional<Row2>>> result = new ArrayList<>();
            List<Row> leftRows = self.toList(null);
            List<Row2> rightRows = right.toList(null);
            
            for (Row left : leftRows) {
                Optional<Row2> matchedRight = Optional.empty();
                
                for (Row2 rightRow : rightRows) {
                    Structure<Structure.Tuple2<Fields, Fields2>, Structure.Tuple2<Row, Row2>> tempStructure = 
                        self.structure.join(((SelectBuilderMock<Fields2, Row2>) right).structure);
                    Structure.Tuple2<Row, Row2> tempTuple = Structure.Tuple2.of(left, rightRow);
                    Boolean matches = tempStructure.untypedEval(pred.apply(tempStructure.fields()), tempTuple)
                        .orElse(false);
                    if (matches) {
                        matchedRight = Optional.of(rightRow);
                        break;
                    }
                }
                
                result.add(Structure.Tuple2.of(left, matchedRight));
            }
            return result;
        };
        
        return new SelectBuilderMock<>(newStructure, newRowsSupplier, SelectParams.empty());
    }
    
    /**
     * Apply the query parameters to filter, sort, and limit the rows.
     */
    public static <Fields, Row> List<Row> applyParams(
            Structure<Fields, Row> structure, 
            List<Row> rows, 
            SelectParams<Fields, Row> params) {
        
        var expanded = OrderByOrSeek.expand(structure.fields(), params);
        List<SqlExpr<Boolean>> filters = expanded.filters();
        List<SortOrder<?>> orderBys = expanded.orderBys();
        
        // Filter rows
        List<Row> filtered = new ArrayList<>();
        for (Row row : rows) {
            boolean includeRow = true;
            for (SqlExpr<Boolean> filter : filters) {
                Boolean passes = structure.untypedEval(filter, row).orElse(false);
                if (!passes) {
                    includeRow = false;
                    break;
                }
            }
            if (includeRow) {
                filtered.add(row);
            }
        }
        
        // Sort rows
        if (!orderBys.isEmpty()) {
            filtered.sort(new RowComparator<>(structure, orderBys));
        }
        
        // Apply offset and limit
        int start = params.offset().orElse(0);
        int end = params.limit()
            .map(limit -> Math.min(start + limit, filtered.size()))
            .orElse(filtered.size());
        
        if (start >= filtered.size()) {
            return new ArrayList<>();
        }
        
        return filtered.subList(start, end);
    }
}