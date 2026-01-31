package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class Wallet(
  val walletId: kotlin.String,
  val provider: kotlin.String
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.walletId)
    size = size + CodedOutputStream.computeStringSize(2, this.provider)
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.walletId)
    output.writeString(2, this.provider)
  }

  companion object {
    val MARSHALLER: Marshaller<Wallet> =
      object : Marshaller<Wallet> {
        override fun stream(value: Wallet): InputStream {
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
        override fun parse(stream: InputStream): Wallet {
          try {
            return Wallet.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): Wallet {
      var walletId: kotlin.String = ""
      var provider: kotlin.String = ""
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { walletId = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { provider = input.readString() }
        else { input.skipField(tag) }
      }
      return Wallet(walletId, provider)
    }
  }
}