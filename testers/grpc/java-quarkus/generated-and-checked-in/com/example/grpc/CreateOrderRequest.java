package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record CreateOrderRequest(Order order) {
  public CreateOrderRequest withOrder(Order order) {
    return new CreateOrderRequest(order);
  }

  public static Marshaller<CreateOrderRequest> MARSHALLER =
      new Marshaller<CreateOrderRequest>() {
        @Override
        public InputStream stream(CreateOrderRequest value) {
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
        public CreateOrderRequest parse(InputStream stream) {
          try {
            return CreateOrderRequest.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static CreateOrderRequest parseFrom(CodedInputStream input) throws IOException {
    Order order = null;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        order = Order.parseFrom(input);
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new CreateOrderRequest(order);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    if (!this.order().equals(null)) {
      size =
          size
              + CodedOutputStream.computeTagSize(1)
              + CodedOutputStream.computeUInt32SizeNoTag(this.order().getSerializedSize())
              + this.order().getSerializedSize();
    }
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    if (!this.order().equals(null)) {
      output.writeTag(1, 2);
      output.writeUInt32NoTag(this.order().getSerializedSize());
      this.order().writeTo(output);
      ;
    }
  }
}
