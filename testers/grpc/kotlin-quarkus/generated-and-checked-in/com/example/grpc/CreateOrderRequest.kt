package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class CreateOrderRequest(val order: Order?) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    if ((this.order != null)) {
      size = size + CodedOutputStream.computeTagSize(1) + CodedOutputStream.computeUInt32SizeNoTag(this.order.getSerializedSize()) + this.order.getSerializedSize()
    }
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    if ((this.order != null)) {
      output.writeTag(1, 2);
      output.writeUInt32NoTag(this.order.getSerializedSize());
      this.order.writeTo(output);
    }
  }

  companion object {
    val MARSHALLER: Marshaller<CreateOrderRequest> =
      object : Marshaller<CreateOrderRequest> {
        override fun stream(value: CreateOrderRequest): InputStream {
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
        override fun parse(stream: InputStream): CreateOrderRequest {
          try {
            return CreateOrderRequest.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): CreateOrderRequest {
      var order: Order? = null
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { val _length = input.readRawVarint32();
        val _oldLimit = input.pushLimit(_length);
        order = Order.parseFrom(input);
        input.popLimit(_oldLimit); }
        else { input.skipField(tag) }
      }
      return CreateOrderRequest(order)
    }
  }
}