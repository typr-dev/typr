package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

public record OrderUpdate(String orderId, OrderStatus status, Instant updatedAt) {
  public OrderUpdate withOrderId(String orderId) {
    return new OrderUpdate(orderId, status, updatedAt);
  }

  public OrderUpdate withStatus(OrderStatus status) {
    return new OrderUpdate(orderId, status, updatedAt);
  }

  public OrderUpdate withUpdatedAt(Instant updatedAt) {
    return new OrderUpdate(orderId, status, updatedAt);
  }

  public static Marshaller<OrderUpdate> MARSHALLER =
      new Marshaller<OrderUpdate>() {
        @Override
        public InputStream stream(OrderUpdate value) {
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
        public OrderUpdate parse(InputStream stream) {
          try {
            return OrderUpdate.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static OrderUpdate parseFrom(CodedInputStream input) throws IOException {
    String orderId = "";
    OrderStatus status = OrderStatus.fromValue(0);
    Instant updatedAt = Instant.EPOCH;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        orderId = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        status = OrderStatus.fromValue(input.readEnum());
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
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
        updatedAt = Instant.ofEpochSecond(_tsSeconds, (long) (_tsNanos));
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new OrderUpdate(orderId, status, updatedAt);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.orderId());
    size = size + CodedOutputStream.computeEnumSize(2, this.status().toValue());
    size =
        size
            + CodedOutputStream.computeTagSize(3)
            + CodedOutputStream.computeUInt32SizeNoTag(
                CodedOutputStream.computeInt64Size(1, this.updatedAt().getEpochSecond())
                    + CodedOutputStream.computeInt32Size(2, this.updatedAt().getNano()))
            + CodedOutputStream.computeInt64Size(1, this.updatedAt().getEpochSecond())
            + CodedOutputStream.computeInt32Size(2, this.updatedAt().getNano());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.orderId());
    output.writeEnum(2, this.status().toValue());
    output.writeTag(3, 2);
    output.writeUInt32NoTag(
        CodedOutputStream.computeInt64Size(1, this.updatedAt().getEpochSecond())
            + CodedOutputStream.computeInt32Size(2, this.updatedAt().getNano()));
    output.writeInt64(1, this.updatedAt().getEpochSecond());
    output.writeInt32(2, this.updatedAt().getNano());
  }
}
