package com.example.grpc;

public enum Priority {
  PRIORITY_UNSPECIFIED("PRIORITY_UNSPECIFIED"),
  PRIORITY_LOW("PRIORITY_LOW"),
  PRIORITY_MEDIUM("PRIORITY_MEDIUM"),
  PRIORITY_HIGH("PRIORITY_HIGH"),
  PRIORITY_CRITICAL("PRIORITY_CRITICAL");
  final java.lang.String value;

  public java.lang.String value() {
    return value;
  }

  Priority(java.lang.String value) {
    this.value = value;
  }

  public static final java.lang.String Names =
      java.util.Arrays.stream(Priority.values())
          .map(x -> x.value)
          .collect(java.util.stream.Collectors.joining(", "));
  public static final java.util.Map<java.lang.String, Priority> ByName =
      java.util.Arrays.stream(Priority.values())
          .collect(java.util.stream.Collectors.toMap(n -> n.value, n -> n));

  public Integer toValue() {
    if (this.toString().equals("PRIORITY_UNSPECIFIED")) {
      return 0;
    } else if (this.toString().equals("PRIORITY_LOW")) {
      return 1;
    } else if (this.toString().equals("PRIORITY_MEDIUM")) {
      return 2;
    } else if (this.toString().equals("PRIORITY_HIGH")) {
      return 3;
    } else if (this.toString().equals("PRIORITY_CRITICAL")) {
      return 4;
    } else {
      return 0;
    }
  }

  public static Priority fromValue(Integer value) {
    if (value == 0) {
      return Priority.PRIORITY_UNSPECIFIED;
    } else if (value == 1) {
      return Priority.PRIORITY_LOW;
    } else if (value == 2) {
      return Priority.PRIORITY_MEDIUM;
    } else if (value == 3) {
      return Priority.PRIORITY_HIGH;
    } else if (value == 4) {
      return Priority.PRIORITY_CRITICAL;
    } else {
      throw new IllegalArgumentException("Unknown enum value: " + value);
    }
  }
  ;

  public static Priority force(java.lang.String str) {
    if (ByName.containsKey(str)) {
      return ByName.get(str);
    } else {
      throw new RuntimeException(
          "'" + str + "' does not match any of the following legal values: " + Names);
    }
  }
}
