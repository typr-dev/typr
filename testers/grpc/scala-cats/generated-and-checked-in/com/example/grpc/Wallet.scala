package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class Wallet(
  walletId: String,
  provider: String
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.walletId)
    output.writeString(2, this.provider)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.walletId)
    size = size + CodedOutputStream.computeStringSize(2, this.provider)
    return size
  }
}

object Wallet {
  given marshaller: Marshaller[Wallet] = {
    new Marshaller[Wallet] {
      override def stream(value: Wallet): InputStream = {
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
      override def parse(stream: InputStream): Wallet = {
        try {
          return Wallet.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): Wallet = {
    var walletId: String = ""
    var provider: String = ""
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { walletId = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { provider = input.readString() }
      else { input.skipField(tag) }
    }
    return new Wallet(walletId, provider)
  }
}