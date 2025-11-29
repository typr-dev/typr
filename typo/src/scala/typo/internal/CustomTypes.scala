package typo
package internal

import typo.internal.codegen.*

import scala.collection.mutable

class CustomTypes(pkg: jvm.QIdent, lang: Lang) {
  import jvm.Code.{CodeOps, TreeOps, TypeOps}

  lazy val TypoBox = CustomType(
    comment = "This represents the box datatype in PostgreSQL",
    sqlType = "box",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoBox")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("x1"), lang.Double),
      jvm.Param(jvm.Ident("y1"), lang.Double),
      jvm.Param(jvm.Ident("x2"), lang.Double),
      jvm.Param(jvm.Ident("y2"), lang.Double)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.PGbox,
      // PGbox.point is a public field, PGpoint.x/y are public fields
      toTypo = (expr, target) =>
        target.construct(
          expr.select("point").arrayIndex(0).select("x"),
          expr.select("point").arrayIndex(0).select("y"),
          expr.select("point").arrayIndex(1).select("x"),
          expr.select("point").arrayIndex(1).select("y")
        )
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.PGbox,
      // TypoBox record/case class has x1(), y1(), x2(), y2() accessors
      fromTypo = (expr, target) =>
        target.construct(
          expr.callNullary("x1"),
          expr.callNullary("y1"),
          expr.callNullary("x2"),
          expr.callNullary("y2")
        )
    ),
    toText = CustomType.Text.string { expr =>
      val x1 = rt(expr.callNullary("x1"))
      val y1 = rt(expr.callNullary("y1"))
      val x2 = rt(expr.callNullary("x2"))
      val y2 = rt(expr.callNullary("y2"))
      lang.s(code"(($x1,$y1),($x2,$y2))")
    }
  )

  lazy val TypoBytea = lang match {
    case LangJava =>
      CustomType(
        comment = "This represents the bytea datatype in PostgreSQL",
        sqlType = "bytea",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoBytea")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), jvm.Type.ArrayOf(lang.Byte))
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = jvm.Type.ArrayOf(lang.Byte),
          // JDBC returns byte[], but Java record uses Byte[], so we box it
          toTypo = (expr, target) => code"new $target(${TypesJava.ByteArrays}.box($expr))"
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = jvm.Type.ArrayOf(lang.Byte),
          // Java record uses Byte[], but JDBC expects byte[], so we unbox it
          fromTypo = (expr, _) => code"${TypesJava.ByteArrays}.unbox($expr.value())"
        ),
        forbidArray = true,
        toText = CustomType.Text(jvm.Type.ArrayOf(lang.Byte), expr => code"${TypesJava.ByteArrays}.unbox($expr.value())")
      )
    case _: LangScala =>
      // In Scala, byte arrays are always Array[scala.Byte] (primitive), never Array[java.lang.Byte]
      val byteArray = jvm.Type.ArrayOf(TypesScala.Byte)
      CustomType(
        comment = "This represents the bytea datatype in PostgreSQL",
        sqlType = "bytea",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoBytea")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), byteArray)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = byteArray,
          toTypo = (expr, target) => jvm.New(target, List(jvm.Arg.Pos(expr)))
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = byteArray,
          fromTypo = (expr, _) => expr.callNullary("value")
        ),
        forbidArray = true,
        toText = CustomType.Text(byteArray, expr => expr.callNullary("value"))
      )
    case other => sys.error(s"Unsupported language: $other")
  }

  lazy val TypoLocalDate = CustomType(
    comment = "This is `java.time.LocalDate`, but transferred to and from postgres as strings. The reason is that postgres driver and db libs are broken",
    sqlType = "date",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoLocalDate")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("value"), TypesJava.LocalDate)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.String,
      toTypo = (expr, target) => jvm.New(target, List(jvm.Arg.Pos(code"${TypesJava.LocalDate}.parse($expr)")))
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.String,
      fromTypo = (expr, _) => expr.callNullary("value").invoke("toString")
    ),
    toText = CustomType.Text.string(expr => expr.callNullary("value").invoke("toString")),
    objBody = target =>
      List(
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("now"),
          params = Nil,
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"new $target(${TypesJava.LocalDate}.now())")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("str"), TypesJava.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"new $target(${TypesJava.LocalDate}.parse(str))")
        )
      )
  )
  lazy val TypoLocalTime = CustomType(
    comment = "This is `java.time.LocalTime`, but with microsecond precision and transferred to and from postgres as strings. The reason is that postgres driver and db libs are broken",
    sqlType = "time",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoLocalTime")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("value"), TypesJava.LocalTime)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.String,
      toTypo = (expr, target) => jvm.New(target, List(jvm.Arg.Pos(code"${TypesJava.LocalTime}.parse($expr)")))
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.String,
      fromTypo = (expr, _) => expr.callNullary("value").callNullary("toString")
    ),
    toText = CustomType.Text.string(expr => expr.callNullary("value").callNullary("toString")),
    objBody = target =>
      List(
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("value"), TypesJava.LocalTime)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"new $target(value.truncatedTo(${TypesJava.ChronoUnit}.MICROS))")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("str"), TypesJava.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(target.code.invoke("apply", TypesJava.LocalTime.code.invoke("parse", code"str")))
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("now"),
          params = Nil,
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"$target.apply(${TypesJava.LocalTime}.now())")
        )
      )
  )

  lazy val TypoLocalDateTime = CustomType(
    comment = "This is `java.time.LocalDateTime`, but with microsecond precision and transferred to and from postgres as strings. The reason is that postgres driver and db libs are broken",
    sqlType = "timestamp",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoLocalDateTime")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("value"), TypesJava.LocalDateTime)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.String,
      toTypo = (expr, target) => code"$target.apply(${TypesJava.LocalDateTime}.parse($expr, parser))"
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.String,
      fromTypo = (expr, _) => expr.callNullary("value").callNullary("toString")
    ),
    toText = CustomType.Text.string(expr => expr.callNullary("value").callNullary("toString")),
    objBody = target =>
      List(
        jvm.Value(
          annotations = Nil,
          name = jvm.Ident("parser"),
          tpe = TypesJava.DateTimeFormatter,
          body = Some(code"""new ${TypesJava.DateTimeFormatterBuilder}().appendPattern("yyyy-MM-dd HH:mm:ss").appendFraction(${TypesJava.ChronoField}.MICRO_OF_SECOND, 0, 6, true).toFormatter()""")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("value"), TypesJava.LocalDateTime)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"new $target(value.truncatedTo(${TypesJava.ChronoUnit}.MICROS))")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("str"), TypesJava.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"$target.apply(${TypesJava.LocalDateTime}.parse(str, parser))")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("now"),
          params = Nil,
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"$target.apply(${TypesJava.LocalDateTime}.now())")
        )
      )
  )

  lazy val TypoInstant = CustomType(
    comment = "This is `java.time.TypoInstant`, but with microsecond precision and transferred to and from postgres as strings. The reason is that postgres driver and db libs are broken",
    sqlType = "timestamptz",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoInstant")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("value"), TypesJava.Instant)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.String,
      toTypo = (expr, _) => code"apply($expr)"
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.String,
      fromTypo = (expr, _) => expr.callNullary("value").callNullary("toString")
    ),
    toText = CustomType.Text.string(expr => expr.callNullary("value").callNullary("toString")),
    objBody = target =>
      List(
        jvm.Value(
          annotations = Nil,
          name = jvm.Ident("parser"),
          tpe = TypesJava.DateTimeFormatter,
          body = Some(
            code"""new ${TypesJava.DateTimeFormatterBuilder}().appendPattern("yyyy-MM-dd HH:mm:ss").appendFraction(${TypesJava.ChronoField}.MICRO_OF_SECOND, 0, 6, true).appendPattern("X").toFormatter()"""
          )
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("value"), TypesJava.Instant)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"new $target(value.truncatedTo(${TypesJava.ChronoUnit}.MICROS))")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("str"), TypesJava.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"$target.apply(${TypesJava.OffsetDateTime}.parse(str, parser).toInstant())")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("now"),
          params = Nil,
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"$target.apply(${TypesJava.Instant}.now())")
        )
      )
  )

  lazy val TypoOffsetTime = CustomType(
    comment = "This is `java.time.OffsetTime`, but with microsecond precision and transferred to and from postgres as strings. The reason is that postgres driver and db libs are broken",
    sqlType = "timetz",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoOffsetTime")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("value"), TypesJava.OffsetTime)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.String,
      toTypo = (expr, target) => code"new $target(${TypesJava.OffsetTime}.parse($expr, parser))"
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.String,
      fromTypo = (expr, _) => expr.callNullary("value").callNullary("toString")
    ),
    toText = CustomType.Text.string(expr => expr.callNullary("value").callNullary("toString")),
    objBody = target =>
      List(
        jvm.Value(
          annotations = Nil,
          name = jvm.Ident("parser"),
          tpe = TypesJava.DateTimeFormatter,
          body =
            Some(code"""new ${TypesJava.DateTimeFormatterBuilder}().appendPattern("HH:mm:ss").appendFraction(${TypesJava.ChronoField}.MICRO_OF_SECOND, 0, 6, true).appendPattern("X").toFormatter()""")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("value"), TypesJava.OffsetTime)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"new $target(value.truncatedTo(${TypesJava.ChronoUnit}.MICROS))")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("str"), TypesJava.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"$target.apply(${TypesJava.OffsetTime}.parse(str, parser))")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("now"),
          params = Nil,
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"$target.apply(${TypesJava.OffsetTime}.now())")
        )
      )
  )

  lazy val TypoCircle = CustomType(
    comment = "This represents circle datatype in PostgreSQL, consisting of a point and a radius",
    sqlType = "circle",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoCircle")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("center"), TypoPoint.typoType),
      jvm.Param(jvm.Ident("radius"), lang.Double)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.PGcircle,
      toTypo = (expr, target) =>
        target.construct(
          TypoPoint.typoType.construct(expr.select("center").select("x"), expr.select("center").select("y")),
          expr.select("radius")
        )
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.PGcircle,
      fromTypo = (expr, target) => target.construct(expr.callNullary("center").callNullary("x"), expr.callNullary("center").callNullary("y"), expr.callNullary("radius"))
    ),
    toText = CustomType.Text.string { expr =>
      val x = rt(expr.callNullary("center").callNullary("x"))
      val y = rt(expr.callNullary("center").callNullary("y"))
      val radius = rt(expr.callNullary("radius"))
      lang.s(code"<($x,$y),$radius>")
    }
  )

  lazy val TypoLine = CustomType(
    comment = "This implements a line represented by the linear equation Ax + By + C = 0",
    sqlType = "line",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoLine")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("a"), lang.Double),
      jvm.Param(jvm.Ident("b"), lang.Double),
      jvm.Param(jvm.Ident("c"), lang.Double)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.PGline,
      // PGline has public fields a, b, c
      toTypo = (expr, target) => target.construct(expr.select("a"), expr.select("b"), expr.select("c"))
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.PGline,
      // TypoLine record/case class has a(), b(), c() accessors
      fromTypo = (expr, target) => target.construct(expr.callNullary("a"), expr.callNullary("b"), expr.callNullary("c"))
    ),
    toText = CustomType.Text.string { expr =>
      val a = rt(expr.callNullary("a"))
      val b = rt(expr.callNullary("b"))
      val c = rt(expr.callNullary("c"))
      lang.s(code"{$a,$b,$c}")
    }
  )

  lazy val TypoLineSegment = CustomType(
    comment = "Line segment datatype in PostgreSQL",
    sqlType = "lseg",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoLineSegment")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("p1"), TypoPoint.typoType),
      jvm.Param(jvm.Ident("p2"), TypoPoint.typoType)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.PGlseg,
      // PGlseg.point is a public PGpoint[] array, PGpoint.x/y are public fields
      toTypo = (expr, target) =>
        target.construct(
          TypoPoint.typoType.construct(expr.select("point").arrayIndex(0).select("x"), expr.select("point").arrayIndex(0).select("y")),
          TypoPoint.typoType.construct(expr.select("point").arrayIndex(1).select("x"), expr.select("point").arrayIndex(1).select("y"))
        )
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.PGlseg,
      // TypoLineSegment has p1()/p2() accessors returning TypoPoint, TypoPoint has x()/y() accessors
      fromTypo = (expr, target) =>
        target.construct(
          TypesJava.PGpoint.construct(expr.callNullary("p1").callNullary("x"), expr.callNullary("p1").callNullary("y")),
          TypesJava.PGpoint.construct(expr.callNullary("p2").callNullary("x"), expr.callNullary("p2").callNullary("y"))
        )
    ),
    toText = CustomType.Text.string { expr =>
      val p1x = rt(expr.callNullary("p1").callNullary("x"))
      val p1y = rt(expr.callNullary("p1").callNullary("y"))
      val p2x = rt(expr.callNullary("p2").callNullary("x"))
      val p2y = rt(expr.callNullary("p2").callNullary("y"))
      lang.s(code"(($p1x,$p1y),($p2x,$p2y))")
    }
  )

  lazy val TypoPath = {
    val p = jvm.Ident("p")
    // For PGpoint (external Java type), x and y are public fields - use direct field access
    val toTypoPointLambda = jvm.Lambda1(p, code"new ${TypoPoint.typoType}(${p.code}.x, ${p.code}.y)")
    // For TypoPoint (our generated type), use callNullary for proper method call handling
    val toPGpointLambda = jvm.Lambda1(p, code"new ${TypesJava.PGpoint}(${p.code.callNullary("x")}, ${p.code.callNullary("y")})")
    val px = rt(p.code.callNullary("x"))
    val py = rt(p.code.callNullary("y"))
    val toStringLambda = jvm.Lambda1(p, lang.s(code"$px, $py").code)
    val listType = lang.ListType.tpe.of(TypoPoint.typoType)
    CustomType(
      comment = "This implements a path (a multiple segmented line, which may be closed)",
      sqlType = "path",
      typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoPath")),
      params = NonEmptyList(
        jvm.Param(jvm.Ident("open"), lang.Boolean),
        jvm.Param(jvm.Ident("points"), listType)
      ),
      toTypo = CustomType.ToTypo(
        jdbcType = TypesJava.PGpath,
        toTypo = (expr, target) =>
          target.construct(
            expr.callNullary("isOpen"),
            lang.ListType.arrayMapToList(code"$expr.points", toTypoPointLambda.code)
          )
      ),
      fromTypo = CustomType.FromTypo(
        jdbcType = TypesJava.PGpath,
        fromTypo = (expr, target) => {
          val arrayGen = lang.newArray(TypesJava.PGpoint, None)
          val pointsArray = lang.ListType.listMapToArray(expr.callNullary("points"), toPGpointLambda.code, arrayGen)
          code"new $target($pointsArray, ${expr.callNullary("open")})"
        }
      ),
      toText = CustomType.Text.string { expr =>
        val openChar = lang.ternary(expr.callNullary("open"), code""""["""", code""""("""")
        val closeChar = lang.ternary(expr.callNullary("open"), code""""]"""", code"""")"""")
        val pointsStr = lang.ListType.mapJoinString(expr.callNullary("points"), lang.renderTree(toStringLambda, lang.Ctx.Empty), ",")
        code"""$openChar + $pointsStr + $closeChar"""
      }
    )
  }

  lazy val TypoPoint = CustomType(
    comment = "Point datatype in PostgreSQL",
    sqlType = "point",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoPoint")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("x"), lang.Double),
      jvm.Param(jvm.Ident("y"), lang.Double)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.PGpoint,
      // PGpoint has public fields x, y
      toTypo = (expr, target) => target.construct(expr.select("x"), expr.select("y"))
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.PGpoint,
      // TypoPoint record/case class has x(), y() accessors
      fromTypo = (expr, target) => target.construct(expr.callNullary("x"), expr.callNullary("y"))
    ),
    toText = CustomType.Text.string { expr =>
      val x = rt(expr.callNullary("x"))
      val y = rt(expr.callNullary("y"))
      lang.s(code"($x,$y)")
    }
  )

  lazy val TypoPolygon = {
    val p = jvm.Ident("p")
    // For PGpoint (external Java type), x and y are public fields - use direct field access
    val toTypoPointLambda = jvm.Lambda1(p, code"new ${TypoPoint.typoType}(${p.code}.x, ${p.code}.y)")
    // For TypoPoint (our generated type), use callNullary for proper method call handling
    val toPGpointLambda = jvm.Lambda1(p, code"new ${TypesJava.PGpoint}(${p.code.callNullary("x")}, ${p.code.callNullary("y")})")
    val px = rt(p.code.callNullary("x"))
    val py = rt(p.code.callNullary("y"))
    val toStringLambda = jvm.Lambda1(p, lang.s(code"$px, $py").code)
    val listType = lang.ListType.tpe.of(TypoPoint.typoType)
    CustomType(
      comment = "Polygon datatype in PostgreSQL",
      sqlType = "polygon",
      typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoPolygon")),
      params = NonEmptyList(
        jvm.Param(jvm.Ident("points"), listType)
      ),
      toTypo = CustomType.ToTypo(
        jdbcType = TypesJava.PGpolygon,
        toTypo = (expr, target) => {
          val arrayMapToList = lang.ListType.arrayMapToList(code"$expr.points", toTypoPointLambda.code)
          code"new $target($arrayMapToList)"
        }
      ),
      fromTypo = CustomType.FromTypo(
        jdbcType = TypesJava.PGpolygon,
        fromTypo = (expr, target) => {
          val arrayGen = lang.newArray(TypesJava.PGpoint, None)
          val pointsArray = lang.ListType.listMapToArray(expr.callNullary("points"), toPGpointLambda.code, arrayGen)
          code"new $target($pointsArray)"
        }
      ),
      toText = CustomType.Text.string { expr =>
        val pointsStr = lang.ListType.mapJoinString(expr.callNullary("points"), lang.renderTree(toStringLambda, lang.Ctx.Empty), ",")
        code""""(" + $pointsStr + ")""""
      }
    )
  }

  lazy val TypoInterval = CustomType(
    comment = "Interval type in PostgreSQL",
    sqlType = "interval",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoInterval")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("years"), lang.Int),
      jvm.Param(jvm.Ident("months"), lang.Int),
      jvm.Param(jvm.Ident("days"), lang.Int),
      jvm.Param(jvm.Ident("hours"), lang.Int),
      jvm.Param(jvm.Ident("minutes"), lang.Int),
      jvm.Param(jvm.Ident("seconds"), lang.Double)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.PGInterval,
      toTypo = (expr, target) => {
        val years = expr.callNullary("getYears")
        val months = expr.callNullary("getMonths")
        val days = expr.callNullary("getDays")
        val hours = expr.callNullary("getHours")
        val minutes = expr.callNullary("getMinutes")
        val seconds = expr.callNullary("getSeconds")
        target.construct(years, months, days, hours, minutes, seconds)
      }
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.PGInterval,
      fromTypo = (expr, target) => {
        val years = expr.callNullary("years")
        val months = expr.callNullary("months")
        val days = expr.callNullary("days")
        val hours = expr.callNullary("hours")
        val minutes = expr.callNullary("minutes")
        val seconds = expr.callNullary("seconds")
        code"new $target($years, $months, $days, $hours, $minutes, $seconds)"
      }
    ),
    toText = CustomType.Text.string { expr =>
      val years = rt(expr.callNullary("years"))
      val months = rt(expr.callNullary("months"))
      val days = rt(expr.callNullary("days"))
      val hours = rt(expr.callNullary("hours"))
      val minutes = rt(expr.callNullary("minutes"))
      val seconds = rt(expr.callNullary("seconds"))
      lang.s(code"P${years}Y${months}M${days}DT${hours}H${minutes}M${seconds}S")
    }
  )

  lazy val TypoHStore = lang match {
    case LangJava =>
      CustomType(
        comment = "The text representation of an hstore, used for input and output, includes zero or more key => value pairs separated by commas",
        sqlType = "hstore",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoHStore")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), TypesJava.Map.of(TypesJava.String, TypesJava.String))
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.Map.of(jvm.Type.Wildcard, jvm.Type.Wildcard),
          toTypo = (expr, target) => code"new $target((${TypesJava.Map}<${TypesJava.String}, ${TypesJava.String}>) $expr)"
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.Map.of(TypesJava.String, TypesJava.String),
          fromTypo = (expr, _) => code"new ${TypesJava.HashMap}<${TypesJava.String}, ${TypesJava.String}>($expr.value())"
        ),
        forbidArray = true,
        toText = CustomType.Text.string { expr =>
          code"""${TypesJava.String}.join(",", $expr.value().entrySet().stream().map(e -> e.getKey() + " => " + e.getValue()).toList())"""
        }
      )
    case _: LangScala =>
      // In Scala code, always use Scala Map for TypoHStore, regardless of TypeSupport
      val scalaMap = TypesScala.Map.of(TypesJava.String, TypesJava.String)
      CustomType(
        comment = "The text representation of an hstore, used for input and output, includes zero or more key => value pairs separated by commas",
        sqlType = "hstore",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoHStore")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), scalaMap)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.Map.of(jvm.Type.Wildcard, jvm.Type.Wildcard),
          toTypo = (expr, target) => {
            code"""|{
                   |  val b = ${TypesScala.Map}.newBuilder[${TypesJava.String}, ${TypesJava.String}]
                   |  $expr.forEach { case (k, v) => b += k.asInstanceOf[${TypesJava.String}] -> v.asInstanceOf[${TypesJava.String}]}
                   |  $target(b.result())
                   |}""".stripMargin
          }
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.Map.of(TypesJava.String, TypesJava.String),
          fromTypo = (expr, _) => {
            code"""|{
                   |  val b = new ${TypesJava.HashMap}[${TypesJava.String}, ${TypesJava.String}]
                   |  $expr.value.foreach { case (k, v) => b.put(k, v)}
                   |  b
                   |}""".stripMargin
          }
        ),
        forbidArray = true,
        toText = CustomType.Text.string { expr =>
          code"""$expr.value.map { case (k, v) => s"$$k => $$v" }.mkString(",")"""
        }
      )
    case other => sys.error(s"Unsupported language: $other")
  }

  lazy val TypoMoney = {
    val bigDecimalType = lang.BigDecimal
    val usesScalaBigDecimal = bigDecimalType == TypesScala.BigDecimal
    CustomType(
      comment = "Money and cash types in PostgreSQL",
      sqlType = "money",
      typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoMoney")),
      params = NonEmptyList(
        jvm.Param(jvm.Ident("value"), bigDecimalType)
      ),
      toTypo = CustomType.ToTypo(
        jdbcType = TypesJava.BigDecimal,
        toTypo =
          if (usesScalaBigDecimal)
            // Convert java.math.BigDecimal to scala.math.BigDecimal
            (expr, target) => target.construct(code"${TypesScala.BigDecimal}($expr)")
          else
            (expr, target) => target.construct(expr)
      ),
      fromTypo = CustomType.FromTypo(
        jdbcType = TypesJava.BigDecimal,
        fromTypo =
          if (usesScalaBigDecimal)
            // Convert scala.math.BigDecimal to java.math.BigDecimal via .bigDecimal
            (expr, _) => expr.callNullary("value").callNullary("bigDecimal")
          else
            (expr, _) => expr.callNullary("value")
      ),
      toText = CustomType.Text(bigDecimalType, expr => expr.callNullary("value"))
    )
  }

  lazy val TypoShort = lang match {
    case LangJava =>
      CustomType(
        comment = "Short primitive",
        sqlType = "int2",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoShort")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), TypesJava.Short)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.Integer,
          toTypo = (expr, target) => code"new $target($expr.shortValue())"
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.Integer,
          fromTypo = (expr, _) => code"(int) $expr.value()"
        ),
        toText = CustomType.Text(TypesJava.Short, expr => expr.callNullary("value")),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = TypesJava.Short,
            toTypo = (expr, target) => target.construct(expr)
          )
        ),
        fromTypoInArray = Some(
          CustomType.FromTypo(
            jdbcType = TypesJava.Short,
            fromTypo = (expr, _) => code"$expr.value()"
          )
        )
      )
    case _: LangScala =>
      CustomType(
        comment = "Short primitive",
        sqlType = "int2",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoShort")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), lang.Short)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.Integer,
          toTypo = (expr, target) => code"$target($expr.toShort)"
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.Integer,
          fromTypo = (expr, _) => code"$expr.value.toInt"
        ),
        toText = CustomType.Text(lang.Short, expr => expr.callNullary("value")),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = TypesJava.Short,
            toTypo = (expr, target) => target.construct(expr)
          )
        ),
        fromTypoInArray = Some(
          CustomType.FromTypo(
            jdbcType = TypesJava.Short,
            fromTypo = (expr, _) => code"$expr.value: ${TypesJava.Short}"
          )
        ),
        objBody = target => {
          val numericOfTarget = TypesScala.Numeric.of(target)

          val numericBody =
            code"""|new ${numericOfTarget} {
                   |  override def compare(x: $target, y: $target): ${TypesScala.Int} = ${TypesJava.Short}.compare(x.value, y.value)
                   |  override def plus(x: $target, y: $target): $target = $target((x.value + y.value).toShort)
                   |  override def minus(x: $target, y: $target): $target = $target((x.value - y.value).toShort)
                   |  override def times(x: $target, y: $target): $target = $target((x.value * y.value).toShort)
                   |  override def negate(x: $target): $target = $target((-x.value).toShort)
                   |  override def fromInt(x: Int): $target = $target(x.toShort)
                   |  override def toInt(x: $target): ${TypesScala.Int} = x.value.toInt
                   |  override def toLong(x: $target): ${TypesScala.Long} = x.value.toLong
                   |  override def toFloat(x: $target): ${TypesScala.Float} = x.value.toFloat
                   |  override def toDouble(x: $target): ${TypesScala.Double} = x.value.toDouble
                   |  def parseString(str: String): Option[$target] = str.toShortOption.map(s => $target(s: ${TypesJava.Short}))
                   |  locally{val _ = parseString("1")}
                   |}
                   |"""
          List(jvm.Given(Nil, jvm.Ident("numeric"), Nil, numericOfTarget, body = numericBody.stripMargin))
        }
      )
    case other => sys.error(s"Unsupported language: $other")
  }

  lazy val TypoUUID = CustomType(
    comment = "UUID",
    sqlType = "uuid",
    typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoUUID")),
    params = NonEmptyList(
      jvm.Param(jvm.Ident("value"), TypesJava.UUID)
    ),
    toTypo = CustomType.ToTypo(
      jdbcType = TypesJava.UUID,
      toTypo = (expr, target) => target.construct(expr)
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.UUID,
      fromTypo = (expr, _) => expr.callNullary("value")
    ),
    toText = CustomType.Text.string(expr => expr.callNullary("value").callNullary("toString")),
    objBody = target =>
      List(
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("str"), TypesJava.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"new $target(${TypesJava.UUID}.fromString(str))")
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("randomUUID"),
          params = Nil,
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = List(code"new $target(${TypesJava.UUID}.randomUUID())")
        )
      )
  )

  lazy val TypoVector = lang match {
    case LangJava =>
      CustomType(
        comment = "extension: https://github.com/pgvector/pgvector",
        sqlType = "vector",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoVector")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), jvm.Type.ArrayOf(TypesJava.Float))
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.PgArray,
          toTypo = (expr, target) => code"new $target((${TypesJava.Float}[]) $expr.getArray())"
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = jvm.Type.ArrayOf(TypesJava.Float),
          fromTypo = (expr, _) => code"$expr.value()"
        ),
        forbidArray = true,
        toText = CustomType.Text.string(expr => code""""[" + ${TypesJava.Arrays}.stream($expr.value()).map(${TypesJava.String}::valueOf).collect(${TypesJava.Collectors}.joining(",")) + "]"""")
      )
    case _: LangScala =>
      CustomType(
        comment = "extension: https://github.com/pgvector/pgvector",
        sqlType = "vector",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoVector")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), jvm.Type.ArrayOf(lang.Float))
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.PgArray,
          toTypo = (expr, target) => code"$target($expr.getArray.asInstanceOf[${jvm.Type.ArrayOf(TypesJava.Float)}].map(Float2float))"
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = jvm.Type.ArrayOf(TypesJava.Float),
          fromTypo = (expr, _) => code"$expr.value.map(x => x: ${TypesJava.Float})"
        ),
        forbidArray = true,
        toText = CustomType.Text.string(expr => code"""$expr.value.mkString("[", ",", "]")""")
      )
    case other => sys.error(s"Unsupported language: $other")
  }

  lazy val TypoXml = lang match {
    case LangJava =>
      CustomType(
        comment = "XML",
        sqlType = "xml",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoXml")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), TypesJava.String)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.PgSQLXML,
          toTypo = (expr, target) => target.construct(expr.callNullary("getString"))
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.String,
          fromTypo = (expr, _) => expr.callNullary("value")
        ),
        toText = CustomType.Text.string(expr => expr.callNullary("value").invoke("toString")),
        fromTypoInArray = Some(
          CustomType.FromTypo(
            jdbcType = TypesJava.PGobject,
            fromTypo = (expr, _) => code"""${TypesJava.TypoPGObjectHelper}.create("xml", $expr.value())"""
          )
        ),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = TypesJava.PGobject,
            toTypo = (expr, target) => target.construct(expr.callNullary("getValue"))
          )
        )
      )
    case _: LangScala =>
      CustomType(
        comment = "XML",
        sqlType = "xml",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoXml")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), TypesJava.String)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.PgSQLXML,
          toTypo = (expr, target) => target.construct(expr.callNullary("getString"))
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.String,
          fromTypo = (expr, _) => expr.callNullary("value")
        ),
        toText = CustomType.Text.string(expr => expr.callNullary("value").invoke("toString")),
        fromTypoInArray = Some(
          CustomType.FromTypo(
            jdbcType = TypesJava.PGobject,
            fromTypo = (expr, target) => {
              val obj = jvm.Ident("obj")
              code"""|{
                     |  val $obj = new $target()
                     |  $obj.setType("xml")
                     |  $obj.setValue($expr.value)
                     |  $obj
                     |}""".stripMargin
            }
          )
        ),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = TypesJava.PGobject,
            toTypo = (expr, target) => target.construct(expr.callNullary("getValue"))
          )
        )
      )
    case other => sys.error(s"Unsupported language: $other")
  }

  def obj(sqlType: String, name: String): CustomType = lang match {
    case LangJava =>
      CustomType(
        comment = s"$sqlType (via PGObject)",
        sqlType = sqlType,
        typoType = jvm.Type.Qualified(pkg / jvm.Ident(name)),
        params = NonEmptyList(jvm.Param(jvm.Ident("value"), TypesJava.String)),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.PGobject,
          toTypo = (expr, target) => target.construct(expr.callNullary("getValue"))
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.PGobject,
          fromTypo = (expr, _) => code"""${TypesJava.TypoPGObjectHelper}.create("$sqlType", $expr.value())"""
        ),
        toText = CustomType.Text.string(expr => expr.callNullary("value")),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = TypesJava.String,
            toTypo = (expr, target) => target.construct(expr)
          )
        )
      )
    case _: LangScala =>
      CustomType(
        comment = s"$sqlType (via PGObject)",
        sqlType = sqlType,
        typoType = jvm.Type.Qualified(pkg / jvm.Ident(name)),
        params = NonEmptyList(jvm.Param(jvm.Ident("value"), TypesJava.String)),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.PGobject,
          toTypo = (expr, target) => target.construct(expr.callNullary("getValue"))
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.PGobject,
          fromTypo = (expr, target) => {
            val obj = jvm.Ident("obj")
            code"""|{
                   |  val $obj = new $target()
                   |  $obj.setType("$sqlType")
                   |  $obj.setValue($expr.value)
                   |  $obj
                   |}""".stripMargin
          }
        ),
        toText = CustomType.Text.string(expr => expr.callNullary("value")),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = TypesJava.String,
            toTypo = (expr, target) => target.construct(expr)
          )
        )
      )
    case other => sys.error(s"Unsupported language: $other")
  }
  lazy val TypoJson = obj("json", "TypoJson")
  lazy val TypoJsonb = obj("jsonb", "TypoJsonb")
  lazy val TypoInet = obj("inet", "TypoInet").copy(toTypoInArray = None)
  lazy val TypoAclItem = obj("aclitem", "TypoAclItem").copy(toTypoInArray = None)
  lazy val TypoAnyArray = obj("anyarray", "TypoAnyArray")
  lazy val TypoInt2Vector = obj("int2vector", "TypoInt2Vector").copy(toTypoInArray = None).withComment(""". Valid syntax: `TypoInt2Vector("1 2 3")""")
  lazy val TypoOidVector = obj("oidvector", "TypoOidVector")
  lazy val TypoPgNodeTree = obj("pg_node_tree", "TypoPgNodeTree")
  lazy val TypoRecord = obj("record", "TypoRecord").copy(toTypoInArray = None)
  lazy val TypoRegclass = obj("regclass", "TypoRegclass")
  lazy val TypoRegconfig = obj("regconfig", "TypoRegconfig")
  lazy val TypoRegdictionary = obj("regdictionary", "TypoRegdictionary")
  lazy val TypoRegnamespace = obj("regnamespace", "TypoRegnamespace")
  lazy val TypoRegoper = obj("regoper", "TypoRegoper")
  lazy val TypoRegoperator = obj("regoperator", "TypoRegoperator")
  lazy val TypoRegproc = obj("regproc", "TypoRegproc")
  lazy val TypoRegprocedure = obj("regprocedure", "TypoRegprocedure")
  lazy val TypoRegrole = obj("regrole", "TypoRegrole")
  lazy val TypoRegtype = obj("regtype", "TypoRegtype")
  lazy val TypoXid = obj("xid", "TypoXid")

  val All: mutable.Map[jvm.Type, CustomType] =
    mutable.Map(
      List(
        TypoAclItem,
        TypoAnyArray,
        TypoBox,
        TypoBytea,
        TypoCircle,
        TypoHStore,
        TypoInet,
        TypoInet,
        TypoInt2Vector,
        TypoInterval,
        TypoJson,
        TypoJson,
        TypoJsonb,
        TypoJsonb,
        TypoLine,
        TypoLineSegment,
        TypoLocalDate,
        TypoLocalDateTime,
        TypoLocalTime,
        TypoMoney,
        TypoInstant,
        TypoOffsetTime,
        TypoOidVector,
        TypoPath,
        TypoPgNodeTree,
        TypoPoint,
        TypoPolygon,
        TypoRecord,
        TypoRegclass,
        TypoRegconfig,
        TypoRegdictionary,
        TypoRegnamespace,
        TypoRegoper,
        TypoRegoperator,
        TypoRegproc,
        TypoRegprocedure,
        TypoRegrole,
        TypoRegtype,
        TypoShort,
        TypoUUID,
        TypoVector,
        TypoXid,
        TypoXml
      ).map(ct => (ct.typoType, ct))*
    )

  def TypoUnknown(sqlType: String): CustomType = {
    val ct = CustomType(
      comment = "This is a type typo does not know how to handle yet. This falls back to casting to string and crossing fingers. Time to file an issue! :]",
      sqlType = sqlType,
      typoType = jvm.Type.Qualified(pkg / jvm.Ident(s"TypoUnknown${Naming.titleCase(sqlType)}")),
      params = NonEmptyList(jvm.Param(jvm.Ident("value"), TypesJava.String)),
      toTypo = CustomType.ToTypo(jdbcType = TypesJava.String, toTypo = (expr, target) => target.construct(expr)),
      fromTypo = CustomType.FromTypo(jdbcType = TypesJava.String, fromTypo = (expr, _) => expr.callNullary("value")),
      toText = CustomType.Text.string(expr => expr.callNullary("value").callNullary("toString"))
    )
    All(ct.typoType) = ct
    ct
  }
}
