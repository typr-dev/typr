package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public record Inventory(
    String warehouseId,
    List<String> productIds,
    Map<String, Integer> stockCounts,
    List<Order> recentOrders) {
  public Inventory withWarehouseId(String warehouseId) {
    return new Inventory(warehouseId, productIds, stockCounts, recentOrders);
  }

  public Inventory withProductIds(List<String> productIds) {
    return new Inventory(warehouseId, productIds, stockCounts, recentOrders);
  }

  public Inventory withStockCounts(Map<String, Integer> stockCounts) {
    return new Inventory(warehouseId, productIds, stockCounts, recentOrders);
  }

  public Inventory withRecentOrders(List<Order> recentOrders) {
    return new Inventory(warehouseId, productIds, stockCounts, recentOrders);
  }

  public static Marshaller<Inventory> MARSHALLER =
      new Marshaller<Inventory>() {
        @Override
        public InputStream stream(Inventory value) {
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
        public Inventory parse(InputStream stream) {
          try {
            return Inventory.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static Inventory parseFrom(CodedInputStream input) throws IOException {
    String warehouseId = "";
    ArrayList<String> productIds = new ArrayList<>();
    HashMap<String, Integer> stockCounts = new HashMap<String, Integer>();
    ArrayList<Order> recentOrders = new ArrayList<>();
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        warehouseId = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        productIds.add(input.readString());
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        var length = input.readRawVarint32();
        var oldLimit = input.pushLimit(length);
        var mapKey = "";
        var mapValue = 0;
        while (!input.isAtEnd()) {
          var entryTag = input.readTag();
          if (WireFormat.getTagFieldNumber(entryTag) == 1) {
            mapKey = input.readString();
          } else if (WireFormat.getTagFieldNumber(entryTag) == 2) {
            mapValue = input.readInt32();
          } else {
            input.skipField(entryTag);
          }
          ;
        }
        ;
        input.popLimit(oldLimit);
        stockCounts.put(mapKey, mapValue);
        ;
      } else if (WireFormat.getTagFieldNumber(tag) == 4) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        recentOrders.add(Order.parseFrom(input));
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new Inventory(warehouseId, productIds, stockCounts, recentOrders);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.warehouseId());
    for (String elem : this.productIds()) {
      size = size + CodedOutputStream.computeStringSize(2, elem);
    }
    ;
    for (Entry<String, Integer> entry : this.stockCounts().entrySet()) {
      size =
          size
              + CodedOutputStream.computeTagSize(3)
              + CodedOutputStream.computeUInt32SizeNoTag(
                  CodedOutputStream.computeStringSize(1, entry.getKey())
                      + CodedOutputStream.computeInt32Size(2, entry.getValue()))
              + CodedOutputStream.computeStringSize(1, entry.getKey())
              + CodedOutputStream.computeInt32Size(2, entry.getValue());
    }
    ;
    for (Order elem : this.recentOrders()) {
      size =
          size
              + CodedOutputStream.computeTagSize(4)
              + CodedOutputStream.computeUInt32SizeNoTag(elem.getSerializedSize())
              + elem.getSerializedSize();
    }
    ;
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.warehouseId());
    for (String elem : this.productIds()) {
      output.writeString(2, elem);
    }
    ;
    for (Entry<String, Integer> entry : this.stockCounts().entrySet()) {
      output.writeTag(3, 2);
      output.writeUInt32NoTag(
          CodedOutputStream.computeStringSize(1, entry.getKey())
              + CodedOutputStream.computeInt32Size(2, entry.getValue()));
      output.writeString(1, entry.getKey());
      output.writeInt32(2, entry.getValue());
    }
    ;
    for (Order elem : this.recentOrders()) {
      output.writeTag(4, 2);
      output.writeUInt32NoTag(elem.getSerializedSize());
      elem.writeTo(output);
    }
    ;
  }
}
