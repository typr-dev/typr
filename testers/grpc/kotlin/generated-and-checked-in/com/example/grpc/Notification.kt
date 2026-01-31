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

data class Notification(
  val message: kotlin.String,
  val priority: Priority,
  val target: NotificationTarget?
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.message)
    size = size + CodedOutputStream.computeEnumSize(2, this.priority.toValue())
    when (val __r = this.target) {
      null -> {}
      is Email -> { val c = __r; size = size + CodedOutputStream.computeStringSize(3, c.email) }
      is Phone -> { val c = __r; size = size + CodedOutputStream.computeStringSize(4, c.phone) }
      is WebhookUrl -> { val c = __r; size = size + CodedOutputStream.computeStringSize(5, c.webhookUrl) }
    }
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.message)
    output.writeEnum(2, this.priority.toValue())
    when (val __r = this.target) {
      null -> {}
      is Email -> { val c = __r; output.writeString(3, c.email) }
      is Phone -> { val c = __r; output.writeString(4, c.phone) }
      is WebhookUrl -> { val c = __r; output.writeString(5, c.webhookUrl) }
    }
  }

  companion object {
    val MARSHALLER: Marshaller<Notification> =
      object : Marshaller<Notification> {
        override fun stream(value: Notification): InputStream {
          val bytes = ByteArray(value.getSerializedSize())
          val cos = CodedOutputStream.newInstance(bytes)
          try {
            value.writeTo(cos)
            cos.flush()
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
          return ByteArrayInputStream(bytes)
        }
        override fun parse(stream: InputStream): Notification {
          try {
            return Notification.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): Notification {
      var message: kotlin.String = ""
      var priority: Priority = Priority.fromValue(0)
      var target: NotificationTarget? = null
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { message = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { priority = Priority.fromValue(input.readEnum()) }
        else if (WireFormat.getTagFieldNumber(tag) == 3) { target = Email(input.readString()) }
        else if (WireFormat.getTagFieldNumber(tag) == 4) { target = Phone(input.readString()) }
        else if (WireFormat.getTagFieldNumber(tag) == 5) { target = WebhookUrl(input.readString()) }
        else { input.skipField(tag) }
      }
      return Notification(message, priority, target)
    }
  }
}