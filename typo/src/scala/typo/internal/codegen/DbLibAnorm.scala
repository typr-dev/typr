package typo
package internal
package codegen

import typo.internal.analysis.MaybeReturnsRows

class DbLibAnorm(pkg: jvm.QIdent, inlineImplicits: Boolean, default: ComputedDefault, enableStreamingInserts: Boolean, override val lang: LangScala) extends DbLib {
  val dialect = lang.dialect
  private val sqlCast = new SqlCast(needsTimestampCasts = true)

  val BatchSql = jvm.Type.Qualified("anorm.BatchSql")
  val Column = jvm.Type.Qualified("anorm.Column")
  val ToStatement = jvm.Type.Qualified("anorm.ToStatement")
  val ToSql = jvm.Type.Qualified("anorm.ToSql")
  val NamedParameter = jvm.Type.Qualified("anorm.NamedParameter")
  val ParameterValue = jvm.Type.Qualified("anorm.ParameterValue")
  val ToParameterValue = jvm.Type.Qualified("anorm.ToParameterValue")
  val RowParser = jvm.Type.Qualified("anorm.RowParser")
  val Success = jvm.Type.Qualified("anorm.Success")
  val SqlMappingError = jvm.Type.Qualified("anorm.SqlMappingError")
  val SqlStringInterpolation = jvm.Type.Qualified("anorm.SqlStringInterpolation")
  val ParameterMetaData = jvm.Type.Qualified("anorm.ParameterMetaData")
  val SQL = jvm.Type.Qualified("anorm.SQL")
  val TypeDoesNotMatch = jvm.Type.Qualified("anorm.TypeDoesNotMatch")
  val SimpleSql = jvm.Type.Qualified("anorm.SimpleSql")
  val Row = jvm.Type.Qualified("anorm.Row")
  val managed = jvm.Type.Qualified("resource.managed")

  def rowParserFor(rowType: jvm.Type) = code"$rowType.$rowParserName(1)"

  def SQL(content: jvm.Code) =
    jvm.StringInterpolate(SqlStringInterpolation, jvm.Ident("SQL"), content)

  val arrayColumnName = jvm.Ident("arrayColumn")
  val arrayToStatementName: jvm.Ident = jvm.Ident("arrayToStatement")
  val columnName: jvm.Ident = jvm.Ident("column")
  val parameterMetadataName: jvm.Ident = jvm.Ident("parameterMetadata")
  val rowParserName: jvm.Ident = jvm.Ident("rowParser")
  val toStatementName: jvm.Ident = jvm.Ident("toStatement")
  val arrayParameterMetaDataName = jvm.Ident("arrayParameterMetaData")
  val textSupport: Option[DbLibTextSupport] =
    if (enableStreamingInserts) Some(new DbLibTextSupport(pkg, inlineImplicits, None, default, lang)) else None
  val ExecuteReturningSyntax = jvm.QIdent(List[List[jvm.Ident]](List(jvm.Ident("anorm")), pkg.idents, List(jvm.Ident("ExecuteReturningSyntax"))).flatten)

  override val additionalFiles: List[typo.jvm.File] = {
    List[List[jvm.File]](
      List(
        jvm.File(
          tpe = jvm.Type.Qualified(ExecuteReturningSyntax),
          contents = {
            // drop structured imports from anorm.*, as the auto-import thing would need to be more clever to handle this
            code"""|object ${ExecuteReturningSyntax.name} {
                   |  /* add executeReturning to anorm. it needs to be inside the package, because everything is hidden */
                   |  implicit class Ops(batchSql: anorm.BatchSql) {
                   |    def executeReturning[T](parser: anorm.ResultSetParser[T])(${dialect.usingDefinition} c: ${TypesJava.Connection}): T =
                   |      $managed(batchSql.getFilledStatement(c, getGeneratedKeys = true))(using anorm.StatementResource, anorm.statementClassTag).acquireAndGet { ps =>
                   |        ps.executeBatch()
                   |        anorm.Sql
                   |          .asTry(
                   |            parser,
                   |            $managed(ps.getGeneratedKeys)(using anorm.ResultSetResource, anorm.resultSetClassTag),
                   |            onFirstRow = false,
                   |            anorm.ColumnAliaser.empty
                   |          )
                   |          .get
                   |      }
                   |  }
                   |}
                   |""".stripMargin
          },
          secondaryTypes = Nil,
          scope = Scope.Main
        )
      ),
      textSupport match {
        case Some(textSupport) =>
          List(
            jvm.File(textSupport.Text, DbLibTextImplementations.Text(dialect), Nil, scope = Scope.Main),
            jvm.File(textSupport.streamingInsert, DbLibTextImplementations.streamingInsertAnorm(textSupport.Text, dialect), Nil, scope = Scope.Main)
          )
        case None => Nil
      }
    ).flatten
  }

  def runtimeInterpolateValue(name: jvm.Code, tpe: jvm.Type): jvm.Code =
    if (inlineImplicits)
      code"$${$ParameterValue($name, null, ${lookupToStatementFor(tpe)})}"
    else code"$${$name}"

  def dbNames(cols: NonEmptyList[ComputedColumn], isRead: Boolean): jvm.Code =
    cols
      .map(c => c.dbName.code ++ (if (isRead) sqlCast.fromPgCode(c) else jvm.Code.Empty))
      .mkCode(", ")

  def matchId(id: IdComputed): jvm.Code =
    id match {
      case id: IdComputed.Unary =>
        code"${id.col.dbName.code} = ${runtimeInterpolateValue(id.paramName, id.tpe)}"
      case composite: IdComputed.Composite =>
        code"${composite.cols.map(cc => code"${cc.dbName.code} = ${runtimeInterpolateValue(code"${composite.paramName}.${cc.name}", cc.tpe)}").mkCode(" AND ")}"
    }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupColumnFor(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) jvm.Summon(Column.of(tpe)).code
    else
      jvm.Type.base(tpe) match {
        case TypesScala.BigDecimal => code"$Column.columnToScalaBigDecimal"
        case TypesScala.Boolean    => code"$Column.columnToBoolean"
        case TypesScala.Byte       => code"$Column.columnToByte"
        case TypesScala.Double     => code"$Column.columnToDouble"
        case TypesScala.Float      => code"$Column.columnToFloat"
        case TypesScala.Int        => code"$Column.columnToInt"
        case TypesScala.Long       => code"$Column.columnToLong"
        case TypesJava.String      => code"$Column.columnToString"
        case TypesJava.UUID        => code"$Column.columnToUUID"
        case lang.Optional(targ)   => code"$Column.columnToOption(${dialect.usingCall}${lookupColumnFor(targ)})"
        // generated type
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$columnName"
        // customized type mapping
        case x if missingInstancesByType.contains(Column.of(x)) =>
          code"${missingInstancesByType(Column.of(x))}"
        // generated array type
        case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arrayColumnName"
        case jvm.Type.ArrayOf(TypesScala.Byte) => code"$Column.columnToByteArray"
        // fallback array case. implementation looks loco, but I guess it works
        case jvm.Type.ArrayOf(targ) => code"$Column.columnToArray[$targ](${dialect.usingCall}${lookupColumnFor(targ)}, implicitly)"
        case other                  => jvm.Summon(Column.of(other)).code
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupParameterMetaDataFor(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) jvm.Summon(ParameterMetaData.of(tpe)).code
    else
      jvm.Type.base(tpe) match {
        case TypesScala.BigDecimal => code"$ParameterMetaData.BigDecimalParameterMetaData"
        case TypesScala.Boolean    => code"$ParameterMetaData.BooleanParameterMetaData"
        case TypesScala.Byte       => code"$ParameterMetaData.ByteParameterMetaData"
        case TypesScala.Double     => code"$ParameterMetaData.DoubleParameterMetaData"
        case TypesScala.Float      => code"$ParameterMetaData.FloatParameterMetaData"
        case TypesScala.Int        => code"$ParameterMetaData.IntParameterMetaData"
        case TypesScala.Long       => code"$ParameterMetaData.LongParameterMetaData"
        case TypesJava.String      => code"$ParameterMetaData.StringParameterMetaData"
        case TypesJava.UUID        => code"$ParameterMetaData.UUIDParameterMetaData"
//        case ScalaTypes.Optional(targ) => lookupParameterMetaDataFor(targ)
        // generated type
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$parameterMetadataName"
        // customized type mapping
        case x if missingInstancesByType.contains(ParameterMetaData.of(x)) =>
          code"${missingInstancesByType(ParameterMetaData.of(x))}"
        // generated array type
//        case sc.Type.TApply(ScalaTypes.Array, List(targ: sc.Type.Qualified)) if targ.value.idents.startsWith(pkg.idents) =>
//          code"$targ.$arrayColumnName"
        case jvm.Type.ArrayOf(TypesScala.Byte) => code"$ParameterMetaData.ByteArrayParameterMetaData"
        // fallback array case.
        case jvm.Type.ArrayOf(targ) => code"${pkg / arrayParameterMetaDataName}(${dialect.usingCall}${lookupParameterMetaDataFor(targ)})"
        case other                  => jvm.Summon(ParameterMetaData.of(other)).code
      }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupToStatementFor(tpe: jvm.Type): jvm.Code =
    if (!inlineImplicits) jvm.Summon(ToStatement.of(tpe)).code
    else
      jvm.Type.base(tpe) match {
        case TypesScala.BigDecimal => code"$ToStatement.scalaBigDecimalToStatement"
        case TypesScala.Boolean    => code"$ToStatement.booleanToStatement"
        case TypesScala.Byte       => code"$ToStatement.byteToStatement"
        case TypesScala.Double     => code"$ToStatement.doubleToStatement"
        case TypesScala.Float      => code"$ToStatement.floatToStatement"
        case TypesScala.Int        => code"$ToStatement.intToStatement"
        case TypesScala.Long       => code"$ToStatement.longToStatement"
        case TypesJava.String      => code"$ToStatement.stringToStatement"
        case TypesJava.UUID        => code"$ToStatement.uuidToStatement"
        case lang.Optional(targ)   => code"$ToStatement.optionToStatement(${dialect.usingCall}${lookupToStatementFor(targ)}, ${lookupParameterMetaDataFor(targ)})"
        // generated type
        case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
          code"$tpe.$toStatementName"
        // customized type mapping
        case x if missingInstancesByType.contains(ToStatement.of(x)) =>
          code"${missingInstancesByType(ToStatement.of(x))}"
        case jvm.Type.ArrayOf(TypesScala.Byte) => code"$ToStatement.byteArrayToStatement"
        // generated array type
        case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) if targ.value.idents.startsWith(pkg.idents) =>
          code"$targ.$arrayToStatementName"
        // fallback array case.
        case jvm.Type.ArrayOf(targ) =>
          // `ToStatement.arrayToParameter` does not work for arbitrary types. if it's a user-defined type, user needs to provide this too
          if (jvm.Type.containsUserDefined(tpe)) // should be `targ`, but this information is stripped in `sc.Type.base` above
            code"$targ.arrayToStatement"
          else code"$ToStatement.arrayToParameter(${dialect.usingCall}${lookupParameterMetaDataFor(targ)})"
        case other => jvm.Summon(ToStatement.of(other)).code
      }

  override def resolveConstAs(typoType: TypoType): jvm.Code = {
    val tpe = typoType.jvmType
    typoType match {
      case TypoType.Nullable(_, inner) =>
        code"${lang.dsl.ConstAsAsOpt}[${inner.jvmType}]($ToParameterValue(null, ${lookupToStatementFor(tpe)}), ${lookupParameterMetaDataFor(tpe)})"
      case _ =>
        code"${lang.dsl.ConstAsAs}[$tpe](${dialect.usingCall}$ToParameterValue(null, ${lookupToStatementFor(tpe)}), ${lookupParameterMetaDataFor(tpe)})"
    }
  }

  val c = jvm.Param(jvm.Ident("c"), TypesJava.Connection)

  override def repoSig(repoMethod: RepoMethod): Right[Nothing, jvm.Method] = {
    def sig(
        params: List[jvm.Param[jvm.Type]],
        implicitParams: List[jvm.Param[jvm.Type]],
        returnType: jvm.Type
    ) = Right(
      jvm.Method(
        Nil,
        comments = repoMethod.comment,
        tparams = Nil,
        name = jvm.Ident(repoMethod.methodName),
        params = params,
        implicitParams = implicitParams,
        tpe = returnType,
        throws = Nil,
        body = jvm.Body.Abstract,
        isOverride = false,
        isDefault = false
      )
    )

    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, implicitParams = Nil, returnType = lang.dsl.SelectBuilder.of(fieldsType, rowType))
      case RepoMethod.SelectAll(_, _, rowType) =>
        sig(params = Nil, implicitParams = List(c), returnType = TypesScala.List.of(rowType))
      case RepoMethod.SelectById(_, _, id, rowType) =>
        sig(params = List(id.param), implicitParams = List(c), returnType = TypesScala.Option.of(rowType))
      case RepoMethod.SelectByIds(_, _, _, idsParam, rowType) =>
        sig(params = List(idsParam), implicitParams = List(c), returnType = TypesScala.List.of(rowType))
      case RepoMethod.SelectByIdsTracked(x) =>
        sig(params = List(x.idsParam), implicitParams = List(c), returnType = TypesScala.Map.of(x.idComputed.tpe, x.rowType))
      case RepoMethod.SelectByUnique(_, keyColumns, _, rowType) =>
        sig(params = keyColumns.toList.map(_.param), implicitParams = List(c), returnType = TypesScala.Option.of(rowType))
      case RepoMethod.SelectByFieldValues(_, _, _, fieldValueOrIdsParam, rowType) =>
        sig(params = List(fieldValueOrIdsParam), implicitParams = List(c), returnType = TypesScala.List.of(rowType))
      case RepoMethod.UpdateBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, implicitParams = Nil, returnType = lang.dsl.UpdateBuilder.of(fieldsType, rowType))
      case RepoMethod.UpdateFieldValues(_, id, varargs, _, _, _) =>
        sig(params = List(id.param, varargs), implicitParams = List(c), returnType = TypesScala.Boolean)
      case RepoMethod.Update(_, _, _, param, _) =>
        sig(params = List(param), implicitParams = List(c), returnType = TypesScala.Option.of(param.tpe))
      case RepoMethod.Insert(_, _, _, unsavedParam, _, returningStrategy) =>
        sig(params = List(unsavedParam), implicitParams = List(c), returnType = returningStrategy.returnType)
      case RepoMethod.InsertStreaming(_, rowType, _) =>
        val unsaved = jvm.Param(jvm.Ident("unsaved"), TypesScala.Iterator.of(rowType))
        val batchSize = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("batchSize"), TypesScala.Int, Some(code"10000"))
        sig(params = List(unsaved, batchSize), implicitParams = List(c), returnType = TypesScala.Long)
      case RepoMethod.Upsert(_, _, _, unsavedParam, rowType, _) =>
        sig(params = List(unsavedParam), implicitParams = List(c), returnType = rowType)
      case RepoMethod.UpsertBatch(_, _, _, rowType, _) =>
        val unsaved = jvm.Param(jvm.Ident("unsaved"), TypesScala.Iterable.of(rowType))
        sig(params = List(unsaved), implicitParams = List(c), returnType = TypesScala.List.of(rowType))
      case RepoMethod.UpsertStreaming(_, _, rowType, _) =>
        val unsaved = jvm.Param(jvm.Ident("unsaved"), TypesScala.Iterator.of(rowType))
        val batchSize = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("batchSize"), TypesScala.Int, Some(code"10000"))
        sig(params = List(unsaved, batchSize), implicitParams = List(c), returnType = TypesScala.Int)
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _, returningStrategy) =>
        sig(params = List(unsavedParam), implicitParams = List(c), returnType = returningStrategy.returnType)
      case RepoMethod.InsertUnsavedStreaming(_, unsaved) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), TypesScala.Iterator.of(unsaved.tpe))
        val batchSize = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("batchSize"), TypesScala.Int, Some(code"10000"))
        sig(params = List(unsavedParam, batchSize), implicitParams = List(c), returnType = TypesScala.Long)
      case RepoMethod.DeleteBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, implicitParams = Nil, returnType = lang.dsl.DeleteBuilder.of(fieldsType, rowType))
      case RepoMethod.Delete(_, id) =>
        sig(params = List(id.param), implicitParams = List(c), returnType = TypesScala.Boolean)
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        sig(params = List(idsParam), implicitParams = List(c), returnType = TypesScala.Int)
      case RepoMethod.SqlFile(sqlScript) =>
        val params = sqlScript.params.map(p => jvm.Param(p.name, p.tpe))
        val retType = sqlScript.maybeRowName match {
          case MaybeReturnsRows.Query(rowName) => TypesScala.List.of(rowName)
          case MaybeReturnsRows.Update         => TypesScala.Int
        }
        sig(params = params, implicitParams = List(c), returnType = retType)
    }
  }

  override def repoImpl(repoMethod: RepoMethod): jvm.Body =
    repoMethod match {
      case RepoMethod.SelectBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"""${lang.dsl.SelectBuilder}.of(${jvm.StrLit(relName.quotedValue)}, $fieldsType.structure, $rowType.rowParser)""")
      case RepoMethod.SelectAll(relName, cols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(cols, isRead = true)}
                 |from $relName
                 |""".stripMargin
        }
        jvm.Body.Expr(code"""$sql.as(${rowParserFor(rowType)}.*)""")

      case RepoMethod.SelectById(relName, cols, id, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(cols, isRead = true)}
                 |from $relName
                 |where ${matchId(id)}
                 |""".stripMargin
        }
        jvm.Body.Expr(code"""$sql.as(${rowParserFor(rowType)}.singleOpt)""")

      case RepoMethod.SelectByIds(relName, cols, computedId, idsParam, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        computedId match {
          case x: IdComputed.Composite =>
            val vals = x.cols.map(col => code"val ${col.name} = ${idsParam.name}.map(_.${col.name})")
            val sql = SQL {
              code"""|select ${joinedColNames}
                     |from $relName
                     |where (${x.cols.map(col => col.dbCol.name.code).mkCode(", ")})
                     |in (select ${x.cols.map(col => code"unnest(${runtimeInterpolateValue(col.name, jvm.Type.ArrayOf(col.tpe))})").mkCode(", ")})
                     |""".stripMargin
            }
            jvm.Body.Stmts(vals.toList ++ List(code"$sql.as(${rowParserFor(rowType)}.*)"))

          case x: IdComputed.Unary =>
            val sql = SQL {
              code"""|select ${joinedColNames}
                     |from $relName
                     |where ${x.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, idsParam.tpe)})
                     |""".stripMargin
            }

            jvm.Body.Expr(code"$sql.as(${rowParserFor(rowType)}.*)")

        }
      case RepoMethod.SelectByIdsTracked(x) =>
        jvm.Body.Stmts(
          List(
            code"val byId = ${x.methodName}(${x.idsParam.name}).view.map(x => (x.${x.idComputed.paramName}, x)).toMap",
            code"${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap"
          )
        )

      case RepoMethod.UpdateBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"${lang.dsl.UpdateBuilder}.of(${jvm.StrLit(relName.quotedValue)}, $fieldsType.structure, $rowType.rowParser(1).*)")

      case RepoMethod.SelectByUnique(relName, keyColumns, allCols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(allCols, isRead = true)}
                 |from $relName
                 |where ${keyColumns.map(c => code"${c.dbName.code} = ${runtimeInterpolateValue(c.name, c.tpe)}").mkCode(" AND ")}
                 |""".stripMargin
        }
        jvm.Body.Expr(code"$sql.as(${rowParserFor(rowType)}.singleOpt)")

      case RepoMethod.SelectByFieldValues(relName, cols, fieldValue, fieldValueOrIdsParam, rowType) =>
        val cases: NonEmptyList[jvm.Code] =
          cols.map { col =>
            code"case $fieldValue.${col.name}(value) => $NamedParameter(${jvm.StrLit(col.dbName.value)}, $ParameterValue(value, null, ${lookupToStatementFor(col.tpe)}))"
          }

        val sql = lang.s {
          code"""|select ${dbNames(cols, isRead = true)}
                 |from $relName
                 |where $${namedParameters.map(x => s"$$quote$${x.name}$$quote = {$${x.name}}").mkString(" AND ")}
                 |""".stripMargin
        }
        // the weird block and wildcard import is to avoid warnings in scala 2 and 3, and to get the implicit `on` in scala 3
        jvm.Body.Expr(
          code"""|${fieldValueOrIdsParam.name} match {
                 |  case Nil => selectAll
                 |  case nonEmpty =>
                 |    val namedParameters = nonEmpty.map{
                 |      ${cases.mkCode("\n")}
                 |    }
                 |    val quote = '"'.toString
                 |    val q = $sql
                 |    $SimpleSql($SQL(q), namedParameters.map(_.tupled).toMap, $RowParser.successful)
                 |      .as(${rowParserFor(rowType)}.*)
                 |}""".stripMargin
        )

      case RepoMethod.UpdateFieldValues(relName, id, varargs, fieldValue, cases0, _) =>
        val cases: NonEmptyList[jvm.Code] =
          cases0.map { col =>
            code"case $fieldValue.${col.name}(value) => $NamedParameter(${jvm.StrLit(col.dbName.value)}, $ParameterValue(value, null, ${lookupToStatementFor(col.tpe)}))"
          }
        val where: jvm.Code =
          id.cols.map { col => code"${col.dbName.code} = {${col.name}}" }.mkCode(" AND ")

        val idCases: NonEmptyList[jvm.Code] =
          id match {
            case unary: IdComputed.Unary =>
              NonEmptyList(code"(${jvm.StrLit(unary.col.dbName.value)}, $ParameterValue(${id.paramName}, null, ${lookupToStatementFor(id.tpe)}))")
            case IdComputed.Composite(cols, _, paramName) =>
              cols.map { col =>
                code"(${jvm.StrLit(col.dbName.value)}, $ParameterValue($paramName.${col.name}, null, ${lookupToStatementFor(col.tpe)}))"
              }
          }

        val sql = lang.s {
          code"""update $relName
                |set $${namedParameters.map(x => s"$$quote$${x.name}$$quote = {$${x.name}}").mkString(", ")}
                |where $where
                |""".stripMargin
        }

        // the weird block and wildcard import is to avoid warnings in scala 2 and 3, and to get the implicit `on` in scala 3
        jvm.Body.Expr(
          code"""|${varargs.name} match {
                 |  case Nil => false
                 |  case nonEmpty =>
                 |    val namedParameters = nonEmpty.map{
                 |      ${cases.mkCode("\n")}
                 |    }
                 |    val quote = '"'.toString
                 |    val q = $sql
                 |    $SimpleSql($SQL(q), namedParameters.map(_.tupled).toMap ++ ${TypesScala.List}(${idCases.mkCode(", ")}), $RowParser.successful)
                 |      .executeUpdate() > 0
                 |}""".stripMargin
        )
      case RepoMethod.Update(relName, cols, id, param, writeableColumnsNotId) =>
        val sql = SQL {
          val setCols = writeableColumnsNotId.map { col =>
            code"${col.dbName.code} = ${runtimeInterpolateValue(code"${param.name}.${col.name}", col.tpe)}${sqlCast.toPgCode(col)}"
          }
          code"""|update $relName
                 |set ${setCols.mkCode(",\n")}
                 |where ${matchId(id)}
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        jvm.Body.Stmts(
          List(
            code"val ${id.paramName} = ${param.name}.${id.paramName}",
            code"$sql.executeInsert(${rowParserFor(param.tpe)}.singleOpt)"
          )
        )

      case RepoMethod.Insert(relName, cols, _, unsavedParam, writeableColumnsWithId, returningStrategy) =>
        val rowType = returningStrategy.returnType
        val values = writeableColumnsWithId.map { c =>
          runtimeInterpolateValue(code"${unsavedParam.name}.${c.name}", c.tpe).code ++ sqlCast.toPgCode(c)
        }
        val sql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${values.mkCode(", ")})
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        jvm.Body.Expr(
          code"""|$sql
                 |  .executeInsert(${rowParserFor(rowType)}.single)"""
        )
      case RepoMethod.Upsert(relName, cols, id, unsavedParam, rowType, writeableColumnsWithId) =>
        val writeableColumnsNotId = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))

        val values = writeableColumnsWithId.map { c =>
          runtimeInterpolateValue(code"${unsavedParam.name}.${c.name}", c.tpe).code ++ sqlCast.toPgCode(c)
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
        jvm.Body.Expr(
          code"""|$sql
                 |  .executeInsert(${rowParserFor(rowType)}.single)"""
        )
      case RepoMethod.UpsertBatch(relName, cols, id, rowType, writeableColumnsWithId) =>
        val writeableColumnsNotId = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))

        val conflictAction = writeableColumnsNotId match {
          case Nil => code"do nothing"
          case nonEmpty =>
            code"""|do update set
                   |  ${nonEmpty.map { c => code"${c.dbName.code} = EXCLUDED.${c.dbName.code}" }.mkCode(",\n")}""".stripMargin
        }

        val sql = lang.s {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${writeableColumnsWithId.map(c => code"{${c.dbName.value}}${sqlCast.toPgCode(c)}").mkCode(", ")})
                 |on conflict (${dbNames(id.cols, isRead = false)})
                 |$conflictAction
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        val toNamedParam =
          code"""|def toNamedParameter(row: $rowType): ${TypesScala.List.of(NamedParameter)} = ${TypesScala.List}(
                 |  ${writeableColumnsWithId.map(c => code"$NamedParameter(${jvm.StrLit(c.dbName.value)}, $ParameterValue(row.${c.name}, null, ${lookupToStatementFor(c.tpe)}))").mkCode(",\n")}
                 |)
                 |""".stripMargin
        val ret =
          code"""|unsaved.toList match {
                 |  case Nil => ${TypesScala.Nil}
                 |  case head :: rest =>
                 |    new $ExecuteReturningSyntax.Ops(
                 |      $BatchSql(
                 |        $sql,
                 |        toNamedParameter(head),
                 |        rest.map(toNamedParameter)*
                 |      )
                 |    ).executeReturning(${rowParserFor(rowType)}.*)
                 |}""".stripMargin
        jvm.Body.Stmts(List(toNamedParam, ret))
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
            code"${SQL(code"create temporary table $tempTablename (like $relName) on commit drop")}.execute(): @${TypesScala.nowarn}",
            code"${textSupport.get.streamingInsert}($copySql, batchSize, unsaved)(${dialect.usingCall}${textSupport.get.lookupTextFor(rowType)}, c): @${TypesScala.nowarn}",
            code"$mergeSql.executeUpdate()"
          )
        )

      case RepoMethod.InsertUnsaved(relName, cols, unsaved, unsavedParam, _, default, returningStrategy) =>
        val rowType = returningStrategy.returnType
        val cases0 = unsaved.normalColumns.map { col =>
          val colCast = jvm.StrLit(sqlCast.toPg(col.dbCol).fold("")(_.withColons))
          code"""Some(($NamedParameter(${jvm.StrLit(col.dbName.value)}, $ParameterValue(${unsavedParam.name}.${col.name}, null, ${lookupToStatementFor(col.tpe)})), $colCast))"""
        }
        val cases1 = unsaved.defaultedCols.map { case ComputedRowUnsaved.DefaultedCol(col @ ComputedColumn(_, ident, dbCol, _), origType, _) =>
          val dbName = jvm.StrLit(dbCol.name.value)
          val colCast = jvm.StrLit(sqlCast.toPg(col.dbCol).fold("")(_.withColons))

          code"""|${unsavedParam.name}.$ident match {
                 |  case ${default.Defaulted}.${default.UseDefault}() => None
                 |  case ${default.Defaulted}.${default.Provided}(value) => Some(($NamedParameter($dbName, $ParameterValue(value, null, ${lookupToStatementFor(origType)})), $colCast))
                 |}"""
        }

        val sql = lang.s {
          code"""|insert into $relName($${namedParameters.map{case (x, _) => quote + x.name + quote}.mkString(", ")})
                 |values ($${namedParameters.map{ case (np, cast) => s"{$${np.name}}$$cast"}.mkString(", ")})
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        val sqlEmpty = SQL {
          code"""|insert into $relName default values
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }

        // the weird block and wildcard import is to avoid warnings in scala 2 and 3, and to get the implicit `on` in scala 3
        jvm.Body.Stmts(
          List(
            code"""|val namedParameters = List(
                 |  ${(cases0 ++ cases1.toList).mkCode(",\n")}
                 |).flatten
                 |val quote = '"'.toString
                 |if (namedParameters.isEmpty) {
                 |  $sqlEmpty
                 |    .executeInsert(${rowParserFor(rowType)}.single)
                 |} else {
                 |  val q = $sql
                 |  $SimpleSql($SQL(q), namedParameters.map { case (np, _) => np.tupled }.toMap, $RowParser.successful)
                 |    .executeInsert(${rowParserFor(rowType)}.single)
                 |}
                 |""".stripMargin
          )
        )
      case RepoMethod.InsertStreaming(relName, rowType, writeableColumnsWithId) =>
        val sql = lang.s(code"COPY $relName(${dbNames(writeableColumnsWithId, isRead = false)}) FROM STDIN")
        jvm.Body.Expr(code"${textSupport.get.streamingInsert}($sql, batchSize, unsaved)(${dialect.usingCall}${textSupport.get.lookupTextFor(rowType)}, c)")
      case RepoMethod.InsertUnsavedStreaming(relName, unsaved) =>
        val sql = lang.s(code"COPY $relName(${dbNames(unsaved.unsavedCols, isRead = false)}) FROM STDIN (DEFAULT '${DbLibTextSupport.DefaultValue}')")
        jvm.Body.Expr(code"${textSupport.get.streamingInsert}($sql, batchSize, unsaved)(${dialect.usingCall}${textSupport.get.lookupTextFor(unsaved.tpe)}, c)")

      case RepoMethod.DeleteBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"${lang.dsl.DeleteBuilder}.of(${jvm.StrLit(relName.quotedValue)}, $fieldsType.structure, $rowType.rowParser(1).*)")
      case RepoMethod.Delete(relName, id) =>
        val sql = SQL {
          code"""delete from $relName where ${matchId(id)}"""
        }
        jvm.Body.Expr(code"$sql.executeUpdate() > 0")
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
            jvm.Body.Stmts(vals.toList ++ List(code"$sql.executeUpdate()"))

          case x: IdComputed.Unary =>
            val sql = SQL {
              code"""|delete
                     |from $relName
                     |where ${x.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, jvm.Type.ArrayOf(x.tpe))})
                     |""".stripMargin
            }
            jvm.Body.Expr(code"$sql.executeUpdate()")
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
                 |  ${SQL(renderedWithCasts)}
                 |sql.as(${rowParserFor(rowName)}.*)""".stripMargin
            )
          )
        }
        (ret.getOrElse(jvm.Body.Expr(code"${SQL(renderedScript)}.executeUpdate()")))
    }

  override def mockRepoImpl(id: IdComputed, repoMethod: RepoMethod, maybeToRow: Option[jvm.Param[jvm.Type.Function1]]): jvm.Body =
    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, _) =>
        jvm.Body.Expr(code"${lang.dsl.SelectBuilderMock}($fieldsType.structure, () => map.values.toList, ${lang.dsl.SelectParams}.empty)")
      case RepoMethod.SelectAll(_, _, _) =>
        jvm.Body.Expr(code"map.values.toList")
      case RepoMethod.SelectById(_, _, id, _) =>
        jvm.Body.Expr(code"map.get(${id.paramName})")
      case RepoMethod.SelectByIds(_, _, _, idsParam, _) =>
        jvm.Body.Expr(code"${idsParam.name}.flatMap(map.get).toList")
      case RepoMethod.SelectByIdsTracked(x) =>
        jvm.Body.Stmts(
          List(code"""|val byId = ${x.methodName}(${x.idsParam.name}).view.map(x => (x.${x.idComputed.paramName}, x)).toMap
               |${x.idsParam.name}.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap""".stripMargin)
        )
      case RepoMethod.SelectByUnique(_, keyColumns, _, _) =>
        jvm.Body.Expr(code"map.values.find(v => ${keyColumns.map(c => code"${c.name} == v.${c.name}").mkCode(" && ")})")

      case RepoMethod.SelectByFieldValues(_, cols, fieldValue, fieldValueOrIdsParam, _) =>
        val cases = cols.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.filter(_.${col.name} == value)"
        }
        jvm.Body.Expr(code"""${fieldValueOrIdsParam.name}.foldLeft(map.values) {
              |  ${cases.mkCode("\n")}
              |}.toList""".stripMargin)
      case RepoMethod.UpdateFieldValues(_, _, varargs, fieldValue, cases0, _) =>
        val cases = cases0.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.copy(${col.name} = value)"
        }

        jvm.Body.Expr(code"""|map.get(${id.paramName}) match {
               |  case ${TypesScala.Some}(oldRow) =>
               |    val updatedRow = ${varargs.name}.foldLeft(oldRow) {
               |      ${cases.mkCode("\n")}
               |    }
               |    if (updatedRow != oldRow) {
               |      map.put(${id.paramName}, updatedRow): @${TypesScala.nowarn}
               |      true
               |    } else {
               |      false
               |    }
               |  case ${TypesScala.None} => false
               |}""".stripMargin)
      case RepoMethod.UpdateBuilder(_, fieldsType, _) =>
        jvm.Body.Expr(code"${lang.dsl.UpdateBuilderMock}(${lang.dsl.UpdateParams}.empty, $fieldsType.structure, map)")
      case RepoMethod.Update(_, _, _, param, _) =>
        jvm.Body.Expr(code"""|map.get(${param.name}.${id.paramName}).map { _ =>
               |  map.put(${param.name}.${id.paramName}, ${param.name}): @${TypesScala.nowarn}
               |  ${param.name}
               |}""".stripMargin)
      case RepoMethod.Insert(_, _, _, unsavedParam, _, _) =>
        jvm.Body.Stmts(
          List(
            code"""|val _ = if (map.contains(${unsavedParam.name}.${id.paramName}))
               |  sys.error(s"id $${${unsavedParam.name}.${id.paramName}} already exists")
               |else
               |  map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name})
               |
               |${unsavedParam.name}""".stripMargin
          )
        )
      case RepoMethod.Upsert(_, _, _, unsavedParam, _, _) =>
        jvm.Body.Expr(
          code"""|map.put(${unsavedParam.name}.${id.paramName}, ${unsavedParam.name}): @${TypesScala.nowarn}
               |${unsavedParam.name}""".stripMargin
        )
      case RepoMethod.UpsertStreaming(_, id, _, _) =>
        jvm.Body.Stmts(
          List(
            code"""|unsaved.foreach { row =>
               |  map += (row.${id.paramName} -> row)
               |}
               |unsaved.size""".stripMargin
          )
        )
      case RepoMethod.UpsertBatch(_, _, id, _, _) =>
        jvm.Body.Expr(code"""|unsaved.map { row =>
               |  map += (row.${id.paramName} -> row)
               |  row
               |}.toList""".stripMargin)
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _, _) =>
        jvm.Body.Expr(code"insert(${maybeToRow.get.name}(${unsavedParam.name}))")
      case RepoMethod.InsertStreaming(_, _, _) =>
        jvm.Body.Expr(code"""|unsaved.foreach { row =>
               |  map += (row.${id.paramName} -> row)
               |}
               |unsaved.size.toLong""".stripMargin)
      case RepoMethod.InsertUnsavedStreaming(_, _) =>
        jvm.Body.Stmts(
          List(code"""|unsaved.foreach { unsavedRow =>
               |  val row = ${maybeToRow.get.name}(unsavedRow)
               |  map += (row.${id.paramName} -> row)
               |}
               |unsaved.size.toLong""".stripMargin)
        )
      case RepoMethod.DeleteBuilder(_, fieldsType, _) =>
        jvm.Body.Expr(code"${lang.dsl.DeleteBuilderMock}(${lang.dsl.DeleteParams}.empty, $fieldsType.structure, map)")
      case RepoMethod.Delete(_, id) =>
        jvm.Body.Expr(code"map.remove(${id.paramName}).isDefined")
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        jvm.Body.Expr(code"${idsParam.name}.map(id => map.remove(id)).count(_.isDefined)")
      case RepoMethod.SqlFile(_) =>
        // should not happen (tm)
        jvm.Body.Expr(code"???")
    }

  override def testInsertMethod(x: ComputedTestInserts.InsertMethod): jvm.Method =
    jvm.Method(
      Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = x.name,
      params = x.params,
      implicitParams = List(c),
      tpe = x.table.names.RowName,
      throws = Nil,
      body = jvm.Body.Expr(code"(new ${x.table.names.RepoImplName}).insert(new ${x.cls}(${x.values.map { case (p, expr) => code"$p = $expr" }.mkCode(", ")}))"),
      isOverride = false,
      isDefault = false
    )

  override val defaultedInstance: List[jvm.Given] =
    textSupport.map(_.defaultedInstance).toList

  override def stringEnumInstances(wrapperType: jvm.Type, underlyingTypoType: TypoType, sqlType: String, openEnum: Boolean): List[jvm.Given] = {
    val underlying = underlyingTypoType.jvmType
    val sqlTypeLit = jvm.StrLit(sqlType)
    val arrayWrapper = jvm.Type.ArrayOf(wrapperType)
    List(
      Some(
        jvm.Given(
          tparams = Nil,
          name = arrayColumnName,
          implicitParams = Nil,
          tpe = Column.of(arrayWrapper),
          body =
            if (openEnum) code"${lookupColumnFor(jvm.Type.ArrayOf(underlying))}.map(_.map($wrapperType.apply))"
            else code"${lookupColumnFor(jvm.Type.ArrayOf(underlying))}.map(_.map($wrapperType.force))"
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = columnName,
          implicitParams = Nil,
          tpe = Column.of(wrapperType),
          body =
            if (openEnum) code"${lookupColumnFor(underlying)}.map($wrapperType.apply)"
            else code"${lookupColumnFor(underlying)}.mapResult(str => $wrapperType(str).left.map($SqlMappingError.apply))"
        )
      ),
      Some(
        jvm.Given(tparams = Nil, name = toStatementName, implicitParams = Nil, tpe = ToStatement.of(wrapperType), body = code"${lookupToStatementFor(underlying)}.contramap(_.value)")
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = arrayToStatementName,
          implicitParams = Nil,
          tpe = ToStatement.of(arrayWrapper),
          body =
            if (openEnum) code"${lookupToStatementFor(jvm.Type.ArrayOf(underlying))}.contramap(_.map(_.value))"
            else code"$ToStatement[$arrayWrapper]((ps, i, arr) => ps.setArray(i, ps.getConnection.createArrayOf($sqlTypeLit, arr.map[AnyRef](_.value))))"
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = parameterMetadataName,
          implicitParams = Nil,
          tpe = ParameterMetaData.of(wrapperType),
          body = code"""|new $ParameterMetaData[$wrapperType] {
                        |  override def sqlType: ${TypesJava.String} = $sqlTypeLit
                        |  override def jdbcType: ${TypesScala.Int} = ${TypesJava.SqlTypes}.OTHER
                        |}""".stripMargin
        )
      ),
      textSupport.map(_.anyValInstance(wrapperType, underlying))
    ).flatten
  }

  override def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, underlyingJvmType: jvm.Type, underlyingDbType: db.Type, overrideDbType: Option[String]): List[jvm.Given] =
    List(
      Some(
        jvm.Given(
          tparams = Nil,
          name = toStatementName,
          implicitParams = Nil,
          tpe = ToStatement.of(wrapperType),
          body = code"${lookupToStatementFor(underlyingJvmType)}.contramap(_.value)"
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = arrayToStatementName,
          implicitParams = Nil,
          tpe = ToStatement.of(jvm.Type.ArrayOf(wrapperType)),
          body = code"${lookupToStatementFor(jvm.Type.ArrayOf(underlyingJvmType))}.contramap(_.map(_.value))"
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = arrayColumnName,
          implicitParams = Nil,
          tpe = Column.of(jvm.Type.ArrayOf(wrapperType)),
          body = code"$Column.columnToArray(${dialect.usingCall}$columnName, implicitly)"
        )
      ),
      Some(
        jvm.Given(tparams = Nil, name = columnName, implicitParams = Nil, tpe = Column.of(wrapperType), body = code"${lookupColumnFor(underlyingJvmType)}.map($wrapperType.apply)")
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = parameterMetadataName,
          implicitParams = Nil,
          tpe = ParameterMetaData.of(wrapperType),
          body = overrideDbType match {
            case Some(dbType) =>
              code"""|new ${ParameterMetaData.of(wrapperType)} {
                       |  override def sqlType: String = ${jvm.StrLit(dbType)}
                       |  override def jdbcType: Int = ${TypesJava.SqlTypes}.OTHER
                       |}""".stripMargin
            case None =>
              code"""|new ${ParameterMetaData.of(wrapperType)} {
                     |  override def sqlType: String = ${lookupParameterMetaDataFor(underlyingJvmType)}.sqlType
                     |  override def jdbcType: Int = ${lookupParameterMetaDataFor(underlyingJvmType)}.jdbcType
                     |}""".stripMargin
          }
        )
      ),
      textSupport.map(_.anyValInstance(wrapperType, underlyingJvmType))
    ).flatten

  override val missingInstances: List[jvm.ClassMember] = {
    val arrayInstances = List[(jvm.Type.Qualified, jvm.StrLit)](
      (TypesScala.Float, jvm.StrLit("float4")),
      (TypesScala.Short, jvm.StrLit("int2")),
      (TypesScala.Int, jvm.StrLit("int4")),
      (TypesScala.Long, jvm.StrLit("int8")),
      (TypesScala.Boolean, jvm.StrLit("bool")),
      (TypesScala.Double, jvm.StrLit("float8"))
    ).flatMap { case (tpe, elemType) =>
      val arrayType = jvm.Type.ArrayOf(tpe)
      val boxedType = TypesScala.boxedType(tpe).getOrElse(TypesScala.AnyRef)
      List(
        jvm.Given(
          Nil,
          tpe.value.name.appended("ArrayToStatement"),
          Nil,
          ToStatement.of(arrayType),
          code"${ToStatement.of(arrayType)}((ps, index, v) => ps.setArray(index, ps.getConnection.createArrayOf($elemType, v.map(v => v: $boxedType))))"
        )
      )
    }
    val bigDecimalArrayToStatement =
      jvm.Given(
        Nil,
        jvm.Ident("BigDecimalArrayToStatement"),
        Nil,
        ToStatement.of(jvm.Type.ArrayOf(TypesScala.BigDecimal)),
        code"${ToStatement.of(jvm.Type.ArrayOf(TypesScala.BigDecimal))}((ps, index, v) => ps.setArray(index, ps.getConnection.createArrayOf(${jvm.StrLit("numeric")}, v.map(v => v.bigDecimal))))"
      )

    val arrayParameterMetaData = {
      val T = jvm.Type.Abstract(jvm.Ident("T"))
      jvm.Given(
        List(T),
        arrayParameterMetaDataName,
        List(jvm.Param(T.value, ParameterMetaData.of(T))),
        ParameterMetaData.of(jvm.Type.ArrayOf(T)),
        code"""|new ${ParameterMetaData.of(jvm.Type.ArrayOf(T))} {
               |  override def sqlType: ${TypesJava.String} =
               |    $T.sqlType match {
               |      case "INTEGER" => "int4[]"
               |      case "FLOAT" => "float4[]"
               |      case "DOUBLE PRECISION" => "float8[]"
               |      case "DECIMAL" => "float8[]"
               |      case "VARCHAR" => "text[]"
               |      case other => s"$${other}[]"
               |    }
               |
               |  override def jdbcType: ${TypesScala.Int} = ${TypesJava.SqlTypes}.ARRAY
               |}""".stripMargin
      )
    }

    arrayInstances ++ List(arrayParameterMetaData, bigDecimalArrayToStatement)
  }

  /** Oracle STRUCT types - not supported in PostgreSQL */
  override def structInstances(computed: ComputedOracleObjectType): List[jvm.ClassMember] = Nil

  /** Oracle COLLECTION types - not supported in PostgreSQL */
  override def collectionInstances(computed: ComputedOracleCollectionType): List[jvm.ClassMember] = Nil

  val missingInstancesByType: Map[jvm.Type, jvm.QIdent] =
    missingInstances.collect { case x: jvm.Given => (x.tpe, pkg / x.name) }.toMap

  override def rowInstances(tpe: jvm.Type, cols: NonEmptyList[ComputedColumn], rowType: DbLib.RowType): List[jvm.ClassMember] = {
    val text = textSupport.map(_.rowInstance(tpe, cols))
    val rowParser = {
      val mappedValues = cols.zipWithIndex.map { case (x, num) =>
        dialect match {
          case Dialect.Scala2XSource3 => code"${x.name} = row(idx + $num)(${lookupColumnFor(x.tpe)})"
          case Dialect.Scala3         => code"${x.name} = row(idx + $num)(using ${lookupColumnFor(x.tpe)})"
        }
      }
      jvm.Method(
        Nil,
        comments = jvm.Comments.Empty,
        Nil,
        rowParserName,
        params = List(jvm.Param(jvm.Ident("idx"), TypesScala.Int)),
        Nil,
        RowParser.of(tpe),
        Nil,
        jvm.Body.Expr {
          code"""|${RowParser.of(tpe)} { row =>
                 |  $Success(
                 |    $tpe(
                 |      ${mappedValues.mkCode(",\n")}
                 |    )
                 |  )
                 |}""".stripMargin
        },
        isOverride = false,
        isDefault = false
      )
    }
    rowType match {
      case DbLib.RowType.Writable      => text.toList
      case DbLib.RowType.ReadWriteable => List(rowParser) ++ text
      case DbLib.RowType.Readable      => List(rowParser)
    }
  }

  override def customTypeInstances(ct: CustomType): List[jvm.Given] = {
    val tpe = ct.typoType
    val v = jvm.Ident("v")
    val normal = List(
      Some(
        jvm.Given(
          Nil,
          toStatementName,
          Nil,
          ToStatement.of(tpe),
          code"${ToStatement.of(tpe)}((s, index, v) => s.setObject(index, ${ct.fromTypo0(v)}))"
        )
      ),
      Some(
        jvm.Given(
          Nil,
          parameterMetadataName,
          Nil,
          ParameterMetaData.of(tpe),
          code"""|new ${ParameterMetaData.of(tpe)} {
               |  override def sqlType: ${TypesJava.String} = ${jvm.StrLit(ct.sqlType)}
               |  override def jdbcType: ${TypesScala.Int} = ${TypesJava.SqlTypes}.OTHER
               |}""".stripMargin
        )
      ),
      Some(
        jvm.Given(
          Nil,
          columnName,
          Nil,
          Column.of(tpe),
          code"""|$Column.nonNull[$tpe]((v1: ${TypesScala.Any}, _) =>
               |  v1 match {
               |    case $v: ${ct.toTypo.jdbcType} => ${TypesScala.Right}(${ct.toTypo0(v)})
               |    case other => ${TypesScala.Left}($TypeDoesNotMatch(s"Expected instance of ${ct.toTypo.jdbcType.render(lang).asString}, got $${other.getClass.getName}"))
               |  }
               |)""".stripMargin
        )
      ),
      textSupport.map(_.customTypeInstance(ct))
    ).flatten

    val array =
      if (ct.forbidArray) Nil
      else {
        val fromTypo = ct.fromTypoInArray.getOrElse(ct.fromTypo)
        val toTypo = ct.toTypoInArray.getOrElse(ct.toTypo)

        List(
          jvm.Given(
            Nil,
            arrayToStatementName,
            Nil,
            ToStatement.of(jvm.Type.ArrayOf(tpe)),
            code"${ToStatement.of(jvm.Type.ArrayOf(tpe))}((s, index, v) => s.setArray(index, s.getConnection.createArrayOf(${jvm.StrLit(ct.sqlType)}, $v.map(v => ${fromTypo.fromTypo0(v)}))))"
          ),
          jvm.Given(
            Nil,
            arrayColumnName,
            Nil,
            Column.of(jvm.Type.ArrayOf(tpe)),
            code"""|$Column.nonNull[${jvm.Type.ArrayOf(tpe)}]((v1: ${TypesScala.Any}, _) =>
                 |  v1 match {
                 |      case $v: ${TypesJava.PgArray} =>
                 |       $v.getArray match {
                 |         case $v: ${jvm.Type.ArrayOf(jvm.Type.Wildcard)} =>
                 |           ${TypesScala.Right}($v.map($v => ${toTypo.toTypo(code"$v.asInstanceOf[${toTypo.jdbcType}]", ct.typoType)}))
                 |         case other => ${TypesScala.Left}($TypeDoesNotMatch(s"Expected one-dimensional array from JDBC to produce an array of ${ct.typoType}, got $${other.getClass.getName}"))
                 |       }
                 |    case other => ${TypesScala.Left}($TypeDoesNotMatch(s"Expected instance of ${TypesJava.PgArray.render(lang).asString}, got $${other.getClass.getName}"))
                 |  }
                 |)""".stripMargin
          )
        )
      }
    normal ++ array
  }
}
