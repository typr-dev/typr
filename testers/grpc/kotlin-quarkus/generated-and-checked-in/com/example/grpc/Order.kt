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

data class Order(
  val orderId: OrderId,
  val customerId: CustomerId,
  val amountCents: kotlin.Long,
  val createdAt: Instant
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.orderId.unwrap())
    size = size + CodedOutputStream.computeStringSize(2, this.customerId.unwrap())
    size = size + CodedOutputStream.computeInt64Size(3, this.amountCents)
    size = size + CodedOutputStream.computeTagSize(4) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeInt64Size(1, this.createdAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.createdAt.getNano())) + CodedOutputStream.computeInt64Size(1, this.createdAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.createdAt.getNano())
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.orderId.unwrap())
    output.writeString(2, this.customerId.unwrap())
    output.writeInt64(3, this.amountCents)
    output.writeTag(4, 2)
    output.writeUInt32NoTag(CodedOutputStream.computeInt64Size(1, this.createdAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.createdAt.getNano()))
    output.writeInt64(1, this.createdAt.getEpochSecond())
    output.writeInt32(2, this.createdAt.getNano())
  }

  companion object {
    val MARSHALLER: Marshaller<Order> =
      object : Marshaller<Order> {
        override fun stream(value: Order): InputStream {
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
        override fun parse(stream: InputStream): Order {
          try {
            return Order.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): Order {
      var orderId: OrderId = OrderId.valueOf("")
      var customerId: CustomerId = CustomerId.valueOf("")
      var amountCents: kotlin.Long = 0L
      var createdAt: Instant = Instant.EPOCH
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { orderId = OrderId.valueOf(input.readString()) }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { customerId = CustomerId.valueOf(input.readString()) }
        else if (WireFormat.getTagFieldNumber(tag) == 3) { amountCents = input.readInt64() }
        else if (WireFormat.getTagFieldNumber(tag) == 4) { val _length = input.readRawVarint32();
        val _oldLimit = input.pushLimit(_length);
        var _tsSeconds = 0L;
        var _tsNanos = 0;
        while (!input.isAtEnd()) {
          val _tsTag = input.readTag()
          if (WireFormat.getTagFieldNumber(_tsTag) == 1) { _tsSeconds = input.readInt64() }
          else if (WireFormat.getTagFieldNumber(_tsTag) == 2) { _tsNanos = input.readInt32() }
          else { input.skipField(_tsTag) }
        };
        createdAt = Instant.ofEpochSecond(_tsSeconds, _tsNanos.toLong());
        input.popLimit(_oldLimit); }
        else { input.skipField(tag) }
      }
      return Order(orderId, customerId, amountCents, createdAt)
    }
  }
}