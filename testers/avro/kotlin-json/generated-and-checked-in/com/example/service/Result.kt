package com.example.service



/** Generic result type - either success value or error */
sealed interface Result<T, E> {
  /** Error result */
  data class Err<T, E>(val error: E) : Result<T, E>

  /** Successful result */
  data class Ok<T, E>(val value: T) : Result<T, E>
}