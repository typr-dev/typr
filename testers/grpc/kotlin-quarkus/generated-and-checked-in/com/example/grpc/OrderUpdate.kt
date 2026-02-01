package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException
import java.time.Instant

data class OrderUpdate(
  val orderId: kotlin.String,
  val status: OrderStatus,
  val updatedAt: Instant
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.orderId)
    size = size + CodedOutputStream.computeEnumSize(2, this.status.toValue())
    size = size + CodedOutputStream.computeTagSize(3) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeInt64Size(1, this.updatedAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.updatedAt.getNano())) + CodedOutputStream.computeInt64Size(1, this.updatedAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.updatedAt.getNano())
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.orderId)
    output.writeEnum(2, this.status.toValue())
    output.writeTag(3, 2)
    output.writeUInt32NoTag(CodedOutputStream.computeInt64Size(1, this.updatedAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.updatedAt.getNano()))
    output.writeInt64(1, this.updatedAt.getEpochSecond())
    output.writeInt32(2, this.updatedAt.getNano())
  }

  companion object {
    val MARSHALLER: Marshaller<OrderUpdate> =
      object : Marshaller<OrderUpdate> {
        override fun stream(value: OrderUpdate): InputStream {
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
        override fun parse(stream: InputStream): OrderUpdate {
          try {
            return OrderUpdate.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): OrderUpdate {
      var orderId: kotlin.String = ""
      var status: OrderStatus = OrderStatus.fromValue(0)
      var updatedAt: Instant = Instant.EPOCH
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { orderId = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { status = OrderStatus.fromValue(input.readEnum()) }
        else if (WireFormat.getTagFieldNumber(tag) == 3) { val _length = input.readRawVarint32();
        val _oldLimit = input.pushLimit(_length);
        var _tsSeconds = 0L;
        var _tsNanos = 0;
        while (!input.isAtEnd()) {
          val _tsTag = input.readTag()
          if (WireFormat.getTagFieldNumber(_tsTag) == 1) { _tsSeconds = input.readInt64() }
          else if (WireFormat.getTagFieldNumber(_tsTag) == 2) { _tsNanos = input.readInt32() }
          else { input.skipField(_tsTag) }
        };
        updatedAt = Instant.ofEpochSecond(_tsSeconds, _tsNanos.toLong());
        input.popLimit(_oldLimit); }
        else { input.skipField(tag) }
      }
      return OrderUpdate(orderId, status, updatedAt)
    }
  }
}