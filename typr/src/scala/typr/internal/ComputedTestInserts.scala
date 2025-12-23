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
      allTablesByName: Map[db.RelationName, ComputedTable],
      // project data
      tables: Iterable[ComputedTable]
  ): ComputedTestInserts = {
    val enumsByName: Map[jvm.Type, ComputedStringEnum] =
      enums.iterator.map(x => x.tpe -> x).toMap

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
      def defaultLocalDate = code"${TypesJava.LocalDate}.ofEpochDay(${lang.Random.nextIntBounded(r, code"30000")}.toLong)"
      def defaultLocalTime = code"${TypesJava.LocalTime}.ofSecondOfDay(${lang.Random.nextIntBounded(r, code"24 * 60 * 60")}.toLong)"
      def defaultLocalDateTime = code"${TypesJava.LocalDateTime}.of($defaultLocalDate, $defaultLocalTime)"
      def defaultZoneOffset = code"${TypesJava.ZoneOffset}.ofHours(${lang.Random.nextIntBounded(r, code"24")} - 12)"

      def go(tpe: jvm.Type, dbType: db.Type, tableUnaryId: Option[IdComputed.Unary]): Option[jvm.Code] =
        tpe match {
          case tpe if tableUnaryId.exists(_.tpe == tpe) =>
            tableUnaryId.get match {
              case x: IdComputed.UnaryNormal        => go(x.underlying, x.col.dbCol.tpe, None).map(default => code"${x.tpe}($default)")
              case x: IdComputed.UnaryInherited     => go(x.underlying, x.col.dbCol.tpe, None)
              case x: IdComputed.UnaryNoIdType      => go(x.underlying, x.col.dbCol.tpe, None)
              case x: IdComputed.UnaryOpenEnum      => go(x.underlying, x.col.dbCol.tpe, None).map(default => code"${x.tpe}($default)")
              case _: IdComputed.UnaryUserSpecified => None
            }
          case tpe if openEnumsByType.contains(tpe) =>
            val openEnum = openEnumsByType(tpe)
            Some(code"${tpe}.All(${lang.Random.nextIntBounded(r, code"${openEnum.values.length}")})")
          case TypesJava.String =>
            val max: Int =
              Option(dbType)
                .collect {
                  case db.PgType.VarChar(Some(maxLength)) => maxLength
                  case db.PgType.Bpchar(Some(maxLength))  => maxLength
                }
                .getOrElse(20)
                .min(20)
            Some(lang.Random.alphanumeric(r, code"$max"))
          case lang.Boolean    => Some(lang.Random.nextBoolean(r))
          case TypesScala.Char => Some(lang.Random.nextPrintableChar(r))
          case lang.Byte       => Some(code"${lang.Random.nextIntBounded(r, lang.maxValue(lang.Byte))}.toByte")
          case lang.Short      => Some(code"${lang.Random.nextIntBounded(r, lang.maxValue(lang.Short))}.toShort")
          case lang.Int =>
            dbType match {
              case db.PgType.Int2 => Some(lang.Random.nextIntBounded(r, lang.maxValue(lang.Short)))
              case _              => Some(lang.Random.nextInt(r))
            }
          case lang.Long       => Some(lang.Random.nextLong(r))
          case lang.Float      => Some(lang.Random.nextFloat(r))
          case lang.Double     => Some(lang.Random.nextDouble(r))
          case lang.BigDecimal => Some(lang.bigDecimalFromDouble(lang.Random.nextDouble(r)))
          case TypesJava.UUID  => Some(code"${TypesJava.UUID}.nameUUIDFromBytes{val bs = ${lang.newByteArray(code"16")}; ${lang.Random.nextBytes(r, code"bs")}; bs}")
          case lang.Optional(underlying) =>
            go(underlying, dbType, tableUnaryId) match {
              case None          => Some(lang.Optional.none)
              case Some(default) => Some(code"if (${lang.Random.nextBoolean(r)}) ${lang.Optional.none} else ${lang.Optional.some(default)}")
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
            Some(code"${customTypes.TypoShort.typoType}(${lang.Random.nextIntBounded(r, lang.maxValue(lang.Short))}.toShort)")
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
            Some(code"${table.default.Defaulted}.${table.default.UseDefault}()")
          case tpe if maybeDomainMethods.exists(_.domainMethodByType.contains(tpe)) =>
            val method = maybeDomainMethods.get.domainMethodByType(tpe)
            Some(code"$domainInsert.${method.name}($random)")
          case tpe if enumsByName.contains(tpe) =>
            Some(code"$tpe.All(${lang.Random.nextIntBounded(r, code"$tpe.All.length")})")
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
