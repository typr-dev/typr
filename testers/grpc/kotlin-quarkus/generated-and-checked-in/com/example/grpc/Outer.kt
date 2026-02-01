package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class Outer(
  val name: kotlin.String,
  val inner: Inner?
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.name)
    if ((this.inner != null)) {
      size = size + CodedOutputStream.computeTagSize(2) + CodedOutputStream.computeUInt32SizeNoTag(this.inner.getSerializedSize()) + this.inner.getSerializedSize()
    }
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.name)
    if ((this.inner != null)) {
      output.writeTag(2, 2);
      output.writeUInt32NoTag(this.inner.getSerializedSize());
      this.inner.writeTo(output);
    }
  }

  companion object {
    val MARSHALLER: Marshaller<Outer> =
      object : Marshaller<Outer> {
        override fun stream(value: Outer): InputStream {
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
        override fun parse(stream: InputStream): Outer {
          try {
            return Outer.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): Outer {
      var name: kotlin.String = ""
      var inner: Inner? = null
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { name = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { val _length = input.readRawVarint32();
        val _oldLimit = input.pushLimit(_length);
        inner = Inner.parseFrom(input);
        input.popLimit(_oldLimit); }
        else { input.skipField(tag) }
      }
      return Outer(name, inner)
    }
  }
}