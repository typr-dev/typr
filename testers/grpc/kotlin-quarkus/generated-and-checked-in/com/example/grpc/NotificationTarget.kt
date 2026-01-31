package com.example.grpc



/** OneOf type for target */
sealed interface NotificationTarget {
  data class Email(val email: kotlin.String) : NotificationTarget

  data class Phone(val phone: kotlin.String) : NotificationTarget

  data class WebhookUrl(val webhookUrl: kotlin.String) : NotificationTarget
}