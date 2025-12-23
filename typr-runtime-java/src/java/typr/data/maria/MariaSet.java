package typr.data.maria;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wrapper for MariaDB SET type.
 *
 * <p>MariaDB SET is a string object that can have zero or more values, each chosen from a list of
 * permitted values. SET values are returned by JDBC as comma-separated strings (e.g.,
 * "email,sms,push").
 *
 * <p>This wrapper provides type-safe access to the individual values.
 */
public final class MariaSet {
  private final Set<String> values;

  private MariaSet(Set<String> values) {
    this.values = Collections.unmodifiableSet(new LinkedHashSet<>(values));
  }

  /** Create a MariaSet from a comma-separated string (as returned by JDBC). */
  public static MariaSet fromString(String commaSeparated) {
    if (commaSeparated == null || commaSeparated.isEmpty()) {
      return new MariaSet(Collections.emptySet());
    }
    Set<String> values =
        Arrays.stream(commaSeparated.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return new MariaSet(values);
  }

  /** Create a MariaSet from individual values. */
  public static MariaSet of(String... values) {
    return new MariaSet(new LinkedHashSet<>(Arrays.asList(values)));
  }

  /** Create a MariaSet from a Set of values. */
  public static MariaSet of(Set<String> values) {
    return new MariaSet(values);
  }

  /** Create an empty MariaSet. */
  public static MariaSet empty() {
    return new MariaSet(Collections.emptySet());
  }

  /** Get the values as an unmodifiable Set. */
  public Set<String> values() {
    return values;
  }

  /** Check if a value is present in the set. */
  public boolean contains(String value) {
    return values.contains(value);
  }

  /** Check if the set is empty. */
  public boolean isEmpty() {
    return values.isEmpty();
  }

  /** Get the number of values in the set. */
  public int size() {
    return values.size();
  }

  /** Convert to a comma-separated string (for JDBC). */
  public String toCommaSeparated() {
    return String.join(",", values);
  }

  @Override
  public String toString() {
    return toCommaSeparated();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MariaSet mariaSet = (MariaSet) o;
    return Objects.equals(values, mariaSet.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(values);
  }
}
