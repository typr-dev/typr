package dev.typr.foundations.internal;

import java.util.Random;
import java.util.UUID;

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

  public static UUID randomUUID(Random random) {
    byte[] bytes = new byte[16];
    random.nextBytes(bytes);
    return UUID.nameUUIDFromBytes(bytes);
  }
}
