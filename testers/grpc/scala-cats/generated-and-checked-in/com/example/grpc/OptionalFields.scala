package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class OptionalFields(
  name: Option[String],
  age: Option[Int],
  customer: Option[Customer]
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    if (this.name.isDefined) {
      val v = this.name.get;
      output.writeString(1, v);
    }
    if (this.age.isDefined) {
      val v = this.age.get;
      output.writeInt32(2, v);
    }
    if (this.customer.isDefined) {
      val v = this.customer.get;
      output.writeTag(3, 2);
      output.writeUInt32NoTag(v.getSerializedSize);
      v.writeTo(output);
    }
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    if (this.name.isDefined) {
      val v = this.name.get;
      size = size + CodedOutputStream.computeStringSize(1, v);
    }
    if (this.age.isDefined) {
      val v = this.age.get;
      size = size + CodedOutputStream.computeInt32Size(2, v);
    }
    if (this.customer.isDefined) {
      val v = this.customer.get;
      size = size + CodedOutputStream.computeTagSize(3) + CodedOutputStream.computeUInt32SizeNoTag(v.getSerializedSize) + v.getSerializedSize;
    }
    return size
  }
}

object OptionalFields {
  given marshaller: Marshaller[OptionalFields] = {
    new Marshaller[OptionalFields] {
      override def stream(value: OptionalFields): InputStream = {
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
      override def parse(stream: InputStream): OptionalFields = {
        try {
          return OptionalFields.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): OptionalFields = {
    var name: Option[String] = None
    var age: Option[Int] = None
    var customer: Option[Customer] = None
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { name = Some(input.readString()) }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { age = Some(input.readInt32()) }
      else if (WireFormat.getTagFieldNumber(tag) == 3) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      customer = Some(Customer.parseFrom(input));
      input.popLimit(`_oldLimit`); }
      else { input.skipField(tag) }
    }
    return new OptionalFields(name, age, customer)
  }
}