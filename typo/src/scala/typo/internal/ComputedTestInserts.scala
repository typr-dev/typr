package typo
package internal

import typo.db.Type
import typo.internal.codegen.*
import typo.internal.compat.*
import typo.internal.metadb.OpenEnum

case class ComputedTestInserts(tpe: sc.Type.Qualified, methods: List[ComputedTestInserts.InsertMethod], maybeDomainMethods: Option[ComputedTestInserts.GenerateDomainMethods])

object ComputedTestInserts {
  val random: sc.Ident = sc.Ident("random")
  val domainInsert: sc.Ident = sc.Ident("domainInsert")

  def apply(
      projectName: String,
      options: InternalOptions,
      // global data
      customTypes: CustomTypes,
      domains: List[ComputedDomain],
      enums: List[ComputedStringEnum],
      allTablesByName: Map[db.RelationName, ComputedTable],
      // project data
      tables: Iterable[ComputedTable]
  ): ComputedTestInserts = {
    val enumsByName: Map[sc.Type, ComputedStringEnum] =
      enums.iterator.map(x => x.tpe -> x).toMap

    val openEnumsByType: Map[sc.Type, OpenEnum] =
      tables.flatMap { table => table.maybeId.collect { case x: IdComputed.UnaryOpenEnum => (x.tpe, x.openEnum) } }.toMap

    val maybeDomainMethods: Option[GenerateDomainMethods] =
      GenerateDomainMethod
        .of(domains, tables)
        .map { methods =>
          val tpe = sc.Type.Qualified(options.pkg / sc.Ident(s"${Naming.titleCase(projectName)}TestDomainInsert"))
          GenerateDomainMethods(tpe, methods)
        }

    def defaultFor(table: ComputedTable, tpe: sc.Type, dbType: db.Type) = {
      def defaultLocalDate = code"${TypesJava.LocalDate}.ofEpochDay($random.nextInt(30000).toLong)"
      def defaultLocalTime = code"${TypesJava.LocalTime}.ofSecondOfDay($random.nextInt(24 * 60 * 60).toLong)"
      def defaultLocalDateTime = code"${TypesJava.LocalDateTime}.of($defaultLocalDate, $defaultLocalTime)"
      def defaultZoneOffset = code"${TypesJava.ZoneOffset}.ofHours($random.nextInt(24) - 12)"

      def go(tpe: sc.Type, dbType: db.Type, tableUnaryId: Option[IdComputed.Unary]): Option[sc.Code] =
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
            Some(code"${tpe}.All($random.nextInt(${openEnum.values.length}))")
          case TypesJava.String =>
            val max: Int =
              Option(dbType)
                .collect {
                  case db.Type.VarChar(Some(maxLength)) => maxLength
                  case db.Type.Bpchar(Some(maxLength))  => maxLength
                }
                .getOrElse(20)
                .min(20)
            Some(code"$random.alphanumeric.take($max).mkString")
          case TypesScala.Boolean => Some(code"$random.nextBoolean()")
          case TypesScala.Char    => Some(code"$random.nextPrintableChar()")
          case TypesScala.Byte    => Some(code"$random.nextInt(${TypesScala.Byte}.MaxValue).toByte")
          case TypesScala.Short   => Some(code"$random.nextInt(${TypesScala.Short}.MaxValue).toShort")
          case TypesScala.Int =>
            dbType match {
              case db.Type.Int2 => Some(code"$random.nextInt(${TypesScala.Short}.MaxValue)")
              case _            => Some(code"$random.nextInt()")
            }
          case TypesScala.Long       => Some(code"$random.nextLong()")
          case TypesScala.Float      => Some(code"$random.nextFloat()")
          case TypesScala.Double     => Some(code"$random.nextDouble()")
          case TypesScala.BigDecimal => Some(code"${TypesScala.BigDecimal}.decimal($random.nextDouble())")
          case TypesJava.UUID        => Some(code"${TypesJava.UUID}.nameUUIDFromBytes{val bs = ${TypesScala.Array}.ofDim[${TypesScala.Byte}](16); $random.nextBytes(bs); bs}")
          case TypesScala.Optional(underlying) =>
            go(underlying, dbType, tableUnaryId) match {
              case None          => Some(TypesScala.None.code)
              case Some(default) => Some(code"if ($random.nextBoolean()) ${TypesScala.None} else ${TypesScala.Some}($default)")
            }
          case sc.Type.ArrayOf(underlying) =>
            dbType match {
              case db.Type.Array(underlyingDb) =>
                go(underlying, underlyingDb, tableUnaryId).map { default =>
                  code"${TypesScala.Array}.fill(random.nextInt(3))($default)"
                }
              case _ => None
            }

          case customTypes.TypoShort.typoType =>
            Some(code"${customTypes.TypoShort.typoType}($random.nextInt(Short.MaxValue).toShort)")
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
            Some(code"${customTypes.TypoInstant.typoType}(${TypesJava.Instant}.ofEpochMilli(1000000000000L + $random.nextLong(1000000000000L)))")
          case sc.Type.TApply(table.default.Defaulted, _) =>
            Some(code"${table.default.Defaulted}.${table.default.UseDefault}")
          case tpe if maybeDomainMethods.exists(_.domainMethodByType.contains(tpe)) =>
            val method = maybeDomainMethods.get.domainMethodByType(tpe)
            Some(code"$domainInsert.${method.name}($random)")
          case tpe if enumsByName.contains(tpe) =>
            Some(code"$tpe.All($random.nextInt($tpe.All.length))")
          case _ =>
            None
        }

      go(sc.Type.base(tpe), dbType, table.maybeId.collect { case x: IdComputed.Unary => x })
    }

    new ComputedTestInserts(
      sc.Type.Qualified(options.pkg / sc.Ident(s"${Naming.titleCase(projectName)}TestInsert")),
      tables.collect {
        case table if !options.readonlyRepo.include(table.dbTable.name) =>
          val hasConstraints: Set[db.ColName] =
            table.dbTable.cols.iterator.flatMap(_.constraints.flatMap(_.columns)).toSet
          val pkColumns: Set[db.ColName] =
            table.maybeId.iterator.flatMap(_.cols.iterator.map(_.dbCol.name)).toSet
          val appearsInFkButNotPk: Set[db.ColName] =
            table.dbTable.foreignKeys.iterator.flatMap(_.cols.toList).filterNot(pkColumns).toSet

          def defaultedParametersFor(cols: List[ComputedColumn]): List[sc.Param] = {
            val params = cols.map { col =>
              val isMeaningful = hasConstraints(col.dbName) || appearsInFkButNotPk(col.dbName)
              val isOpenEnum = openEnumsByType.contains(col.tpe)
              val default = if (isMeaningful && !col.dbCol.isDefaulted && !isOpenEnum) {
                if (col.dbCol.nullability == Nullability.NoNulls) None else Some(TypesScala.None.code)
              } else defaultFor(table, col.tpe, col.dbCol.tpe)
              sc.Param(col.name, col.tpe, default)
            }

            // keep order but pull all required parameters first
            val (requiredParams, optionalParams) = params.partition(_.default.isEmpty)
            requiredParams ++ optionalParams
          }

          FkAnalysis(allTablesByName, table, options.dialect).createWithFkIdsUnsavedRowOrRow match {
            case Some(colsFromFks) =>
              val valuesFromFk: List[(sc.Ident, sc.Code)] =
                colsFromFks.allColumns.toList.map { col =>
                  val expr: sc.Code =
                    colsFromFks.exprForColumn.get(col.name) match {
                      case Some(expr) =>
                        if (col.dbCol.isDefaulted && col.dbCol.nullability != Nullability.NoNulls)
                          code"${table.default.Defaulted}.${table.default.Provided}(${TypesScala.Option}($expr))"
                        else if (col.dbCol.isDefaulted)
                          code"${table.default.Defaulted}.${table.default.Provided}($expr)"
                        else if (col.dbCol.nullability != Nullability.NoNulls)
                          code"${TypesScala.Option}(${expr})"
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
              val params: List[sc.Param] = defaultedParametersFor(cols)
              val values: List[(sc.Ident, sc.Code)] =
                params.map(p => (p.name, p.name.code))

              ComputedTestInserts.InsertMethod(table, params, values)
          }

      }.toList,
      maybeDomainMethods
    )
  }

  case class InsertMethod(table: ComputedTable, params: List[sc.Param], values: List[(sc.Ident, sc.Code)]) {
    val name: sc.Ident =
      table.dbTable.name match {
        case db.RelationName(Some(schema), name) => sc.Ident(s"${Naming.camelCase(schema)}${Naming.titleCase(name)}")
        case db.RelationName(None, name)         => sc.Ident(Naming.titleCase(name))
      }

    val cls = table.maybeUnsavedRow match {
      case Some(unsaved) => unsaved.tpe
      case None          => table.names.RowName
    }
  }

  case class GenerateDomainMethods(tpe: sc.Type.Qualified, methods: NonEmptyList[ComputedTestInserts.GenerateDomainMethod]) {
    val domainMethodByType: Map[sc.Type, GenerateDomainMethod] =
      methods.iterator.map(x => (x.dom.tpe: sc.Type) -> x).toMap
  }

  case class GenerateDomainMethod(dom: ComputedDomain) {
    val name: sc.Ident =
      dom.underlying.name match {
        case db.RelationName(Some(schema), name) => sc.Ident(s"${Naming.camelCase(schema)}${Naming.titleCase(name)}")
        case db.RelationName(None, name)         => sc.Ident(Naming.titleCase(name))
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
          case Type.DomainRef(name, _, _) => computedDomainByType.get(name)
          case _                          => None
        }
      } yield GenerateDomainMethod(domain)

      NonEmptyList.fromList(all.toList.distinctByCompat(_.dom.tpe).sortBy(_.dom.tpe))
    }
  }
}
