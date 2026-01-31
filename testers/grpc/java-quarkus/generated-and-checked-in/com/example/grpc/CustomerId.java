package com.example.grpc;

/** Wrapper type for typr.grpc.GrpcCodegen$$$Lambda/0x00007fc001118400@38145825 */
public record CustomerId(String value) {
  public CustomerId withValue(String value) {
    return new CustomerId(value);
  }

  @Override
  public java.lang.String toString() {
    return value.toString();
  }

  /** Create a CustomerId from a raw value */
  public static CustomerId valueOf(String v) {
    return new CustomerId(v);
  }

  /** Get the underlying value */
  public String unwrap() {
    return this.value();
  }
}
