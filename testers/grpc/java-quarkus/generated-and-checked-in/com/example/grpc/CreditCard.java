package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record CreditCard(String cardNumber, String expiryDate, String cvv) {
  public CreditCard withCardNumber(String cardNumber) {
    return new CreditCard(cardNumber, expiryDate, cvv);
  }

  public CreditCard withExpiryDate(String expiryDate) {
    return new CreditCard(cardNumber, expiryDate, cvv);
  }

  public CreditCard withCvv(String cvv) {
    return new CreditCard(cardNumber, expiryDate, cvv);
  }

  public static Marshaller<CreditCard> MARSHALLER =
      new Marshaller<CreditCard>() {
        @Override
        public InputStream stream(CreditCard value) {
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
        public CreditCard parse(InputStream stream) {
          try {
            return CreditCard.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static CreditCard parseFrom(CodedInputStream input) throws IOException {
    String cardNumber = "";
    String expiryDate = "";
    String cvv = "";
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        cardNumber = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        expiryDate = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        cvv = input.readString();
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new CreditCard(cardNumber, expiryDate, cvv);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.cardNumber());
    size = size + CodedOutputStream.computeStringSize(2, this.expiryDate());
    size = size + CodedOutputStream.computeStringSize(3, this.cvv());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.cardNumber());
    output.writeString(2, this.expiryDate());
    output.writeString(3, this.cvv());
  }
}
