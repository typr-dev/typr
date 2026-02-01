package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class Inner(
  value: Int,
  description: String
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeInt32(1, this.value)
    output.writeString(2, this.description)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeInt32Size(1, this.value)
    size = size + CodedOutputStream.computeStringSize(2, this.description)
    return size
  }
}

object Inner {
  given marshaller: Marshaller[Inner] = {
    new Marshaller[Inner] {
      override def stream(value: Inner): InputStream = {
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
      override def parse(stream: InputStream): Inner = {
        try {
          return Inner.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): Inner = {
    var value: Int = 0
    var description: String = ""
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { value = input.readInt32() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { description = input.readString() }
      else { input.skipField(tag) }
    }
    return new Inner(value, description)
  }
}