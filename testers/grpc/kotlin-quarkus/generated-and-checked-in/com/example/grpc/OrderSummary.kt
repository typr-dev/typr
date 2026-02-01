package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class OrderSummary(
  val totalOrders: Int,
  val totalAmountCents: kotlin.Long
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeInt32Size(1, this.totalOrders)
    size = size + CodedOutputStream.computeInt64Size(2, this.totalAmountCents)
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeInt32(1, this.totalOrders)
    output.writeInt64(2, this.totalAmountCents)
  }

  companion object {
    val MARSHALLER: Marshaller<OrderSummary> =
      object : Marshaller<OrderSummary> {
        override fun stream(value: OrderSummary): InputStream {
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
        override fun parse(stream: InputStream): OrderSummary {
          try {
            return OrderSummary.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): OrderSummary {
      var totalOrders: Int = 0
      var totalAmountCents: kotlin.Long = 0L
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { totalOrders = input.readInt32() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { totalAmountCents = input.readInt64() }
        else { input.skipField(tag) }
      }
      return OrderSummary(totalOrders, totalAmountCents)
    }
  }
}