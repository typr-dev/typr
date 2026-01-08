package typr
package internal

import typr.db.PgType
import typr.internal.codegen.*
import typr.internal.compat.*
import typr.internal.pg.OpenEnum

case class ComputedTestInserts(tpe: jvm.Type.Qualified, methods: List[ComputedTestInserts.InsertMethod], maybeDomainMethods: Option[ComputedTestInserts.GenerateDomainMethods])

object ComputedTestInserts {
  val random: jvm.Ident = jvm.Ident("random")
  val domainInsert: jvm.Ident = jvm.Ident("domainInsert")

  def apply(
      projectName: String,
      options: InternalOptions,
      lang: Lang,
      // global data
      customTypes: CustomTypes,
      domains: List[ComputedDomain],
      enums: List[ComputedStringEnum],
      mariaSets: List[ComputedMariaSet],
      allTablesByName: Map[db.RelationName, ComputedTable],
      // project data
      tables: Iterable[ComputedTable]
  ): ComputedTestInserts = {
    val enumsByName: Map[jvm.Type, ComputedStringEnum] =
      enums.iterator.map(x => x.tpe -> x).toMap

    val mariaSetsByType: Map[jvm.Type, ComputedMariaSet] =
      mariaSets.iterator.map(x => (x.tpe: jvm.Type) -> x).toMap

    val openEnumsByType: Map[jvm.Type, OpenEnum] =
      tables.flatMap { table => table.maybeId.collect { case x: IdComputed.UnaryOpenEnum => (x.tpe, x.openEnum) } }.toMap

    val maybeDomainMethods: Option[GenerateDomainMethods] =
      GenerateDomainMethod
        .of(domains, tables)
        .map { methods =>
          val tpe = jvm.Type.Qualified(options.pkg / jvm.Ident(s"${Naming.titleCase(projectName)}TestDomainInsert"))
          GenerateDomainMethods(tpe, methods)
        }

    def defaultFor(table: ComputedTable, tpe: jvm.Type, dbType: db.Type) = {
      val r = random.code
      def defaultLocalDate = code"${TypesJava.LocalDate}.ofEpochDay(${lang.toLong(lang.Random.nextIntBounded(r, code"30000"))})"
      def defaultLocalTime = code"${TypesJava.LocalTime}.ofSecondOfDay(${lang.toLong(lang.Random.nextIntBounded(r, code"24 * 60 * 60"))})"
      def defaultLocalDateTime = code"${TypesJava.LocalDateTime}.of($defaultLocalDate, $defaultLocalTime)"
      def defaultZoneOffset = code"${TypesJava.ZoneOffset}.ofHours(${lang.Random.nextIntBounded(r, code"24")} - 12)"

      def go(tpe: jvm.Type, dbType: db.Type, tableUnaryId: Option[IdComputed.Unary]): Option[jvm.Code] =
        tpe match {
          case tpe if tableUnaryId.exists(_.tpe == tpe) =>
            tableUnaryId.get match {
              case x: IdComputed.UnaryNormal =>
                go(x.underlying, x.col.dbCol.tpe, None).map(default => jvm.New(x.tpe, List(jvm.Arg.Pos(default))).code)
              case x: IdComputed.UnaryInherited => go(x.underlying, x.col.dbCol.tpe, None)
              case x: IdComputed.UnaryNoIdType  => go(x.underlying, x.col.dbCol.tpe, None)
              case x: IdComputed.UnaryOpenEnum =>
                go(x.underlying, x.col.dbCol.tpe, None).map(default => code"${x.tpe}.apply($default)")
              case _: IdComputed.UnaryUserSpecified => None
            }
          case tpe if openEnumsByType.contains(tpe) =>
            val openEnum = openEnumsByType(tpe)
            val numValues = openEnum.values.length
            val idx = lang.Random.nextIntBounded(r, code"$numValues")
            // Use language-native list access syntax
            lang match {
              case _: LangScala =>
                // Scala: All is a Scala List, use list(idx) syntax
                Some(code"$tpe.All($idx)")
              case LangJava =>
                // Java: Arrays.asList().get(idx)
                Some(code"${TypesJava.Arrays}.asList($tpe.Known.values()).get($idx)")
              case _: LangKotlin =>
                // Kotlin: entries[idx]
                Some(code"$tpe.Known.entries[$idx]")
              case other =>
                sys.error(s"Unexpected language: $other")
            }

          // Precise types - wrapper types with constraints
          case q: jvm.Type.Qualified if q.value.idents.exists(_.value == "precisetypes") =>
            val typeName = q.value.name.value
            if (typeName.startsWith("NonEmptyString") && typeName != "NonEmptyString") {
              val maxLength = typeName.stripPrefix("NonEmptyString").toIntOption.getOrElse(20).min(20)
              val str = lang.Random.alphanumeric(r, code"${math.max(1, maxLength)}")
              Some(code"$q.truncate($str)")
            } else if (typeName == "NonEmptyString") {
              val str = lang.Random.alphanumeric(r, code"10")
              Some(code"$q.unsafeForce($str)")
            } else if (typeName.startsWith("String")) {
              val maxLength = typeName.stripPrefix("String").toIntOption.getOrElse(20).min(20)
              val str = lang.Random.alphanumeric(r, code"$maxLength")
              Some(code"$q.truncate($str)")
            } else if (typeName.startsWith("Binary")) {
              val maxLength = typeName.stripPrefix("Binary").toIntOption.getOrElse(20).min(100)
              Some(code"$q.unsafeForce(${lang.Random.randomBytes(r, code"$maxLength")})")
            } else if (typeName.startsWith("Int")) {
              // Parse precision from name like Int10, Int18
              // Generate values that fit within the precision (max digits)
              val precision = typeName.stripPrefix("Int").toIntOption.getOrElse(10)
              // Cap at 9 digits to stay within int range for simplicity
              val maxDigits = math.min(precision, 9)
              val bound = math.pow(10, maxDigits).toInt
              Some(code"$q.unsafeForce(${TypesJava.BigInteger}.valueOf(${lang.toLong(code"Math.abs(${lang.Random.nextInt(r)}) % $bound")}))")
            } else if (typeName.startsWith("Decimal")) {
              // Parse precision and scale from name like Decimal10_2, Decimal18_4
              val parts = typeName.stripPrefix("Decimal").split("_")
              val precision = parts.headOption.flatMap(_.toIntOption).getOrElse(10)
              val scale = parts.lift(1).flatMap(_.toIntOption).getOrElse(2)
              // Generate a value that fits: e.g. for Decimal(10,2) generate up to 8 integer digits
              val maxIntDigits = math.min(precision - scale, 6) // Keep it small for safety
              val intBound = math.pow(10, maxIntDigits).toInt
              // Generate integer part + fractional part within scale
              val scaleBound = math.pow(10, scale).toInt
              Some(code"$q.unsafeForce(${TypesJava.BigDecimal}.valueOf(${lang.toLong(code"Math.abs(${lang.Random.nextInt(r)}) % $intBound")}).add(${TypesJava.BigDecimal}.valueOf(${lang
                  .toLong(code"Math.abs(${lang.Random.nextInt(r)}) % $scaleBound")}).movePointLeft($scale)))")
            } else if (typeName.startsWith("LocalDateTime")) {
              Some(code"$q.of($defaultLocalDateTime)")
            } else if (typeName.startsWith("LocalTime")) {
              Some(code"$q.of($defaultLocalTime)")
            } else if (typeName.startsWith("OffsetDateTime")) {
              Some(code"$q.of(${TypesJava.OffsetDateTime}.of($defaultLocalDateTime, $defaultZoneOffset))")
            } else if (typeName.startsWith("Instant")) {
              Some(code"$q.of(${TypesJava.Instant}.ofEpochMilli(1000000000000L + ${lang.Random.nextLongBounded(r, code"1000000000000L")}))")
            } else {
              None
            }

          case TypesJava.String =>
            val max: Int =
              Option(dbType)
                .collect {
                  // PostgreSQL
                  case db.PgType.VarChar(Some(maxLength)) => maxLength
                  case db.PgType.Bpchar(Some(maxLength))  => maxLength
                  // SQL Server
                  case db.SqlServerType.Char(Some(maxLength))     => maxLength
                  case db.SqlServerType.VarChar(Some(maxLength))  => maxLength
                  case db.SqlServerType.NChar(Some(maxLength))    => maxLength
                  case db.SqlServerType.NVarChar(Some(maxLength)) => maxLength
                  // MariaDB/MySQL
                  case db.MariaType.Char(Some(maxLength))    => maxLength
                  case db.MariaType.VarChar(Some(maxLength)) => maxLength
                  // DB2
                  case db.DB2Type.Char(Some(maxLength))    => maxLength
                  case db.DB2Type.VarChar(Some(maxLength)) => maxLength
                  // DuckDB
                  case db.DuckDbType.VarChar(Some(maxLength)) => maxLength
                }
                .getOrElse(20)
                .min(20)
            Some(lang.Random.alphanumeric(r, code"$max"))
          case lang.Boolean    => Some(lang.Random.nextBoolean(r))
          case TypesScala.Char => Some(lang.Random.nextPrintableChar(r))
          case lang.Byte       => Some(lang.toByte(lang.Random.nextIntBounded(r, lang.maxValue(lang.Byte))))
          case lang.Short      => Some(lang.toShort(lang.Random.nextIntBounded(r, lang.maxValue(lang.Short))))
          case lang.Int =>
            dbType match {
              case db.PgType.Int2                 => Some(lang.Random.nextIntBounded(r, lang.maxValue(lang.Short)))
              case db.MariaType.MediumInt         => Some(lang.Random.nextIntBounded(r, code"8388607"))
              case db.MariaType.MediumIntUnsigned => Some(lang.Random.nextIntBounded(r, code"16777215"))
              case _                              => Some(lang.Random.nextInt(r))
            }
          case lang.Long       => Some(lang.Random.nextLong(r))
          case lang.Float      => Some(lang.Random.nextFloat(r))
          case lang.Double     => Some(lang.Random.nextDouble(r))
          case lang.BigDecimal => Some(lang.bigDecimalFromDouble(lang.Random.nextDouble(r)))
          case TypesJava.UUID  => Some(lang.Random.randomUUID(r))
          // Raw Java date/time types (used by DbLib.Typo)
          case TypesJava.LocalDate     => Some(defaultLocalDate)
          case TypesJava.LocalTime     => Some(defaultLocalTime)
          case TypesJava.LocalDateTime => Some(defaultLocalDateTime)
          case TypesJava.OffsetTime    => Some(code"$defaultLocalTime.atOffset($defaultZoneOffset)")
          case TypesJava.OffsetDateTime =>
            Some(code"${TypesJava.OffsetDateTime}.of($defaultLocalDateTime, $defaultZoneOffset)")
          case TypesJava.Instant =>
            Some(code"${TypesJava.Instant}.ofEpochMilli(1000000000000L + ${lang.Random.nextLongBounded(r, code"1000000000000L")})")
          // foundations-jdbc runtime types
          case TypesJava.runtime.Inet =>
            // Use string interpolation to build IP address
            val ipStr = lang.s(code"$${${lang.Random.nextIntBounded(r, code"256")}}.$${${lang.Random.nextIntBounded(r, code"256")}}.$${${lang.Random
                .nextIntBounded(r, code"256")}}.$${${lang.Random.nextIntBounded(r, code"256")}}")
            Some(jvm.New(TypesJava.runtime.Inet, List(jvm.Arg.Pos(ipStr))).code)
          case TypesJava.runtime.Json =>
            Some(jvm.New(TypesJava.runtime.Json, List(jvm.Arg.Pos(lang.stringLiteral("{}")))).code)
          case TypesJava.runtime.Jsonb =>
            Some(jvm.New(TypesJava.runtime.Jsonb, List(jvm.Arg.Pos(lang.stringLiteral("{}")))).code)
          case TypesJava.runtime.Money =>
            Some(jvm.New(TypesJava.runtime.Money, List(jvm.Arg.Pos(lang.Random.nextDouble(r)))).code)
          case TypesJava.runtime.Xml =>
            Some(jvm.New(TypesJava.runtime.Xml, List(jvm.Arg.Pos(lang.stringLiteral("<root/>")))).code)
          case TypesJava.runtime.Int2Vector =>
            // Use string constructor for simplicity: "1 2 3"
            Some(jvm.New(TypesJava.runtime.Int2Vector, List(jvm.Arg.Pos(lang.stringLiteral("1 2 3")))).code)
          case TypesJava.runtime.Vector =>
            // Use string constructor for simplicity: "[1.0,2.0,3.0]"
            Some(jvm.New(TypesJava.runtime.Vector, List(jvm.Arg.Pos(lang.stringLiteral("[1.0,2.0,3.0]")))).code)
          // Unsigned integer types
          case TypesJava.unsigned.Uint1 =>
            // 0-255
            Some(code"${TypesJava.unsigned.Uint1}.of(${lang.Random.nextIntBounded(r, code"256")})")
          case TypesJava.unsigned.Uint2 =>
            // 0-65535
            Some(code"${TypesJava.unsigned.Uint2}.of(${lang.Random.nextIntBounded(r, code"65536")})")
          case TypesJava.unsigned.Uint4 =>
            dbType match {
              case db.MariaType.MediumIntUnsigned =>
                // 0-16777215 for MEDIUMINT UNSIGNED
                Some(code"${TypesJava.unsigned.Uint4}.of(${lang.toLong(lang.Random.nextIntBounded(r, code"16777216"))})")
              case _ =>
                // 0-4294967295, use positive int range for practical tests
                val Math = jvm.Type.Qualified("java.lang.Math")
                Some(code"${TypesJava.unsigned.Uint4}.of(${lang.toLong(code"$Math.abs(${lang.Random.nextInt(r)})")})")
            }
          case TypesJava.unsigned.Uint8 =>
            // 0-18446744073709551615, use positive long range for practical tests
            val Math = jvm.Type.Qualified("java.lang.Math")
            Some(code"${TypesJava.unsigned.Uint8}.of($Math.abs(${lang.Random.nextLong(r)}))")
          // PostgreSQL geometric types
          case TypesJava.PGpoint =>
            Some(jvm.New(TypesJava.PGpoint, List(jvm.Arg.Pos(lang.Random.nextDouble(r)), jvm.Arg.Pos(lang.Random.nextDouble(r)))).code)
          case TypesJava.PGbox =>
            Some(
              jvm
                .New(
                  TypesJava.PGbox,
                  List(
                    jvm.Arg.Pos(lang.Random.nextDouble(r)),
                    jvm.Arg.Pos(lang.Random.nextDouble(r)),
                    jvm.Arg.Pos(lang.Random.nextDouble(r)),
                    jvm.Arg.Pos(lang.Random.nextDouble(r))
                  )
                )
                .code
            )
          case TypesJava.PGcircle =>
            Some(jvm.New(TypesJava.PGcircle, List(jvm.Arg.Pos(lang.Random.nextDouble(r)), jvm.Arg.Pos(lang.Random.nextDouble(r)), jvm.Arg.Pos(lang.Random.nextDouble(r)))).code)
          case TypesJava.PGline =>
            Some(jvm.New(TypesJava.PGline, List(jvm.Arg.Pos(lang.Random.nextDouble(r)), jvm.Arg.Pos(lang.Random.nextDouble(r)), jvm.Arg.Pos(lang.Random.nextDouble(r)))).code)
          case TypesJava.PGlseg =>
            Some(
              jvm
                .New(
                  TypesJava.PGlseg,
                  List(
                    jvm.Arg.Pos(lang.Random.nextDouble(r)),
                    jvm.Arg.Pos(lang.Random.nextDouble(r)),
                    jvm.Arg.Pos(lang.Random.nextDouble(r)),
                    jvm.Arg.Pos(lang.Random.nextDouble(r))
                  )
                )
                .code
            )
          case TypesJava.PGpath =>
            val pointArg = jvm.New(TypesJava.PGpoint, List(jvm.Arg.Pos(lang.Random.nextDouble(r)), jvm.Arg.Pos(lang.Random.nextDouble(r)))).code
            Some(jvm.New(TypesJava.PGpath, List(jvm.Arg.Pos(lang.typedArrayOf(TypesJava.PGpoint, List(pointArg))), jvm.Arg.Pos(code"false"))).code)
          case TypesJava.PGpolygon =>
            val points = List(
              jvm.New(TypesJava.PGpoint, List(jvm.Arg.Pos(code"0.0"), jvm.Arg.Pos(code"0.0"))).code,
              jvm.New(TypesJava.PGpoint, List(jvm.Arg.Pos(code"1.0"), jvm.Arg.Pos(code"0.0"))).code,
              jvm.New(TypesJava.PGpoint, List(jvm.Arg.Pos(code"1.0"), jvm.Arg.Pos(code"1.0"))).code,
              jvm.New(TypesJava.PGpoint, List(jvm.Arg.Pos(code"0.0"), jvm.Arg.Pos(code"1.0"))).code
            )
            Some(jvm.New(TypesJava.PGpolygon, List(jvm.Arg.Pos(lang.typedArrayOf(TypesJava.PGpoint, points)))).code)
          case TypesJava.PGInterval =>
            Some(
              jvm
                .New(
                  TypesJava.PGInterval,
                  List(
                    jvm.Arg.Pos(lang.Random.nextIntBounded(r, code"10")),
                    jvm.Arg.Pos(lang.Random.nextIntBounded(r, code"12")),
                    jvm.Arg.Pos(lang.Random.nextIntBounded(r, code"28")),
                    jvm.Arg.Pos(lang.Random.nextIntBounded(r, code"24")),
                    jvm.Arg.Pos(lang.Random.nextIntBounded(r, code"60")),
                    jvm.Arg.Pos(code"${lang.Random.nextDouble(r)} * 60")
                  )
                )
                .code
            )
          // Map (for hstore)
          case jvm.Type.TApply(TypesJava.Map, List(TypesJava.String, TypesJava.String)) =>
            Some(code"${TypesJava.Map}.of()")
          case lang.Optional(underlying) =>
            go(underlying, dbType, tableUnaryId) match {
              case None          => Some(lang.Optional.none)
              case Some(default) => Some(lang.ternary(lang.Random.nextBoolean(r), lang.Optional.none, lang.Optional.some(default)))
            }
          case jvm.Type.ArrayOf(underlying) =>
            dbType match {
              case db.PgType.Array(underlyingDb) =>
                go(underlying, underlyingDb, tableUnaryId).map { default =>
                  lang.arrayFill(lang.Random.nextIntBounded(r, code"3"), default, underlying)
                }
              case _ => None
            }

          case customTypes.TypoShort.typoType =>
            Some(code"${customTypes.TypoShort.typoType}(${lang.toShort(lang.Random.nextIntBounded(r, lang.maxValue(lang.Short)))})")
          case customTypes.TypoLocalDate.typoType =>
            Some(code"${customTypes.TypoLocalDate.typoType}($defaultLocalDate)")
          case customTypes.TypoLocalTime.typoType =>
            Some(code"${customTypes.TypoLocalTime.typoType}($defaultLocalTime)")
          case customTypes.TypoLocalDateTime.typoType =>
            Some(code"${customTypes.TypoLocalDateTime.typoType}($defaultLocalDateTime)")
          case customTypes.TypoOffsetTime.typoType =>
            Some(code"${customTypes.TypoOffsetTime.typoType}($defaultLocalTime.atOffset($defaultZoneOffset))")
          case customTypes.TypoUUID.typoType =>
            Some(code"${customTypes.TypoUUID.typoType}.randomUUID")
          case customTypes.TypoInstant.typoType =>
            // 2001-09-09T01:46:40Z -> 2033-05-18T03:33:20Z
            Some(code"${customTypes.TypoInstant.typoType}(${TypesJava.Instant}.ofEpochMilli(1000000000000L + ${lang.Random.nextLongBounded(r, code"1000000000000L")}))")
          case jvm.Type.TApply(table.default.Defaulted, _) =>
            val UseDefaultQualified = jvm.Type.Qualified(table.default.Defaulted.value / table.default.UseDefault)
            Some(jvm.New(UseDefaultQualified, Nil).code)
          case tpe if maybeDomainMethods.exists(_.domainMethodByType.contains(tpe)) =>
            val method = maybeDomainMethods.get.domainMethodByType(tpe)
            Some(code"$domainInsert.${method.name}($random)")
          case tpe if enumsByName.contains(tpe) =>
            val all = lang.enumAll(tpe)
            // For Scala, enums use Scala List which needs Scala syntax (apply, length)
            // For Java/Kotlin, the list from enumAll is their native list type
            lang match {
              case _: LangScala =>
                // Scala: list(idx), list.length
                Some(code"$all(${lang.Random.nextIntBounded(r, code"$all.length")})")
              case LangJava =>
                // Java: list.get(idx), list.size()
                Some(code"$all.get(${lang.Random.nextIntBounded(r, code"$all.size()")})")
              case _: LangKotlin =>
                // Kotlin: list[idx], list.size
                Some(code"$all[${lang.Random.nextIntBounded(r, code"$all.size")}]")
              case other =>
                sys.error(s"Unexpected language: $other")
            }
          case tpe if mariaSetsByType.contains(tpe) =>
            // Generate SET with first member
            val mariaSet = mariaSetsByType(tpe)
            val firstMember = mariaSet.members.head._1
            val memberEnumType = jvm.Type.Qualified(mariaSet.tpe.value.parentOpt.get / jvm.Ident(mariaSet.tpe.name.value + "Member"))
            lang match {
              case _: LangScala =>
                Some(code"$tpe.of(${TypesScala.List}($memberEnumType.$firstMember))")
              case LangJava =>
                Some(code"$tpe.of(${TypesJava.List}.of($memberEnumType.$firstMember))")
              case _: LangKotlin =>
                Some(code"$tpe.of(listOf($memberEnumType.$firstMember))")
              case other =>
                sys.error(s"Unexpected language: $other")
            }
          case _ =>
            None
        }

      go(jvm.Type.base(tpe), dbType, table.maybeId.collect { case x: IdComputed.Unary => x })
    }

    new ComputedTestInserts(
      jvm.Type.Qualified(options.pkg / jvm.Ident(s"${Naming.titleCase(projectName)}TestInsert")),
      tables.collect {
        case table if !options.readonlyRepo.include(table.dbTable.name) =>
          val hasConstraints: Set[db.ColName] =
            table.dbTable.cols.iterator.flatMap(_.constraints.flatMap(_.columns)).toSet
          val pkColumns: Set[db.ColName] =
            table.maybeId.iterator.flatMap(_.cols.iterator.map(_.dbCol.name)).toSet
          val appearsInFkButNotPk: Set[db.ColName] =
            table.dbTable.foreignKeys.iterator.flatMap(_.cols.toList).filterNot(pkColumns).toSet

          def defaultedParametersFor(cols: List[ComputedColumn]): List[jvm.Param[jvm.Type]] = {
            val params = cols.map { col =>
              val isMeaningful = hasConstraints(col.dbName) || appearsInFkButNotPk(col.dbName)
              val isOpenEnum = openEnumsByType.contains(col.tpe)
              val default = if (isMeaningful && !col.dbCol.isDefaulted && !isOpenEnum) {
                if (col.dbCol.nullability == Nullability.NoNulls) None else Some(lang.Optional.none)
              } else defaultFor(table, col.tpe, col.dbCol.tpe)
              jvm.Param(col.name, col.tpe).copy(default = default)
            }

            // keep order but pull all required parameters first
            val (requiredParams, optionalParams) = params.partition(_.default.isEmpty)
            requiredParams ++ optionalParams
          }

          FkAnalysis(allTablesByName, table, options.lang).createWithFkIdsUnsavedRowOrRow match {
            case Some(colsFromFks) =>
              val valuesFromFk: List[(jvm.Ident, jvm.Code)] =
                colsFromFks.allColumns.toList.map { col =>
                  val expr: jvm.Code =
                    colsFromFks.exprForColumn.get(col.name) match {
                      case Some(expr) =>
                        if (col.dbCol.isDefaulted && col.dbCol.nullability != Nullability.NoNulls)
                          code"${table.default.Defaulted}.${table.default.Provided}(${lang.Optional.some(expr)})"
                        else if (col.dbCol.isDefaulted)
                          code"${table.default.Defaulted}.${table.default.Provided}($expr)"
                        else if (col.dbCol.nullability != Nullability.NoNulls)
                          lang.Optional.some(expr)
                        else
                          expr

                      case None => col.name.code
                    }

                  (col.name, expr)
                }

              val params = defaultedParametersFor(colsFromFks.remainingColumns)

              ComputedTestInserts.InsertMethod(table, colsFromFks.params ++ params, valuesFromFk)

            case None =>
              val cols: List[ComputedColumn] =
                table.maybeUnsavedRow match {
                  case Some(unsaved) => unsaved.unsavedCols.toList
                  case None          => table.cols.toList
                }
              val params: List[jvm.Param[jvm.Type]] = defaultedParametersFor(cols)
              val values: List[(jvm.Ident, jvm.Code)] =
                cols.map(p => (p.name, p.name.code))

              ComputedTestInserts.InsertMethod(table, params, values)
          }

      }.toList,
      maybeDomainMethods
    )
  }

  case class InsertMethod(table: ComputedTable, params: List[jvm.Param[jvm.Type]], values: List[(jvm.Ident, jvm.Code)]) {
    val name: jvm.Ident =
      table.dbTable.name match {
        case db.RelationName(Some(schema), name) => jvm.Ident(s"${Naming.camelCase(schema)}${Naming.titleCase(name)}")
        case db.RelationName(None, name)         => jvm.Ident(Naming.titleCase(name))
      }

    val cls = table.maybeUnsavedRow match {
      case Some(unsaved) => unsaved.tpe
      case None          => table.names.RowName
    }
  }

  case class GenerateDomainMethods(tpe: jvm.Type.Qualified, methods: NonEmptyList[ComputedTestInserts.GenerateDomainMethod]) {
    val domainMethodByType: Map[jvm.Type, GenerateDomainMethod] =
      methods.iterator.map(x => (x.dom.tpe: jvm.Type) -> x).toMap
  }

  case class GenerateDomainMethod(dom: ComputedDomain) {
    val name: jvm.Ident =
      dom.underlying.name match {
        case db.RelationName(Some(schema), name) => jvm.Ident(s"${Naming.camelCase(schema)}${Naming.titleCase(name)}")
        case db.RelationName(None, name)         => jvm.Ident(Naming.titleCase(name))
      }
  }

  object GenerateDomainMethod {
    def of(domains: List[ComputedDomain], computedTables: Iterable[ComputedTable]): Option[NonEmptyList[GenerateDomainMethod]] = {
      val computedDomainByType: Map[db.RelationName, ComputedDomain] =
        domains.iterator.map(x => x.underlying.name -> x).toMap

      val all = for {
        table <- computedTables
        col <- table.cols.toList
        domain <- col.dbCol.tpe match {
          case PgType.DomainRef(name, _, _) => computedDomainByType.get(name)
          case _                            => None
        }
      } yield GenerateDomainMethod(domain)

      NonEmptyList.fromList(all.toList.distinctByCompat(_.dom.tpe).sortBy(_.dom.tpe))
    }
  }
}
