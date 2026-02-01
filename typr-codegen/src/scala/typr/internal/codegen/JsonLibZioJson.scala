package typr
package internal
package codegen

final case class JsonLibZioJson(pkg: jvm.QIdent, default: ComputedDefault, inlineImplicits: Boolean, lang: LangScala) extends JsonLib {
  val dialect = lang.dialect
  private val JsonDecoder = jvm.Type.Qualified("zio.json.JsonDecoder")
  private val JsonEncoder = jvm.Type.Qualified("zio.json.JsonEncoder")
  private val Write = jvm.Type.Qualified("zio.json.internal.Write")
  private val JsonError = jvm.Type.Qualified("zio.json.JsonError")
  private val RetractReader = jvm.Type.Qualified("zio.json.internal.RetractReader")
  private val Success = jvm.Type.Qualified("scala.util.Success")
  private val Json = jvm.Type.Qualified("zio.json.ast.Json")

  private val encoderName = jvm.Ident("jsonEncoder")
  private val decoderName = jvm.Ident("jsonDecoder")

  /** Resolve known implicits at generation-time instead of at compile-time */
  private def lookupDecoderFor(tpe: jvm.Type): jvm.Code = {
    def go(tpe: jvm.Type): jvm.Code =
      tpe match {
        case TypesScala.BigDecimal                                          => code"$JsonDecoder.scalaBigDecimal"
        case TypesScala.Boolean                                             => code"$JsonDecoder.boolean"
        case TypesScala.Byte                                                => code"$JsonDecoder.byte"
        case TypesScala.Double                                              => code"$JsonDecoder.double"
        case TypesScala.Float                                               => code"$JsonDecoder.float"
        case TypesJava.Instant                                              => code"$JsonDecoder.instant"
        case TypesScala.Int                                                 => code"$JsonDecoder.int"
        case TypesJava.LocalDate                                            => code"$JsonDecoder.localDate"
        case TypesJava.LocalDateTime                                        => code"$JsonDecoder.localDateTime"
        case TypesJava.LocalTime                                            => code"$JsonDecoder.localTime"
        case TypesScala.Long                                                => code"$JsonDecoder.long"
        case TypesJava.OffsetDateTime                                       => code"$JsonDecoder.offsetDateTime"
        case TypesJava.OffsetTime                                           => code"$JsonDecoder.offsetTime"
        case TypesJava.String                                               => code"$JsonDecoder.string"
        case TypesJava.UUID                                                 => code"$JsonDecoder.uuid"
        case jvm.Type.ArrayOf(targ)                                         => code"$JsonDecoder.array[$targ](${dialect.usingCall}${go(targ)}, implicitly)"
        case jvm.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$decoderName(${dialect.usingCall}${go(targ)})"
        case lang.Optional(targ)                                            => code"$JsonDecoder.option(${dialect.usingCall}${go(targ)})"
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$decoderName"
        case other                                                          => code"${JsonDecoder.of(other)}"
      }

    if (inlineImplicits) go(jvm.Type.base(tpe)) else JsonDecoder.of(tpe).code
  }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupEncoderFor(tpe: jvm.Type): jvm.Code = {
    def go(tpe: jvm.Type): jvm.Code =
      tpe match {
        case TypesScala.BigDecimal                                          => code"$JsonEncoder.scalaBigDecimal"
        case TypesScala.Boolean                                             => code"$JsonEncoder.boolean"
        case TypesScala.Byte                                                => code"$JsonEncoder.byte"
        case TypesScala.Double                                              => code"$JsonEncoder.double"
        case TypesScala.Float                                               => code"$JsonEncoder.float"
        case TypesJava.Instant                                              => code"$JsonEncoder.instant"
        case TypesScala.Int                                                 => code"$JsonEncoder.int"
        case TypesJava.LocalDate                                            => code"$JsonEncoder.localDate"
        case TypesJava.LocalDateTime                                        => code"$JsonEncoder.localDateTime"
        case TypesJava.LocalTime                                            => code"$JsonEncoder.localTime"
        case TypesScala.Long                                                => code"$JsonEncoder.long"
        case TypesJava.OffsetDateTime                                       => code"$JsonEncoder.offsetDateTime"
        case TypesJava.OffsetTime                                           => code"$JsonEncoder.offsetTime"
        case TypesJava.String                                               => code"$JsonEncoder.string"
        case TypesJava.UUID                                                 => code"$JsonEncoder.uuid"
        case jvm.Type.ArrayOf(targ)                                         => code"$JsonEncoder.array[$targ](${dialect.usingCall}${go(targ)}, implicitly)"
        case jvm.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$encoderName(${dialect.usingCall}${go(targ)})"
        case lang.Optional(targ)                                            => code"$JsonEncoder.option(${dialect.usingCall}${go(targ)})"
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$encoderName"
        case other                                                          => code"${JsonEncoder.of(other)}"
      }

    if (inlineImplicits) go(jvm.Type.base(tpe)) else JsonEncoder.of(tpe).code
  }

  override def defaultedInstance: JsonLib.Instances = JsonLib.Instances.fromGivens {
    val T = jvm.Type.Abstract(jvm.Ident("T"))
    List(
      jvm.Given(
        tparams = List(T),
        name = decoderName,
        implicitParams = List(jvm.Param(jvm.Ident("T"), JsonDecoder.of(T))),
        tpe = JsonDecoder.of(default.Defaulted.of(T)),
        body = code"""|new ${JsonDecoder.of(default.Defaulted.of(T))} {
                      |  override def unsafeDecode(trace: ${TypesScala.List.of(JsonError)}, in: $RetractReader): ${default.Defaulted.of(T)} =
                      |    ${TypesScala.Try}($JsonDecoder.string.unsafeDecode(trace, in)) match {
                      |      case $Success("defaulted") => ${default.UseDefault}()
                      |      case _ => ${default.Provided}(T.unsafeDecode(trace, in))
                      |    }
                      |  }""".stripMargin
      ),
      jvm.Given(
        tparams = List(T),
        name = encoderName,
        implicitParams = List(jvm.Param(jvm.Ident("T"), JsonEncoder.of(T))),
        tpe = JsonEncoder.of(default.Defaulted.of(T)),
        body = code"""|new ${JsonEncoder.of(default.Defaulted.of(T))} {
                 |  override def unsafeEncode(a: ${default.Defaulted.of(T)}, indent: ${TypesScala.Option.of(TypesScala.Int)}, out: $Write): Unit =
                 |    a match {
                 |      case ${default.Provided}(value) =>
                 |        out.write("{")
                 |        out.write("\\"provided\\":")
                 |        ${jvm.Ident("T")}.unsafeEncode(value, None, out)
                 |        out.write("}")
                 |      case ${default.UseDefault}() => out.write("\\"defaulted\\"")
                 |    }
                 |}""".stripMargin
      )
    )
  }

  override def stringEnumInstances(wrapperType: jvm.Type, underlying: jvm.Type, openEnum: Boolean): JsonLib.Instances =
    JsonLib.Instances.fromGivens(
      List(
        jvm.Given(
          tparams = Nil,
          name = decoderName,
          implicitParams = Nil,
          tpe = JsonDecoder.of(wrapperType),
          body =
            if (openEnum) code"""${lookupDecoderFor(underlying)}.map($wrapperType.apply)"""
            else code"""${lookupDecoderFor(underlying)}.mapOrFail($wrapperType.apply)"""
        ),
        jvm.Given(tparams = Nil, name = encoderName, implicitParams = Nil, tpe = JsonEncoder.of(wrapperType), body = code"${lookupEncoderFor(underlying)}.contramap(_.value)")
      )
    )

  override def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, fieldName: jvm.Ident, underlying: jvm.Type): JsonLib.Instances =
    JsonLib.Instances.fromGivens(
      List(
        jvm.Given(tparams = Nil, name = encoderName, implicitParams = Nil, tpe = JsonEncoder.of(wrapperType), body = code"${lookupEncoderFor(underlying)}.contramap(_.$fieldName)"),
        jvm.Given(tparams = Nil, name = decoderName, implicitParams = Nil, tpe = JsonDecoder.of(wrapperType), body = code"${lookupDecoderFor(underlying)}.map($wrapperType.apply)")
      )
    )

  override def productInstances(tpe: jvm.Type, fields: NonEmptyList[JsonLib.Field]): JsonLib.Instances = {
    val decoder =
      jvm.Given(
        tparams = Nil,
        name = decoderName,
        implicitParams = Nil,
        tpe = JsonDecoder.of(tpe),
        body = {
          val vals =
            fields.map(f =>
              f.tpe match {
                case lang.Optional(targ) =>
                  val either = TypesScala.Either.of(TypesJava.String, TypesScala.Option.of(jvm.Type.base(targ)))
                  code"""val ${f.scalaName} = jsonObj.get(${f.jsonName}).fold[$either](${TypesScala.Right}(${TypesScala.None}))(_.as(${dialect.usingCall}${lookupDecoderFor(f.tpe)}))"""
                case _ =>
                  code"""val ${f.scalaName} = jsonObj.get(${f.jsonName}).toRight("Missing field '${f.jsonName.str}'").flatMap(_.as(${dialect.usingCall}${lookupDecoderFor(f.tpe)}))"""
              }
            )

          // specify `Any` explicitly to save the compiler from LUBbing
          val list = TypesScala.List.of(TypesScala.Either.of(TypesJava.String, TypesScala.Any))
          code"""|$JsonDecoder[$Json.Obj].mapOrFail { jsonObj =>
                 |  ${vals.mkCode("\n")}
                 |  if (${fields.map(f => code"${f.scalaName}.isRight").mkCode(" && ")})
                 |    ${TypesScala.Right}($tpe(${fields.map(v => code"${v.scalaName} = ${v.scalaName}.toOption.get").mkCode(", ")}))
                 |  else ${TypesScala.Left}($list(${fields.map(f => code"${f.scalaName}").mkCode(", ")}).flatMap(_.left.toOption).mkString(", "))
                 |}""".stripMargin
        }
      )

    val encoder =
      jvm.Given(
        tparams = Nil,
        name = encoderName,
        implicitParams = Nil,
        tpe = JsonEncoder.of(tpe),
        body = {
          val params =
            fields.map(f => code"""|out.write(\"\"\"${f.jsonName}:\"\"\")
                     |${lookupEncoderFor(f.tpe)}.unsafeEncode(a.${f.scalaName}, indent, out)""".stripMargin)

          code"""|new $JsonEncoder[$tpe] {
                 |  override def unsafeEncode(a: $tpe, indent: Option[Int], out: $Write): Unit = {
                 |    out.write("{")
                 |    ${params.mkCode(code"""\nout.write(",")\n""")}
                 |    out.write("}")
                 |  }
                 |}""".stripMargin
        }
      )

    JsonLib.Instances.fromGivens(List(decoder, encoder))
  }

  override def missingInstances: List[jvm.ClassMember] = List.empty
}
