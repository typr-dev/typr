package typr.avro.codegen

import typr.avro._
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.{jvm, Lang, Naming, Scope}
import typr.internal.codegen._

/** Generates typed header classes for Kafka message headers */
class HeaderCodegen(
    naming: Naming,
    lang: Lang
) {

  // Kafka Headers types
  private val HeadersType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.common.header.Headers"))
  private val RecordHeadersType = jvm.Type.Qualified(jvm.QIdent("org.apache.kafka.common.header.internals.RecordHeaders"))

  // Java/standard library types
  private val UUID = jvm.Type.Qualified(jvm.QIdent("java.util.UUID"))
  private val Instant = jvm.Type.Qualified(jvm.QIdent("java.time.Instant"))
  private val StandardCharsets = jvm.Type.Qualified(jvm.QIdent("java.nio.charset.StandardCharsets"))

  /** Generate a typed header class for a header schema */
  def generateHeaderClass(name: String, schema: HeaderSchema): jvm.File = {
    val className = name.capitalize + "Headers"
    val tpe = jvm.Type.Qualified(naming.avroHeaderPackage / jvm.Ident(className))

    val params = schema.fields.map(fieldToParam)
    val staticMembers = List(generateFromHeadersMethod(tpe, schema))
    val toHeadersMethod = generateToHeadersMethod(schema)

    val recordAdt = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = jvm.Comments(List(s"Typed headers for Kafka messages")),
      name = tpe,
      tparams = Nil,
      params = params,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = List(toHeadersMethod),
      staticMembers = staticMembers
    )

    jvm.File(tpe, jvm.Code.Tree(recordAdt), secondaryTypes = Nil, scope = Scope.Main)
  }

  private def fieldToParam(field: HeaderField): jvm.Param[jvm.Type] = {
    val fieldType = headerTypeToJvmType(field.headerType)
    val paramType = if (field.required) fieldType else lang.Optional.tpe(fieldType)
    jvm.Param[jvm.Type](Nil, jvm.Comments.Empty, jvm.Ident(field.name), paramType, None)
  }

  private def headerTypeToJvmType(headerType: typr.avro.HeaderType): jvm.Type = headerType match {
    case typr.avro.HeaderType.String  => lang.String
    case typr.avro.HeaderType.UUID    => UUID
    case typr.avro.HeaderType.Instant => Instant
    case typr.avro.HeaderType.Long    => lang.Long
    case typr.avro.HeaderType.Int     => lang.Int
    case typr.avro.HeaderType.Boolean => lang.Boolean
  }

  private def generateToHeadersMethod(schema: HeaderSchema): jvm.Method = {
    val addHeaderStmts = schema.fields.flatMap { field =>
      val fieldIdent = jvm.Ident(field.name)
      val headerName = jvm.StrLit(field.name)
      if (field.required) {
        List(addHeaderStmt(headerName, fieldIdent.code, field.headerType))
      } else {
        List(addOptionalHeaderStmt(headerName, fieldIdent.code, field.headerType))
      }
    }

    val body = List(
      jvm
        .LocalVar(
          name = jvm.Ident("headers"),
          tpe = Some(HeadersType),
          value = RecordHeadersType.construct()
        )
        .code
    ) ++ addHeaderStmts ++ List(
      jvm.Return(jvm.Ident("headers").code).code
    )

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Convert to Kafka Headers")),
      tparams = Nil,
      name = jvm.Ident("toHeaders"),
      params = Nil,
      implicitParams = Nil,
      tpe = HeadersType,
      throws = Nil,
      body = jvm.Body.Stmts(body),
      isOverride = false,
      isDefault = false
    )
  }

  private def addHeaderStmt(headerName: jvm.StrLit, fieldValue: jvm.Code, headerType: typr.avro.HeaderType): jvm.Code = {
    val bytesValue = toBytes(fieldValue, headerType)
    jvm.Stmt.simple(jvm.Ident("headers").code.invoke("add", headerName.code, bytesValue)).code
  }

  private def addOptionalHeaderStmt(headerName: jvm.StrLit, fieldValue: jvm.Code, headerType: typr.avro.HeaderType): jvm.Code = {
    lang.extension match {
      case "java" =>
        val lambda = code"v -> headers.add(${headerName.code}, ${toBytes(code"v", headerType)})"
        jvm.Stmt.simple(fieldValue.invoke("ifPresent", lambda)).code
      case "kt" =>
        // Use safe call operator ?.let for Kotlin nullable types
        val addCall = jvm.Ident("headers").code.invoke("add", headerName.code, toBytes(code"it", headerType))
        jvm.Stmt.simple(code"$fieldValue?.let { $addCall }").code
      case "scala" =>
        val addCall = jvm.Ident("headers").code.invoke("add", headerName.code, toBytes(code"v", headerType))
        jvm.Stmt.simple(fieldValue.invoke("foreach", code"v => $addCall")).code
      case _ =>
        throw new RuntimeException(s"Unsupported language: ${lang.extension}")
    }
  }

  private def toBytes(value: jvm.Code, headerType: typr.avro.HeaderType): jvm.Code = {
    val utf8 = StandardCharsets.code.select("UTF_8")
    val JavaLong = jvm.Type.Qualified(jvm.QIdent("java.lang.Long"))

    def getBytes(str: jvm.Code): jvm.Code = lang.extension match {
      case "kt" => str.invoke("toByteArray", utf8)
      case _    => str.invoke("getBytes", utf8)
    }

    headerType match {
      case typr.avro.HeaderType.String  => getBytes(value)
      case typr.avro.HeaderType.UUID    => getBytes(value.invoke("toString"))
      case typr.avro.HeaderType.Instant =>
        // toEpochMilli() returns primitive long
        val epochMillis = lang.extension match {
          case "kt" => value.invoke("toEpochMilli")
          case _    => lang.nullaryMethodCall(value, jvm.Ident("toEpochMilli"))
        }
        // For Kotlin, use value.toString(), for Java use Long.toString(value)
        val epochStr = lang.extension match {
          case "kt" => epochMillis.invoke("toString")
          case _    => JavaLong.code.invoke("toString", epochMillis)
        }
        getBytes(epochStr)
      case typr.avro.HeaderType.Long    => getBytes(value.invoke("toString"))
      case typr.avro.HeaderType.Int     => getBytes(value.invoke("toString"))
      case typr.avro.HeaderType.Boolean => getBytes(value.invoke("toString"))
    }
  }

  private def generateFromHeadersMethod(classType: jvm.Type.Qualified, schema: HeaderSchema): jvm.Method = {
    val headersParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("headers"), HeadersType, None)

    val parseStmts = schema.fields.map { field =>
      val fieldIdent = jvm.Ident(field.name)
      val headerName = jvm.StrLit(field.name)
      generateParseFieldStmt(fieldIdent, headerName, field)
    }

    val constructorArgs = schema.fields.map(f => jvm.Arg.Pos(jvm.Ident(f.name).code))
    val returnStmt = jvm.Return(jvm.New(classType, constructorArgs).code).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Parse from Kafka Headers")),
      tparams = Nil,
      name = jvm.Ident("fromHeaders"),
      params = List(headersParam),
      implicitParams = Nil,
      tpe = classType,
      throws = Nil,
      body = jvm.Body.Stmts(parseStmts ++ List(returnStmt)),
      isOverride = false,
      isDefault = false
    )
  }

  private def generateParseFieldStmt(fieldIdent: jvm.Ident, headerName: jvm.StrLit, field: HeaderField): jvm.Code = {
    val headerValue = jvm.Ident("headers").code.invoke("lastHeader", headerName.code)
    val fieldType = headerTypeToJvmType(field.headerType)

    if (field.required) {
      val parseExpr = parseFromBytes(headerValue.invoke("value"), field.headerType)
      jvm
        .LocalVar(
          name = fieldIdent,
          tpe = Some(fieldType),
          value = parseExpr
        )
        .code
    } else {
      val optionalType = lang.Optional.tpe(fieldType)
      val parseExpr = generateOptionalParse(headerValue, field.headerType)
      jvm
        .LocalVar(
          name = fieldIdent,
          tpe = Some(optionalType),
          value = parseExpr
        )
        .code
    }
  }

  private def parseFromBytes(bytesExpr: jvm.Code, headerType: typr.avro.HeaderType): jvm.Code = {
    val utf8 = StandardCharsets.code.select("UTF_8")
    // Use java.lang.String for constructor (kotlin.String doesn't have byte array constructor)
    val JavaStringType = jvm.Type.Qualified(jvm.QIdent("java.lang.String"))
    val stringValue = jvm.New(JavaStringType, List(jvm.Arg.Pos(bytesExpr), jvm.Arg.Pos(utf8))).code

    // Use java.lang types for parsing static methods (Scala's Long/Int don't have parseLong/parseInt)
    val JavaLong = jvm.Type.Qualified(jvm.QIdent("java.lang.Long"))
    val JavaInt = jvm.Type.Qualified(jvm.QIdent("java.lang.Integer"))
    val JavaBoolean = jvm.Type.Qualified(jvm.QIdent("java.lang.Boolean"))

    headerType match {
      case typr.avro.HeaderType.String  => stringValue
      case typr.avro.HeaderType.UUID    => UUID.code.invoke("fromString", stringValue)
      case typr.avro.HeaderType.Instant =>
        // Kotlin: stringValue.toLong(), Java/Scala: Long.parseLong(stringValue)
        val epochMillis = lang.extension match {
          case "kt" => stringValue.invoke("toLong")
          case _    => JavaLong.code.invoke("parseLong", stringValue)
        }
        Instant.code.invoke("ofEpochMilli", epochMillis)
      case typr.avro.HeaderType.Long =>
        lang.extension match {
          case "kt" => stringValue.invoke("toLong")
          case _    => JavaLong.code.invoke("parseLong", stringValue)
        }
      case typr.avro.HeaderType.Int =>
        lang.extension match {
          case "kt" => stringValue.invoke("toInt")
          case _    => JavaInt.code.invoke("parseInt", stringValue)
        }
      case typr.avro.HeaderType.Boolean =>
        lang.extension match {
          case "kt" => stringValue.invoke("toBoolean")
          case _    => JavaBoolean.code.invoke("parseBoolean", stringValue)
        }
    }
  }

  private def generateOptionalParse(headerValue: jvm.Code, headerType: typr.avro.HeaderType): jvm.Code = {
    lang.extension match {
      case "java" =>
        val parseExpr = parseFromBytes(code"h.value()", headerType)
        jvm.Type.Qualified("java.util.Optional").code.invoke("ofNullable", headerValue).invoke("map", code"h -> $parseExpr")
      case "kt" =>
        val parseExpr = parseFromBytes(code"it.value()", headerType)
        code"$headerValue?.let { $parseExpr }"
      case "scala" =>
        val parseExpr = parseFromBytes(code"h.value()", headerType)
        jvm.Type.Qualified("scala.Option").code.invoke("apply", headerValue).invoke("map", code"h => $parseExpr")
      case _ =>
        throw new RuntimeException(s"Unsupported language: ${lang.extension}")
    }
  }
}
