package com.example.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;
import io.grpc.MethodDescriptor.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record ScalarTypes(
    Double doubleVal,
    Float floatVal,
    Integer int32Val,
    Long int64Val,
    Integer uint32Val,
    Long uint64Val,
    Integer sint32Val,
    Long sint64Val,
    Integer fixed32Val,
    Long fixed64Val,
    Integer sfixed32Val,
    Long sfixed64Val,
    Boolean boolVal,
    String stringVal,
    ByteString bytesVal) {
  public ScalarTypes withDoubleVal(Double doubleVal) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withFloatVal(Float floatVal) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withInt32Val(Integer int32Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withInt64Val(Long int64Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withUint32Val(Integer uint32Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withUint64Val(Long uint64Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withSint32Val(Integer sint32Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withSint64Val(Long sint64Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withFixed32Val(Integer fixed32Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withFixed64Val(Long fixed64Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withSfixed32Val(Integer sfixed32Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withSfixed64Val(Long sfixed64Val) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withBoolVal(Boolean boolVal) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withStringVal(String stringVal) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public ScalarTypes withBytesVal(ByteString bytesVal) {
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public static Marshaller<ScalarTypes> MARSHALLER =
      new Marshaller<ScalarTypes>() {
        @Override
        public InputStream stream(ScalarTypes value) {
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
        public ScalarTypes parse(InputStream stream) {
          try {
            return ScalarTypes.parseFrom(CodedInputStream.newInstance(stream));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };

  public static ScalarTypes parseFrom(CodedInputStream input) throws IOException {
    Double doubleVal = 0.0;
    Float floatVal = 0.0f;
    Integer int32Val = 0;
    Long int64Val = 0L;
    Integer uint32Val = 0;
    Long uint64Val = 0L;
    Integer sint32Val = 0;
    Long sint64Val = 0L;
    Integer fixed32Val = 0;
    Long fixed64Val = 0L;
    Integer sfixed32Val = 0;
    Long sfixed64Val = 0L;
    Boolean boolVal = false;
    String stringVal = "";
    ByteString bytesVal = ByteString.EMPTY;
    while (!input.isAtEnd()) {
      var tag = input.readTag();
      if (WireFormat.getTagFieldNumber(tag) == 1) {
        doubleVal = input.readDouble();
      } else if (WireFormat.getTagFieldNumber(tag) == 2) {
        floatVal = input.readFloat();
      } else if (WireFormat.getTagFieldNumber(tag) == 3) {
        int32Val = input.readInt32();
      } else if (WireFormat.getTagFieldNumber(tag) == 4) {
        int64Val = input.readInt64();
      } else if (WireFormat.getTagFieldNumber(tag) == 5) {
        uint32Val = input.readUInt32();
      } else if (WireFormat.getTagFieldNumber(tag) == 6) {
        uint64Val = input.readUInt64();
      } else if (WireFormat.getTagFieldNumber(tag) == 7) {
        sint32Val = input.readSInt32();
      } else if (WireFormat.getTagFieldNumber(tag) == 8) {
        sint64Val = input.readSInt64();
      } else if (WireFormat.getTagFieldNumber(tag) == 9) {
        fixed32Val = input.readFixed32();
      } else if (WireFormat.getTagFieldNumber(tag) == 10) {
        fixed64Val = input.readFixed64();
      } else if (WireFormat.getTagFieldNumber(tag) == 11) {
        sfixed32Val = input.readSFixed32();
      } else if (WireFormat.getTagFieldNumber(tag) == 12) {
        sfixed64Val = input.readSFixed64();
      } else if (WireFormat.getTagFieldNumber(tag) == 13) {
        boolVal = input.readBool();
      } else if (WireFormat.getTagFieldNumber(tag) == 14) {
        stringVal = input.readString();
      } else if (WireFormat.getTagFieldNumber(tag) == 15) {
        bytesVal = input.readBytes();
      } else {
        input.skipField(tag);
      }
      ;
    }
    ;
    return new ScalarTypes(
        doubleVal,
        floatVal,
        int32Val,
        int64Val,
        uint32Val,
        uint64Val,
        sint32Val,
        sint64Val,
        fixed32Val,
        fixed64Val,
        sfixed32Val,
        sfixed64Val,
        boolVal,
        stringVal,
        bytesVal);
  }

  public Integer getSerializedSize() {
    Integer size = 0;
    size = size + CodedOutputStream.computeDoubleSize(1, this.doubleVal());
    size = size + CodedOutputStream.computeFloatSize(2, this.floatVal());
    size = size + CodedOutputStream.computeInt32Size(3, this.int32Val());
    size = size + CodedOutputStream.computeInt64Size(4, this.int64Val());
    size = size + CodedOutputStream.computeUInt32Size(5, this.uint32Val());
    size = size + CodedOutputStream.computeUInt64Size(6, this.uint64Val());
    size = size + CodedOutputStream.computeSInt32Size(7, this.sint32Val());
    size = size + CodedOutputStream.computeSInt64Size(8, this.sint64Val());
    size = size + CodedOutputStream.computeFixed32Size(9, this.fixed32Val());
    size = size + CodedOutputStream.computeFixed64Size(10, this.fixed64Val());
    size = size + CodedOutputStream.computeSFixed32Size(11, this.sfixed32Val());
    size = size + CodedOutputStream.computeSFixed64Size(12, this.sfixed64Val());
    size = size + CodedOutputStream.computeBoolSize(13, this.boolVal());
    size = size + CodedOutputStream.computeStringSize(14, this.stringVal());
    size = size + CodedOutputStream.computeBytesSize(15, this.bytesVal());
    return size;
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    output.writeDouble(1, this.doubleVal());
    output.writeFloat(2, this.floatVal());
    output.writeInt32(3, this.int32Val());
    output.writeInt64(4, this.int64Val());
    output.writeUInt32(5, this.uint32Val());
    output.writeUInt64(6, this.uint64Val());
    output.writeSInt32(7, this.sint32Val());
    output.writeSInt64(8, this.sint64Val());
    output.writeFixed32(9, this.fixed32Val());
    output.writeFixed64(10, this.fixed64Val());
    output.writeSFixed32(11, this.sfixed32Val());
    output.writeSFixed64(12, this.sfixed64Val());
    output.writeBool(13, this.boolVal());
    output.writeString(14, this.stringVal());
    output.writeBytes(15, this.bytesVal());
  }
}
