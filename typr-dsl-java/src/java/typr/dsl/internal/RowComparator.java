package typr.dsl.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import typr.dsl.SortOrder;
import typr.dsl.Structure;

/**
 * Comparator for rows based on sort order specifications. Used in mock implementations to sort
 * results.
 */
public class RowComparator<Fields, Row> implements Comparator<Row> {
  private final Structure<Fields, Row> structure;
  private final List<SortOrder<?>> sortOrders;

  public RowComparator(Structure<Fields, Row> structure, List<SortOrder<?>> sortOrders) {
    this.structure = structure;
    this.sortOrders = sortOrders;
  }

  @Override
  public int compare(Row leftRow, Row rightRow) {
    for (SortOrder<?> sortOrder : sortOrders) {
      int result = compareBySortOrder(sortOrder, leftRow, rightRow);
      if (result != 0) {
        return result;
      }
    }
    return 0;
  }

  private <T> int compareBySortOrder(SortOrder<T> sortOrder, Row leftRow, Row rightRow) {
    Optional<T> leftValue = structure.untypedEval(sortOrder.expr(), leftRow);
    Optional<T> rightValue = structure.untypedEval(sortOrder.expr(), rightRow);

    Comparator<Optional<T>> comparator =
        (left, right) -> {
          if (left.isEmpty() && right.isEmpty()) return 0;
          if (left.isEmpty()) return sortOrder.nullsFirst() ? -1 : 1;
          if (right.isEmpty()) return sortOrder.nullsFirst() ? 1 : -1;

          // Use DummyComparator for the actual values
          int result = DummyComparator.getInstance().compare(left.get(), right.get());
          return sortOrder.ascending() ? result : -result;
        };

    return comparator.compare(leftValue, rightValue);
  }
}
