package com.example.service



/** Generic result type - either success value or error */
sealed trait Result[T, E]

object Result {
  /** Error result */
  case class Err[T, E](error: E) extends Result[T, E]

  /** Successful result */
  case class Ok[T, E](value: T) extends Result[T, E]
}