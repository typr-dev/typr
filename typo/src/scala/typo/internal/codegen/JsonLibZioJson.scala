package typo
package internal
package codegen

final case class JsonLibZioJson(pkg: sc.QIdent, default: ComputedDefault, inlineImplicits: Boolean, implicitOrUsing: ImplicitOrUsing) extends JsonLib {
  private val JsonDecoder = sc.Type.Qualified("zio.json.JsonDecoder")
  private val JsonEncoder = sc.Type.Qualified("zio.json.JsonEncoder")
  private val Write = sc.Type.Qualified("zio.json.internal.Write")
  private val JsonError = sc.Type.Qualified("zio.json.JsonError")
  private val RetractReader = sc.Type.Qualified("zio.json.internal.RetractReader")
  private val Success = sc.Type.Qualified("scala.util.Success")
  private val Json = sc.Type.Qualified("zio.json.ast.Json")

  private val encoderName = sc.Ident("jsonEncoder")
  private val decoderName = sc.Ident("jsonDecoder")

  /** Resolve known implicits at generation-time instead of at compile-time */
  private def lookupDecoderFor(tpe: sc.Type): sc.Code = {
    def go(tpe: sc.Type): sc.Code =
      tpe match {
        case TypesScala.BigDecimal                                         => code"$JsonDecoder.scalaBigDecimal"
        case TypesScala.Boolean                                            => code"$JsonDecoder.boolean"
        case TypesScala.Byte                                               => code"$JsonDecoder.byte"
        case TypesScala.Double                                             => code"$JsonDecoder.double"
        case TypesScala.Float                                              => code"$JsonDecoder.float"
        case TypesJava.Instant                                             => code"$JsonDecoder.instant"
        case TypesScala.Int                                                => code"$JsonDecoder.int"
        case TypesJava.LocalDate                                           => code"$JsonDecoder.localDate"
        case TypesJava.LocalDateTime                                       => code"$JsonDecoder.localDateTime"
        case TypesJava.LocalTime                                           => code"$JsonDecoder.localTime"
        case TypesScala.Long                                               => code"$JsonDecoder.long"
        case TypesJava.OffsetDateTime                                      => code"$JsonDecoder.offsetDateTime"
        case TypesJava.OffsetTime                                          => code"$JsonDecoder.offsetTime"
        case TypesJava.String                                              => code"$JsonDecoder.string"
        case TypesJava.UUID                                                => code"$JsonDecoder.uuid"
        case sc.Type.ArrayOf(targ)                                         => code"$JsonDecoder.array[$targ](using ${go(targ)}, implicitly)"
        case sc.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$decoderName(${implicitOrUsing.callImplicitOrUsing}${go(targ)})"
        case TypesScala.Optional(targ)                                     => code"$JsonDecoder.option(${implicitOrUsing.callImplicitOrUsing}${go(targ)})"
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$decoderName"
        case other                                                         => code"${JsonDecoder.of(other)}"
      }

    if (inlineImplicits) go(sc.Type.base(tpe)) else JsonDecoder.of(tpe).code
  }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupEncoderFor(tpe: sc.Type): sc.Code = {
    def go(tpe: sc.Type): sc.Code =
      tpe match {
        case TypesScala.BigDecimal                                         => code"$JsonEncoder.scalaBigDecimal"
        case TypesScala.Boolean                                            => code"$JsonEncoder.boolean"
        case TypesScala.Byte                                               => code"$JsonEncoder.byte"
        case TypesScala.Double                                             => code"$JsonEncoder.double"
        case TypesScala.Float                                              => code"$JsonEncoder.float"
        case TypesJava.Instant                                             => code"$JsonEncoder.instant"
        case TypesScala.Int                                                => code"$JsonEncoder.int"
        case TypesJava.LocalDate                                           => code"$JsonEncoder.localDate"
        case TypesJava.LocalDateTime                                       => code"$JsonEncoder.localDateTime"
        case TypesJava.LocalTime                                           => code"$JsonEncoder.localTime"
        case TypesScala.Long                                               => code"$JsonEncoder.long"
        case TypesJava.OffsetDateTime                                      => code"$JsonEncoder.offsetDateTime"
        case TypesJava.OffsetTime                                          => code"$JsonEncoder.offsetTime"
        case TypesJava.String                                              => code"$JsonEncoder.string"
        case TypesJava.UUID                                                => code"$JsonEncoder.uuid"
        case sc.Type.ArrayOf(targ)                                         => code"$JsonEncoder.array[$targ](${implicitOrUsing.callImplicitOrUsing}${go(targ)}, implicitly)"
        case sc.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$encoderName(${implicitOrUsing.callImplicitOrUsing}${go(targ)})"
        case TypesScala.Optional(targ)                                     => code"$JsonEncoder.option(${implicitOrUsing.callImplicitOrUsing}${go(targ)})"
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$encoderName"
        case other                                                         => code"${JsonEncoder.of(other)}"
      }

    if (inlineImplicits) go(sc.Type.base(tpe)) else JsonEncoder.of(tpe).code
  }

  override def defaultedInstance(d: ComputedDefault): List[sc.Given] = {
    val T = sc.Type.Abstract(sc.Ident("T"))
    List(
      sc.Given(
        tparams = List(T),
        name = decoderName,
        implicitParams = List(sc.Param(sc.Ident("T"), JsonDecoder.of(T), None)),
        tpe = JsonDecoder.of(d.Defaulted.of(T)),
        body = code"""|new ${JsonDecoder.of(d.Defaulted.of(T))} {
                      |  override def unsafeDecode(trace: ${TypesScala.List.of(JsonError)}, in: $RetractReader): ${d.Defaulted.of(T)} =
                      |    ${TypesScala.Try}($JsonDecoder.string.unsafeDecode(trace, in)) match {
                      |      case $Success("defaulted") => UseDefault
                      |      case _ => Provided(T.unsafeDecode(trace, in))
                      |    }
                      |  }""".stripMargin,
        implicitOrUsing
      ),
      sc.Given(
        tparams = List(T),
        name = encoderName,
        implicitParams = List(sc.Param(sc.Ident("T"), JsonEncoder.of(T), None)),
        tpe = JsonEncoder.of(d.Defaulted.of(T)),
        body = code"""|new ${JsonEncoder.of(d.Defaulted.of(T))} {
                 |  override def unsafeEncode(a: ${d.Defaulted.of(T)}, indent: ${TypesScala.Option.of(TypesScala.Int)}, out: $Write): Unit =
                 |    a match {
                 |      case ${d.Provided}(value) =>
                 |        out.write("{")
                 |        out.write("\\"provided\\":")
                 |        ${sc.Ident("T")}.unsafeEncode(value, None, out)
                 |        out.write("}")
                 |      case ${d.UseDefault} => out.write("\\"defaulted\\"")
                 |    }
                 |}""".stripMargin,
        implicitOrUsing
      )
    )
  }

  override def stringEnumInstances(wrapperType: sc.Type, underlying: sc.Type, openEnum: Boolean): List[sc.Given] =
    List(
      sc.Given(
        tparams = Nil,
        name = decoderName,
        implicitParams = Nil,
        tpe = JsonDecoder.of(wrapperType),
        body =
          if (openEnum) code"""${lookupDecoderFor(underlying)}.map($wrapperType.apply)"""
          else code"""${lookupDecoderFor(underlying)}.mapOrFail($wrapperType.apply)""",
        implicitOrUsing
      ),
      sc.Given(
        tparams = Nil,
        name = encoderName,
        implicitParams = Nil,
        tpe = JsonEncoder.of(wrapperType),
        body = code"${lookupEncoderFor(underlying)}.contramap(_.value)",
        implicitOrUsing
      )
    )

  override def wrapperTypeInstances(wrapperType: sc.Type.Qualified, fieldName: sc.Ident, underlying: sc.Type): List[sc.Given] =
    List(
      sc.Given(
        tparams = Nil,
        name = encoderName,
        implicitParams = Nil,
        tpe = JsonEncoder.of(wrapperType),
        body = code"${lookupEncoderFor(underlying)}.contramap(_.$fieldName)",
        implicitOrUsing
      ),
      sc.Given(
        tparams = Nil,
        name = decoderName,
        implicitParams = Nil,
        tpe = JsonDecoder.of(wrapperType),
        body = code"${lookupDecoderFor(underlying)}.map($wrapperType.apply)",
        implicitOrUsing
      )
    )

  override def productInstances(tpe: sc.Type, fields: NonEmptyList[JsonLib.Field]): List[sc.Given] = {
    val decoder =
      sc.Given(
        tparams = Nil,
        name = decoderName,
        implicitParams = Nil,
        tpe = JsonDecoder.of(tpe),
        body = {
          val vals =
            fields.map(f =>
              f.tpe match {
                case TypesScala.Optional(targ) =>
                  val either = TypesScala.Either.of(TypesJava.String, TypesScala.Option.of(sc.Type.base(targ)))
                  code"""val ${f.scalaName} = jsonObj.get(${f.jsonName}).fold[$either](${TypesScala.Right}(${TypesScala.None}))(_.as(${lookupDecoderFor(f.tpe)}))"""
                case _ =>
                  code"""val ${f.scalaName} = jsonObj.get(${f.jsonName}).toRight("Missing field '${f.jsonName.str}'").flatMap(_.as(${lookupDecoderFor(f.tpe)}))"""
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
        },
        implicitOrUsing
      )

    val encoder =
      sc.Given(
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
        },
        implicitOrUsing
      )

    List(decoder, encoder)
  }

  override def missingInstances: List[sc.ClassMember] = List.empty
}
