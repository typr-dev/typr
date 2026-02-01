package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record Wallet(String walletId, String provider) {
  public Wallet withWalletId(String walletId) {
    return new Wallet(walletId, provider);
  }

  public Wallet withProvider(String provider) {
    return new Wallet(walletId, provider);
  }

  public static Marshaller<Wallet> MARSHALLER =
      new Marshaller<Wallet>() {
        @Override
        public InputStream stream(Wallet value) {
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
        public Wallet parse(InputStream stream) {
          try {
            return Wallet.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static Wallet parseFrom(CodedInputStream input) throws IOException {
    String walletId = "";
    String provider = "";
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        walletId = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        provider = input.readString();
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new Wallet(walletId, provider);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeStringSize(1, this.walletId());
    size = size + CodedOutputStream.computeStringSize(2, this.provider());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeString(1, this.walletId());
    output.writeString(2, this.provider());
  }
}
