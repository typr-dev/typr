package com.example.grpc;



/** Wrapper type for java.lang.String */
public record CustomerId(String value) {
  public CustomerId withValue(String value) {
    return new CustomerId(value);
  }

  @Override
  public java.lang.String toString() {
    return value.toString();
  }

  /** Create a CustomerId from a raw value */
  static public CustomerId valueOf(String v) {
    return new CustomerId(v);
  }

  /** Get the underlying value */
  public String unwrap() {
    return this.value();
  }
}