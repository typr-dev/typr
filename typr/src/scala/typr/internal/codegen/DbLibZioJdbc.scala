package typr
package internal
package codegen

import typr.internal.analysis.MaybeReturnsRows

class DbLibZioJdbc(pkg: jvm.QIdent, inlineImplicits: Boolean, dslEnabled: Boolean, default: ComputedDefault, enableStreamingInserts: Boolean, override val lang: LangScala) extends DbLib {
  val dialect = lang.dialect
  private val sqlCast = new SqlCast(needsTimestampCasts = true)
  private val ZConnection = jvm.Type.Qualified("zio.jdbc.ZConnection")
  private val Throwable = jvm.Type.Qualified("java.lang.Throwable")
  private val ZStream = jvm.Type.Qualified("zio.stream.ZStream")
  private val ZIO = jvm.Type.Qualified("zio.ZIO")
  private val JdbcEncoder = jvm.Type.Qualified("zio.jdbc.JdbcEncoder")
  private val JdbcDecoder = jvm.Type.Qualified("zio.jdbc.JdbcDecoder")
  private val SqlFragment = jvm.Type.Qualified("zio.jdbc.SqlFragment")
  private val Segment = jvm.Type.Qualified("zio.jdbc.SqlFragment.Segment")
  private val Setter = jvm.Type.Qualified("zio.jdbc.SqlFragment.Setter")
  private val UpdateResult = jvm.Type.Qualified("zio.jdbc.UpdateResult")
  private val Chunk = jvm.Type.Qualified("zio.Chunk")
  private val NonEmptyChunk = jvm.Type.Qualified("zio.NonEmptyChunk")
  private val sqlInterpolator = jvm.Type.Qualified("zio.jdbc.sqlInterpolator")
  private val JdbcDecoderError = jvm.Type.Qualified("zio.jdbc.JdbcDecoderError")

  val textSupport: Option[DbLibTextSupport] =
    if (enableStreamingInserts) Some(new DbLibTextSupport(pkg, inlineImplicits, None, default, lang)) else None

  override val additionalFiles: List[typr.jvm.File] =
    textSupport match {
      case Some(textSupport) =>
        List(
          jvm.File(textSupport.Text, DbLibTextImplementations.Text(dialect), Nil, scope = Scope.Main),
          jvm.File(textSupport.streamingInsert, DbLibTextImplementations.streamingInsertZio(textSupport.Text, dialect), Nil, scope = Scope.Main)
        )
      case None => Nil
    }

  /** This type is basically a mapping from scala type to jdbc type name. zio-jdbc seems to use jdbc type number instead of the (potentially database-specific) type name. In the DSL we need to
    * generate some sql casts based on scala type, so it's unavoidable to have this mapping.
    *
    * A bit unfortunate maybe, but it's not the end of the world to provide it ourselves.
    */
  private val PGType = jvm.Type.Qualified("typr.dsl.PGType")

  def ifDsl(g: jvm.Given): Option[jvm.Given] =
    if (dslEnabled) Some(g) else None

  private def SQL(content: jvm.Code) = jvm.StringInterpolate(sqlInterpolator, jvm.Ident("sql"), content)

  private val arraySetterName: jvm.Ident = jvm.Ident("arraySetter")
  private val arrayJdbcDecoderName: jvm.Ident = jvm.Ident("arrayJdbcDecoder")
  private val arrayJdbcEncoderName: jvm.Ident = jvm.Ident("arrayJdbcEncoder")
  private val jdbcDecoderName: jvm.Ident = jvm.Ident("jdbcDecoder")
  private val jdbcEncoderName: jvm.Ident = jvm.Ident("jdbcEncoder")
  private val setterName: jvm.Ident = jvm.Ident("setter")
  private val pgTypeName: jvm.Ident = jvm.Ident("pgType")

  private def dbNames(cols: NonEmptyList[ComputedColumn], isRead: Boolean): jvm.Code =
    cols
      .map(c => code"${c.dbName}" ++ (if (isRead) sqlCast.fromPgCode(c) else jvm.Code.Empty))
      .mkCode(", ")

  private val missingInstancesByType: Map[jvm.Type, jvm.QIdent] =
    missingInstances.collect { case x: jvm.Given => (x.tpe, pkg / x.name) }.toMap

  /** Resolve known implicits at generation-time instead of at compile-time */
  private def lookupJdbcDecoder(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) JdbcDecoder.of(tpe)
    else
      jvm.Type.base(tpe) match {
        case TypesScala.BigDecimal             => code"$JdbcDecoder.bigDecimalDecoderScala"
        case TypesScala.Boolean                => code"$JdbcDecoder.booleanDecoder"
        case TypesScala.Byte                   => code"$JdbcDecoder.byteDecoder"
        case TypesScala.Double                 => code"$JdbcDecoder.doubleDecoder"
        case TypesScala.Float                  => code"$JdbcDecoder.floatDecoder"
        case TypesScala.Int                    => code"$JdbcDecoder.intDecoder"
        case TypesScala.Long                   => code"$JdbcDecoder.longDecoder"
        case TypesJava.String                  => code"$JdbcDecoder.stringDecoder"
        case TypesJava.UUID                    => code"$JdbcDecoder.uuidDecoder"
        case lang.Optional(targ)               => code"$JdbcDecoder.optionDecoder(${dialect.usingCall}${lookupJdbcDecoder(targ)})"
        case jvm.Type.ArrayOf(TypesScala.Byte) => code"$JdbcDecoder.byteArrayDecoder"
        // generated type
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$x.$jdbcDecoderName"
        case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arrayJdbcDecoderName"
        case x if missingInstancesByType.contains(JdbcDecoder.of(x)) =>
          code"${missingInstancesByType(JdbcDecoder.of(x))}"
        case other =>
          code"${JdbcDecoder.of(other)}"
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  private def lookupJdbcEncoder(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) JdbcEncoder.of(tpe)
    else
      jvm.Type.base(tpe) match {
        case TypesScala.BigDecimal             => code"$JdbcEncoder.bigDecimalEncoderScala"
        case TypesScala.Boolean                => code"$JdbcEncoder.booleanEncoder"
        case TypesScala.Byte                   => code"$JdbcEncoder.byteEncoder"
        case TypesScala.Double                 => code"$JdbcEncoder.doubleEncoder"
        case TypesScala.Float                  => code"$JdbcEncoder.floatEncoder"
        case TypesScala.Int                    => code"$JdbcEncoder.intEncoder"
        case TypesScala.Long                   => code"$JdbcEncoder.longEncoder"
        case TypesJava.String                  => code"$JdbcEncoder.stringEncoder"
        case TypesJava.UUID                    => code"$JdbcEncoder.uuidEncoder"
        case lang.Optional(targ)               => code"$JdbcEncoder.optionEncoder(${dialect.usingCall}${lookupJdbcEncoder(targ)})"
        case jvm.Type.ArrayOf(TypesScala.Byte) => code"$JdbcEncoder.byteArrayEncoder"
        // generated type
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$x.$jdbcEncoderName"
        case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arrayJdbcEncoderName"
        case x if missingInstancesByType.contains(JdbcDecoder.of(x)) =>
          code"${missingInstancesByType(JdbcEncoder.of(x))}"
        case other =>
          code"${JdbcEncoder.of(other)}"
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  private def lookupSetter(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) Setter.of(tpe)
    else
      jvm.Type.base(tpe) match {
        case TypesScala.BigDecimal             => code"$Setter.bigDecimalScalaSetter"
        case TypesScala.Boolean                => code"$Setter.booleanSetter"
        case TypesScala.Byte                   => code"$Setter.byteSetter"
        case TypesScala.Double                 => code"$Setter.doubleSetter"
        case TypesScala.Float                  => code"$Setter.floatSetter"
        case TypesScala.Int                    => code"$Setter.intSetter"
        case TypesScala.Long                   => code"$Setter.longSetter"
        case TypesJava.String                  => code"$Setter.stringSetter"
        case TypesJava.UUID                    => code"$Setter.uuidParamSetter"
        case lang.Optional(targ)               => code"$Setter.optionParamSetter(${dialect.usingCall}${lookupSetter(targ)})"
        case jvm.Type.ArrayOf(TypesScala.Byte) => code"$Setter.byteArraySetter"
        // generated type
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$setterName"
        case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arraySetterName"
        case x if missingInstancesByType.contains(Setter.of(x)) =>
          code"${missingInstancesByType(Setter.of(x))}"
        case other =>
          code"${Setter.of(other)}"
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupPgTypeFor(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) jvm.Summon(PGType.of(tpe)).code
    else
      jvm.Type.base(tpe) match {
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
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$pgTypeName"
        // customized type mapping
        case x if missingInstancesByType.contains(PGType.of(x)) =>
          code"${missingInstancesByType(PGType.of(x))}"
        case jvm.Type.ArrayOf(TypesScala.Byte) => code"$PGType.PGTypeByteArray"
        // fallback array case.
        case jvm.Type.ArrayOf(targ) => code"$PGType.forArray(${dialect.usingCall}${lookupPgTypeFor(targ)})"
        case other                  => jvm.Summon(PGType.of(other)).code
      }

  private def runtimeInterpolateValue(name: jvm.Code, tpe: jvm.Type): jvm.Code = {
    if (inlineImplicits)
      code"$${$Segment.paramSegment($name)(${dialect.usingCall}${lookupSetter(tpe)})}"
    else code"$${$name}"
  }

  private def matchId(id: IdComputed): jvm.Code =
    id match {
      case id: IdComputed.Unary =>
        code"${id.col.dbName} = ${runtimeInterpolateValue(id.paramName, id.tpe)}"
      case composite: IdComputed.Composite =>
        code"${composite.cols.map(cc => code"${cc.dbName} = ${runtimeInterpolateValue(code"${composite.paramName}.${cc.name}", cc.tpe)}").mkCode(" AND ")}"
    }

  override def resolveConstAs(typoType: TypoType): jvm.Code = {
    val tpe = typoType.jvmType
    typoType match {
      case TypoType.Nullable(_, inner) =>
        code"${lang.dsl.ConstAsAs}[${inner.jvmType}](${lookupJdbcEncoder(tpe)}, ${lookupPgTypeFor(tpe)})"
      case _ =>
        code"${lang.dsl.ConstAsAs}[$tpe](${dialect.usingCall}${lookupJdbcEncoder(tpe)}, ${lookupPgTypeFor(tpe)})"
    }
  }

  val batchSize = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("batchSize"), TypesScala.Int, Some(code"10000"))

  override def repoSig(repoMethod: RepoMethod): Either[DbLib.NotImplementedFor, jvm.Method] = {
    def sig(params: List[jvm.Param[jvm.Type]], returnType: jvm.Type) = Right(
      jvm.Method(
        Nil,
        comments = repoMethod.comment,
        tparams = Nil,
        name = jvm.Ident(repoMethod.methodName),
        params = params,
        implicitParams = Nil,
        tpe = returnType,
        throws = Nil,
        body = jvm.Body.Abstract,
        isOverride = false,
        isDefault = false
      )
    )

    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, returnType = lang.dsl.SelectBuilder.of(fieldsType, rowType))
      case RepoMethod.SelectAll(_, _, rowType) =>
        sig(params = Nil, returnType = ZStream.of(ZConnection, Throwable, rowType))
      case RepoMethod.SelectById(_, _, id, rowType) =>
        sig(params = List(id.param), returnType = ZIO.of(ZConnection, Throwable, TypesScala.Option.of(rowType)))
      case RepoMethod.SelectByIds(_, _, _, idsParam, rowType) =>
        sig(params = List(idsParam), returnType = ZStream.of(ZConnection, Throwable, rowType))
      case RepoMethod.SelectByIdsTracked(x) =>
        sig(params = List(x.idsParam), returnType = ZIO.of(ZConnection, Throwable, TypesScala.Map.of(x.idComputed.tpe, x.rowType)))
      case RepoMethod.SelectByUnique(_, keyColumns, _, rowType) =>
        sig(params = keyColumns.toList.map(_.param), returnType = ZIO.of(ZConnection, Throwable, TypesScala.Option.of(rowType)))
      case RepoMethod.SelectByFieldValues(_, _, _, fieldValueOrIdsParam, rowType) =>
        sig(params = List(fieldValueOrIdsParam), returnType = ZStream.of(ZConnection, Throwable, rowType))
      case RepoMethod.UpdateBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, returnType = lang.dsl.UpdateBuilder.of(fieldsType, rowType))
      case RepoMethod.UpdateFieldValues(_, id, varargs, _, _, _) =>
        sig(params = List(id.param, varargs), returnType = ZIO.of(ZConnection, Throwable, TypesScala.Boolean))
      case RepoMethod.Update(_, _, _, param, _) =>
        sig(params = List(param), returnType = ZIO.of(ZConnection, Throwable, TypesScala.Option.of(param.tpe)))
      case RepoMethod.Insert(_, _, _, unsavedParam, _, returningStrategy) =>
        sig(params = List(unsavedParam), returnType = ZIO.of(ZConnection, Throwable, returningStrategy.returnType))
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _, returningStrategy) =>
        sig(params = List(unsavedParam), returnType = ZIO.of(ZConnection, Throwable, returningStrategy.returnType))
      case RepoMethod.InsertStreaming(_, rowType, _) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), ZStream.of(ZConnection, TypesJava.Throwable, rowType))
        sig(params = List(unsavedParam, batchSize), returnType = ZIO.of(ZConnection, TypesJava.Throwable, TypesScala.Long))
      case RepoMethod.UpsertBatch(_, _, _, _, _) =>
        Left(DbLib.NotImplementedFor(repoMethod, "zio-jdbc"))
      case RepoMethod.UpsertStreaming(_, _, rowType, _) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), ZStream.of(ZConnection, TypesJava.Throwable, rowType))
        sig(params = List(unsavedParam, batchSize), returnType = ZIO.of(ZConnection, TypesJava.Throwable, TypesScala.Long))
      case RepoMethod.Upsert(_, _, _, unsavedParam, rowType, _) =>
        sig(params = List(unsavedParam), returnType = ZIO.of(ZConnection, Throwable, UpdateResult.of(rowType)))
      case RepoMethod.InsertUnsavedStreaming(_, unsaved) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), ZStream.of(ZConnection, TypesJava.Throwable, unsaved.tpe))
        sig(params = List(unsavedParam, batchSize), returnType = ZIO.of(ZConnection, TypesJava.Throwable, TypesScala.Long))
      case RepoMethod.DeleteBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, returnType = lang.dsl.DeleteBuilder.of(fieldsType, rowType))
      case RepoMethod.Delete(_, id) =>
        sig(params = List(id.param), returnType = ZIO.of(ZConnection, Throwable, TypesScala.Boolean))
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        sig(params = List(idsParam), returnType = ZIO.of(ZConnection, Throwable, TypesScala.Long))
      case RepoMethod.SqlFile(sqlScript) =>
        val params = sqlScript.params.map(p => jvm.Param(p.name, p.tpe))

        val retType = sqlScript.maybeRowName match {
          case MaybeReturnsRows.Query(rowName) => ZStream.of(ZConnection, Throwable, rowName)
          case MaybeReturnsRows.Update         => ZIO.of(ZConnection, Throwable, TypesScala.Long)
        }
        sig(params = params, returnType = retType)
    }
  }

  override def repoImpl(repoMethod: RepoMethod): jvm.Body =
    repoMethod match {
      case RepoMethod.SelectBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"""${lang.dsl.SelectBuilder}.of(${jvm.StrLit(relName.quotedValue)}, $fieldsType.structure, ${lookupJdbcDecoder(rowType)})""")

      case RepoMethod.SelectAll(relName, cols, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        val sql = SQL(code"""select $joinedColNames from $relName""")
        jvm.Body.Expr(code"""$sql.query(${dialect.usingCall}${lookupJdbcDecoder(rowType)}).selectStream()""")

      case RepoMethod.SelectById(relName, cols, id, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        val sql = SQL(code"""select $joinedColNames from $relName where ${matchId(id)}""")
        jvm.Body.Expr(code"""$sql.query(${dialect.usingCall}${lookupJdbcDecoder(rowType)}).selectOne""")

      case RepoMethod.SelectByIds(relName, cols, computedId, idsParam, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        computedId match {
          case x: IdComputed.Composite =>
            val vals = x.cols.map(col => code"val ${col.name} = ${idsParam.name}.map(_.${col.name})")
            val sql = SQL {
              code"""|select ${dbNames(cols, isRead = true)}
                     |from $relName
                     |where (${x.cols.map(col => col.dbCol.name.code).mkCode(", ")})
                     |in (select ${x.cols.map(col => code"unnest(${runtimeInterpolateValue(col.name, jvm.Type.ArrayOf(col.tpe))})").mkCode(", ")})
                     |""".stripMargin
            }
            jvm.Body.Stmts(
              vals.toList ++ List(code"$sql.query(using ${lookupJdbcDecoder(rowType)}).selectStream()")
            )

          case unaryId: IdComputed.Unary =>
            val sql = SQL(
              code"""select $joinedColNames from $relName where ${unaryId.col.dbName} = ANY(${runtimeInterpolateValue(idsParam.name, idsParam.tpe)})"""
            )
            jvm.Body.Expr(code"""$sql.query(${dialect.usingCall}${lookupJdbcDecoder(rowType)}).selectStream()""")
        }
      case RepoMethod.SelectByIdsTracked(x) =>
        jvm.Body.Expr(
          code"""|${x.methodName}(${x.idsParam.name}).runCollect.map { rows =>
                 |  val byId = rows.view.map(x => (x.${x.idComputed.paramName}, x)).toMap
                 |  ${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap
                 |}""".stripMargin
        )

      case RepoMethod.SelectByUnique(relName, keyColumns, allCols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(allCols, isRead = true)}
                 |from $relName
                 |where ${keyColumns.map(c => code"${c.dbName} = ${runtimeInterpolateValue(c.name, c.tpe)}").mkCode(" AND ")}
                 |""".stripMargin
        }
        jvm.Body.Expr(code"""$sql.query(${dialect.usingCall}${lookupJdbcDecoder(rowType)}).selectOne""")

      case RepoMethod.SelectByFieldValues(relName, cols, fieldValue, fieldValueOrIdsParam, rowType) =>
        val cases =
          cols.map { col =>
            val fr = SQL(code"${col.dbName} = ${runtimeInterpolateValue(jvm.Ident("value"), col.tpe)}")
            code"case $fieldValue.${col.name}(value) => $fr"
          }

        jvm.Body.Expr(
          code"""${fieldValueOrIdsParam.name} match {
                |  case Nil      => selectAll
                |  case nonEmpty =>
                |    val wheres = $SqlFragment.empty.and(
                |      nonEmpty.map {
                |        ${cases.mkCode("\n")}
                |      }
                |    )
              |    ${SQL(code"""select ${dbNames(cols, isRead = true)} from $relName where $$wheres""")}.query(${dialect.usingCall}${lookupJdbcDecoder(rowType)}).selectStream()
                |}""".stripMargin
        )

      case RepoMethod.UpdateFieldValues(relName, id, varargs, fieldValue, cases0, _) =>
        val cases: NonEmptyList[jvm.Code] =
          cases0.map { col =>
            val sql = SQL(code"${col.dbName} = ${runtimeInterpolateValue(jvm.Ident("value"), col.tpe)}${sqlCast.toPgCode(col)}")
            code"case $fieldValue.${col.name}(value) => $sql"
          }

        val sql = SQL {
          code"""|update $relName
                 |set $$updates
                 |where ${matchId(id)}
                 |""".stripMargin
        }
        jvm.Body.Expr(
          code"""$NonEmptyChunk.fromIterableOption(${varargs.name}) match {
                |  case None           => $ZIO.succeed(false)
                |  case Some(nonEmpty) =>
                |    val updates = nonEmpty.map { ${cases.mkCode("\n")} }.mkFragment($SqlFragment(", "))
                |    $sql.update.map(_ > 0)
                |}""".stripMargin
        )

      case RepoMethod.UpdateBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"${lang.dsl.UpdateBuilder}.of(${jvm.StrLit(relName.quotedValue)}, $fieldsType.structure, ${lookupJdbcDecoder(rowType)})")

      case RepoMethod.Update(relName, cols, id, param, writeableCols) =>
        val sql = SQL(
          code"""|update $relName
                 |set ${writeableCols.map { col => code"${col.dbName} = ${runtimeInterpolateValue(code"${param.name}.${col.name}", col.tpe)}${sqlCast.toPgCode(col)}" }.mkCode(",\n")}
                |where ${matchId(id)}
                |returning ${dbNames(cols, isRead = true)}""".stripMargin
        )
        jvm.Body.Stmts(
          List(
            code"val ${id.paramName} = ${param.name}.${id.paramName}",
            code"""|$sql
               |  .query(${dialect.usingCall}${lookupJdbcDecoder(param.tpe)})
               |  .selectOne""".stripMargin
          )
        )

      case RepoMethod.InsertUnsaved(relName, cols, unsaved, unsavedParam, _, default, returningStrategy) =>
        val rowType = returningStrategy.returnType
        val cases0 = unsaved.normalColumns.map { col =>
          val set = SQL(code"${runtimeInterpolateValue(code"${unsavedParam.name}.${col.name}", col.tpe)}${sqlCast.toPgCode(col)}")
          code"""Some((${SQL(col.dbName)}, $set))"""
        }
        val cases1 = unsaved.defaultedCols.map { case ComputedRowUnsaved.DefaultedCol(col @ ComputedColumn(_, ident, _, _), origType, _) =>
          val setValue = SQL(code"${runtimeInterpolateValue(code"value: $origType", origType)}${sqlCast.toPgCode(col)}")
          code"""|${unsavedParam.name}.$ident match {
                 |  case ${default.Defaulted}.${default.UseDefault}() => None
                 |  case ${default.Defaulted}.${default.Provided}(value) => Some((${SQL(col.dbName)}, $setValue))
                 |}""".stripMargin
        }

        val sqlEmpty = SQL {
          code"""|insert into $relName default values
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        jvm.Body.Stmts(
          List(
            code"""|val fs = List(
                 |  ${(cases0 ++ cases1.toList).mkCode(",\n")}
                 |).flatten""".stripMargin,
            code"""|val q = if (fs.isEmpty) {
                 |  $sqlEmpty
                 |} else {
                 |  val names  = fs.map { case (n, _) => n }.mkFragment($SqlFragment(", "))
                 |  val values = fs.map { case (_, f) => f }.mkFragment($SqlFragment(", "))
                 |  ${SQL(code"insert into $relName($$names) values ($$values) returning ${dbNames(cols, isRead = true)}")}
                 |}""".stripMargin,
            code"q.insertReturning(${dialect.usingCall}${lookupJdbcDecoder(rowType)}).map(_.updatedKeys.head)"
          )
        )
      case RepoMethod.Upsert(relName, cols, id, unsavedParam, rowType, writeableColumnsWithId) =>
        val writeableColumnsNotId = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))

        val values = writeableColumnsWithId.map { c =>
          code"${runtimeInterpolateValue(code"${unsavedParam.name}.${c.name}", c.tpe)}${sqlCast.toPgCode(c)}"
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

        jvm.Body.Expr(code"$sql.insertReturning(${dialect.usingCall}${lookupJdbcDecoder(rowType)})")

      case RepoMethod.UpsertBatch(_, _, _, _, _) =>
        jvm.Body.Expr("???")
      case RepoMethod.UpsertStreaming(relName, id, rowType, writeableColumnsWithId) =>
        val writeableColumnsNotId = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))

        val conflictAction = writeableColumnsNotId match {
          case Nil => code"do nothing"
          case nonEmpty =>
            code"""|do update set
                   |  ${nonEmpty.map { c => code"${c.dbName.code} = EXCLUDED.${c.dbName.code}" }.mkCode(",\n")}""".stripMargin
        }
        val tempTablename = s"${relName.name}_TEMP"

        val copySql = lang.s(code"copy $tempTablename(${dbNames(writeableColumnsWithId, isRead = false)}) from stdin")

        val mergeSql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |select * from $tempTablename
                 |on conflict (${dbNames(id.cols, isRead = false)})
                 |$conflictAction
                 |;
                 |drop table $tempTablename;""".stripMargin
        }
        jvm.Body.Stmts(
          List(
            code"val created = ${SQL(code"create temporary table $tempTablename (like $relName) on commit drop")}.execute",
            code"val copied = ${textSupport.get.streamingInsert}($copySql, batchSize, unsaved)(${dialect.usingCall}${textSupport.get.lookupTextFor(rowType)})",
            code"val merged = $mergeSql.update",
            code"created *> copied *> merged"
          )
        )

      case RepoMethod.Insert(relName, cols, _, unsavedParam, writeableColumnsWithId, returningStrategy) =>
        val rowType = returningStrategy.returnType
        val values = writeableColumnsWithId.map { c =>
          code"${runtimeInterpolateValue(code"${unsavedParam.name}.${c.name}", c.tpe)}${sqlCast.toPgCode(c)}"
        }
        val sql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${values.mkCode(", ")})
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }

        jvm.Body.Expr(code"$sql.insertReturning(${dialect.usingCall}${lookupJdbcDecoder(rowType)}).map(_.updatedKeys.head)")
      case RepoMethod.InsertStreaming(relName, rowType, writeableColumnsWithId) =>
        val sql = lang.s(code"COPY $relName(${dbNames(writeableColumnsWithId, isRead = false)}) FROM STDIN")
        jvm.Body.Expr(code"${textSupport.get.streamingInsert}($sql, batchSize, unsaved)(${dialect.usingCall}${textSupport.get.lookupTextFor(rowType)})")
      case RepoMethod.InsertUnsavedStreaming(relName, unsaved) =>
        val sql = lang.s(code"COPY $relName(${dbNames(unsaved.unsavedCols, isRead = false)}) FROM STDIN (DEFAULT '${DbLibTextSupport.DefaultValue}')")
        jvm.Body.Expr(code"${textSupport.get.streamingInsert}($sql, batchSize, unsaved)(${dialect.usingCall}${textSupport.get.lookupTextFor(unsaved.tpe)})")

      case RepoMethod.DeleteBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"${lang.dsl.DeleteBuilder}.of(${jvm.StrLit(relName.quotedValue)}, $fieldsType.structure, ${lookupJdbcDecoder(rowType)})")
      case RepoMethod.Delete(relName, id) =>
        val sql = SQL(code"""delete from $relName where ${matchId(id)}""")
        jvm.Body.Expr(code"$sql.delete.map(_ > 0)")

      case RepoMethod.DeleteByIds(relName, computedId, idsParam) =>
        computedId match {
          case x: IdComputed.Composite =>
            val vals = x.cols.map(col => code"val ${col.name} = ${idsParam.name}.map(_.${col.name})")
            val sql = SQL {
              code"""|delete
                     |from $relName
                     |where (${x.cols.map(col => col.dbCol.name.code).mkCode(", ")})
                     |in (select ${x.cols.map(col => code"unnest(${runtimeInterpolateValue(col.name, jvm.Type.ArrayOf(col.tpe))})").mkCode(", ")})
                     |""".stripMargin
            }
            jvm.Body.Stmts(vals.toList ++ List(code"$sql.delete"))

          case x: IdComputed.Unary =>
            val sql = SQL(
              code"""delete from $relName where ${code"${x.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, jvm.Type.ArrayOf(x.tpe))})"}"""
            )
            jvm.Body.Expr(code"$sql.delete")
        }

      case RepoMethod.SqlFile(sqlScript) =>
        val renderedScript: jvm.Code = sqlScript.sqlFile.decomposedSql.renderCode { (paramAtIndex: Int) =>
          val param = sqlScript.params.find(_.indices.contains(paramAtIndex)).get
          val cast = sqlCast.toPg(param).fold("")(_.withColons)
          code"${runtimeInterpolateValue(param.name, param.tpe)}$cast"
        }
        val ret = for {
          cols <- sqlScript.maybeCols.toOption
          rowName <- sqlScript.maybeRowName.toOption
        } yield {
          // this is necessary to make custom types work with sql scripts, unfortunately.
          val renderedWithCasts: jvm.Code =
            cols.toList.flatMap(c => sqlCast.fromPg(c.dbCol.tpe)) match {
              case Nil => renderedScript.code
              case _ =>
                val row = jvm.Ident("row")

                code"""|with $row as (
                       |  $renderedScript
                       |)
                       |select ${cols.map(c => code"$row.${c.dbCol.parsedName.originalName.code}${sqlCast.fromPgCode(c)}").mkCode(", ")}
                       |from $row""".stripMargin
            }
          jvm.Body.Stmts(
            List(
              code"""|val sql =
                   |  ${SQL(renderedWithCasts)}""".stripMargin,
              code"sql.query(${dialect.usingCall}${lookupJdbcDecoder(rowName)}).selectStream()"
            )
          )
        }
        ret.getOrElse {
          jvm.Body.Expr(code"${SQL(renderedScript)}.update")
        }
    }

  override def mockRepoImpl(id: IdComputed, repoMethod: RepoMethod, maybeToRow: Option[jvm.Param[jvm.Type.Function1]]): jvm.Body =
    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, _) =>
        jvm.Body.Expr(code"${lang.dsl.SelectBuilderMock}($fieldsType.structure, $ZIO.succeed($Chunk.fromIterable(map.values)), ${lang.dsl.SelectParams}.empty)")
      case RepoMethod.SelectAll(_, _, _) =>
        jvm.Body.Expr(code"$ZStream.fromIterable(map.values)")
      case RepoMethod.SelectById(_, _, id, _) =>
        jvm.Body.Expr(code"$ZIO.succeed(map.get(${id.paramName}))")
      case RepoMethod.SelectByIds(_, _, _, idsParam, _) =>
        jvm.Body.Expr(code"$ZStream.fromIterable(${idsParam.name}.flatMap(map.get))")
      case RepoMethod.SelectByIdsTracked(x) =>
        jvm.Body.Expr(code"""|${x.methodName}(${x.idsParam.name}).runCollect.map { rows =>
               |  val byId = rows.view.map(x => (x.${x.idComputed.paramName}, x)).toMap
               |  ${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap
               |}""".stripMargin)
      case RepoMethod.SelectByUnique(_, keyColumns, _, _) =>
        jvm.Body.Expr(code"$ZIO.succeed(map.values.find(v => ${keyColumns.map(c => code"${c.name} == v.${c.name}").mkCode(" && ")}))")

      case RepoMethod.SelectByFieldValues(_, cols, fieldValue, fieldValueOrIdsParam, _) =>
        val cases = cols.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.filter(_.${col.name} == value)"
        }
        jvm.Body.Expr(code"""$ZStream.fromIterable {
              |  ${fieldValueOrIdsParam.name}.foldLeft(map.values) {
              |    ${cases.mkCode("\n")}
              |  }
              |}""".stripMargin)
      case RepoMethod.UpdateBuilder(_, fieldsType, _) =>
        jvm.Body.Expr(code"${lang.dsl.UpdateBuilderMock}(${lang.dsl.UpdateParams}.empty, $fieldsType.structure, map)")
      case RepoMethod.UpdateFieldValues(_, id, varargs, fieldValue, cases0, _) =>
        val cases = cases0.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.copy(${col.name} = value)"
        }

        jvm.Body.Expr(code"""|$ZIO.succeed {
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
               |}""".stripMargin)
      case RepoMethod.Update(_, _, _, param, _) =>
        jvm.Body.Expr(code"""$ZIO.succeed {
              |  map.get(${param.name}.${id.paramName}).map { _ =>
              |    map.put(${param.name}.${id.paramName}, ${param.name}): @${TypesScala.nowarn}
              |    ${param.name}
              |  }
              |}""".stripMargin)
      case RepoMethod.Insert(_, _, _, unsavedParam, _, _) =>
        jvm.Body.Expr(code"""|$ZIO.succeed {
               |  val _ =
               |    if (map.contains(${unsavedParam.name}.${id.paramName}))
               |      sys.error(s"id $${${unsavedParam.name}.${id.paramName}} already exists")
               |    else
               |      map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name})
               |
               |  ${unsavedParam.name}
               |}""")
      case RepoMethod.Upsert(_, _, _, unsavedParam, _, _) =>
        jvm.Body.Expr(code"""|$ZIO.succeed {
               |  map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name}): @${TypesScala.nowarn}
               |  $UpdateResult(1, $Chunk.single(${unsavedParam.name}))
               |}""".stripMargin)
      case RepoMethod.UpsertBatch(_, _, id, _, _) =>
        jvm.Body.Expr(code"""|ZIO.succeed {
               |  unsaved.map{ row =>
               |    map += (row.${id.paramName} -> row)
               |    row
               |  }
               |}""".stripMargin)
      case RepoMethod.UpsertStreaming(_, _, _, _) =>
        jvm.Body.Expr(code"""|unsaved.scanZIO(0L) { case (acc, row) =>
               |  ZIO.succeed {
               |    map += (row.${id.paramName} -> row)
               |    acc + 1
               |  }
               |}.runLast.map(_.getOrElse(0L))""".stripMargin)
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _, _) =>
        jvm.Body.Expr(code"insert(${maybeToRow.get.name}(${unsavedParam.name}))")

      case RepoMethod.DeleteBuilder(_, fieldsType, _) =>
        jvm.Body.Expr(code"${lang.dsl.DeleteBuilderMock}(${lang.dsl.DeleteParams}.empty, $fieldsType.structure, map)")
      case RepoMethod.Delete(_, id) =>
        jvm.Body.Expr(code"$ZIO.succeed(map.remove(${id.paramName}).isDefined)")
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        jvm.Body.Expr(code"$ZIO.succeed(${idsParam.name}.map(id => map.remove(id)).count(_.isDefined).toLong)")
      case RepoMethod.InsertStreaming(_, _, _) =>
        jvm.Body.Expr(code"""|unsaved.scanZIO(0L) { case (acc, row) =>
               |  ZIO.succeed {
               |    map += (row.${id.paramName} -> row)
               |    acc + 1
               |  }
               |}.runLast.map(_.getOrElse(0L))""".stripMargin)
      case RepoMethod.InsertUnsavedStreaming(_, _) =>
        jvm.Body.Expr(code"""|unsaved.scanZIO(0L) { case (acc, unsavedRow) =>
               |  ZIO.succeed {
               |    val row = toRow(unsavedRow)
               |    map += (row.${id.paramName} -> row)
               |    acc + 1
               |  }
               |}.runLast.map(_.getOrElse(0L))""".stripMargin)
      case RepoMethod.SqlFile(_) =>
        // should not happen (tm)
        jvm.Body.Expr(code"???")
    }

  override def testInsertMethod(x: ComputedTestInserts.InsertMethod): jvm.Method =
    jvm.Method(
      Nil,
      comments = jvm.Comments.Empty,
      Nil,
      x.name,
      x.params,
      Nil,
      ZIO.of(ZConnection, Throwable, x.table.names.RowName),
      Nil,
      jvm.Body.Expr(
        code"(new ${x.table.names.RepoImplName}).insert(new ${x.cls}(${x.values.map { case (p, expr) => code"$p = $expr" }.mkCode(", ")}))"
      ),
      isOverride = false,
      isDefault = false
    )

  override val defaultedInstance: List[jvm.Given] =
    textSupport.map(_.defaultedInstance).toList

  override def stringEnumInstances(wrapperType: jvm.Type, underlyingTypoType: TypoType, sqlType: String, openEnum: Boolean): List[jvm.ClassMember] = {
    val underlying = underlyingTypoType.jvmType
    val sqlTypeLit = jvm.StrLit(sqlType)
    val arrayWrapper = jvm.Type.ArrayOf(wrapperType)
    val arraySetter = jvm.Given(
      tparams = Nil,
      name = arraySetterName,
      implicitParams = Nil,
      tpe = Setter.of(arrayWrapper),
      body =
        if (openEnum) code"${lookupSetter(jvm.Type.ArrayOf(underlying))}.contramap(_.map(_.value))"
        else code"""|$Setter.forSqlType[$arrayWrapper](
                    |    (ps, i, v) => ps.setArray(i, ps.getConnection.createArrayOf($sqlTypeLit, v.map(x => x.value))),
                    |    java.sql.Types.ARRAY
                    |  )""".stripMargin
    )
    val arrayJdbcDecoder = jvm.Given(
      tparams = Nil,
      name = arrayJdbcDecoderName,
      implicitParams = Nil,
      tpe = JdbcDecoder.of(arrayWrapper),
      body =
        if (openEnum)
          code"""${lookupJdbcDecoder(jvm.Type.ArrayOf(underlying))}.map(a => if (a == null) null else a.map(apply))"""
        else code"""${lookupJdbcDecoder(jvm.Type.ArrayOf(underlying))}.map(a => if (a == null) null else a.map(force))"""
    )
    val arrayJdbcEncoder = jvm.Given(
      tparams = Nil,
      name = arrayJdbcEncoderName,
      implicitParams = Nil,
      tpe = JdbcEncoder.of(arrayWrapper),
      // JdbcEncoder for unary types defined in terms of `Setter`
      body = code"""$JdbcEncoder.singleParamEncoder(${dialect.usingCall}${arraySetterName})"""
    )
    val jdbcEncoder = jvm.Given(
      tparams = Nil,
      name = jdbcEncoderName,
      implicitParams = Nil,
      tpe = JdbcEncoder.of(wrapperType),
      body = code"""${lookupJdbcEncoder(underlying)}.contramap(_.value)"""
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
      jvm.Given(tparams = Nil, name = jdbcDecoderName, implicitParams = Nil, tpe = JdbcDecoder.of(wrapperType), body = body)
    }

    val setter = jvm.Given(tparams = Nil, name = setterName, implicitParams = Nil, tpe = Setter.of(wrapperType), body = code"""${lookupSetter(underlying)}.contramap(_.value)""")

    val parameterMetadata = {
      val body = code"$PGType.instance[$wrapperType](${sqlTypeLit}, ${TypesJava.SqlTypes}.OTHER)"
      jvm.Given(tparams = Nil, name = pgTypeName, implicitParams = Nil, tpe = PGType.of(wrapperType), body = body)
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

  override def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, underlyingJvmType: jvm.Type, underlyingDbType: db.Type, overrideDbType: Option[String]): List[jvm.ClassMember] =
    List(
      Option(
        jvm.Given(
          tparams = Nil,
          name = jdbcEncoderName,
          implicitParams = Nil,
          tpe = JdbcEncoder.of(wrapperType),
          body = code"""${lookupJdbcEncoder(underlyingJvmType)}.contramap(_.value)"""
        )
      ),
      Option(
        jvm.Given(
          tparams = Nil,
          name = arrayJdbcEncoderName,
          implicitParams = Nil,
          tpe = JdbcEncoder.of(jvm.Type.ArrayOf(wrapperType)),
          body = code"""${lookupJdbcEncoder(jvm.Type.ArrayOf(underlyingJvmType))}.contramap(_.map(_.value))"""
        )
      ),
      Option(
        jvm.Given(
          tparams = Nil,
          name = jdbcDecoderName,
          implicitParams = Nil,
          tpe = JdbcDecoder.of(wrapperType),
          body = code"""${lookupJdbcDecoder(underlyingJvmType)}.map($wrapperType.apply)"""
        )
      ),
      Option(
        jvm.Given(
          tparams = Nil,
          name = arrayJdbcDecoderName,
          implicitParams = Nil,
          tpe = JdbcDecoder.of(jvm.Type.ArrayOf(wrapperType)),
          body = code"""${lookupJdbcDecoder(jvm.Type.ArrayOf(underlyingJvmType))}.map(_.map($wrapperType.apply))"""
        )
      ),
      Option(
        jvm.Given(tparams = Nil, name = setterName, implicitParams = Nil, tpe = Setter.of(wrapperType), body = code"""${lookupSetter(underlyingJvmType)}.contramap(_.value)""")
      ),
      Option(
        jvm.Given(
          tparams = Nil,
          name = arraySetterName,
          implicitParams = Nil,
          tpe = Setter.of(jvm.Type.ArrayOf(wrapperType)),
          body = code"""${lookupSetter(jvm.Type.ArrayOf(underlyingJvmType))}.contramap(_.map(_.value))"""
        )
      ),
      ifDsl(
        jvm.Given(
          tparams = Nil,
          name = pgTypeName,
          implicitParams = Nil,
          tpe = PGType.of(wrapperType),
          body = overrideDbType match {
            case Some(dbType) => code"PGType.instance(${jvm.StrLit(dbType)}, ${TypesJava.SqlTypes}.OTHER)"
            case None         => code"${lookupPgTypeFor(underlyingJvmType)}.as"
          }
        )
      ),
      textSupport.map(_.anyValInstance(wrapperType, underlyingJvmType))
    ).flatten

  override def missingInstances: List[jvm.ClassMember] = {

    /** Adapted from Quill implementation
      *
      * Works for primitive types but not for more complex types
      */
    def primitiveArrayDecoder(T: jvm.Type.Qualified) = {
      val body =
        code"""|new ${JdbcDecoder.of(jvm.Type.ArrayOf(T))} {
               |  override def unsafeDecode(columIndex: ${TypesScala.Int}, rs: ${TypesJava.ResultSet}): (${TypesScala.Int}, ${jvm.Type.ArrayOf(T)}) = {
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
      jvm.Given(tparams = Nil, name = jvm.Ident(s"${T.name.value}ArrayDecoder"), implicitParams = Nil, tpe = JdbcDecoder.of(jvm.Type.ArrayOf(T)), body = body)
    }

    def primitiveArraySetter(T: jvm.Type.Qualified, sqlType: jvm.StrLit, asAnyRef: jvm.Code => jvm.Code) = {
      val v = jvm.Ident("v")
      val body =
        code"""|$Setter.forSqlType[${jvm.Type.ArrayOf(T)}](
               |  (ps, i, $v) => {
               |    ps.setArray(i, ps.getConnection.createArrayOf($sqlType, ${asAnyRef(v)}))
               |  },
               |  ${TypesJava.SqlTypes}.ARRAY
               |)""".stripMargin
      jvm.Given(tparams = Nil, name = jvm.Ident(s"${T.name.value}ArraySetter"), implicitParams = Nil, tpe = Setter.of(jvm.Type.ArrayOf(T)), body = body)
    }

    def primitiveArrayEncoder(T: jvm.Type.Qualified) = {
      jvm.Given(
        tparams = Nil,
        name = jvm.Ident(s"${T.name.value}ArrayEncoder"),
        implicitParams = Nil,
        tpe = JdbcEncoder.of(jvm.Type.ArrayOf(T)),
        body = code"""$JdbcEncoder.singleParamEncoder(${dialect.usingCall}${T.name.value}ArraySetter)"""
      )
    }

    def ScalaBigDecimal =
      List(
        jvm.Given(
          tparams = Nil,
          name = jvm.Ident("ScalaBigDecimalArrayEncoder"),
          implicitParams = Nil,
          tpe = JdbcEncoder.of(jvm.Type.ArrayOf(TypesScala.BigDecimal)),
          body = code"""BigDecimalArrayEncoder.contramap(_.map(_.bigDecimal))"""
        ),
        jvm.Given(
          tparams = Nil,
          name = jvm.Ident("ScalaBigDecimalArrayDecoder"),
          implicitParams = Nil,
          tpe = JdbcDecoder.of(jvm.Type.ArrayOf(TypesScala.BigDecimal)),
          body = code"""BigDecimalArrayDecoder.map(v => if (v eq null) null else v.map(${TypesScala.BigDecimal}.apply))"""
        ),
        jvm.Given(
          tparams = Nil,
          name = jvm.Ident("ScalaBigDecimalArraySetter"),
          implicitParams = Nil,
          tpe = Setter.of(jvm.Type.ArrayOf(TypesScala.BigDecimal)),
          body = code"""BigDecimalArraySetter.contramap(_.map(_.bigDecimal))"""
        )
      )

    def all(T: jvm.Type.Qualified, sqlType: String, asAnyRef: jvm.Code => jvm.Code) = {
      List(primitiveArrayDecoder(T), primitiveArraySetter(T, jvm.StrLit(sqlType), asAnyRef), primitiveArrayEncoder(T))
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

  /** Oracle STRUCT types - not supported in PostgreSQL */
  override def structInstances(computed: ComputedOracleObjectType): List[jvm.ClassMember] = Nil

  /** Oracle COLLECTION types - not supported in PostgreSQL */
  override def collectionInstances(computed: ComputedOracleCollectionType): List[jvm.ClassMember] = Nil

  override def rowInstances(tpe: jvm.Type, cols: NonEmptyList[ComputedColumn], rowType: DbLib.RowType): List[jvm.ClassMember] = {
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
      jvm.Given(tparams = Nil, name = jdbcDecoderName, implicitParams = Nil, tpe = JdbcDecoder.of(tpe), body = body)
    }
    rowType match {
      case DbLib.RowType.Writable      => text.toList
      case DbLib.RowType.ReadWriteable => List(decoder) ++ text
      case DbLib.RowType.Readable      => List(decoder)
    }
  }

  override def customTypeInstances(ct: CustomType): List[jvm.ClassMember] =
    customTypeOne(ct) ++ (if (ct.forbidArray) Nil else customTypeArray(ct))

  def customTypeOne(ct: CustomType): List[jvm.Given] = {

    val jdbcEncoder = jvm.Given(
      tparams = Nil,
      name = jdbcEncoderName,
      implicitParams = Nil,
      tpe = JdbcEncoder.of(ct.typoType),
      // JdbcEncoder for unary types defined in terms of `Setter`
      body = code"""$JdbcEncoder.singleParamEncoder(${dialect.usingCall}$setterName)"""
    )

    val jdbcDecoder = {
      val expectedType = jvm.StrLit(ct.fromTypo.jdbcType.render(lang).asString)
      val body =
        code"""|${JdbcDecoder.of(ct.typoType)}(
               |  (rs: ${TypesJava.ResultSet}) => (i: ${TypesScala.Int}) => {
               |    val v = rs.getObject(i)
               |    if (v eq null) null else ${ct.toTypo0(code"v.asInstanceOf[${ct.toTypo.jdbcType}]")}
               |  },
               |  $expectedType
               |)""".stripMargin
      jvm.Given(tparams = Nil, name = jdbcDecoderName, implicitParams = Nil, tpe = JdbcDecoder.of(ct.typoType), body = body)
    }

    val setter = {
      val v = jvm.Ident("v")
      val body =
        code"""|$Setter.other(
               |  (ps, i, $v) => {
               |    ps.setObject(
               |      i,
               |      ${ct.fromTypo0(v)}
               |    )
               |  },
               |  ${jvm.StrLit(ct.sqlType)}
               |)""".stripMargin
      jvm.Given(tparams = Nil, name = setterName, implicitParams = Nil, tpe = Setter.of(ct.typoType), body = body)
    }

    val pgType = {
      val body = code"$PGType.instance[${ct.typoType}](${jvm.StrLit(ct.sqlType)}, ${TypesJava.SqlTypes}.OTHER)"
      jvm.Given(Nil, pgTypeName, Nil, PGType.of(ct.typoType), body)
    }
    val text = textSupport.map(_.customTypeInstance(ct))

    List(Option(jdbcEncoder), Option(jdbcDecoder), Option(setter), ifDsl(pgType), text).flatten
  }

  def customTypeArray(ct: CustomType): List[jvm.Given] = {
    val fromTypo = ct.fromTypoInArray.getOrElse(ct.fromTypo)
    val toTypo = ct.toTypoInArray.getOrElse(ct.toTypo)
    val jdbcEncoder = jvm.Given(
      tparams = Nil,
      name = arrayJdbcEncoderName,
      implicitParams = Nil,
      tpe = JdbcEncoder.of(jvm.Type.ArrayOf(ct.typoType)),
      // JdbcEncoder for unary types defined in terms of `Setter`
      body = code"""$JdbcEncoder.singleParamEncoder(${dialect.usingCall}${arraySetterName})"""
    )

    val jdbcDecoder = {
      val expectedType = jvm.StrLit(jvm.Type.ArrayOf(ct.fromTypo.jdbcType).render(lang).asString)
      val body =
        code"""|${JdbcDecoder.of(jvm.Type.ArrayOf(ct.typoType))}((rs: ${TypesJava.ResultSet}) => (i: ${TypesScala.Int}) =>
               |  rs.getArray(i) match {
               |    case null => null
               |    case arr => arr.getArray.asInstanceOf[Array[AnyRef]].map(x => ${toTypo.toTypo(code"x.asInstanceOf[${toTypo.jdbcType}]", ct.typoType)})
               |  },
               |  $expectedType
               |)""".stripMargin
      jvm.Given(tparams = Nil, name = arrayJdbcDecoderName, implicitParams = Nil, tpe = JdbcDecoder.of(jvm.Type.ArrayOf(ct.typoType)), body = body)
    }

    val setter = {
      val v = jvm.Ident("v")
      val vv = jvm.Ident("vv")
      val body =
        code"""|$Setter.forSqlType((ps, i, $v) =>
               |  ps.setArray(
               |    i,
               |    ps.getConnection.createArrayOf(
               |      ${jvm.StrLit(ct.sqlType)},
               |      $v.map { $vv =>
               |        ${fromTypo.fromTypo0(vv)}
               |      }
               |    )
               |  ),
               |  ${TypesJava.SqlTypes}.ARRAY
               |)""".stripMargin

      jvm.Given(tparams = Nil, name = arraySetterName, implicitParams = Nil, tpe = Setter.of(jvm.Type.ArrayOf(ct.typoType)), body = body)
    }

    List(jdbcEncoder, jdbcDecoder, setter)
  }
}
