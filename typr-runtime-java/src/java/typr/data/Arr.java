package typr.data;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

public class Arr<A> {
  public static final Arr<?> EMPTY = new Arr<>(new Object[0], new int[0], new int[] {0});

  private final Object[] data;
  private final int[] extent;
  private final int[] _offsets;

  public Arr(Object[] data, int[] extent) {
    this(data, extent, _offsets(extent));
  }

  private Arr(Object[] data, int[] extent, int[] _offsets) {
    this.data = data;
    this.extent = extent;
    this._offsets = _offsets;
  }

  public Optional<Arr<A>> reshape(int... dimensions) {
    if (dimensions.length == 0) {
      if (extent.length == 0) return Optional.of(this);
      return Optional.of(new Arr<>(data, new int[0]));
    }
    int product = IntStream.of(dimensions).reduce(1, (a, b) -> a * b);
    if (product == data.length) return Optional.of(new Arr<>(data, dimensions));
    else return Optional.empty();
  }

  public int size() {
    return data.length;
  }

  public boolean isEmpty() {
    return data.length == 0;
  }

  public int[] dimensions() {
    return Arrays.copyOf(extent, extent.length);
  }

  public Optional<A> get(int... ords) {
    if (ords.length == extent.length) {
      int a = 0;
      for (int i = 0; i < extent.length; i++) {
        int ii = ords[i];
        if (ii >= 0 && ii < extent[i]) {
          a += ords[i] * _offsets[i];
        } else {
          return Optional.empty();
        }
      }
      return Optional.of((A) data[a]);
    }
    return Optional.empty();
  }

  public String encode(Function<A, String> f, char delim) {
    StringBuilder sb = new StringBuilder();
    if (extent.length == 0) return "{}";
    sb.append('{');
    encode_go(0, 0, sb);
    sb.append('}');
    return sb.toString();
  }

  public String encode(Function<A, String> f) {
    return encode(f, ',');
  }

  private static void encode_appendEscaped(StringBuilder sb, String s) {
    sb.append('"');
    s.chars()
        .forEach(
            c -> {
              if (c == '"') sb.append("\\\"");
              else if (c == '\\') sb.append("\\\\");
              else sb.append((char) c);
            });
    sb.append('"');
  }

  private void encode_go(int offset, int ie, StringBuilder sb) {
    boolean v = ie == extent.length - 1;
    int o = _offsets[ie];
    for (int i = 0; i < extent[ie]; i++) {
      if (i > 0) sb.append(',');
      if (v) {
        encode_appendEscaped(sb, data[offset + i].toString());
      } else {
        sb.append('{');
        encode_go(offset + o * i, ie + 1, sb);
        sb.append('}');
      }
    }
  }

  @Override
  public String toString() {
    return "Arr(" + encode(Object::toString, ',') + ")";
  }

  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Arr<?> other)) return false;
    return Arrays.equals(extent, other.extent) && Arrays.equals(data, other.data);
  }

  public int hashCode() {
    int result = 1;
    result = result * 59 + Arrays.hashCode(extent);
    result = result * 59 + Arrays.hashCode(data);
    return result;
  }

  public <B> B foldLeft(B b, BiFunction<B, A, B> f) {
    B acc = b;
    for (Object a : data) {
      acc = f.apply(acc, (A) a);
    }
    return acc;
  }

  public <B> Arr<B> map(Function<A, B> f) {
    Object[] newData = new Object[data.length];
    for (int i = 0; i < data.length; i++) {
      newData[i] = f.apply((A) data[i]);
    }
    return new Arr<>(newData, extent);
  }

  public void forEach(Function<A, Void> f) {
    for (Object a : data) {
      f.apply((A) a);
    }
  }

  @SafeVarargs
  public static <A> Arr<A> of(A... as) {
    return new Arr<>(as, new int[] {as.length});
  }

  public static <A> Arr<A> ofCollection(Collection<A> as) {
    return new Arr<>(as.toArray(), new int[] {as.size()});
  }

  public static <A> Arr<A> empty() {
    return (Arr<A>) EMPTY;
  }

  static int[] _offsets(int[] extent) {
    int[] ret = new int[extent.length];
    int o = 1;
    for (int i = extent.length - 1; i >= 0; i--) {
      ret[i] = o;
      o *= extent[i];
    }

    return ret;
  }
}
