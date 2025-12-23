package typr.dsl;

// Tuple types for joining - record implementation for proper equals/hashCode
public record Tuple2<T1, T2>(T1 _1, T2 _2) {
  public static <T1, T2> Tuple2<T1, T2> of(T1 first, T2 second) {
    return new Tuple2<>(first, second);
  }
}
