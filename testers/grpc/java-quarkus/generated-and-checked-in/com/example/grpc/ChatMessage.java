package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

public record ChatMessage(String sender, String content, Instant sentAt) {
  public ChatMessage withSender(String sender) {
    return new ChatMessage(sender, content, sentAt);
  }

  public ChatMessage withContent(String content) {
    return new ChatMessage(sender, content, sentAt);
  }

  public ChatMessage withSentAt(Instant sentAt) {
    return new ChatMessage(sender, content, sentAt);
  }

  public static Marshaller<ChatMessage> MARSHALLER =
      new Marshaller<ChatMessage>() {
        @Override
        public InputStream stream(ChatMessage value) {
          var bytes = new byte[value.getSerializedSize()];
          var cos = CodedOutputStream.newInstance(bytes);
          try {
            value.writeTo(cos);
            cos.flush();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          return new ByteArrayInputStream(bytes);
        }

        @Override
        public ChatMessage parse(InputStream stream) {
          try {
            return ChatMessage.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static ChatMessage parseFrom(CodedInputStream input) throws IOException {
    String sender = "";
    String content = "";
    Instant sentAt = Instant.EPOCH;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        sender = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        content = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        var _tsSeconds = 0L;
        var _tsNanos = 0;
        while (!input.isAtEnd()) {
          var _tsTag = input.readTag();
          if (WireFormat.getTagFieldNumber(_tsTag) == 1) {
            _tsSeconds = input.readInt64();
          } else if (WireFormat.getTagFieldNumber(_tsTag) == 2) {
            _tsNanos = input.readInt32();
          } else {
            input.skipField(_tsTag);
          }
          ;
        }
        ;
        sentAt = Instant.ofEpochSecond(_tsSeconds, (long) (_tsNanos));
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new ChatMessage(sender, content, sentAt);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.sender());
    size = size + CodedOutputStream.computeStringSize(2, this.content());
    size =
        size
            + CodedOutputStream.computeTagSize(3)
            + CodedOutputStream.computeUInt32SizeNoTag(
                CodedOutputStream.computeInt64Size(1, this.sentAt().getEpochSecond())
                    + CodedOutputStream.computeInt32Size(2, this.sentAt().getNano()))
            + CodedOutputStream.computeInt64Size(1, this.sentAt().getEpochSecond())
            + CodedOutputStream.computeInt32Size(2, this.sentAt().getNano());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.sender());
    output.writeString(2, this.content());
    output.writeTag(3, 2);
    output.writeUInt32NoTag(
        CodedOutputStream.computeInt64Size(1, this.sentAt().getEpochSecond())
            + CodedOutputStream.computeInt32Size(2, this.sentAt().getNano()));
    output.writeInt64(1, this.sentAt().getEpochSecond());
    output.writeInt32(2, this.sentAt().getNano());
  }
}
