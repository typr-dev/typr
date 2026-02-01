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

data class PaymentMethod(
  val id: kotlin.String,
  val method: PaymentMethodMethod?
) {
  fun getSerializedSize(): Int {
    var size: Int = 0
    size = size + CodedOutputStream.computeStringSize(1, this.id)
    when (val __r = this.method) {
      null -> {}
      is CreditCardValue -> { val c = __r; size = size + CodedOutputStream.computeTagSize(2) + CodedOutputStream.computeUInt32SizeNoTag(c.creditCard.getSerializedSize()) + c.creditCard.getSerializedSize() }
      is BankTransferValue -> { val c = __r; size = size + CodedOutputStream.computeTagSize(3) + CodedOutputStream.computeUInt32SizeNoTag(c.bankTransfer.getSerializedSize()) + c.bankTransfer.getSerializedSize() }
      is WalletValue -> { val c = __r; size = size + CodedOutputStream.computeTagSize(4) + CodedOutputStream.computeUInt32SizeNoTag(c.wallet.getSerializedSize()) + c.wallet.getSerializedSize() }
    }
    return size
  }

  @Throws(IOException::class)
  fun writeTo(output: CodedOutputStream) {
    output.writeString(1, this.id)
    when (val __r = this.method) {
      null -> {}
      is CreditCardValue -> {
        val c = __r
        output.writeTag(2, 2);
          output.writeUInt32NoTag(c.creditCard.getSerializedSize());
          c.creditCard.writeTo(output);
      }
      is BankTransferValue -> {
        val c = __r
        output.writeTag(3, 2);
          output.writeUInt32NoTag(c.bankTransfer.getSerializedSize());
          c.bankTransfer.writeTo(output);
      }
      is WalletValue -> {
        val c = __r
        output.writeTag(4, 2);
          output.writeUInt32NoTag(c.wallet.getSerializedSize());
          c.wallet.writeTo(output);
      }
    }
  }

  companion object {
    val MARSHALLER: Marshaller<PaymentMethod> =
      object : Marshaller<PaymentMethod> {
        override fun stream(value: PaymentMethod): InputStream {
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
        override fun parse(stream: InputStream): PaymentMethod {
          try {
            return PaymentMethod.parseFrom(CodedInputStream.newInstance(stream))
          } catch (e: IOException) {
            throw RuntimeException(e)
          } 
        }
      }

    @Throws(IOException::class)
    fun parseFrom(input: CodedInputStream): PaymentMethod {
      var id: kotlin.String = ""
      var method: PaymentMethodMethod? = null
      while (!input.isAtEnd()) {
        val tag = input.readTag()
        if (WireFormat.getTagFieldNumber(tag) == 1) { id = input.readString() }
        else if (WireFormat.getTagFieldNumber(tag) == 2) { val _length = input.readRawVarint32();
        val _oldLimit = input.pushLimit(_length);
        method = CreditCardValue(CreditCard.parseFrom(input));
        input.popLimit(_oldLimit); }
        else if (WireFormat.getTagFieldNumber(tag) == 3) { val _length = input.readRawVarint32();
        val _oldLimit = input.pushLimit(_length);
        method = BankTransferValue(BankTransfer.parseFrom(input));
        input.popLimit(_oldLimit); }
        else if (WireFormat.getTagFieldNumber(tag) == 4) { val _length = input.readRawVarint32();
        val _oldLimit = input.pushLimit(_length);
        method = WalletValue(Wallet.parseFrom(input));
        input.popLimit(_oldLimit); }
        else { input.skipField(tag) }
      }
      return PaymentMethod(id, method)
    }
  }
}