package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class CreditCard(
  cardNumber: String,
  expiryDate: String,
  cvv: String
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.cardNumber)
    output.writeString(2, this.expiryDate)
    output.writeString(3, this.cvv)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.cardNumber)
    size = size + CodedOutputStream.computeStringSize(2, this.expiryDate)
    size = size + CodedOutputStream.computeStringSize(3, this.cvv)
    return size
  }
}

object CreditCard {
  given marshaller: Marshaller[CreditCard] = {
    new Marshaller[CreditCard] {
      override def stream(value: CreditCard): InputStream = {
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
      override def parse(stream: InputStream): CreditCard = {
        try {
          return CreditCard.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): CreditCard = {
    var cardNumber: String = ""
    var expiryDate: String = ""
    var cvv: String = ""
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { cardNumber = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { expiryDate = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 3) { cvv = input.readString() }
      else { input.skipField(tag) }
    }
    return new CreditCard(cardNumber, expiryDate, cvv)
  }
}