package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class Inner(
  val value: Int,
  val description: kotlin.String
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeInt32Size(1, this.value)
    size = size + CodedOutputStream.computeStringSize(2, this.description)
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeInt32(1, this.value)
    output.writeString(2, this.description)
  }

  companion object {
    val MARSHALLER: Marshaller<Inner> =
      object : Marshaller<Inner> {
        override fun stream(value: Inner): InputStream {
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
        override fun parse(stream: InputStream): Inner {
          try {
            return Inner.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): Inner {
      var value: Int = 0
      var description: kotlin.String = ""
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { value = input.readInt32() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { description = input.readString() }
        else { input.skipField(tag) }
      }
      return Inner(value, description)
    }
  }
}