package typr
package internal
package codegen

object DbLibTextSupport {
  // name of type class instance
  val textName = jvm.Ident("pgText")
  // configurable default value used in CSV file. this must match between what the generated COPY statement and the `Text` instance says
  val DefaultValue = "__DEFAULT_VALUE__"
}

class DbLibTextSupport(pkg: jvm.QIdent, inlineImplicits: Boolean, externalText: Option[jvm.Type.Qualified], default: ComputedDefault, lang: LangScala) {
  import DbLibTextSupport.*
  val dialect = lang.dialect
  // may refer to the qualified name of `Text` typeclass in doobie, or the one we generate ourselves for other libraries
  val Text = externalText.getOrElse(jvm.Type.Qualified(pkg / jvm.Ident("Text")))
  // boilerplate for streaming insert we generate for non-doobie libraries
  val streamingInsert = jvm.Type.Qualified(pkg / jvm.Ident("streamingInsert"))

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupTextFor(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) Text.of(tpe).code
    else
      jvm.Type.base(tpe) match {
        case TypesScala.BigDecimal                                          => code"$Text.bigDecimalInstance"
        case TypesScala.Boolean                                             => code"$Text.booleanInstance"
        case TypesScala.Double                                              => code"$Text.doubleInstance"
        case TypesScala.Float                                               => code"$Text.floatInstance"
        case TypesScala.Int                                                 => code"$Text.intInstance"
        case TypesScala.Long                                                => code"$Text.longInstance"
        case TypesJava.String                                               => code"$Text.stringInstance"
        case jvm.Type.ArrayOf(TypesScala.Byte)                              => code"$Text.byteArrayInstance"
        case lang.Optional(targ)                                            => code"$Text.option(${dialect.usingCall}${lookupTextFor(targ)})"
        case jvm.Type.TApply(default.Defaulted, List(targ))                 => code"${default.Defaulted}.$textName(${dialect.usingCall}${lookupTextFor(targ)})"
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) => code"$tpe.$textName"
        case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          val summoner = if (dialect == Dialect.Scala2XSource3) "implicitly" else "summon"
          code"$Text.iterableInstance[${TypesScala.Array}, $targ](${dialect.usingCall}${lookupTextFor(targ)}, $summoner)"
        case other => code"${Text.of(other)}"
      }

  val defaultedInstance: jvm.Given = {
    val T = jvm.Type.Abstract(jvm.Ident("T"))
    val textofT = jvm.Ident("t")
    jvm.Given(
      tparams = List(T),
      name = textName,
      implicitParams = List(jvm.Param(textofT, Text.of(T))),
      tpe = Text.of(default.Defaulted.of(T)),
      body = code"""|$Text.instance {
               |  case (${default.Defaulted}.${default.Provided}(value), sb) => $textofT.unsafeEncode(value, sb)
               |  case (${default.Defaulted}.${default.UseDefault}(), sb) =>
               |    sb.append("$DefaultValue")
               |    ()
               |}""".stripMargin
    )
  }

  def anyValInstance(wrapperType: jvm.Type, underlying: jvm.Type): jvm.Given =
    jvm.Given(
      tparams = Nil,
      name = textName,
      implicitParams = Nil,
      tpe = Text.of(wrapperType),
      body = {
        val underlyingText = lookupTextFor(underlying)
        val v = jvm.Ident("v")
        code"""|new ${Text.of(wrapperType)} {
               |  override def unsafeEncode($v: $wrapperType, sb: ${TypesJava.StringBuilder}): Unit = $underlyingText.unsafeEncode($v.value, sb)
               |  override def unsafeArrayEncode($v: $wrapperType, sb: ${TypesJava.StringBuilder}): Unit = $underlyingText.unsafeArrayEncode($v.value, sb)
               |}""".stripMargin
      }
    )

  def rowInstance(tpe: jvm.Type, cols: NonEmptyList[ComputedColumn]): jvm.Given = {
    val row = jvm.Ident("row")
    val sb = jvm.Ident("sb")
    val textCols = cols.toList
      .filterNot(_.dbCol.maybeGenerated.exists(_.ALWAYS))
      .map { col => code"${lookupTextFor(col.tpe)}.unsafeEncode($row.${col.name}, $sb)" }
    val body =
      code"""|$Text.instance[$tpe]{ ($row, $sb) =>
             |  ${textCols.mkCode(code"\n$sb.append($Text.DELIMETER)\n")}
             |}""".stripMargin
    jvm.Given(tparams = Nil, name = textName, implicitParams = Nil, tpe = Text.of(tpe), body = body)
  }

  def customTypeInstance(ct: CustomType): jvm.Given = {
    jvm.Given(
      tparams = Nil,
      name = textName,
      implicitParams = Nil,
      tpe = Text.of(ct.typoType),
      body = {
        val underlying = lookupTextFor(ct.toText.textType)
        val v = jvm.Ident("v")
        code"""|new ${Text.of(ct.typoType)} {
               |  override def unsafeEncode($v: ${ct.typoType}, sb: ${TypesJava.StringBuilder}): Unit = $underlying.unsafeEncode(${ct.toText.toTextType(v)}, sb)
               |  override def unsafeArrayEncode($v: ${ct.typoType}, sb: ${TypesJava.StringBuilder}): Unit = $underlying.unsafeArrayEncode(${ct.toText.toTextType(v)}, sb)
               |}""".stripMargin
      }
    )
  }
}
