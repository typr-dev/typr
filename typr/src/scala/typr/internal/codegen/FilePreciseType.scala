package typr
package internal
package codegen

import typr.TypeSupportScala

object FilePreciseType {

  def forStringN(
      typoType: jvm.Type.Qualified,
      maxLength: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, lang.String)

    val staticMethods = List(
      ofMethodString(typoType, maxLength, hasNonEmpty = false, lang),
      unsafeForceMethodString(typoType, maxLength, hasNonEmpty = false, lang),
      truncateMethod(typoType, maxLength, lang)
    )

    val instanceMethods = List(
      rawValueMethod(lang.String),
      maxLengthMethod(maxLength, lang),
      semanticEqualsMethodStringValue(FoundationsTypes.precise.StringN, lang),
      semanticHashCodeMethodStringValue(lang),
      equalsMethodStringValue(FoundationsTypes.precise.StringN, lang),
      hashCodeMethodStringValue(lang)
    )

    mkFile(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, List(FoundationsTypes.precise.StringN), options, lang)
  }

  def forPaddedStringN(
      typoType: jvm.Type.Qualified,
      length: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, lang.String)

    val staticMethods = List(
      ofMethodPaddedString(typoType, length, lang),
      unsafeForceMethodPaddedString(typoType, length, lang)
    )

    val instanceMethods = List(
      rawValueMethod(lang.String),
      lengthMethod(length, lang),
      trimmedMethod(lang),
      semanticEqualsMethodPaddedString(FoundationsTypes.precise.PaddedStringN, lang),
      semanticHashCodeMethodPaddedString(lang),
      equalsMethodPaddedString(FoundationsTypes.precise.PaddedStringN, lang),
      hashCodeMethodPaddedString(lang)
    )

    mkFile(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, List(FoundationsTypes.precise.PaddedStringN), options, lang)
  }

  def forNonEmptyPaddedStringN(
      typoType: jvm.Type.Qualified,
      length: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, lang.String)

    val staticMethods = List(
      ofMethodNonEmptyPaddedString(typoType, length, lang),
      unsafeForceMethodNonEmptyPaddedString(typoType, length, lang)
    )

    val instanceMethods = List(
      rawValueMethod(lang.String),
      lengthMethod(length, lang),
      trimmedMethod(lang),
      semanticEqualsMethodPaddedString(FoundationsTypes.precise.NonEmptyPaddedStringN, lang),
      semanticHashCodeMethodPaddedString(lang),
      equalsMethodPaddedString(FoundationsTypes.precise.NonEmptyPaddedStringN, lang),
      hashCodeMethodPaddedString(lang)
    )

    mkFile(
      typoType,
      valueParam,
      underlyingDbType,
      staticMethods,
      instanceMethods,
      List(FoundationsTypes.precise.NonEmptyPaddedStringN),
      options,
      lang
    )
  }

  def forNonEmptyString(
      typoType: jvm.Type.Qualified,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, lang.String)

    val staticMethods = List(
      ofMethodNonEmptyString(typoType, lang),
      unsafeForceMethodNonEmptyString(typoType, lang)
    )

    mkFile(typoType, valueParam, underlyingDbType, staticMethods, Nil, Nil, options, lang)
  }

  def forNonEmptyStringN(
      typoType: jvm.Type.Qualified,
      maxLength: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, lang.String)

    val staticMethods = List(
      ofMethodString(typoType, maxLength, hasNonEmpty = true, lang),
      unsafeForceMethodString(typoType, maxLength, hasNonEmpty = true, lang),
      truncateMethod(typoType, maxLength, lang)
    )

    val instanceMethods = List(
      rawValueMethod(lang.String),
      maxLengthMethod(maxLength, lang),
      semanticEqualsMethodStringValue(FoundationsTypes.precise.NonEmptyStringN, lang),
      semanticHashCodeMethodStringValue(lang),
      equalsMethodStringValue(FoundationsTypes.precise.NonEmptyStringN, lang),
      hashCodeMethodStringValue(lang)
    )

    mkFile(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, List(FoundationsTypes.precise.NonEmptyStringN), options, lang)
  }

  def forBinaryN(
      typoType: jvm.Type.Qualified,
      maxLength: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, lang.ByteArray)

    val staticMethods = List(
      ofMethodBinary(typoType, maxLength, lang),
      unsafeForceMethodBinary(typoType, maxLength, lang)
    )

    val instanceMethods = List(
      rawValueMethod(lang.ByteArray),
      maxLengthMethod(maxLength, lang),
      semanticEqualsMethodBinary(lang),
      semanticHashCodeMethodBinary(lang),
      equalsMethodBinary(lang),
      hashCodeMethodBinary(lang)
    )

    mkFile(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, List(FoundationsTypes.precise.BinaryN), options, lang)
  }

  def forDecimalN(
      typoType: jvm.Type.Qualified,
      precision: Int,
      scale: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val underlyingType = if (scale == 0) lang.typeSupport.BigInteger else lang.BigDecimal
    val valueParam = jvm.Param(value, underlyingType)

    val zeroConst = zeroConstantDecimal(typoType, scale, lang)

    val staticMethods = List(
      Some(ofMethodDecimal(typoType, precision, scale, lang)),
      Some(unsafeForceMethodDecimal(typoType, precision, scale, lang)),
      ofMethodDecimalFromInt(typoType, precision, scale, lang),
      ofMethodDecimalFromLong(typoType, precision, scale, lang),
      ofMethodDecimalFromDouble(typoType, precision, scale, lang)
    ).flatten

    val instanceMethods = List(
      decimalValueMethod(scale, lang),
      precisionMethod(precision, lang),
      scaleMethod(scale, lang),
      semanticEqualsMethodDecimal(lang),
      semanticHashCodeMethodDecimal(lang),
      equalsMethodDecimal(lang),
      hashCodeMethodDecimal(lang)
    )

    // For scale=0 (BigInteger), we need custom db instances that convert BigDecimal <-> BigInteger
    // since the underlying db type uses BigDecimal but our wrapper uses BigInteger
    if (scale == 0) {
      mkFileWithScale0Instances(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, zeroConst, options, lang)
    } else {
      // For scale>0 in Scala with Scala types, we need custom instances for arrays because DuckDbTypes.decimalArray
      // uses java.math.BigDecimal but the wrapper uses scala.BigDecimal
      lang match {
        case langScala: LangScala if langScala.typeSupport == TypeSupportScala =>
          mkFileWithScalaDecimalInstances(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, zeroConst, options, lang)
        case _ =>
          mkFileWithZeroConst(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, zeroConst, options, lang)
      }
    }
  }

  def forLocalDateTimeN(
      typoType: jvm.Type.Qualified,
      fsp: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, TypesJava.LocalDateTime)

    val staticMethods = List(
      ofMethodDateTime(typoType, TypesJava.LocalDateTime, fsp, lang),
      nowMethodDateTime(typoType, TypesJava.LocalDateTime, fsp, lang)
    )

    val instanceMethods = List(
      rawValueMethod(TypesJava.LocalDateTime),
      fractionalSecondsPrecisionMethod(fsp, lang),
      semanticEqualsMethodTemporal(FoundationsTypes.precise.LocalDateTimeN, lang),
      semanticHashCodeMethodTemporal(lang),
      equalsMethodTemporal(FoundationsTypes.precise.LocalDateTimeN, lang),
      hashCodeMethodTemporal(lang)
    )

    mkFile(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, List(FoundationsTypes.precise.LocalDateTimeN), options, lang)
  }

  def forInstantN(
      typoType: jvm.Type.Qualified,
      fsp: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, TypesJava.Instant)

    val staticMethods = List(
      ofMethodInstant(typoType, fsp, lang),
      nowMethodInstant(typoType, fsp, lang)
    )

    val instanceMethods = List(
      rawValueMethod(TypesJava.Instant),
      fractionalSecondsPrecisionMethod(fsp, lang),
      semanticEqualsMethodTemporal(FoundationsTypes.precise.InstantN, lang),
      semanticHashCodeMethodTemporal(lang),
      equalsMethodTemporal(FoundationsTypes.precise.InstantN, lang),
      hashCodeMethodTemporal(lang)
    )

    mkFile(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, List(FoundationsTypes.precise.InstantN), options, lang)
  }

  def forLocalTimeN(
      typoType: jvm.Type.Qualified,
      fsp: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, TypesJava.LocalTime)

    val staticMethods = List(
      ofMethodDateTime(typoType, TypesJava.LocalTime, fsp, lang),
      nowMethodDateTime(typoType, TypesJava.LocalTime, fsp, lang)
    )

    val instanceMethods = List(
      rawValueMethod(TypesJava.LocalTime),
      fractionalSecondsPrecisionMethod(fsp, lang),
      semanticEqualsMethodTemporal(FoundationsTypes.precise.LocalTimeN, lang),
      semanticHashCodeMethodTemporal(lang),
      equalsMethodTemporal(FoundationsTypes.precise.LocalTimeN, lang),
      hashCodeMethodTemporal(lang)
    )

    mkFile(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, List(FoundationsTypes.precise.LocalTimeN), options, lang)
  }

  def forOffsetDateTimeN(
      typoType: jvm.Type.Qualified,
      fsp: Int,
      underlyingDbType: db.Type,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val valueParam = jvm.Param(value, TypesJava.OffsetDateTime)

    val staticMethods = List(
      ofMethodDateTime(typoType, TypesJava.OffsetDateTime, fsp, lang),
      nowMethodOffsetDateTime(typoType, fsp, lang)
    )

    val instanceMethods = List(
      rawValueMethod(TypesJava.OffsetDateTime),
      fractionalSecondsPrecisionMethod(fsp, lang),
      semanticEqualsMethodTemporal(FoundationsTypes.precise.OffsetDateTimeN, lang),
      semanticHashCodeMethodTemporal(lang),
      equalsMethodTemporal(FoundationsTypes.precise.OffsetDateTimeN, lang),
      hashCodeMethodTemporal(lang)
    )

    mkFile(typoType, valueParam, underlyingDbType, staticMethods, instanceMethods, List(FoundationsTypes.precise.OffsetDateTimeN), options, lang)
  }

  private def mkFile(
      typoType: jvm.Type.Qualified,
      valueParam: jvm.Param[jvm.Type],
      underlyingDbType: db.Type,
      staticMethods: List[jvm.Method],
      instanceMethods: List[jvm.Method],
      implementsInterfaces: List[jvm.Type.Qualified],
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = valueParam.name

    val bijection =
      if (options.enableDsl)
        Some {
          val thisBijection = lang.dsl.Bijection.of(typoType, valueParam.tpe)
          val expr = lang.bijection(typoType, valueParam.tpe, jvm.FieldGetterRef(typoType, value), jvm.ConstructorMethodRef(typoType))
          jvm.Given(Nil, jvm.Ident("bijection"), Nil, thisBijection, expr)
        }
      else None

    val jsonInstances = options.jsonLibs.map(_.wrapperTypeInstances(wrapperType = typoType, fieldName = value, underlying = valueParam.tpe))
    val instances = List(
      bijection.toList,
      jsonInstances.flatMap(_.givens),
      options.dbLib.toList.flatMap(
        _.wrapperTypeInstances(
          wrapperType = typoType,
          underlyingJvmType = valueParam.tpe,
          underlyingDbType = underlyingDbType,
          overrideDbType = None
        )
      )
    ).flatten

    val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
    val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

    val paramsWithAnnotations = List(valueParam).map { p =>
      fieldAnnotations.get(p.name) match {
        case Some(anns) => p.copy(annotations = p.annotations ++ anns)
        case None       => p
      }
    }

    val cls = jvm.Adt.Record(
      annotations = typeAnnotations,
      constructorAnnotations = Nil,
      isWrapper = true,
      privateConstructor = true,
      comments = jvm.Comments.Empty,
      name = typoType,
      tparams = Nil,
      params = paramsWithAnnotations,
      implicitParams = Nil,
      `extends` = None,
      implements = implementsInterfaces,
      members = instanceMethods,
      staticMembers = staticMethods ++ instances
    )

    jvm.File(typoType, cls, secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Simple version that adds a Zero constant */
  private def mkFileWithZeroConst(
      typoType: jvm.Type.Qualified,
      valueParam: jvm.Param[jvm.Type],
      underlyingDbType: db.Type,
      staticMethods: List[jvm.Method],
      instanceMethods: List[jvm.Method],
      zeroConst: jvm.Given,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = valueParam.name

    val bijection =
      if (options.enableDsl)
        Some {
          val thisBijection = lang.dsl.Bijection.of(typoType, valueParam.tpe)
          val expr = lang.bijection(typoType, valueParam.tpe, jvm.FieldGetterRef(typoType, value), jvm.ConstructorMethodRef(typoType))
          jvm.Given(Nil, jvm.Ident("bijection"), Nil, thisBijection, expr)
        }
      else None

    val jsonInstances = options.jsonLibs.map(_.wrapperTypeInstances(wrapperType = typoType, fieldName = value, underlying = valueParam.tpe))
    val instances = List(
      List(zeroConst),
      bijection.toList,
      jsonInstances.flatMap(_.givens),
      options.dbLib.toList.flatMap(
        _.wrapperTypeInstances(
          wrapperType = typoType,
          underlyingJvmType = valueParam.tpe,
          underlyingDbType = underlyingDbType,
          overrideDbType = None
        )
      )
    ).flatten

    val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
    val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

    val paramsWithAnnotations = List(valueParam).map { p =>
      fieldAnnotations.get(p.name) match {
        case Some(anns) => p.copy(annotations = p.annotations ++ anns)
        case None       => p
      }
    }

    val cls = jvm.Adt.Record(
      annotations = typeAnnotations,
      constructorAnnotations = Nil,
      isWrapper = true,
      privateConstructor = true,
      comments = jvm.Comments.Empty,
      name = typoType,
      tparams = Nil,
      params = paramsWithAnnotations,
      implicitParams = Nil,
      `extends` = None,
      implements = List(FoundationsTypes.precise.DecimalN),
      members = instanceMethods,
      staticMembers = staticMethods ++ instances
    )

    jvm.File(typoType, cls, secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Special version for scale=0 decimals (BigInteger wrapper) that need to convert BigDecimal <-> BigInteger */
  private def mkFileWithScale0Instances(
      typoType: jvm.Type.Qualified,
      valueParam: jvm.Param[jvm.Type],
      underlyingDbType: db.Type,
      staticMethods: List[jvm.Method],
      instanceMethods: List[jvm.Method],
      zeroConst: jvm.Given,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = valueParam.name

    val bijection =
      if (options.enableDsl)
        Some {
          val thisBijection = lang.dsl.Bijection.of(typoType, valueParam.tpe)
          val expr = lang.bijection(typoType, valueParam.tpe, jvm.FieldGetterRef(typoType, value), jvm.ConstructorMethodRef(typoType))
          jvm.Given(Nil, jvm.Ident("bijection"), Nil, thisBijection, expr)
        }
      else None

    val jsonInstances = options.jsonLibs.map(_.wrapperTypeInstances(wrapperType = typoType, fieldName = value, underlying = valueParam.tpe))

    // Generate custom db instances that convert BigDecimal <-> BigInteger
    val dbInstances: List[jvm.Given] = options.dbLib.toList.flatMap {
      case dbLib: DbLibFoundations =>
        val adapter = dbLib.adapter
        val xs = jvm.Ident("xs")
        val bd = jvm.Ident("bd")
        val v = jvm.Ident("v")

        // Language-specific lambda generation
        val (readLambda, writeLambda, readArrayLambda, writeArrayLambda) = lang match {
          case langScala: LangScala if langScala.typeSupport == TypeSupportScala =>
            // Scala with Scala types - uses BigInt, not java.math.BigInteger
            // Read: java.math.BigDecimal -> Type (via scala.BigInt)
            // bd => new Type(BigInt(bd.toBigIntegerExact()))
            val readL = code"$bd => new $typoType(${TypesScala.BigInt}($bd.toBigIntegerExact()))"
            // Write: Type -> java.math.BigDecimal (via BigInt.bigInteger)
            // _.value.bigInteger gives java.math.BigInteger
            val writeL = code"$v => new ${TypesJava.BigDecimal}($v.${value}.bigInteger)"
            val readArrL = code"$xs => $xs.map($bd => new $typoType(${TypesScala.BigInt}($bd.toBigIntegerExact())))"
            val writeArrL = code"$xs => $xs.map($v => new ${TypesJava.BigDecimal}($v.${value}.bigInteger))"
            (readL, writeL, readArrL, writeArrL)
          case _: LangScala =>
            // Scala with Java types - uses java.math.BigInteger
            val readL = code"$bd => new $typoType($bd.toBigIntegerExact())"
            val writeL = code"$v => new ${TypesJava.BigDecimal}($v.${value})"
            val readArrL = code"$xs => $xs.map($bd => new $typoType($bd.toBigIntegerExact()))"
            val writeArrL = code"$xs => $xs.map($v => new ${TypesJava.BigDecimal}($v.${value}))"
            (readL, writeL, readArrL, writeArrL)
          case LangJava =>
            // Java uses java.math.BigInteger
            val readL = jvm.Lambda(bd, jvm.New(typoType, List(jvm.Arg.Pos(code"$bd.toBigIntegerExact()"))).code).code
            val writeL = jvm.Lambda(v, jvm.New(TypesJava.BigDecimal, List(jvm.Arg.Pos(code"$v.${value}()"))).code).code
            val readArrL = jvm.Lambda(xs, lang.arrayMap(xs.code, jvm.Lambda(bd, jvm.New(typoType, List(jvm.Arg.Pos(code"$bd.toBigIntegerExact()"))).code).code, jvm.ClassOf(typoType).code)).code
            val writeArrL = jvm
              .Lambda(
                xs,
                lang.arrayMap(xs.code, jvm.Lambda(v, jvm.New(TypesJava.BigDecimal, List(jvm.Arg.Pos(code"$v.${value}()"))).code).code, jvm.ClassOf(TypesJava.BigDecimal).code)
              )
              .code
            (readL, writeL, readArrL, writeArrL)
          case _: LangKotlin =>
            // Kotlin uses java.math.BigInteger
            val readL = code"{ $bd: ${TypesJava.BigDecimal} -> $typoType($bd.toBigIntegerExact()) }"
            val writeL = code"{ $v: $typoType -> ${TypesJava.BigDecimal}($v.${value}) }"
            val readArrL = code"{ $xs: Array<${TypesJava.BigDecimal}> -> $xs.map { $bd -> $typoType($bd.toBigIntegerExact()) }.toTypedArray() }"
            val writeArrL = code"{ $xs: Array<$typoType> -> $xs.map { $v -> ${TypesJava.BigDecimal}($v.${value}) }.toTypedArray() }"
            (readL, writeL, readArrL, writeArrL)
          case other =>
            sys.error(s"Unsupported language: $other")
        }

        val dbTypeInstance = jvm.Given(
          tparams = Nil,
          name = adapter.typeFieldName,
          implicitParams = Nil,
          tpe = adapter.TypeClass.of(typoType),
          body = code"${adapter.numericTypeCode}.bimap($readLambda, $writeLambda)"
        )

        // Array instance with conversion
        val dbTypeArrayInstance: Option[jvm.Given] =
          if (!adapter.supportsArrays) None
          else {
            Some(
              jvm.Given(
                tparams = Nil,
                name = jvm.Ident("dbTypeArray"),
                implicitParams = Nil,
                tpe = adapter.TypeClass.of(jvm.Type.ArrayOf(typoType)),
                body = code"${adapter.Types}.decimalArray.bimap($readArrayLambda, $writeArrayLambda)"
              )
            )
          }

        List(Some(dbTypeInstance), dbTypeArrayInstance).flatten
      case _ =>
        // Legacy DbLibs don't support precision types
        Nil
    }

    val instances = List(
      List(zeroConst),
      bijection.toList,
      jsonInstances.flatMap(_.givens),
      dbInstances
    ).flatten

    val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
    val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

    val paramsWithAnnotations = List(valueParam).map { p =>
      fieldAnnotations.get(p.name) match {
        case Some(anns) => p.copy(annotations = p.annotations ++ anns)
        case None       => p
      }
    }

    val cls = jvm.Adt.Record(
      annotations = typeAnnotations,
      constructorAnnotations = Nil,
      isWrapper = true,
      privateConstructor = true,
      comments = jvm.Comments.Empty,
      name = typoType,
      tparams = Nil,
      params = paramsWithAnnotations,
      implicitParams = Nil,
      `extends` = None,
      implements = List(FoundationsTypes.precise.DecimalN),
      members = instanceMethods,
      staticMembers = staticMethods ++ instances
    )

    jvm.File(typoType, cls, secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Special version for Scala decimal types (scale > 0) that need to convert java.math.BigDecimal <-> scala.BigDecimal for arrays. The single-value instance uses ScalaDbTypes which already handles
    * the conversion, but array instances need explicit conversion.
    */
  private def mkFileWithScalaDecimalInstances(
      typoType: jvm.Type.Qualified,
      valueParam: jvm.Param[jvm.Type],
      underlyingDbType: db.Type,
      staticMethods: List[jvm.Method],
      instanceMethods: List[jvm.Method],
      zeroConst: jvm.Given,
      options: InternalOptions,
      lang: Lang
  ): jvm.File = {
    val value = valueParam.name

    val bijection =
      if (options.enableDsl)
        Some {
          val thisBijection = lang.dsl.Bijection.of(typoType, valueParam.tpe)
          val expr = lang.bijection(typoType, valueParam.tpe, jvm.FieldGetterRef(typoType, value), jvm.ConstructorMethodRef(typoType))
          jvm.Given(Nil, jvm.Ident("bijection"), Nil, thisBijection, expr)
        }
      else None

    val jsonInstances = options.jsonLibs.map(_.wrapperTypeInstances(wrapperType = typoType, fieldName = value, underlying = valueParam.tpe))

    // Generate custom db instances for Scala BigDecimal
    // The single-value instance uses ScalaDbTypes which handles java.math.BigDecimal <-> scala.BigDecimal
    // The array instance needs explicit conversion since DuckDbTypes.decimalArray uses java.math.BigDecimal[]
    val dbInstances: List[jvm.Given] = options.dbLib.toList.flatMap {
      case dbLib: DbLibFoundations =>
        val adapter = dbLib.adapter
        val xs = jvm.Ident("xs")
        val bd = jvm.Ident("bd")
        val v = jvm.Ident("v")

        // Array instance: convert java.math.BigDecimal[] <-> Decimal10_2[]
        // Read: java.math.BigDecimal[] -> Decimal10_2[] (wrap java BD in scala BD, then create wrapper)
        // Write: Decimal10_2[] -> java.math.BigDecimal[] (get scala BD value, convert to java BD)
        val readArrayLambda = code"$xs => $xs.map($bd => new $typoType(${TypesScala.BigDecimal}($bd)))"
        val writeArrayLambda = code"$xs => $xs.map($v => $v.${value}.bigDecimal)"

        val dbTypeArrayInstance: Option[jvm.Given] =
          if (!adapter.supportsArrays) None
          else {
            Some(
              jvm.Given(
                tparams = Nil,
                name = jvm.Ident("dbTypeArray"),
                implicitParams = Nil,
                tpe = adapter.TypeClass.of(jvm.Type.ArrayOf(typoType)),
                body = code"${adapter.Types}.decimalArray.bimap($readArrayLambda, $writeArrayLambda)"
              )
            )
          }

        // Single-value instance uses ScalaDbTypes which already handles java <-> scala BigDecimal conversion
        val dbTypeInstance = jvm.Given(
          tparams = Nil,
          name = adapter.typeFieldName,
          implicitParams = Nil,
          tpe = adapter.TypeClass.of(typoType),
          body =
            code"${TypesScala.ScalaDbTypes}.${adapter.Types.value.idents.last}.${jvm.Ident(adapter.numericTypeName)}.bimap(${jvm.ConstructorMethodRef(typoType)}, ${jvm.FieldGetterRef(typoType, value)})"
        )

        List(dbTypeArrayInstance, Some(dbTypeInstance)).flatten
      case _ =>
        // Legacy DbLibs don't support precision types
        Nil
    }

    val instances = List(
      List(zeroConst),
      bijection.toList,
      jsonInstances.flatMap(_.givens),
      dbInstances
    ).flatten

    val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
    val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

    val paramsWithAnnotations = List(valueParam).map { p =>
      fieldAnnotations.get(p.name) match {
        case Some(anns) => p.copy(annotations = p.annotations ++ anns)
        case None       => p
      }
    }

    val cls = jvm.Adt.Record(
      annotations = typeAnnotations,
      constructorAnnotations = Nil,
      isWrapper = true,
      privateConstructor = true,
      comments = jvm.Comments.Empty,
      name = typoType,
      tparams = Nil,
      params = paramsWithAnnotations,
      implicitParams = Nil,
      `extends` = None,
      implements = List(FoundationsTypes.precise.DecimalN),
      members = instanceMethods,
      staticMembers = staticMethods ++ instances
    )

    jvm.File(typoType, cls, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def ofMethodString(typoType: jvm.Type.Qualified, maxLength: Int, hasNonEmpty: Boolean, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val lengthCheck = code"${lang.prop(value.code, "length")} <= $maxLength"
    val nonEmptyCheck = code"!${lang.nullaryMethodCall(value.code, jvm.Ident("isEmpty"))}"
    val condition = if (hasNonEmpty) code"$nonEmptyCheck && $lengthCheck" else lengthCheck
    val body = lang.ternary(condition, lang.Optional.some(jvm.New(typoType, List(jvm.Arg.Pos(value.code))).code), lang.Optional.none)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("of"),
      params = List(jvm.Param(value, lang.String)),
      implicitParams = Nil,
      tpe = lang.Optional.tpe(typoType),
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  private def ofMethodNonEmptyString(typoType: jvm.Type.Qualified, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val condition = code"$value != null && !${lang.nullaryMethodCall(value.code, jvm.Ident("isEmpty"))}"
    val body = lang.ternary(condition, lang.Optional.some(jvm.New(typoType, List(jvm.Arg.Pos(value.code))).code), lang.Optional.none)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("of"),
      params = List(jvm.Param(value, lang.String)),
      implicitParams = Nil,
      tpe = lang.Optional.tpe(typoType),
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  private def unsafeForceMethodString(typoType: jvm.Type.Qualified, maxLength: Int, hasNonEmpty: Boolean, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val lengthCheck = code"${lang.prop(value.code, "length")} > $maxLength"
    val nonEmptyCheck = lang.nullaryMethodCall(value.code, jvm.Ident("isEmpty"))

    val stmts = List.newBuilder[jvm.Code]

    if (hasNonEmpty) {
      val nullOrEmptyCheck = if (lang.needsExplicitNullCheck) code"$value == null || $nonEmptyCheck" else nonEmptyCheck
      stmts += jvm
        .If(
          List(
            jvm.If.Branch(
              nullOrEmptyCheck,
              jvm.Throw(jvm.New(TypesJava.IllegalArgumentException, List(jvm.Arg.Pos(lang.stringLiteral("Value must not be null or empty"))))).code
            )
          ),
          None
        )
        .code
    }

    stmts += jvm
      .If(
        List(
          jvm.If.Branch(
            lengthCheck,
            jvm.Throw(jvm.New(TypesJava.IllegalArgumentException, List(jvm.Arg.Pos(lang.s(code"Value length $${${lang.prop(value.code, "length")}} exceeds maximum $maxLength"))))).code
          )
        ),
        None
      )
      .code

    stmts += jvm.Return(jvm.New(typoType, List(jvm.Arg.Pos(value.code))).code).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("unsafeForce"),
      params = List(jvm.Param(value, lang.String)),
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Stmts(stmts.result()),
      isOverride = false,
      isDefault = false
    )
  }

  private def unsafeForceMethodNonEmptyString(typoType: jvm.Type.Qualified, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val nonEmptyCheck = lang.nullaryMethodCall(value.code, jvm.Ident("isEmpty"))
    val nullOrEmptyCheck = if (lang.needsExplicitNullCheck) code"$value == null || $nonEmptyCheck" else nonEmptyCheck

    val stmts = List(
      jvm
        .If(
          List(
            jvm.If.Branch(
              nullOrEmptyCheck,
              jvm.Throw(jvm.New(TypesJava.IllegalArgumentException, List(jvm.Arg.Pos(lang.stringLiteral("Value must not be null or empty"))))).code
            )
          ),
          None
        )
        .code,
      jvm.Return(jvm.New(typoType, List(jvm.Arg.Pos(value.code))).code).code
    )

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("unsafeForce"),
      params = List(jvm.Param(value, lang.String)),
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Stmts(stmts),
      isOverride = false,
      isDefault = false
    )
  }

  private def truncateMethod(typoType: jvm.Type.Qualified, maxLength: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val lengthCheck = code"${lang.prop(value.code, "length")} <= $maxLength"
    val truncated = code"$value.substring(0, $maxLength)"
    val body = jvm.New(typoType, List(jvm.Arg.Pos(lang.ternary(lengthCheck, value.code, truncated)))).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("truncate"),
      params = List(jvm.Param(value, lang.String)),
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  private def ofMethodBinary(typoType: jvm.Type.Qualified, maxLength: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val lengthCheck = code"${lang.byteArrayLength(value.code)} <= $maxLength"
    val body = lang.ternary(lengthCheck, lang.Optional.some(jvm.New(typoType, List(jvm.Arg.Pos(value.code))).code), lang.Optional.none)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("of"),
      params = List(jvm.Param(value, lang.ByteArray)),
      implicitParams = Nil,
      tpe = lang.Optional.tpe(typoType),
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  private def unsafeForceMethodBinary(typoType: jvm.Type.Qualified, maxLength: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val lengthCheck = code"${lang.byteArrayLength(value.code)} > $maxLength"

    val stmts = List(
      jvm
        .If(
          List(
            jvm.If.Branch(
              lengthCheck,
              jvm.Throw(jvm.New(TypesJava.IllegalArgumentException, List(jvm.Arg.Pos(lang.s(code"Value length $${${lang.byteArrayLength(value.code)}} exceeds maximum $maxLength"))))).code
            )
          ),
          None
        )
        .code,
      jvm.Return(jvm.New(typoType, List(jvm.Arg.Pos(value.code))).code).code
    )

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("unsafeForce"),
      params = List(jvm.Param(value, lang.ByteArray)),
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Stmts(stmts),
      isOverride = false,
      isDefault = false
    )
  }

  private def ofMethodDecimal(typoType: jvm.Type.Qualified, precision: Int, scale: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val underlyingType = if (scale == 0) lang.typeSupport.BigInteger else lang.BigDecimal

    val body = if (scale == 0) {
      // For scale=0 (BigInteger), check bit length
      val precisionCheck = code"${lang.nullaryMethodCall(value.code, jvm.Ident("bitLength"))} <= ${precision * 4}"
      jvm.Body.Expr(lang.ternary(precisionCheck, lang.Optional.some(jvm.New(typoType, List(jvm.Arg.Pos(value.code))).code), lang.Optional.none))
    } else {
      // For scale>0, auto-scale to target scale using HALF_UP rounding, then check precision
      // Use java.math.RoundingMode for java.math.BigDecimal, scala.math.BigDecimal.RoundingMode for scala.math.BigDecimal
      val scaled = jvm.Ident("scaled")
      // scala.math.BigDecimal.setScale expects scala.math.BigDecimal.RoundingMode, java.math.BigDecimal expects java.math.RoundingMode
      val roundingModeHalfUp: jvm.Code = lang.typeSupport match {
        case TypeSupportScala => code"${TypesScala.BigDecimal}.RoundingMode.HALF_UP"
        case _                => code"${TypesJava.RoundingMode}.HALF_UP"
      }
      lang match {
        case LangJava =>
          jvm.Body.Stmts(
            List(
              code"${lang.BigDecimal} $scaled = $value.setScale($scale, $roundingModeHalfUp)",
              code"return ${lang.nullaryMethodCall(scaled.code, jvm.Ident("precision"))} <= $precision ? ${lang.Optional.some(jvm.New(typoType, List(jvm.Arg.Pos(scaled.code))).code)} : ${lang.Optional.none}"
            )
          )
        case _: LangKotlin =>
          jvm.Body.Stmts(
            List(
              code"val $scaled = $value.setScale($scale, $roundingModeHalfUp)",
              code"return if (${lang.nullaryMethodCall(scaled.code, jvm.Ident("precision"))} <= $precision) ${lang.Optional.some(jvm.New(typoType, List(jvm.Arg.Pos(scaled.code))).code)} else ${lang.Optional.none}"
            )
          )
        case _: LangScala =>
          val setScaleExpr = code"$value.setScale($scale, $roundingModeHalfUp)"
          val precisionCheck = code"$scaled.precision <= $precision"
          jvm.Body.Expr(code"{ val $scaled = $setScaleExpr; if ($precisionCheck) ${lang.Optional.some(jvm.New(typoType, List(jvm.Arg.Pos(scaled.code))).code)} else ${lang.Optional.none} }")
        case o => sys.error(s"Unsupported language: $o")
      }
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("of"),
      params = List(jvm.Param(value, underlyingType)),
      implicitParams = Nil,
      tpe = lang.Optional.tpe(typoType),
      throws = Nil,
      body = body,
      isOverride = false,
      isDefault = false
    )
  }

  private def unsafeForceMethodDecimal(typoType: jvm.Type.Qualified, precision: Int, scale: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val underlyingType = if (scale == 0) lang.typeSupport.BigInteger else lang.BigDecimal

    val body = if (scale == 0) {
      // For scale=0 (BigInteger), check bit length
      val precisionCheck = code"${lang.nullaryMethodCall(value.code, jvm.Ident("bitLength"))} > ${precision * 4}"
      val stmts = List(
        jvm
          .If(
            List(
              jvm.If.Branch(
                precisionCheck,
                jvm.Throw(jvm.New(TypesJava.IllegalArgumentException, List(jvm.Arg.Pos(lang.stringLiteral(s"Value exceeds precision($precision, $scale)"))))).code
              )
            ),
            None
          )
          .code,
        jvm.Return(jvm.New(typoType, List(jvm.Arg.Pos(value.code))).code).code
      )
      jvm.Body.Stmts(stmts)
    } else {
      // For scale>0, auto-scale to target scale using HALF_UP rounding, then check precision
      val scaled = jvm.Ident("scaled")
      // scala.math.BigDecimal.setScale expects scala.math.BigDecimal.RoundingMode, java.math.BigDecimal expects java.math.RoundingMode
      val roundingModeHalfUp: jvm.Code = lang.typeSupport match {
        case TypeSupportScala => code"${TypesScala.BigDecimal}.RoundingMode.HALF_UP"
        case _                => code"${TypesJava.RoundingMode}.HALF_UP"
      }
      lang match {
        case LangJava =>
          jvm.Body.Stmts(
            List(
              code"${lang.BigDecimal} $scaled = $value.setScale($scale, $roundingModeHalfUp)",
              code"if (${lang.nullaryMethodCall(scaled.code, jvm.Ident("precision"))} > $precision) { throw new ${TypesJava.IllegalArgumentException}(${lang
                  .stringLiteral(s"Value exceeds precision($precision, $scale)")}); }",
              code"return new $typoType($scaled)"
            )
          )
        case _: LangKotlin =>
          jvm.Body.Stmts(
            List(
              code"val $scaled = $value.setScale($scale, $roundingModeHalfUp)",
              code"if (${lang.nullaryMethodCall(scaled.code, jvm.Ident("precision"))} > $precision) throw ${TypesJava.IllegalArgumentException}(${lang
                  .stringLiteral(s"Value exceeds precision($precision, $scale)")})",
              code"return $typoType($scaled)"
            )
          )
        case _: LangScala =>
          val setScaleExpr = code"$value.setScale($scale, $roundingModeHalfUp)"
          jvm.Body.Expr(code"{ val $scaled = $setScaleExpr; if ($scaled.precision > $precision) throw new ${TypesJava.IllegalArgumentException}(${lang
              .stringLiteral(s"Value exceeds precision($precision, $scale)")}); new $typoType($scaled) }")
        case o => sys.error(s"Unsupported language: $o")
      }
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("unsafeForce"),
      params = List(jvm.Param(value, underlyingType)),
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = body,
      isOverride = false,
      isDefault = false
    )
  }

  /** Zero constant for decimal types */
  private def zeroConstantDecimal(typoType: jvm.Type.Qualified, scale: Int, lang: Lang): jvm.Given = {
    val zeroValue = if (scale == 0) {
      lang match {
        case langScala: LangScala if langScala.typeSupport == TypeSupportScala => code"${TypesScala.BigInt}(0)"
        case _: LangScala                                                      => code"${TypesJava.BigInteger}.ZERO"
        case LangJava                                                          => code"${TypesJava.BigInteger}.ZERO"
        case _: LangKotlin                                                     => code"${TypesJava.BigInteger}.ZERO"
        case other                                                             => sys.error(s"Unsupported language: $other")
      }
    } else {
      lang match {
        case langScala: LangScala if langScala.typeSupport == TypeSupportScala => code"${TypesScala.BigDecimal}(0)"
        case _: LangScala                                                      => code"${TypesJava.BigDecimal}.ZERO"
        case LangJava                                                          => code"${TypesJava.BigDecimal}.ZERO"
        case _: LangKotlin                                                     => code"${TypesJava.BigDecimal}.ZERO"
        case other                                                             => sys.error(s"Unsupported language: $other")
      }
    }
    jvm.Given(
      tparams = Nil,
      name = jvm.Ident("Zero"),
      implicitParams = Nil,
      tpe = typoType,
      body = jvm.New(typoType, List(jvm.Arg.Pos(zeroValue))).code
    )
  }

  /** of(int) method - always succeeds if Int max (10 digits) fits in precision, otherwise checks */
  private def ofMethodDecimalFromInt(typoType: jvm.Type.Qualified, precision: Int, scale: Int, lang: Lang): Option[jvm.Method] = {
    val value = jvm.Ident("value")
    // Int max is 2,147,483,647 (10 digits). If precision >= 10, Int always fits.
    val intAlwaysFits = precision >= 10

    val body = if (scale == 0) {
      // BigInteger type
      val bigIntValue = lang match {
        case langScala: LangScala if langScala.typeSupport == TypeSupportScala => code"${TypesScala.BigInt}($value)"
        case _: LangScala                                                      => code"${TypesJava.BigInteger}.valueOf(${lang.toLong(value.code)})"
        case LangJava                                                          => code"${TypesJava.BigInteger}.valueOf(${lang.toLong(value.code)})"
        case _: LangKotlin                                                     => code"${TypesJava.BigInteger}.valueOf(${lang.toLong(value.code)})"
        case other                                                             => sys.error(s"Unsupported language: $other")
      }
      jvm.New(typoType, List(jvm.Arg.Pos(bigIntValue))).code
    } else {
      // BigDecimal type
      val bigDecValue = lang match {
        case langScala: LangScala if langScala.typeSupport == TypeSupportScala => code"${TypesScala.BigDecimal}($value)"
        case _: LangScala                                                      => code"${TypesJava.BigDecimal}.valueOf(${lang.toLong(value.code)})"
        case LangJava                                                          => code"${TypesJava.BigDecimal}.valueOf(${lang.toLong(value.code)})"
        case _: LangKotlin                                                     => code"${TypesJava.BigDecimal}.valueOf(${lang.toLong(value.code)})"
        case other                                                             => sys.error(s"Unsupported language: $other")
      }
      jvm.New(typoType, List(jvm.Arg.Pos(bigDecValue))).code
    }

    // For Int, we always succeed without checking since Int max fits in any reasonable precision
    // An Int as BigDecimal always has scale 0, which is <= any scale
    Some(
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("of"),
        params = List(jvm.Param(value, lang.Int)),
        implicitParams = Nil,
        tpe = typoType,
        throws = Nil,
        body = jvm.Body.Expr(body),
        isOverride = false,
        isDefault = false
      )
    )
  }

  /** of(long) method - always succeeds if Long max (19 digits) fits in precision, otherwise checks */
  private def ofMethodDecimalFromLong(typoType: jvm.Type.Qualified, precision: Int, scale: Int, lang: Lang): Option[jvm.Method] = {
    val value = jvm.Ident("value")
    // Long max is 9,223,372,036,854,775,807 (19 digits). If precision >= 19, Long always fits.
    val longAlwaysFits = precision >= 19

    val body = if (scale == 0) {
      // BigInteger type
      val bigIntValue = lang match {
        case langScala: LangScala if langScala.typeSupport == TypeSupportScala => code"${TypesScala.BigInt}($value)"
        case _: LangScala                                                      => code"${TypesJava.BigInteger}.valueOf($value)"
        case LangJava                                                          => code"${TypesJava.BigInteger}.valueOf($value)"
        case _: LangKotlin                                                     => code"${TypesJava.BigInteger}.valueOf($value)"
        case other                                                             => sys.error(s"Unsupported language: $other")
      }
      if (longAlwaysFits) {
        jvm.New(typoType, List(jvm.Arg.Pos(bigIntValue))).code
      } else {
        // Need to check - use of(BigInteger) which returns Optional
        val bigIntExpr = bigIntValue
        code"$typoType.of($bigIntExpr)"
      }
    } else {
      // BigDecimal type
      val bigDecValue = lang match {
        case langScala: LangScala if langScala.typeSupport == TypeSupportScala => code"${TypesScala.BigDecimal}($value)"
        case _: LangScala                                                      => code"${TypesJava.BigDecimal}.valueOf($value)"
        case LangJava                                                          => code"${TypesJava.BigDecimal}.valueOf($value)"
        case _: LangKotlin                                                     => code"${TypesJava.BigDecimal}.valueOf($value)"
        case other                                                             => sys.error(s"Unsupported language: $other")
      }
      if (longAlwaysFits) {
        jvm.New(typoType, List(jvm.Arg.Pos(bigDecValue))).code
      } else {
        // Need to check - use of(BigDecimal) which returns Optional
        val bigDecExpr = bigDecValue
        code"$typoType.of($bigDecExpr)"
      }
    }

    val returnType = if (longAlwaysFits) typoType else lang.Optional.tpe(typoType)

    Some(
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("of"),
        params = List(jvm.Param(value, lang.Long)),
        implicitParams = Nil,
        tpe = returnType,
        throws = Nil,
        body = jvm.Body.Expr(body),
        isOverride = false,
        isDefault = false
      )
    )
  }

  /** of(double) method for decimal types (scale > 0 only) - delegates to of(BigDecimal) for validation */
  private def ofMethodDecimalFromDouble(typoType: jvm.Type.Qualified, precision: Int, scale: Int, lang: Lang): Option[jvm.Method] = {
    // Only for decimal types with scale > 0, not for integer types
    if (scale == 0) return None

    val value = jvm.Ident("value")

    // Convert double to BigDecimal and delegate to of(BigDecimal) which does validation
    val bigDecValue = lang match {
      case langScala: LangScala if langScala.typeSupport == TypeSupportScala => code"${TypesScala.BigDecimal}($value)"
      case _: LangScala                                                      => code"${TypesJava.BigDecimal}.valueOf($value)"
      case LangJava                                                          => code"${TypesJava.BigDecimal}.valueOf($value)"
      case _: LangKotlin                                                     => code"${TypesJava.BigDecimal}.valueOf($value)"
      case other                                                             => sys.error(s"Unsupported language: $other")
    }

    val body = code"$typoType.of($bigDecValue)"

    Some(
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident("of"),
        params = List(jvm.Param(value, lang.Double)),
        implicitParams = Nil,
        tpe = lang.Optional.tpe(typoType),
        throws = Nil,
        body = jvm.Body.Expr(body),
        isOverride = false,
        isDefault = false
      )
    )
  }

  private def chronoUnitForFsp(fsp: Int): jvm.Code = {
    val ChronoUnit = jvm.Type.Qualified("java.time.temporal.ChronoUnit")
    fsp match {
      case 0 => code"$ChronoUnit.SECONDS"
      case 1 => code"$ChronoUnit.MILLIS"
      case 2 => code"$ChronoUnit.MILLIS"
      case 3 => code"$ChronoUnit.MILLIS"
      case 4 => code"$ChronoUnit.MICROS"
      case 5 => code"$ChronoUnit.MICROS"
      case 6 => code"$ChronoUnit.MICROS"
      case _ => code"$ChronoUnit.NANOS"
    }
  }

  private def ofMethodDateTime(typoType: jvm.Type.Qualified, dateTimeType: jvm.Type.Qualified, fsp: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val truncated = code"$value.truncatedTo(${chronoUnitForFsp(fsp)})"
    val body = jvm.New(typoType, List(jvm.Arg.Pos(truncated))).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("of"),
      params = List(jvm.Param(value, dateTimeType)),
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  private def nowMethodDateTime(typoType: jvm.Type.Qualified, dateTimeType: jvm.Type.Qualified, fsp: Int, lang: Lang): jvm.Method = {
    val now = code"$dateTimeType.now()"
    val truncated = code"$now.truncatedTo(${chronoUnitForFsp(fsp)})"
    val body = jvm.New(typoType, List(jvm.Arg.Pos(truncated))).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("now"),
      params = Nil,
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  private def ofMethodInstant(typoType: jvm.Type.Qualified, fsp: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val truncated = code"$value.truncatedTo(${chronoUnitForFsp(fsp)})"
    val body = jvm.New(typoType, List(jvm.Arg.Pos(truncated))).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("of"),
      params = List(jvm.Param(value, TypesJava.Instant)),
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  private def nowMethodInstant(typoType: jvm.Type.Qualified, fsp: Int, lang: Lang): jvm.Method = {
    val now = code"${TypesJava.Instant}.now()"
    val truncated = code"$now.truncatedTo(${chronoUnitForFsp(fsp)})"
    val body = jvm.New(typoType, List(jvm.Arg.Pos(truncated))).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("now"),
      params = Nil,
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  private def nowMethodOffsetDateTime(typoType: jvm.Type.Qualified, fsp: Int, lang: Lang): jvm.Method = {
    val now = code"${TypesJava.OffsetDateTime}.now()"
    val truncated = code"$now.truncatedTo(${chronoUnitForFsp(fsp)})"
    val body = jvm.New(typoType, List(jvm.Arg.Pos(truncated))).code

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("now"),
      params = Nil,
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  /** Instance method: maxLength() for StringN, NonEmptyStringN, BinaryN */
  private def maxLengthMethod(maxLength: Int, lang: Lang): jvm.Method = {
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("maxLength"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(code"$maxLength"),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: precision() for DecimalN */
  private def precisionMethod(precision: Int, lang: Lang): jvm.Method = {
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("precision"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(code"$precision"),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: scale() for DecimalN */
  private def scaleMethod(scale: Int, lang: Lang): jvm.Method = {
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("scale"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(code"$scale"),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: decimalValue() for DecimalN - returns BigDecimal (converts from BigInteger if scale=0) */
  private def decimalValueMethod(scale: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val body = if (scale == 0) {
      // For scale=0, value is BigInteger, need to convert to BigDecimal
      lang match {
        case langScala: LangScala if langScala.typeSupport == TypeSupportScala =>
          // Scala BigInt -> java.math.BigDecimal
          code"new ${TypesJava.BigDecimal}($value.bigInteger)"
        case _: LangScala =>
          // java.math.BigInteger -> java.math.BigDecimal
          code"new ${TypesJava.BigDecimal}($value)"
        case LangJava =>
          // value is already BigInteger, just wrap in BigDecimal
          code"new ${TypesJava.BigDecimal}($value)"
        case _: LangKotlin =>
          code"${TypesJava.BigDecimal}($value)"
        case other =>
          sys.error(s"Unsupported language: $other")
      }
    } else {
      // For scale>0, value is BigDecimal
      lang match {
        case langScala: LangScala if langScala.typeSupport == TypeSupportScala =>
          // Scala BigDecimal -> java.math.BigDecimal
          code"$value.bigDecimal"
        case _: LangScala =>
          // Already java.math.BigDecimal
          value.code
        case LangJava =>
          // value is already BigDecimal, just return it
          value.code
        case _: LangKotlin =>
          value.code
        case other =>
          sys.error(s"Unsupported language: $other")
      }
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("decimalValue"),
      params = Nil,
      implicitParams = Nil,
      tpe = TypesJava.BigDecimal,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: fractionalSecondsPrecision() for temporal types */
  private def fractionalSecondsPrecisionMethod(fsp: Int, lang: Lang): jvm.Method = {
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("fractionalSecondsPrecision"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(code"$fsp"),
      isOverride = true,
      isDefault = false
    )
  }

  /** Static method: of(String) for PaddedStringN - pads to fixed length or returns empty if too long */
  private def ofMethodPaddedString(typoType: jvm.Type.Qualified, length: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val lengthCheck = code"${lang.prop(value.code, "length")} <= $length"
    // Pad with spaces to fixed length
    val formatSpec = code""""%-${length}s""""
    val paddedValue = code"${TypesJava.String}.format($formatSpec, $value)"
    val body = lang.ternary(lengthCheck, lang.Optional.some(jvm.New(typoType, List(jvm.Arg.Pos(paddedValue))).code), lang.Optional.none)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("of"),
      params = List(jvm.Param(value, lang.String)),
      implicitParams = Nil,
      tpe = lang.Optional.tpe(typoType),
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  /** Static method: unsafeForce(String) for PaddedStringN - pads to fixed length or throws if too long */
  private def unsafeForceMethodPaddedString(typoType: jvm.Type.Qualified, length: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val lengthCheck = code"${lang.prop(value.code, "length")} > $length"
    // Pad with spaces to fixed length
    val formatSpec = code""""%-${length}s""""
    val paddedValue = code"${TypesJava.String}.format($formatSpec, $value)"

    val stmts = List(
      jvm
        .If(
          List(
            jvm.If.Branch(
              lengthCheck,
              jvm.Throw(jvm.New(TypesJava.IllegalArgumentException, List(jvm.Arg.Pos(lang.s(code"Value length $${${lang.prop(value.code, "length")}} exceeds fixed length $length"))))).code
            )
          ),
          None
        )
        .code,
      jvm.Return(jvm.New(typoType, List(jvm.Arg.Pos(paddedValue))).code).code
    )

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("unsafeForce"),
      params = List(jvm.Param(value, lang.String)),
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Stmts(stmts),
      isOverride = false,
      isDefault = false
    )
  }

  /** Static method: of(String) for NonEmptyPaddedStringN - pads to fixed length, returns empty if empty or too long */
  private def ofMethodNonEmptyPaddedString(typoType: jvm.Type.Qualified, length: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    // Kotlin uses trim(), Java uses strip()
    val trimmedValue = lang match {
      case _: LangKotlin => code"$value.trim()"
      case _             => code"$value.strip()"
    }
    val nonEmptyCheck = code"!${lang.nullaryMethodCall(trimmedValue, jvm.Ident("isEmpty"))}"
    val lengthCheck = code"${lang.prop(value.code, "length")} <= $length"
    val condition = code"$nonEmptyCheck && $lengthCheck"
    // Pad with spaces to fixed length
    val formatSpec = code""""%-${length}s""""
    val paddedValue = code"${TypesJava.String}.format($formatSpec, $value)"
    val body = lang.ternary(condition, lang.Optional.some(jvm.New(typoType, List(jvm.Arg.Pos(paddedValue))).code), lang.Optional.none)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("of"),
      params = List(jvm.Param(value, lang.String)),
      implicitParams = Nil,
      tpe = lang.Optional.tpe(typoType),
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = false,
      isDefault = false
    )
  }

  /** Static method: unsafeForce(String) for NonEmptyPaddedStringN - pads to fixed length, throws if empty or too long */
  private def unsafeForceMethodNonEmptyPaddedString(typoType: jvm.Type.Qualified, length: Int, lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    // Kotlin uses trim(), Java uses strip()
    val trimmedValue = lang match {
      case _: LangKotlin => code"$value.trim()"
      case _             => code"$value.strip()"
    }
    val emptyCheck = lang.nullaryMethodCall(trimmedValue, jvm.Ident("isEmpty"))
    val nullOrEmptyCheck = if (lang.needsExplicitNullCheck) code"$value == null || $emptyCheck" else emptyCheck
    val lengthCheck = code"${lang.prop(value.code, "length")} > $length"
    // Pad with spaces to fixed length
    val formatSpec = code""""%-${length}s""""
    val paddedValue = code"${TypesJava.String}.format($formatSpec, $value)"

    val stmts = List(
      jvm
        .If(
          List(
            jvm.If.Branch(
              nullOrEmptyCheck,
              jvm.Throw(jvm.New(TypesJava.IllegalArgumentException, List(jvm.Arg.Pos(lang.stringLiteral("Value must not be null or empty"))))).code
            )
          ),
          None
        )
        .code,
      jvm
        .If(
          List(
            jvm.If.Branch(
              lengthCheck,
              jvm.Throw(jvm.New(TypesJava.IllegalArgumentException, List(jvm.Arg.Pos(lang.s(code"Value length $${${lang.prop(value.code, "length")}} exceeds fixed length $length"))))).code
            )
          ),
          None
        )
        .code,
      jvm.Return(jvm.New(typoType, List(jvm.Arg.Pos(paddedValue))).code).code
    )

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("unsafeForce"),
      params = List(jvm.Param(value, lang.String)),
      implicitParams = Nil,
      tpe = typoType,
      throws = Nil,
      body = jvm.Body.Stmts(stmts),
      isOverride = false,
      isDefault = false
    )
  }

  /** Instance method: length() for PaddedStringN - returns the fixed length */
  private def lengthMethod(length: Int, lang: Lang): jvm.Method = {
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("length"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(code"$length"),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: trimmed() for PaddedStringN - returns value with trailing spaces removed */
  private def trimmedMethod(lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val body = lang match {
      case LangJava =>
        // value is already a String, just call stripTrailing()
        code"$value.stripTrailing()"
      case _: LangKotlin =>
        code"$value.trimEnd()"
      case _: LangScala =>
        code"$value.stripTrailing()"
      case other =>
        sys.error(s"Unsupported language: $other")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("trimmed"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.String,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticEquals(T) for StringN, NonEmptyStringN - compares underlying value */
  private def semanticEqualsMethodStringValue(interfaceType: jvm.Type.Qualified, lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")
    val value = jvm.Ident("value")
    val nullCheck = code"$other == null"
    val equalsCheck = lang match {
      case LangJava      => code"$value.equals($other.rawValue())"
      case _: LangKotlin => code"$value == $other.rawValue()"
      case _: LangScala  => code"$value == $other.rawValue()"
      case o             => sys.error(s"Unsupported language: $o")
    }
    val body = lang.ternary(nullCheck, code"false", equalsCheck)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticEquals"),
      params = List(jvm.Param(other, interfaceType)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticHashCode() for StringN, NonEmptyStringN - hash of underlying value */
  private def semanticHashCodeMethodStringValue(lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val body = lang match {
      case LangJava      => code"$value.hashCode()"
      case _: LangKotlin => code"$value.hashCode()"
      case _: LangScala  => code"$value.hashCode()"
      case o             => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticHashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticEquals(T) for PaddedStringN, NonEmptyPaddedStringN - compares trimmed value */
  private def semanticEqualsMethodPaddedString(interfaceType: jvm.Type.Qualified, lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")
    val nullCheck = code"$other == null"
    val trimmedThis = lang match {
      case LangJava      => code"trimmed()"
      case _: LangKotlin => code"trimmed()"
      case _: LangScala  => code"trimmed()"
      case o             => sys.error(s"Unsupported language: $o")
    }
    val trimmedOther = lang match {
      case LangJava      => code"$other.trimmed()"
      case _: LangKotlin => code"$other.trimmed()"
      case _: LangScala  => code"$other.trimmed()"
      case o             => sys.error(s"Unsupported language: $o")
    }
    val equalsCheck = lang match {
      case LangJava      => code"$trimmedThis.equals($trimmedOther)"
      case _: LangKotlin => code"$trimmedThis == $trimmedOther"
      case _: LangScala  => code"$trimmedThis == $trimmedOther"
      case o             => sys.error(s"Unsupported language: $o")
    }
    val body = lang.ternary(nullCheck, code"false", equalsCheck)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticEquals"),
      params = List(jvm.Param(other, interfaceType)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticHashCode() for PaddedStringN, NonEmptyPaddedStringN - hash of trimmed value */
  private def semanticHashCodeMethodPaddedString(lang: Lang): jvm.Method = {
    val body = lang match {
      case LangJava      => code"trimmed().hashCode()"
      case _: LangKotlin => code"trimmed().hashCode()"
      case _: LangScala  => code"trimmed().hashCode()"
      case o             => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticHashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticEquals(BinaryN) for BinaryN - compares byte arrays */
  private def semanticEqualsMethodBinary(lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")
    val value = jvm.Ident("value")
    val nullCheck = code"$other == null"
    val equalsCheck = lang match {
      case LangJava      => code"${TypesJava.Arrays}.equals($value, $other.rawValue())"
      case _: LangKotlin => code"$value.contentEquals($other.rawValue())"
      case _: LangScala  => code"${TypesJava.Arrays}.equals($value, $other.rawValue())"
      case o             => sys.error(s"Unsupported language: $o")
    }
    val body = lang.ternary(nullCheck, code"false", equalsCheck)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticEquals"),
      params = List(jvm.Param(other, FoundationsTypes.precise.BinaryN)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticHashCode() for BinaryN - hash of byte array */
  private def semanticHashCodeMethodBinary(lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val body = lang match {
      case LangJava      => code"${TypesJava.Arrays}.hashCode($value)"
      case _: LangKotlin => code"$value.contentHashCode()"
      case _: LangScala  => code"${TypesJava.Arrays}.hashCode($value)"
      case o             => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticHashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticEquals(DecimalN) for DecimalN - compares decimal values using compareTo */
  private def semanticEqualsMethodDecimal(lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")
    val nullCheck = code"$other == null"
    val compareCheck = lang match {
      case LangJava      => code"decimalValue().compareTo($other.decimalValue()) == 0"
      case _: LangKotlin => code"decimalValue().compareTo($other.decimalValue()) == 0"
      case _: LangScala  => code"decimalValue().compareTo($other.decimalValue()) == 0"
      case o             => sys.error(s"Unsupported language: $o")
    }
    val body = lang.ternary(nullCheck, code"false", compareCheck)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticEquals"),
      params = List(jvm.Param(other, FoundationsTypes.precise.DecimalN)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticHashCode() for DecimalN - hash of normalized decimal value */
  private def semanticHashCodeMethodDecimal(lang: Lang): jvm.Method = {
    val body = lang match {
      case LangJava      => code"decimalValue().stripTrailingZeros().hashCode()"
      case _: LangKotlin => code"decimalValue().stripTrailingZeros().hashCode()"
      case _: LangScala  => code"decimalValue().stripTrailingZeros().hashCode()"
      case o             => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticHashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: equals(Object) for DecimalN - uses semantic equality across all DecimalN types */
  private def equalsMethodDecimal(lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")

    val (paramName, paramType, body) = lang match {
      case LangJava =>
        val obj = jvm.Ident("obj")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this == $obj) return true",
            code"if (!($obj instanceof ${FoundationsTypes.precise.DecimalN} $other)) return false",
            code"return decimalValue().compareTo($other.decimalValue()) == 0"
          )
        )
        (obj, TypesJava.Object, stmts)
      case _: LangKotlin =>
        val other2 = jvm.Ident("other")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this === $other2) return true",
            code"if ($other2 !is ${FoundationsTypes.precise.DecimalN}) return false",
            code"return decimalValue().compareTo($other2.decimalValue()) == 0"
          )
        )
        (other2, jvm.Type.KotlinNullable(TypesKotlin.Any), stmts)
      case _: LangScala =>
        val that = jvm.Ident("that")
        val compareExpr = code"$that match { case $other: ${FoundationsTypes.precise.DecimalN} => decimalValue().compareTo($other.decimalValue()) == 0; case _ => false }"
        val expr = jvm.Body.Expr(code"(this eq $that.asInstanceOf[AnyRef]) || ($compareExpr)")
        (that, TypesScala.Any, expr)
      case o => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("equals"),
      params = List(jvm.Param(paramName, paramType)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = body,
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: hashCode() for DecimalN - uses semantic hash for cross-type equality */
  private def hashCodeMethodDecimal(lang: Lang): jvm.Method = {
    val body = lang match {
      case LangJava      => code"decimalValue().stripTrailingZeros().hashCode()"
      case _: LangKotlin => code"decimalValue().stripTrailingZeros().hashCode()"
      case _: LangScala  => code"decimalValue().stripTrailingZeros().hashCode()"
      case o             => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("hashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticEquals(T) for temporal types - compares underlying value */
  private def semanticEqualsMethodTemporal(interfaceType: jvm.Type.Qualified, lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")
    val value = jvm.Ident("value")
    val nullCheck = code"$other == null"
    val equalsCheck = lang match {
      case LangJava      => code"$value.equals($other.rawValue())"
      case _: LangKotlin => code"$value == $other.rawValue()"
      case _: LangScala  => code"$value == $other.rawValue()"
      case o             => sys.error(s"Unsupported language: $o")
    }
    val body = lang.ternary(nullCheck, code"false", equalsCheck)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticEquals"),
      params = List(jvm.Param(other, interfaceType)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: semanticHashCode() for temporal types - hash of underlying value */
  private def semanticHashCodeMethodTemporal(lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val body = lang match {
      case LangJava      => code"$value.hashCode()"
      case _: LangKotlin => code"$value.hashCode()"
      case _: LangScala  => code"$value.hashCode()"
      case o             => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("semanticHashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: equals(Object) for StringN/NonEmptyStringN - uses semantic equality across all StringN types */
  private def equalsMethodStringValue(interfaceType: jvm.Type.Qualified, lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")
    val value = jvm.Ident("value")

    val (paramName, paramType, body) = lang match {
      case LangJava =>
        val obj = jvm.Ident("obj")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this == $obj) return true",
            code"if (!($obj instanceof $interfaceType $other)) return false",
            code"return $value.equals($other.rawValue())"
          )
        )
        (obj, TypesJava.Object, stmts)
      case _: LangKotlin =>
        val other2 = jvm.Ident("other")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this === $other2) return true",
            code"if ($other2 !is $interfaceType) return false",
            code"return $value == $other2.rawValue()"
          )
        )
        (other2, jvm.Type.KotlinNullable(TypesKotlin.Any), stmts)
      case _: LangScala =>
        val that = jvm.Ident("that")
        val compareExpr = code"$that match { case $other: $interfaceType => $value == $other.rawValue(); case _ => false }"
        val expr = jvm.Body.Expr(code"(this eq $that.asInstanceOf[AnyRef]) || ($compareExpr)")
        (that, TypesScala.Any, expr)
      case o => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("equals"),
      params = List(jvm.Param(paramName, paramType)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = body,
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: hashCode() for StringN/NonEmptyStringN - uses semantic hash for cross-type equality */
  private def hashCodeMethodStringValue(lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val body = code"$value.hashCode()"

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("hashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: equals(Object) for PaddedStringN/NonEmptyPaddedStringN - uses semantic equality based on trimmed value */
  private def equalsMethodPaddedString(interfaceType: jvm.Type.Qualified, lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")

    val (paramName, paramType, body) = lang match {
      case LangJava =>
        val obj = jvm.Ident("obj")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this == $obj) return true",
            code"if (!($obj instanceof $interfaceType $other)) return false",
            code"return trimmed().equals($other.trimmed())"
          )
        )
        (obj, TypesJava.Object, stmts)
      case _: LangKotlin =>
        val other2 = jvm.Ident("other")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this === $other2) return true",
            code"if ($other2 !is $interfaceType) return false",
            code"return trimmed() == $other2.trimmed()"
          )
        )
        (other2, jvm.Type.KotlinNullable(TypesKotlin.Any), stmts)
      case _: LangScala =>
        val that = jvm.Ident("that")
        val compareExpr = code"$that match { case $other: $interfaceType => trimmed() == $other.trimmed(); case _ => false }"
        val expr = jvm.Body.Expr(code"(this eq $that.asInstanceOf[AnyRef]) || ($compareExpr)")
        (that, TypesScala.Any, expr)
      case o => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("equals"),
      params = List(jvm.Param(paramName, paramType)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = body,
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: hashCode() for PaddedStringN/NonEmptyPaddedStringN - uses semantic hash based on trimmed value */
  private def hashCodeMethodPaddedString(lang: Lang): jvm.Method = {
    val body = code"trimmed().hashCode()"

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("hashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: equals(Object) for BinaryN - uses semantic equality across all BinaryN types */
  private def equalsMethodBinary(lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")
    val value = jvm.Ident("value")

    val (paramName, paramType, body) = lang match {
      case LangJava =>
        val obj = jvm.Ident("obj")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this == $obj) return true",
            code"if (!($obj instanceof ${FoundationsTypes.precise.BinaryN} $other)) return false",
            code"return ${TypesJava.Arrays}.equals($value, $other.rawValue())"
          )
        )
        (obj, TypesJava.Object, stmts)
      case _: LangKotlin =>
        val other2 = jvm.Ident("other")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this === $other2) return true",
            code"if ($other2 !is ${FoundationsTypes.precise.BinaryN}) return false",
            code"return $value.contentEquals($other2.rawValue())"
          )
        )
        (other2, jvm.Type.KotlinNullable(TypesKotlin.Any), stmts)
      case _: LangScala =>
        val that = jvm.Ident("that")
        val compareExpr = code"$that match { case $other: ${FoundationsTypes.precise.BinaryN} => ${TypesJava.Arrays}.equals($value, $other.rawValue()); case _ => false }"
        val expr = jvm.Body.Expr(code"(this eq $that.asInstanceOf[AnyRef]) || ($compareExpr)")
        (that, TypesScala.Any, expr)
      case o => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("equals"),
      params = List(jvm.Param(paramName, paramType)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = body,
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: hashCode() for BinaryN - uses semantic hash for cross-type equality */
  private def hashCodeMethodBinary(lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val body = lang match {
      case LangJava      => code"${TypesJava.Arrays}.hashCode($value)"
      case _: LangKotlin => code"$value.contentHashCode()"
      case _: LangScala  => code"${TypesJava.Arrays}.hashCode($value)"
      case o             => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("hashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: equals(Object) for temporal types - uses semantic equality across same temporal interface types */
  private def equalsMethodTemporal(interfaceType: jvm.Type.Qualified, lang: Lang): jvm.Method = {
    val other = jvm.Ident("other")
    val value = jvm.Ident("value")

    val (paramName, paramType, body) = lang match {
      case LangJava =>
        val obj = jvm.Ident("obj")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this == $obj) return true",
            code"if (!($obj instanceof $interfaceType $other)) return false",
            code"return $value.equals($other.rawValue())"
          )
        )
        (obj, TypesJava.Object, stmts)
      case _: LangKotlin =>
        val other2 = jvm.Ident("other")
        val stmts = jvm.Body.Stmts(
          List(
            code"if (this === $other2) return true",
            code"if ($other2 !is $interfaceType) return false",
            code"return $value == $other2.rawValue()"
          )
        )
        (other2, jvm.Type.KotlinNullable(TypesKotlin.Any), stmts)
      case _: LangScala =>
        val that = jvm.Ident("that")
        val compareExpr = code"$that match { case $other: $interfaceType => $value == $other.rawValue(); case _ => false }"
        val expr = jvm.Body.Expr(code"(this eq $that.asInstanceOf[AnyRef]) || ($compareExpr)")
        (that, TypesScala.Any, expr)
      case o => sys.error(s"Unsupported language: $o")
    }

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("equals"),
      params = List(jvm.Param(paramName, paramType)),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = body,
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method: hashCode() for temporal types - uses semantic hash for cross-type equality */
  private def hashCodeMethodTemporal(lang: Lang): jvm.Method = {
    val value = jvm.Ident("value")
    val body = code"$value.hashCode()"

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("hashCode"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.primitiveInt,
      throws = Nil,
      body = jvm.Body.Expr(body),
      isOverride = true,
      isDefault = false
    )
  }

  /** Instance method rawValue() - implements the interface method to return the underlying value. */
  private def rawValueMethod(valueType: jvm.Type): jvm.Method = {
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("rawValue"),
      params = Nil,
      implicitParams = Nil,
      tpe = valueType,
      throws = Nil,
      body = jvm.Body.Expr(jvm.Ident("value").code),
      isOverride = true,
      isDefault = false
    )
  }
}
