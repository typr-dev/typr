package typr.runtime.internal;

import java.util.Random;

public class RandomHelper {
  private static final String ALPHANUMERIC =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  public static String alphanumeric(Random random, int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
    }
    return sb.toString();
  }
}
