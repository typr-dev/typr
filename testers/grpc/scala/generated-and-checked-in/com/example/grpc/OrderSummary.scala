package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class OrderSummary(
  totalOrders: Int,
  totalAmountCents: Long
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeInt32(1, this.totalOrders)
    output.writeInt64(2, this.totalAmountCents)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeInt32Size(1, this.totalOrders)
    size = size + CodedOutputStream.computeInt64Size(2, this.totalAmountCents)
    return size
  }
}

object OrderSummary {
  given marshaller: Marshaller[OrderSummary] = {
    new Marshaller[OrderSummary] {
      override def stream(value: OrderSummary): InputStream = {
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
      override def parse(stream: InputStream): OrderSummary = {
        try {
          return OrderSummary.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): OrderSummary = {
    var totalOrders: Int = 0
    var totalAmountCents: Long = 0L
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { totalOrders = input.readInt32() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { totalAmountCents = input.readInt64() }
      else { input.skipField(tag) }
    }
    return new OrderSummary(totalOrders, totalAmountCents)
  }
}