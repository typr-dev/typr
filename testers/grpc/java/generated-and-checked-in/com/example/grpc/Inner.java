package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record Inner(Integer value, String description) {
  public Inner withValue(Integer value) {
    return new Inner(value, description);
  }

  public Inner withDescription(String description) {
    return new Inner(value, description);
  }

  public static Marshaller<Inner> MARSHALLER =
      new Marshaller<Inner>() {
        @Override
        public InputStream stream(Inner value) {
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
        public Inner parse(InputStream stream) {
          try {
            return Inner.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static Inner parseFrom(CodedInputStream input) throws IOException {
    Integer value = 0;
    String description = "";
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        value = input.readInt32();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        description = input.readString();
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new Inner(value, description);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeInt32Size(1, this.value());
    size = size + CodedOutputStream.computeStringSize(2, this.description());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeInt32(1, this.value());
    output.writeString(2, this.description());
  }
}
