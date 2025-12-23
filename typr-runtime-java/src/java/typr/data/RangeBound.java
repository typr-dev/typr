package typr.data;

public sealed interface RangeBound<T> permits RangeBound.Infinite, RangeBound.Finite {
  RangeBound<Object> infinite = new Infinite<>();

  @SuppressWarnings("unchecked")
  static <T> RangeBound<T> infinite() {
    return (RangeBound<T>) infinite;
  }

  final class Infinite<T> implements RangeBound<T> {}

  sealed interface Finite<T> extends RangeBound<T> permits Open, Closed {
    T value();
  }

  record Open<T>(T value) implements Finite<T> {}

  record Closed<T>(T value) implements Finite<T> {}
}
