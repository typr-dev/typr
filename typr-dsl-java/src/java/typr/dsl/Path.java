package typr.dsl;

import java.util.Objects;

/** Represents a path segment in a SQL query, typically used for table aliases and join tracking. */
public sealed interface Path permits Path.LeftInJoin, Path.RightInJoin, Path.Named {

  /** Left side of a join operation. */
  record LeftInJoin() implements Path {
    @Override
    public String toString() {
      return "LeftInJoin";
    }
  }

  /** Right side of a join operation. */
  record RightInJoin() implements Path {
    @Override
    public String toString() {
      return "RightInJoin";
    }
  }

  /** Named path with a specific value. */
  record Named(String value) implements Path {
    public Named {
      Objects.requireNonNull(value, "Path value cannot be null");
      if (value.isEmpty()) {
        throw new IllegalArgumentException("Path value cannot be empty");
      }
    }

    @Override
    public String toString() {
      return value;
    }
  }

  // Constants for common paths
  Path LEFT_IN_JOIN = new LeftInJoin();
  Path RIGHT_IN_JOIN = new RightInJoin();

  static Path of(String value) {
    return new Named(value);
  }
}
