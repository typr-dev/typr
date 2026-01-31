package com.example.grpc;

public enum OrderStatus {
  ORDER_STATUS_UNSPECIFIED("ORDER_STATUS_UNSPECIFIED"),
  ORDER_STATUS_PENDING("ORDER_STATUS_PENDING"),
  ORDER_STATUS_PROCESSING("ORDER_STATUS_PROCESSING"),
  ORDER_STATUS_SHIPPED("ORDER_STATUS_SHIPPED"),
  ORDER_STATUS_DELIVERED("ORDER_STATUS_DELIVERED"),
  ORDER_STATUS_CANCELLED("ORDER_STATUS_CANCELLED");
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

  public Integer toValue() {
    if (this.toString().equals("ORDER_STATUS_UNSPECIFIED")) {
      return 0;
    } else if (this.toString().equals("ORDER_STATUS_PENDING")) {
      return 1;
    } else if (this.toString().equals("ORDER_STATUS_PROCESSING")) {
      return 2;
    } else if (this.toString().equals("ORDER_STATUS_SHIPPED")) {
      return 3;
    } else if (this.toString().equals("ORDER_STATUS_DELIVERED")) {
      return 4;
    } else if (this.toString().equals("ORDER_STATUS_CANCELLED")) {
      return 5;
    } else {
      return 0;
    }
  }

  public static OrderStatus fromValue(Integer value) {
    if (value == 0) {
      return OrderStatus.ORDER_STATUS_UNSPECIFIED;
    } else if (value == 1) {
      return OrderStatus.ORDER_STATUS_PENDING;
    } else if (value == 2) {
      return OrderStatus.ORDER_STATUS_PROCESSING;
    } else if (value == 3) {
      return OrderStatus.ORDER_STATUS_SHIPPED;
    } else if (value == 4) {
      return OrderStatus.ORDER_STATUS_DELIVERED;
    } else if (value == 5) {
      return OrderStatus.ORDER_STATUS_CANCELLED;
    } else {
      throw new IllegalArgumentException("Unknown enum value: " + value);
    }
  }
  ;

  public static OrderStatus force(java.lang.String str) {
    if (ByName.containsKey(str)) {
      return ByName.get(str);
    } else {
      throw new RuntimeException(
          "'" + str + "' does not match any of the following legal values: " + Names);
    }
  }
}
