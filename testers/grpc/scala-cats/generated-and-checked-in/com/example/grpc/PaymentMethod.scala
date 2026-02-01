package com.example.grpc

import com.example.grpc.PaymentMethodMethod.BankTransferValue
import com.example.grpc.PaymentMethodMethod.CreditCardValue
import com.example.grpc.PaymentMethodMethod.WalletValue
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.grpc.MethodDescriptor.Marshaller
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException

case class PaymentMethod(
  id: String,
  method: PaymentMethodMethod
) {
  @throws[IOException]
  def writeTo(output: CodedOutputStream): Unit = {
    output.writeString(1, this.id)
    this.method match {
      case null => {}
      case c: CreditCardValue => {
        output.writeTag(2, 2);
        output.writeUInt32NoTag(c.creditCard.getSerializedSize);
        c.creditCard.writeTo(output);
      }
      case c: BankTransferValue => {
        output.writeTag(3, 2);
        output.writeUInt32NoTag(c.bankTransfer.getSerializedSize);
        c.bankTransfer.writeTo(output);
      }
      case c: WalletValue => {
        output.writeTag(4, 2);
        output.writeUInt32NoTag(c.wallet.getSerializedSize);
        c.wallet.writeTo(output);
      }
    }
  }

  def getSerializedSize: Int = {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.id)
    this.method match {
      case null => {}
      case c: CreditCardValue => size = size + CodedOutputStream.computeTagSize(2) + CodedOutputStream.computeUInt32SizeNoTag(c.creditCard.getSerializedSize) + c.creditCard.getSerializedSize
      case c: BankTransferValue => size = size + CodedOutputStream.computeTagSize(3) + CodedOutputStream.computeUInt32SizeNoTag(c.bankTransfer.getSerializedSize) + c.bankTransfer.getSerializedSize
      case c: WalletValue => size = size + CodedOutputStream.computeTagSize(4) + CodedOutputStream.computeUInt32SizeNoTag(c.wallet.getSerializedSize) + c.wallet.getSerializedSize
    }
    return size
  }
}

object PaymentMethod {
  given marshaller: Marshaller[PaymentMethod] = {
    new Marshaller[PaymentMethod] {
      override def stream(value: PaymentMethod): InputStream = {
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
      override def parse(stream: InputStream): PaymentMethod = {
        try {
          return PaymentMethod.parseFrom(CodedInputStream.newInstance(stream))
        } catch {
          case e: IOException => throw new RuntimeException(e)
        } 
      }
    }
  }

  @throws[IOException]
  def parseFrom(input: CodedInputStream): PaymentMethod = {
    var id: String = ""
    var method: PaymentMethodMethod = null
    while (!input.isAtEnd()) {
      val tag = input.readTag()
      if (WireFormat.getTagFieldNumber(tag) == 1) { id = input.readString() }
      else if (WireFormat.getTagFieldNumber(tag) == 2) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      method = new CreditCardValue(CreditCard.parseFrom(input));
      input.popLimit(`_oldLimit`); }
      else if (WireFormat.getTagFieldNumber(tag) == 3) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      method = new BankTransferValue(BankTransfer.parseFrom(input));
      input.popLimit(`_oldLimit`); }
      else if (WireFormat.getTagFieldNumber(tag) == 4) { val `_length` = input.readRawVarint32();
      val `_oldLimit` = input.pushLimit(`_length`);
      method = new WalletValue(Wallet.parseFrom(input));
      input.popLimit(`_oldLimit`); }
      else { input.skipField(tag) }
    }
    return new PaymentMethod(id, method)
  }
}