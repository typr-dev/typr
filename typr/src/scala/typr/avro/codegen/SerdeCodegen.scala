package typr.avro.codegen

import typr.avro._
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.{jvm, Lang, Naming, Scope}
import typr.internal.codegen._

/** Generates Kafka Serializer, Deserializer, and Serde classes for Avro records */
class SerdeCodegen(
    naming: Naming,
    lang: Lang,
    avroWireFormat: AvroWireFormat
) {

  // Kafka types
  private val SerializerType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.common.serialization.Serializer"))
  private val DeserializerType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.common.serialization.Deserializer"))
  private val SerdeType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.common.serialization.Serde"))
  private val SerdesType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.common.serialization.Serdes"))
  private val MapType = jvm.Type.Qualified(jvm.QIdent("java.util.Map"))
  private val MutableMapType = jvm.Type.Qualified(jvm.QIdent("kotlin.collections.MutableMap"))

  // For Kotlin we need MutableMap to match Java's Map, for Java/Scala use java.util.Map
  private def WildcardMap =
    if (lang.extension == "kt") MutableMapType.of(lang.String, jvm.Type.Wildcard)
    else MapType.of(lang.String, jvm.Type.Wildcard)

  // Nullable type wrapper - for Kotlin uses KotlinNullable, for Java/Scala just the type
  private def nullable(tpe: jvm.Type): jvm.Type =
    if (lang.extension == "kt") jvm.Type.KotlinNullable(tpe)
    else tpe
  private val GenericRecordType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericRecord"))
  private def ByteArrayType = lang.ByteArrayType

  // Confluent types
  private val KafkaAvroSerializerType = jvm.Type.Qualified(jvm.QIdent("io.confluent.kafka.serializers.KafkaAvroSerializer"))
  private val KafkaAvroDeserializerType = jvm.Type.Qualified(jvm.QIdent("io.confluent.kafka.serializers.KafkaAvroDeserializer"))

  /** Generate a Serializer class for a record */
  def generateSerializer(record: AvroRecord): jvm.File = {
    val recordType = jvm.Type.Qualified(jvm.QIdent(record.fullName))
    val tpe = jvm.Type.Qualified(naming.avroSerializerName(record.name))

    val members = avroWireFormat match {
      case AvroWireFormat.ConfluentRegistry => confluentSerializerMembers(recordType)
      case AvroWireFormat.BinaryEncoded     => vanillaSerializerMembers(recordType)
      case AvroWireFormat.JsonEncoded(_)    => sys.error("SerdeCodegen should not be called for JSON wire format")
    }

    val classAdt = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"Serializer for ${record.name}")),
      classType = jvm.ClassType.Class,
      name = tpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = List(SerializerType.of(recordType)),
      members = members,
      staticMembers = Nil
    )

    jvm.File(tpe, jvm.Code.Tree(classAdt), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate a Deserializer class for a record */
  def generateDeserializer(record: AvroRecord): jvm.File = {
    val recordType = jvm.Type.Qualified(jvm.QIdent(record.fullName))
    val tpe = jvm.Type.Qualified(naming.avroDeserializerName(record.name))

    val members = avroWireFormat match {
      case AvroWireFormat.ConfluentRegistry => confluentDeserializerMembers(recordType)
      case AvroWireFormat.BinaryEncoded     => vanillaDeserializerMembers(recordType)
      case AvroWireFormat.JsonEncoded(_)    => sys.error("SerdeCodegen should not be called for JSON wire format")
    }

    val classAdt = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"Deserializer for ${record.name}")),
      classType = jvm.ClassType.Class,
      name = tpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = List(DeserializerType.of(recordType)),
      members = members,
      staticMembers = Nil
    )

    jvm.File(tpe, jvm.Code.Tree(classAdt), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate a Serde class for a record - implements Serde, Serializer, and Deserializer directly */
  def generateSerde(record: AvroRecord): jvm.File = {
    val recordType = jvm.Type.Qualified(jvm.QIdent(record.fullName))
    val tpe = jvm.Type.Qualified(naming.avroSerdeName(record.name))

    val members = avroWireFormat match {
      case AvroWireFormat.ConfluentRegistry => confluentSerdeMembers(recordType)
      case AvroWireFormat.BinaryEncoded     => vanillaSerdeMembers(recordType)
      case AvroWireFormat.JsonEncoded(_)    => sys.error("SerdeCodegen should not be called for JSON wire format")
    }

    val classAdt = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"Serde for ${record.name}")),
      classType = jvm.ClassType.Class,
      name = tpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = List(
        SerdeType.of(recordType),
        SerializerType.of(recordType),
        DeserializerType.of(recordType)
      ),
      members = members,
      staticMembers = Nil
    )

    jvm.File(tpe, jvm.Code.Tree(classAdt), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Confluent serde members - implements Serializer and Deserializer inline */
  private def confluentSerdeMembers(recordType: jvm.Type.Qualified): List[jvm.ClassMember] = {
    val innerSerField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("innerSerializer"),
      tpe = KafkaAvroSerializerType,
      body = Some(KafkaAvroSerializerType.construct()),
      isLazy = false,
      isOverride = false
    )

    val innerDeserField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("innerDeserializer"),
      tpe = KafkaAvroDeserializerType,
      body = Some(KafkaAvroDeserializerType.construct()),
      isLazy = false,
      isOverride = false
    )

    val configureMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("configure"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("configs"), WildcardMap, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("isKey"), lang.primitiveBoolean, None)
      ),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.Stmt.simple(jvm.Ident("innerSerializer").code.invoke("configure", jvm.Ident("configs").code, jvm.Ident("isKey").code)).code,
          jvm.Stmt.simple(jvm.Ident("innerDeserializer").code.invoke("configure", jvm.Ident("configs").code, jvm.Ident("isKey").code)).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val serializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(recordType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(ByteArrayType),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .If(
              List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
              None
            )
            .code,
          jvm.Return(jvm.Ident("innerSerializer").code.invoke("serialize", jvm.Ident("topic").code, lang.nullaryMethodCall(jvm.Ident("data").code, jvm.Ident("toGenericRecord")))).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val deserializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(ByteArrayType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(recordType),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .If(
              List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
              None
            )
            .code,
          jvm
            .LocalVar(
              name = jvm.Ident("record"),
              tpe = Some(GenericRecordType),
              value = jvm.Cast(GenericRecordType, jvm.Ident("innerDeserializer").code.invoke("deserialize", jvm.Ident("topic").code, jvm.Ident("data").code)).code
            )
            .code,
          jvm.Return(recordType.code.invoke("fromGenericRecord", jvm.Ident("record").code)).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val closeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("close"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.Stmt.simple(jvm.Ident("innerSerializer").code.invoke("close")).code,
          jvm.Stmt.simple(jvm.Ident("innerDeserializer").code.invoke("close")).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val serializerAccessor = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serializer"),
      params = Nil,
      implicitParams = Nil,
      tpe = SerializerType.of(recordType),
      throws = Nil,
      body = jvm.Body.Expr(jvm.Code.Str("this")),
      isOverride = true,
      isDefault = false
    )

    val deserializerAccessor = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserializer"),
      params = Nil,
      implicitParams = Nil,
      tpe = DeserializerType.of(recordType),
      throws = Nil,
      body = jvm.Body.Expr(jvm.Code.Str("this")),
      isOverride = true,
      isDefault = false
    )

    List(innerSerField, innerDeserField, configureMethod, serializeMethod, deserializeMethod, closeMethod, serializerAccessor, deserializerAccessor)
  }

  /** Vanilla serde members - implements Serializer and Deserializer inline */
  private def vanillaSerdeMembers(recordType: jvm.Type.Qualified): List[jvm.ClassMember] = {
    val DatumWriterType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.DatumWriter"))
    val DatumReaderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.DatumReader"))
    val GenericDatumWriterType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericDatumWriter"))
    val GenericDatumReaderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericDatumReader"))
    val EncoderFactoryType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.EncoderFactory"))
    val DecoderFactoryType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.DecoderFactory"))
    val ByteArrayOutputStreamType = jvm.Type.Qualified(jvm.QIdent("java.io.ByteArrayOutputStream"))
    val BinaryEncoderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.BinaryEncoder"))
    val BinaryDecoderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.BinaryDecoder"))

    val writerField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("writer"),
      tpe = DatumWriterType.of(GenericRecordType),
      body = Some(GenericDatumWriterType.construct(recordType.code.select("SCHEMA"))),
      isLazy = false,
      isOverride = false
    )

    val readerField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("reader"),
      tpe = DatumReaderType.of(GenericRecordType),
      body = Some(GenericDatumReaderType.construct(recordType.code.select("SCHEMA"))),
      isLazy = false,
      isOverride = false
    )

    val configureMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("configure"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("configs"), WildcardMap, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("isKey"), lang.primitiveBoolean, None)
      ),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(Nil),
      isOverride = true,
      isDefault = false
    )

    val serializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(recordType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(ByteArrayType),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .If(
              List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
              None
            )
            .code,
          jvm
            .TryCatch(
              tryBlock = List(
                jvm.LocalVar(jvm.Ident("out"), Some(ByteArrayOutputStreamType), ByteArrayOutputStreamType.construct()).code,
                jvm.LocalVar(jvm.Ident("encoder"), Some(BinaryEncoderType), EncoderFactoryType.code.invoke("get").invoke("binaryEncoder", jvm.Ident("out").code, code"null")).code,
                jvm.Stmt.simple(jvm.Ident("writer").code.invoke("write", lang.nullaryMethodCall(jvm.Ident("data").code, jvm.Ident("toGenericRecord")), jvm.Ident("encoder").code)).code,
                jvm.Stmt.simple(jvm.Ident("encoder").code.invoke("flush")).code,
                jvm.Return(jvm.Ident("out").code.invoke("toByteArray")).code
              ),
              catches = List(
                jvm.TryCatch.Catch(
                  exceptionType = jvm.Type.Qualified("java.io.IOException"),
                  ident = jvm.Ident("e"),
                  body = List(
                    jvm.Throw(jvm.Type.Qualified("org.apache.kafka.common.errors.SerializationException").construct(jvm.StrLit("Error serializing Avro message").code, jvm.Ident("e").code)).code
                  )
                )
              ),
              finallyBlock = Nil
            )
            .code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val deserializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(ByteArrayType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(recordType),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .If(
              List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
              None
            )
            .code,
          jvm
            .TryCatch(
              tryBlock = List(
                jvm.LocalVar(jvm.Ident("decoder"), Some(BinaryDecoderType), DecoderFactoryType.code.invoke("get").invoke("binaryDecoder", jvm.Ident("data").code, code"null")).code,
                jvm.LocalVar(jvm.Ident("record"), Some(GenericRecordType), jvm.Ident("reader").code.invoke("read", code"null", jvm.Ident("decoder").code)).code,
                jvm.Return(recordType.code.invoke("fromGenericRecord", jvm.Ident("record").code)).code
              ),
              catches = List(
                jvm.TryCatch.Catch(
                  exceptionType = jvm.Type.Qualified("java.io.IOException"),
                  ident = jvm.Ident("e"),
                  body = List(
                    jvm.Throw(jvm.Type.Qualified("org.apache.kafka.common.errors.SerializationException").construct(jvm.StrLit("Error deserializing Avro message").code, jvm.Ident("e").code)).code
                  )
                )
              ),
              finallyBlock = Nil
            )
            .code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val closeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("close"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(Nil),
      isOverride = true,
      isDefault = false
    )

    val serializerAccessor = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serializer"),
      params = Nil,
      implicitParams = Nil,
      tpe = SerializerType.of(recordType),
      throws = Nil,
      body = jvm.Body.Expr(jvm.Code.Str("this")),
      isOverride = true,
      isDefault = false
    )

    val deserializerAccessor = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserializer"),
      params = Nil,
      implicitParams = Nil,
      tpe = DeserializerType.of(recordType),
      throws = Nil,
      body = jvm.Body.Expr(jvm.Code.Str("this")),
      isOverride = true,
      isDefault = false
    )

    List(writerField, readerField, configureMethod, serializeMethod, deserializeMethod, closeMethod, serializerAccessor, deserializerAccessor)
  }

  // Confluent implementation - uses KafkaAvroSerializer/KafkaAvroDeserializer under the hood
  private def confluentSerializerMembers(recordType: jvm.Type.Qualified): List[jvm.ClassMember] = {
    val innerField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("inner"),
      tpe = KafkaAvroSerializerType,
      body = Some(KafkaAvroSerializerType.construct()),
      isLazy = false,
      isOverride = false
    )

    val configureMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("configure"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("configs"), WildcardMap, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("isKey"), lang.primitiveBoolean, None)
      ),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.Stmt.simple(jvm.Ident("inner").code.invoke("configure", jvm.Ident("configs").code, jvm.Ident("isKey").code)).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val serializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(recordType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(ByteArrayType),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .If(
              List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
              None
            )
            .code,
          jvm.Return(jvm.Ident("inner").code.invoke("serialize", jvm.Ident("topic").code, lang.nullaryMethodCall(jvm.Ident("data").code, jvm.Ident("toGenericRecord")))).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val closeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("close"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.Stmt.simple(jvm.Ident("inner").code.invoke("close")).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    List(innerField, configureMethod, serializeMethod, closeMethod)
  }

  private def confluentDeserializerMembers(recordType: jvm.Type.Qualified): List[jvm.ClassMember] = {
    val innerField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("inner"),
      tpe = KafkaAvroDeserializerType,
      body = Some(KafkaAvroDeserializerType.construct()),
      isLazy = false,
      isOverride = false
    )

    val configureMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("configure"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("configs"), WildcardMap, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("isKey"), lang.primitiveBoolean, None)
      ),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.Stmt.simple(jvm.Ident("inner").code.invoke("configure", jvm.Ident("configs").code, jvm.Ident("isKey").code)).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val deserializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(ByteArrayType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(recordType),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .If(
              List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
              None
            )
            .code,
          jvm
            .LocalVar(
              name = jvm.Ident("record"),
              tpe = Some(GenericRecordType),
              value = jvm.Cast(GenericRecordType, jvm.Ident("inner").code.invoke("deserialize", jvm.Ident("topic").code, jvm.Ident("data").code)).code
            )
            .code,
          jvm.Return(recordType.code.invoke("fromGenericRecord", jvm.Ident("record").code)).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val closeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("close"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.Stmt.simple(jvm.Ident("inner").code.invoke("close")).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    List(innerField, configureMethod, deserializeMethod, closeMethod)
  }

  // Vanilla Avro implementation - uses binary encoder/decoder directly
  private def vanillaSerializerMembers(recordType: jvm.Type.Qualified): List[jvm.ClassMember] = {
    val DatumWriterType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.DatumWriter"))
    val GenericDatumWriterType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericDatumWriter"))
    val EncoderFactoryType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.EncoderFactory"))
    val ByteArrayOutputStreamType = jvm.Type.Qualified(jvm.QIdent("java.io.ByteArrayOutputStream"))
    val BinaryEncoderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.BinaryEncoder"))

    val writerField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("writer"),
      tpe = DatumWriterType.of(GenericRecordType),
      body = Some(GenericDatumWriterType.construct(recordType.code.select("SCHEMA"))),
      isLazy = false,
      isOverride = false
    )

    val configureMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("configure"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("configs"), WildcardMap, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("isKey"), lang.primitiveBoolean, None)
      ),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(Nil),
      isOverride = true,
      isDefault = false
    )

    // Note: We need to use try-catch for IOException
    val serializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(recordType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(ByteArrayType),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .If(
              List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
              None
            )
            .code,
          jvm
            .TryCatch(
              tryBlock = List(
                jvm.LocalVar(jvm.Ident("out"), Some(ByteArrayOutputStreamType), ByteArrayOutputStreamType.construct()).code,
                jvm.LocalVar(jvm.Ident("encoder"), Some(BinaryEncoderType), EncoderFactoryType.code.invoke("get").invoke("binaryEncoder", jvm.Ident("out").code, code"null")).code,
                jvm.Stmt.simple(jvm.Ident("writer").code.invoke("write", lang.nullaryMethodCall(jvm.Ident("data").code, jvm.Ident("toGenericRecord")), jvm.Ident("encoder").code)).code,
                jvm.Stmt.simple(jvm.Ident("encoder").code.invoke("flush")).code,
                jvm.Return(jvm.Ident("out").code.invoke("toByteArray")).code
              ),
              catches = List(
                jvm.TryCatch.Catch(
                  exceptionType = jvm.Type.Qualified("java.io.IOException"),
                  ident = jvm.Ident("e"),
                  body = List(
                    jvm.Throw(jvm.Type.Qualified("org.apache.kafka.common.errors.SerializationException").construct(jvm.StrLit("Error serializing Avro message").code, jvm.Ident("e").code)).code
                  )
                )
              ),
              finallyBlock = Nil
            )
            .code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val closeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("close"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(Nil),
      isOverride = true,
      isDefault = false
    )

    List(writerField, configureMethod, serializeMethod, closeMethod)
  }

  private def vanillaDeserializerMembers(recordType: jvm.Type.Qualified): List[jvm.ClassMember] = {
    val DatumReaderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.DatumReader"))
    val GenericDatumReaderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericDatumReader"))
    val DecoderFactoryType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.DecoderFactory"))
    val BinaryDecoderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.BinaryDecoder"))

    val readerField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("reader"),
      tpe = DatumReaderType.of(GenericRecordType),
      body = Some(GenericDatumReaderType.construct(recordType.code.select("SCHEMA"))),
      isLazy = false,
      isOverride = false
    )

    val configureMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("configure"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("configs"), WildcardMap, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("isKey"), lang.primitiveBoolean, None)
      ),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(Nil),
      isOverride = true,
      isDefault = false
    )

    val deserializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(ByteArrayType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(recordType),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .If(
              List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
              None
            )
            .code,
          jvm
            .TryCatch(
              tryBlock = List(
                jvm.LocalVar(jvm.Ident("decoder"), Some(BinaryDecoderType), DecoderFactoryType.code.invoke("get").invoke("binaryDecoder", jvm.Ident("data").code, code"null")).code,
                jvm.LocalVar(jvm.Ident("record"), Some(GenericRecordType), jvm.Ident("reader").code.invoke("read", code"null", jvm.Ident("decoder").code)).code,
                jvm.Return(recordType.code.invoke("fromGenericRecord", jvm.Ident("record").code)).code
              ),
              catches = List(
                jvm.TryCatch.Catch(
                  exceptionType = jvm.Type.Qualified("java.io.IOException"),
                  ident = jvm.Ident("e"),
                  body = List(
                    jvm.Throw(jvm.Type.Qualified("org.apache.kafka.common.errors.SerializationException").construct(jvm.StrLit("Error deserializing Avro message").code, jvm.Ident("e").code)).code
                  )
                )
              ),
              finallyBlock = Nil
            )
            .code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val closeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("close"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(Nil),
      isOverride = true,
      isDefault = false
    )

    List(readerField, configureMethod, deserializeMethod, closeMethod)
  }

  /** Generate a Serde for an event group (sealed type) that dispatches based on schema */
  def generateEventGroupSerde(group: AvroEventGroup): jvm.File = {
    avroWireFormat match {
      case AvroWireFormat.ConfluentRegistry => generateConfluentEventGroupSerde(group)
      case AvroWireFormat.BinaryEncoded     => generateVanillaEventGroupSerde(group)
      case AvroWireFormat.JsonEncoded(_)    => sys.error("SerdeCodegen should not be called for JSON wire format")
    }
  }

  /** Generate event group serde for Confluent Schema Registry */
  private def generateConfluentEventGroupSerde(group: AvroEventGroup): jvm.File = {
    val groupType = naming.avroEventGroupTypeName(group.name, group.namespace)
    val tpe = jvm.Type.Qualified(naming.avroSerdeName(group.name))

    // The serializer uses TypeSwitch to dispatch to the appropriate member serde
    val serializeCases = group.members.map { member =>
      val memberType = jvm.Type.Qualified(jvm.QIdent(member.fullName))
      val memberSerdeType = jvm.Type.Qualified(naming.avroSerdeName(member.name))
      jvm.TypeSwitch.Case(
        tpe = memberType,
        ident = jvm.Ident("e"),
        body = memberSerdeType.construct().invoke("serialize", jvm.Ident("topic").code, jvm.Ident("e").code)
      )
    }

    // For Kotlin, we need an else branch since data is nullable (even though we checked for null above)
    val defaultCase =
      if (lang.extension == "kt") Some(jvm.Throw(jvm.Type.Qualified("java.lang.IllegalStateException").construct(jvm.StrLit("Unexpected type").code)).code)
      else None

    val serializeBody = List(
      jvm
        .If(
          List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
          None
        )
        .code,
      jvm.Return(jvm.TypeSwitch(jvm.Ident("data").code, serializeCases, None, defaultCase, unchecked = false).code).code
    )

    val serializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(groupType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(ByteArrayType),
      throws = Nil,
      body = jvm.Body.Stmts(serializeBody),
      isOverride = true,
      isDefault = false
    )

    // The deserializer delegates to the event group's fromGenericRecord dispatcher
    val innerField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("inner"),
      tpe = KafkaAvroDeserializerType,
      body = Some(KafkaAvroDeserializerType.construct()),
      isLazy = false,
      isOverride = false
    )

    val configureMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("configure"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("configs"), WildcardMap, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("isKey"), lang.primitiveBoolean, None)
      ),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.Stmt.simple(jvm.Ident("inner").code.invoke("configure", jvm.Ident("configs").code, jvm.Ident("isKey").code)).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val deserializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(ByteArrayType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(groupType),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .If(
              List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
              None
            )
            .code,
          jvm
            .LocalVar(
              name = jvm.Ident("record"),
              tpe = Some(GenericRecordType),
              value = jvm.Cast(GenericRecordType, jvm.Ident("inner").code.invoke("deserialize", jvm.Ident("topic").code, jvm.Ident("data").code)).code
            )
            .code,
          jvm.Return(groupType.code.invoke("fromGenericRecord", jvm.Ident("record").code)).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val closeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("close"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.Stmt.simple(jvm.Ident("inner").code.invoke("close")).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    // Serializer() and Deserializer() methods for Serde interface
    val serializerAccessor = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serializer"),
      params = Nil,
      implicitParams = Nil,
      tpe = SerializerType.of(groupType),
      throws = Nil,
      body = jvm.Body.Expr(jvm.Code.Str("this")),
      isOverride = true,
      isDefault = false
    )

    val deserializerAccessor = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserializer"),
      params = Nil,
      implicitParams = Nil,
      tpe = DeserializerType.of(groupType),
      throws = Nil,
      body = jvm.Body.Expr(jvm.Code.Str("this")),
      isOverride = true,
      isDefault = false
    )

    // The class implements Serde, Serializer, and Deserializer
    val classAdt = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"Serde for ${group.name} (sealed type with multiple event variants)")),
      classType = jvm.ClassType.Class,
      name = tpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = List(
        SerdeType.of(groupType),
        SerializerType.of(groupType),
        DeserializerType.of(groupType)
      ),
      members = List(innerField, configureMethod, serializeMethod, deserializeMethod, closeMethod, serializerAccessor, deserializerAccessor),
      staticMembers = Nil
    )

    jvm.File(tpe, jvm.Code.Tree(classAdt), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate event group serde for VanillaAvro (no Schema Registry) */
  private def generateVanillaEventGroupSerde(group: AvroEventGroup): jvm.File = {
    val groupType = naming.avroEventGroupTypeName(group.name, group.namespace)
    val tpe = jvm.Type.Qualified(naming.avroSerdeName(group.name))

    val DatumReaderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.DatumReader"))
    val GenericDatumReaderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.generic.GenericDatumReader"))
    val DecoderFactoryType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.DecoderFactory"))
    val BinaryDecoderType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.io.BinaryDecoder"))
    val SchemaType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.Schema"))
    val HashMapType = jvm.Type.Qualified(jvm.QIdent("java.util.HashMap"))
    val MapType = jvm.Type.Qualified(jvm.QIdent("java.util.Map"))
    val FunctionType = jvm.Type.Qualified(jvm.QIdent("java.util.function.Function"))

    // Build a map of schema full name -> reader for each member
    // Map<String, GenericDatumReader<GenericRecord>>
    val readerMapType = MapType.of(lang.String, DatumReaderType.of(GenericRecordType))

    // Generate the readers map initialization
    val readersField = jvm.Value(
      annotations = Nil,
      name = jvm.Ident("readers"),
      tpe = readerMapType,
      body = Some(HashMapType.construct()),
      isLazy = false,
      isOverride = false
    )

    // Static initializer to populate the readers map
    val readerInitStmts = group.members.flatMap { member =>
      val memberType = jvm.Type.Qualified(jvm.QIdent(member.fullName))
      List(
        jvm.Stmt.simple(jvm.Ident("readers").code.invoke("put", jvm.StrLit(member.fullName).code, GenericDatumReaderType.construct(memberType.code.select("SCHEMA")))).code
      )
    }

    // The serializer uses TypeSwitch to dispatch to the appropriate member serde
    val serializeCases = group.members.map { member =>
      val memberType = jvm.Type.Qualified(jvm.QIdent(member.fullName))
      val memberSerdeType = jvm.Type.Qualified(naming.avroSerdeName(member.name))
      jvm.TypeSwitch.Case(
        tpe = memberType,
        ident = jvm.Ident("e"),
        body = memberSerdeType.construct().invoke("serialize", jvm.Ident("topic").code, jvm.Ident("e").code)
      )
    }

    val defaultCase =
      if (lang.extension == "kt") Some(jvm.Throw(jvm.Type.Qualified("java.lang.IllegalStateException").construct(jvm.StrLit("Unexpected type").code)).code)
      else None

    val serializeBody = List(
      jvm
        .If(
          List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
          None
        )
        .code,
      jvm.Return(jvm.TypeSwitch(jvm.Ident("data").code, serializeCases, None, defaultCase, unchecked = false).code).code
    )

    val serializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(groupType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(ByteArrayType),
      throws = Nil,
      body = jvm.Body.Stmts(serializeBody),
      isOverride = true,
      isDefault = false
    )

    val configureMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("configure"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("configs"), WildcardMap, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("isKey"), lang.primitiveBoolean, None)
      ),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(Nil),
      isOverride = true,
      isDefault = false
    )

    // For vanilla Avro, we need to know the schema name upfront.
    // The simplest approach is to try each schema in sequence until one works.
    // This is a fallback approach - in practice, users should use a schema header or other mechanism.
    val deserializeBody = List(
      jvm
        .If(
          List(jvm.If.Branch(code"data == null", jvm.Return(code"null").code)),
          None
        )
        .code,
      // Try each member's serde in order
      jvm
        .TryCatch(
          tryBlock = {
            // For each member, try to deserialize using its serde
            group.members.flatMap { member =>
              val memberType = jvm.Type.Qualified(jvm.QIdent(member.fullName))
              val memberSerdeType = jvm.Type.Qualified(naming.avroSerdeName(member.name))
              List(
                jvm
                  .TryCatch(
                    tryBlock = List(
                      jvm.LocalVar(jvm.Ident("result"), Some(memberType), memberSerdeType.construct().invoke("deserialize", jvm.Ident("topic").code, jvm.Ident("data").code)).code,
                      jvm
                        .If(
                          List(jvm.If.Branch(code"result != null", jvm.Return(jvm.Ident("result").code).code)),
                          None
                        )
                        .code
                    ),
                    catches = List(
                      jvm.TryCatch.Catch(
                        exceptionType = jvm.Type.Qualified("java.lang.Exception"),
                        ident = jvm.Ident("ignored"),
                        body = Nil
                      )
                    ),
                    finallyBlock = Nil
                  )
                  .code
              )
            } :+ jvm.Throw(jvm.Type.Qualified("org.apache.kafka.common.errors.SerializationException").construct(jvm.StrLit("Could not deserialize to any known event type").code)).code
          },
          catches = List(
            jvm.TryCatch.Catch(
              exceptionType = jvm.Type.Qualified("java.lang.Exception"),
              ident = jvm.Ident("e"),
              body = List(
                jvm.Throw(jvm.Type.Qualified("org.apache.kafka.common.errors.SerializationException").construct(jvm.StrLit("Error deserializing Avro message").code, jvm.Ident("e").code)).code
              )
            )
          ),
          finallyBlock = Nil
        )
        .code
    )

    val deserializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Deserialize by trying each member schema. For production use, consider adding a schema header.")),
      tparams = Nil,
      name = jvm.Ident("deserialize"),
      params = List(
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("topic"), lang.String, None),
        jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("data"), nullable(ByteArrayType), None)
      ),
      implicitParams = Nil,
      tpe = nullable(groupType),
      throws = Nil,
      body = jvm.Body.Stmts(deserializeBody),
      isOverride = true,
      isDefault = false
    )

    val closeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("close"),
      params = Nil,
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = Nil,
      body = jvm.Body.Stmts(Nil),
      isOverride = true,
      isDefault = false
    )

    val serializerAccessor = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serializer"),
      params = Nil,
      implicitParams = Nil,
      tpe = SerializerType.of(groupType),
      throws = Nil,
      body = jvm.Body.Expr(jvm.Code.Str("this")),
      isOverride = true,
      isDefault = false
    )

    val deserializerAccessor = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserializer"),
      params = Nil,
      implicitParams = Nil,
      tpe = DeserializerType.of(groupType),
      throws = Nil,
      body = jvm.Body.Expr(jvm.Code.Str("this")),
      isOverride = true,
      isDefault = false
    )

    val classAdt = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List(s"Serde for ${group.name} (sealed type with multiple event variants, vanilla Avro)")),
      classType = jvm.ClassType.Class,
      name = tpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = List(
        SerdeType.of(groupType),
        SerializerType.of(groupType),
        DeserializerType.of(groupType)
      ),
      members = List(readersField, configureMethod, serializeMethod, deserializeMethod, closeMethod, serializerAccessor, deserializerAccessor),
      staticMembers = Nil
    )

    jvm.File(tpe, jvm.Code.Tree(classAdt), secondaryTypes = Nil, scope = Scope.Main)
  }

}
