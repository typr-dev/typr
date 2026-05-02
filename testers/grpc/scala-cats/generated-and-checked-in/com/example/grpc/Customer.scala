package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class Customer(
  customerId: CustomerId,
  name: String,
  email: String
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.customerId.unwrap)
    output.writeString(2, this.name)
    output.writeString(3, this.email)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.customerId.unwrap)
    size = size + CodedOutputStream.computeStringSize(2, this.name)
    size = size + CodedOutputStream.computeStringSize(3, this.email)
    return size
  }
}

object Customer {
  given marshaller: Marshaller[Customer] = {
    new Marshaller[Customer] {
      override def stream(value: Customer): InputStream = {
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
      override def parse(stream: InputStream): Customer = {
        try {
          return Customer.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): Customer = {
    var customerId: CustomerId = CustomerId.valueOf("")
    var name: String = ""
    var email: String = ""
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { customerId = CustomerId.valueOf(input.readString()) }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { name = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 3) { email = input.readString() }
      else { input.skipField(tag) }
    }
    return new Customer(customerId, name, email)
  }
}