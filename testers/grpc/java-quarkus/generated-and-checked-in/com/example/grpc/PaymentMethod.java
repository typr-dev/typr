package com.example.grpc;

import com.example.grpc.PaymentMethodMethod.BankTransferValue;
import com.example.grpc.PaymentMethodMethod.CreditCardValue;
import com.example.grpc.PaymentMethodMethod.WalletValue;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record PaymentMethod(String id, PaymentMethodMethod method) {
  public PaymentMethod withId(String id) {
    return new PaymentMethod(id, method);
  }

  public PaymentMethod withMethod(PaymentMethodMethod method) {
    return new PaymentMethod(id, method);
  }

  public static Marshaller<PaymentMethod> MARSHALLER =
      new Marshaller<PaymentMethod>() {
        @Override
        public InputStream stream(PaymentMethod value) {
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
        public PaymentMethod parse(InputStream stream) {
          try {
            return PaymentMethod.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static PaymentMethod parseFrom(CodedInputStream input) throws IOException {
    String id = "";
    PaymentMethodMethod method = null;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        id = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        method = new CreditCardValue(CreditCard.parseFrom(input));
        input.popLimit(_oldLimit);
        ;
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        method = new BankTransferValue(BankTransfer.parseFrom(input));
        input.popLimit(_oldLimit);
        ;
      } else if (WireFormat.getTagFieldNumber(tag) == 4) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        method = new WalletValue(Wallet.parseFrom(input));
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new PaymentMethod(id, method);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.id());
    switch (this.method()) {
      case null -> {}
      case CreditCardValue c ->
          size =
              size
                  + CodedOutputStream.computeTagSize(2)
                  + CodedOutputStream.computeUInt32SizeNoTag(c.creditCard().getSerializedSize())
                  + c.creditCard().getSerializedSize();
      case BankTransferValue c ->
          size =
              size
                  + CodedOutputStream.computeTagSize(3)
                  + CodedOutputStream.computeUInt32SizeNoTag(c.bankTransfer().getSerializedSize())
                  + c.bankTransfer().getSerializedSize();
      case WalletValue c ->
          size =
              size
                  + CodedOutputStream.computeTagSize(4)
                  + CodedOutputStream.computeUInt32SizeNoTag(c.wallet().getSerializedSize())
                  + c.wallet().getSerializedSize();
    }
    ;
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.id());
    switch (this.method()) {
      case null -> {}
      case CreditCardValue c -> {
        output.writeTag(2, 2);
        output.writeUInt32NoTag(c.creditCard().getSerializedSize());
        c.creditCard().writeTo(output);
      }
      case BankTransferValue c -> {
        output.writeTag(3, 2);
        output.writeUInt32NoTag(c.bankTransfer().getSerializedSize());
        c.bankTransfer().writeTo(output);
      }
      case WalletValue c -> {
        output.writeTag(4, 2);
        output.writeUInt32NoTag(c.wallet().getSerializedSize());
        c.wallet().writeTo(output);
      }
    }
    ;
  }
}
