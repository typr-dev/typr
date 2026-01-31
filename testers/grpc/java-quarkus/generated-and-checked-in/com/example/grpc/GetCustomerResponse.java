package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record GetCustomerResponse(Customer customer) {
  public GetCustomerResponse withCustomer(Customer customer) {
    return new GetCustomerResponse(customer);
  }

  public static Marshaller<GetCustomerResponse> MARSHALLER =
      new Marshaller<GetCustomerResponse>() {
        @Override
        public InputStream stream(GetCustomerResponse value) {
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
        public GetCustomerResponse parse(InputStream stream) {
          try {
            return GetCustomerResponse.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static GetCustomerResponse parseFrom(CodedInputStream input) throws IOException {
    Customer customer = null;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        customer = Customer.parseFrom(input);
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new GetCustomerResponse(customer);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    if (!this.customer().equals(null)) {
      size =
          size
              + CodedOutputStream.computeTagSize(1)
              + CodedOutputStream.computeUInt32SizeNoTag(this.customer().getSerializedSize())
              + this.customer().getSerializedSize();
    }
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    if (!this.customer().equals(null)) {
      output.writeTag(1, 2);
      output.writeUInt32NoTag(this.customer().getSerializedSize());
      this.customer().writeTo(output);
      ;
    }
  }
}
