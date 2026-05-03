package com.example.grpc



/** OneOf type for target */
sealed trait NotificationTarget

object NotificationTarget {
  case class Email(email: String) extends NotificationTarget

  case class Phone(phone: String) extends NotificationTarget

  case class WebhookUrl(webhookUrl: String) extends NotificationTarget
}