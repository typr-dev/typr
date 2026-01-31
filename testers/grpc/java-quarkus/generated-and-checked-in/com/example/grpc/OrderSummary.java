package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record OrderSummary(Integer totalOrders, Long totalAmountCents) {
  public OrderSummary withTotalOrders(Integer totalOrders) {
    return new OrderSummary(totalOrders, totalAmountCents);
  }

  public OrderSummary withTotalAmountCents(Long totalAmountCents) {
    return new OrderSummary(totalOrders, totalAmountCents);
  }

  public static Marshaller<OrderSummary> MARSHALLER =
      new Marshaller<OrderSummary>() {
        @Override
        public InputStream stream(OrderSummary value) {
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
        public OrderSummary parse(InputStream stream) {
          try {
            return OrderSummary.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static OrderSummary parseFrom(CodedInputStream input) throws IOException {
    Integer totalOrders = 0;
    Long totalAmountCents = 0L;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        totalOrders = input.readInt32();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        totalAmountCents = input.readInt64();
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new OrderSummary(totalOrders, totalAmountCents);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeInt32Size(1, this.totalOrders());
    size = size + CodedOutputStream.computeInt64Size(2, this.totalAmountCents());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeInt32(1, this.totalOrders());
    output.writeInt64(2, this.totalAmountCents());
  }
}
