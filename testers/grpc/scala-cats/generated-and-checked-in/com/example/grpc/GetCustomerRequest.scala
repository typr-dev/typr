package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class GetCustomerRequest(customerId: String) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.customerId)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.customerId)
    return size
  }
}

object GetCustomerRequest {
  given marshaller: Marshaller[GetCustomerRequest] = {
    new Marshaller[GetCustomerRequest] {
      override def stream(value: GetCustomerRequest): InputStream = {
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
      override def parse(stream: InputStream): GetCustomerRequest = {
        try {
          return GetCustomerRequest.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): GetCustomerRequest = {
    var customerId: String = ""
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { customerId = input.readString() }
      else { input.skipField(tag) }
    }
    return new GetCustomerRequest(customerId)
  }
}