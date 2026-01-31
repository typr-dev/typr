package com.example.grpc;

import com.example.grpc.NotificationTarget.Email;
import com.example.grpc.NotificationTarget.Phone;
import com.example.grpc.NotificationTarget.WebhookUrl;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record Notification(String message, Priority priority, NotificationTarget target) {
  public Notification withMessage(String message) {
    return new Notification(message, priority, target);
  }

  public Notification withPriority(Priority priority) {
    return new Notification(message, priority, target);
  }

  public Notification withTarget(NotificationTarget target) {
    return new Notification(message, priority, target);
  }

  public static Marshaller<Notification> MARSHALLER =
      new Marshaller<Notification>() {
        @Override
        public InputStream stream(Notification value) {
          var bytes = new byte[value.getSerializedSize()];
          var cos = CodedOutputStream.newInstance(bytes);
          try {
            value.writeTo(cos);
            cos.flush();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          return new ByteArrayInputStream(bytes);
        }

        @Override
        public Notification parse(InputStream stream) {
          try {
            return Notification.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static Notification parseFrom(CodedInputStream input) throws IOException {
    String message = "";
    Priority priority = Priority.fromValue(0);
    NotificationTarget target = null;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        message = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        priority = Priority.fromValue(input.readEnum());
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        target = new Email(input.readString());
      } else if (WireFormat.getTagFieldNumber(tag) == 4) {
        target = new Phone(input.readString());
      } else if (WireFormat.getTagFieldNumber(tag) == 5) {
        target = new WebhookUrl(input.readString());
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new Notification(message, priority, target);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.message());
    size = size + CodedOutputStream.computeEnumSize(2, this.priority().toValue());
    switch (this.target()) {
      case null -> {}
      case Email c -> size = size + CodedOutputStream.computeStringSize(3, c.email());
      case Phone c -> size = size + CodedOutputStream.computeStringSize(4, c.phone());
      case WebhookUrl c -> size = size + CodedOutputStream.computeStringSize(5, c.webhookUrl());
    }
    ;
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.message());
    output.writeEnum(2, this.priority().toValue());
    switch (this.target()) {
      case null -> {}
      case Email c -> output.writeString(3, c.email());
      case Phone c -> output.writeString(4, c.phone());
      case WebhookUrl c -> output.writeString(5, c.webhookUrl());
    }
    ;
  }
}
