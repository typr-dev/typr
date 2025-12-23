package typr.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * PostgreSQL range type - represents a range of values. Ranges can be either empty (containing no
 * values) or have bounds.
 *
 * <p>Use the typed factory methods to create ranges. Discrete types (integers, dates) are
 * automatically normalized to PostgreSQL's canonical [) form.
 */
public sealed interface Range<T extends Comparable<? super T>> permits Range.Empty, Range.NonEmpty {

  /** An empty range - contains no values. */
  record Empty<T extends Comparable<? super T>>() implements Range<T> {
    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public boolean contains(T value) {
      return false;
    }

    @Override
    public Optional<RangeFinite<T>> finite() {
      return Optional.empty();
    }

    @Override
    public String toString() {
      return "empty";
    }
  }

  /** A non-empty range with lower and upper bounds. */
  record NonEmpty<T extends Comparable<? super T>>(RangeBound<T> from, RangeBound<T> to)
      implements Range<T> {
    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public Optional<RangeFinite<T>> finite() {
      if (from instanceof RangeBound.Finite<T> && to instanceof RangeBound.Finite<T>) {
        return Optional.of(
            new RangeFinite<>((RangeBound.Finite<T>) from, (RangeBound.Finite<T>) to));
      }
      return Optional.empty();
    }

    @Override
    public String toString() {
      var left =
          switch (from) {
            case RangeBound.Infinite<T> x -> "(";
            case RangeBound.Finite.Open<T> x -> "(" + x.value();
            case RangeBound.Finite.Closed<T> x -> "[" + x.value();
          };
      var right =
          switch (to) {
            case RangeBound.Infinite<T> x -> ")";
            case RangeBound.Finite.Open<T> x -> x.value() + ")";
            case RangeBound.Finite.Closed<T> x -> x.value() + "]";
          };
      return left + "," + right;
    }

    @Override
    public boolean contains(T value) {
      var withinRangeLeft =
          switch (from) {
            case RangeBound.Infinite<T> x -> true;
            case RangeBound.Finite.Open<T> x -> value.compareTo(x.value()) > 0;
            case RangeBound.Finite.Closed<T> x -> value.compareTo(x.value()) >= 0;
          };
      var withRangeRight =
          switch (to) {
            case RangeBound.Infinite<T> x -> true;
            case RangeBound.Finite.Open<T> x -> value.compareTo(x.value()) < 0;
            case RangeBound.Finite.Closed<T> x -> value.compareTo(x.value()) <= 0;
          };
      return withinRangeLeft && withRangeRight;
    }
  }

  boolean isEmpty();

  boolean contains(T value);

  Optional<RangeFinite<T>> finite();

  // ==================== Factory methods ====================

  /** Empty range - contains no values */
  static <T extends Comparable<? super T>> Range<T> empty() {
    return new Empty<>();
  }

  // ----- Discrete types (normalized to canonical [) form) -----

  /** int4range - normalized to [) form */
  static Range<Integer> int4(RangeBound<Integer> from, RangeBound<Integer> to) {
    return normalized(from, to, i -> i + 1);
  }

  /** int8range - normalized to [) form */
  static Range<Long> int8(RangeBound<Long> from, RangeBound<Long> to) {
    return normalized(from, to, i -> i + 1);
  }

  /** daterange - normalized to [) form */
  static Range<LocalDate> date(RangeBound<LocalDate> from, RangeBound<LocalDate> to) {
    return normalized(from, to, d -> d.plusDays(1));
  }

  // ----- Continuous types (no normalization) -----

  /** numrange - not normalized (continuous type) */
  static Range<BigDecimal> numeric(RangeBound<BigDecimal> from, RangeBound<BigDecimal> to) {
    return new NonEmpty<>(from, to);
  }

  /** tsrange - not normalized (continuous type) */
  static Range<LocalDateTime> timestamp(
      RangeBound<LocalDateTime> from, RangeBound<LocalDateTime> to) {
    return new NonEmpty<>(from, to);
  }

  /** tstzrange - not normalized (continuous type) */
  static Range<Instant> timestamptz(RangeBound<Instant> from, RangeBound<Instant> to) {
    return new NonEmpty<>(from, to);
  }

  // ==================== Factory function references for parser ====================

  /** Factory for int4range */
  BiFunction<RangeBound<Integer>, RangeBound<Integer>, Range<Integer>> INT4 = Range::int4;

  /** Factory for int8range */
  BiFunction<RangeBound<Long>, RangeBound<Long>, Range<Long>> INT8 = Range::int8;

  /** Factory for daterange */
  BiFunction<RangeBound<LocalDate>, RangeBound<LocalDate>, Range<LocalDate>> DATE = Range::date;

  /** Factory for numrange */
  BiFunction<RangeBound<BigDecimal>, RangeBound<BigDecimal>, Range<BigDecimal>> NUMERIC =
      Range::numeric;

  /** Factory for tsrange */
  BiFunction<RangeBound<LocalDateTime>, RangeBound<LocalDateTime>, Range<LocalDateTime>> TIMESTAMP =
      Range::timestamp;

  /** Factory for tstzrange */
  BiFunction<RangeBound<Instant>, RangeBound<Instant>, Range<Instant>> TIMESTAMPTZ =
      Range::timestamptz;

  // ==================== Internal helpers ====================

  /**
   * Normalize a discrete range to PostgreSQL canonical form [). - (a,b) -> [a+1,b) - (a,b] ->
   * [a+1,b+1) - [a,b] -> [a,b+1) - [a,b) -> [a,b) (already canonical)
   */
  private static <T extends Comparable<? super T>> Range<T> normalized(
      RangeBound<T> from, RangeBound<T> to, UnaryOperator<T> step) {
    // Normalize lower bound: (a -> [a+1
    RangeBound<T> normalizedFrom =
        switch (from) {
          case RangeBound.Infinite<T> i -> i;
          case RangeBound.Finite.Closed<T> c -> c; // already canonical
          case RangeBound.Finite.Open<T> o -> new RangeBound.Closed<>(step.apply(o.value()));
        };

    // Normalize upper bound: b] -> b+1)
    RangeBound<T> normalizedTo =
        switch (to) {
          case RangeBound.Infinite<T> i -> i;
          case RangeBound.Finite.Open<T> o -> o; // already canonical
          case RangeBound.Finite.Closed<T> c -> new RangeBound.Open<>(step.apply(c.value()));
        };

    // Check for empty range: if lower >= upper after normalization, it's empty
    if (normalizedFrom instanceof RangeBound.Finite<T> finiteFrom
        && normalizedTo instanceof RangeBound.Finite<T> finiteTo) {
      T fromVal = finiteFrom.value();
      T toVal = finiteTo.value();
      if (fromVal.compareTo(toVal) >= 0) {
        return empty();
      }
    }

    return new NonEmpty<>(normalizedFrom, normalizedTo);
  }
}
