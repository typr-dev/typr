package com.example.service



/** Thrown when input validation fails */
data class ValidationError(
  val field: kotlin.String,
  val message: kotlin.String
)