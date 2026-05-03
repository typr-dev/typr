package typr
package internal
package codegen

case class JsonLibPlay(pkg: jvm.QIdent, default: ComputedDefault, inlineImplicits: Boolean, lang: LangScala) extends JsonLib {
  val dialect = lang.dialect
  val Reads = jvm.Type.Qualified("play.api.libs.json.Reads")
  val Writes = jvm.Type.Qualified("play.api.libs.json.Writes")
  val OWrites = jvm.Type.Qualified("play.api.libs.json.OWrites")
  val Json = jvm.Type.Qualified("play.api.libs.json.Json")
  val JsString = jvm.Type.Qualified("play.api.libs.json.JsString")
  val JsError = jvm.Type.Qualified("play.api.libs.json.JsError")
  val JsSuccess = jvm.Type.Qualified("play.api.libs.json.JsSuccess")
  val JsValue = jvm.Type.Qualified("play.api.libs.json.JsValue")
  val JsNull = jvm.Type.Qualified("play.api.libs.json.JsNull")
  val JsObject = jvm.Type.Qualified("play.api.libs.json.JsObject")
  val JsResult = jvm.Type.Qualified("play.api.libs.json.JsResult")

  val readsName: jvm.Ident = jvm.Ident("reads")
  val readsOptName = jvm.Ident("readsOpt")
  val writesName: jvm.Ident = jvm.Ident("writes")

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupReadsFor(tpe: jvm.Type): jvm.Code = {
    def go(tpe: jvm.Type): jvm.Code =
      tpe match {
        case TypesScala.BigDecimal                                          => code"$Reads.bigDecReads"
        case TypesScala.Boolean                                             => code"$Reads.BooleanReads"
        case TypesScala.Byte                                                => code"$Reads.ByteReads"
        case TypesScala.Double                                              => code"$Reads.DoubleReads"
        case TypesScala.Float                                               => code"$Reads.FloatReads"
        case TypesJava.Instant                                              => code"$Reads.DefaultInstantReads"
        case TypesScala.Int                                                 => code"$Reads.IntReads"
        case TypesJava.LocalDate                                            => code"$Reads.DefaultLocalDateReads"
        case TypesJava.LocalDateTime                                        => code"$Reads.DefaultLocalDateTimeReads"
        case TypesJava.LocalTime                                            => code"$Reads.DefaultLocalTimeReads"
        case TypesScala.Long                                                => code"$Reads.LongReads"
        case TypesJava.OffsetDateTime                                       => code"$Reads.DefaultOffsetDateTimeReads"
        case TypesJava.String                                               => code"$Reads.StringReads"
        case TypesJava.UUID                                                 => code"$Reads.uuidReads"
        case jvm.Type.ArrayOf(targ)                                         => code"$Reads.ArrayReads[$targ](${dialect.usingCall}${go(targ)}, implicitly)"
        case jvm.Type.TApply(default.Defaulted, List(lang.Optional(targ)))  => code"${default.Defaulted}.$readsOptName(${dialect.usingCall}${go(targ)})"
        case jvm.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$readsName(${dialect.usingCall}${go(targ)})"
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$readsName"
        case x if missingInstancesByType.contains(Reads.of(x))              => code"${missingInstancesByType(Reads.of(x))}"
        case other                                                          => jvm.Summon(Reads.of(other)).code
      }

    go(jvm.Type.base(tpe))
  }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupWritesFor(tpe: jvm.Type): jvm.Code = {
    def go(tpe: jvm.Type): jvm.Code =
      tpe match {
        case TypesScala.BigDecimal                                          => code"$Writes.BigDecimalWrites"
        case TypesScala.Boolean                                             => code"$Writes.BooleanWrites"
        case TypesScala.Byte                                                => code"$Writes.ByteWrites"
        case TypesScala.Double                                              => code"$Writes.DoubleWrites"
        case TypesScala.Float                                               => code"$Writes.FloatWrites"
        case TypesJava.Instant                                              => code"$Writes.DefaultInstantWrites"
        case TypesScala.Int                                                 => code"$Writes.IntWrites"
        case TypesJava.LocalDate                                            => code"$Writes.DefaultLocalDateWrites"
        case TypesJava.LocalDateTime                                        => code"$Writes.DefaultLocalDateTimeWrites"
        case TypesJava.LocalTime                                            => code"$Writes.DefaultLocalTimeWrites"
        case TypesScala.Long                                                => code"$Writes.LongWrites"
        case TypesJava.OffsetDateTime                                       => code"$Writes.DefaultOffsetDateTimeWrites"
        case TypesJava.String                                               => code"$Writes.StringWrites"
        case TypesJava.UUID                                                 => code"$Writes.UuidWrites"
        case jvm.Type.ArrayOf(targ)                                         => code"$Writes.arrayWrites[$targ](${dialect.usingCall}implicitly, ${go(targ)})"
        case jvm.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$writesName(${dialect.usingCall}${go(targ)})"
        case lang.Optional(targ)                                            => code"$Writes.OptionWrites(${dialect.usingCall}${go(targ)})"
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$writesName"
        case x if missingInstancesByType.contains(Writes.of(x))             => code"${missingInstancesByType(Writes.of(x))}"
        case other                                                          => jvm.Summon(Writes.of(other)).code
      }

    go(jvm.Type.base(tpe))
  }

  override def defaultedInstance: JsonLib.Instances = JsonLib.Instances.fromGivens {
    val T = jvm.Type.Abstract(jvm.Ident("T"))
    val reader = jvm.Given(
      tparams = List(T),
      name = readsName,
      implicitParams = List(jvm.Param(jvm.Ident("T"), Reads.of(T))),
      tpe = Reads.of(default.Defaulted.of(T)),
      body = code"""|{
                    |  case $JsString("defaulted") =>
                    |    $JsSuccess(${default.Defaulted}.${default.UseDefault}())
                    |  case $JsObject(Seq(("provided", providedJson: $JsValue))) =>
                    |    $Json.fromJson[T](providedJson).map(${default.Defaulted}.${default.Provided}.apply)
                    |  case _ =>
                    |    $JsError(s"Expected `${default.Defaulted}` json object structure")
                    |}""".stripMargin
    )

    val readerOpt = jvm.Given(
      tparams = List(T),
      name = readsOptName,
      implicitParams = List(jvm.Param(jvm.Ident("T"), Reads.of(T))),
      tpe = Reads.of(default.Defaulted.of(TypesScala.Option.of(T))),
      body = code"""|{
                    |  case $JsString("defaulted") =>
                    |    $JsSuccess(${default.Defaulted}.${default.UseDefault}())
                    |  case $JsObject(Seq(("provided", $JsNull))) =>
                    |    $JsSuccess(${default.Defaulted}.${default.Provided}(${TypesScala.None}))
                    |  case $JsObject(Seq(("provided", providedJson: $JsValue))) =>
                    |    $Json.fromJson[T](providedJson).map(x => ${default.Defaulted}.${default.Provided}(${TypesScala.Some}(x)))
                    |  case _ =>
                    |    $JsError(s"Expected `${default.Defaulted}` json object structure")
                    |}
                    |""".stripMargin
    )

    val writer = jvm.Given(
      tparams = List(T),
      name = writesName,
      implicitParams = List(jvm.Param(jvm.Ident("T"), Writes.of(T))),
      tpe = Writes.of(default.Defaulted.of(T)),
      body = code"""|{
                    |  case ${default.Defaulted}.${default.Provided}(value) => $Json.obj("provided" -> $T.writes(value))
                    |  case ${default.Defaulted}.${default.UseDefault}()    => $JsString("defaulted")
                    |}""".stripMargin
    )

    List(reader, readerOpt, writer)
  }

  override def stringEnumInstances(wrapperType: jvm.Type, underlying: jvm.Type, openEnum: Boolean): JsonLib.Instances =
    JsonLib.Instances.fromGivens(
      List(
        jvm.Given(
          tparams = Nil,
          name = readsName,
          implicitParams = Nil,
          tpe = Reads.of(wrapperType),
          body = {
            if (openEnum)
              code"${Reads.of(wrapperType)}{(value: $JsValue) => value.validate(${dialect.usingCall}${lookupReadsFor(underlying)}).map($wrapperType.apply)}"
            else
              code"${Reads.of(wrapperType)}{(value: $JsValue) => value.validate(${dialect.usingCall}${lookupReadsFor(underlying)}).flatMap(str => $wrapperType(str).fold($JsError.apply, $JsSuccess(_)))}"
          }
        ),
        jvm.Given(
          tparams = Nil,
          name = writesName,
          implicitParams = Nil,
          tpe = Writes.of(wrapperType),
          body = code"${Writes.of(wrapperType)}(value => ${lookupWritesFor(underlying)}.writes(value.value))"
        )
      )
    )

  override def productInstances(tpe: jvm.Type, fields: NonEmptyList[JsonLib.Field]): JsonLib.Instances =
    JsonLib.Instances.fromGivens(
      List(
        jvm.Given(
          tparams = Nil,
          name = readsName,
          implicitParams = Nil,
          tpe = Reads.of(tpe),
          body = {
            val newFields = fields.map { f =>
              val value = f.tpe match {
                case lang.Optional(of) => code"""json.\\(${f.jsonName}).toOption.map(_.as(${dialect.usingCall}${lookupReadsFor(of)}))"""
                case _                 => code"""json.\\(${f.jsonName}).as(${dialect.usingCall}${lookupReadsFor(f.tpe)})"""
              }

              code"""${f.scalaName} = $value"""
            }
            code"""|${Reads.of(tpe)}(json => $JsResult.fromTry(
                 |    ${TypesScala.Try}(
                 |      $tpe(
                 |        ${newFields.mkCode(",\n")}
                 |      )
                 |    )
                 |  ),
                 |)""".stripMargin
          }
        ),
        jvm.Given(
          tparams = Nil,
          name = writesName,
          implicitParams = Nil,
          tpe = OWrites.of(tpe),
          body = {
            val newFields = fields.map { f =>
              code"""${f.jsonName} -> ${lookupWritesFor(f.tpe)}.writes(o.${f.scalaName})"""
            }
            code"""|${OWrites.of(tpe)}(o =>
                 |  new $JsObject(${TypesScala.ListMap.of(TypesJava.String, JsValue)}(
                 |    ${newFields.mkCode(",\n")}
                 |  ))
                 |)""".stripMargin
          }
        )
      )
    )

  def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, fieldName: jvm.Ident, underlying: jvm.Type): JsonLib.Instances =
    JsonLib.Instances.fromGivens(
      List(
        jvm.Given(tparams = Nil, name = readsName, implicitParams = Nil, tpe = Reads.of(wrapperType), body = code"${lookupReadsFor(underlying)}.map(${wrapperType.value.name}.apply)"),
        jvm.Given(tparams = Nil, name = writesName, implicitParams = Nil, tpe = Writes.of(wrapperType), body = code"${lookupWritesFor(underlying)}.contramap(_.$fieldName)")
      )
    )

  override val missingInstances: List[jvm.ClassMember] =
    List(
      jvm.Given(
        tparams = Nil,
        name = jvm.Ident("OffsetTimeReads"),
        implicitParams = Nil,
        tpe = Reads.of(TypesJava.OffsetTime),
        body = code"""|${lookupReadsFor(TypesJava.String)}.flatMapResult { str =>
                      |  try $JsSuccess(${TypesJava.OffsetTime}.parse(str)) catch {
                      |    case x: ${TypesJava.DateTimeParseException} => $JsError(s"must follow $${${TypesJava.DateTimeFormatter}.ISO_OFFSET_TIME}: $${x.getMessage}")
                      |  }
                      |}""".stripMargin
      ),
      jvm.Given(
        tparams = Nil,
        name = jvm.Ident("OffsetTimeWrites"),
        implicitParams = Nil,
        tpe = Writes.of(TypesJava.OffsetTime),
        body = code"${lookupWritesFor(TypesJava.String)}.contramap(_.toString)"
      )
    )

  val missingInstancesByType: Map[jvm.Type, jvm.QIdent] =
    missingInstances.collect { case x: jvm.Given => (x.tpe, pkg / x.name) }.toMap
}
