package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public record OptionalFields(
    Optional<String> name, Optional<Integer> age, Optional<Customer> customer) {
  public OptionalFields withName(Optional<String> name) {
    return new OptionalFields(name, age, customer);
  }

  public OptionalFields withAge(Optional<Integer> age) {
    return new OptionalFields(name, age, customer);
  }

  public OptionalFields withCustomer(Optional<Customer> customer) {
    return new OptionalFields(name, age, customer);
  }

  public static Marshaller<OptionalFields> MARSHALLER =
      new Marshaller<OptionalFields>() {
        @Override
        public InputStream stream(OptionalFields value) {
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
        public OptionalFields parse(InputStream stream) {
          try {
            return OptionalFields.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static OptionalFields parseFrom(CodedInputStream input) throws IOException {
    Optional<String> name = Optional.empty();
    Optional<Integer> age = Optional.empty();
    Optional<Customer> customer = Optional.empty();
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        name = Optional.of(input.readString());
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        age = Optional.of(input.readInt32());
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        customer = Optional.of(Customer.parseFrom(input));
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new OptionalFields(name, age, customer);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    if (this.name().isPresent()) {
      var v = this.name().get();
      size = size + CodedOutputStream.computeStringSize(1, v);
      ;
    }
    if (this.age().isPresent()) {
      var v = this.age().get();
      size = size + CodedOutputStream.computeInt32Size(2, v);
      ;
    }
    if (this.customer().isPresent()) {
      var v = this.customer().get();
      size =
          size
              + CodedOutputStream.computeTagSize(3)
              + CodedOutputStream.computeUInt32SizeNoTag(v.getSerializedSize())
              + v.getSerializedSize();
      ;
    }
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    if (this.name().isPresent()) {
      var v = this.name().get();
      output.writeString(1, v);
      ;
    }
    if (this.age().isPresent()) {
      var v = this.age().get();
      output.writeInt32(2, v);
      ;
    }
    if (this.customer().isPresent()) {
      var v = this.customer().get();
      output.writeTag(3, 2);
      output.writeUInt32NoTag(v.getSerializedSize());
      v.writeTo(output);
      ;
    }
  }
}
