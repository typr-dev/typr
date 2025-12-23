package typr.runtime.internal;

import java.lang.reflect.Array;
import java.util.function.Function;

public class arrayMap {
  @SuppressWarnings("unchecked")
  public static <A, B> B[] map(A[] arr, Function<A, B> f, Class<B> clazz) {
    B[] result = (B[]) Array.newInstance(clazz, arr.length);
    for (int i = 0; i < arr.length; i++) {
      result[i] = f.apply(arr[i]);
    }
    return result;
  }
}
