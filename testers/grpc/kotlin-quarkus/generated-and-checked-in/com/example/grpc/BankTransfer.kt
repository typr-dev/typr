package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class BankTransfer(
  val accountNumber: kotlin.String,
  val routingNumber: kotlin.String
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.accountNumber)
    size = size + CodedOutputStream.computeStringSize(2, this.routingNumber)
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.accountNumber)
    output.writeString(2, this.routingNumber)
  }

  companion object {
    val MARSHALLER: Marshaller<BankTransfer> =
      object : Marshaller<BankTransfer> {
        override fun stream(value: BankTransfer): InputStream {
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
        override fun parse(stream: InputStream): BankTransfer {
          try {
            return BankTransfer.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): BankTransfer {
      var accountNumber: kotlin.String = ""
      var routingNumber: kotlin.String = ""
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { accountNumber = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { routingNumber = input.readString() }
        else { input.skipField(tag) }
      }
      return BankTransfer(accountNumber, routingNumber)
    }
  }
}