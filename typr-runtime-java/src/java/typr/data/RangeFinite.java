package typr.data;

public record RangeFinite<T extends Comparable<? super T>>(
    RangeBound.Finite<T> from, RangeBound.Finite<T> to) {
  public Range<T> asRange() {
    return new Range.NonEmpty<>(from, to);
  }

  public boolean contains(T value) {
    var withinRangeLeft =
        switch (from) {
          case RangeBound.Finite.Open<T> x -> value.compareTo(x.value()) > 0;
          case RangeBound.Finite.Closed<T> x -> value.compareTo(x.value()) >= 0;
        };
    var withRangeRight =
        switch (to) {
          case RangeBound.Finite.Open<T> x -> value.compareTo(x.value()) < 0;
          case RangeBound.Finite.Closed<T> x -> value.compareTo(x.value()) <= 0;
        };
    return withinRangeLeft && withRangeRight;
  }
}
