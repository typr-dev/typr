package com.example.service

import com.example.service.CreateUserResponse.Error
import com.example.service.CreateUserResponse.Success
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(value = [Type(value = Success::class, name = "Success"), Type(value = Error::class, name = "Error")])
/** Response wrapper for createUser RPC call */
sealed interface CreateUserResponse {
  /** Error response */
  data class Error(
    val correlationId: kotlin.String,
    val error: ValidationError
  ) : CreateUserResponse

  /** Successful response */
  data class Success(
    val correlationId: kotlin.String,
    val value: User
  ) : CreateUserResponse
}