package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException
import scala.collection.mutable.ListBuffer

case class Inventory(
  warehouseId: String,
  productIds: List[String],
  stockCounts: Map[String, Int],
  recentOrders: List[Order]
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.warehouseId)
    for (elem <- this.productIds) {
      output.writeString(2, elem);
    }
    for ((k, v) <- this.stockCounts) {
      output.writeTag(3, 2);
      output.writeUInt32NoTag(CodedOutputStream.computeStringSize(1, k) + CodedOutputStream.computeInt32Size(2, v));
      output.writeString(1, k);
      output.writeInt32(2, v);
    }
    for (elem <- this.recentOrders) {
      output.writeTag(4, 2);
      output.writeUInt32NoTag(elem.getSerializedSize);
      elem.writeTo(output);
    }
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.warehouseId)
    for (elem <- this.productIds) {
      size = size + CodedOutputStream.computeStringSize(2, elem);
    }
    for ((k, v) <- this.stockCounts) {
      size = size + CodedOutputStream.computeTagSize(3) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeStringSize(1, k) + CodedOutputStream.computeInt32Size(2, v)) + CodedOutputStream.computeStringSize(1, k) + CodedOutputStream.computeInt32Size(2, v);
    }
    for (elem <- this.recentOrders) {
      size = size + CodedOutputStream.computeTagSize(4) + CodedOutputStream.computeUInt32SizeNoTag(elem.getSerializedSize) + elem.getSerializedSize;
    }
    return size
  }
}

object Inventory {
  given marshaller: Marshaller[Inventory] = {
    new Marshaller[Inventory] {
      override def stream(value: Inventory): InputStream = {
        val bytes = Array.ofDim[Byte](value.getSerializedSize)
        val cos = CodedOutputStream.newInstance(bytes)
        try {
          value.writeTo(cos)
          cos.flush()
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
        return new ByteArrayInputStream(bytes)
      }
      override def parse(stream: InputStream): Inventory = {
        try {
          return Inventory.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): Inventory = {
    var warehouseId: String = ""
    var productIds: ListBuffer[String] = ListBuffer()
    var stockCounts: scala.collection.mutable.Map[String, Int] = scala.collection.mutable.Map.empty[String, Int]
    var recentOrders: ListBuffer[Order] = ListBuffer()
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { warehouseId = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { productIds.addOne(input.readString()): @scala.annotation.nowarn }
      else if (WireFormat.getTagFieldNumber(tag) == 3) { val length = input.readRawVarint32();
      val oldLimit = input.pushLimit(length);
      var mapKey = "";
      var mapValue = 0;
      while (!input.isAtEnd()) {
        val entryTag = input.readTag()
        if (WireFormat.getTagFieldNumber(entryTag) == 1) { mapKey = input.readString() }
        else if (WireFormat.getTagFieldNumber(entryTag) == 2) { mapValue = input.readInt32() }
        else { input.skipField(entryTag) }
      };
      input.popLimit(oldLimit);
      stockCounts.put(mapKey, mapValue): @scala.annotation.nowarn; }
      else if (WireFormat.getTagFieldNumber(tag) == 4) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      recentOrders.addOne(Order.parseFrom(input)): @scala.annotation.nowarn;
      input.popLimit(`_oldLimit`); }
      else { input.skipField(tag) }
    }
    return new Inventory(
      warehouseId,
      productIds.toList,
      stockCounts.toMap,
      recentOrders.toList
    )
  }
}