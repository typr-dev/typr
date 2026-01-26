package com.example.service;

/** Generic result type - either success value or error */
public sealed interface Result<T, E> permits Result.Ok, Result.Err {
  /** Error result */
  record Err<T, E>(E error) implements Result<T, E> {
    public Err<T, E> withError(E error) {
      return new Err<>(error);
    }
  }

  /** Successful result */
  record Ok<T, E>(T value) implements Result<T, E> {
    public Ok<T, E> withValue(T value) {
      return new Ok<>(value);
    }
  }
}
