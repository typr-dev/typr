package com.example.events;

/** Union type for: string | int | boolean */
public sealed interface StringOrIntOrBoolean
    permits StringOrIntOrBoolean.StringValue,
        StringOrIntOrBoolean.IntValue,
        StringOrIntOrBoolean.BooleanValue {
  /** Wrapper for boolean value in union */
  record BooleanValue(Boolean value) implements StringOrIntOrBoolean {
    public BooleanValue withValue(Boolean value) {
      return new BooleanValue(value);
    }

    @Override
    public java.lang.String toString() {
      return value.toString();
    }

    @Override
    public Boolean asBoolean() {
      return value;
    }

    @Override
    public Integer asInt() {
      throw new UnsupportedOperationException("Not a Int value");
    }

    @Override
    public String asString() {
      throw new UnsupportedOperationException("Not a String value");
    }

    @Override
    public Boolean isBoolean() {
      return true;
    }

    @Override
    public Boolean isInt() {
      return false;
    }

    @Override
    public Boolean isString() {
      return false;
    }
  }

  /** Wrapper for int value in union */
  record IntValue(Integer value) implements StringOrIntOrBoolean {
    public IntValue withValue(Integer value) {
      return new IntValue(value);
    }

    @Override
    public java.lang.String toString() {
      return value.toString();
    }

    @Override
    public Boolean asBoolean() {
      throw new UnsupportedOperationException("Not a Boolean value");
    }

    @Override
    public Integer asInt() {
      return value;
    }

    @Override
    public String asString() {
      throw new UnsupportedOperationException("Not a String value");
    }

    @Override
    public Boolean isBoolean() {
      return false;
    }

    @Override
    public Boolean isInt() {
      return true;
    }

    @Override
    public Boolean isString() {
      return false;
    }
  }

  /** Wrapper for string value in union */
  record StringValue(String value) implements StringOrIntOrBoolean {
    public StringValue withValue(String value) {
      return new StringValue(value);
    }

    @Override
    public java.lang.String toString() {
      return value.toString();
    }

    @Override
    public Boolean asBoolean() {
      throw new UnsupportedOperationException("Not a Boolean value");
    }

    @Override
    public Integer asInt() {
      throw new UnsupportedOperationException("Not a Int value");
    }

    @Override
    public String asString() {
      return value;
    }

    @Override
    public Boolean isBoolean() {
      return false;
    }

    @Override
    public Boolean isInt() {
      return false;
    }

    @Override
    public Boolean isString() {
      return true;
    }
  }

  /** Create a union value from a string */
  static StringOrIntOrBoolean of(String value) {
    return new com.example.events.StringOrIntOrBoolean.StringValue(value);
  }

  /** Create a union value from a int */
  static StringOrIntOrBoolean of(Integer value) {
    return new IntValue(value);
  }

  /** Create a union value from a boolean */
  static StringOrIntOrBoolean of(Boolean value) {
    return new BooleanValue(value);
  }

  /** Get the boolean value. Throws if this is not a boolean. */
  Boolean asBoolean();

  /** Get the int value. Throws if this is not a int. */
  Integer asInt();

  /** Get the string value. Throws if this is not a string. */
  String asString();

  /** Check if this union contains a boolean value */
  Boolean isBoolean();

  /** Check if this union contains a int value */
  Boolean isInt();

  /** Check if this union contains a string value */
  Boolean isString();
}
