package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException
import java.time.Instant

case class ChatMessage(
  sender: String,
  content: String,
  sentAt: Instant
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.sender)
    output.writeString(2, this.content)
    output.writeTag(3, 2)
    output.writeUInt32NoTag(CodedOutputStream.computeInt64Size(1, this.sentAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.sentAt.getNano()))
    output.writeInt64(1, this.sentAt.getEpochSecond())
    output.writeInt32(2, this.sentAt.getNano())
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.sender)
    size = size + CodedOutputStream.computeStringSize(2, this.content)
    size = size + CodedOutputStream.computeTagSize(3) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeInt64Size(1, this.sentAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.sentAt.getNano())) + CodedOutputStream.computeInt64Size(1, this.sentAt.getEpochSecond()) + CodedOutputStream.computeInt32Size(2, this.sentAt.getNano())
    return size
  }
}

object ChatMessage {
  given marshaller: Marshaller[ChatMessage] = {
    new Marshaller[ChatMessage] {
      override def stream(value: ChatMessage): InputStream = {
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
      override def parse(stream: InputStream): ChatMessage = {
        try {
          return ChatMessage.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): ChatMessage = {
    var sender: String = ""
    var content: String = ""
    var sentAt: Instant = Instant.EPOCH
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { sender = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { content = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 3) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      var `_tsSeconds` = 0L;
      var `_tsNanos` = 0;
      while (!input.isAtEnd()) {
        val `_tsTag` = input.readTag()
        if (WireFormat.getTagFieldNumber(`_tsTag`) == 1) { `_tsSeconds` = input.readInt64() }
        else if (WireFormat.getTagFieldNumber(`_tsTag`) == 2) { `_tsNanos` = input.readInt32() }
        else { input.skipField(`_tsTag`) }
      };
      sentAt = Instant.ofEpochSecond(`_tsSeconds`, `_tsNanos`.toLong);
      input.popLimit(`_oldLimit`); }
      else { input.skipField(tag) }
    }
    return new ChatMessage(sender, content, sentAt)
  }
}