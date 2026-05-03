package com.example.grpc

import com.example.grpc.NotificationTarget.Email
import com.example.grpc.NotificationTarget.Phone
import com.example.grpc.NotificationTarget.WebhookUrl
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class Notification(
  message: String,
  priority: Priority,
  target: NotificationTarget
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.message)
    output.writeEnum(2, this.priority.toValue)
    this.target match {
      case null => {}
      case c: Email => output.writeString(3, c.email)
      case c: Phone => output.writeString(4, c.phone)
      case c: WebhookUrl => output.writeString(5, c.webhookUrl)
    }
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.message)
    size = size + CodedOutputStream.computeEnumSize(2, this.priority.toValue)
    this.target match {
      case null => {}
      case c: Email => size = size + CodedOutputStream.computeStringSize(3, c.email)
      case c: Phone => size = size + CodedOutputStream.computeStringSize(4, c.phone)
      case c: WebhookUrl => size = size + CodedOutputStream.computeStringSize(5, c.webhookUrl)
    }
    return size
  }
}

object Notification {
  given marshaller: Marshaller[Notification] = {
    new Marshaller[Notification] {
      override def stream(value: Notification): InputStream = {
        val bytes = Array.ofDim[Byte](value.getSerializedSize)
        val cos = CodedOutputStream.newInstance(bytes)
        try {
          value.writeTo(cos)
          cos.flush()
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
        return new ByteArrayInputStream(bytes)
      }
      override def parse(stream: InputStream): Notification = {
        try {
          return Notification.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): Notification = {
    var message: String = ""
    var priority: Priority = Priority.fromValue(0)
    var target: NotificationTarget = null
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { message = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { priority = Priority.fromValue(input.readEnum()) }
      else if (WireFormat.getTagFieldNumber(tag) == 3) { target = new Email(input.readString()) }
      else if (WireFormat.getTagFieldNumber(tag) == 4) { target = new Phone(input.readString()) }
      else if (WireFormat.getTagFieldNumber(tag) == 5) { target = new WebhookUrl(input.readString()) }
      else { input.skipField(tag) }
    }
    return new Notification(message, priority, target)
  }
}