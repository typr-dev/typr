package typr.data;

import java.util.Arrays;

public record Vector(float[] values) {
  public static Vector parse(String value) {
    // Handle pgvector format: "[1.0,2.2,3.3]"
    var trimmed = value.trim();
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }
    if (trimmed.isEmpty()) {
      return new Vector(new float[0]);
    }
    var parts = trimmed.split(",");
    var ret = new float[parts.length];
    for (var i = 0; i < parts.length; i++) {
      ret[i] = Float.parseFloat(parts[i].trim());
    }
    return new Vector(ret);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Vector other) {
      return Arrays.equals(values, other.values);
    }
    return false;
  }

  /** Returns the vector in pgvector format: [1.0,2.2,3.3] */
  public String value() {
    var sb = new StringBuilder("[");
    for (var i = 0; i < values.length; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(values[i]);
    }
    sb.append("]");
    return sb.toString();
  }

  public Vector(String value) {
    this(Vector.parse(value).values);
  }
}
