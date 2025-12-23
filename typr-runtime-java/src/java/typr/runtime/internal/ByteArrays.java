package typr.runtime.internal;

/**
 * Utility methods for converting between primitive byte[] and boxed Byte[] arrays. This is needed
 * because Java records typically use boxed Byte[] while JDBC uses primitive byte[].
 */
public final class ByteArrays {
  private ByteArrays() {} // prevent instantiation

  /** Convert primitive byte[] to boxed Byte[] */
  public static Byte[] box(byte[] arr) {
    if (arr == null) return null;
    Byte[] result = new Byte[arr.length];
    for (int i = 0; i < arr.length; i++) result[i] = arr[i];
    return result;
  }

  /** Convert boxed Byte[] to primitive byte[] */
  public static byte[] unbox(Byte[] arr) {
    if (arr == null) return null;
    byte[] result = new byte[arr.length];
    for (int i = 0; i < arr.length; i++) result[i] = arr[i];
    return result;
  }
}
