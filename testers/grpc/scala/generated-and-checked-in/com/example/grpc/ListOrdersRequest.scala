package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class ListOrdersRequest(
  customerId: String,
  pageSize: Int
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.customerId)
    output.writeInt32(2, this.pageSize)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.customerId)
    size = size + CodedOutputStream.computeInt32Size(2, this.pageSize)
    return size
  }
}

object ListOrdersRequest {
  given marshaller: Marshaller[ListOrdersRequest] = {
    new Marshaller[ListOrdersRequest] {
      override def stream(value: ListOrdersRequest): InputStream = {
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
      override def parse(stream: InputStream): ListOrdersRequest = {
        try {
          return ListOrdersRequest.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): ListOrdersRequest = {
    var customerId: String = ""
    var pageSize: Int = 0
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { customerId = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { pageSize = input.readInt32() }
      else { input.skipField(tag) }
    }
    return new ListOrdersRequest(customerId, pageSize)
  }
}