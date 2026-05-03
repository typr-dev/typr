package com.example.grpc

import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class ScalarTypes(
  doubleVal: Double,
  floatVal: Float,
  int32Val: Int,
  int64Val: Long,
  uint32Val: Int,
  uint64Val: Long,
  sint32Val: Int,
  sint64Val: Long,
  fixed32Val: Int,
  fixed64Val: Long,
  sfixed32Val: Int,
  sfixed64Val: Long,
  boolVal: Boolean,
  stringVal: String,
  bytesVal: ByteString
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeDouble(1, this.doubleVal)
    output.writeFloat(2, this.floatVal)
    output.writeInt32(3, this.int32Val)
    output.writeInt64(4, this.int64Val)
    output.writeUInt32(5, this.uint32Val)
    output.writeUInt64(6, this.uint64Val)
    output.writeSInt32(7, this.sint32Val)
    output.writeSInt64(8, this.sint64Val)
    output.writeFixed32(9, this.fixed32Val)
    output.writeFixed64(10, this.fixed64Val)
    output.writeSFixed32(11, this.sfixed32Val)
    output.writeSFixed64(12, this.sfixed64Val)
    output.writeBool(13, this.boolVal)
    output.writeString(14, this.stringVal)
    output.writeBytes(15, this.bytesVal)
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeDoubleSize(1, this.doubleVal)
    size = size + CodedOutputStream.computeFloatSize(2, this.floatVal)
    size = size + CodedOutputStream.computeInt32Size(3, this.int32Val)
    size = size + CodedOutputStream.computeInt64Size(4, this.int64Val)
    size = size + CodedOutputStream.computeUInt32Size(5, this.uint32Val)
    size = size + CodedOutputStream.computeUInt64Size(6, this.uint64Val)
    size = size + CodedOutputStream.computeSInt32Size(7, this.sint32Val)
    size = size + CodedOutputStream.computeSInt64Size(8, this.sint64Val)
    size = size + CodedOutputStream.computeFixed32Size(9, this.fixed32Val)
    size = size + CodedOutputStream.computeFixed64Size(10, this.fixed64Val)
    size = size + CodedOutputStream.computeSFixed32Size(11, this.sfixed32Val)
    size = size + CodedOutputStream.computeSFixed64Size(12, this.sfixed64Val)
    size = size + CodedOutputStream.computeBoolSize(13, this.boolVal)
    size = size + CodedOutputStream.computeStringSize(14, this.stringVal)
    size = size + CodedOutputStream.computeBytesSize(15, this.bytesVal)
    return size
  }
}

object ScalarTypes {
  given marshaller: Marshaller[ScalarTypes] = {
    new Marshaller[ScalarTypes] {
      override def stream(value: ScalarTypes): InputStream = {
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
      override def parse(stream: InputStream): ScalarTypes = {
        try {
          return ScalarTypes.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): ScalarTypes = {
    var doubleVal: Double = 0.0
    var floatVal: Float = 0.0f
    var int32Val: Int = 0
    var int64Val: Long = 0L
    var uint32Val: Int = 0
    var uint64Val: Long = 0L
    var sint32Val: Int = 0
    var sint64Val: Long = 0L
    var fixed32Val: Int = 0
    var fixed64Val: Long = 0L
    var sfixed32Val: Int = 0
    var sfixed64Val: Long = 0L
    var boolVal: Boolean = false
    var stringVal: String = ""
    var bytesVal: ByteString = ByteString.EMPTY
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { doubleVal = input.readDouble() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { floatVal = input.readFloat() }
      else if (WireFormat.getTagFieldNumber(tag) == 3) { int32Val = input.readInt32() }
      else if (WireFormat.getTagFieldNumber(tag) == 4) { int64Val = input.readInt64() }
      else if (WireFormat.getTagFieldNumber(tag) == 5) { uint32Val = input.readUInt32() }
      else if (WireFormat.getTagFieldNumber(tag) == 6) { uint64Val = input.readUInt64() }
      else if (WireFormat.getTagFieldNumber(tag) == 7) { sint32Val = input.readSInt32() }
      else if (WireFormat.getTagFieldNumber(tag) == 8) { sint64Val = input.readSInt64() }
      else if (WireFormat.getTagFieldNumber(tag) == 9) { fixed32Val = input.readFixed32() }
      else if (WireFormat.getTagFieldNumber(tag) == 10) { fixed64Val = input.readFixed64() }
      else if (WireFormat.getTagFieldNumber(tag) == 11) { sfixed32Val = input.readSFixed32() }
      else if (WireFormat.getTagFieldNumber(tag) == 12) { sfixed64Val = input.readSFixed64() }
      else if (WireFormat.getTagFieldNumber(tag) == 13) { boolVal = input.readBool() }
      else if (WireFormat.getTagFieldNumber(tag) == 14) { stringVal = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 15) { bytesVal = input.readBytes() }
      else { input.skipField(tag) }
    }
    return new ScalarTypes(
      doubleVal,
      floatVal,
      int32Val,
      int64Val,
      uint32Val,
      uint64Val,
      sint32Val,
      sint64Val,
      fixed32Val,
      fixed64Val,
      sfixed32Val,
      sfixed64Val,
      boolVal,
      stringVal,
      bytesVal
    )
  }
}