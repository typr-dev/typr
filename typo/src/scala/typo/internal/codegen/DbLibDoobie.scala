package typo
package internal
package codegen

import typo.internal.analysis.MaybeReturnsRows

class DbLibDoobie(pkg: sc.QIdent, inlineImplicits: Boolean, default: ComputedDefault, enableStreamingInserts: Boolean, fixVerySlowImplicit: Boolean, implicitOrUsing: ImplicitOrUsing) extends DbLib {

  val SqlInterpolator = sc.Type.Qualified("doobie.syntax.string.toSqlInterpolator")
  def SQL(content: sc.Code) =
    sc.StringInterpolate(SqlInterpolator, sc.Ident("sql"), content)
  def frInterpolate(content: sc.Code) =
    sc.StringInterpolate(SqlInterpolator, sc.Ident("fr"), content)
  val Meta = sc.Type.Qualified("doobie.util.meta.Meta")
  val Put = sc.Type.Qualified("doobie.util.Put")
  val Get = sc.Type.Qualified("doobie.util.Get")
  val Write = sc.Type.Qualified("doobie.util.Write")
  val Read = sc.Type.Qualified("doobie.util.Read")
  val ConnectionIO = sc.Type.Qualified("doobie.free.connection.ConnectionIO")
  val Nullability = sc.Type.Qualified("doobie.enumerated.Nullability")
  val Fragments = sc.Type.Qualified("doobie.util.fragments")
  val Fragment = sc.Type.Qualified("doobie.util.fragment.Fragment")
  val pureCIO = sc.Type.Qualified("doobie.free.connection.pure")
  val delayCIO = sc.Type.Qualified("doobie.free.connection.delay")
  val fs2Stream = sc.Type.Qualified("fs2.Stream")
  val NonEmptyList = sc.Type.Qualified("cats.data.NonEmptyList")
  val fromWrite = sc.Type.Qualified("doobie.syntax.SqlInterpolator.SingleFragment.fromWrite")
  val FragmentOps = sc.Type.Qualified("doobie.postgres.syntax.FragmentOps")
  val JdbcType = sc.Type.Qualified("doobie.enumerated.JdbcType")
  val Update = sc.Type.Qualified("doobie.util.update.Update")
  val catsStdInstancesForList = sc.Type.Qualified("cats.instances.list.catsStdInstancesForList")

  val arrayGetName: sc.Ident = sc.Ident("arrayGet")
  val arrayPutName: sc.Ident = sc.Ident("arrayPut")
  val getName: sc.Ident = sc.Ident("get")
  val putName: sc.Ident = sc.Ident("put")
  val readName: sc.Ident = sc.Ident("read")
  val writeName = sc.Ident("write")

  val textSupport: Option[DbLibTextSupport] =
    if (enableStreamingInserts) Some(new DbLibTextSupport(pkg, inlineImplicits, Some(sc.Type.Qualified("doobie.postgres.Text")), default, implicitOrUsing)) else None

  def dbNames(cols: NonEmptyList[ComputedColumn], isRead: Boolean): sc.Code =
    cols
      .map(c => c.dbName.code ++ (if (isRead) SqlCast.fromPgCode(c) else sc.Code.Empty))
      .mkCode(", ")

  def runtimeInterpolateValue(name: sc.Code, tpe: sc.Type, forbidInline: Boolean = false): sc.Code =
    if (inlineImplicits && !forbidInline)
      tpe match {
        case TypesScala.Optional(underlying) =>
          code"$${$fromWrite($name)(new $Write.SingleOpt(${lookupPutFor(underlying)}))}"
        case other =>
          code"$${$fromWrite($name)(new $Write.Single(${lookupPutFor(other)}))}"
      }
    else code"$${$name}"

  def matchId(id: IdComputed): sc.Code =
    id match {
      case id: IdComputed.Unary =>
        code"${id.col.dbName.code} = ${runtimeInterpolateValue(id.paramName, id.tpe)}"
      case composite: IdComputed.Composite =>
        code"${composite.cols.map(cc => code"${cc.dbName.code} = ${runtimeInterpolateValue(code"${composite.paramName}.${cc.name}", cc.tpe)}").mkCode(" AND ")}"
    }

  override def repoSig(repoMethod: RepoMethod): Right[Nothing, sc.Code] = {
    val name = repoMethod.methodName
    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, rowType) =>
        Right(code"def $name: ${sc.Type.dsl.SelectBuilder.of(fieldsType, rowType)}")
      case RepoMethod.SelectAll(_, _, rowType) =>
        Right(code"def $name: ${fs2Stream.of(ConnectionIO, rowType)}")
      case RepoMethod.SelectById(_, _, id, rowType) =>
        Right(code"def $name(${id.param}): ${ConnectionIO.of(TypesScala.Option.of(rowType))}")
      case RepoMethod.SelectByIds(_, _, idComputed, idsParam, rowType) =>
        val usedDefineds = idComputed.userDefinedColTypes.zipWithIndex.map { case (colType, i) => sc.Param(sc.Ident(s"puts$i"), Put.of(sc.Type.ArrayOf(colType)), None) }
        usedDefineds match {
          case Nil =>
            Right(code"def $name($idsParam): ${fs2Stream.of(ConnectionIO, rowType)}")
          case nonEmpty =>
            Right(code"def $name($idsParam)($implicitOrUsing ${nonEmpty.map(_.code).mkCode(", ")}): ${fs2Stream.of(ConnectionIO, rowType)}")
        }
      case RepoMethod.SelectByIdsTracked(x) =>
        val usedDefineds = x.idComputed.userDefinedColTypes.zipWithIndex.map { case (colType, i) => sc.Param(sc.Ident(s"puts$i"), Put.of(sc.Type.ArrayOf(colType)), None) }
        val returnType = ConnectionIO.of(TypesScala.Map.of(x.idComputed.tpe, x.rowType))
        usedDefineds match {
          case Nil =>
            Right(code"def $name(${x.idsParam}): $returnType")
          case nonEmpty =>
            Right(code"def $name(${x.idsParam})($implicitOrUsing ${nonEmpty.map(_.code).mkCode(", ")}): $returnType")
        }

      case RepoMethod.SelectByUnique(_, keyColumns, _, rowType) =>
        Right(code"def $name(${keyColumns.map(_.param.code).mkCode(", ")}): ${ConnectionIO.of(TypesScala.Option.of(rowType))}")
      case RepoMethod.SelectByFieldValues(_, _, _, fieldValueOrIdsParam, rowType) =>
        Right(code"def $name($fieldValueOrIdsParam): ${fs2Stream.of(ConnectionIO, rowType)}")
      case RepoMethod.UpdateBuilder(_, fieldsType, rowType) =>
        Right(code"def $name: ${sc.Type.dsl.UpdateBuilder.of(fieldsType, rowType)}")
      case RepoMethod.UpdateFieldValues(_, id, varargs, _, _, _) =>
        Right(code"def $name(${id.param}, $varargs): ${ConnectionIO.of(TypesScala.Boolean)}")
      case RepoMethod.Update(_, _, _, param, _) =>
        Right(code"def $name($param): ${ConnectionIO.of(TypesScala.Option.of(param.tpe))}")
      case RepoMethod.Insert(_, _, unsavedParam, rowType, _) =>
        Right(code"def $name($unsavedParam): ${ConnectionIO.of(rowType)}")
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, rowType) =>
        Right(code"def $name($unsavedParam): ${ConnectionIO.of(rowType)}")
      case RepoMethod.InsertStreaming(_, rowType, _) =>
        Right(code"def $name(unsaved: ${fs2Stream.of(ConnectionIO, rowType)}, batchSize: ${TypesScala.Int} = 10000): ${ConnectionIO.of(TypesScala.Long)}")
      case RepoMethod.UpsertBatch(_, _, _, rowType, _) =>
        Right(code"def $name(unsaved: ${TypesScala.List.of(rowType)}): ${fs2Stream.of(ConnectionIO, rowType)}")
      case RepoMethod.InsertUnsavedStreaming(_, unsaved) =>
        Right(code"def $name(unsaved: ${fs2Stream.of(ConnectionIO, unsaved.tpe)}, batchSize: ${TypesScala.Int} = 10000): ${ConnectionIO.of(TypesScala.Long)}")
      case RepoMethod.Upsert(_, _, _, unsavedParam, rowType, _) =>
        Right(code"def $name($unsavedParam): ${ConnectionIO.of(rowType)}")
      case RepoMethod.UpsertStreaming(_, _, rowType, _) =>
        Right(code"def $name(unsaved: ${fs2Stream.of(ConnectionIO, rowType)}, batchSize: ${TypesScala.Int} = 10000): ${ConnectionIO.of(TypesScala.Int)}")
      case RepoMethod.DeleteBuilder(_, fieldsType, rowType) =>
        Right(code"def $name: ${sc.Type.dsl.DeleteBuilder.of(fieldsType, rowType)}")
      case RepoMethod.Delete(_, id) =>
        Right(code"def $name(${id.param}): ${ConnectionIO.of(TypesScala.Boolean)}")
      case RepoMethod.DeleteByIds(_, idComputed, idsParam) =>
        val usedDefineds = idComputed.userDefinedColTypes.zipWithIndex.map { case (colType, i) => sc.Param(sc.Ident(s"put$i"), Put.of(sc.Type.ArrayOf(colType)), None) }
        usedDefineds match {
          case Nil =>
            Right(code"def $name($idsParam): ${ConnectionIO.of(TypesScala.Int)}")
          case nonEmpty =>
            Right(code"def $name($idsParam)($implicitOrUsing ${nonEmpty.map(_.code).mkCode(", ")}): ${ConnectionIO.of(TypesScala.Int)}")
        }

      case RepoMethod.SqlFile(sqlScript) =>
        val params = sc.Params(sqlScript.params.map(p => sc.Param(p.name, p.tpe, None)))

        val retType = sqlScript.maybeRowName match {
          case MaybeReturnsRows.Query(rowName) => fs2Stream.of(ConnectionIO, rowName)
          case MaybeReturnsRows.Update         => ConnectionIO.of(TypesScala.Int)
        }

        Right(code"def $name$params: $retType")
    }
  }

  def query(sql: sc.Code, rowType: sc.Type): sc.Code =
    if (fixVerySlowImplicit) code"$sql.query(using $rowType.$readName)"
    else code"$sql.query[$rowType]"

  override def repoImpl(repoMethod: RepoMethod): sc.Code =
    repoMethod match {
      case RepoMethod.SelectBuilder(relName, fieldsType, rowType) =>
        code"""${sc.Type.dsl.SelectBuilderSql}(${sc.StrLit(relName.quotedValue)}, $fieldsType.structure, $rowType.read)"""

      case RepoMethod.SelectAll(relName, cols, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        val sql = SQL(code"""select $joinedColNames from $relName""")
        code"""${query(sql, rowType)}.stream"""

      case RepoMethod.SelectById(relName, cols, id, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        val sql = SQL(code"""select $joinedColNames from $relName where ${matchId(id)}""")
        code"""${query(sql, rowType)}.option"""

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
                   |${query(sql, rowType)}.stream
                   |""".stripMargin

          case unaryId: IdComputed.Unary =>
            val sql = SQL(
              code"""select $joinedColNames from $relName where ${code"${unaryId.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, idsParam.tpe, forbidInline = true)})"}"""
            )
            code"""${query(sql, rowType)}.stream"""
        }
      case RepoMethod.SelectByIdsTracked(x) =>
        code"""|${x.methodName}(${x.idsParam.name}).compile.toList.map { rows =>
               |  val byId = rows.view.map(x => (x.${x.idComputed.paramName}, x)).toMap
               |  ${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap
               |}""".stripMargin

      case RepoMethod.SelectByUnique(relName, keyColumns, allColumns, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(allColumns, isRead = true)}
                 |from $relName
                 |where ${keyColumns.map(c => code"${c.dbName.code} = ${runtimeInterpolateValue(c.name, c.tpe)}").mkCode(" AND ")}
                 |""".stripMargin
        }
        code"""${query(sql, rowType)}.option"""

      case RepoMethod.SelectByFieldValues(relName, cols, fieldValue, fieldValueOrIdsParam, rowType) =>
        val cases: NonEmptyList[sc.Code] =
          cols.map { col =>
            val fr = frInterpolate(code"${col.dbName.code} = ${runtimeInterpolateValue(sc.Ident("value"), col.tpe)}")
            code"case $fieldValue.${col.name}(value) => $fr"
          }

        val sql = SQL(code"""select ${dbNames(cols, isRead = true)} from $relName $$where""")
        code"""val where = $Fragments.whereAndOpt(
              |  ${fieldValueOrIdsParam.name}.map {
              |    ${cases.mkCode("\n")}
              |  }
              |)
              |${query(sql, rowType)}.stream""".stripMargin

      case RepoMethod.UpdateFieldValues(relName, id, varargs, fieldValue, cases0, _) =>
        val cases: NonEmptyList[sc.Code] =
          cases0.map { col =>
            val fr = frInterpolate(code"${col.dbName.code} = ${runtimeInterpolateValue(sc.Ident("value"), col.tpe)}${SqlCast.toPgCode(col)}")
            code"case $fieldValue.${col.name}(value) => $fr"
          }

        val sql = SQL {
          code"""|update $relName
                 |$$updates
                 |where ${matchId(id)}""".stripMargin
        }

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

      case RepoMethod.UpdateBuilder(relName, fieldsType, rowType) =>
        code"${sc.Type.dsl.UpdateBuilder}(${sc.StrLit(relName.quotedValue)}, $fieldsType.structure, $rowType.read)"

      case RepoMethod.Update(relName, cols, id, param, writeableCols) =>
        val sql = SQL(
          code"""update $relName
                |set ${writeableCols.map { col => code"${col.dbName.code} = ${runtimeInterpolateValue(code"${param.name}.${col.name}", col.tpe)}${SqlCast.toPgCode(col)}" }.mkCode(",\n")}
                |where ${matchId(id)}
                |returning ${dbNames(cols, isRead = true)}""".stripMargin
        )
        code"""|val ${id.paramName} = ${param.name}.${id.paramName}
               |${query(sql, param.tpe)}.option"""

      case RepoMethod.InsertUnsaved(relName, cols, unsaved, unsavedParam, default, rowType) =>
        val cases0 = unsaved.restCols.map { col =>
          val set = frInterpolate(code"${runtimeInterpolateValue(code"${unsavedParam.name}.${col.name}", col.tpe)}${SqlCast.toPgCode(col)}")
          code"""Some(($Fragment.const0(${sc.s(col.dbName.code)}), $set))"""
        }
        val cases1 = unsaved.defaultCols.map { case (col @ ComputedColumn(_, ident, _, _), origType) =>
          val setValue = frInterpolate(code"${runtimeInterpolateValue(code"value: $origType", origType)}${SqlCast.toPgCode(col)}")
          code"""|${unsavedParam.name}.$ident match {
                 |  case ${default.Defaulted}.${default.UseDefault} => None
                 |  case ${default.Defaulted}.${default.Provided}(value) => Some(($Fragment.const0(${sc.s(col.dbName.code)}), $setValue))
                 |}"""
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

        code"""|val fs = List(
               |  ${(cases0 ++ cases1.toList).mkCode(",\n")}
               |).flatten
               |
               |val q = if (fs.isEmpty) {
               |  $sqlEmpty
               |} else {
               |  val CommaSeparate = $Fragment.FragmentMonoid.intercalate(fr", ")
               |  $sql
               |}
               |${query(sc.Ident("q"), rowType)}.unique
               |"""

      case RepoMethod.InsertStreaming(relName, rowType, writeableColumnsWithId) =>
        val sql = SQL(code"COPY $relName(${dbNames(writeableColumnsWithId, isRead = false)}) FROM STDIN")

        if (fixVerySlowImplicit) code"new $FragmentOps($sql).copyIn(unsaved, batchSize)(using ${textSupport.get.lookupTextFor(rowType)})"
        else code"new $FragmentOps($sql).copyIn[$rowType](unsaved, batchSize)"

      case RepoMethod.InsertUnsavedStreaming(relName, unsaved) =>
        val sql = SQL(code"COPY $relName(${dbNames(unsaved.unsavedCols, isRead = false)}) FROM STDIN (DEFAULT '${textSupport.get.DefaultValue}')")

        if (fixVerySlowImplicit) code"new $FragmentOps($sql).copyIn(unsaved, batchSize)(using ${textSupport.get.lookupTextFor(unsaved.tpe)})"
        else code"new $FragmentOps($sql).copyIn[${unsaved.tpe}](unsaved, batchSize)"

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
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }

        code"${query(sql, rowType)}.unique"

      case RepoMethod.UpsertBatch(relName, cols, id, rowType, writeableColumnsWithId) =>
        val writeableColumnsNotId = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))

        val conflictAction = writeableColumnsNotId match {
          case Nil => code"do nothing"
          case nonEmpty =>
            code"""|do update set
                   |  ${nonEmpty.map { c => code"${c.dbName.code} = EXCLUDED.${c.dbName.code}" }.mkCode(",\n")}""".stripMargin
        }

        val sql = sc.s {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${writeableColumnsWithId.map(c => code"?${SqlCast.toPgCode(c)}").mkCode(code",")})
                 |on conflict (${dbNames(id.cols, isRead = false)})
                 |$conflictAction
                 |returning ${dbNames(cols, isRead = true)}""".stripMargin
        }

        if (fixVerySlowImplicit)
          code"""|${Update.of(rowType)}(
                 |  $sql
                 |)(using $rowType.$writeName)
                 |.updateManyWithGeneratedKeys[$rowType](${dbNames(cols, isRead = false)})(unsaved)(using $catsStdInstancesForList, $rowType.$readName)""".stripMargin
        else
          code"""|${Update.of(rowType)}(
                 |  $sql
                 |).updateManyWithGeneratedKeys[$rowType](${dbNames(cols, isRead = false)})(unsaved)"""

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
          if (fixVerySlowImplicit) code"new $FragmentOps($sql).copyIn(unsaved, batchSize)(using ${textSupport.get.lookupTextFor(rowType)})"
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

        code"""|for {
               |  _ <- ${SQL(code"create temporary table $tempTablename (like $relName) on commit drop")}.update.run
               |  _ <- $streamingInsert
               |  res <- $mergeSql.update.run
               |} yield res""".stripMargin

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

        code"${query(sql, rowType)}.unique"

      case RepoMethod.DeleteBuilder(relName, fieldsType, _) =>
        code"${sc.Type.dsl.DeleteBuilder}(${sc.StrLit(relName.quotedValue)}, $fieldsType.structure)"
      case RepoMethod.Delete(relName, id) =>
        val sql = SQL(code"""delete from $relName where ${matchId(id)}""")
        code"$sql.update.run.map(_ > 0)"
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
                   |$sql.update.run
                   |""".stripMargin

          case x: IdComputed.Unary =>
            val sql = SQL(
              code"""delete from $relName where ${code"${x.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, x.tpe, forbidInline = true)})"}"""
            )
            code"$sql.update.run"
        }

      case RepoMethod.SqlFile(sqlScript) =>
        val renderedScript: sc.Code = sqlScript.sqlFile.decomposedSql.renderCode { (paramAtIndex: Int) =>
          val param = sqlScript.params.find(_.indices.contains(paramAtIndex)).get
          val cast = SqlCast.toPg(param).fold("")(_.withColons)
          code"${runtimeInterpolateValue(param.name, param.tpe)}$cast"
        }
        val ret = for {
          cols <- sqlScript.maybeCols.toOption
          rowType <- sqlScript.maybeRowName.toOption
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
                 |${query(sc.Ident("sql"), rowType)}.stream""".stripMargin

        }
        ret.getOrElse {
          code"${SQL(renderedScript)}.update.run"
        }
    }

  override def mockRepoImpl(id: IdComputed, repoMethod: RepoMethod, maybeToRow: Option[sc.Param]): sc.Code = {
    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, _) =>
        code"${sc.Type.dsl.SelectBuilderMock}($fieldsType.structure, $delayCIO(map.values.toList), ${sc.Type.dsl.SelectParams}.empty)"
      case RepoMethod.SelectAll(_, _, _) =>
        code"$fs2Stream.emits(map.values.toList)"
      case RepoMethod.SelectById(_, _, id, _) =>
        code"$delayCIO(map.get(${id.paramName}))"
      case RepoMethod.SelectByIds(_, _, _, idsParam, _) =>
        code"$fs2Stream.emits(${idsParam.name}.flatMap(map.get).toList)"
      case RepoMethod.SelectByIdsTracked(x) =>
        code"""|${x.methodName}(${x.idsParam.name}).compile.toList.map { rows =>
               |  val byId = rows.view.map(x => (x.${x.idComputed.paramName}, x)).toMap
               |  ${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap
               |}""".stripMargin
      case RepoMethod.SelectByUnique(_, keyColumns, _, _) =>
        code"$delayCIO(map.values.find(v => ${keyColumns.map(c => code"${c.name} == v.${c.name}").mkCode(" && ")}))"
      case RepoMethod.SelectByFieldValues(_, cols, fieldValue, fieldValueOrIdsParam, _) =>
        val cases = cols.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.filter(_.${col.name} == value)"
        }
        code"""$fs2Stream.emits {
              |  ${fieldValueOrIdsParam.name}.foldLeft(map.values) {
              |    ${cases.mkCode("\n")}
              |  }.toList
              |}""".stripMargin
      case RepoMethod.UpdateBuilder(_, fieldsType, _) =>
        code"${sc.Type.dsl.UpdateBuilderMock}(${sc.Type.dsl.UpdateParams}.empty, $fieldsType.structure, map)"
      case RepoMethod.UpdateFieldValues(_, id, varargs, fieldValue, cases0, _) =>
        val cases = cases0.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.copy(${col.name} = value)"
        }

        code"""|$delayCIO {
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
        code"""$delayCIO {
              |  map.get(${param.name}.${id.paramName}).map { _ =>
              |    map.put(${param.name}.${id.paramName}, ${param.name}): @${TypesScala.nowarn}
              |    ${param.name}
              |  }
              |}""".stripMargin
      case RepoMethod.Insert(_, _, unsavedParam, _, _) =>
        code"""|$delayCIO {
               |  val _ = if (map.contains(${unsavedParam.name}.${id.paramName}))
               |    sys.error(s"id $${${unsavedParam.name}.${id.paramName}} already exists")
               |  else
               |    map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name})
               |
               |  ${unsavedParam.name}
               |}"""
      case RepoMethod.Upsert(_, _, _, unsavedParam, _, _) =>
        code"""|$delayCIO {
               |  map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name}): @${TypesScala.nowarn}
               |  ${unsavedParam.name}
               |}""".stripMargin
      case RepoMethod.UpsertStreaming(_, _, _, _) =>
        code"""|unsaved.compile.toList.map { rows =>
               |  var num = 0
               |  rows.foreach { row =>
               |    map += (row.${id.paramName} -> row)
               |    num += 1
               |  }
               |  num
               |}""".stripMargin
      case RepoMethod.UpsertBatch(_, _, _, _, _) =>
        code"""|$fs2Stream.emits {
               |  unsaved.map { row =>
               |    map += (row.${id.paramName} -> row)
               |    row
               |  }
               |}""".stripMargin
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _) =>
        code"insert(${maybeToRow.get.name}(${unsavedParam.name}))"
      case RepoMethod.InsertStreaming(_, _, _) =>
        code"""|unsaved.compile.toList.map { rows =>
               |  var num = 0L
               |  rows.foreach { row =>
               |    map += (row.${id.paramName} -> row)
               |    num += 1
               |  }
               |  num
               |}""".stripMargin
      case RepoMethod.InsertUnsavedStreaming(_, _) =>
        code"""|unsaved.compile.toList.map { unsavedRows =>
               |  var num = 0L
               |  unsavedRows.foreach { unsavedRow =>
               |    val row = ${maybeToRow.get.name}(unsavedRow)
               |    map += (row.${id.paramName} -> row)
               |    num += 1
               |  }
               |  num
               |}""".stripMargin

      case RepoMethod.DeleteBuilder(_, fieldsType, _) =>
        code"${sc.Type.dsl.DeleteBuilderMock}(${sc.Type.dsl.DeleteParams}.empty, $fieldsType.structure, map)"
      case RepoMethod.Delete(_, id) =>
        code"$delayCIO(map.remove(${id.paramName}).isDefined)"
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        code"$delayCIO(${idsParam.name}.map(id => map.remove(id)).count(_.isDefined))"
      case RepoMethod.SqlFile(_) =>
        // should not happen (tm)
        code"???"
    }
  }

  override def testInsertMethod(x: ComputedTestInserts.InsertMethod): sc.Value =
    sc.Value(
      Nil,
      x.name,
      x.params,
      Nil,
      ConnectionIO.of(x.table.names.RowName),
      code"(new ${x.table.names.RepoImplName}).insert(new ${x.cls}(${x.values.map { case (p, expr) => code"$p = $expr" }.mkCode(", ")}))"
    )

  override val defaultedInstance: List[sc.Given] =
    textSupport.map(_.defaultedInstance).toList

  override def stringEnumInstances(wrapperType: sc.Type, underlying: sc.Type, sqlType: String, openEnum: Boolean): List[sc.Given] = {
    val sqlTypeLit = sc.StrLit(sqlType)
    val sqlArrayTypeLit = sc.StrLit(sqlType + "[]")
    List(
      Some(
        sc.Given(
          tparams = Nil,
          name = putName,
          implicitParams = Nil,
          tpe = Put.of(wrapperType),
          body =
            if (openEnum) code"${lookupPutFor(underlying)}.contramap(_.value)"
            else code"$Put.Advanced.one[$wrapperType]($JdbcType.Other, $NonEmptyList.one($sqlTypeLit), (ps, i, a) => ps.setString(i, a.value), (rs, i, a) => rs.updateString(i, a.value))",
          implicitOrUsing
        )
      ),
      Some(
        sc.Given(
          tparams = Nil,
          name = arrayPutName,
          implicitParams = Nil,
          tpe = Put.of(sc.Type.ArrayOf(wrapperType)),
          body =
            if (openEnum) code"${lookupPutFor(sc.Type.ArrayOf(underlying))}.contramap(_.map(_.value))"
            else code"$Put.Advanced.array[${TypesScala.AnyRef}]($NonEmptyList.one($sqlArrayTypeLit), $sqlTypeLit).contramap(_.map(_.value))",
          implicitOrUsing
        )
      ),
      Some(
        sc.Given(
          tparams = Nil,
          name = getName,
          implicitParams = Nil,
          tpe = Get.of(wrapperType),
          body =
            if (openEnum) code"""${lookupGetFor(underlying)}.map($wrapperType.apply)"""
            else code"""${lookupGetFor(underlying)}.temap($wrapperType.apply)""",
          implicitOrUsing
        )
      ),
      Some(
        sc.Given(
          tparams = Nil,
          name = arrayGetName,
          implicitParams = Nil,
          tpe = Get.of(sc.Type.ArrayOf(wrapperType)),
          body = {
            val get = lookupGetFor(sc.Type.ArrayOf(underlying))
            if (openEnum) code"""$get.map(_.map($wrapperType.apply))"""
            else code"$get.map(_.map(force))"
          },
          implicitOrUsing
        )
      ),
      Some(
        sc.Given(
          tparams = Nil,
          name = writeName,
          implicitParams = Nil,
          tpe = Write.of(wrapperType),
          body = code"new $Write.Single($putName)",
          implicitOrUsing
        )
      ),
      Some(
        sc.Given(
          tparams = Nil,
          name = readName,
          implicitParams = Nil,
          tpe = Read.of(wrapperType),
          body = code"new $Read.Single($getName)",
          implicitOrUsing
        )
      ),
      textSupport.map(_.anyValInstance(wrapperType, underlying))
    ).flatten
  }

  override def wrapperTypeInstances(wrapperType: sc.Type.Qualified, underlying: sc.Type, overrideDbType: Option[String]): List[sc.Given] =
    List(
      Some(
        sc.Given(
          tparams = Nil,
          name = putName,
          implicitParams = Nil,
          tpe = Put.of(wrapperType),
          body = code"${lookupPutFor(underlying)}.contramap(_.value)",
          implicitOrUsing
        )
      ),
      Some(
        sc.Given(
          tparams = Nil,
          name = getName,
          implicitParams = Nil,
          tpe = Get.of(wrapperType),
          body = code"${lookupGetFor(underlying)}.map($wrapperType.apply)",
          implicitOrUsing
        )
      ),
      Some(
        sc.Given(
          tparams = Nil,
          name = arrayPutName,
          implicitParams = Nil,
          tpe = Put.of(sc.Type.ArrayOf(wrapperType)),
          body = code"${lookupPutFor(sc.Type.ArrayOf(underlying))}.contramap(_.map(_.value))",
          implicitOrUsing
        )
      ),
      Some(
        sc.Given(
          tparams = Nil,
          name = arrayGetName,
          implicitParams = Nil,
          tpe = Get.of(sc.Type.ArrayOf(wrapperType)),
          body = code"${lookupGetFor(sc.Type.ArrayOf(underlying))}.map(_.map($wrapperType.apply))",
          implicitOrUsing
        )
      ),
      textSupport.map(_.anyValInstance(wrapperType, underlying))
    ).flatten

  override val missingInstances: List[sc.ClassMember] = {
    def i(name: String, metaType: sc.Type, qident: String) =
      sc.Given(Nil, sc.Ident(name), Nil, Meta.of(metaType), sc.QIdent(qident), implicitOrUsing)

    List(
      i("UUIDMeta", TypesJava.UUID, "doobie.postgres.implicits.UuidType"),
      i("UUIDArrayMeta", sc.Type.ArrayOf(TypesJava.UUID), "doobie.postgres.implicits.unliftedUUIDArrayType"),
      i("StringArrayMeta", sc.Type.ArrayOf(TypesJava.String), "doobie.postgres.implicits.unliftedStringArrayType"),
      i("BooleanArrayMeta", sc.Type.ArrayOf(TypesScala.Boolean), "doobie.postgres.implicits.unliftedUnboxedBooleanArrayType"),
      i("IntegerArrayMeta", sc.Type.ArrayOf(TypesScala.Int), "doobie.postgres.implicits.unliftedUnboxedIntegerArrayType"),
      i("LongArrayMeta", sc.Type.ArrayOf(TypesScala.Long), "doobie.postgres.implicits.unliftedUnboxedLongArrayType"),
      i("FloatArrayMeta", sc.Type.ArrayOf(TypesScala.Float), "doobie.postgres.implicits.unliftedUnboxedFloatArrayType"),
      i("DoubleArrayMeta", sc.Type.ArrayOf(TypesScala.Double), "doobie.postgres.implicits.unliftedUnboxedDoubleArrayType"),
      i("BigDecimalMeta", sc.Type.ArrayOf(TypesScala.BigDecimal), "doobie.postgres.implicits.bigDecimalMeta")
    )
  }

  val missingInstancesByType: Map[sc.Type, sc.QIdent] =
    missingInstances.collect { case x: sc.Given => (x.tpe, pkg / x.name) }.toMap

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupGetFor(tpe: sc.Type): sc.Code =
    if (!inlineImplicits) Get.of(tpe).code
    else
      sc.Type.base(tpe) match {
        case TypesScala.BigDecimal => code"$Meta.ScalaBigDecimalMeta.get"
        case TypesScala.Boolean    => code"$Meta.BooleanMeta.get"
        case TypesScala.Byte       => code"$Meta.ByteMeta.get"
        case TypesScala.Double     => code"$Meta.DoubleMeta.get"
        case TypesScala.Float      => code"$Meta.FloatMeta.get"
        case TypesScala.Int        => code"$Meta.IntMeta.get"
        case TypesScala.Long       => code"$Meta.LongMeta.get"
        case TypesJava.String      => code"$Meta.StringMeta.get"
        case sc.Type.ArrayOf(TypesScala.Byte) =>
          code"$Meta.ByteArrayMeta.get"
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$getName"
        case sc.Type.ArrayOf(targ: sc.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arrayGetName"
        case x if missingInstancesByType.contains(Meta.of(x)) =>
          code"${missingInstancesByType(Meta.of(x))}.get"
        case other =>
          code"${Get.of(other)}"
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupPutFor(tpe: sc.Type): sc.Code =
    if (!inlineImplicits) Put.of(tpe).code
    else
      sc.Type.base(tpe) match {
        case TypesScala.BigDecimal => code"$Meta.ScalaBigDecimalMeta.put"
        case TypesScala.Boolean    => code"$Meta.BooleanMeta.put"
        case TypesScala.Byte       => code"$Meta.ByteMeta.put"
        case TypesScala.Double     => code"$Meta.DoubleMeta.put"
        case TypesScala.Float      => code"$Meta.FloatMeta.put"
        case TypesScala.Int        => code"$Meta.IntMeta.put"
        case TypesScala.Long       => code"$Meta.LongMeta.put"
        case TypesJava.String      => code"$Meta.StringMeta.put"
        case sc.Type.ArrayOf(TypesScala.Byte) =>
          code"$Meta.ByteArrayMeta.put"
        case x: sc.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$putName"
        case sc.Type.ArrayOf(targ: sc.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arrayPutName"
        case x if missingInstancesByType.contains(Meta.of(x)) =>
          code"${missingInstancesByType(Meta.of(x))}.put"
        case other =>
          code"${Put.of(other)}"
      }

  override def rowInstances(tpe: sc.Type, cols: NonEmptyList[ComputedColumn], rowType: DbLib.RowType): List[sc.Given] = {
    val text = textSupport.map(_.rowInstance(tpe, cols))

    val read = {
      val colsList = cols.toList

      val readInstances = colsList.map { c =>
        c.tpe match {
          case TypesScala.Optional(underlying) =>
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
                        |))(using scala.reflect.ClassTag.Any).map { arr =>
                        |  $tpe(
                        |    $constructorArgs
                        |  )
                        |}""".stripMargin

      sc.Given(tparams = Nil, name = readName, implicitParams = Nil, tpe = Read.of(tpe), body = body, implicitOrUsing)
    }

    val write = {
      val writeableColumnsWithId = cols.toList.filterNot(_.dbCol.maybeGenerated.exists(_.ALWAYS))

      val writeInstances = writeableColumnsWithId.map { c =>
        c.tpe match {
          case TypesScala.Optional(underlying) =>
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

      sc.Given(tparams = Nil, name = writeName, implicitParams = Nil, tpe = Write.of(tpe), body = body, implicitOrUsing)
    }
    rowType match {
      case DbLib.RowType.Writable      => text.toList
      case DbLib.RowType.ReadWriteable => List(read, write) ++ text
      case DbLib.RowType.Readable      => List(read)
    }
  }

  override def customTypeInstances(ct: CustomType): List[sc.ClassMember] = {

    val v = sc.Ident("v")
    val sqlTypeLit = sc.StrLit(ct.sqlType)
    val single =
      List(
        Some(
          sc.Given(
            tparams = Nil,
            name = getName,
            implicitParams = Nil,
            tpe = Get.of(ct.typoType),
            body = code"""|$Get.Advanced.other[${ct.toTypo.jdbcType}]($NonEmptyList.one($sqlTypeLit))
                          |  .map($v => ${ct.toTypo0(v)})""".stripMargin,
            implicitOrUsing
          )
        ),
        Some(
          sc.Given(
            tparams = Nil,
            name = putName,
            implicitParams = Nil,
            tpe = Put.of(ct.typoType),
            body = code"$Put.Advanced.other[${ct.fromTypo.jdbcType}]($NonEmptyList.one($sqlTypeLit)).contramap($v => ${ct.fromTypo0(v)})",
            implicitOrUsing
          )
        ),
        textSupport.map(_.customTypeInstance(ct))
      ).flatten

    val array =
      if (ct.forbidArray) Nil
      else {
        val fromTypo = ct.fromTypoInArray.getOrElse(ct.fromTypo)
        val toTypo = ct.toTypoInArray.getOrElse(ct.toTypo)
        val sqlArrayTypeLit = sc.StrLit(ct.sqlType + "[]")
        val arrayType = sc.Type.ArrayOf(ct.typoType)
        List(
          sc.Given(
            tparams = Nil,
            name = arrayGetName,
            implicitParams = Nil,
            tpe = Get.of(arrayType),
            body = code"""|$Get.Advanced.array[${TypesScala.AnyRef}]($NonEmptyList.one($sqlArrayTypeLit))
                          |  .map(_.map($v => ${toTypo.toTypo(code"$v.asInstanceOf[${toTypo.jdbcType}]", ct.typoType)}))""".stripMargin,
            implicitOrUsing
          ),
          sc.Given(
            tparams = Nil,
            name = arrayPutName,
            implicitParams = Nil,
            tpe = Put.of(arrayType),
            body = code"""|$Put.Advanced.array[${TypesScala.AnyRef}]($NonEmptyList.one($sqlArrayTypeLit), $sqlTypeLit)
                          |  .contramap(_.map($v => ${fromTypo.fromTypo0(v)}))""".stripMargin,
            implicitOrUsing
          )
        )
      }

    single ++ array
  }

  val additionalFiles: List[typo.sc.File] = Nil
}
