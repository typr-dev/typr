package typo
package internal
package codegen

import typo.internal.analysis.MaybeReturnsRows

class DbLibZioJdbc(pkg: sc.QIdent, inlineImplicits: Boolean, dslEnabled: Boolean, default: ComputedDefault, enableStreamingInserts: Boolean, implicitOrUsing: ImplicitOrUsing) extends DbLib {
  private val ZConnection = sc.Type.Qualified("zio.jdbc.ZConnection")
  private val Throwable = sc.Type.Qualified("java.lang.Throwable")
  private val ZStream = sc.Type.Qualified("zio.stream.ZStream")
  private val ZIO = sc.Type.Qualified("zio.ZIO")
  private val JdbcEncoder = sc.Type.Qualified("zio.jdbc.JdbcEncoder")
  private val JdbcDecoder = sc.Type.Qualified("zio.jdbc.JdbcDecoder")
  private val SqlFragment = sc.Type.Qualified("zio.jdbc.SqlFragment")
  private val Segment = sc.Type.Qualified("zio.jdbc.SqlFragment.Segment")
  private val Setter = sc.Type.Qualified("zio.jdbc.SqlFragment.Setter")
  private val UpdateResult = sc.Type.Qualified("zio.jdbc.UpdateResult")
  private val Chunk = sc.Type.Qualified("zio.Chunk")
  private val NonEmptyChunk = sc.Type.Qualified("zio.NonEmptyChunk")
  private val sqlInterpolator = sc.Type.Qualified("zio.jdbc.sqlInterpolator")
  private val JdbcDecoderError = sc.Type.Qualified("zio.jdbc.JdbcDecoderError")

  val textSupport: Option[DbLibTextSupport] =
    if (enableStreamingInserts) Some(new DbLibTextSupport(pkg, inlineImplicits, None, default, implicitOrUsing)) else None

  override val additionalFiles: List[typo.sc.File] =
    textSupport match {
      case Some(textSupport) =>
        List(
          sc.File(textSupport.Text, DbLibTextImplementations.Text(implicitOrUsing), Nil, scope = Scope.Main),
          sc.File(textSupport.streamingInsert, DbLibTextImplementations.streamingInsertZio(textSupport.Text, implicitOrUsing), Nil, scope = Scope.Main)
        )
      case None => Nil
    }

  /** This type is basically a mapping from scala type to jdbc type name. zio-jdbc seems to use jdbc type number instead of the (potentially database-specific) type name. In the DSL we need to
    * generate some sql casts based on scala type, so it's unavoidable to have this mapping.
    *
    * A bit unfortunate maybe, but it's not the end of the world to provide it ourselves.
    */
  private val PGType = sc.Type.Qualified("typo.dsl.PGType")

  def ifDsl(g: sc.Given): Option[sc.Given] =
    if (dslEnabled) Some(g) else None

  private def SQL(content: sc.Code) = sc.StringInterpolate(sqlInterpolator, sc.Ident("sql"), content)

  private val arraySetterName: sc.Ident = sc.Ident("arraySetter")
  private val arrayJdbcDecoderName: sc.Ident = sc.Ident("arrayJdbcDecoder")
  private val arrayJdbcEncoderName: sc.Ident = sc.Ident("arrayJdbcEncoder")
  private val jdbcDecoderName: sc.Ident = sc.Ident("jdbcDecoder")
  private val jdbcEncoderName: sc.Ident = sc.Ident("jdbcEncoder")
  private val setterName: sc.Ident = sc.Ident("setter")
  private val pgTypeName: sc.Ident = sc.Ident("pgType")

  private def dbNames(cols: NonEmptyList[ComputedColumn], isRead: Boolean): sc.Code =
    cols
      .map(c => code"${c.dbName}" ++ (if (isRead) SqlCast.fromPgCode(c) else sc.Code.Empty))
      .mkCode(", ")

  private val missingInstancesByType: Map[sc.Type, sc.QIdent] =
    missingInstances.collect { case x: sc.Given => (x.tpe, pkg / x.name) }.toMap

  /** Resolve known implicits at generation-time instead of at compile-time */
  private def lookupJdbcDecoder(tpe: sc.Type): sc.Code =
    if (!inlineImplicits) JdbcDecoder.of(tpe)
    else
      sc.Type.base(tpe) match {
        case TypesScala.BigDecimal            => code"$JdbcDecoder.bigDecimalDecoderScala"
        case TypesScala.Boolean               => code"$JdbcDecoder.booleanDecoder"
        case TypesScala.Byte                  => code"$JdbcDecoder.byteDecoder"
        case TypesScala.Double                => code"$JdbcDecoder.doubleDecoder"
        case TypesScala.Float                 => code"$JdbcDecoder.floatDecoder"
        case TypesScala.Int                   => code"$JdbcDecoder.intDecoder"
        case TypesScala.Long                  => code"$JdbcDecoder.longDecoder"
        case TypesJava.String                 => code"$JdbcDecoder.stringDecoder"
        case TypesJava.UUID                   => code"$JdbcDecoder.uuidDecoder"
        case TypesScala.Optional(targ)        => code"$JdbcDecoder.optionDecoder(${lookupJdbcDecoder(targ)})"
        case sc.Type.ArrayOf(TypesScala.Byte) => code"$JdbcDecoder.byteArrayDecoder"
        // generated type
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$x.$jdbcDecoderName"
        case x if missingInstancesByType.contains(JdbcDecoder.of(x)) =>
          code"${missingInstancesByType(JdbcDecoder.of(x))}"
        case other =>
          code"${JdbcDecoder.of(other)}"
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  private def lookupJdbcEncoder(tpe: sc.Type): sc.Code =
    if (!inlineImplicits) JdbcEncoder.of(tpe)
    else
      sc.Type.base(tpe) match {
        case TypesScala.BigDecimal            => code"$JdbcEncoder.bigDecimalEncoderScala"
        case TypesScala.Boolean               => code"$JdbcEncoder.booleanEncoder"
        case TypesScala.Byte                  => code"$JdbcEncoder.byteEncoder"
        case TypesScala.Double                => code"$JdbcEncoder.doubleEncoder"
        case TypesScala.Float                 => code"$JdbcEncoder.floatEncoder"
        case TypesScala.Int                   => code"$JdbcEncoder.intEncoder"
        case TypesScala.Long                  => code"$JdbcEncoder.longEncoder"
        case TypesJava.String                 => code"$JdbcEncoder.stringEncoder"
        case TypesJava.UUID                   => code"$JdbcEncoder.uuidEncoder"
        case TypesScala.Optional(targ)        => code"$JdbcEncoder.optionEncoder(${lookupJdbcEncoder(targ)})"
        case sc.Type.ArrayOf(TypesScala.Byte) => code"$JdbcEncoder.byteArrayEncoder"
        // generated type
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$x.$jdbcEncoderName"
        case x if missingInstancesByType.contains(JdbcDecoder.of(x)) =>
          code"${missingInstancesByType(JdbcEncoder.of(x))}"
        case other =>
          code"${JdbcEncoder.of(other)}"
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  private def lookupSetter(tpe: sc.Type): sc.Code =
    if (!inlineImplicits) Setter.of(tpe)
    else
      sc.Type.base(tpe) match {
        case TypesScala.BigDecimal            => code"$Setter.bigDecimalScalaSetter"
        case TypesScala.Boolean               => code"$Setter.booleanSetter"
        case TypesScala.Byte                  => code"$Setter.byteSetter"
        case TypesScala.Double                => code"$Setter.doubleSetter"
        case TypesScala.Float                 => code"$Setter.floatSetter"
        case TypesScala.Int                   => code"$Setter.intSetter"
        case TypesScala.Long                  => code"$Setter.longSetter"
        case TypesJava.String                 => code"$Setter.stringSetter"
        case TypesJava.UUID                   => code"$Setter.uuidParamSetter"
        case TypesScala.Optional(targ)        => code"$Setter.optionParamSetter(${lookupSetter(targ)})"
        case sc.Type.ArrayOf(TypesScala.Byte) => code"$Setter.byteArraySetter"
        // generated type
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$setterName"
        case sc.Type.ArrayOf(targ: sc.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arraySetterName"
        case x if missingInstancesByType.contains(Setter.of(x)) =>
          code"${missingInstancesByType(Setter.of(x))}"
        case other =>
          code"${Setter.of(other)}"
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupPgTypeFor(tpe: sc.Type): sc.Code =
    if (!inlineImplicits) sc.Summon(PGType.of(tpe), implicitOrUsing).code
    else
      sc.Type.base(tpe) match {
        case TypesScala.BigDecimal => code"$PGType.PGTypeBigDecimal"
        case TypesScala.Boolean    => code"$PGType.PGTypeBoolean"
        case TypesScala.Double     => code"$PGType.PGTypeDouble"
        case TypesScala.Float      => code"$PGType.PGTypeFloat"
        case TypesScala.Int        => code"$PGType.PGTypeInt"
        case TypesScala.Long       => code"$PGType.PGTypeLong"
        case TypesJava.String      => code"$PGType.PGTypeString"
        case TypesJava.UUID        => code"$PGType.PGTypeUUID"
        //        case ScalaTypes.Optional(targ) => lookupParameterMetaDataFor(targ)
        // generated type
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$pgTypeName"
        // customized type mapping
        case x if missingInstancesByType.contains(PGType.of(x)) =>
          code"${missingInstancesByType(PGType.of(x))}"
        case sc.Type.ArrayOf(TypesScala.Byte) => code"$PGType.PGTypeByteArray"
        // fallback array case.
        case sc.Type.ArrayOf(targ) => code"$PGType.forArray(${lookupPgTypeFor(targ)})"
        case other                 => sc.Summon(PGType.of(other), implicitOrUsing).code
      }

  private def runtimeInterpolateValue(name: sc.Code, tpe: sc.Type, forbidInline: Boolean = false): sc.Code = {
    if (inlineImplicits && !forbidInline)
      code"$${$Segment.paramSegment($name)(${lookupSetter(tpe)})}"
    else code"$${$name}"
  }

  private def matchId(id: IdComputed): sc.Code =
    id match {
      case id: IdComputed.Unary =>
        code"${id.col.dbName} = ${runtimeInterpolateValue(id.paramName, id.tpe)}"
      case composite: IdComputed.Composite =>
        code"${composite.cols.map(cc => code"${cc.dbName} = ${runtimeInterpolateValue(code"${composite.paramName}.${cc.name}", cc.tpe)}").mkCode(" AND ")}"
    }

  override def repoSig(repoMethod: RepoMethod): Either[DbLib.NotImplementedFor, sc.Code] = {
    val name = repoMethod.methodName
    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, rowType) =>
        Right(code"def $name: ${sc.Type.dsl.SelectBuilder.of(fieldsType, rowType)}")
      case RepoMethod.SelectAll(_, _, rowType) =>
        Right(code"def $name: ${ZStream.of(ZConnection, Throwable, rowType)}")
      case RepoMethod.SelectById(_, _, id, rowType) =>
        Right(code"def $name(${id.param}): ${ZIO.of(ZConnection, Throwable, TypesScala.Option.of(rowType))}")
      case RepoMethod.SelectByIds(_, _, idComputed, idsParam, rowType) =>
        val usedDefineds = idComputed.userDefinedColTypes.zipWithIndex.map { case (colType, i) => sc.Param(sc.Ident(s"encoder$i"), JdbcEncoder.of(sc.Type.ArrayOf(colType)), None) }

        usedDefineds match {
          case Nil =>
            Right(code"def $name($idsParam): ${ZStream.of(ZConnection, Throwable, rowType)}")
          case nonEmpty =>
            Right(code"def $name($idsParam)($implicitOrUsing ${nonEmpty.map(_.code).mkCode(", ")}): ${ZStream.of(ZConnection, Throwable, rowType)}")
        }
      case RepoMethod.SelectByIdsTracked(x) =>
        val usedDefineds = x.idComputed.userDefinedColTypes.zipWithIndex.map { case (colType, i) => sc.Param(sc.Ident(s"encoder$i"), JdbcEncoder.of(sc.Type.ArrayOf(colType)), None) }
        val returnType = ZIO.of(ZConnection, Throwable, TypesScala.Map.of(x.idComputed.tpe, x.rowType))
        usedDefineds match {
          case Nil =>
            Right(code"def $name(${x.idsParam}): $returnType")
          case nonEmpty =>
            Right(code"def $name(${x.idsParam})($implicitOrUsing ${nonEmpty.map(_.code).mkCode(", ")}): $returnType")
        }

      case RepoMethod.SelectByUnique(_, keyColumns, _, rowType) =>
        Right(code"def $name(${keyColumns.map(_.param.code).mkCode(", ")}): ${ZIO.of(ZConnection, Throwable, TypesScala.Option.of(rowType))}")
      case RepoMethod.SelectByFieldValues(_, _, _, fieldValueOrIdsParam, rowType) =>
        Right(code"def $name($fieldValueOrIdsParam): ${ZStream.of(ZConnection, Throwable, rowType)}")
      case RepoMethod.UpdateBuilder(_, fieldsType, rowType) =>
        Right(code"def $name: ${sc.Type.dsl.UpdateBuilder.of(fieldsType, rowType)}")
      case RepoMethod.UpdateFieldValues(_, id, varargs, _, _, _) =>
        Right(code"def $name(${id.param}, $varargs): ${ZIO.of(ZConnection, Throwable, TypesScala.Boolean)}")
      case RepoMethod.Update(_, _, _, param, _) =>
        Right(code"def $name($param): ${ZIO.of(ZConnection, Throwable, TypesScala.Option.of(param.tpe))}")
      case RepoMethod.Insert(_, _, unsavedParam, rowType, _) =>
        Right(code"def $name($unsavedParam): ${ZIO.of(ZConnection, Throwable, rowType)}")
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, rowType) =>
        Right(code"def $name($unsavedParam): ${ZIO.of(ZConnection, Throwable, rowType)}")
      case RepoMethod.InsertStreaming(_, rowType, _) =>
        val in = ZStream.of(ZConnection, TypesJava.Throwable, rowType)
        val out = ZIO.of(ZConnection, TypesJava.Throwable, TypesScala.Long)
        Right(code"def $name(unsaved: $in, batchSize: Int = 10000): $out")
      case RepoMethod.UpsertBatch(_, _, _, _, _) =>
//        val in = TypesScala.List.of(rowType)
//        val out = ZIO.of(ZConnection, TypesJava.Throwable, TypesScala.List.of(rowType))
//        Right(code"def $name(unsaved: $in): $out")
        Left(DbLib.NotImplementedFor("zio-jdbc"))
      case RepoMethod.UpsertStreaming(_, _, rowType, _) =>
        val in = ZStream.of(ZConnection, TypesJava.Throwable, rowType)
        val out = ZIO.of(ZConnection, TypesJava.Throwable, TypesScala.Long)
        Right(code"def $name(unsaved: $in, batchSize: Int = 10000): $out")
      case RepoMethod.Upsert(_, _, _, unsavedParam, rowType, _) =>
        Right(code"def $name($unsavedParam): ${ZIO.of(ZConnection, Throwable, UpdateResult.of(rowType))}")
      case RepoMethod.InsertUnsavedStreaming(_, unsaved) =>
        val in = ZStream.of(ZConnection, TypesJava.Throwable, unsaved.tpe)
        val out = ZIO.of(ZConnection, TypesJava.Throwable, TypesScala.Long)
        Right(code"def $name(unsaved: $in, batchSize: ${TypesScala.Int} = 10000): $out")
      case RepoMethod.DeleteBuilder(_, fieldsType, rowType) =>
        Right(code"def $name: ${sc.Type.dsl.DeleteBuilder.of(fieldsType, rowType)}")
      case RepoMethod.Delete(_, id) =>
        Right(code"def $name(${id.param}): ${ZIO.of(ZConnection, Throwable, TypesScala.Boolean)}")
      case RepoMethod.DeleteByIds(_, idComputed, idsParam) =>
        val usedDefineds = idComputed.userDefinedColTypes.zipWithIndex.map { case (colType, i) => sc.Param(sc.Ident(s"encoder$i"), JdbcEncoder.of(sc.Type.ArrayOf(colType)), None) }
        usedDefineds match {
          case Nil =>
            Right(code"def $name(${idsParam}): ${ZIO.of(ZConnection, Throwable, TypesScala.Long)}")
          case nonEmpty =>
            Right(code"def $name(${idsParam})($implicitOrUsing ${nonEmpty.map(_.code).mkCode(", ")}): ${ZIO.of(ZConnection, Throwable, TypesScala.Long)}")
        }
      case RepoMethod.SqlFile(sqlScript) =>
        val params = sc.Params(sqlScript.params.map(p => sc.Param(p.name, p.tpe, None)))

        val retType = sqlScript.maybeRowName match {
          case MaybeReturnsRows.Query(rowName) => ZStream.of(ZConnection, Throwable, rowName)
          case MaybeReturnsRows.Update         => ZIO.of(ZConnection, Throwable, TypesScala.Long)
        }

        Right(code"def $name$params: $retType")
    }
  }

  override def repoImpl(repoMethod: RepoMethod): sc.Code =
    repoMethod match {
      case RepoMethod.SelectBuilder(relName, fieldsType, rowType) =>
        code"""${sc.Type.dsl.SelectBuilderSql}(${sc.StrLit(relName.quotedValue)}, $fieldsType.structure, ${lookupJdbcDecoder(rowType)})"""

      case RepoMethod.SelectAll(relName, cols, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        val sql = SQL(code"""select $joinedColNames from $relName""")
        code"""$sql.query(using ${lookupJdbcDecoder(rowType)}).selectStream()"""

      case RepoMethod.SelectById(relName, cols, id, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        val sql = SQL(code"""select $joinedColNames from $relName where ${matchId(id)}""")
        code"""$sql.query(using ${lookupJdbcDecoder(rowType)}).selectOne"""

      case RepoMethod.SelectByIds(relName, cols, computedId, idsParam, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        computedId match {
          case x: IdComputed.Composite =>
            val vals = x.cols.map(col => code"val ${col.name} = ${idsParam.name}.map(_.${col.name})").mkCode("\n")
            val sql = SQL {
              code"""|select ${dbNames(cols, isRead = true)}
                     |from $relName
                     |where (${x.cols.map(col => col.dbCol.name.code).mkCode(", ")})
                     |in (select ${x.cols.map(col => code"unnest(${runtimeInterpolateValue(col.name, col.tpe, forbidInline = true)})").mkCode(", ")})
                     |""".stripMargin
            }
            code"""|$vals
                   |$sql.query(using ${lookupJdbcDecoder(rowType)}).selectStream()
                   |""".stripMargin

          case unaryId: IdComputed.Unary =>
            val sql = SQL(
              code"""select $joinedColNames from $relName where ${unaryId.col.dbName} = ANY(${runtimeInterpolateValue(idsParam.name, idsParam.tpe)})"""
            )
            code"""$sql.query(using ${lookupJdbcDecoder(rowType)}).selectStream()"""
        }
      case RepoMethod.SelectByIdsTracked(x) =>
        code"""|${x.methodName}(${x.idsParam.name}).runCollect.map { rows =>
               |  val byId = rows.view.map(x => (x.${x.idComputed.paramName}, x)).toMap
               |  ${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap
               |}""".stripMargin

      case RepoMethod.SelectByUnique(relName, keyColumns, allCols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(allCols, isRead = true)}
                 |from $relName
                 |where ${keyColumns.map(c => code"${c.dbName} = ${runtimeInterpolateValue(c.name, c.tpe)}").mkCode(" AND ")}
                 |""".stripMargin
        }
        code"""$sql.query(using ${lookupJdbcDecoder(rowType)}).selectOne"""

      case RepoMethod.SelectByFieldValues(relName, cols, fieldValue, fieldValueOrIdsParam, rowType) =>
        val cases =
          cols.map { col =>
            val fr = SQL(code"${col.dbName} = ${runtimeInterpolateValue(sc.Ident("value"), col.tpe)}")
            code"case $fieldValue.${col.name}(value) => $fr"
          }

        code"""${fieldValueOrIdsParam.name} match {
              |  case Nil      => selectAll
              |  case nonEmpty =>
              |    val wheres = $SqlFragment.empty.and(
              |      nonEmpty.map {
              |        ${cases.mkCode("\n")}
              |      }
              |    )
              |    ${SQL(code"""select ${dbNames(cols, isRead = true)} from $relName where $$wheres""")}.query(using ${lookupJdbcDecoder(rowType)}).selectStream()
              |}""".stripMargin

      case RepoMethod.UpdateFieldValues(relName, id, varargs, fieldValue, cases0, _) =>
        val cases: NonEmptyList[sc.Code] =
          cases0.map { col =>
            val sql = SQL(code"${col.dbName} = ${runtimeInterpolateValue(sc.Ident("value"), col.tpe)}${SqlCast.toPgCode(col)}")
            code"case $fieldValue.${col.name}(value) => $sql"
          }

        val sql = SQL {
          code"""|update $relName
                 |set $$updates
                 |where ${matchId(id)}
                 |""".stripMargin
        }

        code"""$NonEmptyChunk.fromIterableOption(${varargs.name}) match {
              |  case None           => $ZIO.succeed(false)
              |  case Some(nonEmpty) =>
              |    val updates = nonEmpty.map { ${cases.mkCode("\n")} }.mkFragment($SqlFragment(", "))
              |    $sql.update.map(_ > 0)
              |}""".stripMargin

      case RepoMethod.UpdateBuilder(relName, fieldsType, rowType) =>
        code"${sc.Type.dsl.UpdateBuilder}(${sc.StrLit(relName.quotedValue)}, $fieldsType.structure, ${lookupJdbcDecoder(rowType)})"

      case RepoMethod.Update(relName, cols, id, param, writeableCols) =>
        val sql = SQL(
          code"""update $relName
                |set ${writeableCols.map { col => code"${col.dbName} = ${runtimeInterpolateValue(code"${param.name}.${col.name}", col.tpe)}${SqlCast.toPgCode(col)}" }.mkCode(",\n")}
                |where ${matchId(id)}
                |returning ${dbNames(cols, isRead = true)}""".stripMargin
        )
        code"""|val ${id.paramName} = ${param.name}.${id.paramName}
               |$sql
               |  .query(${lookupJdbcDecoder(param.tpe)})
               |  .selectOne"""

      case RepoMethod.InsertUnsaved(relName, cols, unsaved, unsavedParam, default, rowType) =>
        val cases0 = unsaved.restCols.map { col =>
          val set = SQL(code"${runtimeInterpolateValue(code"${unsavedParam.name}.${col.name}", col.tpe)}${SqlCast.toPgCode(col)}")
          code"""Some((${SQL(col.dbName)}, $set))"""
        }
        val cases1 = unsaved.defaultCols.map { case (col @ ComputedColumn(_, ident, _, _), origType) =>
          val setValue = SQL(code"${runtimeInterpolateValue(code"value: $origType", origType)}${SqlCast.toPgCode(col)}")
          code"""|${unsavedParam.name}.$ident match {
                 |  case ${default.Defaulted}.${default.UseDefault} => None
                 |  case ${default.Defaulted}.${default.Provided}(value) => Some((${SQL(col.dbName)}, $setValue))
                 |}"""
        }

        val sqlEmpty = SQL {
          code"""|insert into $relName default values
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }

        code"""|val fs = List(
               |  ${(cases0 ++ cases1.toList).mkCode(",\n")}
               |).flatten
               |
               |val q = if (fs.isEmpty) {
               |  $sqlEmpty
               |} else {
               |  val names  = fs.map { case (n, _) => n }.mkFragment($SqlFragment(", "))
               |  val values = fs.map { case (_, f) => f }.mkFragment($SqlFragment(", "))
               |  ${SQL(code"insert into $relName($$names) values ($$values) returning ${dbNames(cols, isRead = true)}")}
               |}
               |q.insertReturning(using ${lookupJdbcDecoder(rowType)}).map(_.updatedKeys.head)
               |"""
      case RepoMethod.Upsert(relName, cols, id, unsavedParam, rowType, writeableColumnsWithId) =>
        val writeableColumnsNotId = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))

        val values = writeableColumnsWithId.map { c =>
          code"${runtimeInterpolateValue(code"${unsavedParam.name}.${c.name}", c.tpe)}${SqlCast.toPgCode(c)}"
        }

        val conflictAction = writeableColumnsNotId match {
          case Nil =>
            val arbitraryColumn = id.cols.head
            code"do update set ${arbitraryColumn.dbName.code} = EXCLUDED.${arbitraryColumn.dbName.code}"
          case nonEmpty =>
            code"""|do update set
                   |  ${nonEmpty.map { c => code"${c.dbName.code} = EXCLUDED.${c.dbName.code}" }.mkCode(",\n")}""".stripMargin
        }

        val sql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (
                 |  ${values.mkCode(",\n")}
                 |)
                 |on conflict (${dbNames(id.cols, isRead = false)})
                 |$conflictAction
                 |returning ${dbNames(cols, isRead = true)}""".stripMargin
        }

        code"$sql.insertReturning(using ${lookupJdbcDecoder(rowType)})"

      case RepoMethod.UpsertBatch(_, _, _, _, _) =>
        "???"
      case RepoMethod.UpsertStreaming(relName, id, rowType, writeableColumnsWithId) =>
        val writeableColumnsNotId = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))

        val conflictAction = writeableColumnsNotId match {
          case Nil => code"do nothing"
          case nonEmpty =>
            code"""|do update set
                   |  ${nonEmpty.map { c => code"${c.dbName.code} = EXCLUDED.${c.dbName.code}" }.mkCode(",\n")}""".stripMargin
        }
        val tempTablename = s"${relName.name}_TEMP"

        val copySql = sc.s(code"copy $tempTablename(${dbNames(writeableColumnsWithId, isRead = false)}) from stdin")

        val mergeSql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |select * from $tempTablename
                 |on conflict (${dbNames(id.cols, isRead = false)})
                 |$conflictAction
                 |;
                 |drop table $tempTablename;""".stripMargin
        }
        code"""|val created = ${SQL(code"create temporary table $tempTablename (like $relName) on commit drop")}.execute
               |val copied = ${textSupport.get.streamingInsert}($copySql, batchSize, unsaved)(${implicitOrUsing.callImplicitOrUsing}${textSupport.get.lookupTextFor(rowType)})
               |val merged = $mergeSql.update
               |created *> copied *> merged""".stripMargin

      case RepoMethod.Insert(relName, cols, unsavedParam, rowType, writeableColumnsWithId) =>
        val values = writeableColumnsWithId.map { c =>
          code"${runtimeInterpolateValue(code"${unsavedParam.name}.${c.name}", c.tpe)}${SqlCast.toPgCode(c)}"
        }
        val sql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${values.mkCode(", ")})
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }

        code"$sql.insertReturning(using ${lookupJdbcDecoder(rowType)}).map(_.updatedKeys.head)"
      case RepoMethod.InsertStreaming(relName, rowType, writeableColumnsWithId) =>
        val sql = sc.s(code"COPY $relName(${dbNames(writeableColumnsWithId, isRead = false)}) FROM STDIN")
        code"${textSupport.get.streamingInsert}($sql, batchSize, unsaved)(${implicitOrUsing.callImplicitOrUsing}${textSupport.get.lookupTextFor(rowType)})"
      case RepoMethod.InsertUnsavedStreaming(relName, unsaved) =>
        val sql = sc.s(code"COPY $relName(${dbNames(unsaved.unsavedCols, isRead = false)}) FROM STDIN (DEFAULT '${textSupport.get.DefaultValue}')")
        code"${textSupport.get.streamingInsert}($sql, batchSize, unsaved)(${implicitOrUsing.callImplicitOrUsing}${textSupport.get.lookupTextFor(unsaved.tpe)})"

      case RepoMethod.DeleteBuilder(relName, fieldsType, _) =>
        code"${sc.Type.dsl.DeleteBuilder}(${sc.StrLit(relName.quotedValue)}, $fieldsType.structure)"
      case RepoMethod.Delete(relName, id) =>
        val sql = SQL(code"""delete from $relName where ${matchId(id)}""")
        code"$sql.delete.map(_ > 0)"

      case RepoMethod.DeleteByIds(relName, computedId, idsParam) =>
        computedId match {
          case x: IdComputed.Composite =>
            val vals = x.cols.map(col => code"val ${col.name} = ${idsParam.name}.map(_.${col.name})").mkCode("\n")
            val sql = SQL {
              code"""|delete
                     |from $relName
                     |where (${x.cols.map(col => col.dbCol.name.code).mkCode(", ")})
                     |in (select ${x.cols.map(col => code"unnest(${runtimeInterpolateValue(col.name, col.tpe, forbidInline = true)})").mkCode(", ")})
                     |""".stripMargin
            }
            code"""|$vals
                   |$sql.delete
                   |""".stripMargin

          case x: IdComputed.Unary =>
            val sql = SQL(
              code"""delete from $relName where ${code"${x.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, x.tpe, forbidInline = true)})"}"""
            )
            code"$sql.delete"
        }

      case RepoMethod.SqlFile(sqlScript) =>
        val renderedScript: sc.Code = sqlScript.sqlFile.decomposedSql.renderCode { (paramAtIndex: Int) =>
          val param = sqlScript.params.find(_.indices.contains(paramAtIndex)).get
          val cast = SqlCast.toPg(param).fold("")(_.withColons)
          code"${runtimeInterpolateValue(param.name, param.tpe)}$cast"
        }
        val ret = for {
          cols <- sqlScript.maybeCols.toOption
          rowName <- sqlScript.maybeRowName.toOption
        } yield {
          // this is necessary to make custom types work with sql scripts, unfortunately.
          val renderedWithCasts: sc.Code =
            cols.toList.flatMap(c => SqlCast.fromPg(c.dbCol.tpe)) match {
              case Nil => renderedScript.code
              case _ =>
                val row = sc.Ident("row")

                code"""|with $row as (
                       |  $renderedScript
                       |)
                       |select ${cols.map(c => code"$row.${c.dbCol.parsedName.originalName.code}${SqlCast.fromPgCode(c)}").mkCode(", ")}
                       |from $row""".stripMargin
            }

          code"""|val sql =
                 |  ${SQL(renderedWithCasts)}
                 |sql.query(using ${lookupJdbcDecoder(rowName)}).selectStream()""".stripMargin
        }
        ret.getOrElse {
          code"${SQL(renderedScript)}.update"
        }
    }

  override def mockRepoImpl(id: IdComputed, repoMethod: RepoMethod, maybeToRow: Option[sc.Param]): sc.Code =
    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, _) =>
        code"${sc.Type.dsl.SelectBuilderMock}($fieldsType.structure, $ZIO.succeed($Chunk.fromIterable(map.values)), ${sc.Type.dsl.SelectParams}.empty)"
      case RepoMethod.SelectAll(_, _, _) =>
        code"$ZStream.fromIterable(map.values)"
      case RepoMethod.SelectById(_, _, id, _) =>
        code"$ZIO.succeed(map.get(${id.paramName}))"
      case RepoMethod.SelectByIds(_, _, _, idsParam, _) =>
        code"$ZStream.fromIterable(${idsParam.name}.flatMap(map.get))"
      case RepoMethod.SelectByIdsTracked(x) =>
        code"""|${x.methodName}(${x.idsParam.name}).runCollect.map { rows =>
               |  val byId = rows.view.map(x => (x.${x.idComputed.paramName}, x)).toMap
               |  ${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap
               |}""".stripMargin
      case RepoMethod.SelectByUnique(_, keyColumns, _, _) =>
        code"$ZIO.succeed(map.values.find(v => ${keyColumns.map(c => code"${c.name} == v.${c.name}").mkCode(" && ")}))"

      case RepoMethod.SelectByFieldValues(_, cols, fieldValue, fieldValueOrIdsParam, _) =>
        val cases = cols.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.filter(_.${col.name} == value)"
        }
        code"""$ZStream.fromIterable {
              |  ${fieldValueOrIdsParam.name}.foldLeft(map.values) {
              |    ${cases.mkCode("\n")}
              |  }
              |}""".stripMargin
      case RepoMethod.UpdateBuilder(_, fieldsType, _) =>
        code"${sc.Type.dsl.UpdateBuilderMock}(${sc.Type.dsl.UpdateParams}.empty, $fieldsType.structure, map)"
      case RepoMethod.UpdateFieldValues(_, id, varargs, fieldValue, cases0, _) =>
        val cases = cases0.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.copy(${col.name} = value)"
        }

        code"""|$ZIO.succeed {
               |  map.get(${id.paramName}) match {
               |    case ${TypesScala.Some}(oldRow) =>
               |      val updatedRow = ${varargs.name}.foldLeft(oldRow) {
               |        ${cases.mkCode("\n")}
               |      }
               |      if (updatedRow != oldRow) {
               |        map.put(${id.paramName}, updatedRow): @${TypesScala.nowarn}
               |        true
               |      } else {
               |        false
               |      }
               |    case ${TypesScala.None} => false
               |  }
               |}""".stripMargin
      case RepoMethod.Update(_, _, _, param, _) =>
        code"""$ZIO.succeed {
              |  map.get(${param.name}.${id.paramName}).map { _ =>
              |    map.put(${param.name}.${id.paramName}, ${param.name}): @${TypesScala.nowarn}
              |    ${param.name}
              |  }
              |}""".stripMargin
      case RepoMethod.Insert(_, _, unsavedParam, _, _) =>
        code"""|$ZIO.succeed {
               |  val _ =
               |    if (map.contains(${unsavedParam.name}.${id.paramName}))
               |      sys.error(s"id $${${unsavedParam.name}.${id.paramName}} already exists")
               |    else
               |      map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name})
               |
               |  ${unsavedParam.name}
               |}"""
      case RepoMethod.Upsert(_, _, _, unsavedParam, _, _) =>
        code"""|$ZIO.succeed {
               |  map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name}): @${TypesScala.nowarn}
               |  $UpdateResult(1, $Chunk.single(${unsavedParam.name}))
               |}""".stripMargin
      case RepoMethod.UpsertBatch(_, _, id, _, _) =>
        code"""|ZIO.succeed {
               |  unsaved.map{ row =>
               |    map += (row.${id.paramName} -> row)
               |    row
               |  }
               |}""".stripMargin
      case RepoMethod.UpsertStreaming(_, _, _, _) =>
        code"""|unsaved.scanZIO(0L) { case (acc, row) =>
               |  ZIO.succeed {
               |    map += (row.${id.paramName} -> row)
               |    acc + 1
               |  }
               |}.runLast.map(_.getOrElse(0L))""".stripMargin
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _) =>
        code"insert(${maybeToRow.get.name}(${unsavedParam.name}))"

      case RepoMethod.DeleteBuilder(_, fieldsType, _) =>
        code"${sc.Type.dsl.DeleteBuilderMock}(${sc.Type.dsl.DeleteParams}.empty, $fieldsType.structure, map)"
      case RepoMethod.Delete(_, id) =>
        code"$ZIO.succeed(map.remove(${id.paramName}).isDefined)"
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        code"$ZIO.succeed(${idsParam.name}.map(id => map.remove(id)).count(_.isDefined).toLong)"
      case RepoMethod.InsertStreaming(_, _, _) =>
        code"""|unsaved.scanZIO(0L) { case (acc, row) =>
               |  ZIO.succeed {
               |    map += (row.${id.paramName} -> row)
               |    acc + 1
               |  }
               |}.runLast.map(_.getOrElse(0L))""".stripMargin
      case RepoMethod.InsertUnsavedStreaming(_, _) =>
        code"""|unsaved.scanZIO(0L) { case (acc, unsavedRow) =>
               |  ZIO.succeed {
               |    val row = toRow(unsavedRow)
               |    map += (row.${id.paramName} -> row)
               |    acc + 1
               |  }
               |}.runLast.map(_.getOrElse(0L))""".stripMargin
      case RepoMethod.SqlFile(_) =>
        // should not happen (tm)
        code"???"
    }

  override def testInsertMethod(x: ComputedTestInserts.InsertMethod): sc.Value =
    sc.Value(
      Nil,
      x.name,
      x.params,
      Nil,
      ZIO.of(ZConnection, Throwable, x.table.names.RowName),
      code"(new ${x.table.names.RepoImplName}).insert(new ${x.cls}(${x.values.map { case (p, expr) => code"$p = $expr" }.mkCode(", ")}))"
    )

  override val defaultedInstance: List[sc.Given] =
    textSupport.map(_.defaultedInstance).toList

  override def stringEnumInstances(wrapperType: sc.Type, underlying: sc.Type, sqlType: String, openEnum: Boolean): List[sc.ClassMember] = {
    val sqlTypeLit = sc.StrLit(sqlType)
    val arrayWrapper = sc.Type.ArrayOf(wrapperType)
    val arraySetter = sc.Given(
      tparams = Nil,
      name = arraySetterName,
      implicitParams = Nil,
      tpe = Setter.of(arrayWrapper),
      body =
        if (openEnum) code"${lookupSetter(sc.Type.ArrayOf(underlying))}.contramap(_.map(_.value))"
        else code"""|$Setter.forSqlType[$arrayWrapper](
                    |    (ps, i, v) => ps.setArray(i, ps.getConnection.createArrayOf($sqlTypeLit, v.map(x => x.value))),
                    |    java.sql.Types.ARRAY
                    |  )""".stripMargin,
      implicitOrUsing
    )
    val arrayJdbcDecoder = sc.Given(
      tparams = Nil,
      name = arrayJdbcDecoderName,
      implicitParams = Nil,
      tpe = JdbcDecoder.of(arrayWrapper),
      body =
        if (openEnum)
          code"""${lookupJdbcDecoder(sc.Type.ArrayOf(underlying))}.map(a => if (a == null) null else a.map(apply))"""
        else code"""${lookupJdbcDecoder(sc.Type.ArrayOf(underlying))}.map(a => if (a == null) null else a.map(force))""",
      implicitOrUsing
    )
    val arrayJdbcEncoder = sc.Given(
      tparams = Nil,
      name = arrayJdbcEncoderName,
      implicitParams = Nil,
      tpe = JdbcEncoder.of(arrayWrapper),
      // JdbcEncoder for unary types defined in terms of `Setter`
      body = code"""$JdbcEncoder.singleParamEncoder(using ${arraySetterName})""",
      implicitOrUsing
    )
    val jdbcEncoder = sc.Given(
      tparams = Nil,
      name = jdbcEncoderName,
      implicitParams = Nil,
      tpe = JdbcEncoder.of(wrapperType),
      body = code"""${lookupJdbcEncoder(underlying)}.contramap(_.value)""",
      implicitOrUsing
    )
    val jdbcDecoder = {
      val body =
        if (openEnum) code"${lookupJdbcDecoder(underlying)}.map($wrapperType.apply)"
        else code"""|${lookupJdbcDecoder(underlying)}.flatMap { s =>
                  |  new ${JdbcDecoder.of(wrapperType)} {
                  |    override def unsafeDecode(columIndex: ${TypesScala.Int}, rs: ${TypesJava.ResultSet}): (${TypesScala.Int}, $wrapperType) = {
                  |      def error(msg: ${TypesJava.String}): $JdbcDecoderError =
                  |        $JdbcDecoderError(
                  |          message = s"Error decoding $wrapperType from ResultSet",
                  |          cause = new RuntimeException(msg),
                  |          metadata = rs.getMetaData,
                  |          row = rs.getRow
                  |        )
                  |
                  |      $wrapperType.apply(s).fold(e => throw error(e), (columIndex, _))
                  |    }
                  |  }
                  |}""".stripMargin
      sc.Given(tparams = Nil, name = jdbcDecoderName, implicitParams = Nil, tpe = JdbcDecoder.of(wrapperType), body = body, implicitOrUsing)
    }

    val setter = sc.Given(
      tparams = Nil,
      name = setterName,
      implicitParams = Nil,
      tpe = Setter.of(wrapperType),
      body = code"""${lookupSetter(underlying)}.contramap(_.value)""",
      implicitOrUsing
    )

    val parameterMetadata = {
      val body = code"$PGType.instance[$wrapperType](${sqlTypeLit}, ${TypesJava.SqlTypes}.OTHER)"
      sc.Given(tparams = Nil, name = pgTypeName, implicitParams = Nil, tpe = PGType.of(wrapperType), body = body, implicitOrUsing)
    }

    val text = textSupport.map(_.anyValInstance(wrapperType, underlying))

    List(
      Option(arraySetter),
      Option(arrayJdbcDecoder),
      Option(arrayJdbcEncoder),
      Option(jdbcEncoder),
      Option(jdbcDecoder),
      Option(setter),
      ifDsl(parameterMetadata),
      text
    ).flatten
  }

  override def wrapperTypeInstances(wrapperType: sc.Type.Qualified, underlying: sc.Type, overrideDbType: Option[String]): List[sc.ClassMember] =
    List(
      Option(
        sc.Given(
          tparams = Nil,
          name = jdbcEncoderName,
          implicitParams = Nil,
          tpe = JdbcEncoder.of(wrapperType),
          body = code"""${lookupJdbcEncoder(underlying)}.contramap(_.value)""",
          implicitOrUsing
        )
      ),
      Option(
        sc.Given(
          tparams = Nil,
          name = arrayJdbcEncoderName,
          implicitParams = Nil,
          tpe = JdbcEncoder.of(sc.Type.ArrayOf(wrapperType)),
          body = code"""${lookupJdbcEncoder(sc.Type.ArrayOf(underlying))}.contramap(_.map(_.value))""",
          implicitOrUsing
        )
      ),
      Option(
        sc.Given(
          tparams = Nil,
          name = jdbcDecoderName,
          implicitParams = Nil,
          tpe = JdbcDecoder.of(wrapperType),
          body = code"""${lookupJdbcDecoder(underlying)}.map($wrapperType.apply)""",
          implicitOrUsing
        )
      ),
      Option(
        sc.Given(
          tparams = Nil,
          name = arrayJdbcDecoderName,
          implicitParams = Nil,
          tpe = JdbcDecoder.of(sc.Type.ArrayOf(wrapperType)),
          body = code"""${lookupJdbcDecoder(sc.Type.ArrayOf(underlying))}.map(_.map($wrapperType.apply))""",
          implicitOrUsing
        )
      ),
      Option(
        sc.Given(
          tparams = Nil,
          name = setterName,
          implicitParams = Nil,
          tpe = Setter.of(wrapperType),
          body = code"""${lookupSetter(underlying)}.contramap(_.value)""",
          implicitOrUsing
        )
      ),
      Option(
        sc.Given(
          tparams = Nil,
          name = arraySetterName,
          implicitParams = Nil,
          tpe = Setter.of(sc.Type.ArrayOf(wrapperType)),
          body = code"""${lookupSetter(sc.Type.ArrayOf(underlying))}.contramap(_.map(_.value))""",
          implicitOrUsing
        )
      ),
      ifDsl(
        sc.Given(
          tparams = Nil,
          name = pgTypeName,
          implicitParams = Nil,
          tpe = PGType.of(wrapperType),
          body = overrideDbType match {
            case Some(dbType) => code"PGType.instance(${sc.StrLit(dbType)}, ${TypesJava.SqlTypes}.OTHER)"
            case None         => code"${lookupPgTypeFor(underlying)}.as"
          },
          implicitOrUsing
        )
      ),
      textSupport.map(_.anyValInstance(wrapperType, underlying))
    ).flatten

  override def missingInstances: List[sc.ClassMember] = {

    /** Adapted from Quill implementation
      *
      * Works for primitive types but not for more complex types
      */
    def primitiveArrayDecoder(T: sc.Type.Qualified) = {
      val body =
        code"""|new ${JdbcDecoder.of(sc.Type.ArrayOf(T))} {
               |  override def unsafeDecode(columIndex: ${TypesScala.Int}, rs: ${TypesJava.ResultSet}): (${TypesScala.Int}, ${sc.Type.ArrayOf(T)}) = {
               |    val arr = rs.getArray(columIndex)
               |    if (arr eq null) columIndex -> null
               |    else {
               |      columIndex ->
               |        arr
               |          .getArray
               |          .asInstanceOf[Array[Any]]
               |          .foldLeft(Array.newBuilder[${T.value}]) {
               |            case (b, x: ${T.value}) => b += x
               |            case (b, x: java.lang.Number) => b += x.asInstanceOf[${T.value}]
               |            case (_, x) =>
               |              throw $JdbcDecoderError(
               |                message = s"Error decoding ${TypesScala.Array}(${T.value}) from ResultSet",
               |                cause = new IllegalStateException(
               |                  s"Retrieved $${x.getClass.getCanonicalName} type from JDBC array, but expected (${T.value}). Re-check your decoder implementation"
               |                ),
               |                metadata = rs.getMetaData,
               |                row = rs.getRow
               |              )
               |          }
               |          .result()
               |    }
               |  }
               |}""".stripMargin
      sc.Given(tparams = Nil, name = sc.Ident(s"${T.name.value}ArrayDecoder"), implicitParams = Nil, tpe = JdbcDecoder.of(sc.Type.ArrayOf(T)), body = body, implicitOrUsing)
    }

    def primitiveArraySetter(T: sc.Type.Qualified, sqlType: sc.StrLit, asAnyRef: sc.Code => sc.Code) = {
      val v = sc.Ident("v")
      val body =
        code"""|$Setter.forSqlType[${sc.Type.ArrayOf(T)}](
               |  (ps, i, $v) => {
               |    ps.setArray(i, ps.getConnection.createArrayOf($sqlType, ${asAnyRef(v)}))
               |  },
               |  ${TypesJava.SqlTypes}.ARRAY
               |)""".stripMargin
      sc.Given(tparams = Nil, name = sc.Ident(s"${T.name.value}ArraySetter"), implicitParams = Nil, tpe = Setter.of(sc.Type.ArrayOf(T)), body = body, implicitOrUsing)
    }

    def primitiveArrayEncoder(T: sc.Type.Qualified) = {
      sc.Given(
        tparams = Nil,
        name = sc.Ident(s"${T.name.value}ArrayEncoder"),
        implicitParams = Nil,
        tpe = JdbcEncoder.of(sc.Type.ArrayOf(T)),
        body = code"""$JdbcEncoder.singleParamEncoder(using ${T.name.value}ArraySetter)""",
        implicitOrUsing
      )
    }

    def ScalaBigDecimal =
      List(
        sc.Given(
          tparams = Nil,
          name = sc.Ident("ScalaBigDecimalArrayEncoder"),
          implicitParams = Nil,
          tpe = JdbcEncoder.of(sc.Type.ArrayOf(TypesScala.BigDecimal)),
          body = code"""BigDecimalArrayEncoder.contramap(_.map(_.bigDecimal))""",
          implicitOrUsing
        ),
        sc.Given(
          tparams = Nil,
          name = sc.Ident("ScalaBigDecimalArrayDecoder"),
          implicitParams = Nil,
          tpe = JdbcDecoder.of(sc.Type.ArrayOf(TypesScala.BigDecimal)),
          body = code"""BigDecimalArrayDecoder.map(v => if (v eq null) null else v.map(${TypesScala.BigDecimal}.apply))""",
          implicitOrUsing
        ),
        sc.Given(
          tparams = Nil,
          name = sc.Ident("ScalaBigDecimalArraySetter"),
          implicitParams = Nil,
          tpe = Setter.of(sc.Type.ArrayOf(TypesScala.BigDecimal)),
          body = code"""BigDecimalArraySetter.contramap(_.map(_.bigDecimal))""",
          implicitOrUsing
        )
      )

    def all(T: sc.Type.Qualified, sqlType: String, asAnyRef: sc.Code => sc.Code) = {
      List(primitiveArrayDecoder(T), primitiveArraySetter(T, sc.StrLit(sqlType), asAnyRef), primitiveArrayEncoder(T))
    }

    all(TypesJava.String, "varchar", array => code"$array.map(x => x: ${TypesScala.AnyRef})") ++
      all(TypesScala.Int, "int4", array => code"$array.map(x => int2Integer(x): ${TypesScala.AnyRef})") ++
      all(TypesScala.Long, "int8", array => code"$array.map(x => long2Long(x): ${TypesScala.AnyRef})") ++
      all(TypesScala.Float, "float4", array => code"$array.map(x => float2Float(x): ${TypesScala.AnyRef})") ++
      all(TypesScala.Double, "float8", array => code"$array.map(x => double2Double(x): ${TypesScala.AnyRef})") ++
      all(TypesJava.BigDecimal, "numeric", array => code"$array.map(x => x: ${TypesScala.AnyRef})") ++
      ScalaBigDecimal ++
      all(TypesScala.Boolean, "bool", array => code"$array.map(x => boolean2Boolean(x): ${TypesScala.AnyRef})")
  }

  override def rowInstances(tpe: sc.Type, cols: NonEmptyList[ComputedColumn], rowType: DbLib.RowType): List[sc.ClassMember] = {
    val text = textSupport.map(_.rowInstance(tpe, cols))
    val decoder = {
      val body =
        if (cols.length == 1)
          code"""${lookupJdbcDecoder(cols.head.tpe)}.map(v => $tpe(${cols.head.name} = v))""".stripMargin
        else {
          val namedParams = cols.zipWithIndex.map { case (c, idx) =>
            code"${c.name} = ${lookupJdbcDecoder(c.tpe)}.unsafeDecode(columIndex + $idx, rs)._2"
          }

          code"""|new ${JdbcDecoder.of(tpe)} {
                 |  override def unsafeDecode(columIndex: ${TypesScala.Int}, rs: ${TypesJava.ResultSet}): (${TypesScala.Int}, $tpe) =
                 |    columIndex + ${cols.length - 1} ->
                 |      $tpe(
                 |        ${namedParams.mkCode(",\n")}
                 |      )
                 |}""".stripMargin
        }
      sc.Given(tparams = Nil, name = jdbcDecoderName, implicitParams = Nil, tpe = JdbcDecoder.of(tpe), body = body, implicitOrUsing)
    }
    rowType match {
      case DbLib.RowType.Writable      => text.toList
      case DbLib.RowType.ReadWriteable => List(decoder) ++ text
      case DbLib.RowType.Readable      => List(decoder)
    }
  }

  override def customTypeInstances(ct: CustomType): List[sc.ClassMember] =
    customTypeOne(ct) ++ (if (ct.forbidArray) Nil else customTypeArray(ct))

  def customTypeOne(ct: CustomType): List[sc.Given] = {

    val jdbcEncoder = sc.Given(
      tparams = Nil,
      name = jdbcEncoderName,
      implicitParams = Nil,
      tpe = JdbcEncoder.of(ct.typoType),
      // JdbcEncoder for unary types defined in terms of `Setter`
      body = code"""$JdbcEncoder.singleParamEncoder(using $setterName)""",
      implicitOrUsing
    )

    val jdbcDecoder = {
      val expectedType = sc.StrLit(ct.fromTypo.jdbcType.render.asString)
      val body =
        code"""|${JdbcDecoder.of(ct.typoType)}(
               |  (rs: ${TypesJava.ResultSet}) => (i: ${TypesScala.Int}) => {
               |    val v = rs.getObject(i)
               |    if (v eq null) null else ${ct.toTypo0(code"v.asInstanceOf[${ct.toTypo.jdbcType}]")}
               |  },
               |  $expectedType
               |)""".stripMargin
      sc.Given(tparams = Nil, name = jdbcDecoderName, implicitParams = Nil, tpe = JdbcDecoder.of(ct.typoType), body = body, implicitOrUsing)
    }

    val setter = {
      val v = sc.Ident("v")
      val body =
        code"""|$Setter.other(
               |  (ps, i, $v) => {
               |    ps.setObject(
               |      i,
               |      ${ct.fromTypo0(v)}
               |    )
               |  },
               |  ${sc.StrLit(ct.sqlType)}
               |)""".stripMargin
      sc.Given(tparams = Nil, name = setterName, implicitParams = Nil, tpe = Setter.of(ct.typoType), body = body, implicitOrUsing)
    }

    val pgType = {
      val body = code"$PGType.instance[${ct.typoType}](${sc.StrLit(ct.sqlType)}, ${TypesJava.SqlTypes}.OTHER)"
      sc.Given(Nil, pgTypeName, Nil, PGType.of(ct.typoType), body, implicitOrUsing)
    }
    val text = textSupport.map(_.customTypeInstance(ct))

    List(Option(jdbcEncoder), Option(jdbcDecoder), Option(setter), ifDsl(pgType), text).flatten
  }

  def customTypeArray(ct: CustomType): List[sc.Given] = {
    val fromTypo = ct.fromTypoInArray.getOrElse(ct.fromTypo)
    val toTypo = ct.toTypoInArray.getOrElse(ct.toTypo)
    val jdbcEncoder = sc.Given(
      tparams = Nil,
      name = arrayJdbcEncoderName,
      implicitParams = Nil,
      tpe = JdbcEncoder.of(sc.Type.ArrayOf(ct.typoType)),
      // JdbcEncoder for unary types defined in terms of `Setter`
      body = code"""$JdbcEncoder.singleParamEncoder(using ${arraySetterName})""",
      implicitOrUsing
    )

    val jdbcDecoder = {
      val expectedType = sc.StrLit(sc.Type.ArrayOf(ct.fromTypo.jdbcType).render.asString)
      val body =
        code"""|${JdbcDecoder.of(sc.Type.ArrayOf(ct.typoType))}((rs: ${TypesJava.ResultSet}) => (i: ${TypesScala.Int}) =>
               |  rs.getArray(i) match {
               |    case null => null
               |    case arr => arr.getArray.asInstanceOf[Array[AnyRef]].map(x => ${toTypo.toTypo(code"x.asInstanceOf[${toTypo.jdbcType}]", ct.typoType)})
               |  },
               |  $expectedType
               |)""".stripMargin
      sc.Given(tparams = Nil, name = arrayJdbcDecoderName, implicitParams = Nil, tpe = JdbcDecoder.of(sc.Type.ArrayOf(ct.typoType)), body = body, implicitOrUsing)
    }

    val setter = {
      val v = sc.Ident("v")
      val vv = sc.Ident("vv")
      val body =
        code"""|$Setter.forSqlType((ps, i, $v) =>
               |  ps.setArray(
               |    i,
               |    ps.getConnection.createArrayOf(
               |      ${sc.StrLit(ct.sqlType)},
               |      $v.map { $vv =>
               |        ${fromTypo.fromTypo0(vv)}
               |      }
               |    )
               |  ),
               |  ${TypesJava.SqlTypes}.ARRAY
               |)""".stripMargin

      sc.Given(tparams = Nil, name = arraySetterName, implicitParams = Nil, tpe = Setter.of(sc.Type.ArrayOf(ct.typoType)), body = body, implicitOrUsing)
    }

    List(jdbcEncoder, jdbcDecoder, setter)
  }
}
