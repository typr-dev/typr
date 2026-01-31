package typr.grpc.codegen

import typr.grpc._
import typr.{jvm, Lang, Naming, Scope}
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.internal.codegen._

/** Generates jvm.File for Protobuf message types.
  *
  * Proto messages are mapped to clean JVM records/data classes/case classes with direct protobuf binary serialization via CodedOutputStream/CodedInputStream.
  */
class MessageCodegen(
    naming: Naming,
    typeMapper: ProtobufTypeMapper,
    lang: Lang,
    wrapperTypeMap: Map[String, jvm.Type.Qualified]
) {

  private val CodedOutputStreamType = jvm.Type.Qualified(jvm.QIdent("com.google.protobuf.CodedOutputStream"))
  private val CodedInputStreamType = jvm.Type.Qualified(jvm.QIdent("com.google.protobuf.CodedInputStream"))
  private val WireFormatType = jvm.Type.Qualified(jvm.QIdent("com.google.protobuf.WireFormat"))
  private val ByteArrayInputStreamType = jvm.Type.Qualified(jvm.QIdent("java.io.ByteArrayInputStream"))
  private val InputStreamType = jvm.Type.Qualified(jvm.QIdent("java.io.InputStream"))
  private val IOException = jvm.Type.Qualified("java.io.IOException")
  private val MethodDescriptorType = jvm.Type.Qualified(jvm.QIdent("io.grpc.MethodDescriptor"))
  private val MarshallerType = MethodDescriptorType / jvm.Ident("Marshaller")

  /** Generate a record class from a ProtoMessage */
  def generate(message: ProtoMessage): jvm.File = {
    val tpe = naming.grpcMessageTypeName(message.fullName)

    val regularFields = message.fields.filter(_.oneofIndex.isEmpty)

    val params = regularFields.map { field =>
      val fieldType = typeMapper.mapFieldType(field)
      val effectiveType = field.fieldType match {
        case ProtoType.Message(_) if !field.isRepeated && !field.isMapField && !field.proto3Optional =>
          lang.nullableRefType(fieldType)
        case _ => fieldType
      }
      jvm.Param(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        name = naming.grpcFieldName(field.name),
        tpe = effectiveType,
        default = None
      )
    }

    val oneofParams = message.oneofs.map { oneof =>
      val oneofType = naming.grpcOneofTypeName(message.fullName, oneof.name)
      jvm.Param(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        name = naming.grpcFieldName(oneof.name),
        tpe = lang.nullableRefType(oneofType),
        default = None
      )
    }

    val allParams = params ++ oneofParams

    val writeTo = generateWriteToMethod(message)
    val getSerializedSize = generateGetSerializedSizeMethod(message)
    val parseFrom = generateParseFromMethod(message, tpe)
    val marshaller = generateMarshallerField(tpe)

    val recordAdt = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments.Empty,
      name = tpe,
      tparams = Nil,
      params = allParams,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = List(writeTo, getSerializedSize),
      staticMembers = List(parseFrom, marshaller)
    )

    jvm.File(tpe, jvm.Code.Tree(recordAdt), secondaryTypes = Nil, scope = Scope.Main)
  }

  private def intCode(n: Int): jvm.Code = jvm.Code.Str(n.toString)

  /** Generate writeTo(CodedOutputStream) method */
  private def generateWriteToMethod(message: ProtoMessage): jvm.Method = {
    val outputParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("output"), CodedOutputStreamType, None)
    val output = outputParam.name.code

    val regularFields = message.fields.filter(_.oneofIndex.isEmpty)

    val fieldWrites = regularFields.flatMap { field =>
      generateFieldWrite(output, field)
    }

    val oneofWrites = message.oneofs.flatMap { oneof =>
      generateOneofWrite(output, oneof, message.fullName)
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("writeTo"),
      params = List(outputParam),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = List(IOException),
      body = jvm.Body.Stmts(fieldWrites ++ oneofWrites),
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate write statements for a single field */
  private def generateFieldWrite(output: jvm.Code, field: ProtoField): List[jvm.Code] = {
    val fieldAccess = lang.prop(code"this", naming.grpcFieldName(field.name))
    val unwrapped = field.wrapperType match {
      case Some(_) => fieldAccess.callNullary("unwrap")
      case None    => fieldAccess
    }
    val fieldNumber = intCode(field.number)

    if (field.isMapField) {
      generateMapFieldWrite(output, field, fieldAccess, fieldNumber)
    } else if (field.isRepeated) {
      generateRepeatedFieldWrite(output, field, fieldAccess, fieldNumber)
    } else if (field.proto3Optional || ProtoType.isWrapperType(field.fieldType)) {
      generateOptionalFieldWrite(output, field, fieldAccess, fieldNumber)
    } else {
      val writeStmts = generateScalarFieldWrite(output, field.fieldType, unwrapped, fieldNumber)
      field.fieldType match {
        case ProtoType.Message(_) =>
          val body = writeStmts.map(s => code"$s;").mkCode("\n")
          List(jvm.If(lang.notEquals(fieldAccess, code"null"), body).code)
        case _ => writeStmts
      }
    }
  }

  /** Generate write for a scalar field */
  private def generateScalarFieldWrite(output: jvm.Code, fieldType: ProtoType, value: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    fieldType match {
      case ProtoType.Double   => List(output.invoke("writeDouble", fieldNumber, value))
      case ProtoType.Float    => List(output.invoke("writeFloat", fieldNumber, value))
      case ProtoType.Int32    => List(output.invoke("writeInt32", fieldNumber, value))
      case ProtoType.Int64    => List(output.invoke("writeInt64", fieldNumber, value))
      case ProtoType.UInt32   => List(output.invoke("writeUInt32", fieldNumber, value))
      case ProtoType.UInt64   => List(output.invoke("writeUInt64", fieldNumber, value))
      case ProtoType.SInt32   => List(output.invoke("writeSInt32", fieldNumber, value))
      case ProtoType.SInt64   => List(output.invoke("writeSInt64", fieldNumber, value))
      case ProtoType.Fixed32  => List(output.invoke("writeFixed32", fieldNumber, value))
      case ProtoType.Fixed64  => List(output.invoke("writeFixed64", fieldNumber, value))
      case ProtoType.SFixed32 => List(output.invoke("writeSFixed32", fieldNumber, value))
      case ProtoType.SFixed64 => List(output.invoke("writeSFixed64", fieldNumber, value))
      case ProtoType.Bool     => List(output.invoke("writeBool", fieldNumber, value))
      case ProtoType.String   => List(output.invoke("writeString", fieldNumber, value))
      case ProtoType.Bytes    => List(output.invoke("writeBytes", fieldNumber, value))

      case ProtoType.Enum(_) =>
        List(output.invoke("writeEnum", fieldNumber, value.callNullary("toValue")))

      case ProtoType.Message(_) =>
        generateNestedMessageWrite(output, value, fieldNumber)

      case ProtoType.Timestamp =>
        generateTimestampWrite(output, value, fieldNumber)

      case ProtoType.Duration =>
        generateDurationWrite(output, value, fieldNumber)

      case wkt if ProtoType.isWrapperType(wkt) =>
        generateWrapperTypeWrite(output, wkt, value, fieldNumber)

      case _ => List(output.invoke("writeString", fieldNumber, value.invoke("toString")))
    }
  }

  /** Generate write for a nested message field: write tag, compute nested size, write length, call nested writeTo */
  private def generateNestedMessageWrite(output: jvm.Code, value: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    val nestedSize = value.callNullary("getSerializedSize")
    List(
      output.invoke("writeTag", fieldNumber, code"2"),
      output.invoke("writeUInt32NoTag", nestedSize),
      value.invoke("writeTo", output)
    )
  }

  /** Generate write for Timestamp (seconds=1, nanos=2 as nested message) */
  private def generateTimestampWrite(output: jvm.Code, value: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    val seconds = value.invoke("getEpochSecond")
    val nanos = value.invoke("getNano")
    val sizeExpr = code"$CodedOutputStreamType.computeInt64Size(1, $seconds) + $CodedOutputStreamType.computeInt32Size(2, $nanos)"
    List(
      output.invoke("writeTag", fieldNumber, code"2"),
      output.invoke("writeUInt32NoTag", sizeExpr),
      output.invoke("writeInt64", code"1", seconds),
      output.invoke("writeInt32", code"2", nanos)
    )
  }

  /** Generate write for Duration (seconds=1, nanos=2 as nested message) */
  private def generateDurationWrite(output: jvm.Code, value: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    val seconds = value.invoke("getSeconds")
    val nanos = value.invoke("getNano")
    val sizeExpr = code"$CodedOutputStreamType.computeInt64Size(1, $seconds) + $CodedOutputStreamType.computeInt32Size(2, $nanos)"
    List(
      output.invoke("writeTag", fieldNumber, code"2"),
      output.invoke("writeUInt32NoTag", sizeExpr),
      output.invoke("writeInt64", code"1", seconds),
      output.invoke("writeInt32", code"2", nanos)
    )
  }

  /** Generate write for well-known wrapper types (nested message with value=1) */
  private def generateWrapperTypeWrite(output: jvm.Code, wkt: ProtoType, value: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    val underlying = ProtoType.unwrapWellKnown(wkt).getOrElse(sys.error(s"Not a wrapper type: $wkt"))
    val innerValue = value.invoke("get")
    val computeMethod = computeSizeMethodName(underlying)
    val sizeExpr = code"$CodedOutputStreamType.$computeMethod(1, $innerValue)"
    val writeMethod = writeMethodName(underlying)
    List(
      output.invoke("writeTag", fieldNumber, code"2"),
      output.invoke("writeUInt32NoTag", sizeExpr),
      output.invoke(writeMethod, code"1", innerValue)
    )
  }

  /** Generate write for repeated field */
  private def generateRepeatedFieldWrite(output: jvm.Code, field: ProtoField, value: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    val elem = jvm.Ident("elem")
    val elemRef = elem.code
    val unwrappedElem = field.wrapperType match {
      case Some(_) => elemRef.callNullary("unwrap")
      case None    => elemRef
    }

    val writeStmts = field.fieldType match {
      case ProtoType.Message(_) =>
        generateNestedMessageWrite(output, unwrappedElem, fieldNumber)
      case ProtoType.Enum(_) =>
        List(output.invoke("writeEnum", fieldNumber, unwrappedElem.callNullary("toValue")))
      case ProtoType.Timestamp =>
        generateTimestampWrite(output, unwrappedElem, fieldNumber)
      case ProtoType.Duration =>
        generateDurationWrite(output, unwrappedElem, fieldNumber)
      case _ =>
        generateScalarFieldWrite(output, field.fieldType, unwrappedElem, fieldNumber)
    }

    val elemType = typeMapper.mapFieldType(field.copy(label = ProtoFieldLabel.Optional))
    List(lang.ListType.forEachStmt(value, elem, elemType)(_ => writeStmts))
  }

  /** Generate write for map field */
  private def generateMapFieldWrite(output: jvm.Code, field: ProtoField, value: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    field.fieldType match {
      case ProtoType.Map(keyType, valueType) =>
        val keyJvmType = typeMapper.mapType(keyType)
        val valueJvmType = typeMapper.mapType(valueType)
        val keyWriteMethod = writeMethodName(keyType)
        val valWriteMethod = writeMethodName(valueType)

        val forEachCode = lang.MapOps.forEachEntry(value, keyJvmType, valueJvmType) { (keyRef, valRef) =>
          val keySizeExpr = computeSizeExpr(keyType, code"1", keyRef)
          val valSizeExpr = computeSizeExpr(valueType, code"2", valRef)
          val entrySize = code"$keySizeExpr + $valSizeExpr"

          List(
            output.invoke("writeTag", fieldNumber, code"2"),
            output.invoke("writeUInt32NoTag", entrySize),
            output.invoke(keyWriteMethod, code"1", keyRef),
            output.invoke(valWriteMethod, code"2", valRef)
          )
        }
        List(forEachCode)
      case _ => Nil
    }
  }

  /** Generate write for optional field */
  private def generateOptionalFieldWrite(output: jvm.Code, field: ProtoField, fieldAccess: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    val v = jvm.Ident("v")
    val getV = jvm.LocalVar(v, None, lang.Optional.get(fieldAccess))
    val unwrappedV = field.wrapperType match {
      case Some(_) => v.code.callNullary("unwrap")
      case None    => v.code
    }
    val writeStmts = field.fieldType match {
      case ProtoType.Message(_) =>
        generateNestedMessageWrite(output, unwrappedV, fieldNumber)
      case ProtoType.Enum(_) =>
        List(output.invoke("writeEnum", fieldNumber, unwrappedV.callNullary("toValue")))
      case ProtoType.Timestamp =>
        generateTimestampWrite(output, unwrappedV, fieldNumber)
      case ProtoType.Duration =>
        generateDurationWrite(output, unwrappedV, fieldNumber)
      case wkt if ProtoType.isWrapperType(wkt) =>
        val underlying = ProtoType.unwrapWellKnown(wkt).getOrElse(sys.error(s"Not a wrapper type: $wkt"))
        val innerValue = unwrappedV
        val computeMethod = computeSizeMethodName(underlying)
        val sizeExpr = code"$CodedOutputStreamType.$computeMethod(1, $innerValue)"
        val writeMethod = writeMethodName(underlying)
        List(
          output.invoke("writeTag", fieldNumber, code"2"),
          output.invoke("writeUInt32NoTag", sizeExpr),
          output.invoke(writeMethod, code"1", innerValue)
        )
      case _ =>
        generateScalarFieldWrite(output, field.fieldType, unwrappedV, fieldNumber)
    }
    val body = (getV.code :: writeStmts).map(s => code"$s;").mkCode("\n")
    List(jvm.If(lang.Optional.isDefined(fieldAccess), body).code)
  }

  /** Generate oneof write: type-switch on the sealed type, write whichever case is set */
  private def generateOneofWrite(output: jvm.Code, oneof: ProtoOneof, messageFullName: String): List[jvm.Code] = {
    val oneofType = naming.grpcOneofTypeName(messageFullName, oneof.name)
    val fieldAccess = lang.prop(code"this", naming.grpcFieldName(oneof.name))

    val cases = oneof.fields.map { field =>
      val caseName = naming.grpcOneofCaseName(field)
      val caseType = oneofType / caseName
      val caseIdent = jvm.Ident("c")
      val fieldValue = lang.prop(caseIdent.code, naming.grpcFieldName(field.name))
      val unwrapped = field.wrapperType match {
        case Some(_) => fieldValue.callNullary("unwrap")
        case None    => fieldValue
      }
      val fieldNumber = intCode(field.number)
      val writeStmts = field.fieldType match {
        case ProtoType.Message(_) =>
          generateNestedMessageWrite(output, unwrapped, fieldNumber)
        case ProtoType.Enum(_) =>
          List(output.invoke("writeEnum", fieldNumber, unwrapped.callNullary("toValue")))
        case ProtoType.Timestamp =>
          generateTimestampWrite(output, unwrapped, fieldNumber)
        case ProtoType.Duration =>
          generateDurationWrite(output, unwrapped, fieldNumber)
        case _ =>
          generateScalarFieldWrite(output, field.fieldType, unwrapped, fieldNumber)
      }
      val body =
        if (writeStmts.size == 1) writeStmts.head
        else {
          val stmtsCode = writeStmts.map(s => code"$s;").mkCode("\n")
          code"""|{
               |  $stmtsCode
               |}""".stripMargin
        }
      jvm.TypeSwitch.Case(caseType, caseIdent, body)
    }

    List(jvm.TypeSwitch(fieldAccess, cases, nullCase = Some(code"{}")).code)
  }

  /** Generate getSerializedSize() method */
  private def generateGetSerializedSizeMethod(message: ProtoMessage): jvm.Method = {
    val regularFields = message.fields.filter(_.oneofIndex.isEmpty)

    val sizeVar = jvm.Ident("size")
    val sizeInit = jvm.MutableVar(sizeVar, Some(lang.Int), code"0")

    val fieldSizeStmts = regularFields.flatMap { field =>
      generateFieldSizeStmts(sizeVar, field)
    }

    val oneofSizeStmts = message.oneofs.flatMap { oneof =>
      generateOneofSizeStmts(sizeVar, oneof, message.fullName)
    }

    val returnStmt = jvm.Return(sizeVar.code).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("getSerializedSize"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.Int,
      throws = Nil,
      body = jvm.Body.Stmts(List(sizeInit.code) ++ fieldSizeStmts ++ oneofSizeStmts ++ List(returnStmt)),
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate size computation statements for a field */
  private def generateFieldSizeStmts(sizeVar: jvm.Ident, field: ProtoField): List[jvm.Code] = {
    val fieldAccess = lang.prop(code"this", naming.grpcFieldName(field.name))
    val unwrapped = field.wrapperType match {
      case Some(_) => fieldAccess.callNullary("unwrap")
      case None    => fieldAccess
    }
    val fieldNumber = intCode(field.number)

    if (field.isMapField) {
      generateMapFieldSizeStmts(sizeVar, field, fieldAccess, fieldNumber)
    } else if (field.isRepeated) {
      generateRepeatedFieldSizeStmts(sizeVar, field, fieldAccess, fieldNumber)
    } else if (field.proto3Optional || ProtoType.isWrapperType(field.fieldType)) {
      generateOptionalFieldSizeStmts(sizeVar, field, fieldAccess, fieldNumber)
    } else {
      val sizeExpr = computeSizeExprForField(field.fieldType, fieldNumber, unwrapped)
      val assignStmt = jvm.Assign(sizeVar, code"${sizeVar.code} + $sizeExpr").code
      field.fieldType match {
        case ProtoType.Message(_) =>
          List(jvm.If(lang.notEquals(fieldAccess, code"null"), assignStmt).code)
        case _ =>
          List(assignStmt)
      }
    }
  }

  /** Compute size expression for a field type */
  private def computeSizeExprForField(fieldType: ProtoType, fieldNumber: jvm.Code, value: jvm.Code): jvm.Code = {
    fieldType match {
      case ProtoType.Message(_) =>
        val nestedSize = value.callNullary("getSerializedSize")
        code"$CodedOutputStreamType.computeTagSize($fieldNumber) + $CodedOutputStreamType.computeUInt32SizeNoTag($nestedSize) + $nestedSize"

      case ProtoType.Timestamp =>
        val seconds = value.invoke("getEpochSecond")
        val nanos = value.invoke("getNano")
        val innerSize = code"$CodedOutputStreamType.computeInt64Size(1, $seconds) + $CodedOutputStreamType.computeInt32Size(2, $nanos)"
        code"$CodedOutputStreamType.computeTagSize($fieldNumber) + $CodedOutputStreamType.computeUInt32SizeNoTag($innerSize) + $innerSize"

      case ProtoType.Duration =>
        val seconds = value.invoke("getSeconds")
        val nanos = value.invoke("getNano")
        val innerSize = code"$CodedOutputStreamType.computeInt64Size(1, $seconds) + $CodedOutputStreamType.computeInt32Size(2, $nanos)"
        code"$CodedOutputStreamType.computeTagSize($fieldNumber) + $CodedOutputStreamType.computeUInt32SizeNoTag($innerSize) + $innerSize"

      case ProtoType.Enum(_) =>
        code"$CodedOutputStreamType.computeEnumSize($fieldNumber, ${value.callNullary("toValue")})"

      case wkt if ProtoType.isWrapperType(wkt) =>
        val underlying = ProtoType.unwrapWellKnown(wkt).getOrElse(sys.error(s"Not a wrapper type: $wkt"))
        val innerValue = value.invoke("get")
        val innerSize = computeSizeExpr(underlying, code"1", innerValue)
        code"$CodedOutputStreamType.computeTagSize($fieldNumber) + $CodedOutputStreamType.computeUInt32SizeNoTag($innerSize) + $innerSize"

      case _ =>
        computeSizeExpr(fieldType, fieldNumber, value)
    }
  }

  /** Generate size stmts for repeated field */
  private def generateRepeatedFieldSizeStmts(sizeVar: jvm.Ident, field: ProtoField, value: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    val elem = jvm.Ident("elem")
    val unwrappedElem = field.wrapperType match {
      case Some(_) => elem.code.callNullary("unwrap")
      case None    => elem.code
    }
    val elemSize = computeSizeExprForField(field.fieldType, fieldNumber, unwrappedElem)
    val body = List(jvm.Assign(sizeVar, code"${sizeVar.code} + $elemSize").code)
    val elemType = typeMapper.mapFieldType(field.copy(label = ProtoFieldLabel.Optional))
    List(lang.ListType.forEachStmt(value, elem, elemType)(_ => body))
  }

  /** Generate size stmts for map field */
  private def generateMapFieldSizeStmts(sizeVar: jvm.Ident, field: ProtoField, value: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    field.fieldType match {
      case ProtoType.Map(keyType, valueType) =>
        val keyJvmType = typeMapper.mapType(keyType)
        val valueJvmType = typeMapper.mapType(valueType)
        val forEachCode = lang.MapOps.forEachEntry(value, keyJvmType, valueJvmType) { (keyRef, valRef) =>
          val keySizeExpr = computeSizeExpr(keyType, code"1", keyRef)
          val valSizeExpr = computeSizeExpr(valueType, code"2", valRef)
          val entrySize = code"$keySizeExpr + $valSizeExpr"
          val totalEntrySize = code"$CodedOutputStreamType.computeTagSize($fieldNumber) + $CodedOutputStreamType.computeUInt32SizeNoTag($entrySize) + $entrySize"
          List(jvm.Assign(sizeVar, code"${sizeVar.code} + $totalEntrySize").code)
        }
        List(forEachCode)
      case _ => Nil
    }
  }

  /** Generate size stmts for optional field */
  private def generateOptionalFieldSizeStmts(sizeVar: jvm.Ident, field: ProtoField, fieldAccess: jvm.Code, fieldNumber: jvm.Code): List[jvm.Code] = {
    val v = jvm.Ident("v")
    val getV = jvm.LocalVar(v, None, lang.Optional.get(fieldAccess))
    val unwrappedV = field.wrapperType match {
      case Some(_) => v.code.callNullary("unwrap")
      case None    => v.code
    }
    val sizeExpr = field.fieldType match {
      case wkt if ProtoType.isWrapperType(wkt) =>
        val underlying = ProtoType.unwrapWellKnown(wkt).getOrElse(sys.error(s"Not a wrapper type: $wkt"))
        val innerValue = unwrappedV
        val innerSize = computeSizeExpr(underlying, code"1", innerValue)
        code"$CodedOutputStreamType.computeTagSize($fieldNumber) + $CodedOutputStreamType.computeUInt32SizeNoTag($innerSize) + $innerSize"
      case _ =>
        computeSizeExprForField(field.fieldType, fieldNumber, unwrappedV)
    }
    val body = List(getV.code, jvm.Assign(sizeVar, code"${sizeVar.code} + $sizeExpr").code).map(s => code"$s;").mkCode("\n")
    List(jvm.If(lang.Optional.isDefined(fieldAccess), body).code)
  }

  /** Generate size stmts for oneof */
  private def generateOneofSizeStmts(sizeVar: jvm.Ident, oneof: ProtoOneof, messageFullName: String): List[jvm.Code] = {
    val oneofType = naming.grpcOneofTypeName(messageFullName, oneof.name)
    val fieldAccess = lang.prop(code"this", naming.grpcFieldName(oneof.name))

    val cases = oneof.fields.map { field =>
      val caseName = naming.grpcOneofCaseName(field)
      val caseType = oneofType / caseName
      val caseIdent = jvm.Ident("c")
      val fieldValue = lang.prop(caseIdent.code, naming.grpcFieldName(field.name))
      val unwrapped = field.wrapperType match {
        case Some(_) => fieldValue.callNullary("unwrap")
        case None    => fieldValue
      }
      val fieldNumber = intCode(field.number)
      val sizeExpr = computeSizeExprForField(field.fieldType, fieldNumber, unwrapped)
      jvm.TypeSwitch.Case(caseType, caseIdent, jvm.Assign(sizeVar, code"${sizeVar.code} + $sizeExpr").code)
    }

    List(jvm.TypeSwitch(fieldAccess, cases, nullCase = Some(code"{}")).code)
  }

  /** Generate parseFrom(CodedInputStream) static method */
  private def generateParseFromMethod(message: ProtoMessage, cleanType: jvm.Type.Qualified): jvm.Method = {
    val inputParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("input"), CodedInputStreamType, None)
    val input = inputParam.name.code

    val regularFields = message.fields.filter(_.oneofIndex.isEmpty)

    // Declare mutable local variables with default values for each field (reassigned in while loop)
    val fieldVarDecls = regularFields.map { field =>
      val varName = jvm.Ident(naming.grpcFieldName(field.name).value)
      val defaultValue = defaultValueForField(field)
      val varType = if (field.isRepeated) {
        lang.typeSupport.MutableListOps.tpe.of(typeMapper.mapFieldType(field.copy(label = ProtoFieldLabel.Optional)))
      } else if (field.isMapField) {
        field.fieldType match {
          case ProtoType.Map(keyType, valueType) =>
            lang.MapOps.mutableImpl.of(typeMapper.mapType(keyType), typeMapper.mapType(valueType))
          case _ => typeMapper.mapFieldType(field)
        }
      } else {
        val baseType = typeMapper.mapFieldType(field)
        field.fieldType match {
          case ProtoType.Message(_) if !field.proto3Optional => lang.nullableRefType(baseType)
          case _                                             => baseType
        }
      }
      jvm.MutableVar(varName, Some(varType), defaultValue)
    }

    val oneofVarDecls = message.oneofs.map { oneof =>
      val varName = jvm.Ident(naming.grpcFieldName(oneof.name).value)
      val oneofType = naming.grpcOneofTypeName(message.fullName, oneof.name)
      jvm.MutableVar(varName, Some(lang.nullableRefType(oneofType)), code"null")
    }

    // Build if-else chain for tag parsing (no Switch in AST, use IfElseChain)
    val allFields = regularFields ++ message.oneofs.flatMap(_.fields)

    val tagVar = jvm.Ident("tag")
    val tagDecl = jvm.LocalVar(tagVar, None, input.invoke("readTag"))
    val fieldNumberExpr = WireFormatType.code.invoke("getTagFieldNumber", tagVar.code)

    val ifCases: List[(jvm.Code, jvm.Code)] = allFields.map { field =>
      val cond = code"$fieldNumberExpr == ${intCode(field.number)}"
      val readStmts = generateFieldRead(input, field, message)
      val body =
        if (readStmts.size == 1) readStmts.head
        else readStmts.map(s => code"$s;").mkCode("\n")
      (cond, body)
    }
    val elseCase = input.invoke("skipField", tagVar.code)

    val dispatchStmt = if (ifCases.nonEmpty) {
      jvm.IfElseChain(ifCases, elseCase).code
    } else {
      elseCase
    }

    val whileBody = List(tagDecl.code, dispatchStmt)
    val whileLoop = jvm.While(code"!${input.invoke("isAtEnd")}", whileBody)

    // Build constructor call - convert mutable collections to immutable for the record fields
    val fieldArgs = regularFields.map { field =>
      val varRef = jvm.Ident(naming.grpcFieldName(field.name).value).code
      val argValue = if (field.isRepeated) {
        lang.typeSupport.MutableListOps.toImmutable(varRef)
      } else if (field.isMapField) {
        field.fieldType match {
          case ProtoType.Map(keyType, valueType) =>
            lang.MapOps.toImmutable(varRef, typeMapper.mapType(keyType), typeMapper.mapType(valueType))
          case _ => varRef
        }
      } else {
        varRef
      }
      jvm.Arg.Pos(argValue)
    }
    val oneofArgs = message.oneofs.map { oneof =>
      jvm.Arg.Pos(jvm.Ident(naming.grpcFieldName(oneof.name).value).code)
    }
    val constructorCall = jvm.New(cleanType.code, fieldArgs ++ oneofArgs)
    val returnStmt = jvm.Return(constructorCall.code).code

    val allStmts = fieldVarDecls.map(_.code) ++ oneofVarDecls.map(_.code) ++ List(whileLoop.code, returnStmt)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("parseFrom"),
      params = List(inputParam),
      implicitParams = Nil,
      tpe = cleanType,
      throws = List(IOException),
      body = jvm.Body.Stmts(allStmts),
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate the read expression(s) for a field inside the tag dispatch */
  private def generateFieldRead(input: jvm.Code, field: ProtoField, message: ProtoMessage): List[jvm.Code] = {
    val isOneofField = field.oneofIndex.isDefined
    val varName = if (isOneofField) {
      val oneof = message.oneofs(field.oneofIndex.get)
      jvm.Ident(naming.grpcFieldName(oneof.name).value)
    } else {
      jvm.Ident(naming.grpcFieldName(field.name).value)
    }

    if (isOneofField) {
      val oneof = message.oneofs(field.oneofIndex.get)
      val oneofType = naming.grpcOneofTypeName(message.fullName, oneof.name)
      val caseName = naming.grpcOneofCaseName(field)
      val caseType = oneofType / caseName
      if (isLenDelimitedType(field.fieldType)) {
        generateLenDelimitedRead(input, field.fieldType, field.wrapperType) { readValue =>
          val caseConstructor = jvm.New(caseType.code, List(jvm.Arg.Pos(readValue)))
          List(jvm.Assign(varName, caseConstructor.code).code)
        }
      } else {
        val readValue = readScalarValue(input, field.fieldType, field.wrapperType)
        val caseConstructor = jvm.New(caseType.code, List(jvm.Arg.Pos(readValue)))
        List(jvm.Assign(varName, caseConstructor.code).code)
      }
    } else if (field.isMapField) {
      generateMapFieldRead(input, field, varName)
    } else if (field.isRepeated) {
      if (isLenDelimitedType(field.fieldType)) {
        generateLenDelimitedRead(input, field.fieldType, field.wrapperType) { readValue =>
          List(lang.typeSupport.MutableListOps.add(varName.code, readValue))
        }
      } else {
        val readValue = readScalarValue(input, field.fieldType, field.wrapperType)
        List(lang.typeSupport.MutableListOps.add(varName.code, readValue))
      }
    } else if (field.proto3Optional || ProtoType.isWrapperType(field.fieldType)) {
      if (isLenDelimitedType(field.fieldType)) {
        generateLenDelimitedRead(input, field.fieldType, field.wrapperType) { readValue =>
          List(jvm.Assign(varName, lang.Optional.some(readValue)).code)
        }
      } else {
        val readValue = readScalarValue(input, field.fieldType, field.wrapperType)
        List(jvm.Assign(varName, lang.Optional.some(readValue)).code)
      }
    } else {
      if (isLenDelimitedType(field.fieldType)) {
        generateLenDelimitedRead(input, field.fieldType, field.wrapperType) { readValue =>
          List(jvm.Assign(varName, readValue).code)
        }
      } else {
        val readValue = readScalarValue(input, field.fieldType, field.wrapperType)
        List(jvm.Assign(varName, readValue).code)
      }
    }
  }

  /** Check if a field type requires length-delimited (LEN wire type) parsing with pushLimit/popLimit */
  private def isLenDelimitedType(fieldType: ProtoType): Boolean = fieldType match {
    case ProtoType.Message(_)                => true
    case ProtoType.Timestamp                 => true
    case ProtoType.Duration                  => true
    case wkt if ProtoType.isWrapperType(wkt) => true
    case _                                   => false
  }

  /** Generate pushLimit/popLimit wrapper for reading a length-delimited nested message. The `body` function receives the read value expression and returns the assignment/usage statements.
    */
  private def generateLenDelimitedRead(input: jvm.Code, fieldType: ProtoType, wrapperType: Option[String])(
      body: jvm.Code => List[jvm.Code]
  ): List[jvm.Code] = {
    val length = jvm.Ident("_length")
    val limit = jvm.Ident("_oldLimit")
    val lengthDecl = jvm.LocalVar(length, None, input.invoke("readRawVarint32"))
    val limitDecl = jvm.LocalVar(limit, None, input.invoke("pushLimit", length.code))

    val (innerStmts, readValue) = readLenDelimitedValue(input, fieldType, wrapperType)
    val popLimit = input.invoke("popLimit", limit.code)
    List(lengthDecl.code, limitDecl.code) ++ innerStmts ++ body(readValue) ++ List(popLimit)
  }

  /** Read a value from a length-delimited sub-message (after pushLimit is already applied). Returns (extra statements needed before the value, the value expression).
    */
  private def readLenDelimitedValue(input: jvm.Code, fieldType: ProtoType, wrapperType: Option[String]): (List[jvm.Code], jvm.Code) = {
    val (stmts, raw) = fieldType match {
      case ProtoType.Message(fullName) =>
        val msgType = naming.grpcMessageTypeName(fullName)
        (Nil, msgType.code.invoke("parseFrom", input))

      case ProtoType.Timestamp =>
        generateTimestampRead(input)

      case ProtoType.Duration =>
        generateDurationRead(input)

      case wkt if ProtoType.isWrapperType(wkt) =>
        val underlying = ProtoType.unwrapWellKnown(wkt).getOrElse(sys.error(s"Not a wrapper type: $wkt"))
        val skipTag = input.invoke("readTag")
        (List(skipTag), readPrimitiveValue(input, underlying))

      case _ => (Nil, readPrimitiveValue(input, fieldType))
    }
    val finalValue = wrapperType match {
      case Some(wrapperName) =>
        val wType = wrapperTypeMap.getOrElse(wrapperName, sys.error(s"Wrapper type $wrapperName not found"))
        wType.code.invoke("valueOf", raw)
      case None => raw
    }
    (stmts, finalValue)
  }

  /** Generate statements to read a Timestamp from the stream (pushLimit already applied). Returns (statements, value expression).
    */
  private def generateTimestampRead(input: jvm.Code): (List[jvm.Code], jvm.Code) = {
    val seconds = jvm.Ident("_tsSeconds")
    val nanos = jvm.Ident("_tsNanos")
    val secondsDecl = jvm.MutableVar(seconds, None, code"0L")
    val nanosDecl = jvm.MutableVar(nanos, None, code"0")

    val innerTag = jvm.Ident("_tsTag")
    val innerTagDecl = jvm.LocalVar(innerTag, None, input.invoke("readTag"))
    val innerFieldNumber = WireFormatType.code.invoke("getTagFieldNumber", innerTag.code)

    val ifChain = jvm.IfElseChain(
      List(
        (code"$innerFieldNumber == 1", jvm.Assign(seconds, input.invoke("readInt64")).code),
        (code"$innerFieldNumber == 2", jvm.Assign(nanos, input.invoke("readInt32")).code)
      ),
      input.invoke("skipField", innerTag.code)
    )
    val whileLoop = jvm.While(code"!${input.invoke("isAtEnd")}", List(innerTagDecl.code, ifChain.code))

    val InstantType = jvm.Type.Qualified(jvm.QIdent("java.time.Instant"))
    val value = InstantType.code.invoke("ofEpochSecond", seconds.code, lang.toLong(nanos.code))

    (List(secondsDecl.code, nanosDecl.code, whileLoop.code), value)
  }

  /** Generate statements to read a Duration from the stream (pushLimit already applied). Returns (statements, value expression).
    */
  private def generateDurationRead(input: jvm.Code): (List[jvm.Code], jvm.Code) = {
    val seconds = jvm.Ident("_durSeconds")
    val nanos = jvm.Ident("_durNanos")
    val secondsDecl = jvm.MutableVar(seconds, None, code"0L")
    val nanosDecl = jvm.MutableVar(nanos, None, code"0")

    val innerTag = jvm.Ident("_durTag")
    val innerTagDecl = jvm.LocalVar(innerTag, None, input.invoke("readTag"))
    val innerFieldNumber = WireFormatType.code.invoke("getTagFieldNumber", innerTag.code)

    val ifChain = jvm.IfElseChain(
      List(
        (code"$innerFieldNumber == 1", jvm.Assign(seconds, input.invoke("readInt64")).code),
        (code"$innerFieldNumber == 2", jvm.Assign(nanos, input.invoke("readInt32")).code)
      ),
      input.invoke("skipField", innerTag.code)
    )
    val whileLoop = jvm.While(code"!${input.invoke("isAtEnd")}", List(innerTagDecl.code, ifChain.code))

    val DurationType = jvm.Type.Qualified(jvm.QIdent("java.time.Duration"))
    val value = DurationType.code.invoke("ofSeconds", seconds.code, lang.toLong(nanos.code))

    (List(secondsDecl.code, nanosDecl.code, whileLoop.code), value)
  }

  /** Generate read for a map field entry */
  private def generateMapFieldRead(input: jvm.Code, field: ProtoField, varName: jvm.Ident): List[jvm.Code] = {
    field.fieldType match {
      case ProtoType.Map(keyType, valueType) =>
        val keyVar = jvm.Ident("mapKey")
        val valVar = jvm.Ident("mapValue")
        val keyDefault = defaultValueForType(keyType, None)
        val valDefault = defaultValueForType(valueType, None)
        val length = jvm.Ident("length")
        val limit = jvm.Ident("oldLimit")

        val lengthDecl = jvm.LocalVar(length, None, input.invoke("readRawVarint32"))
        val limitDecl = jvm.LocalVar(limit, None, input.invoke("pushLimit", length.code))
        val keyDecl = jvm.MutableVar(keyVar, None, keyDefault)
        val valDecl = jvm.MutableVar(valVar, None, valDefault)

        val innerTagVar = jvm.Ident("entryTag")
        val innerTagDecl = jvm.LocalVar(innerTagVar, None, input.invoke("readTag"))
        val keyRead = readPrimitiveValue(input, keyType)
        val valRead = readPrimitiveValue(input, valueType)
        val innerFieldNumber = WireFormatType.code.invoke("getTagFieldNumber", innerTagVar.code)

        val innerIfChain = jvm.IfElseChain(
          List(
            (code"$innerFieldNumber == 1", jvm.Assign(keyVar, keyRead).code),
            (code"$innerFieldNumber == 2", jvm.Assign(valVar, valRead).code)
          ),
          input.invoke("skipField", innerTagVar.code)
        )
        val innerWhile = jvm.While(code"!${input.invoke("isAtEnd")}", List(innerTagDecl.code, innerIfChain.code))
        val popLimit = input.invoke("popLimit", limit.code)
        val putEntry = lang.MapOps.putVoid(varName.code, keyVar.code, valVar.code)

        List(lengthDecl.code, limitDecl.code, keyDecl.code, valDecl.code, innerWhile.code, popLimit, putEntry)
      case _ => Nil
    }
  }

  /** Read a scalar/message value from input */
  private def readScalarValue(input: jvm.Code, fieldType: ProtoType, wrapperType: Option[String]): jvm.Code = {
    val raw = readPrimitiveValue(input, fieldType)
    wrapperType match {
      case Some(wrapperName) =>
        val wType = wrapperTypeMap.getOrElse(wrapperName, sys.error(s"Wrapper type $wrapperName not found"))
        wType.code.invoke("valueOf", raw)
      case None => raw
    }
  }

  /** Read a primitive/message value from CodedInputStream */
  private def readPrimitiveValue(input: jvm.Code, fieldType: ProtoType): jvm.Code = fieldType match {
    case ProtoType.Double   => input.invoke("readDouble")
    case ProtoType.Float    => input.invoke("readFloat")
    case ProtoType.Int32    => input.invoke("readInt32")
    case ProtoType.Int64    => input.invoke("readInt64")
    case ProtoType.UInt32   => input.invoke("readUInt32")
    case ProtoType.UInt64   => input.invoke("readUInt64")
    case ProtoType.SInt32   => input.invoke("readSInt32")
    case ProtoType.SInt64   => input.invoke("readSInt64")
    case ProtoType.Fixed32  => input.invoke("readFixed32")
    case ProtoType.Fixed64  => input.invoke("readFixed64")
    case ProtoType.SFixed32 => input.invoke("readSFixed32")
    case ProtoType.SFixed64 => input.invoke("readSFixed64")
    case ProtoType.Bool     => input.invoke("readBool")
    case ProtoType.String   => input.invoke("readString")
    case ProtoType.Bytes    => input.invoke("readBytes")

    case ProtoType.Enum(fullName) =>
      val enumType = naming.grpcEnumTypeName(fullName)
      enumType.code.invoke("fromValue", input.invoke("readEnum"))

    case ProtoType.Message(fullName) =>
      val msgType = naming.grpcMessageTypeName(fullName)
      msgType.code.invoke("parseFrom", input)

    case ProtoType.Timestamp =>
      val InstantType = jvm.Type.Qualified(jvm.QIdent("java.time.Instant"))
      InstantType.code.select("EPOCH")

    case ProtoType.Duration =>
      val DurationType = jvm.Type.Qualified(jvm.QIdent("java.time.Duration"))
      DurationType.code.select("ZERO")

    case wkt if ProtoType.isWrapperType(wkt) =>
      val underlying = ProtoType.unwrapWellKnown(wkt).getOrElse(sys.error(s"Not a wrapper type: $wkt"))
      readPrimitiveValue(input, underlying)

    case _ => input.invoke("readString")
  }

  /** Get default value for a field */
  private def defaultValueForField(field: ProtoField): jvm.Code = {
    if (field.isRepeated) {
      lang.typeSupport.MutableListOps.empty
    } else if (field.isMapField) {
      field.fieldType match {
        case ProtoType.Map(keyType, valueType) =>
          lang.MapOps.newMutableMap(typeMapper.mapType(keyType), typeMapper.mapType(valueType))
        case _ => lang.MapOps.newMutableMap(lang.topType, lang.topType)
      }
    } else if (field.proto3Optional || ProtoType.isWrapperType(field.fieldType)) {
      lang.Optional.none
    } else {
      defaultValueForType(field.fieldType, field.wrapperType)
    }
  }

  /** Get default value for a proto type */
  private def defaultValueForType(protoType: ProtoType, wrapperType: Option[String]): jvm.Code = {
    val raw: jvm.Code = protoType match {
      case ProtoType.Double                                                                               => code"0.0"
      case ProtoType.Float                                                                                => code"0.0f"
      case ProtoType.Int32 | ProtoType.UInt32 | ProtoType.SInt32 | ProtoType.Fixed32 | ProtoType.SFixed32 => code"0"
      case ProtoType.Int64 | ProtoType.UInt64 | ProtoType.SInt64 | ProtoType.Fixed64 | ProtoType.SFixed64 => code"0L"
      case ProtoType.Bool                                                                                 => code"false"
      case ProtoType.String                                                                               => jvm.StrLit("").code
      case ProtoType.Bytes                                                                                => jvm.Type.Qualified(jvm.QIdent("com.google.protobuf.ByteString")).code.select("EMPTY")
      case ProtoType.Enum(fullName)                                                                       => naming.grpcEnumTypeName(fullName).code.invoke("fromValue", code"0")
      case ProtoType.Message(_)                                                                           => code"null"
      case ProtoType.Timestamp                                                                            => jvm.Type.Qualified(jvm.QIdent("java.time.Instant")).code.select("EPOCH")
      case ProtoType.Duration                                                                             => jvm.Type.Qualified(jvm.QIdent("java.time.Duration")).code.select("ZERO")
      case wkt if ProtoType.isWrapperType(wkt)                                                            => lang.Optional.none
      case _                                                                                              => code"null"
    }
    wrapperType match {
      case Some(wrapperName) =>
        val wType = wrapperTypeMap.getOrElse(wrapperName, sys.error(s"Wrapper type $wrapperName not found"))
        wType.code.invoke("valueOf", raw)
      case None => raw
    }
  }

  /** Generate the MARSHALLER static field */
  private def generateMarshallerField(tpe: jvm.Type.Qualified): jvm.ClassMember = {
    val marshallerTpe = MarshallerType.of(tpe)

    val valueParam = jvm.Ident("value")
    val streamParam = jvm.Ident("stream")

    // stream method: serialize to byte array then wrap in ByteArrayInputStream
    val streamMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("stream"),
      params = List(jvm.Param(Nil, jvm.Comments.Empty, valueParam, tpe, None)),
      implicitParams = Nil,
      tpe = InputStreamType,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.LocalVar(jvm.Ident("bytes"), None, lang.newByteArray(valueParam.code.callNullary("getSerializedSize"))).code,
          jvm.LocalVar(jvm.Ident("cos"), None, CodedOutputStreamType.code.invoke("newInstance", jvm.Ident("bytes").code)).code,
          jvm
            .TryCatch(
              tryBlock = List(
                valueParam.code.invoke("writeTo", jvm.Ident("cos").code),
                jvm.Ident("cos").code.invoke("flush")
              ),
              catches = List(
                jvm.TryCatch.Catch(
                  exceptionType = IOException,
                  ident = jvm.Ident("e"),
                  body = List(
                    jvm
                      .Throw(
                        jvm.Type.Qualified("java.lang.RuntimeException").construct(jvm.Ident("e").code)
                      )
                      .code
                  )
                )
              ),
              finallyBlock = Nil
            )
            .code,
          jvm.Return(ByteArrayInputStreamType.construct(jvm.Ident("bytes").code)).code
        )
      ),
      isOverride = true,
      isDefault = false
    )

    // parse method: read from InputStream
    val parseMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("parse"),
      params = List(jvm.Param(Nil, jvm.Comments.Empty, streamParam, InputStreamType, None)),
      implicitParams = Nil,
      tpe = tpe,
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm
            .TryCatch(
              tryBlock = List(
                jvm.Return(tpe.code.invoke("parseFrom", CodedInputStreamType.code.invoke("newInstance", streamParam.code))).code
              ),
              catches = List(
                jvm.TryCatch.Catch(
                  exceptionType = IOException,
                  ident = jvm.Ident("e"),
                  body = List(
                    jvm
                      .Throw(
                        jvm.Type.Qualified("java.lang.RuntimeException").construct(jvm.Ident("e").code)
                      )
                      .code
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

    jvm.Given(
      tparams = Nil,
      name = naming.grpcMarshallerName,
      implicitParams = Nil,
      tpe = marshallerTpe,
      body = jvm
        .NewWithBody(
          extendsClass = None,
          implementsInterface = Some(marshallerTpe),
          members = List(streamMethod, parseMethod)
        )
        .code
    )
  }

  // ============================================================
  // Helper methods for wire format method names and size computation
  // ============================================================

  /** Get the CodedOutputStream write method name for a proto type */
  private def writeMethodName(protoType: ProtoType): String = protoType match {
    case ProtoType.Double   => "writeDouble"
    case ProtoType.Float    => "writeFloat"
    case ProtoType.Int32    => "writeInt32"
    case ProtoType.Int64    => "writeInt64"
    case ProtoType.UInt32   => "writeUInt32"
    case ProtoType.UInt64   => "writeUInt64"
    case ProtoType.SInt32   => "writeSInt32"
    case ProtoType.SInt64   => "writeSInt64"
    case ProtoType.Fixed32  => "writeFixed32"
    case ProtoType.Fixed64  => "writeFixed64"
    case ProtoType.SFixed32 => "writeSFixed32"
    case ProtoType.SFixed64 => "writeSFixed64"
    case ProtoType.Bool     => "writeBool"
    case ProtoType.String   => "writeString"
    case ProtoType.Bytes    => "writeBytes"
    case ProtoType.Enum(_)  => "writeEnum"
    case _                  => "writeString"
  }

  /** Get the CodedOutputStream compute*Size method name for a proto type */
  private def computeSizeMethodName(protoType: ProtoType): String = protoType match {
    case ProtoType.Double   => "computeDoubleSize"
    case ProtoType.Float    => "computeFloatSize"
    case ProtoType.Int32    => "computeInt32Size"
    case ProtoType.Int64    => "computeInt64Size"
    case ProtoType.UInt32   => "computeUInt32Size"
    case ProtoType.UInt64   => "computeUInt64Size"
    case ProtoType.SInt32   => "computeSInt32Size"
    case ProtoType.SInt64   => "computeSInt64Size"
    case ProtoType.Fixed32  => "computeFixed32Size"
    case ProtoType.Fixed64  => "computeFixed64Size"
    case ProtoType.SFixed32 => "computeSFixed32Size"
    case ProtoType.SFixed64 => "computeSFixed64Size"
    case ProtoType.Bool     => "computeBoolSize"
    case ProtoType.String   => "computeStringSize"
    case ProtoType.Bytes    => "computeBytesSize"
    case ProtoType.Enum(_)  => "computeEnumSize"
    case _                  => "computeStringSize"
  }

  /** Generate a size computation expression for a scalar field */
  private def computeSizeExpr(protoType: ProtoType, fieldNumber: jvm.Code, value: jvm.Code): jvm.Code = {
    val method = computeSizeMethodName(protoType)
    code"$CodedOutputStreamType.$method($fieldNumber, $value)"
  }
}
