package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record GetCustomerRequest(String customerId) {
  public GetCustomerRequest withCustomerId(String customerId) {
    return new GetCustomerRequest(customerId);
  }

  public static Marshaller<GetCustomerRequest> MARSHALLER =
      new Marshaller<GetCustomerRequest>() {
        @Override
        public InputStream stream(GetCustomerRequest value) {
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
        public GetCustomerRequest parse(InputStream stream) {
          try {
            return GetCustomerRequest.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static GetCustomerRequest parseFrom(CodedInputStream input) throws IOException {
    String customerId = "";
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        customerId = input.readString();
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new GetCustomerRequest(customerId);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.customerId());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.customerId());
  }
}
