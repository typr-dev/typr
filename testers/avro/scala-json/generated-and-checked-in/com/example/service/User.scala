package com.example.service

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

case class User(
  /** User unique identifier */
  @JsonProperty("id") id: String,
  /** User email address */
  @JsonProperty("email") email: String,
  /** User display name */
  @JsonProperty("name") name: String,
  @JsonProperty("createdAt") createdAt: Instant
)