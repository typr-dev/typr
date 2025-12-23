package typr.data.maria;

import java.util.Objects;

/**
 * Wrapper for MariaDB INET6 type. Represents an IPv6 address (can also store IPv4 addresses in IPv6
 * format).
 *
 * <p>MariaDB stores INET6 internally as a 16-byte binary value but returns it as a string in
 * standard IPv6 notation (e.g., "::ffff:192.168.1.1" or "2001:db8::1").
 */
public record Inet6(String value) {
  public Inet6 {
    Objects.requireNonNull(value, "Inet6 value cannot be null");
  }

  @Override
  public String toString() {
    return value;
  }

  /** Parse an IPv6 address from a string. */
  public static Inet6 parse(String value) {
    return new Inet6(value);
  }

  /** Check if this is an IPv4-mapped IPv6 address (::ffff:x.x.x.x). */
  public boolean isIPv4Mapped() {
    return value.startsWith("::ffff:") || value.startsWith("::FFFF:");
  }

  /**
   * If this is an IPv4-mapped address, extract the IPv4 part. Returns null if not an IPv4-mapped
   * address.
   */
  public Inet4 toIPv4() {
    if (!isIPv4Mapped()) {
      return null;
    }
    String ipv4Part = value.substring(7); // Skip "::ffff:"
    return new Inet4(ipv4Part);
  }

  /** Create an IPv6 address from an IPv4 address (as IPv4-mapped IPv6). */
  public static Inet6 fromIPv4(Inet4 ipv4) {
    return new Inet6("::ffff:" + ipv4.value());
  }
}
