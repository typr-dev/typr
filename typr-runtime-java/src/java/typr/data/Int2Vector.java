package typr.data;

import java.util.Arrays;

public record Int2Vector(short[] values) {
  public static Int2Vector parse(String value) {
    var values = value.split(" ");
    var ret = new short[values.length];
    for (var i = 0; i < values.length; i++) {
      ret[i] = Short.parseShort(values[i]);
    }
    return new Int2Vector(ret);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Int2Vector(var values1)) {
      if (values.length != values1.length) {
        return false;
      }
      for (var i = 0; i < values.length; i++) {
        if (values[i] != values1[i]) {
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

  public Int2Vector(String value) {
    this(Int2Vector.parse(value).values);
  }
}
