package com.example.events;

/** Union type for: string | long */
public sealed interface StringOrLong permits StringOrLong.StringValue, StringOrLong.LongValue {
  /** Wrapper for long value in union */
  record LongValue(Long value) implements StringOrLong {
    public LongValue withValue(Long value) {
      return new LongValue(value);
    }

    @Override
    public java.lang.String toString() {
      return value.toString();
    }

    @Override
    public Long asLong() {
      return value;
    }

    @Override
    public String asString() {
      throw new UnsupportedOperationException("Not a String value");
    }

    @Override
    public Boolean isLong() {
      return true;
    }

    @Override
    public Boolean isString() {
      return false;
    }
  }

  /** Wrapper for string value in union */
  record StringValue(String value) implements StringOrLong {
    public StringValue withValue(String value) {
      return new StringValue(value);
    }

    @Override
    public java.lang.String toString() {
      return value.toString();
    }

    @Override
    public Long asLong() {
      throw new UnsupportedOperationException("Not a Long value");
    }

    @Override
    public String asString() {
      return value;
    }

    @Override
    public Boolean isLong() {
      return false;
    }

    @Override
    public Boolean isString() {
      return true;
    }
  }

  /** Create a union value from a string */
  static StringOrLong of(String value) {
    return new StringValue(value);
  }

  /** Create a union value from a long */
  static StringOrLong of(Long value) {
    return new LongValue(value);
  }

  /** Get the long value. Throws if this is not a long. */
  Long asLong();

  /** Get the string value. Throws if this is not a string. */
  String asString();

  /** Check if this union contains a long value */
  Boolean isLong();

  /** Check if this union contains a string value */
  Boolean isString();
}
