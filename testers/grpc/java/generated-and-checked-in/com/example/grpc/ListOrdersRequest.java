package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record ListOrdersRequest(String customerId, Integer pageSize) {
  public ListOrdersRequest withCustomerId(String customerId) {
    return new ListOrdersRequest(customerId, pageSize);
  }

  public ListOrdersRequest withPageSize(Integer pageSize) {
    return new ListOrdersRequest(customerId, pageSize);
  }

  public static Marshaller<ListOrdersRequest> MARSHALLER =
      new Marshaller<ListOrdersRequest>() {
        @Override
        public InputStream stream(ListOrdersRequest value) {
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
        public ListOrdersRequest parse(InputStream stream) {
          try {
            return ListOrdersRequest.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static ListOrdersRequest parseFrom(CodedInputStream input) throws IOException {
    String customerId = "";
    Integer pageSize = 0;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        customerId = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        pageSize = input.readInt32();
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new ListOrdersRequest(customerId, pageSize);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.customerId());
    size = size + CodedOutputStream.computeInt32Size(2, this.pageSize());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.customerId());
    output.writeInt32(2, this.pageSize());
  }
}
