package typo
package internal
package codegen

case class JsonLibPlay(pkg: sc.QIdent, default: ComputedDefault, inlineImplicits: Boolean, dialect: Dialect) extends JsonLib {
  val Reads = sc.Type.Qualified("play.api.libs.json.Reads")
  val Writes = sc.Type.Qualified("play.api.libs.json.Writes")
  val OWrites = sc.Type.Qualified("play.api.libs.json.OWrites")
  val Json = sc.Type.Qualified("play.api.libs.json.Json")
  val JsString = sc.Type.Qualified("play.api.libs.json.JsString")
  val JsError = sc.Type.Qualified("play.api.libs.json.JsError")
  val JsSuccess = sc.Type.Qualified("play.api.libs.json.JsSuccess")
  val JsValue = sc.Type.Qualified("play.api.libs.json.JsValue")
  val JsNull = sc.Type.Qualified("play.api.libs.json.JsNull")
  val JsObject = sc.Type.Qualified("play.api.libs.json.JsObject")
  val JsResult = sc.Type.Qualified("play.api.libs.json.JsResult")

  val readsName: sc.Ident = sc.Ident("reads")
  val readsOptName = sc.Ident("readsOpt")
  val writesName: sc.Ident = sc.Ident("writes")

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupReadsFor(tpe: sc.Type): sc.Code = {
    def go(tpe: sc.Type): sc.Code =
      tpe match {
        case TypesScala.BigDecimal                                              => code"$Reads.bigDecReads"
        case TypesScala.Boolean                                                 => code"$Reads.BooleanReads"
        case TypesScala.Byte                                                    => code"$Reads.ByteReads"
        case TypesScala.Double                                                  => code"$Reads.DoubleReads"
        case TypesScala.Float                                                   => code"$Reads.FloatReads"
        case TypesJava.Instant                                                  => code"$Reads.DefaultInstantReads"
        case TypesScala.Int                                                     => code"$Reads.IntReads"
        case TypesJava.LocalDate                                                => code"$Reads.DefaultLocalDateReads"
        case TypesJava.LocalDateTime                                            => code"$Reads.DefaultLocalDateTimeReads"
        case TypesJava.LocalTime                                                => code"$Reads.DefaultLocalTimeReads"
        case TypesScala.Long                                                    => code"$Reads.LongReads"
        case TypesJava.OffsetDateTime                                           => code"$Reads.DefaultOffsetDateTimeReads"
        case TypesJava.String                                                   => code"$Reads.StringReads"
        case TypesJava.UUID                                                     => code"$Reads.uuidReads"
        case sc.Type.ArrayOf(targ)                                              => code"$Reads.ArrayReads[$targ](${dialect.usingCall}${go(targ)}, implicitly)"
        case sc.Type.TApply(default.Defaulted, List(TypesScala.Optional(targ))) => code"${default.Defaulted}.$readsOptName(${dialect.usingCall}${go(targ)})"
        case sc.Type.TApply(default.Defaulted, List(targ))                      => code"${default.Defaulted}.$readsName(${dialect.usingCall}${go(targ)})"
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents)      => code"$tpe.$readsName"
        case x if missingInstancesByType.contains(Reads.of(x))                  => code"${missingInstancesByType(Reads.of(x))}"
        case other                                                              => sc.Summon(Reads.of(other)).code
      }

    go(sc.Type.base(tpe))
  }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupWritesFor(tpe: sc.Type): sc.Code = {
    def go(tpe: sc.Type): sc.Code =
      tpe match {
        case TypesScala.BigDecimal                                         => code"$Writes.BigDecimalWrites"
        case TypesScala.Boolean                                            => code"$Writes.BooleanWrites"
        case TypesScala.Byte                                               => code"$Writes.ByteWrites"
        case TypesScala.Double                                             => code"$Writes.DoubleWrites"
        case TypesScala.Float                                              => code"$Writes.FloatWrites"
        case TypesJava.Instant                                             => code"$Writes.DefaultInstantWrites"
        case TypesScala.Int                                                => code"$Writes.IntWrites"
        case TypesJava.LocalDate                                           => code"$Writes.DefaultLocalDateWrites"
        case TypesJava.LocalDateTime                                       => code"$Writes.DefaultLocalDateTimeWrites"
        case TypesJava.LocalTime                                           => code"$Writes.DefaultLocalTimeWrites"
        case TypesScala.Long                                               => code"$Writes.LongWrites"
        case TypesJava.OffsetDateTime                                      => code"$Writes.DefaultOffsetDateTimeWrites"
        case TypesJava.String                                              => code"$Writes.StringWrites"
        case TypesJava.UUID                                                => code"$Writes.UuidWrites"
        case sc.Type.ArrayOf(targ)                                         => code"$Writes.arrayWrites[$targ](${dialect.usingCall}implicitly, ${go(targ)})"
        case sc.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$writesName(${dialect.usingCall}${go(targ)})"
        case TypesScala.Optional(targ)                                     => code"$Writes.OptionWrites(${dialect.usingCall}${go(targ)})"
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$writesName"
        case x if missingInstancesByType.contains(Writes.of(x))            => code"${missingInstancesByType(Writes.of(x))}"
        case other                                                         => sc.Summon(Writes.of(other)).code
      }

    go(sc.Type.base(tpe))
  }

  override def defaultedInstance(d: ComputedDefault): List[sc.Given] = {
    val T = sc.Type.Abstract(sc.Ident("T"))
    val reader = sc.Given(
      tparams = List(T),
      name = readsName,
      implicitParams = List(sc.Param(sc.Ident("T"), Reads.of(T), None)),
      tpe = Reads.of(d.Defaulted.of(T)),
      body = code"""|{
                    |  case $JsString("defaulted") =>
                    |    $JsSuccess(${d.Defaulted}.${d.UseDefault})
                    |  case $JsObject(Seq(("provided", providedJson: $JsValue))) =>
                    |    $Json.fromJson[T](providedJson).map(${d.Defaulted}.${d.Provided}.apply)
                    |  case _ =>
                    |    $JsError(s"Expected `${d.Defaulted}` json object structure")
                    |}""".stripMargin
    )

    val readerOpt = sc.Given(
      tparams = List(T),
      name = readsOptName,
      implicitParams = List(sc.Param(sc.Ident("T"), Reads.of(T), None)),
      tpe = Reads.of(d.Defaulted.of(TypesScala.Option.of(T))),
      body = code"""|{
                    |  case $JsString("defaulted") =>
                    |    $JsSuccess(${d.Defaulted}.${d.UseDefault})
                    |  case $JsObject(Seq(("provided", $JsNull))) =>
                    |    $JsSuccess(${d.Defaulted}.${d.Provided}(${TypesScala.None}))
                    |  case $JsObject(Seq(("provided", providedJson: $JsValue))) =>
                    |    $Json.fromJson[T](providedJson).map(x => ${d.Defaulted}.${d.Provided}(${TypesScala.Some}(x)))
                    |  case _ =>
                    |    $JsError(s"Expected `${d.Defaulted}` json object structure")
                    |}
                    |""".stripMargin
    )

    val writer = sc.Given(
      tparams = List(T),
      name = writesName,
      implicitParams = List(sc.Param(sc.Ident("T"), Writes.of(T), None)),
      tpe = Writes.of(d.Defaulted.of(T)),
      body = code"""|{
                    |  case ${d.Defaulted}.${d.Provided}(value) => $Json.obj("provided" -> $T.writes(value))
                    |  case ${d.Defaulted}.${d.UseDefault}      => $JsString("defaulted")
                    |}""".stripMargin
    )

    List(reader, readerOpt, writer)
  }

  override def stringEnumInstances(wrapperType: sc.Type, underlying: sc.Type, openEnum: Boolean): List[sc.Given] =
    List(
      sc.Given(
        tparams = Nil,
        name = readsName,
        implicitParams = Nil,
        tpe = Reads.of(wrapperType),
        body = {
          if (openEnum)
            code"${Reads.of(wrapperType)}{(value: $JsValue) => value.validate(${lookupReadsFor(underlying)}).map($wrapperType.apply)}"
          else
            code"${Reads.of(wrapperType)}{(value: $JsValue) => value.validate(${lookupReadsFor(underlying)}).flatMap(str => $wrapperType(str).fold($JsError.apply, $JsSuccess(_)))}"
        }
      ),
      sc.Given(
        tparams = Nil,
        name = writesName,
        implicitParams = Nil,
        tpe = Writes.of(wrapperType),
        body = code"${Writes.of(wrapperType)}(value => ${lookupWritesFor(underlying)}.writes(value.value))"
      )
    )

  override def productInstances(tpe: sc.Type, fields: NonEmptyList[JsonLib.Field]): List[sc.Given] =
    List(
      sc.Given(
        tparams = Nil,
        name = readsName,
        implicitParams = Nil,
        tpe = Reads.of(tpe),
        body = {
          val newFields = fields.map { f =>
            val value = f.tpe match {
              case TypesScala.Optional(of) => code"""json.\\(${f.jsonName}).toOption.map(_.as(${lookupReadsFor(of)}))"""
              case _                       => code"""json.\\(${f.jsonName}).as(${lookupReadsFor(f.tpe)})"""
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
      sc.Given(
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

  def wrapperTypeInstances(wrapperType: sc.Type.Qualified, fieldName: sc.Ident, underlying: sc.Type): List[sc.Given] =
    List(
      sc.Given(tparams = Nil, name = readsName, implicitParams = Nil, tpe = Reads.of(wrapperType), body = code"${lookupReadsFor(underlying)}.map(${wrapperType.value.name}.apply)"),
      sc.Given(tparams = Nil, name = writesName, implicitParams = Nil, tpe = Writes.of(wrapperType), body = code"${lookupWritesFor(underlying)}.contramap(_.$fieldName)")
    )

  override val missingInstances: List[sc.ClassMember] =
    List(
      sc.Given(
        tparams = Nil,
        name = sc.Ident("OffsetTimeReads"),
        implicitParams = Nil,
        tpe = Reads.of(TypesJava.OffsetTime),
        body = code"""|${lookupReadsFor(TypesJava.String)}.flatMapResult { str =>
                      |  try $JsSuccess(${TypesJava.OffsetTime}.parse(str)) catch {
                      |    case x: ${TypesJava.DateTimeParseException} => $JsError(s"must follow $${${TypesJava.DateTimeFormatter}.ISO_OFFSET_TIME}: $${x.getMessage}")
                      |  }
                      |}""".stripMargin
      ),
      sc.Given(
        tparams = Nil,
        name = sc.Ident("OffsetTimeWrites"),
        implicitParams = Nil,
        tpe = Writes.of(TypesJava.OffsetTime),
        body = code"${lookupWritesFor(TypesJava.String)}.contramap(_.toString)"
      )
    )

  val missingInstancesByType: Map[sc.Type, sc.QIdent] =
    missingInstances.collect { case x: sc.Given => (x.tpe, pkg / x.name) }.toMap
}
