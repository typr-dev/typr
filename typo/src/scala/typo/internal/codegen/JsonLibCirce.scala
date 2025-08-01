package typo
package internal
package codegen

case class JsonLibCirce(pkg: sc.QIdent, default: ComputedDefault, inlineImplicits: Boolean, implicitOrUsing: ImplicitOrUsing) extends JsonLib {
  val Decoder = sc.Type.Qualified("io.circe.Decoder")
  val Encoder = sc.Type.Qualified("io.circe.Encoder")
  val HCursor = sc.Type.Qualified("io.circe.HCursor")
  val Json = sc.Type.Qualified("io.circe.Json")
  val DecodingFailure = sc.Type.Qualified("io.circe.DecodingFailure")

  val encoderName = sc.Ident("encoder")
  val decoderName = sc.Ident("decoder")

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupDecoderFor(tpe: sc.Type): sc.Code = {
    def go(tpe: sc.Type): sc.Code =
      tpe match {
        case TypesScala.BigDecimal                                         => code"$Decoder.decodeBigDecimal"
        case TypesScala.Boolean                                            => code"$Decoder.decodeBoolean"
        case TypesScala.Byte                                               => code"$Decoder.decodeByte"
        case TypesScala.Double                                             => code"$Decoder.decodeDouble"
        case TypesScala.Float                                              => code"$Decoder.decodeFloat"
        case TypesJava.Instant                                             => code"$Decoder.decodeInstant"
        case TypesScala.Int                                                => code"$Decoder.decodeInt"
        case TypesJava.LocalDate                                           => code"$Decoder.decodeLocalDate"
        case TypesJava.LocalDateTime                                       => code"$Decoder.decodeLocalDateTime"
        case TypesJava.LocalTime                                           => code"$Decoder.decodeLocalTime"
        case TypesScala.Long                                               => code"$Decoder.decodeLong"
        case TypesJava.OffsetDateTime                                      => code"$Decoder.decodeOffsetDateTime"
        case TypesJava.OffsetTime                                          => code"$Decoder.decodeOffsetTime"
        case TypesJava.String                                              => code"$Decoder.decodeString"
        case TypesJava.UUID                                                => code"$Decoder.decodeUUID"
        case sc.Type.ArrayOf(targ)                                         => code"$Decoder.decodeArray[$targ](${implicitOrUsing.callImplicitOrUsing}${go(targ)}, implicitly)"
        case sc.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$decoderName(${implicitOrUsing.callImplicitOrUsing}${go(targ)})"
        case TypesScala.Optional(targ)                                     => code"$Decoder.decodeOption(${implicitOrUsing.callImplicitOrUsing}${go(targ)})"
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$decoderName"
        case other                                                         => code"${Decoder.of(other)}"
      }

    if (inlineImplicits) go(sc.Type.base(tpe)) else Decoder.of(tpe).code
  }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupEncoderFor(tpe: sc.Type): sc.Code = {
    def go(tpe: sc.Type): sc.Code =
      tpe match {
        case TypesScala.BigDecimal                                         => code"$Encoder.encodeBigDecimal"
        case TypesScala.Boolean                                            => code"$Encoder.encodeBoolean"
        case TypesScala.Byte                                               => code"$Encoder.encodeByte"
        case TypesScala.Double                                             => code"$Encoder.encodeDouble"
        case TypesScala.Float                                              => code"$Encoder.encodeFloat"
        case TypesJava.Instant                                             => code"$Encoder.encodeInstant"
        case TypesScala.Int                                                => code"$Encoder.encodeInt"
        case TypesJava.LocalDate                                           => code"$Encoder.encodeLocalDate"
        case TypesJava.LocalDateTime                                       => code"$Encoder.encodeLocalDateTime"
        case TypesJava.LocalTime                                           => code"$Encoder.encodeLocalTime"
        case TypesScala.Long                                               => code"$Encoder.encodeLong"
        case TypesJava.OffsetDateTime                                      => code"$Encoder.encodeOffsetDateTime"
        case TypesJava.OffsetTime                                          => code"$Encoder.encodeOffsetTime"
        case TypesJava.String                                              => code"$Encoder.encodeString"
        case TypesJava.UUID                                                => code"$Encoder.encodeUUID"
        case sc.Type.ArrayOf(targ)                                         => code"$Encoder.encodeIterable[$targ, ${TypesScala.Array}](${implicitOrUsing.callImplicitOrUsing}${go(targ)}, implicitly)"
        case sc.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$encoderName(${implicitOrUsing.callImplicitOrUsing}${go(targ)})"
        case TypesScala.Optional(targ)                                     => code"$Encoder.encodeOption(${implicitOrUsing.callImplicitOrUsing}${go(targ)})"
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$encoderName"
        case other                                                         => code"${Encoder.of(other)}"
      }

    if (inlineImplicits) go(sc.Type.base(tpe)) else Encoder.of(tpe).code
  }

  def wrapperTypeInstances(wrapperType: sc.Type.Qualified, fieldName: sc.Ident, underlying: sc.Type): List[sc.Given] =
    List(
      sc.Given(
        tparams = Nil,
        name = encoderName,
        implicitParams = Nil,
        tpe = Encoder.of(wrapperType),
        body = code"${lookupEncoderFor(underlying)}.contramap(_.$fieldName)",
        implicitOrUsing
      ),
      sc.Given(
        tparams = Nil,
        name = decoderName,
        implicitParams = Nil,
        tpe = Decoder.of(wrapperType),
        body = code"${lookupDecoderFor(underlying)}.map($wrapperType.apply)",
        implicitOrUsing
      )
    )

  override def defaultedInstance(d: ComputedDefault): List[sc.Given] = {
    val T = sc.Type.Abstract(sc.Ident("T"))
    List(
      sc.Given(
        tparams = List(T),
        name = decoderName,
        implicitParams = List(sc.Param(sc.Ident("T"), Decoder.of(T), None)),
        tpe = Decoder.of(d.Defaulted.of(T)),
        body = code"""|c => c.as[${TypesJava.String}].flatMap {
                 |    case "defaulted" => ${TypesScala.Right}(${d.UseDefault})
                 |    case _           => c.downField("provided").as[$T].map(${d.Provided}.apply)
                 |  }""".stripMargin,
        implicitOrUsing
      ),
      sc.Given(
        tparams = List(T),
        name = encoderName,
        implicitParams = List(sc.Param(sc.Ident("T"), Encoder.of(T), None)),
        tpe = Encoder.of(d.Defaulted.of(T)),
        body = code"""|$Encoder.instance {
                 |  case ${d.Provided}(value) => $Json.obj("provided" -> ${Encoder.of(T)}.apply(value))
                 |  case ${d.UseDefault}      => $Json.fromString("defaulted")
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
        tpe = Decoder.of(wrapperType),
        body =
          if (openEnum: Boolean) code"""${lookupDecoderFor(underlying)}.map($wrapperType.apply)"""
          else code"""${lookupDecoderFor(underlying)}.emap($wrapperType.apply)""",
        implicitOrUsing
      ),
      sc.Given(
        tparams = Nil,
        name = encoderName,
        implicitParams = Nil,
        tpe = Encoder.of(wrapperType),
        body = code"${lookupEncoderFor(underlying)}.contramap(_.value)",
        implicitOrUsing
      )
    )

  def productInstances(tpe: sc.Type, fields: NonEmptyList[JsonLib.Field]): List[sc.Given] = {
    val decoder =
      fields.length match {
        case n if n < 23 =>
          val fieldNames = fields.map(_.jsonName.code).mkCode(", ")
          val instances = fields.map(f => lookupDecoderFor(f.tpe)).mkCode(", ")

          sc.Given(
            tparams = Nil,
            name = decoderName,
            implicitParams = Nil,
            tpe = Decoder.of(tpe),
            body = code"""$Decoder.forProduct$n[$tpe, ${fields.map(_.tpe.code).mkCode(", ")}]($fieldNames)($tpe.apply)($instances)""",
            implicitOrUsing
          )
        case _ =>
          val cases = fields.map { f =>
            code"""${f.scalaName} = orThrow(c.get(${f.jsonName})(${lookupDecoderFor(f.tpe)}))"""
          }
          sc.Given(
            tparams = Nil,
            name = decoderName,
            implicitParams = Nil,
            tpe = Decoder.of(tpe),
            body = code"""|$Decoder.instanceTry[$tpe]((c: $HCursor) =>
                          |  ${TypesScala.Try} {
                          |    def orThrow[R](either: ${TypesScala.Either}[$DecodingFailure, R]): R = either match {
                          |      case Left(err) => throw err
                          |      case Right(r)  => r
                          |    }
                          |    $tpe(
                          |      ${cases.mkCode(",\n")}
                          |    )
                          |  }
                          |)""".stripMargin,
            implicitOrUsing
          )

      }

    val encoder = {
      fields.length match {
        case n if n < 23 =>
          val fieldNames = fields.map(_.jsonName.code).mkCode(", ")
          val f = code"x => (${fields.map(f => code"x.${f.scalaName}").mkCode(", ")})"
          val instances = fields.map(f => lookupEncoderFor(f.tpe)).mkCode(", ")
          sc.Given(
            tparams = Nil,
            name = encoderName,
            implicitParams = Nil,
            tpe = Encoder.of(tpe),
            body = code"""$Encoder.forProduct$n[$tpe, ${fields.map(_.tpe.code).mkCode(", ")}]($fieldNames)($f)($instances)""",
            implicitOrUsing
          )

        case _ =>
          val cases = fields.map { f =>
            code"""${f.jsonName} -> ${lookupEncoderFor(f.tpe)}.apply(row.${f.scalaName})"""
          }
          sc.Given(
            tparams = Nil,
            name = encoderName,
            implicitParams = Nil,
            tpe = Encoder.of(tpe),
            body = code"""|${Encoder.of(tpe)}(row =>
                          |  $Json.obj(
                          |    ${cases.mkCode(",\n")}
                          |  )
                          |)""".stripMargin,
            implicitOrUsing
          )

      }
    }

    List(decoder, encoder)
  }

  override def missingInstances: List[sc.ClassMember] = Nil
}
