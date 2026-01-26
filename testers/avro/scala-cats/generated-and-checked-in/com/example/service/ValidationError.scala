package com.example.service



/** Thrown when input validation fails */
case class ValidationError(
  field: String,
  message: String
)