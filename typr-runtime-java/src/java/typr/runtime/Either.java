package typr.runtime;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface Either<L, R> permits Either.Left, Either.Right {
  static <L, R> Either<L, R> left(L l) {
    return new Left<>(l);
  }

  static <L, R> Either<L, R> right(R r) {
    return new Right<>(r);
  }

  default boolean isLeft() {
    return this instanceof Left;
  }

  default boolean isRight() {
    return this instanceof Right;
  }

  default <RR> Either<L, RR> map(Function<R, RR> f) {
    return switch (this) {
      case Left<L, R> l -> left(l.value());
      case Right<L, R> r -> right(f.apply(r.value()));
    };
  }

  default <LL> Either<LL, R> mapLeft(Function<L, LL> f) {
    return switch (this) {
      case Left<L, R> l -> left(f.apply(l.value()));
      case Right<L, R> r -> right(r.value());
    };
  }

  default Optional<R> asOptional() {
    return switch (this) {
      case Left<L, R> l -> Optional.empty();
      case Right<L, R> r -> Optional.of(r.value());
    };
  }

  default R getOrElse(Supplier<R> defaultValue) {
    return switch (this) {
      case Left<L, R> l -> defaultValue.get();
      case Right<L, R> r -> r.value();
    };
  }

  default Either<R, L> swap() {
    return switch (this) {
      case Left<L, R> l -> right(l.value());
      case Right<L, R> r -> left(r.value());
    };
  }

  default <T> T fold(Function<L, T> leftMapper, Function<R, T> rightMapper) {
    return switch (this) {
      case Left<L, R> l -> leftMapper.apply(l.value());
      case Right<L, R> r -> rightMapper.apply(r.value());
    };
  }

  final class Left<L, R> implements Either<L, R> {
    private final L value;

    public Left(L value) {
      this.value = value;
    }

    public L value() {
      return value;
    }

    public String toString() {
      return "Left(" + value + ")";
    }
  }

  final class Right<L, R> implements Either<L, R> {
    private final R value;

    public Right(R value) {
      this.value = value;
    }

    public R value() {
      return value;
    }

    public String toString() {
      return "Right(" + value + ")";
    }
  }
}
