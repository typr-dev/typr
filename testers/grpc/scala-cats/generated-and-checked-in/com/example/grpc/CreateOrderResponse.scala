package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class CreateOrderResponse(
  orderId: String,
  status: OrderStatus
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.orderId)
    output.writeEnum(2, this.status.toValue)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.orderId)
    size = size + CodedOutputStream.computeEnumSize(2, this.status.toValue)
    return size
  }
}

object CreateOrderResponse {
  given marshaller: Marshaller[CreateOrderResponse] = {
    new Marshaller[CreateOrderResponse] {
      override def stream(value: CreateOrderResponse): InputStream = {
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
      override def parse(stream: InputStream): CreateOrderResponse = {
        try {
          return CreateOrderResponse.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): CreateOrderResponse = {
    var orderId: String = ""
    var status: OrderStatus = OrderStatus.fromValue(0)
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { orderId = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { status = OrderStatus.fromValue(input.readEnum()) }
      else { input.skipField(tag) }
    }
    return new CreateOrderResponse(orderId, status)
  }
}