package typr
package internal

import typr.internal.codegen.*

import scala.collection.mutable

class CustomTypes(pkg: jvm.QIdent, lang: Lang) {
  import jvm.Code.{CodeOps, TreeOps, TypeOps}
  import lang.prop

  // Helper to wrap nullable expressions with !! for Kotlin
  private def notNull(code: jvm.Code): jvm.Code = lang match {
    case _: codegen.LangKotlin => jvm.NotNull(code).code
    case _                     => code
  }

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
      // PGbox.point is a public field (@Nullable in Kotlin), PGpoint.x/y are public fields
      toTypo = (expr, target) => {
        val point = notNull(expr.select("point"))
        target.construct(
          point.arrayIndex(0).select("x"),
          point.arrayIndex(0).select("y"),
          point.arrayIndex(1).select("x"),
          point.arrayIndex(1).select("y")
        )
      }
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.PGbox,
      // TypoBox record/case class has x1, y1, x2, y2 fields
      fromTypo = (expr, target) =>
        target.construct(
          prop(expr, "x1"),
          prop(expr, "y1"),
          prop(expr, "x2"),
          prop(expr, "y2")
        )
    ),
    toText = CustomType.Text.string { expr =>
      val x1 = rt(prop(expr, "x1"))
      val y1 = rt(prop(expr, "y1"))
      val x2 = rt(prop(expr, "x2"))
      val y2 = rt(prop(expr, "y2"))
      lang.s(code"(($x1,$y1),($x2,$y2))")
    }
  )

  lazy val TypoBytea = lang match {
    case LangJava =>
      // Java records use Byte[] (boxed) for generics compatibility, but JDBC uses byte[] (primitive)
      val primitiveByteArray = jvm.Type.ArrayOf(TypesJava.BytePrimitive)
      CustomType(
        comment = "This represents the bytea datatype in PostgreSQL",
        sqlType = "bytea",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoBytea")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), jvm.Type.ArrayOf(lang.Byte)) // Record uses Byte[]
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = primitiveByteArray, // JDBC returns byte[]
          // JDBC returns byte[], but Java record uses Byte[], so we box it
          toTypo = (expr, target) => target.construct(code"${TypesJava.ByteArrays}.box($expr)")
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = primitiveByteArray, // JDBC expects byte[]
          // Java record uses Byte[], but JDBC expects byte[], so we unbox it
          fromTypo = (expr, _) => code"${TypesJava.ByteArrays}.unbox(${prop(expr, "value")})"
        ),
        forbidArray = true,
        toText = CustomType.Text(primitiveByteArray, expr => code"${TypesJava.ByteArrays}.unbox(${prop(expr, "value")})")
      )
    case _: LangKotlin =>
      // Kotlin's ByteArray is a primitive array type, equivalent to byte[] in Java
      CustomType(
        comment = "This represents the bytea datatype in PostgreSQL",
        sqlType = "bytea",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoBytea")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), TypesKotlin.ByteArray)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesKotlin.ByteArray,
          toTypo = (expr, target) => target.construct(expr)
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesKotlin.ByteArray,
          fromTypo = (expr, _) => prop(expr, "value")
        ),
        forbidArray = true,
        toText = CustomType.Text(TypesKotlin.ByteArray, expr => prop(expr, "value"))
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
          fromTypo = (expr, _) => prop(expr, "value")
        ),
        forbidArray = true,
        toText = CustomType.Text(byteArray, expr => prop(expr, "value"))
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
      // Use lang.String for Kotlin compatibility
      jdbcType = lang.String,
      toTypo = (expr, target) => jvm.New(target, List(jvm.Arg.Pos(code"${TypesJava.LocalDate}.parse($expr)")))
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = lang.String,
      fromTypo = (expr, _) => prop(expr, "value").invoke("toString")
    ),
    toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString")),
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
          body = jvm.Body.Expr(target.construct(code"${TypesJava.LocalDate}.now()")),
          isOverride = false,
          isDefault = false
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          // Use lang.String for Kotlin compatibility
          params = List(jvm.Param(jvm.Ident("str"), lang.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = jvm.Body.Expr(target.construct(code"${TypesJava.LocalDate}.parse(str)")),
          isOverride = false,
          isDefault = false
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
      // Use lang.String for Kotlin compatibility
      jdbcType = lang.String,
      toTypo = (expr, target) => jvm.New(target, List(jvm.Arg.Pos(code"${TypesJava.LocalTime}.parse($expr)")))
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = lang.String,
      fromTypo = (expr, _) => prop(expr, "value").invoke("toString")
    ),
    toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString")),
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
          body = jvm.Body.Expr(target.construct(code"value.truncatedTo(${TypesJava.ChronoUnit}.MICROS)")),
          isOverride = false,
          isDefault = false
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          // Use lang.String for Kotlin compatibility
          params = List(jvm.Param(jvm.Ident("str"), lang.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = jvm.Body.Expr(target.code.invoke("apply", TypesJava.LocalTime.code.invoke("parse", code"str"))),
          isOverride = false,
          isDefault = false
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
          body = jvm.Body.Expr(code"$target.apply(${TypesJava.LocalTime}.now())"),
          isOverride = false,
          isDefault = false
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
      // Use lang.String for Kotlin compatibility
      // Use apply(String) to handle both JDBC (space-delimited) and JSON (ISO with 'T') formats
      jdbcType = lang.String,
      toTypo = (expr, target) => code"$target.apply($expr)"
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = lang.String,
      fromTypo = (expr, _) => prop(expr, "value").invoke("toString")
    ),
    toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString")),
    objBody = target =>
      List(
        // Parser for JDBC format (space-delimited)
        jvm.Value(
          annotations = Nil,
          name = jvm.Ident("parser"),
          tpe = TypesJava.DateTimeFormatter,
          body =
            Some(code"""${TypesJava.DateTimeFormatterBuilder.construct()}.appendPattern("yyyy-MM-dd HH:mm:ss").appendFraction(${TypesJava.ChronoField}.MICRO_OF_SECOND, 0, 6, true).toFormatter()"""),
          isLazy = false,
          isOverride = false
        ),
        // Parser for JSON format (ISO with 'T' delimiter)
        jvm.Value(
          annotations = Nil,
          name = jvm.Ident("jsonParser"),
          tpe = TypesJava.DateTimeFormatter,
          body =
            Some(code"""${TypesJava.DateTimeFormatterBuilder.construct()}.appendPattern("yyyy-MM-dd'T'HH:mm:ss").appendFraction(${TypesJava.ChronoField}.MICRO_OF_SECOND, 0, 6, true).toFormatter()"""),
          isLazy = false,
          isOverride = false
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
          body = jvm.Body.Expr(target.construct(code"value.truncatedTo(${TypesJava.ChronoUnit}.MICROS)")),
          isOverride = false,
          isDefault = false
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          // Use lang.String for Kotlin compatibility
          params = List(jvm.Param(jvm.Ident("str"), lang.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          // Parse both formats: ISO with 'T' or space-delimited
          body = jvm.Body.Expr {
            val chosenParser = lang.ternary(code"""str.contains("T")""", code"jsonParser", code"parser")
            code"$target.apply(${TypesJava.LocalDateTime}.parse(str, $chosenParser))"
          },
          isOverride = false,
          isDefault = false
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
          body = jvm.Body.Expr(code"$target.apply(${TypesJava.LocalDateTime}.now())"),
          isOverride = false,
          isDefault = false
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
      // Use lang.String for Kotlin compatibility (bimap expects kotlin.String)
      jdbcType = lang.String,
      toTypo = (expr, _) => code"apply($expr)"
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = lang.String,
      fromTypo = (expr, _) => prop(expr, "value").invoke("toString")
    ),
    toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString")),
    objBody = target =>
      List(
        // Parser for JDBC format (space-delimited with timezone)
        jvm.Value(
          annotations = Nil,
          name = jvm.Ident("parser"),
          tpe = TypesJava.DateTimeFormatter,
          body = Some(
            code"""${TypesJava.DateTimeFormatterBuilder
                .construct()}.appendPattern("yyyy-MM-dd HH:mm:ss").appendFraction(${TypesJava.ChronoField}.MICRO_OF_SECOND, 0, 6, true).appendPattern("X").toFormatter()"""
          ),
          isLazy = false,
          isOverride = false
        ),
        // Parser for JSON format (ISO with 'T' delimiter and optional Z suffix)
        jvm.Value(
          annotations = Nil,
          name = jvm.Ident("jsonParser"),
          tpe = TypesJava.DateTimeFormatter,
          body = Some(
            code"""${TypesJava.DateTimeFormatterBuilder
                .construct()}.appendPattern("yyyy-MM-dd'T'HH:mm:ss").appendFraction(${TypesJava.ChronoField}.MICRO_OF_SECOND, 0, 6, true).appendPattern("X").toFormatter()"""
          ),
          isLazy = false,
          isOverride = false
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
          body = jvm.Body.Expr(target.construct(code"value.truncatedTo(${TypesJava.ChronoUnit}.MICROS)")),
          isOverride = false,
          isDefault = false
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          // Use lang.String for Kotlin compatibility
          params = List(jvm.Param(jvm.Ident("str"), lang.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          // Parse both formats: ISO with 'T' or space-delimited
          body = jvm.Body.Expr {
            val chosenParser = lang.ternary(code"""str.contains("T")""", code"jsonParser", code"parser")
            code"$target.apply(${TypesJava.OffsetDateTime}.parse(str, $chosenParser).toInstant())"
          },
          isOverride = false,
          isDefault = false
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
          body = jvm.Body.Expr(code"$target.apply(${TypesJava.Instant}.now())"),
          isOverride = false,
          isDefault = false
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
      // Use lang.String for Kotlin compatibility
      jdbcType = lang.String,
      toTypo = (expr, target) => target.construct(code"${TypesJava.OffsetTime}.parse($expr, parser)")
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = lang.String,
      fromTypo = (expr, _) => prop(expr, "value").invoke("toString")
    ),
    toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString")),
    objBody = target =>
      List(
        jvm.Value(
          annotations = Nil,
          name = jvm.Ident("parser"),
          tpe = TypesJava.DateTimeFormatter,
          body = Some(
            code"""${TypesJava.DateTimeFormatterBuilder.construct()}.appendPattern("HH:mm:ss").appendFraction(${TypesJava.ChronoField}.MICRO_OF_SECOND, 0, 6, true).appendPattern("X").toFormatter()"""
          ),
          isLazy = false,
          isOverride = false
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
          body = jvm.Body.Expr(target.construct(code"value.truncatedTo(${TypesJava.ChronoUnit}.MICROS)")),
          isOverride = false,
          isDefault = false
        ),
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          // Use lang.String for Kotlin compatibility
          params = List(jvm.Param(jvm.Ident("str"), lang.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = jvm.Body.Expr(code"$target.apply(${TypesJava.OffsetTime}.parse(str, parser))"),
          isOverride = false,
          isDefault = false
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
          body = jvm.Body.Expr(code"$target.apply(${TypesJava.OffsetTime}.now())"),
          isOverride = false,
          isDefault = false
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
      toTypo = (expr, target) => {
        val center = notNull(expr.select("center"))
        target.construct(
          TypoPoint.typoType.construct(center.select("x"), center.select("y")),
          expr.select("radius")
        )
      }
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.PGcircle,
      fromTypo = (expr, target) => target.construct(prop(prop(expr, "center"), "x"), prop(prop(expr, "center"), "y"), prop(expr, "radius"))
    ),
    toText = CustomType.Text.string { expr =>
      val x = rt(prop(prop(expr, "center"), "x"))
      val y = rt(prop(prop(expr, "center"), "y"))
      val radius = rt(prop(expr, "radius"))
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
      fromTypo = (expr, target) => target.construct(prop(expr, "a"), prop(expr, "b"), prop(expr, "c"))
    ),
    toText = CustomType.Text.string { expr =>
      val a = rt(prop(expr, "a"))
      val b = rt(prop(expr, "b"))
      val c = rt(prop(expr, "c"))
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
      toTypo = (expr, target) => {
        val point = notNull(expr.select("point"))
        target.construct(
          TypoPoint.typoType.construct(point.arrayIndex(0).select("x"), point.arrayIndex(0).select("y")),
          TypoPoint.typoType.construct(point.arrayIndex(1).select("x"), point.arrayIndex(1).select("y"))
        )
      }
    ),
    fromTypo = CustomType.FromTypo(
      jdbcType = TypesJava.PGlseg,
      // TypoLineSegment has p1/p2 properties returning TypoPoint, TypoPoint has x/y properties
      fromTypo = (expr, target) =>
        target.construct(
          TypesJava.PGpoint.construct(prop(prop(expr, "p1"), "x"), prop(prop(expr, "p1"), "y")),
          TypesJava.PGpoint.construct(prop(prop(expr, "p2"), "x"), prop(prop(expr, "p2"), "y"))
        )
    ),
    toText = CustomType.Text.string { expr =>
      val p1x = rt(prop(prop(expr, "p1"), "x"))
      val p1y = rt(prop(prop(expr, "p1"), "y"))
      val p2x = rt(prop(prop(expr, "p2"), "x"))
      val p2y = rt(prop(prop(expr, "p2"), "y"))
      lang.s(code"(($p1x,$p1y),($p2x,$p2y))")
    }
  )

  lazy val TypoPath = {
    val p = jvm.Ident("p")
    // For PGpoint (external Java type), x and y are public fields - use direct field access
    val toTypoPointLambda = jvm.Lambda(p, TypoPoint.typoType.construct(code"${p.code}.x", code"${p.code}.y"))
    // For TypoPoint (our generated type), use prop for property access (Java records need method call syntax)
    val toPGpointLambda = jvm.Lambda(p, TypesJava.PGpoint.construct(prop(p.code, "x"), prop(p.code, "y")))
    val px = rt(prop(p.code, "x"))
    val py = rt(prop(p.code, "y"))
    val toStringLambda = jvm.Lambda(p, lang.s(code"$px, $py").code)
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
        toTypo = (expr, target) => {
          // PGpath.points is nullable in Kotlin, wrap with notNull for safe access
          val points = notNull(expr.select("points"))
          target.construct(
            expr.callNullary("isOpen"),
            lang.ListType.arrayMapToList(points, toTypoPointLambda.code)
          )
        }
      ),
      fromTypo = CustomType.FromTypo(
        jdbcType = TypesJava.PGpath,
        fromTypo = (expr, target) => {
          val arrayGen = lang.newArray(TypesJava.PGpoint, None)
          val pointsArray = lang.ListType.listMapToArray(prop(expr, "points"), toPGpointLambda.code, arrayGen)
          target.construct(pointsArray, prop(expr, "open"))
        }
      ),
      toText = CustomType.Text.string { expr =>
        val openChar = lang.ternary(prop(expr, "open"), code""""["""", code""""("""")
        val closeChar = lang.ternary(prop(expr, "open"), code""""]"""", code"""")"""")
        val pointsStr = lang.ListType.mapJoinString(prop(expr, "points"), lang.renderTree(toStringLambda, lang.Ctx.Empty), ",")
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
      fromTypo = (expr, target) => target.construct(prop(expr, "x"), prop(expr, "y"))
    ),
    toText = CustomType.Text.string { expr =>
      val x = rt(prop(expr, "x"))
      val y = rt(prop(expr, "y"))
      lang.s(code"($x,$y)")
    }
  )

  lazy val TypoPolygon = {
    val p = jvm.Ident("p")
    // For PGpoint (external Java type), x and y are public fields - use direct field access
    val toTypoPointLambda = jvm.Lambda(p, TypoPoint.typoType.construct(code"${p.code}.x", code"${p.code}.y"))
    // For TypoPoint (our generated type), use prop for property access (Java records need method call syntax)
    val toPGpointLambda = jvm.Lambda(p, TypesJava.PGpoint.construct(prop(p.code, "x"), prop(p.code, "y")))
    val px = rt(prop(p.code, "x"))
    val py = rt(prop(p.code, "y"))
    val toStringLambda = jvm.Lambda(p, lang.s(code"$px, $py").code)
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
          // PGpolygon.points is nullable in Kotlin, wrap with notNull for safe access
          val points = notNull(expr.select("points"))
          val arrayMapToList = lang.ListType.arrayMapToList(points, toTypoPointLambda.code)
          target.construct(arrayMapToList)
        }
      ),
      fromTypo = CustomType.FromTypo(
        jdbcType = TypesJava.PGpolygon,
        fromTypo = (expr, target) => {
          val arrayGen = lang.newArray(TypesJava.PGpoint, None)
          val pointsArray = lang.ListType.listMapToArray(prop(expr, "points"), toPGpointLambda.code, arrayGen)
          target.construct(pointsArray)
        }
      ),
      toText = CustomType.Text.string { expr =>
        val pointsStr = lang.ListType.mapJoinString(prop(expr, "points"), lang.renderTree(toStringLambda, lang.Ctx.Empty), ",")
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
        val years = prop(expr, "years")
        val months = prop(expr, "months")
        val days = prop(expr, "days")
        val hours = prop(expr, "hours")
        val minutes = prop(expr, "minutes")
        val seconds = prop(expr, "seconds")
        target.construct(years, months, days, hours, minutes, seconds)
      }
    ),
    toText = CustomType.Text.string { expr =>
      val years = rt(prop(expr, "years"))
      val months = rt(prop(expr, "months"))
      val days = rt(prop(expr, "days"))
      val hours = rt(prop(expr, "hours"))
      val minutes = rt(prop(expr, "minutes"))
      val seconds = rt(prop(expr, "seconds"))
      lang.s(code"P${years}Y${months}M${days}DT${hours}H${minutes}M${seconds}S")
    }
  )

  lazy val TypoHStore = lang match {
    case LangJava =>
      // Java lambdas can't use wildcard types (Map<?, ?>) as parameter types,
      // so we use the raw Map type for the jdbcType
      CustomType(
        comment = "The text representation of an hstore, used for input and output, includes zero or more key => value pairs separated by commas",
        sqlType = "hstore",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoHStore")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), TypesJava.Map.of(TypesJava.String, TypesJava.String))
        ),
        toTypo = CustomType.ToTypo(
          // Use raw Map type because Java lambdas can't have Map<?, ?> as parameter type
          jdbcType = TypesJava.Map,
          toTypo = (expr, target) => target.construct(lang.MapOps.castMap(expr, TypesJava.String, TypesJava.String))
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.Map.of(TypesJava.String, TypesJava.String),
          fromTypo = (expr, _) => TypesJava.HashMap.of(TypesJava.String, TypesJava.String).construct(prop(expr, "value"))
        ),
        forbidArray = true,
        toText = CustomType.Text.string { expr =>
          lang.MapOps.mkStringKV(prop(expr, "value"), " => ", ",")
        },
        dbTypeAnnotations = List(jvm.Annotation(jvm.Type.Qualified("java.lang.SuppressWarnings"), List(jvm.Annotation.Arg.Positional(jvm.StrLit("unchecked").code))))
      )
    case _: LangKotlin =>
      // In Kotlin, use kotlin.collections.Map with kotlin.String for native Kotlin interop
      val kotlinMap = TypesKotlin.Map.of(lang.String, lang.String)
      CustomType(
        comment = "The text representation of an hstore, used for input and output, includes zero or more key => value pairs separated by commas",
        sqlType = "hstore",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoHStore")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), kotlinMap)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.Map.of(jvm.Type.Wildcard, jvm.Type.Wildcard),
          toTypo = (expr, target) => target.construct(lang.MapOps.castMap(expr, lang.String, lang.String))
        ),
        fromTypo = CustomType.FromTypo(
          // Use Map<*, *> as the write type since Kotlin won't coerce HashMap to Map<*, *>
          jdbcType = TypesJava.Map.of(jvm.Type.Wildcard, jvm.Type.Wildcard),
          // Cast to Map<*, *> to satisfy the type checker
          fromTypo = (expr, _) => code"${prop(expr, "value")} as ${TypesJava.Map}<*, *>"
        ),
        forbidArray = true,
        toText = CustomType.Text.string { expr =>
          lang.MapOps.mkStringKV(prop(expr, "value"), " => ", ",")
        },
        dbTypeAnnotations = List(jvm.Annotation(jvm.Type.Qualified("kotlin.Suppress"), List(jvm.Annotation.Arg.Positional(jvm.StrLit("UNCHECKED_CAST").code))))
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
            (expr, _) => prop(expr, "value").callNullary("bigDecimal")
          else
            (expr, _) => prop(expr, "value")
      ),
      toText = CustomType.Text(bigDecimalType, expr => prop(expr, "value"))
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
          toTypo = (expr, target) => target.construct(code"$expr.shortValue()")
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.Integer,
          fromTypo = (expr, _) => code"(int) ${prop(expr, "value")}"
        ),
        toText = CustomType.Text(TypesJava.Short, expr => prop(expr, "value")),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = TypesJava.Short,
            toTypo = (expr, target) => target.construct(expr)
          )
        ),
        fromTypoInArray = Some(
          CustomType.FromTypo(
            jdbcType = TypesJava.Short,
            fromTypo = (expr, _) => prop(expr, "value")
          )
        )
      )
    case _: LangKotlin =>
      CustomType(
        comment = "Short primitive",
        sqlType = "int2",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoShort")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), lang.Short)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.Integer,
          // Kotlin uses .toShort() instead of .shortValue()
          toTypo = (expr, target) => target.construct(code"$expr.toShort()")
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.Integer,
          // Kotlin uses .toInt() instead of (int) cast
          fromTypo = (expr, _) => code"${prop(expr, "value")}.toInt()"
        ),
        toText = CustomType.Text(lang.Short, expr => prop(expr, "value")),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = lang.Short,
            toTypo = (expr, target) => target.construct(expr)
          )
        ),
        fromTypoInArray = Some(
          CustomType.FromTypo(
            jdbcType = lang.Short,
            fromTypo = (expr, _) => prop(expr, "value")
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
        toText = CustomType.Text(lang.Short, expr => prop(expr, "value")),
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
      fromTypo = (expr, _) => prop(expr, "value")
    ),
    toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString")),
    objBody = target =>
      List(
        jvm.Method(
          Nil,
          comments = jvm.Comments.Empty,
          tparams = Nil,
          name = jvm.Ident("apply"),
          params = List(jvm.Param(jvm.Ident("str"), lang.String)),
          implicitParams = Nil,
          tpe = target,
          throws = Nil,
          body = jvm.Body.Expr(target.construct(code"${TypesJava.UUID}.fromString(str)")),
          isOverride = false,
          isDefault = false
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
          body = jvm.Body.Expr(target.construct(code"${TypesJava.UUID}.randomUUID()")),
          isOverride = false,
          isDefault = false
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
          toTypo = (expr, target) => target.construct(code"(${TypesJava.Float}[]) $expr.getArray()")
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = jvm.Type.ArrayOf(TypesJava.Float),
          fromTypo = (expr, _) => prop(expr, "value")
        ),
        forbidArray = true,
        toText =
          CustomType.Text.string(expr => code""""[" + ${TypesJava.Arrays}.stream(${prop(expr, "value")}).map(${TypesJava.String}::valueOf).collect(${TypesJava.Collectors}.joining(",")) + "]"""")
      )
    case _: LangKotlin =>
      val floatArray = jvm.Type.ArrayOf(TypesJava.Float)
      CustomType(
        comment = "extension: https://github.com/pgvector/pgvector",
        sqlType = "vector",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoVector")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), floatArray)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.PgArray,
          // Kotlin cast syntax: expr as Type
          toTypo = (expr, target) => target.construct(code"$expr.getArray() as $floatArray")
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = floatArray,
          fromTypo = (expr, _) => prop(expr, "value")
        ),
        forbidArray = true,
        toText =
          // In Kotlin, use joinToString with explicit lambda instead of Java method references
          CustomType.Text.string(expr => code""""[" + ${prop(expr, "value")}.joinToString(",") { it.toString() } + "]"""")
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
          fromTypo = (expr, _) => prop(expr, "value")
        ),
        toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString")),
        fromTypoInArray = Some(
          CustomType.FromTypo(
            jdbcType = TypesJava.PGobject,
            fromTypo = (expr, _) => code"""${TypesJava.TypoPGObjectHelper}.create("xml", ${prop(expr, "value")})"""
          )
        ),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = TypesJava.PGobject,
            toTypo = (expr, target) => target.construct(expr.callNullary("getValue"))
          )
        )
      )
    case _: LangKotlin =>
      CustomType(
        comment = "XML",
        sqlType = "xml",
        typoType = jvm.Type.Qualified(pkg / jvm.Ident("TypoXml")),
        params = NonEmptyList(
          jvm.Param(jvm.Ident("value"), lang.String)
        ),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.PgSQLXML,
          // getString() returns nullable in Kotlin, wrap with notNull
          toTypo = (expr, target) => target.construct(notNull(expr.callNullary("getString")))
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = lang.String,
          fromTypo = (expr, _) => prop(expr, "value")
        ),
        toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString")),
        fromTypoInArray = Some(
          CustomType.FromTypo(
            jdbcType = TypesJava.PGobject,
            fromTypo = (expr, _) => code"""${TypesJava.TypoPGObjectHelper}.create("xml", ${prop(expr, "value")})"""
          )
        ),
        toTypoInArray = Some(
          CustomType.ToTypo(
            jdbcType = TypesJava.PGobject,
            // getValue() returns nullable in Kotlin, wrap with notNull
            toTypo = (expr, target) => target.construct(notNull(expr.callNullary("getValue")))
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
          fromTypo = (expr, _) => prop(expr, "value")
        ),
        toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString")),
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
    case LangJava | _: LangKotlin =>
      CustomType(
        comment = s"$sqlType (via PGObject)",
        sqlType = sqlType,
        typoType = jvm.Type.Qualified(pkg / jvm.Ident(name)),
        // Use lang.String for Kotlin compatibility (kotlin.String vs java.lang.String)
        params = NonEmptyList(jvm.Param(jvm.Ident("value"), lang.String)),
        toTypo = CustomType.ToTypo(
          jdbcType = TypesJava.PGobject,
          // getValue() returns String? in Kotlin, need notNull for safe access
          toTypo = (expr, target) => target.construct(notNull(expr.callNullary("getValue")))
        ),
        fromTypo = CustomType.FromTypo(
          jdbcType = TypesJava.PGobject,
          fromTypo = (expr, _) => code"""${TypesJava.TypoPGObjectHelper}.create("$sqlType", ${prop(expr, "value")})"""
        ),
        toText = CustomType.Text.string(expr => prop(expr, "value")),
        toTypoInArray = Some(
          CustomType.ToTypo(
            // Use lang.String for Kotlin array elements
            jdbcType = lang.String,
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
        toText = CustomType.Text.string(expr => prop(expr, "value")),
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
  lazy val TypoCidr = obj("cidr", "TypoCidr").copy(toTypoInArray = None)
  lazy val TypoMacAddr = obj("macaddr", "TypoMacAddr").copy(toTypoInArray = None)
  lazy val TypoMacAddr8 = obj("macaddr8", "TypoMacAddr8").copy(toTypoInArray = None)
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
        TypoCidr,
        TypoMacAddr,
        TypoMacAddr8,
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
      params = NonEmptyList(jvm.Param(jvm.Ident("value"), lang.String)),
      toTypo = CustomType.ToTypo(jdbcType = lang.String, toTypo = (expr, target) => target.construct(expr)),
      fromTypo = CustomType.FromTypo(jdbcType = lang.String, fromTypo = (expr, _) => prop(expr, "value")),
      toText = CustomType.Text.string(expr => prop(expr, "value").invoke("toString"))
    )
    All(ct.typoType) = ct
    ct
  }
}
