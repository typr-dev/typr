package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class CreditCard(
  val cardNumber: kotlin.String,
  val expiryDate: kotlin.String,
  val cvv: kotlin.String
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.cardNumber)
    size = size + CodedOutputStream.computeStringSize(2, this.expiryDate)
    size = size + CodedOutputStream.computeStringSize(3, this.cvv)
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.cardNumber)
    output.writeString(2, this.expiryDate)
    output.writeString(3, this.cvv)
  }

  companion object {
    val MARSHALLER: Marshaller<CreditCard> =
      object : Marshaller<CreditCard> {
        override fun stream(value: CreditCard): InputStream {
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
        override fun parse(stream: InputStream): CreditCard {
          try {
            return CreditCard.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): CreditCard {
      var cardNumber: kotlin.String = ""
      var expiryDate: kotlin.String = ""
      var cvv: kotlin.String = ""
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { cardNumber = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { expiryDate = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 3) { cvv = input.readString() }
        else { input.skipField(tag) }
      }
      return CreditCard(cardNumber, expiryDate, cvv)
    }
  }
}