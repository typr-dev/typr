package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class Customer(
  val customerId: CustomerId,
  val name: kotlin.String,
  val email: kotlin.String
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.customerId.unwrap())
    size = size + CodedOutputStream.computeStringSize(2, this.name)
    size = size + CodedOutputStream.computeStringSize(3, this.email)
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.customerId.unwrap())
    output.writeString(2, this.name)
    output.writeString(3, this.email)
  }

  companion object {
    val MARSHALLER: Marshaller<Customer> =
      object : Marshaller<Customer> {
        override fun stream(value: Customer): InputStream {
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
        override fun parse(stream: InputStream): Customer {
          try {
            return Customer.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): Customer {
      var customerId: CustomerId = CustomerId.valueOf("")
      var name: kotlin.String = ""
      var email: kotlin.String = ""
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { customerId = CustomerId.valueOf(input.readString()) }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { name = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 3) { email = input.readString() }
        else { input.skipField(tag) }
      }
      return Customer(customerId, name, email)
    }
  }
}