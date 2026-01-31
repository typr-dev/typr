package com.example.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public record WellKnownTypesMessage(
    Instant createdAt,
    Duration ttl,
    Optional<String> nullableString,
    Optional<Integer> nullableInt,
    Optional<Boolean> nullableBool) {
  public WellKnownTypesMessage withCreatedAt(Instant createdAt) {
    return new WellKnownTypesMessage(createdAt, ttl, nullableString, nullableInt, nullableBool);
  }

  public WellKnownTypesMessage withTtl(Duration ttl) {
    return new WellKnownTypesMessage(createdAt, ttl, nullableString, nullableInt, nullableBool);
  }

  public WellKnownTypesMessage withNullableString(Optional<String> nullableString) {
    return new WellKnownTypesMessage(createdAt, ttl, nullableString, nullableInt, nullableBool);
  }

  public WellKnownTypesMessage withNullableInt(Optional<Integer> nullableInt) {
    return new WellKnownTypesMessage(createdAt, ttl, nullableString, nullableInt, nullableBool);
  }

  public WellKnownTypesMessage withNullableBool(Optional<Boolean> nullableBool) {
    return new WellKnownTypesMessage(createdAt, ttl, nullableString, nullableInt, nullableBool);
  }

  public static Marshaller<WellKnownTypesMessage> MARSHALLER =
      new Marshaller<WellKnownTypesMessage>() {
        @Override
        public InputStream stream(WellKnownTypesMessage value) {
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
        public WellKnownTypesMessage parse(InputStream stream) {
          try {
            return WellKnownTypesMessage.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static WellKnownTypesMessage parseFrom(CodedInputStream input) throws IOException {
    Instant createdAt = Instant.EPOCH;
    Duration ttl = Duration.ZERO;
    Optional<String> nullableString = Optional.empty();
    Optional<Integer> nullableInt = Optional.empty();
    Optional<Boolean> nullableBool = Optional.empty();
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
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
        createdAt = Instant.ofEpochSecond(_tsSeconds, (long) (_tsNanos));
        input.popLimit(_oldLimit);
        ;
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        var _durSeconds = 0L;
        var _durNanos = 0;
        while (!input.isAtEnd()) {
          var _durTag = input.readTag();
          if (WireFormat.getTagFieldNumber(_durTag) == 1) {
            _durSeconds = input.readInt64();
          } else if (WireFormat.getTagFieldNumber(_durTag) == 2) {
            _durNanos = input.readInt32();
          } else {
            input.skipField(_durTag);
          }
          ;
        }
        ;
        ttl = Duration.ofSeconds(_durSeconds, (long) (_durNanos));
        input.popLimit(_oldLimit);
        ;
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        input.readTag();
        nullableString = Optional.of(input.readString());
        input.popLimit(_oldLimit);
        ;
      } else if (WireFormat.getTagFieldNumber(tag) == 4) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        input.readTag();
        nullableInt = Optional.of(input.readInt32());
        input.popLimit(_oldLimit);
        ;
      } else if (WireFormat.getTagFieldNumber(tag) == 5) {
        var _length = input.readRawVarint32();
        var _oldLimit = input.pushLimit(_length);
        input.readTag();
        nullableBool = Optional.of(input.readBool());
        input.popLimit(_oldLimit);
        ;
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new WellKnownTypesMessage(createdAt, ttl, nullableString, nullableInt, nullableBool);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size =
        size
            + CodedOutputStream.computeTagSize(1)
            + CodedOutputStream.computeUInt32SizeNoTag(
                CodedOutputStream.computeInt64Size(1, this.createdAt().getEpochSecond())
                    + CodedOutputStream.computeInt32Size(2, this.createdAt().getNano()))
            + CodedOutputStream.computeInt64Size(1, this.createdAt().getEpochSecond())
            + CodedOutputStream.computeInt32Size(2, this.createdAt().getNano());
    size =
        size
            + CodedOutputStream.computeTagSize(2)
            + CodedOutputStream.computeUInt32SizeNoTag(
                CodedOutputStream.computeInt64Size(1, this.ttl().getSeconds())
                    + CodedOutputStream.computeInt32Size(2, this.ttl().getNano()))
            + CodedOutputStream.computeInt64Size(1, this.ttl().getSeconds())
            + CodedOutputStream.computeInt32Size(2, this.ttl().getNano());
    if (this.nullableString().isPresent()) {
      var v = this.nullableString().get();
      size =
          size
              + CodedOutputStream.computeTagSize(3)
              + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeStringSize(1, v))
              + CodedOutputStream.computeStringSize(1, v);
      ;
    }
    if (this.nullableInt().isPresent()) {
      var v = this.nullableInt().get();
      size =
          size
              + CodedOutputStream.computeTagSize(4)
              + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeInt32Size(1, v))
              + CodedOutputStream.computeInt32Size(1, v);
      ;
    }
    if (this.nullableBool().isPresent()) {
      var v = this.nullableBool().get();
      size =
          size
              + CodedOutputStream.computeTagSize(5)
              + CodedOutputStream.computeUInt32SizeNoTag(CodedOutputStream.computeBoolSize(1, v))
              + CodedOutputStream.computeBoolSize(1, v);
      ;
    }
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeTag(1, 2);
    output.writeUInt32NoTag(
        CodedOutputStream.computeInt64Size(1, this.createdAt().getEpochSecond())
            + CodedOutputStream.computeInt32Size(2, this.createdAt().getNano()));
    output.writeInt64(1, this.createdAt().getEpochSecond());
    output.writeInt32(2, this.createdAt().getNano());
    output.writeTag(2, 2);
    output.writeUInt32NoTag(
        CodedOutputStream.computeInt64Size(1, this.ttl().getSeconds())
            + CodedOutputStream.computeInt32Size(2, this.ttl().getNano()));
    output.writeInt64(1, this.ttl().getSeconds());
    output.writeInt32(2, this.ttl().getNano());
    if (this.nullableString().isPresent()) {
      var v = this.nullableString().get();
      output.writeTag(3, 2);
      output.writeUInt32NoTag(CodedOutputStream.computeStringSize(1, v));
      output.writeString(1, v);
      ;
    }
    if (this.nullableInt().isPresent()) {
      var v = this.nullableInt().get();
      output.writeTag(4, 2);
      output.writeUInt32NoTag(CodedOutputStream.computeInt32Size(1, v));
      output.writeInt32(1, v);
      ;
    }
    if (this.nullableBool().isPresent()) {
      var v = this.nullableBool().get();
      output.writeTag(5, 2);
      output.writeUInt32NoTag(CodedOutputStream.computeBoolSize(1, v));
      output.writeBool(1, v);
      ;
    }
  }
}
