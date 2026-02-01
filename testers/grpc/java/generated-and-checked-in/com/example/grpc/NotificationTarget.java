package com.example.grpc;

/** OneOf type for target */
public sealed interface NotificationTarget
    permits NotificationTarget.Email, NotificationTarget.Phone, NotificationTarget.WebhookUrl {
  record Email(String email) implements NotificationTarget {
    public Email withEmail(String email) {
      return new Email(email);
    }
  }

  record Phone(String phone) implements NotificationTarget {
    public Phone withPhone(String phone) {
      return new Phone(phone);
    }
  }

  record WebhookUrl(String webhookUrl) implements NotificationTarget {
    public WebhookUrl withWebhookUrl(String webhookUrl) {
      return new WebhookUrl(webhookUrl);
    }
  }
}
