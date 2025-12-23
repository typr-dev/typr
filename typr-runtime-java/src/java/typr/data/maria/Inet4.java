package typr.data.maria;

import java.util.Objects;

/**
 * Wrapper for MariaDB INET4 type. Represents an IPv4 address.
 *
 * <p>MariaDB stores INET4 internally as a 4-byte binary value but returns it as a string in
 * dotted-decimal notation (e.g., "192.168.1.1").
 */
public record Inet4(String value) {
  public Inet4 {
    Objects.requireNonNull(value, "Inet4 value cannot be null");
  }

  @Override
  public String toString() {
    return value;
  }

  /** Parse an IPv4 address from a dotted-decimal string. */
  public static Inet4 parse(String value) {
    return new Inet4(value);
  }

  /** Get the address as an array of 4 bytes. */
  public byte[] toBytes() {
    String[] parts = value.split("\\.");
    if (parts.length != 4) {
      throw new IllegalStateException("Invalid IPv4 address: " + value);
    }
    byte[] bytes = new byte[4];
    for (int i = 0; i < 4; i++) {
      int octet = Integer.parseInt(parts[i]);
      if (octet < 0 || octet > 255) {
        throw new IllegalStateException("Invalid octet in IPv4 address: " + value);
      }
      bytes[i] = (byte) octet;
    }
    return bytes;
  }

  /** Create an Inet4 from a byte array. */
  public static Inet4 fromBytes(byte[] bytes) {
    if (bytes.length != 4) {
      throw new IllegalArgumentException("IPv4 address must be 4 bytes");
    }
    return new Inet4(
        String.format(
            "%d.%d.%d.%d", bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF));
  }
}
