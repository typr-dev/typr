package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException
import java.time.Duration
import java.time.Instant

case class WellKnownTypesMessage(
  createdAt: Instant,
  ttl: Duration,
  nullableString: Option[String],
  nullableInt: Option[Int],
  nullableBool: Option[Boolean]
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeTag(1, 2)
    output.writeUInt32NoTag(CodedOutputStream.computeInt64Size(1, this.createdAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.createdAt.getNano()))
    output.writeInt64(1, this.createdAt.getEpochSecond())
    output.writeInt32(2, this.createdAt.getNano())
    output.writeTag(2, 2)
    output.writeUInt32NoTag(CodedOutputStream.computeInt64Size(1, this.ttl.getSeconds()) + CodedOutputStream.computeInt32Size(2, this.ttl.getNano()))
    output.writeInt64(1, this.ttl.getSeconds())
    output.writeInt32(2, this.ttl.getNano())
    if (this.nullableString.isDefined) {
      val v = this.nullableString.get;
      output.writeTag(3, 2);
      output.writeUInt32NoTag(CodedOutputStream.computeStringSize(1, v));
      output.writeString(1, v);
    }
    if (this.nullableInt.isDefined) {
      val v = this.nullableInt.get;
      output.writeTag(4, 2);
      output.writeUInt32NoTag(CodedOutputStream.computeInt32Size(1, v));
      output.writeInt32(1, v);
    }
    if (this.nullableBool.isDefined) {
      val v = this.nullableBool.get;
      output.writeTag(5, 2);
      output.writeUInt32NoTag(CodedOutputStream.computeBoolSize(1, v));
      output.writeBool(1, v);
    }
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeTagSize(1) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeInt64Size(1, this.createdAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.createdAt.getNano())) + CodedOutputStream.computeInt64Size(1, this.createdAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.createdAt.getNano())
    size = size + CodedOutputStream.computeTagSize(2) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeInt64Size(1, this.ttl.getSeconds()) + CodedOutputStream.computeInt32Size(2, this.ttl.getNano())) + CodedOutputStream.computeInt64Size(1, this.ttl.getSeconds()) + CodedOutputStream.computeInt32Size(2, this.ttl.getNano())
    if (this.nullableString.isDefined) {
      val v = this.nullableString.get;
      size = size + CodedOutputStream.computeTagSize(3) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeStringSize(1, v)) + CodedOutputStream.computeStringSize(1, v);
    }
    if (this.nullableInt.isDefined) {
      val v = this.nullableInt.get;
      size = size + CodedOutputStream.computeTagSize(4) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeInt32Size(1, v)) + CodedOutputStream.computeInt32Size(1, v);
    }
    if (this.nullableBool.isDefined) {
      val v = this.nullableBool.get;
      size = size + CodedOutputStream.computeTagSize(5) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeBoolSize(1, v)) + CodedOutputStream.computeBoolSize(1, v);
    }
    return size
  }
}

object WellKnownTypesMessage {
  given marshaller: Marshaller[WellKnownTypesMessage] = {
    new Marshaller[WellKnownTypesMessage] {
      override def stream(value: WellKnownTypesMessage): InputStream = {
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
      override def parse(stream: InputStream): WellKnownTypesMessage = {
        try {
          return WellKnownTypesMessage.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): WellKnownTypesMessage = {
    var createdAt: Instant = Instant.EPOCH
    var ttl: Duration = Duration.ZERO
    var nullableString: Option[String] = None
    var nullableInt: Option[Int] = None
    var nullableBool: Option[Boolean] = None
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      var `_tsSeconds` = 0L;
      var `_tsNanos` = 0;
      while (!input.isAtEnd()) {
        val `_tsTag` = input.readTag()
        if (WireFormat.getTagFieldNumber(`_tsTag`) == 1) { `_tsSeconds` = input.readInt64() }
        else if (WireFormat.getTagFieldNumber(`_tsTag`) == 2) { `_tsNanos` = input.readInt32() }
        else { input.skipField(`_tsTag`) }
      };
      createdAt = Instant.ofEpochSecond(`_tsSeconds`, `_tsNanos`.toLong);
      input.popLimit(`_oldLimit`); }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      var `_durSeconds` = 0L;
      var `_durNanos` = 0;
      while (!input.isAtEnd()) {
        val `_durTag` = input.readTag()
        if (WireFormat.getTagFieldNumber(`_durTag`) == 1) { `_durSeconds` = input.readInt64() }
        else if (WireFormat.getTagFieldNumber(`_durTag`) == 2) { `_durNanos` = input.readInt32() }
        else { input.skipField(`_durTag`) }
      };
      ttl = Duration.ofSeconds(`_durSeconds`, `_durNanos`.toLong);
      input.popLimit(`_oldLimit`); }
      else if (WireFormat.getTagFieldNumber(tag) == 3) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      input.readTag();
      nullableString = Some(input.readString());
      input.popLimit(`_oldLimit`); }
      else if (WireFormat.getTagFieldNumber(tag) == 4) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      input.readTag();
      nullableInt = Some(input.readInt32());
      input.popLimit(`_oldLimit`); }
      else if (WireFormat.getTagFieldNumber(tag) == 5) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      input.readTag();
      nullableBool = Some(input.readBool());
      input.popLimit(`_oldLimit`); }
      else { input.skipField(tag) }
    }
    return new WellKnownTypesMessage(
      createdAt,
      ttl,
      nullableString,
      nullableInt,
      nullableBool
    )
  }
}