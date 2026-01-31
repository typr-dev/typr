package com.example.grpc;

/** Wrapper type for typr.grpc.GrpcCodegen$$$Lambda/0x00007fc001118400@41330d4f */
public record OrderId(String value) {
  public OrderId withValue(String value) {
    return new OrderId(value);
  }

  @Override
  public java.lang.String toString() {
    return value.toString();
  }

  /** Create a OrderId from a raw value */
  public static OrderId valueOf(String v) {
    return new OrderId(v);
  }

  /** Get the underlying value */
  public String unwrap() {
    return this.value();
  }
}
