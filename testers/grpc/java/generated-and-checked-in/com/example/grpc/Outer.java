package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record Outer(String name, Inner inner) {
  public Outer withName(String name) {
    return new Outer(name, inner);
  }

  public Outer withInner(Inner inner) {
    return new Outer(name, inner);
  }

  public static Marshaller<Outer> MARSHALLER =
      new Marshaller<Outer>() {
        @Override
        public InputStream stream(Outer value) {
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
        public Outer parse(InputStream stream) {
          try {
            return Outer.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static Outer parseFrom(CodedInputStream input) throws IOException {
    String name = "";
    Inner inner = null;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        name = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        inner = Inner.parseFrom(input);
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new Outer(name, inner);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.name());
    if (!this.inner().equals(null)) {
      size =
          size
              + CodedOutputStream.computeTagSize(2)
              + CodedOutputStream.computeUInt32SizeNoTag(this.inner().getSerializedSize())
              + this.inner().getSerializedSize();
    }
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.name());
    if (!this.inner().equals(null)) {
      output.writeTag(2, 2);
      output.writeUInt32NoTag(this.inner().getSerializedSize());
      this.inner().writeTo(output);
      ;
    }
  }
}
