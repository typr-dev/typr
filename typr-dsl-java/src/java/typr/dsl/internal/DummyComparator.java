package typr.dsl.internal;

import java.util.Comparator;
import java.util.Optional;

/** A generic comparator that can compare any objects. Used for mock implementations and testing. */
public class DummyComparator implements Comparator<Object> {

  private static final DummyComparator INSTANCE = new DummyComparator();

  public static DummyComparator getInstance() {
    return INSTANCE;
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public int compare(Object x, Object y) {
    if (x == y) return 0;
    if (x == null) return -1;
    if (y == null) return 1;

    // Handle Optional specially
    if (x instanceof Optional && y instanceof Optional) {
      Optional<?> ox = (Optional<?>) x;
      Optional<?> oy = (Optional<?>) y;

      if (ox.isEmpty() && oy.isEmpty()) return 0;
      if (ox.isEmpty()) return -1;
      if (oy.isEmpty()) return 1;

      return compare(ox.get(), oy.get());
    }

    // Handle Comparable objects
    if (x instanceof Comparable && y instanceof Comparable) {
      try {
        return ((Comparable) x).compareTo(y);
      } catch (ClassCastException e) {
        // Fall through to string comparison
      }
    }

    // Fall back to string comparison
    return x.toString().compareTo(y.toString());
  }

  /** Get a typed comparator. */
  @SuppressWarnings("unchecked")
  public static <T> Comparator<T> typed() {
    return (Comparator<T>) INSTANCE;
  }

  /** Get a comparator for Optional values. */
  public static <T> Comparator<Optional<T>> optional() {
    return (ox, oy) -> {
      if (ox.isEmpty() && oy.isEmpty()) return 0;
      if (ox.isEmpty()) return -1;
      if (oy.isEmpty()) return 1;
      return getInstance().compare(ox.get(), oy.get());
    };
  }
}
