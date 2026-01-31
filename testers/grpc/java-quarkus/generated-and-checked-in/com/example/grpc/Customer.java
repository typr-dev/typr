package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record Customer(CustomerId customerId, String name, String email) {
  public Customer withCustomerId(CustomerId customerId) {
    return new Customer(customerId, name, email);
  }

  public Customer withName(String name) {
    return new Customer(customerId, name, email);
  }

  public Customer withEmail(String email) {
    return new Customer(customerId, name, email);
  }

  public static Marshaller<Customer> MARSHALLER =
      new Marshaller<Customer>() {
        @Override
        public InputStream stream(Customer value) {
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
        public Customer parse(InputStream stream) {
          try {
            return Customer.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static Customer parseFrom(CodedInputStream input) throws IOException {
    CustomerId customerId = CustomerId.valueOf("");
    String name = "";
    String email = "";
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        customerId = CustomerId.valueOf(input.readString());
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        name = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        email = input.readString();
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new Customer(customerId, name, email);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.customerId().unwrap());
    size = size + CodedOutputStream.computeStringSize(2, this.name());
    size = size + CodedOutputStream.computeStringSize(3, this.email());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.customerId().unwrap());
    output.writeString(2, this.name());
    output.writeString(3, this.email());
  }
}
