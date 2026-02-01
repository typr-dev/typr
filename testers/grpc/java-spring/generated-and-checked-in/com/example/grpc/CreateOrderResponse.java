package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record CreateOrderResponse(String orderId, OrderStatus status) {
  public CreateOrderResponse withOrderId(String orderId) {
    return new CreateOrderResponse(orderId, status);
  }

  public CreateOrderResponse withStatus(OrderStatus status) {
    return new CreateOrderResponse(orderId, status);
  }

  public static Marshaller<CreateOrderResponse> MARSHALLER =
      new Marshaller<CreateOrderResponse>() {
        @Override
        public InputStream stream(CreateOrderResponse value) {
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
        public CreateOrderResponse parse(InputStream stream) {
          try {
            return CreateOrderResponse.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static CreateOrderResponse parseFrom(CodedInputStream input) throws IOException {
    String orderId = "";
    OrderStatus status = OrderStatus.fromValue(0);
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        orderId = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        status = OrderStatus.fromValue(input.readEnum());
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new CreateOrderResponse(orderId, status);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.orderId());
    size = size + CodedOutputStream.computeEnumSize(2, this.status().toValue());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.orderId());
    output.writeEnum(2, this.status().toValue());
  }
}
