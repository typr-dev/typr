package typr
package internal
package codegen

case class JsonLibCirce(pkg: jvm.QIdent, default: ComputedDefault, inlineImplicits: Boolean, lang: LangScala) extends JsonLib {
  val dialect = lang.dialect
  val Decoder = jvm.Type.Qualified("io.circe.Decoder")
  val Encoder = jvm.Type.Qualified("io.circe.Encoder")
  val HCursor = jvm.Type.Qualified("io.circe.HCursor")
  val Json = jvm.Type.Qualified("io.circe.Json")
  val DecodingFailure = jvm.Type.Qualified("io.circe.DecodingFailure")

  val encoderName = jvm.Ident("encoder")
  val decoderName = jvm.Ident("decoder")

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupDecoderFor(tpe: jvm.Type): jvm.Code = {
    def go(tpe: jvm.Type): jvm.Code =
      tpe match {
        case TypesScala.BigDecimal                                          => code"$Decoder.decodeBigDecimal"
        case TypesScala.Boolean                                             => code"$Decoder.decodeBoolean"
        case TypesScala.Byte                                                => code"$Decoder.decodeByte"
        case TypesScala.Double                                              => code"$Decoder.decodeDouble"
        case TypesScala.Float                                               => code"$Decoder.decodeFloat"
        case TypesJava.Instant                                              => code"$Decoder.decodeInstant"
        case TypesScala.Int                                                 => code"$Decoder.decodeInt"
        case TypesJava.LocalDate                                            => code"$Decoder.decodeLocalDate"
        case TypesJava.LocalDateTime                                        => code"$Decoder.decodeLocalDateTime"
        case TypesJava.LocalTime                                            => code"$Decoder.decodeLocalTime"
        case TypesScala.Long                                                => code"$Decoder.decodeLong"
        case TypesJava.OffsetDateTime                                       => code"$Decoder.decodeOffsetDateTime"
        case TypesJava.OffsetTime                                           => code"$Decoder.decodeOffsetTime"
        case TypesJava.String                                               => code"$Decoder.decodeString"
        case TypesJava.UUID                                                 => code"$Decoder.decodeUUID"
        case jvm.Type.ArrayOf(targ)                                         => code"$Decoder.decodeArray[$targ](${dialect.usingCall}${go(targ)}, implicitly)"
        case jvm.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$decoderName(${dialect.usingCall}${go(targ)})"
        case lang.Optional(targ)                                            => code"$Decoder.decodeOption(${dialect.usingCall}${go(targ)})"
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$decoderName"
        case other                                                          => code"${Decoder.of(other)}"
      }

    if (inlineImplicits) go(jvm.Type.base(tpe)) else Decoder.of(tpe).code
  }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupEncoderFor(tpe: jvm.Type): jvm.Code = {
    def go(tpe: jvm.Type): jvm.Code =
      tpe match {
        case TypesScala.BigDecimal                                          => code"$Encoder.encodeBigDecimal"
        case TypesScala.Boolean                                             => code"$Encoder.encodeBoolean"
        case TypesScala.Byte                                                => code"$Encoder.encodeByte"
        case TypesScala.Double                                              => code"$Encoder.encodeDouble"
        case TypesScala.Float                                               => code"$Encoder.encodeFloat"
        case TypesJava.Instant                                              => code"$Encoder.encodeInstant"
        case TypesScala.Int                                                 => code"$Encoder.encodeInt"
        case TypesJava.LocalDate                                            => code"$Encoder.encodeLocalDate"
        case TypesJava.LocalDateTime                                        => code"$Encoder.encodeLocalDateTime"
        case TypesJava.LocalTime                                            => code"$Encoder.encodeLocalTime"
        case TypesScala.Long                                                => code"$Encoder.encodeLong"
        case TypesJava.OffsetDateTime                                       => code"$Encoder.encodeOffsetDateTime"
        case TypesJava.OffsetTime                                           => code"$Encoder.encodeOffsetTime"
        case TypesJava.String                                               => code"$Encoder.encodeString"
        case TypesJava.UUID                                                 => code"$Encoder.encodeUUID"
        case jvm.Type.ArrayOf(targ)                                         => code"$Encoder.encodeIterable[$targ, ${TypesScala.Array}](${dialect.usingCall}${go(targ)}, implicitly)"
        case jvm.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$encoderName(${dialect.usingCall}${go(targ)})"
        case lang.Optional(targ)                                            => code"$Encoder.encodeOption(${dialect.usingCall}${go(targ)})"
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$encoderName"
        case other                                                          => code"${Encoder.of(other)}"
      }

    if (inlineImplicits) go(jvm.Type.base(tpe)) else Encoder.of(tpe).code
  }

  def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, fieldName: jvm.Ident, underlying: jvm.Type): JsonLib.Instances =
    JsonLib.Instances.fromGivens(
      List(
        jvm.Given(tparams = Nil, name = encoderName, implicitParams = Nil, tpe = Encoder.of(wrapperType), body = code"${lookupEncoderFor(underlying)}.contramap(_.$fieldName)"),
        jvm.Given(tparams = Nil, name = decoderName, implicitParams = Nil, tpe = Decoder.of(wrapperType), body = code"${lookupDecoderFor(underlying)}.map($wrapperType.apply)")
      )
    )

  override def defaultedInstance: JsonLib.Instances = JsonLib.Instances.fromGivens {
    val T = jvm.Type.Abstract(jvm.Ident("T"))
    List(
      jvm.Given(
        tparams = List(T),
        name = decoderName,
        implicitParams = List(jvm.Param(jvm.Ident("T"), Decoder.of(T))),
        tpe = Decoder.of(default.Defaulted.of(T)),
        body = code"""|c => c.as[${TypesJava.String}].flatMap {
                 |    case "defaulted" => ${TypesScala.Right}(${default.UseDefault}())
                 |    case _           => c.downField("provided").as[$T].map(${default.Provided}.apply)
                 |  }""".stripMargin
      ),
      jvm.Given(
        tparams = List(T),
        name = encoderName,
        implicitParams = List(jvm.Param(jvm.Ident("T"), Encoder.of(T))),
        tpe = Encoder.of(default.Defaulted.of(T)),
        body = code"""|$Encoder.instance {
                 |  case ${default.Provided}(value) => $Json.obj("provided" -> ${Encoder.of(T)}.apply(value))
                 |  case ${default.UseDefault}()      => $Json.fromString("defaulted")
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
          tpe = Decoder.of(wrapperType),
          body =
            if (openEnum: Boolean) code"""${lookupDecoderFor(underlying)}.map($wrapperType.apply)"""
            else code"""${lookupDecoderFor(underlying)}.emap($wrapperType.apply)"""
        ),
        jvm.Given(tparams = Nil, name = encoderName, implicitParams = Nil, tpe = Encoder.of(wrapperType), body = code"${lookupEncoderFor(underlying)}.contramap(_.value)")
      )
    )

  def productInstances(tpe: jvm.Type, fields: NonEmptyList[JsonLib.Field]): JsonLib.Instances = JsonLib.Instances.fromGivens {
    val decoder =
      fields.length match {
        case n if n < 23 =>
          val fieldNames = fields.map(_.jsonName.code).mkCode(", ")
          val instances = fields.map(f => lookupDecoderFor(f.tpe)).mkCode(", ")

          jvm.Given(
            tparams = Nil,
            name = decoderName,
            implicitParams = Nil,
            tpe = Decoder.of(tpe),
            body = code"""$Decoder.forProduct$n[$tpe, ${fields.map(_.tpe.code).mkCode(", ")}]($fieldNames)($tpe.apply)(${dialect.usingCall}$instances)"""
          )
        case _ =>
          val cases = fields.map { f =>
            code"""${f.scalaName} = orThrow(c.get(${f.jsonName})(${dialect.usingCall}${lookupDecoderFor(f.tpe)}))"""
          }
          jvm.Given(
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
                          |)""".stripMargin
          )

      }

    val encoder = {
      fields.length match {
        case n if n < 23 =>
          val fieldNames = fields.map(_.jsonName.code).mkCode(", ")
          val f = code"x => (${fields.map(f => code"x.${f.scalaName}").mkCode(", ")})"
          val instances = fields.map(f => lookupEncoderFor(f.tpe)).mkCode(", ")
          jvm.Given(
            tparams = Nil,
            name = encoderName,
            implicitParams = Nil,
            tpe = Encoder.of(tpe),
            body = code"""$Encoder.forProduct$n[$tpe, ${fields.map(_.tpe.code).mkCode(", ")}]($fieldNames)($f)(${dialect.usingCall}$instances)"""
          )

        case _ =>
          val cases = fields.map { f =>
            code"""${f.jsonName} -> ${lookupEncoderFor(f.tpe)}.apply(row.${f.scalaName})"""
          }
          jvm.Given(
            tparams = Nil,
            name = encoderName,
            implicitParams = Nil,
            tpe = Encoder.of(tpe),
            body = code"""|$Encoder.instance[$tpe](row =>
                          |  $Json.obj(
                          |    ${cases.mkCode(",\n")}
                          |  )
                          |)""".stripMargin
          )

      }
    }

    List(decoder, encoder)
  }

  override def missingInstances: List[jvm.ClassMember] = Nil
}
