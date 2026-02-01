package com.example.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException
import java.util.ArrayList
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableMap

data class Inventory(
  val warehouseId: kotlin.String,
  val productIds: List<kotlin.String>,
  val stockCounts: Map<kotlin.String, Int>,
  val recentOrders: List<Order>
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.warehouseId)
    for (elem: kotlin.String in this.productIds) {
      size = size + CodedOutputStream.computeStringSize(2, elem)
    }
    for ((k, v) in this.stockCounts) {
      size = size + CodedOutputStream.computeTagSize(3) + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeStringSize(1, k) + CodedOutputStream.computeInt32Size(2, v)) + CodedOutputStream.computeStringSize(1, k) + CodedOutputStream.computeInt32Size(2, v);
    }
    for (elem: Order in this.recentOrders) {
      size = size + CodedOutputStream.computeTagSize(4) + CodedOutputStream.computeUInt32SizeNoTag(elem.getSerializedSize()) + elem.getSerializedSize()
    }
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.warehouseId)
    for (elem: kotlin.String in this.productIds) {
      output.writeString(2, elem)
    }
    for ((k, v) in this.stockCounts) {
      output.writeTag(3, 2);
      output.writeUInt32NoTag(CodedOutputStream.computeStringSize(1, k) + CodedOutputStream.computeInt32Size(2, v));
      output.writeString(1, k);
      output.writeInt32(2, v);
    }
    for (elem: Order in this.recentOrders) {
      output.writeTag(4, 2)
      output.writeUInt32NoTag(elem.getSerializedSize())
      elem.writeTo(output)
    }
  }

  companion object {
    val MARSHALLER: Marshaller<Inventory> =
      object : Marshaller<Inventory> {
        override fun stream(value: Inventory): InputStream {
          val bytes = ByteArray(value.getSerializedSize())
          val cos = CodedOutputStream.newInstance(bytes)
          try {
            value.writeTo(cos)
            cos.flush()
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
          return ByteArrayInputStream(bytes)
        }
        override fun parse(stream: InputStream): Inventory {
          try {
            return Inventory.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): Inventory {
      var warehouseId: kotlin.String = ""
      var productIds: ArrayList<kotlin.String> = ArrayList()
      var stockCounts: MutableMap<kotlin.String, Int> = mutableMapOf<kotlin.String, Int>()
      var recentOrders: ArrayList<Order> = ArrayList()
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { warehouseId = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { productIds.add(input.readString()) }
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
        stockCounts[mapKey] = mapValue; }
        else if (WireFormat.getTagFieldNumber(tag) == 4) { val _length = input.readRawVarint32();
        val _oldLimit = input.pushLimit(_length);
        recentOrders.add(Order.parseFrom(input));
        input.popLimit(_oldLimit); }
        else { input.skipField(tag) }
      }
      return Inventory(warehouseId, productIds, stockCounts.toMap(), recentOrders)
    }
  }
}