package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class ListOrdersRequest(
  val customerId: kotlin.String,
  val pageSize: Int
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.customerId)
    size = size + CodedOutputStream.computeInt32Size(2, this.pageSize)
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.customerId)
    output.writeInt32(2, this.pageSize)
  }

  companion object {
    val MARSHALLER: Marshaller<ListOrdersRequest> =
      object : Marshaller<ListOrdersRequest> {
        override fun stream(value: ListOrdersRequest): InputStream {
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
        override fun parse(stream: InputStream): ListOrdersRequest {
          try {
            return ListOrdersRequest.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): ListOrdersRequest {
      var customerId: kotlin.String = ""
      var pageSize: Int = 0
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { customerId = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { pageSize = input.readInt32() }
        else { input.skipField(tag) }
      }
      return ListOrdersRequest(customerId, pageSize)
    }
  }
}