package typo
package internal

import typo.internal.pg.OpenEnum
import typo.internal.rewriteDependentData.Eval

case class ComputedTable(
    lang: Lang,
    dbType: DbType,
    options: InternalOptions,
    default: ComputedDefault,
    dbTable: db.Table,
    naming: Naming,
    scalaTypeMapper: TypeMapperJvm,
    eval: Eval[db.RelationName, HasSource],
    openEnumsByTable: Map[db.RelationName, OpenEnum]
) extends HasSource {
  override val source: Source.Table = Source.Table(dbTable.name)

  private def mkComputedColumn(pointsTo: List[(Source.Relation, db.ColName)], name: jvm.Ident, tpe: jvm.Type, dbCol: db.Col): ComputedColumn =
    ComputedColumn(
      pointsTo = pointsTo,
      name = name,
      dbCol = dbCol,
      typoType = TypoType.fromJvmAndDb(tpe, dbCol.tpe, naming.pkg, lang)
    )

  val deps: Map[db.ColName, List[(Source.Relation, db.ColName)]] = {
    val (fkSelf, fkOther) = dbTable.foreignKeys.partition { fk => fk.otherTable == dbTable.name }

    val fromSelf: List[(db.ColName, (Source.Relation, db.ColName))] =
      fkSelf.flatMap(fk => fk.cols.zip(fk.otherCols.map(cn => (source, cn))).toList)

    val fromOthers: List[(db.ColName, (Source.Relation, db.ColName))] =
      fkOther.flatMap { fk =>
        eval(fk.otherTable).get match {
          case None =>
            options.logger.warn(s"Circular: ${dbTable.name.value} => ${fk.otherTable.value}")
            Nil
          case Some(otherTable) =>
            fk.cols.zip(fk.otherCols.map(cn => (otherTable.source, cn))).toList
        }
      }

    (fromSelf ++ fromOthers).groupBy(_._1).map { case (colName, tuples) =>
      val sorted = tuples
        .map { case (_, other) => other }
        .sortBy { case (rel, colName) => (rel.name.value, colName.value) }
      colName -> sorted
    }
  }

  val dbColsByName: Map[db.ColName, db.Col] =
    dbTable.cols.map(col => (col.name, col)).toMap

  /** Whether the database supports streaming insert (PostgreSQL COPY command) */
  val hasStreamingSupport: Boolean = dbType.adapter(scalaTypeMapper.needsTimestampCasts).supportsCopyStreaming

  val maybeId: Option[IdComputed] =
    dbTable.primaryKey.flatMap { pk =>
      pk.colNames match {
        case NonEmptyList(colName, Nil) =>
          val dbCol = dbColsByName(colName)
          val pointsTo = deps.getOrElse(dbCol.name, Nil)
          lazy val underlying = scalaTypeMapper.col(dbTable.name, dbCol, None)

          val fromFk: Option[IdComputed.UnaryInherited] =
            findTypeFromFk(options.logger, source, dbCol.name, pointsTo, eval.asMaybe, options.lang)(_ => None).map { tpe =>
              val col = mkComputedColumn(pointsTo, naming.field(dbCol.name), tpe, dbCol)
              IdComputed.UnaryInherited(col, tpe)
            }

          val fromOpenEnum: Option[IdComputed.UnaryOpenEnum] =
            openEnumsByTable.get(dbTable.name).map { values =>
              val tpe = jvm.Type.Qualified(naming.idName(source, List(dbCol)))
              val col = mkComputedColumn(pointsTo, naming.field(dbCol.name), tpe, dbCol)
              IdComputed.UnaryOpenEnum(col, tpe, underlying, values)
            }

          fromFk.orElse(fromOpenEnum).orElse {
            val underlying = scalaTypeMapper.col(dbTable.name, dbCol, None)
            val col = mkComputedColumn(pointsTo, naming.field(dbCol.name), underlying, dbCol)
            if (jvm.Type.containsUserDefined(underlying))
              Some(IdComputed.UnaryUserSpecified(col, underlying))
            else if (!options.enablePrimaryKeyType.include(dbTable.name))
              Some(IdComputed.UnaryNoIdType(col, underlying))
            else {
              val tpe = jvm.Type.Qualified(naming.idName(source, List(col.dbCol)))
              Some(IdComputed.UnaryNormal(col, tpe))
            }
          }

        case colNames =>
          val cols: NonEmptyList[ComputedColumn] =
            colNames.map { colName =>
              val dbCol = dbColsByName(colName)
              mkComputedColumn(Nil, naming.field(colName), deriveType(colName), dbCol)
            }
          val tpe = jvm.Type.Qualified(naming.idName(source, cols.toList.map(_.dbCol)))
          Some(IdComputed.Composite(cols, tpe, paramName = jvm.Ident("compositeId")))
      }
    }

  val cols: NonEmptyList[ComputedColumn] =
    dbTable.cols.map { dbCol =>
      mkComputedColumn(deps.getOrElse(dbCol.name, Nil), naming.field(dbCol.name), deriveType(dbCol.name), dbCol)
    }

  def deriveType(colName: db.ColName): jvm.Type = {
    val dbCol = dbColsByName(colName)
    // we let types flow through constraints down to this column, the point is to reuse id types downstream
    val typeFromFk: Option[jvm.Type] =
      findTypeFromFk(options.logger, source, colName, deps.getOrElse(colName, Nil), eval.asMaybe, options.lang)(otherColName => Some(deriveType(otherColName)))

    val typeFromId: Option[jvm.Type] =
      maybeId match {
        case Some(id: IdComputed.Unary) if id.col.dbName == colName => Some(id.tpe)
        case _                                                      => None
      }

    scalaTypeMapper.col(dbTable.name, dbCol, typeFromFk.orElse(typeFromId))
  }

  val names = ComputedNames(naming, source, maybeId, options.enableFieldValue.include(dbTable.name), options.enableDsl)

  val colsNotId: Option[NonEmptyList[ComputedColumn]] =
    maybeId.flatMap { id =>
      val idNames = id.cols.map(_.dbName)
      NonEmptyList.fromList(
        cols.toList.filterNot(col => idNames.contains(col.dbName))
      )
    }

  val writeableColumnsNotId: Option[NonEmptyList[ComputedColumn]] =
    colsNotId.flatMap { colsNotId =>
      NonEmptyList.fromList(
        colsNotId.toList.filterNot(_.dbCol.maybeGenerated.exists(_.ALWAYS))
      )
    }

  val writeableColumnsWithId: Option[NonEmptyList[ComputedColumn]] =
    NonEmptyList.fromList(cols.toList.filterNot(c => c.dbCol.maybeGenerated.exists(_.ALWAYS)))

  val maybeUnsavedRow: Option[ComputedRowUnsaved] =
    ComputedRowUnsaved(source, cols, default, naming)

  val repoMethods: Option[NonEmptyList[RepoMethod]] = {
    val maybeMethods = List(
      List[Iterable[RepoMethod]](
        for {
          fieldsName <- names.FieldsName.toList
          builder <- List(
            RepoMethod.UpdateBuilder(dbTable.name, fieldsName, names.RowName),
            RepoMethod.SelectBuilder(dbTable.name, fieldsName, names.RowName),
            RepoMethod.DeleteBuilder(dbTable.name, fieldsName, names.RowName)
          )
        } yield builder,
        Some(RepoMethod.SelectAll(dbTable.name, cols, names.RowName)),
        maybeId.map(id => RepoMethod.SelectById(dbTable.name, cols, id, names.RowName)),
        for {
          id <- maybeId
          writeableColumnsWithId <- writeableColumnsWithId
        } yield {
          val unsavedParam = jvm.Param(jvm.Ident("unsaved"), names.RowName)
          RepoMethod.Upsert(dbTable.name, cols, id, unsavedParam, names.RowName, writeableColumnsWithId)
        },
        maybeId
          .collect {
            case unary: IdComputed.Unary =>
              RepoMethod.SelectByIds(dbTable.name, cols, unary, jvm.Param(unary.paramName.appended("s"), jvm.Type.ArrayOf(unary.tpe)), names.RowName)
            case x: IdComputed.Composite if x.cols.forall(col => col.dbCol.nullability == Nullability.NoNulls) =>
              RepoMethod.SelectByIds(dbTable.name, cols, x, jvm.Param(x.paramName.appended("s"), jvm.Type.ArrayOf(x.tpe)), names.RowName)
          }
          .toList
          .flatMap { x => List(x, RepoMethod.SelectByIdsTracked(x)) },
        for {
          fieldValueName <- names.FieldOrIdValueName
        } yield {
          val fieldValueOrIdsParam = jvm.Param(jvm.Ident("fieldValues"), lang.ListType.tpe.of(fieldValueName.of(jvm.Type.Wildcard)))
          RepoMethod.SelectByFieldValues(dbTable.name, cols, fieldValueName, fieldValueOrIdsParam, names.RowName)
        },
        for {
          id <- maybeId
          fieldValueName <- names.FieldOrIdValueName
        } yield RepoMethod.UpdateFieldValues(
          dbTable.name,
          id,
          jvm.Param(jvm.Ident("fieldValues"), lang.ListType.tpe.of(fieldValueName.of(jvm.Type.Wildcard))),
          fieldValueName,
          cols,
          names.RowName
        ),
        for {
          id <- maybeId
          writeableColumnsNotId <- writeableColumnsNotId
        } yield RepoMethod.Update(dbTable.name, cols, id, jvm.Param(jvm.Ident("row"), names.RowName), writeableColumnsNotId),
        for {
          writeableColumnsWithId <- writeableColumnsWithId
        } yield {
          val unsavedParam = jvm.Param(jvm.Ident("unsaved"), names.RowName)
          val returningStrategy = dbType.adapter(needsTimestampCasts = false).returningStrategy(cols, names.RowName, maybeId)
          RepoMethod.Insert(dbTable.name, cols, maybeId, unsavedParam, writeableColumnsWithId, returningStrategy)
        },
        for {
          writeableColumnsWithId <- writeableColumnsWithId
          _ <- if (options.enableStreamingInserts) Some(()) else None // weird syntax because of scala 2.13
        } yield RepoMethod.InsertStreaming(dbTable.name, names.RowName, writeableColumnsWithId),
        for {
          id <- maybeId
          writeableColumnsWithId <- writeableColumnsWithId
          _ <- if (options.enableStreamingInserts) Some(()) else None // weird syntax because of scala 2.13
        } yield RepoMethod.UpsertStreaming(dbTable.name, id, names.RowName, writeableColumnsWithId),
        for {
          id <- maybeId
          writeableColumnsWithId <- writeableColumnsWithId
        } yield RepoMethod.UpsertBatch(dbTable.name, cols, id, names.RowName, writeableColumnsWithId),
        for {
          unsavedRow <- maybeUnsavedRow
        } yield {
          val unsavedParam = jvm.Param(jvm.Ident("unsaved"), unsavedRow.tpe)
          val returningStrategy = dbType.adapter(needsTimestampCasts = false).returningStrategy(cols, names.RowName, maybeId)
          RepoMethod.InsertUnsaved(dbTable.name, cols, unsavedRow, unsavedParam, maybeId, default, returningStrategy)
        },
        maybeUnsavedRow.collect {
          case unsavedRow if options.enableStreamingInserts =>
            RepoMethod.InsertUnsavedStreaming(dbTable.name, unsavedRow)
        },
        maybeId.map(id => RepoMethod.Delete(dbTable.name, id)),
        maybeId.collect {
          case unary: IdComputed.Unary =>
            RepoMethod.DeleteByIds(dbTable.name, unary, jvm.Param(unary.paramName.appended("s"), jvm.Type.ArrayOf(unary.tpe)))
          case x: IdComputed.Composite if x.cols.forall(col => col.dbCol.nullability == Nullability.NoNulls) =>
            RepoMethod.DeleteByIds(dbTable.name, x, jvm.Param(x.paramName.appended("s"), jvm.Type.ArrayOf(x.tpe)))
        }
      ).flatten,
      dbTable.uniqueKeys
        .flatMap { uk =>
          val keyColumns = uk.cols.toList.flatMap(colName => cols.find(_.dbName == colName))

          // Only create select-by-unique methods if all referenced columns exist
          if (keyColumns.size == uk.cols.length) {
            NonEmptyList.fromList(keyColumns).map { nelKeyColumns =>
              RepoMethod.SelectByUnique(
                dbTable.name,
                keyColumns = nelKeyColumns,
                allColumns = cols,
                rowType = names.RowName
              )
            }
          } else {
            // Skip unique keys that reference non-existent columns (e.g., nested table storage tables)
            options.logger.warn(s"Skipping unique key ${uk.constraintName.value} on ${dbTable.name.value}: references columns not in table (${uk.cols.map(_.value).mkString(", ")})")
            None
          }
        }
    )
    val valid = maybeMethods.flatten.filter { method =>
      val mutatorAllowed = method match {
        case _: RepoMethod.Mutator => !options.readonlyRepo.include(dbTable.name)
        case _                     => true
      }
      val streamingAllowed = !method.requiresStreamingSupport || hasStreamingSupport
      mutatorAllowed && streamingAllowed
    }.sorted

    NonEmptyList.fromList(valid)
  }
}
