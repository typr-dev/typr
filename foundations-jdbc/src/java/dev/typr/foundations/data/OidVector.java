package dev.typr.foundations.data;

import java.util.Arrays;

public record OidVector(int[] values) {
  public static dev.typr.foundations.data.OidVector parse(String value) {
    var values = value.split(" ");
    var ret = new int[values.length];
    for (var i = 0; i < values.length; i++) {
      ret[i] = Integer.parseInt(values[i]);
    }
    return new dev.typr.foundations.data.OidVector(ret);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof dev.typr.foundations.data.OidVector other) {
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
    this(dev.typr.foundations.data.OidVector.parse(value).values);
  }
}
