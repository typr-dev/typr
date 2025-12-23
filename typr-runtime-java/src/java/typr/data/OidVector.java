package typr.data;

import java.util.Arrays;

public record OidVector(int[] values) {
  public static typr.data.OidVector parse(String value) {
    var values = value.split(" ");
    var ret = new int[values.length];
    for (var i = 0; i < values.length; i++) {
      ret[i] = Integer.parseInt(values[i]);
    }
    return new typr.data.OidVector(ret);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof typr.data.OidVector other) {
      if (values.length != other.values.length) {
        return false;
      }
      for (var i = 0; i < values.length; i++) {
        if (values[i] != other.values[i]) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public String value() {
    var sb = new StringBuilder();
    for (var i = 0; i < values.length; i++) {
      if (i > 0) {
        sb.append(" ");
      }
      sb.append(values[i]);
    }
    return sb.toString();
  }

  public OidVector(String value) {
    this(typr.data.OidVector.parse(value).values);
  }
}
