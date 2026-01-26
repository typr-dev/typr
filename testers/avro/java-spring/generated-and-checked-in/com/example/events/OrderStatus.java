package com.example.events;

public enum OrderStatus {
  PENDING("PENDING"),
  CONFIRMED("CONFIRMED"),
  SHIPPED("SHIPPED"),
  DELIVERED("DELIVERED"),
  CANCELLED("CANCELLED");
  final java.lang.String value;

  public java.lang.String value() {
    return value;
  }

  OrderStatus(java.lang.String value) {
    this.value = value;
  }

  public static final java.lang.String Names =
      java.util.Arrays.stream(OrderStatus.values())
          .map(x -> x.value)
          .collect(java.util.stream.Collectors.joining(", "));
  public static final java.util.Map<java.lang.String, OrderStatus> ByName =
      java.util.Arrays.stream(OrderStatus.values())
          .collect(java.util.stream.Collectors.toMap(n -> n.value, n -> n));

  public static OrderStatus force(java.lang.String str) {
    if (ByName.containsKey(str)) {
      return ByName.get(str);
    } else {
      throw new RuntimeException(
          "'" + str + "' does not match any of the following legal values: " + Names);
    }
  }
}
