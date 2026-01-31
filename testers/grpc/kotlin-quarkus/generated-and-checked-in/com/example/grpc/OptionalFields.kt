package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

data class OptionalFields(
  val name: kotlin.String?,
  val age: Int?,
  val customer: Customer?
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    if (this.name != null) {
      val v = this.name!!;
      size = size + CodedOutputStream.computeStringSize(1, v);
    }
    if (this.age != null) {
      val v = this.age!!;
      size = size + CodedOutputStream.computeInt32Size(2, v);
    }
    if (this.customer != null) {
      val v = this.customer!!;
      size = size + CodedOutputStream.computeTagSize(3) + CodedOutputStream.computeUInt32SizeNoTag(v.getSerializedSize()) + v.getSerializedSize();
    }
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    if (this.name != null) {
      val v = this.name!!;
      output.writeString(1, v);
    }
    if (this.age != null) {
      val v = this.age!!;
      output.writeInt32(2, v);
    }
    if (this.customer != null) {
      val v = this.customer!!;
      output.writeTag(3, 2);
      output.writeUInt32NoTag(v.getSerializedSize());
      v.writeTo(output);
    }
  }

  companion object {
    val MARSHALLER: Marshaller<OptionalFields> =
      object : Marshaller<OptionalFields> {
        override fun stream(value: OptionalFields): InputStream {
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
        override fun parse(stream: InputStream): OptionalFields {
          try {
            return OptionalFields.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): OptionalFields {
      var name: kotlin.String? = null
      var age: Int? = null
      var customer: Customer? = null
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { name = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { age = input.readInt32() }
        else if (WireFormat.getTagFieldNumber(tag) == 3) { val _length = input.readRawVarint32();
        val _oldLimit = input.pushLimit(_length);
        customer = Customer.parseFrom(input);
        input.popLimit(_oldLimit); }
        else { input.skipField(tag) }
      }
      return OptionalFields(name, age, customer)
    }
  }
}