package typr
package internal
package codegen

import typr.internal.analysis.MaybeReturnsRows

class DbLibDoobie(pkg: jvm.QIdent, inlineImplicits: Boolean, default: ComputedDefault, enableStreamingInserts: Boolean, fixVerySlowImplicit: Boolean, override val lang: LangScala) extends DbLib {
  val dialect = lang.dialect
  private val sqlCast = new SqlCast(needsTimestampCasts = true)

  val SqlInterpolator = jvm.Type.Qualified("doobie.syntax.string.toSqlInterpolator")
  def SQL(content: jvm.Code) =
    jvm.StringInterpolate(SqlInterpolator, jvm.Ident("sql"), content)
  def frInterpolate(content: jvm.Code) =
    jvm.StringInterpolate(SqlInterpolator, jvm.Ident("fr"), content)
  val Meta = jvm.Type.Qualified("doobie.util.meta.Meta")
  val Put = jvm.Type.Qualified("doobie.util.Put")
  val Get = jvm.Type.Qualified("doobie.util.Get")
  val Write = jvm.Type.Qualified("doobie.util.Write")
  val Read = jvm.Type.Qualified("doobie.util.Read")
  val ConnectionIO = jvm.Type.Qualified("doobie.free.connection.ConnectionIO")
  val Nullability = jvm.Type.Qualified("doobie.enumerated.Nullability")
  val Fragments = jvm.Type.Qualified("doobie.util.fragments")
  val Fragment = jvm.Type.Qualified("doobie.util.fragment.Fragment")
  val pureCIO = jvm.Type.Qualified("doobie.free.connection.pure")
  val delayCIO = jvm.Type.Qualified("doobie.free.connection.delay")
  val fs2Stream = jvm.Type.Qualified("fs2.Stream")
  val NonEmptyList = jvm.Type.Qualified("cats.data.NonEmptyList")
  val fromWrite = jvm.Type.Qualified("doobie.syntax.SqlInterpolator.SingleFragment.fromWrite")
  val FragmentOps = jvm.Type.Qualified("doobie.postgres.syntax.FragmentOps")
  val JdbcType = jvm.Type.Qualified("doobie.enumerated.JdbcType")
  val Update = jvm.Type.Qualified("doobie.util.update.Update")
  val catsStdInstancesForList = jvm.Type.Qualified("cats.instances.list.catsStdInstancesForList")

  val arrayGetName: jvm.Ident = jvm.Ident("arrayGet")
  val arrayPutName: jvm.Ident = jvm.Ident("arrayPut")
  val getName: jvm.Ident = jvm.Ident("get")
  val putName: jvm.Ident = jvm.Ident("put")
  val readName: jvm.Ident = jvm.Ident("read")
  val writeName = jvm.Ident("write")

  val textSupport: Option[DbLibTextSupport] =
    if (enableStreamingInserts) Some(new DbLibTextSupport(pkg, inlineImplicits, Some(jvm.Type.Qualified("doobie.postgres.Text")), default, lang)) else None

  def dbNames(cols: NonEmptyList[ComputedColumn], isRead: Boolean): jvm.Code =
    cols
      .map(c => c.dbName.code ++ (if (isRead) sqlCast.fromPgCode(c) else jvm.Code.Empty))
      .mkCode(", ")

  def runtimeInterpolateValue(name: jvm.Code, tpe: jvm.Type): jvm.Code =
    if (inlineImplicits)
      tpe match {
        case lang.Optional(underlying) =>
          code"$${$fromWrite($name)(${dialect.usingCall}new $Write.SingleOpt(${lookupPutFor(underlying)}))}"
        case other =>
          code"$${$fromWrite($name)(${dialect.usingCall}new $Write.Single(${lookupPutFor(other)}))}"
      }
    else code"$${$name}"

  def matchId(id: IdComputed): jvm.Code =
    id match {
      case id: IdComputed.Unary =>
        code"${id.col.dbName.code} = ${runtimeInterpolateValue(id.paramName, id.tpe)}"
      case composite: IdComputed.Composite =>
        code"${composite.cols.map(cc => code"${cc.dbName.code} = ${runtimeInterpolateValue(code"${composite.paramName}.${cc.name}", cc.tpe)}").mkCode(" AND ")}"
    }
  override def resolveConstAs(typoType: TypoType): jvm.Code = {
    val tpe = typoType.jvmType
    val put = lookupPutFor(tpe)
    typoType match {
      case TypoType.Nullable(_, inner) =>
        code"${lang.dsl.ConstAsAsOpt}[${inner.jvmType}](${dialect.usingCall}$put)"
      case _ =>
        code"${lang.dsl.ConstAsAs}[$tpe](${dialect.usingCall}$put)"
    }
  }

  val batchSize = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("batchSize"), TypesScala.Int, Some(code"10000"))

  override def repoSig(repoMethod: RepoMethod): Right[Nothing, jvm.Method] = {
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
        sig(params = Nil, returnType = fs2Stream.of(ConnectionIO, rowType))
      case RepoMethod.SelectById(_, _, id, rowType) =>
        sig(params = List(id.param), returnType = ConnectionIO.of(TypesScala.Option.of(rowType)))
      case RepoMethod.SelectByIds(_, _, _, idsParam, rowType) =>
        sig(params = List(idsParam), returnType = fs2Stream.of(ConnectionIO, rowType))
      case RepoMethod.SelectByIdsTracked(x) =>
        sig(params = List(x.idsParam), returnType = ConnectionIO.of(TypesScala.Map.of(x.idComputed.tpe, x.rowType)))
      case RepoMethod.SelectByUnique(_, keyColumns, _, rowType) =>
        sig(params = keyColumns.toList.map(_.param), returnType = ConnectionIO.of(TypesScala.Option.of(rowType)))
      case RepoMethod.SelectByFieldValues(_, _, _, fieldValueOrIdsParam, rowType) =>
        sig(params = List(fieldValueOrIdsParam), returnType = fs2Stream.of(ConnectionIO, rowType))
      case RepoMethod.UpdateBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, returnType = lang.dsl.UpdateBuilder.of(fieldsType, rowType))
      case RepoMethod.UpdateFieldValues(_, id, varargs, _, _, _) =>
        sig(params = List(id.param, varargs), returnType = ConnectionIO.of(TypesScala.Boolean))
      case RepoMethod.Update(_, _, _, param, _) =>
        sig(params = List(param), returnType = ConnectionIO.of(TypesScala.Option.of(param.tpe)))
      case RepoMethod.Insert(_, _, _, unsavedParam, _, returningStrategy) =>
        sig(params = List(unsavedParam), returnType = ConnectionIO.of(returningStrategy.returnType))
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _, returningStrategy) =>
        sig(params = List(unsavedParam), returnType = ConnectionIO.of(returningStrategy.returnType))
      case RepoMethod.InsertStreaming(_, rowType, _) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), fs2Stream.of(ConnectionIO, rowType))
        sig(params = List(unsavedParam, batchSize), returnType = ConnectionIO.of(TypesScala.Long))
      case RepoMethod.UpsertBatch(_, _, _, rowType, _, _) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), TypesScala.List.of(rowType))
        sig(params = List(unsavedParam), returnType = fs2Stream.of(ConnectionIO, rowType))
      case RepoMethod.InsertUnsavedStreaming(_, unsaved) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), fs2Stream.of(ConnectionIO, unsaved.tpe))
        sig(params = List(unsavedParam, batchSize), returnType = ConnectionIO.of(TypesScala.Long))
      case RepoMethod.Upsert(_, _, _, unsavedParam, rowType, _, _) =>
        sig(params = List(unsavedParam), returnType = ConnectionIO.of(rowType))
      case RepoMethod.UpsertStreaming(_, _, rowType, _) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), fs2Stream.of(ConnectionIO, rowType))
        sig(params = List(unsavedParam, batchSize), returnType = ConnectionIO.of(TypesScala.Int))
      case RepoMethod.DeleteBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, returnType = lang.dsl.DeleteBuilder.of(fieldsType, rowType))
      case RepoMethod.Delete(_, id) =>
        sig(params = List(id.param), returnType = ConnectionIO.of(TypesScala.Boolean))
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        sig(params = List(idsParam), returnType = ConnectionIO.of(TypesScala.Int))

      case RepoMethod.SqlFile(sqlScript) =>
        val params = sqlScript.params.map(p => jvm.Param(p.name, p.tpe))

        val retType = sqlScript.maybeRowName match {
          case MaybeReturnsRows.Query(rowName) => fs2Stream.of(ConnectionIO, rowName)
          case MaybeReturnsRows.Update         => ConnectionIO.of(TypesScala.Int)
        }

        sig(params = params, returnType = retType)
    }
  }

  def query(sql: jvm.Code, rowType: jvm.Type): jvm.Code =
    if (fixVerySlowImplicit) code"$sql.query(${dialect.usingCall}$rowType.$readName)"
    else code"$sql.query[$rowType]"

  override def repoImpl(repoMethod: RepoMethod): jvm.Body =
    repoMethod match {
      case RepoMethod.SelectBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"${lang.dsl.SelectBuilder}.of(${jvm.StrLit(relName.quotedValue)}, $fieldsType.structure, $rowType.read)")

      case RepoMethod.SelectAll(relName, cols, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        val sql = SQL(code"""select $joinedColNames from $relName""")
        jvm.Body.Expr(code"${query(sql, rowType)}.stream")

      case RepoMethod.SelectById(relName, cols, id, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        val sql = SQL(code"""select $joinedColNames from $relName where ${matchId(id)}""")
        jvm.Body.Expr(code"${query(sql, rowType)}.option")

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
            jvm.Body.Stmts(vals.toList ++ List(code"${query(sql, rowType)}.stream"))

          case unaryId: IdComputed.Unary =>
            val sql = SQL(
              code"""select $joinedColNames from $relName where ${code"${unaryId.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, idsParam.tpe)})"}"""
            )
            jvm.Body.Expr(code"""${query(sql, rowType)}.stream""")
        }
      case RepoMethod.SelectByIdsTracked(x) =>
        jvm.Body.Expr(
          code"""|${x.methodName}(${x.idsParam.name}).compile.toList.map { rows =>
                 |  val byId = rows.view.map(x => (x.${x.idComputed.paramName}, x)).toMap
                 |  ${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap
                 |}""".stripMargin
        )

      case RepoMethod.SelectByUnique(relName, keyColumns, allColumns, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(allColumns, isRead = true)}
                 |from $relName
                 |where ${keyColumns.map(c => code"${c.dbName.code} = ${runtimeInterpolateValue(c.name, c.tpe)}").mkCode(" AND ")}
                 |""".stripMargin
        }
        jvm.Body.Expr(code"""${query(sql, rowType)}.option""")

      case RepoMethod.SelectByFieldValues(relName, cols, fieldValue, fieldValueOrIdsParam, rowType) =>
        val cases: NonEmptyList[jvm.Code] =
          cols.map { col =>
            val fr = frInterpolate(code"${col.dbName.code} = ${runtimeInterpolateValue(jvm.Ident("value"), col.tpe)}")
            code"case $fieldValue.${col.name}(value) => $fr"
          }

        val sql = SQL(code"""select ${dbNames(cols, isRead = true)} from $relName $$where""")
        jvm.Body.Stmts(
          List(
            code"""val where = $Fragments.whereAndOpt(
                  |  ${fieldValueOrIdsParam.name}.map {
                  |    ${cases.mkCode("\n")}
                  |  }
                  |)""".stripMargin,
            code"${query(sql, rowType)}.stream"
          )
        )

      case RepoMethod.UpdateFieldValues(relName, id, varargs, fieldValue, cases0, _) =>
        val cases: NonEmptyList[jvm.Code] =
          cases0.map { col =>
            val fr = frInterpolate(code"${col.dbName.code} = ${runtimeInterpolateValue(jvm.Ident("value"), col.tpe)}${sqlCast.toPgCode(col)}")
            code"case $fieldValue.${col.name}(value) => $fr"
          }

        val sql = SQL {
          code"""|update $relName
                 |$$updates
                 |where ${matchId(id)}""".stripMargin
        }

        jvm.Body.Expr(
          code"""$NonEmptyList.fromList(${varargs.name}) match {
                |  case None => $pureCIO(false)
                |  case Some(nonEmpty) =>
                |    val updates = $Fragments.set(
                |      nonEmpty.map {
                |        ${cases.mkCode("\n")}
                |      }
                |    )
                |    $sql.update.run.map(_ > 0)
                |}""".stripMargin
        )

      case RepoMethod.UpdateBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"${lang.dsl.UpdateBuilder}.of(${jvm.StrLit(relName.quotedValue)}, $fieldsType.structure, $rowType.read)")

      case RepoMethod.Update(relName, cols, id, param, writeableCols) =>
        val sql = SQL(
          code"""update $relName
                |set ${writeableCols.map { col => code"${col.dbName.code} = ${runtimeInterpolateValue(code"${param.name}.${col.name}", col.tpe)}${sqlCast.toPgCode(col)}" }.mkCode(",\n")}
                |where ${matchId(id)}
                |returning ${dbNames(cols, isRead = true)}""".stripMargin
        )
        jvm.Body.Stmts(
          List(
            code"val ${id.paramName} = ${param.name}.${id.paramName}",
            code"""|${query(sql, param.tpe)}.option""".stripMargin
          )
        )

      case RepoMethod.InsertUnsaved(relName, cols, unsaved, unsavedParam, _, default, returningStrategy) =>
        val rowType = returningStrategy.returnType
        val cases0 = unsaved.normalColumns.map { col =>
          val set = frInterpolate(code"${runtimeInterpolateValue(code"${unsavedParam.name}.${col.name}", col.tpe)}${sqlCast.toPgCode(col)}")
          code"""Some(($Fragment.const0(${lang.s(col.dbName.code)}), $set))"""
        }
        val cases1 = unsaved.defaultedCols.map { case ComputedRowUnsaved.DefaultedCol(col @ ComputedColumn(_, ident, _, _), origType, _) =>
          val setValue = frInterpolate(code"${runtimeInterpolateValue(code"value: $origType", origType)}${sqlCast.toPgCode(col)}")
          code"""|${unsavedParam.name}.$ident match {
                 |  case ${default.Defaulted}.${default.UseDefault}() => None
                 |  case ${default.Defaulted}.${default.Provided}(value) => Some(($Fragment.const0(${lang.s(col.dbName.code)}), $setValue))
                 |}""".stripMargin
        }

        val sql = SQL {
          code"""|insert into $relName($${CommaSeparate.combineAllOption(fs.map { case (n, _) => n }).get})
                 |values ($${CommaSeparate.combineAllOption(fs.map { case (_, f) => f }).get})
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
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
                   |  val CommaSeparate = $Fragment.FragmentMonoid.intercalate(fr", ")
                   |  $sql
                   |}""".stripMargin,
            code"${query(jvm.Ident("q"), rowType)}.unique"
          )
        )

      case RepoMethod.InsertStreaming(relName, rowType, writeableColumnsWithId) =>
        val sql = SQL(code"COPY $relName(${dbNames(writeableColumnsWithId, isRead = false)}) FROM STDIN")

        jvm.Body.Expr(
          if (fixVerySlowImplicit) code"new $FragmentOps($sql).copyIn(unsaved, batchSize)(${dialect.usingCall}${textSupport.get.lookupTextFor(rowType)})"
          else code"new $FragmentOps($sql).copyIn[$rowType](unsaved, batchSize)"
        )

      case RepoMethod.InsertUnsavedStreaming(relName, unsaved) =>
        val sql = SQL(code"COPY $relName(${dbNames(unsaved.unsavedCols, isRead = false)}) FROM STDIN (DEFAULT '${DbLibTextSupport.DefaultValue}')")

        jvm.Body.Expr(
          if (fixVerySlowImplicit) code"new $FragmentOps($sql).copyIn(unsaved, batchSize)(${dialect.usingCall}${textSupport.get.lookupTextFor(unsaved.tpe)})"
          else code"new $FragmentOps($sql).copyIn[${unsaved.tpe}](unsaved, batchSize)"
        )

      case RepoMethod.Upsert(relName, cols, id, unsavedParam, rowType, writeableColumnsWithId, _) =>
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
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }

        jvm.Body.Expr(code"${query(sql, rowType)}.unique")

      case RepoMethod.UpsertBatch(relName, cols, id, rowType, writeableColumnsWithId, _) =>
        val writeableColumnsNotId = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))

        val conflictAction = writeableColumnsNotId match {
          case Nil => code"do nothing"
          case nonEmpty =>
            code"""|do update set
                   |  ${nonEmpty.map { c => code"${c.dbName.code} = EXCLUDED.${c.dbName.code}" }.mkCode(",\n")}""".stripMargin
        }

        val sql = lang.s {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${writeableColumnsWithId.map(c => code"?${sqlCast.toPgCode(c)}").mkCode(code",")})
                 |on conflict (${dbNames(id.cols, isRead = false)})
                 |$conflictAction
                 |returning ${dbNames(cols, isRead = true)}""".stripMargin
        }
        jvm.Body.Expr(
          if (fixVerySlowImplicit)
            code"""|${Update.of(rowType)}(
                   |  $sql
                 |)(${dialect.usingCall}$rowType.$writeName)
                 |.updateManyWithGeneratedKeys[$rowType](${dbNames(cols, isRead = false)})(unsaved)(${dialect.usingCall}$catsStdInstancesForList, $rowType.$readName)""".stripMargin
          else
            code"""|${Update.of(rowType)}(
                   |  $sql
                   |).updateManyWithGeneratedKeys[$rowType](${dbNames(cols, isRead = false)})(unsaved)""".stripMargin
        )
      case RepoMethod.UpsertStreaming(relName, id, rowType, writeableColumnsWithId) =>
        val writeableColumnsNotId = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))

        val conflictAction = writeableColumnsNotId match {
          case Nil => code"do nothing"
          case nonEmpty =>
            code"""|do update set
                   |  ${nonEmpty.map { c => code"${c.dbName.code} = EXCLUDED.${c.dbName.code}" }.mkCode(",\n")}""".stripMargin
        }
        val tempTablename = s"${relName.name}_TEMP"

        val streamingInsert = {
          val sql = SQL(code"copy $tempTablename(${dbNames(writeableColumnsWithId, isRead = false)}) from stdin")
          if (fixVerySlowImplicit) code"new $FragmentOps($sql).copyIn(unsaved, batchSize)(${dialect.usingCall}${textSupport.get.lookupTextFor(rowType)})"
          else code"new $FragmentOps($sql).copyIn[$rowType](unsaved, batchSize)"
        }

        val mergeSql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |select * from $tempTablename
                 |on conflict (${dbNames(id.cols, isRead = false)})
                 |$conflictAction
                 |;
                 |drop table $tempTablename;""".stripMargin
        }
        jvm.Body.Expr(
          code"""|for {
                 |  _ <- ${SQL(code"create temporary table $tempTablename (like $relName) on commit drop")}.update.run
                 |  _ <- $streamingInsert
                 |  res <- $mergeSql.update.run
                 |} yield res""".stripMargin
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

        jvm.Body.Expr(code"${query(sql, rowType)}.unique")

      case RepoMethod.DeleteBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"${lang.dsl.DeleteBuilder}.of(${jvm.StrLit(relName.quotedValue)}, $fieldsType.structure, $rowType.read)")
      case RepoMethod.Delete(relName, id) =>
        val sql = SQL(code"""delete from $relName where ${matchId(id)}""")
        jvm.Body.Expr(code"$sql.update.run.map(_ > 0)")
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
            jvm.Body.Stmts(vals.toList ++ List(code"$sql.update.run"))

          case x: IdComputed.Unary =>
            val sql = SQL(
              code"""delete from $relName where ${code"${x.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, jvm.Type.ArrayOf(x.tpe))})"}"""
            )
            jvm.Body.Expr(code"$sql.update.run")
        }

      case RepoMethod.SqlFile(sqlScript) =>
        val renderedScript: jvm.Code = sqlScript.sqlFile.decomposedSql.renderCode { (paramAtIndex: Int) =>
          val param = sqlScript.params.find(_.indices.contains(paramAtIndex)).get
          val cast = sqlCast.toPg(param).fold("")(_.withColons)
          code"${runtimeInterpolateValue(param.name, param.tpe)}$cast"
        }
        val ret = for {
          cols <- sqlScript.maybeCols.toOption
          rowType <- sqlScript.maybeRowName.toOption
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
              code"${query(jvm.Ident("sql"), rowType)}.stream"
            )
          )
        }
        ret.getOrElse(jvm.Body.Expr(code"${SQL(renderedScript)}.update.run"))
    }

  override def mockRepoImpl(id: IdComputed, repoMethod: RepoMethod, maybeToRow: Option[jvm.Param[jvm.Type.Function1]]): jvm.Body =
    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, _) =>
        jvm.Body.Expr(code"${lang.dsl.SelectBuilderMock}($fieldsType.structure, $delayCIO(map.values.toList), ${lang.dsl.SelectParams}.empty)")
      case RepoMethod.SelectAll(_, _, _) =>
        jvm.Body.Expr(code"$fs2Stream.emits(map.values.toList)")
      case RepoMethod.SelectById(_, _, id, _) =>
        jvm.Body.Expr(code"$delayCIO(map.get(${id.paramName}))")
      case RepoMethod.SelectByIds(_, _, _, idsParam, _) =>
        jvm.Body.Expr(code"$fs2Stream.emits(${idsParam.name}.flatMap(map.get).toList)")
      case RepoMethod.SelectByIdsTracked(x) =>
        jvm.Body.Expr(code"""|${x.methodName}(${x.idsParam.name}).compile.toList.map { rows =>
               |  val byId = rows.view.map(x => (x.${x.idComputed.paramName}, x)).toMap
               |  ${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap
               |}""".stripMargin)
      case RepoMethod.SelectByUnique(_, keyColumns, _, _) =>
        jvm.Body.Expr(code"$delayCIO(map.values.find(v => ${keyColumns.map(c => code"${c.name} == v.${c.name}").mkCode(" && ")}))")
      case RepoMethod.SelectByFieldValues(_, cols, fieldValue, fieldValueOrIdsParam, _) =>
        val cases = cols.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.filter(_.${col.name} == value)"
        }
        jvm.Body.Expr(code"""$fs2Stream.emits {
              |  ${fieldValueOrIdsParam.name}.foldLeft(map.values) {
              |    ${cases.mkCode("\n")}
              |  }.toList
              |}""".stripMargin)
      case RepoMethod.UpdateBuilder(_, fieldsType, _) =>
        jvm.Body.Expr(code"${lang.dsl.UpdateBuilderMock}(${lang.dsl.UpdateParams}.empty, $fieldsType.structure, map)")
      case RepoMethod.UpdateFieldValues(_, id, varargs, fieldValue, cases0, _) =>
        val cases = cases0.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.copy(${col.name} = value)"
        }

        jvm.Body.Expr(code"""|$delayCIO {
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
        jvm.Body.Expr(code"""$delayCIO {
              |  map.get(${param.name}.${id.paramName}).map { _ =>
              |    map.put(${param.name}.${id.paramName}, ${param.name}): @${TypesScala.nowarn}
              |    ${param.name}
              |  }
              |}""".stripMargin)
      case RepoMethod.Insert(_, _, _, unsavedParam, _, _) =>
        jvm.Body.Expr(code"""|$delayCIO {
               |  val _ = if (map.contains(${unsavedParam.name}.${id.paramName}))
               |    sys.error(s"id $${${unsavedParam.name}.${id.paramName}} already exists")
               |  else
               |    map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name})
               |
               |  ${unsavedParam.name}
               |}""")
      case RepoMethod.Upsert(_, _, _, unsavedParam, _, _, _) =>
        jvm.Body.Expr(code"""|$delayCIO {
               |  map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name}): @${TypesScala.nowarn}
               |  ${unsavedParam.name}
               |}""".stripMargin)
      case RepoMethod.UpsertStreaming(_, _, _, _) =>
        jvm.Body.Expr(code"""|unsaved.compile.toList.map { rows =>
               |  var num = 0
               |  rows.foreach { row =>
               |    map += (row.${id.paramName} -> row)
               |    num += 1
               |  }
               |  num
               |}""".stripMargin)
      case RepoMethod.UpsertBatch(_, _, _, _, _, _) =>
        jvm.Body.Expr(code"""|$fs2Stream.emits {
               |  unsaved.map { row =>
               |    map += (row.${id.paramName} -> row)
               |    row
               |  }
               |}""".stripMargin)
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _, _) =>
        jvm.Body.Expr(code"insert(${maybeToRow.get.name}(${unsavedParam.name}))")
      case RepoMethod.InsertStreaming(_, _, _) =>
        jvm.Body.Expr(code"""|unsaved.compile.toList.map { rows =>
               |  var num = 0L
               |  rows.foreach { row =>
               |    map += (row.${id.paramName} -> row)
               |    num += 1
               |  }
               |  num
               |}""".stripMargin)
      case RepoMethod.InsertUnsavedStreaming(_, _) =>
        jvm.Body.Expr(code"""|unsaved.compile.toList.map { unsavedRows =>
               |  var num = 0L
               |  unsavedRows.foreach { unsavedRow =>
               |    val row = ${maybeToRow.get.name}(unsavedRow)
               |    map += (row.${id.paramName} -> row)
               |    num += 1
               |  }
               |  num
               |}""".stripMargin)

      case RepoMethod.DeleteBuilder(_, fieldsType, _) =>
        jvm.Body.Expr(code"${lang.dsl.DeleteBuilderMock}(${lang.dsl.DeleteParams}.empty, $fieldsType.structure, map)")
      case RepoMethod.Delete(_, id) =>
        jvm.Body.Expr(code"$delayCIO(map.remove(${id.paramName}).isDefined)")
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        jvm.Body.Expr(code"$delayCIO(${idsParam.name}.map(id => map.remove(id)).count(_.isDefined))")
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
      ConnectionIO.of(x.table.names.RowName),
      Nil,
      jvm.Body.Expr(code"(new ${x.table.names.RepoImplName}).insert(new ${x.cls}(${x.values.map { case (p, expr) => code"$p = $expr" }.mkCode(", ")}))"),
      isOverride = false,
      isDefault = false
    )

  override val defaultedInstance: List[jvm.Given] =
    textSupport.map(_.defaultedInstance).toList

  override def stringEnumInstances(wrapperType: jvm.Type, underlyingTypoType: TypoType, sqlType: String, openEnum: Boolean): List[jvm.Given] = {
    val underlying = underlyingTypoType.jvmType
    val sqlTypeLit = jvm.StrLit(sqlType)
    val sqlArrayTypeLit = jvm.StrLit(sqlType + "[]")
    List(
      Some(
        jvm.Given(
          tparams = Nil,
          name = putName,
          implicitParams = Nil,
          tpe = Put.of(wrapperType),
          body =
            if (openEnum) code"${lookupPutFor(underlying)}.contramap(_.value)"
            else code"$Put.Advanced.one[$wrapperType]($JdbcType.Other, $NonEmptyList.one($sqlTypeLit), (ps, i, a) => ps.setString(i, a.value), (rs, i, a) => rs.updateString(i, a.value))"
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = arrayPutName,
          implicitParams = Nil,
          tpe = Put.of(jvm.Type.ArrayOf(wrapperType)),
          body =
            if (openEnum) code"${lookupPutFor(jvm.Type.ArrayOf(underlying))}.contramap(_.map(_.value))"
            else code"$Put.Advanced.array[${TypesScala.AnyRef}]($NonEmptyList.one($sqlArrayTypeLit), $sqlTypeLit).contramap(_.map(_.value))"
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = getName,
          implicitParams = Nil,
          tpe = Get.of(wrapperType),
          body =
            if (openEnum) code"""${lookupGetFor(underlying)}.map($wrapperType.apply)"""
            else code"""${lookupGetFor(underlying)}.temap($wrapperType.apply)"""
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = arrayGetName,
          implicitParams = Nil,
          tpe = Get.of(jvm.Type.ArrayOf(wrapperType)),
          body = {
            val get = lookupGetFor(jvm.Type.ArrayOf(underlying))
            if (openEnum) code"""$get.map(_.map($wrapperType.apply))"""
            else code"$get.map(_.map(force))"
          }
        )
      ),
      Some(
        jvm.Given(tparams = Nil, name = writeName, implicitParams = Nil, tpe = Write.of(wrapperType), body = code"new $Write.Single($putName)")
      ),
      Some(
        jvm.Given(tparams = Nil, name = readName, implicitParams = Nil, tpe = Read.of(wrapperType), body = code"new $Read.Single($getName)")
      ),
      textSupport.map(_.anyValInstance(wrapperType, underlying))
    ).flatten
  }

  override def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, underlyingJvmType: jvm.Type, underlyingDbType: db.Type, overrideDbType: Option[String]): List[jvm.Given] =
    List(
      Some(
        jvm.Given(tparams = Nil, name = putName, implicitParams = Nil, tpe = Put.of(wrapperType), body = code"${lookupPutFor(underlyingJvmType)}.contramap(_.value)")
      ),
      Some(
        jvm.Given(tparams = Nil, name = getName, implicitParams = Nil, tpe = Get.of(wrapperType), body = code"${lookupGetFor(underlyingJvmType)}.map($wrapperType.apply)")
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = arrayPutName,
          implicitParams = Nil,
          tpe = Put.of(jvm.Type.ArrayOf(wrapperType)),
          body = code"${lookupPutFor(jvm.Type.ArrayOf(underlyingJvmType))}.contramap(_.map(_.value))"
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = arrayGetName,
          implicitParams = Nil,
          tpe = Get.of(jvm.Type.ArrayOf(wrapperType)),
          body = code"${lookupGetFor(jvm.Type.ArrayOf(underlyingJvmType))}.map(_.map($wrapperType.apply))"
        )
      ),
      textSupport.map(_.anyValInstance(wrapperType, underlyingJvmType))
    ).flatten

  override val missingInstances: List[jvm.ClassMember] = {
    def i(name: String, metaType: jvm.Type, qident: String) =
      jvm.Given(Nil, jvm.Ident(name), Nil, Meta.of(metaType), jvm.QIdent(qident))

    List(
      i("UUIDMeta", TypesJava.UUID, "doobie.postgres.implicits.UuidType"),
      i("UUIDArrayMeta", jvm.Type.ArrayOf(TypesJava.UUID), "doobie.postgres.implicits.unliftedUUIDArrayType"),
      i("StringArrayMeta", jvm.Type.ArrayOf(TypesJava.String), "doobie.postgres.implicits.unliftedStringArrayType"),
      i("BooleanArrayMeta", jvm.Type.ArrayOf(TypesScala.Boolean), "doobie.postgres.implicits.unliftedUnboxedBooleanArrayType"),
      i("IntegerArrayMeta", jvm.Type.ArrayOf(TypesScala.Int), "doobie.postgres.implicits.unliftedUnboxedIntegerArrayType"),
      i("LongArrayMeta", jvm.Type.ArrayOf(TypesScala.Long), "doobie.postgres.implicits.unliftedUnboxedLongArrayType"),
      i("FloatArrayMeta", jvm.Type.ArrayOf(TypesScala.Float), "doobie.postgres.implicits.unliftedUnboxedFloatArrayType"),
      i("DoubleArrayMeta", jvm.Type.ArrayOf(TypesScala.Double), "doobie.postgres.implicits.unliftedUnboxedDoubleArrayType"),
      i("BigDecimalMeta", jvm.Type.ArrayOf(TypesScala.BigDecimal), "doobie.postgres.implicits.bigDecimalMeta")
    )
  }

  val missingInstancesByType: Map[jvm.Type, jvm.QIdent] =
    missingInstances.collect { case x: jvm.Given => (x.tpe, pkg / x.name) }.toMap

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupGetFor(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) Get.of(tpe).code
    else
      jvm.Type.base(tpe) match {
        case TypesScala.BigDecimal => code"$Meta.ScalaBigDecimalMeta.get"
        case TypesScala.Boolean    => code"$Meta.BooleanMeta.get"
        case TypesScala.Byte       => code"$Meta.ByteMeta.get"
        case TypesScala.Double     => code"$Meta.DoubleMeta.get"
        case TypesScala.Float      => code"$Meta.FloatMeta.get"
        case TypesScala.Int        => code"$Meta.IntMeta.get"
        case TypesScala.Long       => code"$Meta.LongMeta.get"
        case TypesJava.String      => code"$Meta.StringMeta.get"
        case jvm.Type.ArrayOf(TypesScala.Byte) =>
          code"$Meta.ByteArrayMeta.get"
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$getName"
        case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arrayGetName"
        case x if missingInstancesByType.contains(Meta.of(x)) =>
          code"${missingInstancesByType(Meta.of(x))}.get"
        case other =>
          code"${Get.of(other)}"
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupPutFor(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) Put.of(tpe).code
    else
      jvm.Type.base(tpe) match {
        case TypesScala.BigDecimal => code"$Meta.ScalaBigDecimalMeta.put"
        case TypesScala.Boolean    => code"$Meta.BooleanMeta.put"
        case TypesScala.Byte       => code"$Meta.ByteMeta.put"
        case TypesScala.Double     => code"$Meta.DoubleMeta.put"
        case TypesScala.Float      => code"$Meta.FloatMeta.put"
        case TypesScala.Int        => code"$Meta.IntMeta.put"
        case TypesScala.Long       => code"$Meta.LongMeta.put"
        case TypesJava.String      => code"$Meta.StringMeta.put"
        case jvm.Type.ArrayOf(TypesScala.Byte) =>
          code"$Meta.ByteArrayMeta.put"
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$putName"
        case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arrayPutName"
        case x if missingInstancesByType.contains(Meta.of(x)) =>
          code"${missingInstancesByType(Meta.of(x))}.put"
        case other =>
          code"${Put.of(other)}"
      }

  /** Oracle STRUCT types - not supported in PostgreSQL */
  override def structInstances(computed: ComputedOracleObjectType): List[jvm.ClassMember] = Nil

  /** Oracle COLLECTION types - not supported in PostgreSQL */
  override def collectionInstances(computed: ComputedOracleCollectionType): List[jvm.ClassMember] = Nil

  override def rowInstances(tpe: jvm.Type, cols: NonEmptyList[ComputedColumn], rowType: DbLib.RowType): List[jvm.Given] = {
    val text = textSupport.map(_.rowInstance(tpe, cols))

    val read = {
      val colsList = cols.toList

      val readInstances = colsList.map { c =>
        c.tpe match {
          case lang.Optional(underlying) =>
            code"new $Read.SingleOpt(${lookupGetFor(underlying)}).asInstanceOf[${Read.of(TypesScala.Any)}]"
          case other =>
            code"new $Read.Single(${lookupGetFor(other)}).asInstanceOf[${Read.of(TypesScala.Any)}]"
        }
      }

      val constructorArgs = colsList.zipWithIndex
        .map { case (col, idx) =>
          code"${col.name} = arr($idx).asInstanceOf[${col.tpe}]"
        }
        .mkCode(",\n    ")

      val body = code"""|new $Read.CompositeOfInstances(${TypesScala.Array}(
                        |  ${readInstances.mkCode(",\n  ")}
                        |))(${dialect.usingCall}scala.reflect.ClassTag.Any).map { arr =>
                        |  $tpe(
                        |    $constructorArgs
                        |  )
                        |}""".stripMargin

      jvm.Given(tparams = Nil, name = readName, implicitParams = Nil, tpe = Read.of(tpe), body = body)
    }

    val write = {
      val writeableColumnsWithId = cols.toList.filterNot(_.dbCol.maybeGenerated.exists(_.ALWAYS))

      val writeInstances = writeableColumnsWithId.map { c =>
        c.tpe match {
          case lang.Optional(underlying) =>
            code"new $Write.Single(${lookupPutFor(underlying)}).toOpt"
          case other =>
            code"new $Write.Single(${lookupPutFor(other)})"
        }
      }

      val deconstruct = {
        val parts = writeableColumnsWithId.map(c => code"a.${c.name}")
        code"a => ${TypesScala.List}(${parts.mkCode(", ")})"
      }

      val body =
        code"""|new $Write.Composite[$tpe](
               |  ${TypesScala.List}(${writeInstances.mkCode(",\n")}),
               |  $deconstruct
               |)""".stripMargin

      jvm.Given(tparams = Nil, name = writeName, implicitParams = Nil, tpe = Write.of(tpe), body = body)
    }
    rowType match {
      case DbLib.RowType.Writable      => text.toList
      case DbLib.RowType.ReadWriteable => List(read, write) ++ text
      case DbLib.RowType.Readable      => List(read)
    }
  }

  override def customTypeInstances(ct: CustomType): List[jvm.ClassMember] = {

    val v = jvm.Ident("v")
    val sqlTypeLit = jvm.StrLit(ct.sqlType)
    val single =
      List(
        Some(
          jvm.Given(
            tparams = Nil,
            name = getName,
            implicitParams = Nil,
            tpe = Get.of(ct.typoType),
            body = code"""|$Get.Advanced.other[${ct.toTypo.jdbcType}]($NonEmptyList.one($sqlTypeLit))
                          |  .map($v => ${ct.toTypo0(v)})""".stripMargin
          )
        ),
        Some(
          jvm.Given(
            tparams = Nil,
            name = putName,
            implicitParams = Nil,
            tpe = Put.of(ct.typoType),
            body = code"$Put.Advanced.other[${ct.fromTypo.jdbcType}]($NonEmptyList.one($sqlTypeLit)).contramap($v => ${ct.fromTypo0(v)})"
          )
        ),
        textSupport.map(_.customTypeInstance(ct))
      ).flatten

    val array =
      if (ct.forbidArray) Nil
      else {
        val fromTypo = ct.fromTypoInArray.getOrElse(ct.fromTypo)
        val toTypo = ct.toTypoInArray.getOrElse(ct.toTypo)
        val sqlArrayTypeLit = jvm.StrLit(ct.sqlType + "[]")
        val arrayType = jvm.Type.ArrayOf(ct.typoType)
        List(
          jvm.Given(
            tparams = Nil,
            name = arrayGetName,
            implicitParams = Nil,
            tpe = Get.of(arrayType),
            body = code"""|$Get.Advanced.array[${TypesScala.AnyRef}]($NonEmptyList.one($sqlArrayTypeLit))
                          |  .map(_.map($v => ${toTypo.toTypo(code"$v.asInstanceOf[${toTypo.jdbcType}]", ct.typoType)}))""".stripMargin
          ),
          jvm.Given(
            tparams = Nil,
            name = arrayPutName,
            implicitParams = Nil,
            tpe = Put.of(arrayType),
            body = code"""|$Put.Advanced.array[${TypesScala.AnyRef}]($NonEmptyList.one($sqlArrayTypeLit), $sqlTypeLit)
                          |  .contramap(_.map($v => ${fromTypo.fromTypo0(v)}))""".stripMargin
          )
        )
      }

    single ++ array
  }

  val additionalFiles: List[typr.jvm.File] = Nil
}
