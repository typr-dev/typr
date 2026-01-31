package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class CreateOrderResponse(
  val orderId: kotlin.String,
  val status: OrderStatus
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.orderId)
    size = size + CodedOutputStream.computeEnumSize(2, this.status.toValue())
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.orderId)
    output.writeEnum(2, this.status.toValue())
  }

  companion object {
    val MARSHALLER: Marshaller<CreateOrderResponse> =
      object : Marshaller<CreateOrderResponse> {
        override fun stream(value: CreateOrderResponse): InputStream {
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
        override fun parse(stream: InputStream): CreateOrderResponse {
          try {
            return CreateOrderResponse.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): CreateOrderResponse {
      var orderId: kotlin.String = ""
      var status: OrderStatus = OrderStatus.fromValue(0)
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { orderId = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { status = OrderStatus.fromValue(input.readEnum()) }
        else { input.skipField(tag) }
      }
      return CreateOrderResponse(orderId, status)
    }
  }
}