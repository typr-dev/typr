package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record BankTransfer(String accountNumber, String routingNumber) {
  public BankTransfer withAccountNumber(String accountNumber) {
    return new BankTransfer(accountNumber, routingNumber);
  }

  public BankTransfer withRoutingNumber(String routingNumber) {
    return new BankTransfer(accountNumber, routingNumber);
  }

  public static Marshaller<BankTransfer> MARSHALLER =
      new Marshaller<BankTransfer>() {
        @Override
        public InputStream stream(BankTransfer value) {
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
        public BankTransfer parse(InputStream stream) {
          try {
            return BankTransfer.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static BankTransfer parseFrom(CodedInputStream input) throws IOException {
    String accountNumber = "";
    String routingNumber = "";
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        accountNumber = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        routingNumber = input.readString();
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new BankTransfer(accountNumber, routingNumber);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.accountNumber());
    size = size + CodedOutputStream.computeStringSize(2, this.routingNumber());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.accountNumber());
    output.writeString(2, this.routingNumber());
  }
}
