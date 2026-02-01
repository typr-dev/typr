package typr
package internal
package codegen

import typr.jvm.Code.TypeOps

case class JsonLibJackson(pkg: jvm.QIdent, default: ComputedDefault, lang: Lang) extends JsonLib {
  val topType: jvm.Type = lang.topType

  // Jackson annotation types
  val JsonProperty = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonProperty")
  val JsonValue = jvm.Type.Qualified("com.fasterxml.jackson.annotation.JsonValue")
  val JsonSerialize = jvm.Type.Qualified("com.fasterxml.jackson.databind.annotation.JsonSerialize")
  val JsonDeserialize = jvm.Type.Qualified("com.fasterxml.jackson.databind.annotation.JsonDeserialize")

  // Jackson types for serializers
  val JsonSerializer = jvm.Type.Qualified("com.fasterxml.jackson.databind.JsonSerializer")
  val JsonDeserializerBase = jvm.Type.Qualified("com.fasterxml.jackson.databind.JsonDeserializer")
  val JsonGenerator = jvm.Type.Qualified("com.fasterxml.jackson.core.JsonGenerator")
  val JsonParser = jvm.Type.Qualified("com.fasterxml.jackson.core.JsonParser")
  val JsonToken = jvm.Type.Qualified("com.fasterxml.jackson.core.JsonToken")
  val SerializerProvider = jvm.Type.Qualified("com.fasterxml.jackson.databind.SerializerProvider")
  val DeserializationContext = jvm.Type.Qualified("com.fasterxml.jackson.databind.DeserializationContext")
  val ContextualDeserializer = jvm.Type.Qualified("com.fasterxml.jackson.databind.deser.ContextualDeserializer")
  val BeanProperty = jvm.Type.Qualified("com.fasterxml.jackson.databind.BeanProperty")
  val JavaType = jvm.Type.Qualified("com.fasterxml.jackson.databind.JavaType")
  val IOException = jvm.Type.Qualified("java.io.IOException")

  override def defaultedInstance: JsonLib.Instances = {
    val serializerType = jvm.Type.Qualified(pkg / default.Defaulted.name.appended("Serializer"))
    val deserializerType = jvm.Type.Qualified(pkg / default.Defaulted.name.appended("Deserializer"))

    val serializeAnn = jvm.Annotation(JsonSerialize, List(jvm.Annotation.Arg.Named(jvm.Ident("using"), lang.annotationClassRef(serializerType))))
    val deserializeAnn = jvm.Annotation(JsonDeserialize, List(jvm.Annotation.Arg.Named(jvm.Ident("using"), lang.annotationClassRef(deserializerType))))

    val serializerFile = generateSerializer(serializerType)
    val deserializerFile = generateDeserializer(deserializerType)

    JsonLib.Instances(Nil, List(serializeAnn, deserializeAnn), Map.empty, List(serializerFile, deserializerFile))
  }

  private def generateSerializer(serializerType: jvm.Type.Qualified): jvm.File = {
    // Create qualified nested type references for UseDefault and Provided
    val UseDefault = jvm.Type.Qualified(default.Defaulted.value / default.UseDefault)
    val Provided = jvm.Type.Qualified(default.Defaulted.value / default.Provided)

    val value = jvm.Ident("value")
    val gen = jvm.Ident("gen")
    val serializers = jvm.Ident("serializers")
    val p = jvm.Ident("p")
    val u = jvm.Ident("u")

    val typeSwitch = jvm.TypeSwitch(
      value = value.code,
      cases = List(
        jvm.TypeSwitch.Case(UseDefault.of(jvm.Type.Wildcard), u, code"$gen.writeString(${jvm.StrLit("defaulted")})"),
        jvm.TypeSwitch.Case(
          Provided.of(jvm.Type.Wildcard),
          p,
          code"""|{
                 |  $gen.writeStartObject();
                 |  $gen.writeFieldName(${jvm.StrLit("provided")});
                 |  $serializers.defaultSerializeValue(${lang.propertyGetterAccess(p.code, jvm.Ident("value"))}, $gen);
                 |  $gen.writeEndObject();
                 |}""".stripMargin
        )
      ),
      nullCase = Some(code"$gen.writeNull()"),
      // In Kotlin, use javaClass.name instead of getClass().getName()
      defaultCase = Some {
        val className = lang match {
          case _: LangKotlin => code"$value.javaClass.name"
          case _             => code"$value.getClass().getName()"
        }
        code"throw ${IOException.construct(code"${jvm.StrLit("Unknown Defaulted subtype: ")} + $className")}"
      }
    )

    val serializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("serialize"),
      params = List(
        jvm.Param(value, default.Defaulted.of(jvm.Type.Wildcard)),
        jvm.Param(gen, JsonGenerator),
        jvm.Param(serializers, SerializerProvider)
      ),
      implicitParams = Nil,
      tpe = jvm.Type.Void,
      throws = List(IOException),
      body = jvm.Body.Expr(typeSwitch.code),
      isOverride = true,
      isDefault = false
    )

    val cls = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List("Jackson serializer for Defaulted types")),
      classType = jvm.ClassType.Class,
      name = serializerType,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = Some(JsonSerializer.of(default.Defaulted.of(jvm.Type.Wildcard))),
      implements = Nil,
      members = List(serializeMethod),
      staticMembers = Nil
    )

    jvm.File(serializerType, cls.code, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generateDeserializer(deserializerType: jvm.Type.Qualified): jvm.File = {
    // Create qualified nested type references for UseDefault and Provided
    val UseDefault = jvm.Type.Qualified(default.Defaulted.value / default.UseDefault)
    val Provided = jvm.Type.Qualified(default.Defaulted.value / default.Provided)

    val defaultedClassType = jvm.Type.Qualified("java.lang.Class").of(jvm.Type.Wildcard)

    val valueType = jvm.Ident("valueType")
    val defaultedClass = jvm.Ident("defaultedClass")
    val ctxt = jvm.Ident("ctxt")
    val property = jvm.Ident("property")
    val p = jvm.Ident("p")
    val tpe = jvm.Ident("type")
    val text = jvm.Ident("text")
    val valueIdent = jvm.Ident("value")

    val contextType = jvm.Ident("contextType")
    val createContextualMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("createContextual"),
      params = List(
        jvm.Param(ctxt, DeserializationContext),
        jvm.Param(property, BeanProperty)
      ),
      implicitParams = Nil,
      tpe = JsonDeserializerBase.of(jvm.Type.Wildcard),
      throws = Nil,
      body = jvm.Body.Stmts(
        List(
          jvm.LocalVar(contextType, Some(JavaType), code"$ctxt.getContextualType()"),
          jvm.LocalVar(
            tpe,
            Some(JavaType),
            jvm
              .IfExpr(
                code"$contextType == null && $property != null",
                code"$property.getType()",
                contextType.code
              )
              .code
          ),
          code"""|if ($tpe != null && $tpe.containedTypeCount() > 0) {
               |  return ${deserializerType.construct(code"$tpe.containedType(0)", code"$tpe.getRawClass()")};
               |}""".stripMargin,
          jvm.Throw(TypesJava.RuntimeException.construct(jvm.StrLit("unexpected")))
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val textLocalVar = jvm.LocalVar(text, Some(lang.String), code"$p.getText()")
    val valueLocalVar = jvm.LocalVar(valueIdent, Some(topType), code"$ctxt.readValue($p, $valueType)")

    val deserializeMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("deserialize"),
      params = List(
        jvm.Param(p, JsonParser),
        jvm.Param(ctxt, DeserializationContext)
      ),
      implicitParams = Nil,
      tpe = default.Defaulted.of(jvm.Type.Wildcard),
      throws = List(IOException),
      body = jvm.Body.Stmts(
        List(
          code"""|if ($p.currentToken() == $JsonToken.VALUE_STRING) {
               |  $textLocalVar;
               |  if (${jvm.StrLit("defaulted")}.equals($text)) {
               |    return ${UseDefault.of(topType).construct()};
               |  }
               |  throw ${IOException.construct(code"${jvm.StrLit("Expected 'defaulted' but got: ")} + $text")};
               |}
               |if ($p.currentToken() == $JsonToken.START_OBJECT) {
               |  $p.nextToken();
               |  if ($p.currentToken() == $JsonToken.FIELD_NAME && ${jvm.StrLit("provided")}.equals($p.currentName())) {
               |    $p.nextToken();
               |    $valueLocalVar;
               |    $p.nextToken();
               |    return ${Provided.of(topType).construct(valueIdent.code)};
               |  }
               |
               |}""".stripMargin,
          jvm.Throw(IOException.construct(code"${jvm.StrLit("Expected 'provided' field but got: ")} + $p.currentName()"))
        )
      ),
      isOverride = true,
      isDefault = false
    )

    val cls = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(List("Jackson deserializer for Defaulted types")),
      classType = jvm.ClassType.Class,
      name = deserializerType,
      tparams = Nil,
      params = List(
        jvm.Param(valueType, JavaType),
        jvm.Param(defaultedClass, defaultedClassType)
      ),
      implicitParams = Nil,
      `extends` = Some(JsonDeserializerBase.of(default.Defaulted.of(jvm.Type.Wildcard))),
      implements = List(ContextualDeserializer),
      members = List(createContextualMethod, deserializeMethod),
      staticMembers = Nil
    )

    jvm.File(deserializerType, cls, secondaryTypes = Nil, scope = Scope.Main)
  }

  override def stringEnumInstances(wrapperType: jvm.Type, underlying: jvm.Type, openEnum: Boolean): JsonLib.Instances = JsonLib.Instances.Empty
  override def missingInstances: List[jvm.ClassMember] = Nil

  override def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, fieldName: jvm.Ident, underlying: jvm.Type): JsonLib.Instances = {
    // For Java records, Jackson uses the canonical constructor automatically - no @JsonCreator needed
    // @JsonValue on the field marks how to serialize
    val valueAnn = jvm.Annotation(JsonValue, Nil)
    JsonLib.Instances(Nil, Nil, Map(fieldName -> List(valueAnn)), Nil)
  }

  override def productInstances(tpe: jvm.Type, fields: NonEmptyList[JsonLib.Field]): JsonLib.Instances = {
    val fieldAnns = fields.toList.flatMap { field =>
      val jsonName = field.jsonName.str
      val scalaName = field.scalaName.value
      if (jsonName != scalaName) {
        val ann = jvm.Annotation(JsonProperty, List(jvm.Annotation.Arg.Positional(jvm.StrLit(jsonName).code)))
        Some(field.scalaName -> List(ann))
      } else None
    }.toMap
    JsonLib.Instances(Nil, Nil, fieldAnns, Nil)
  }
}
