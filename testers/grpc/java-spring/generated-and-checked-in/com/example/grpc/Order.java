package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

public record Order(OrderId orderId, CustomerId customerId, Long amountCents, Instant createdAt) {
  public Order withOrderId(OrderId orderId) {
    return new Order(orderId, customerId, amountCents, createdAt);
  }

  public Order withCustomerId(CustomerId customerId) {
    return new Order(orderId, customerId, amountCents, createdAt);
  }

  public Order withAmountCents(Long amountCents) {
    return new Order(orderId, customerId, amountCents, createdAt);
  }

  public Order withCreatedAt(Instant createdAt) {
    return new Order(orderId, customerId, amountCents, createdAt);
  }

  public static Marshaller<Order> MARSHALLER =
      new Marshaller<Order>() {
        @Override
        public InputStream stream(Order value) {
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
        public Order parse(InputStream stream) {
          try {
            return Order.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static Order parseFrom(CodedInputStream input) throws IOException {
    OrderId orderId = OrderId.valueOf("");
    CustomerId customerId = CustomerId.valueOf("");
    Long amountCents = 0L;
    Instant createdAt = Instant.EPOCH;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        orderId = OrderId.valueOf(input.readString());
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        customerId = CustomerId.valueOf(input.readString());
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        amountCents = input.readInt64();
      } else if (WireFormat.getTagFieldNumber(tag) == 4) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        var _tsSeconds = 0L;
        var _tsNanos = 0;
        while (!input.isAtEnd()) {
          var _tsTag = input.readTag();
          if (WireFormat.getTagFieldNumber(_tsTag) == 1) {
            _tsSeconds = input.readInt64();
          } else if (WireFormat.getTagFieldNumber(_tsTag) == 2) {
            _tsNanos = input.readInt32();
          } else {
            input.skipField(_tsTag);
          }
          ;
        }
        ;
        createdAt = Instant.ofEpochSecond(_tsSeconds, (long) (_tsNanos));
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new Order(orderId, customerId, amountCents, createdAt);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.orderId().unwrap());
    size = size + CodedOutputStream.computeStringSize(2, this.customerId().unwrap());
    size = size + CodedOutputStream.computeInt64Size(3, this.amountCents());
    size =
        size
            + CodedOutputStream.computeTagSize(4)
            + CodedOutputStream.computeUInt32SizeNoTag(
                CodedOutputStream.computeInt64Size(1, this.createdAt().getEpochSecond())
                    + CodedOutputStream.computeInt32Size(2, this.createdAt().getNano()))
            + CodedOutputStream.computeInt64Size(1, this.createdAt().getEpochSecond())
            + CodedOutputStream.computeInt32Size(2, this.createdAt().getNano());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.orderId().unwrap());
    output.writeString(2, this.customerId().unwrap());
    output.writeInt64(3, this.amountCents());
    output.writeTag(4, 2);
    output.writeUInt32NoTag(
        CodedOutputStream.computeInt64Size(1, this.createdAt().getEpochSecond())
            + CodedOutputStream.computeInt32Size(2, this.createdAt().getNano()));
    output.writeInt64(1, this.createdAt().getEpochSecond());
    output.writeInt32(2, this.createdAt().getNano());
  }
}
