package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class GetCustomerResponse(customer: Customer) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    if ((this.customer != null)) {
      output.writeTag(1, 2);
      output.writeUInt32NoTag(this.customer.getSerializedSize);
      this.customer.writeTo(output);
    }
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    if ((this.customer != null)) {
      size = size + CodedOutputStream.computeTagSize(1) + CodedOutputStream.computeUInt32SizeNoTag(this.customer.getSerializedSize) + this.customer.getSerializedSize
    }
    return size
  }
}

object GetCustomerResponse {
  given marshaller: Marshaller[GetCustomerResponse] = {
    new Marshaller[GetCustomerResponse] {
      override def stream(value: GetCustomerResponse): InputStream = {
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
      override def parse(stream: InputStream): GetCustomerResponse = {
        try {
          return GetCustomerResponse.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): GetCustomerResponse = {
    var customer: Customer = null
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      customer = Customer.parseFrom(input);
      input.popLimit(`_oldLimit`); }
      else { input.skipField(tag) }
    }
    return new GetCustomerResponse(customer)
  }
}