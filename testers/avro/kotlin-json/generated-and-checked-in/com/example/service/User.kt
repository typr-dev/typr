package com.example.service

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class User(
  /** User unique identifier */
  @field:JsonProperty("id") val id: kotlin.String,
  /** User email address */
  @field:JsonProperty("email") val email: kotlin.String,
  /** User display name */
  @field:JsonProperty("name") val name: kotlin.String,
  @field:JsonProperty("createdAt") val createdAt: Instant
)