package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class BankTransfer(
  accountNumber: String,
  routingNumber: String
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.accountNumber)
    output.writeString(2, this.routingNumber)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.accountNumber)
    size = size + CodedOutputStream.computeStringSize(2, this.routingNumber)
    return size
  }
}

object BankTransfer {
  given marshaller: Marshaller[BankTransfer] = {
    new Marshaller[BankTransfer] {
      override def stream(value: BankTransfer): InputStream = {
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
      override def parse(stream: InputStream): BankTransfer = {
        try {
          return BankTransfer.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): BankTransfer = {
    var accountNumber: String = ""
    var routingNumber: String = ""
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { accountNumber = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { routingNumber = input.readString() }
      else { input.skipField(tag) }
    }
    return new BankTransfer(accountNumber, routingNumber)
  }
}