package typo
package internal
package codegen

import typo.internal.analysis.MaybeReturnsRows
import typo.jvm.Code.TreeOps

class DbLibTypo(pkg: jvm.QIdent, override val lang: Lang, default: ComputedDefault, enableStreamingInserts: Boolean) extends DbLib {
  val Fragment = jvm.Type.Qualified("typo.runtime.Fragment")
  val FragmentLiteral = Fragment / jvm.Ident("Literal")
  val FragmentInterpolated = Fragment
  val PgText = jvm.Type.Qualified("typo.runtime.PgText")
  val PgType = jvm.Type.Qualified("typo.runtime.PgType")
  val PgTypes = jvm.Type.Qualified("typo.runtime.PgTypes")
  val RowParser = jvm.Type.Qualified("typo.runtime.RowParser")
  val RowParsers = jvm.Type.Qualified("typo.runtime.RowParsers")
  val streamingInsert = jvm.Type.Qualified("typo.runtime.streamingInsert")
  def Tuple(n: Int) = RowParsers / jvm.Ident(s"Tuple${n}")
  val DefaultValue = "__DEFAULT_VALUE__"
  // For Java, use Fragment.interpolate (static method)
  // For Scala, use FragmentInterpolator.interpolate (extension method)
  val SqlStringInterpolation: jvm.Type.Qualified = lang match {
    case _: LangScala => jvm.Type.Qualified("typo.runtime.FragmentInterpolator")
    case LangJava     => jvm.Type.Qualified("typo.runtime.Fragment")
    case _            => ???
  }
  val BatchSql = jvm.Type.Qualified("anorm.BatchSql")
  val Column = jvm.Type.Qualified("anorm.Column")
  val ToStatement = jvm.Type.Qualified("anorm.ToStatement")
  val ToSql = jvm.Type.Qualified("anorm.ToSql")
  val NamedParameter = jvm.Type.Qualified("anorm.NamedParameter")
  val ParameterValue = jvm.Type.Qualified("anorm.ParameterValue")
  val ToParameterValue = jvm.Type.Qualified("anorm.ToParameterValue")
  val Success = jvm.Type.Qualified("anorm.Success")
  val SqlMappingError = jvm.Type.Qualified("anorm.SqlMappingError")
  val ParameterMetaData = jvm.Type.Qualified("anorm.ParameterMetaData")
  val SQL = jvm.Type.Qualified("anorm.SQL")
  val TypeDoesNotMatch = jvm.Type.Qualified("anorm.TypeDoesNotMatch")
  val SimpleSql = jvm.Type.Qualified("anorm.SimpleSql")
  val Row = jvm.Type.Qualified("anorm.Row")
  val managed = jvm.Type.Qualified("resource.managed")

  def rowParserFor(rowType: jvm.Type) = code"$rowType.$rowParserName"

  def SQL(content: jvm.Code) = jvm.StringInterpolate(SqlStringInterpolation / jvm.Ident("interpolate"), jvm.Ident("interpolate"), content)
  def FR(content: jvm.Code) = SQL(content)

  val pgTypeName = jvm.Ident("pgType")
  val pgTypeArrayName = jvm.Ident("pgTypeArray")
  val rowParserName: jvm.Ident = jvm.Ident("_rowParser")

  val arrayColumnName = jvm.Ident("arrayColumn")
  val arrayToStatementName: jvm.Ident = jvm.Ident("arrayToStatement")
  val columnName: jvm.Ident = jvm.Ident("column")
  val parameterMetadataName: jvm.Ident = jvm.Ident("parameterMetadata")
  val toStatementName: jvm.Ident = jvm.Ident("toStatement")
  val textSupport: Option[DbLibTextSupport] = None
  val ExecuteReturningSyntax = jvm.QIdent(List[List[jvm.Ident]](List(jvm.Ident("anorm")), pkg.idents, List(jvm.Ident("ExecuteReturningSyntax"))).flatten)

  val arrayMap = jvm.Type.Qualified("typo.runtime.internal.arrayMap")

  def mapArray(arr: jvm.Code, mapElem: jvm.Code => jvm.Code, toType: jvm.Type) = {
    val x = jvm.Ident("x")
    lang.arrayMap(arr, jvm.Lambda1(x, mapElem(x)).code, code"$toType.class")
  }

  override val additionalFiles: List[typo.jvm.File] = Nil

  def runtimeInterpolateValue(name: jvm.Code, tpe: jvm.Type): jvm.Code =
    jvm.RuntimeInterpolation(code"${lookupPgType(tpe)}.encode($name)")

  def dbNames(cols: NonEmptyList[ComputedColumn], isRead: Boolean): jvm.Code =
    cols
      .map(c => c.dbName.code ++ (if (isRead) SqlCast.fromPgCode(c) else jvm.Code.Empty))
      .mkCode(", ")

  def matchId(id: IdComputed): jvm.Code =
    id match {
      case id: IdComputed.Unary =>
        code"${id.col.dbName.code} = ${runtimeInterpolateValue(id.paramName, id.tpe)}"
      case composite: IdComputed.Composite =>
        composite.cols.map(cc => code"${cc.dbName.code} = ${runtimeInterpolateValue(code"${jvm.ApplyNullary(composite.paramName, cc.name)}", cc.tpe)}").mkCode(" AND ")
    }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupPgType(tpe: jvm.Type): jvm.Code =
    jvm.Type.base(tpe) match {
      case TypesJava.BigDecimal        => code"$PgTypes.numeric"
      case TypesJava.Boolean           => code"$PgTypes.bool"
      case TypesJava.Double            => code"$PgTypes.float8"
      case TypesJava.Float             => code"$PgTypes.float4"
      case TypesJava.Short             => code"$PgTypes.int2"
      case TypesJava.Integer           => code"$PgTypes.int4"
      case TypesJava.Long              => code"$PgTypes.int8"
      case TypesJava.String            => code"$PgTypes.text"
      case TypesJava.UUID              => code"$PgTypes.uuid"
      case lang.Optional(targ)         => code"${lookupPgType(targ)}.opt()"
      case jvm.Type.ArrayOf(lang.Byte) => code"$PgTypes.bytea"
      // generated type
      case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
        code"$tpe.$pgTypeName"
      // generated array type
      case jvm.Type.ArrayOf(targ: jvm.Type.Qualified) =>
        lookupPgType(targ).code ++ code"Array"
      case other => sys.error(s"Unsupported type: $other")
    }

  override def resolveConstAs(tpe: jvm.Type): jvm.Code =
    tpe match {
      case lang.Optional(underlying) =>
        code"${jvm.Type.dsl.ConstAsAsOpt.of(underlying)}(${lookupPgType(underlying)})"
      case _ =>
        code"${jvm.Type.dsl.ConstAsAs.of(tpe)}(${lookupPgType(tpe)})"
    }

  /** Combine boolean expressions using SqlExpr.all(). Works uniformly for both Java and Scala. */
  def booleanAndChain(exprs: NonEmptyList[jvm.Code]): jvm.Code =
    code"${jvm.Type.dsl.SqlExpr}.all(${exprs.toList.mkCode(", ")})"

  /** Create CompositeInPart expression with explicit type arguments: Part<T, Tuple, Row> */
  def compositeInPart(fieldType: jvm.Type, compositeIdType: jvm.Type, rowType: jvm.Type, fieldExpr: jvm.Code, getterField: jvm.Ident, pgType: jvm.Code): jvm.Code = {
    val getterRef = jvm.FieldGetterRef(compositeIdType, getterField)
    val partType = jvm.Type.dsl.CompositeInPart.of(fieldType, compositeIdType, rowType)
    jvm.New(partType, List(jvm.Arg.Pos(fieldExpr), jvm.Arg.Pos(getterRef), jvm.Arg.Pos(pgType)))
  }

  val c: jvm.Param[jvm.Type] = jvm.Param(jvm.Ident("c"), TypesJava.Connection)

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
        body = Nil
      )
    )

    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, implicitParams = Nil, returnType = jvm.Type.dsl.SelectBuilder.of(fieldsType, rowType))
      case RepoMethod.SelectAll(_, _, rowType) =>
        sig(params = Nil, implicitParams = List(c), returnType = lang.ListType.tpe.of(rowType))
      case RepoMethod.SelectById(_, _, id, rowType) =>
        sig(params = List(id.param), implicitParams = List(c), returnType = lang.Optional.tpe.of(rowType))
      case RepoMethod.SelectByIds(_, _, _, idsParam, rowType) =>
        sig(params = List(idsParam), implicitParams = List(c), returnType = lang.ListType.tpe.of(rowType))
      case RepoMethod.SelectByIdsTracked(x) =>
        sig(params = List(x.idsParam), implicitParams = List(c), returnType = lang.MapOps.tpe.of(x.idComputed.tpe, x.rowType))
      case RepoMethod.SelectByUnique(_, keyColumns, _, rowType) =>
        sig(params = keyColumns.toList.map(_.param), implicitParams = List(c), returnType = lang.Optional.tpe.of(rowType))
      case RepoMethod.SelectByFieldValues(_, _, _, fieldValueOrIdsParam, rowType) =>
        sig(params = List(fieldValueOrIdsParam), implicitParams = List(c), returnType = lang.ListType.tpe.of(rowType))
      case RepoMethod.UpdateBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, implicitParams = Nil, returnType = jvm.Type.dsl.UpdateBuilder.of(fieldsType, rowType))
      case RepoMethod.UpdateFieldValues(_, id, varargs, _, _, _) =>
        sig(params = List(id.param, varargs), implicitParams = List(c), returnType = lang.Boolean)
      case RepoMethod.Update(_, _, _, param, _) =>
        sig(params = List(param), implicitParams = List(c), returnType = lang.Boolean)
      case RepoMethod.Insert(_, _, unsavedParam, rowType, _) =>
        sig(params = List(unsavedParam), implicitParams = List(c), returnType = rowType)
      case RepoMethod.InsertStreaming(_, rowType, _) =>
        val unsaved = jvm.Param(jvm.Ident("unsaved"), lang.IteratorType.of(rowType))
        val batchSize = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("batchSize"), lang.Int, Some(code"10000"))
        sig(params = List(unsaved, batchSize), implicitParams = List(c), returnType = lang.Long)
      case RepoMethod.Upsert(_, _, _, unsavedParam, rowType, _) =>
        sig(params = List(unsavedParam), implicitParams = List(c), returnType = rowType)
      case RepoMethod.UpsertBatch(_, _, _, rowType, _) =>
        val unsaved = jvm.Param(jvm.Ident("unsaved"), lang.IteratorType.of(rowType))
        sig(params = List(unsaved), implicitParams = List(c), returnType = lang.ListType.tpe.of(rowType))
      case RepoMethod.UpsertStreaming(_, _, rowType, _) =>
        val unsaved = jvm.Param(jvm.Ident("unsaved"), lang.IteratorType.of(rowType))
        val batchSize = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("batchSize"), lang.Int, Some(code"10000"))
        sig(params = List(unsaved, batchSize), implicitParams = List(c), returnType = lang.Int)
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, rowType) =>
        sig(params = List(unsavedParam), implicitParams = List(c), returnType = rowType)
      case RepoMethod.InsertUnsavedStreaming(_, unsaved) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), lang.IteratorType.of(unsaved.tpe))
        val batchSize = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("batchSize"), lang.Int, Some(code"10000"))
        sig(params = List(unsavedParam, batchSize), implicitParams = List(c), returnType = lang.Long)
      case RepoMethod.DeleteBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, implicitParams = Nil, returnType = jvm.Type.dsl.DeleteBuilder.of(fieldsType, rowType))
      case RepoMethod.Delete(_, id) =>
        sig(params = List(id.param), implicitParams = List(c), returnType = lang.Boolean)
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        sig(params = List(idsParam), implicitParams = List(c), returnType = lang.Int)
      case RepoMethod.SqlFile(sqlScript) =>
        val params = sqlScript.params.map(p => jvm.Param(p.name, p.tpe))
        val retType = sqlScript.maybeRowName match {
          case MaybeReturnsRows.Query(rowName) => lang.ListType.tpe.of(rowName)
          case MaybeReturnsRows.Update         => lang.Int
        }
        sig(params = params, implicitParams = List(c), returnType = retType)
    }
  }

  override def repoImpl(repoMethod: RepoMethod): List[jvm.Code] =
    repoMethod match {
      case RepoMethod.SelectBuilder(relName, fieldsType, rowType) =>
        val structure = code"$fieldsType".callNullary("structure")
        List(code"${jvm.Type.dsl.SelectBuilder}.of(${jvm.StrLit(relName.value)}, $structure, $rowType.$rowParserName)")
      case RepoMethod.SelectAll(relName, cols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(cols, isRead = true)}
                 |from $relName
                 |""".stripMargin
        }
        List(code"""$sql.as(${rowParserFor(rowType)}.all()).runUnchecked(c)""")

      case RepoMethod.SelectById(relName, cols, id, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(cols, isRead = true)}
                 |from $relName
                 |where ${matchId(id)}""".stripMargin
        }
        List(code"""$sql.as(${rowParserFor(rowType)}.first()).runUnchecked(c)""")

      case RepoMethod.SelectByIds(relName, cols, computedId, idsParam, rowType) =>
        val joinedColNames = dbNames(cols, isRead = true)
        computedId match {
          case x: IdComputed.Composite =>
            val vals =
              x.cols.map(col => jvm.Value(Nil, col.name, jvm.Type.ArrayOf(col.tpe), Some(lang.arrayMap(idsParam.name.code, jvm.FieldGetterRef(x.tpe, col.name).code, jvm.ClassOf(col.tpe).code))).code)
            // Build SQL avoiding problematic string patterns in Java text blocks
            // The issue is that Java text blocks can't have string literals containing ")
            // So we need to structure the SQL to avoid this pattern
            // Helper to get array SQL cast for a column
            def arrayCast(col: ComputedColumn): jvm.Code = {
              val baseCast = SqlCast.toPg(col.dbCol).map(_.typeName).getOrElse(col.dbCol.udtName.getOrElse(""))
              if (baseCast.nonEmpty) jvm.Code.Str(s"::${baseCast}[]") else jvm.Code.Empty
            }
            val sql = SQL {
              val colsStr = x.cols.map(_.dbCol.name.code).mkCode(", ")
              // Instead of joining with ", " which creates problematic patterns,
              // we'll build the SQL differently
              val selectClause = x.cols.toList match {
                case single :: Nil =>
                  code"select unnest(${runtimeInterpolateValue(single.name, jvm.Type.ArrayOf(single.tpe))}${arrayCast(single)})"
                case first :: rest =>
                  // Build the select clause without creating problematic string patterns
                  val firstUnnest = code"select unnest(${runtimeInterpolateValue(first.name, jvm.Type.ArrayOf(first.tpe))}${arrayCast(first)})"
                  val restUnnests = rest.map { col =>
                    code"unnest(${runtimeInterpolateValue(col.name, jvm.Type.ArrayOf(col.tpe))}${arrayCast(col)})"
                  }
                  // Join them with comma space, but ensure we don't create "), " pattern
                  restUnnests.foldLeft(firstUnnest) { (acc, unnest) =>
                    code"$acc, $unnest"
                  }
                case Nil => sys.error("No columns")
              }
              code"""|select $joinedColNames
                     |from $relName
                     |where ($colsStr)
                     |in ($selectClause)
                     |""".stripMargin
            }
            vals.toList ++ List(code"$sql.as(${rowParserFor(rowType)}.all()).runUnchecked(c)")

          case x: IdComputed.Unary =>
            val sql = SQL {
              code"""|select $joinedColNames
                     |from $relName
                     |where ${x.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, idsParam.tpe)})""".stripMargin
            }
            List(code"$sql.as(${rowParserFor(rowType)}.all()).runUnchecked(c)")
        }

      case RepoMethod.SelectByIdsTracked(x) =>
        val ret = jvm.Ident("ret")
        val row = jvm.Ident("row")
        val selectByIdsCall = jvm.Call.withImplicits(
          code"selectByIds",
          List(jvm.Arg.Pos(x.idsParam.name.code)),
          List(jvm.Arg.Pos(code"c"))
        )
        // Use IgnoreResult to discard the return value of map.put() for Scala Consumer compatibility
        val putCall = jvm.IgnoreResult(code"ret.put(${row.code.callNullary(x.idComputed.paramName)}, $row)")
        val forEachLambda = lang.renderTree(jvm.Lambda1(row, putCall.code), lang.Ctx.Empty)
        List(
          jvm.Value(Nil, ret, TypesJava.Map.of(x.idComputed.tpe, x.rowType), Some(jvm.New(jvm.InferredTargs(TypesJava.HashMap), Nil))),
          lang.ListType.forEach(selectByIdsCall, forEachLambda),
          ret
        )

      case RepoMethod.UpdateBuilder(relName, fieldsType, rowType) =>
        val structure = code"$fieldsType".callNullary("structure")
        List(code"${jvm.Type.dsl.UpdateBuilder}.of(${jvm.StrLit(relName.value)}, $structure, $rowType.$rowParserName.all())")

      case RepoMethod.SelectByUnique(relName, keyColumns, allCols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(allCols, isRead = true)}
                 |from $relName
                 |where ${keyColumns.map(c => code"${c.dbName.code} = ${runtimeInterpolateValue(c.name, c.tpe)}").mkCode(" AND ")}
                 |""".stripMargin
        }

        List(code"$sql.as(${rowParserFor(rowType)}.first()).runUnchecked(c)")

      case RepoMethod.SelectByFieldValues(relName, cols, fieldValue, fieldValueOrIdsParam, rowType) =>
        val where = {
          val x: jvm.Ident = jvm.Ident("x")
          val fv: jvm.Ident = jvm.Ident("fv")

          val typeSwitch = jvm.TypeSwitch(
            fv,
            cols.toList.map { col =>
              jvm.TypeSwitch.Case(fieldValue / col.name, x, FR(code"${col.dbName.code} = ${runtimeInterpolateValue(jvm.ApplyNullary(x, jvm.Ident("value")), col.tpe)}"))
            }
          )
          val mapper = lang.renderTree(jvm.Lambda1(fv, typeSwitch.code), lang.Ctx.Empty)
          val mappedList = lang.ListType.map(fieldValueOrIdsParam.name.code, mapper)
          jvm.Value(
            Nil,
            jvm.Ident("where"),
            Fragment,
            Some(code"""|$Fragment.whereAnd(
                     |  $mappedList
                     |)""".stripMargin)
          )
        }

        val sql = SQL(code"""select ${dbNames(cols, isRead = true)} from $relName ${jvm.RuntimeInterpolation(code"${where.name}")}""")
        List(
          where,
          code"$sql.as(${rowParserFor(rowType)}.all()).runUnchecked(c)"
        )

      case RepoMethod.UpdateFieldValues(relName, id, varargsParam, fieldValue, cases0, _) =>
        val updates = {
          val x: jvm.Ident = jvm.Ident("x")
          val fv: jvm.Ident = jvm.Ident("fv")

          val typeSwitch = jvm.TypeSwitch(
            fv,
            cases0.toList.map { col =>
              jvm.TypeSwitch.Case(fieldValue / col.name, x, FR(code"${col.dbName.code} = ${runtimeInterpolateValue(jvm.ApplyNullary(x, jvm.Ident("value")), col.tpe)}"))
            }
          )
          val mapper = lang.renderTree(jvm.Lambda1(fv, typeSwitch.code), lang.Ctx.Empty)
          val mappedList = lang.ListType.map(varargsParam.name.code, mapper)
          jvm.Value(Nil, jvm.Ident("updates"), lang.ListType.tpe.of(FragmentInterpolated), Some(mappedList))
        }

        val sql = SQL {
          code"""|update $relName
                 |${jvm.RuntimeInterpolation(code"$Fragment.set(${updates.name})")}
                 |where ${matchId(id)}""".stripMargin
        }

        List(
          updates,
          jvm.IfExpr(
            jvm.ApplyNullary(updates.name, jvm.Ident("isEmpty")),
            code"false",
            code"""|$sql
                   |  .update().runUnchecked(c) > 0""".stripMargin
          )
        )
      case RepoMethod.Update(relName, _, id, param, colsUnsaved) =>
        val sql = SQL {
          val setCols = colsUnsaved.map { col =>
            code"${col.dbName.code} = ${runtimeInterpolateValue(jvm.ApplyNullary(param.name, col.name), col.tpe)}${SqlCast.toPgCode(col)}"
          }
          code"""|update $relName
                 |set ${setCols.mkCode(",\n")}
                 |where ${matchId(id)}""".stripMargin
        }
        List(
          jvm.Value(Nil, id.paramName, id.tpe, Some(jvm.ApplyNullary(param.name, id.paramName))),
          code"$sql.update().runUnchecked(c) > 0"
        )

      case RepoMethod.Insert(relName, cols, unsavedParam, rowType, writeableColumnsWithId) =>
        val values = writeableColumnsWithId.map { c =>
          runtimeInterpolateValue(jvm.ApplyNullary(unsavedParam.name.code, c.name), c.tpe).code ++ SqlCast.toPgCode(c)
        }
        val sql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${values.mkCode(", ")})
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        List(
          code"""|$sql
                 |  .updateReturning(${rowParserFor(rowType)}.exactlyOne()).runUnchecked(c)"""
        )
      case RepoMethod.Upsert(relName, cols, id, unsavedParam, rowType, writeableColumnsWithId) =>
        val values = writeableColumnsWithId.map { c =>
          runtimeInterpolateValue(code"${jvm.ApplyNullary(unsavedParam.name, c.name)}", c.tpe).code ++ SqlCast.toPgCode(c)
        }

        // When all columns are PK, use a no-op update to ensure RETURNING works
        val conflictAction = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name)) match {
          case Nil =>
            val firstCol = id.cols.head
            code"do update set ${firstCol.dbName.code} = EXCLUDED.${firstCol.dbName.code}"
          case nonEmpty =>
            code"""|do update set
                   |  ${nonEmpty.map { c => code"${c.dbName.code} = EXCLUDED.${c.dbName.code}" }.mkCode(",\n")}""".stripMargin
        }

        val sql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${values.mkCode(", ")})
                 |on conflict (${dbNames(id.cols, isRead = false)})
                 |$conflictAction
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }

        List(
          code"""|$sql
                 |  .updateReturning($rowType.$rowParserName.exactlyOne())
                 |  .runUnchecked(c)"""
        )
      case RepoMethod.UpsertBatch(relName, cols, id, rowType, writeableColumnsWithId) =>
        val conflictAction = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name)) match {
          case Nil => code"do nothing"
          case nonEmpty =>
            code"""|do update set
                   |  ${nonEmpty.map { c => code"${c.dbName.code} = EXCLUDED.${c.dbName.code}" }.mkCode(",\n")}""".stripMargin
        }

        val sql = SQL {
          code"""|insert into $relName(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${writeableColumnsWithId.map(c => code"?${SqlCast.toPgCode(c)}").mkCode(", ")})
                 |on conflict (${dbNames(id.cols, isRead = false)})
                 |$conflictAction
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }

        List(
          code"""|$sql
                 |  .updateManyReturning($rowType.$rowParserName, unsaved)
                 |  .runUnchecked(c)""".stripMargin
        )

      case RepoMethod.UpsertStreaming(relName, id, rowType, writeableColumnsWithId) =>
        val conflictAction = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name)) match {
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
        List(
          jvm.IgnoreResult(code"${SQL(code"create temporary table $tempTablename (like $relName) on commit drop")}.update().runUnchecked(c)"),
          jvm.IgnoreResult(code"$streamingInsert.insertUnchecked($copySql, batchSize, unsaved, c, $rowType.${DbLibTextSupport.textName})"),
          code"$mergeSql.update().runUnchecked(c)"
        )

      case RepoMethod.InsertUnsaved(relName, cols, unsaved, unsavedParam, _, rowType) =>
        val columns = jvm.Value(Nil, jvm.Ident("columns"), TypesJava.List.of(FragmentLiteral), Some(jvm.New(jvm.InferredTargs(TypesJava.ArrayList), Nil)))
        val values = jvm.Value(Nil, jvm.Ident("values"), TypesJava.List.of(Fragment), Some(jvm.New(jvm.InferredTargs(TypesJava.ArrayList), Nil)))

        val cases0 = unsaved.normalColumns.flatMap { col =>
          val value = FR(code"${runtimeInterpolateValue(jvm.ApplyNullary(unsavedParam.name, col.name), col.tpe)}${SqlCast.toPgCode(col)}")
          val quotedColName = "\"" + col.dbName.value + "\""
          List(
            jvm.IgnoreResult(code"${columns.name}.add($Fragment.lit(${jvm.StrLit(quotedColName)}))").code,
            jvm.IgnoreResult(code"${values.name}.add($value)").code
          )
        }

        val cases1 = unsaved.defaultedCols.map { case ComputedRowUnsaved.DefaultedCol(col @ ComputedColumn(_, ident, _, _), origType) =>
          val value = FR(code"${runtimeInterpolateValue(code"value", origType)}${SqlCast.toPgCode(col)}")
          val quotedColName = "\"" + col.dbName.value + "\""
          val valueIdent = jvm.Ident("value")
          val byName0 = jvm.ByName(code"()") // By-name parameter: just () in Scala, () -> {} in Java
          val lambda1Body = code"""|{
                                   |  ${jvm.IgnoreResult(code"${columns.name}.add($Fragment.lit(${jvm.StrLit(quotedColName)}))")};
                                   |  ${jvm.IgnoreResult(code"${values.name}.add($value)")};
                                   |}""".stripMargin
          val lambda1 = jvm.Lambda1(valueIdent, lambda1Body)
          code"""|${jvm.ApplyNullary(unsavedParam.name, ident)}.visit(
                 |  $byName0,
                 |  $lambda1
                 |);""".stripMargin
        }

        val sql = SQL {
          code"""|insert into $relName(${jvm.RuntimeInterpolation(code"$Fragment.comma(${columns.name})")})
                 |values (${jvm.RuntimeInterpolation(code"$Fragment.comma(${values.name})")})
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        val sqlEmpty = SQL {
          code"""|insert into $relName default values
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        val q = {
          val body = if (unsaved.normalColumns.isEmpty) jvm.IfExpr(jvm.ApplyNullary(columns.name, jvm.Ident("isEmpty")), sqlEmpty, sql) else sql
          jvm.Value(Nil, jvm.Ident("q"), Fragment, Some(body))
        }

        List[List[jvm.Code]](
          List(columns, values),
          cases0,
          cases1,
          List(
            q,
            code"q.updateReturning($rowType.$rowParserName.exactlyOne()).runUnchecked(c)"
          )
        ).flatten

      case RepoMethod.InsertStreaming(relName, rowType, writeableColumnsWithId) =>
        val sql = lang.s(code"COPY $relName(${dbNames(writeableColumnsWithId, isRead = false)}) FROM STDIN")
        List(code"$streamingInsert.insertUnchecked($sql, batchSize, unsaved, c, $rowType.${DbLibTextSupport.textName})")
      case RepoMethod.InsertUnsavedStreaming(relName, unsaved) =>
        val sql = lang.s(code"COPY $relName(${dbNames(unsaved.unsavedCols, isRead = false)}) FROM STDIN (DEFAULT '$DefaultValue')")
        List(code"$streamingInsert.insertUnchecked($sql, batchSize, unsaved, c, ${unsaved.tpe}.${DbLibTextSupport.textName})")
      case RepoMethod.DeleteBuilder(relName, fieldsType, _) =>
        val structure = code"$fieldsType".callNullary("structure")
        List(code"${jvm.Type.dsl.DeleteBuilder}.of(${jvm.StrLit(relName.value)}, $structure)")
      case RepoMethod.Delete(relName, id) =>
        val sql = SQL {
          code"""delete from $relName where ${matchId(id)}"""
        }
        List(code"$sql.update().runUnchecked(c) > 0")
      case RepoMethod.DeleteByIds(relName, computedId, idsParam) =>
        computedId match {
          case x: IdComputed.Composite =>
            val vals =
              x.cols.map(col => jvm.Value(Nil, col.name, jvm.Type.ArrayOf(col.tpe), Some(lang.arrayMap(idsParam.name.code, jvm.FieldGetterRef(x.tpe, col.name).code, jvm.ClassOf(col.tpe).code))).code)
            // Build SQL avoiding problematic string patterns in Java text blocks
            // The issue is that Java text blocks can't have string literals containing ")
            // So we need to structure the SQL to avoid this pattern
            // Helper to get array SQL cast for a column
            def arrayCast(col: ComputedColumn): jvm.Code = {
              val baseCast = SqlCast.toPg(col.dbCol).map(_.typeName).getOrElse(col.dbCol.udtName.getOrElse(""))
              if (baseCast.nonEmpty) jvm.Code.Str(s"::${baseCast}[]") else jvm.Code.Empty
            }
            val sql = SQL {
              val colsStr = x.cols.map(_.dbCol.name.code).mkCode(", ")
              // Instead of joining with ", " which creates problematic patterns,
              // we'll build the SQL differently
              val selectClause = x.cols.toList match {
                case single :: Nil =>
                  code"select unnest(${runtimeInterpolateValue(single.name, jvm.Type.ArrayOf(single.tpe))}${arrayCast(single)})"
                case first :: rest =>
                  // Build the select clause without creating problematic string patterns
                  val firstUnnest = code"select unnest(${runtimeInterpolateValue(first.name, jvm.Type.ArrayOf(first.tpe))}${arrayCast(first)})"
                  val restUnnests = rest.map { col =>
                    code"unnest(${runtimeInterpolateValue(col.name, jvm.Type.ArrayOf(col.tpe))}${arrayCast(col)})"
                  }
                  // Join them with comma space, but ensure we don't create "), " pattern
                  restUnnests.foldLeft(firstUnnest) { (acc, unnest) =>
                    code"$acc, $unnest"
                  }
                case Nil => sys.error("No columns")
              }
              code"""|delete
                     |from $relName
                     |where ($colsStr)
                     |in ($selectClause)
                     |""".stripMargin
            }
            vals.toList ++ List(code"$sql.update().runUnchecked(c)")

          case x: IdComputed.Unary =>
            val sql = SQL {
              code"""|delete
                     |from $relName
                     |where ${x.col.dbName.code} = ANY(${runtimeInterpolateValue(idsParam.name, jvm.Type.ArrayOf(x.tpe))})""".stripMargin
            }
            List(
              code"""|$sql
                     |  .update()
                     |  .runUnchecked(c)""".stripMargin
            )
        }

      case RepoMethod.SqlFile(sqlScript) =>
        val renderedScript: jvm.Code = sqlScript.sqlFile.decomposedSql.renderCode { (paramAtIndex: Int) =>
          val param = sqlScript.params.find(_.indices.contains(paramAtIndex)).get
          val cast = SqlCast.toPg(param).fold("")(_.withColons)
          code"${runtimeInterpolateValue(param.name, param.tpe)}$cast"
        }
        val ret = for {
          cols <- sqlScript.maybeCols.toOption
          rowName <- sqlScript.maybeRowName.toOption
        } yield {
          // this is necessary to make custom types work with sql scripts, unfortunately.
          val renderedWithCasts: jvm.Code =
            cols.toList.flatMap(c => SqlCast.fromPg(c.dbCol.tpe)) match {
              case Nil => renderedScript.code
              case _ =>
                val row = jvm.Ident("row")

                code"""|with $row as (
                       |  $renderedScript
                       |)
                       |select ${cols.map(c => code"$row.${c.dbCol.parsedName.originalName.code}${SqlCast.fromPgCode(c)}").mkCode(", ")}
                       |from $row""".stripMargin
            }

          code"${SQL(renderedWithCasts)}.as(${rowParserFor(rowName)}.all()).runUnchecked(c)"
        }

        List(ret.getOrElse(code"${SQL(renderedScript)}.update().runUnchecked(c)"))
    }

  override def mockRepoImpl(id: IdComputed, repoMethod: RepoMethod, maybeToRow: Option[jvm.Param[jvm.Type.Function1]]): List[jvm.Code] = {
    val mapCode = jvm.Ident("map").code
    val MapOps = lang.MapOps
    val Opt = lang.Optional

    // Helper to generate proper field access (method call in Java, field access in Scala)
    def idAccess(on: jvm.Code): jvm.Code = lang.renderTree(jvm.ApplyNullary(on, id.paramName), lang.Ctx.Empty)

    // Immutable local variable keyword: Scala uses `val`, Java uses `var`
    val valKw = lang match {
      case _: LangScala => "val"
      case LangJava     => "var"
      case _            => "var" // default for any future implementations
    }

    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, _) =>
        // Java: new SelectBuilderMock<>(structure, () -> new ArrayList<>(map.values()), SelectParams.empty())
        val supplierLambda = lang.renderTree(jvm.Lambda0(MapOps.valuesToList(mapCode)), lang.Ctx.Empty)
        val structure = code"$fieldsType".callNullary("structure")
        List(
          jvm
            .New(
              jvm.InferredTargs(jvm.Type.dsl.SelectBuilderMock),
              List(jvm.Arg.Pos(structure), jvm.Arg.Pos(supplierLambda), jvm.Arg.Pos(code"${jvm.Type.dsl.SelectParams}.empty()"))
            )
            .code
        )
      case RepoMethod.SelectAll(_, _, _) =>
        List(MapOps.valuesToList(mapCode))
      case RepoMethod.SelectById(_, _, id, _) =>
        List(MapOps.get(mapCode, id.paramName.code))
      case RepoMethod.SelectByIds(_, _, _, idsParam, rowType) =>
        val idVar = jvm.Ident("id")
        val resultVar = jvm.Ident("result")
        val optVar = jvm.Ident("opt")
        val getOpt = MapOps.get(mapCode, idVar.code)
        val addCall = jvm.IgnoreResult(code"$resultVar.add(${lang.Optional.get(optVar.code)})")
        val foreachBody =
          code"""|$valKw $optVar = $getOpt${lang.`;`}
                 |if (${lang.Optional.isDefined(optVar.code)}) $addCall${lang.`;`}""".stripMargin
        List(
          code"$valKw $resultVar = ${jvm.New(TypesJava.ArrayList.of(rowType), Nil)}",
          lang.arrayForEach(idsParam.name.code, idVar, foreachBody),
          resultVar.code
        )
      case RepoMethod.SelectByIdsTracked(x) =>
        val rowVar = jvm.Ident("row")
        // Use TypedLambda1 to ensure proper type inference in Scala with Java collectors
        val keyExtractor = jvm.TypedLambda1(x.rowType, rowVar, jvm.ApplyNullary(rowVar, x.idComputed.paramName))
        val keyExtractorCode = lang.renderTree(keyExtractor, lang.Ctx.Empty)
        val methodCall = jvm.Call.withImplicits(
          x.methodName.code,
          List(jvm.Arg.Pos(x.idsParam.name.code)),
          List(jvm.Arg.Pos(code"c"))
        )
        List(
          lang.ListType.collectToMap(
            methodCall,
            keyExtractorCode,
            x.idComputed.tpe,
            x.rowType
          )
        )
      case RepoMethod.SelectByUnique(_, keyColumns, _, _) =>
        val vVar = jvm.Ident("v")
        val predicateBody = keyColumns
          .map { c =>
            code"${c.name}.equals(${jvm.ApplyNullary(vVar, c.name)})"
          }
          .mkCode(" && ")
        val predicateLambda = jvm.Lambda1(vVar, predicateBody)
        val predicateCode = lang.renderTree(predicateLambda, lang.Ctx.Empty)
        List(lang.ListType.findFirst(MapOps.valuesToList(mapCode), predicateCode))

      case RepoMethod.SelectByFieldValues(_, cols, fieldValue, fieldValueOrIdsParam, _) =>
        val cases = cols.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.filter(_.${col.name} == value)"
        }
        List(code"""${fieldValueOrIdsParam.name}.foldLeft(map.values) {
              |  ${cases.mkCode("\n")}
              |}.toList""".stripMargin)
      case RepoMethod.UpdateFieldValues(_, _, varargs, fieldValue, cases0, _) =>
        val cases = cases0.map { col =>
          code"case (acc, $fieldValue.${col.name}(value)) => acc.copy(${col.name} = value)"
        }

        List(code"""|map.get(${id.paramName}) match {
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
        // Java: new UpdateBuilderMock<>(structure, () -> new ArrayList<>(map.values()), UpdateParams.empty(), row -> row)
        val supplierLambda = lang.renderTree(jvm.Lambda0(MapOps.valuesToList(mapCode)), lang.Ctx.Empty)
        val rowIdent = jvm.Ident("row")
        val copyRowLambda = lang.renderTree(jvm.Lambda1(rowIdent, rowIdent.code), lang.Ctx.Empty)
        val structure = code"$fieldsType".callNullary("structure")
        List(
          jvm
            .New(
              jvm.InferredTargs(jvm.Type.dsl.UpdateBuilderMock),
              List(jvm.Arg.Pos(structure), jvm.Arg.Pos(supplierLambda), jvm.Arg.Pos(code"${jvm.Type.dsl.UpdateParams}.empty()"), jvm.Arg.Pos(copyRowLambda))
            )
            .code
        )
      case RepoMethod.Update(_, _, _, param, _) =>
        val paramIdAccess = idAccess(param.name.code)
        val shouldUpdateVar = jvm.Ident("shouldUpdate")
        val oldRowVar = jvm.Ident("oldRow")

        // Build the predicate lambda: oldRow -> !oldRow.equals(param.name)
        val predicateBody = code"!$oldRowVar.equals(${param.name})"
        val predicateLambda = jvm.Lambda1(oldRowVar, predicateBody)
        val predicateCode = lang.renderTree(predicateLambda, lang.Ctx.Empty)

        // shouldUpdate = map.get(id).filter(predicate).isDefined
        val filtered = Opt.filter(MapOps.get(mapCode, paramIdAccess), predicateCode)
        val shouldUpdateExpr = Opt.isDefined(filtered)

        val localVar = jvm.LocalVar(shouldUpdateVar, None, shouldUpdateExpr)

        List(
          lang.renderTree(localVar, lang.Ctx.Empty),
          code"""|if ($shouldUpdateVar) {
                 |  ${MapOps.putVoid(mapCode, paramIdAccess, param.name.code)}${lang.`;`}
                 |}""".stripMargin,
          shouldUpdateVar.code
        )
      case RepoMethod.Insert(_, _, unsavedParam, _, _) =>
        val unsavedIdAccess = idAccess(unsavedParam.name.code)
        List(
          code"""|if (${MapOps.contains(mapCode, unsavedIdAccess)}) {
                 |  throw new RuntimeException(${lang.s(code"id $$${unsavedIdAccess} already exists")})${lang.`;`}
                 |}""".stripMargin,
          MapOps.putVoid(mapCode, unsavedIdAccess, unsavedParam.name.code),
          unsavedParam.name.code
        )
      case RepoMethod.Upsert(_, _, _, unsavedParam, _, _) =>
        val unsavedIdAccess = idAccess(unsavedParam.name.code)
        List(
          MapOps.putVoid(mapCode, unsavedIdAccess, unsavedParam.name.code),
          unsavedParam.name.code
        )
      case RepoMethod.UpsertStreaming(_, localId, _, _) =>
        val rowVar = jvm.Ident("row")
        val countVar = jvm.Ident("count")
        val rowIdAccess = lang.renderTree(jvm.ApplyNullary(rowVar, localId.paramName), lang.Ctx.Empty)
        List(
          code"var $countVar = 0",
          code"""|while (unsaved.hasNext()) {
                 |  $valKw $rowVar = unsaved.next()${lang.`;`}
                 |  ${MapOps.putVoid(mapCode, rowIdAccess, rowVar.code)}${lang.`;`}
                 |  $countVar = $countVar + 1${lang.`;`}
                 |}""".stripMargin,
          countVar.code
        )
      case RepoMethod.UpsertBatch(_, _, localId, rowType, _) =>
        val rowVar = jvm.Ident("row")
        val resultVar = jvm.Ident("result")
        val rowIdAccess = lang.renderTree(jvm.ApplyNullary(rowVar, localId.paramName), lang.Ctx.Empty)
        val addCall = jvm.IgnoreResult(code"$resultVar.add($rowVar)")
        List(
          code"$valKw $resultVar = ${jvm.New(TypesJava.ArrayList.of(rowType), Nil)}",
          code"""|while (unsaved.hasNext()) {
                 |  $valKw $rowVar = unsaved.next()${lang.`;`}
                 |  ${MapOps.putVoid(mapCode, rowIdAccess, rowVar.code)}${lang.`;`}
                 |  $addCall${lang.`;`}
                 |}""".stripMargin,
          resultVar.code
        )
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _) =>
        val insertCall = jvm.Call.withImplicits(
          code"insert",
          List(jvm.Arg.Pos(jvm.Apply1(maybeToRow.get, unsavedParam.name))),
          List(jvm.Arg.Pos(code"c"))
        )
        List(insertCall)
      case RepoMethod.InsertStreaming(_, _, _) =>
        val rowVar = jvm.Ident("row")
        val countVar = jvm.Ident("count")
        val rowIdAccess = idAccess(rowVar.code)
        List(
          code"var $countVar = 0L",
          code"""|while (unsaved.hasNext()) {
                 |  $valKw $rowVar = unsaved.next()${lang.`;`}
                 |  ${MapOps.putVoid(mapCode, rowIdAccess, rowVar.code)}${lang.`;`}
                 |  $countVar = $countVar + 1L${lang.`;`}
                 |}""".stripMargin,
          countVar.code
        )
      case RepoMethod.InsertUnsavedStreaming(_, _) =>
        val unsavedRowVar = jvm.Ident("unsavedRow")
        val rowVar = jvm.Ident("row")
        val countVar = jvm.Ident("count")
        val rowIdAccess = idAccess(rowVar.code)
        List(
          code"var $countVar = 0L",
          code"""|while (unsaved.hasNext()) {
                 |  $valKw $unsavedRowVar = unsaved.next()${lang.`;`}
                 |  $valKw $rowVar = ${jvm.Apply1(maybeToRow.get, unsavedRowVar)}${lang.`;`}
                 |  ${MapOps.putVoid(mapCode, rowIdAccess, rowVar.code)}${lang.`;`}
                 |  $countVar = $countVar + 1L${lang.`;`}
                 |}""".stripMargin,
          countVar.code
        )
      case RepoMethod.DeleteBuilder(_, fieldsType, _) =>
        // Java: new DeleteBuilderMock<>(structure, () -> new ArrayList<>(map.values()), DeleteParams.empty(), row -> row.id(), id -> map.remove(id))
        val rowVar = jvm.Ident("row")
        val idVar = jvm.Ident("id")
        // Use removeVoid for Consumer - returns void instead of Optional
        List(
          jvm
            .New(
              jvm.InferredTargs(jvm.Type.dsl.DeleteBuilderMock),
              List(
                jvm.Arg.Pos(code"$fieldsType".callNullary("structure")),
                jvm.Arg.Pos(lang.renderTree(jvm.Lambda0(MapOps.valuesToList(mapCode)), lang.Ctx.Empty)),
                jvm.Arg.Pos(code"${jvm.Type.dsl.DeleteParams}.empty()"),
                jvm.Arg.Pos(lang.renderTree(jvm.Lambda1(rowVar, idAccess(rowVar.code)), lang.Ctx.Empty)),
                jvm.Arg.Pos(lang.renderTree(jvm.Lambda1(idVar, MapOps.removeVoid(mapCode, idVar.code)), lang.Ctx.Empty))
              )
            )
            .code
        )

      case RepoMethod.Delete(_, id) =>
        List(Opt.isDefined(MapOps.remove(mapCode, id.paramName.code)))
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        val idVar = jvm.Ident("id")
        val countVar = jvm.Ident("count")
        val forEachBody = code"""|if (${Opt.isDefined(MapOps.remove(mapCode, idVar.code))}) {
                                 |  $countVar = $countVar + 1${lang.`;`}
                                 |}""".stripMargin
        List(
          code"var $countVar = 0",
          lang.arrayForEach(idsParam.name.code, idVar, forEachBody),
          countVar.code
        )
      case RepoMethod.SqlFile(_) =>
        // should not happen (tm)
        List(code"???")
    }
  }

  override def testInsertMethod(x: ComputedTestInserts.InsertMethod): jvm.Method = {
    val newRepo = jvm.New(x.table.names.RepoImplName, Nil)
    val newRow = jvm.New(x.cls, x.values.map { case (p, expr) => jvm.Arg.Named(p, expr) })
    jvm.Method(
      Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = x.name,
      params = x.params,
      implicitParams = List(c),
      tpe = x.table.names.RowName,
      throws = Nil,
      body = List(jvm.Call.withImplicits(code"($newRepo).insert", List(jvm.Arg.Pos(newRow)), List(jvm.Arg.Pos(c.name))))
    )
  }

  override val defaultedInstance: List[jvm.Given] = {
    def textInstance = {
      val T = jvm.Type.Abstract(jvm.Ident("T"))
      val textofT = jvm.Ident("t")
      val ot = jvm.Ident("ot")
      val sb = jvm.Ident("sb")
      val value = jvm.Ident("value")
      val innerLambda0 = jvm.ByName(code"{ ${jvm.IgnoreResult(code"""$sb.append("$DefaultValue")""")}; }")
      val innerLambda1 = jvm.Lambda1(value, code"$textofT.unsafeEncode($value, $sb)")
      val outerLambda = jvm.Lambda2(ot, sb, code"$ot.visit($innerLambda0, $innerLambda1)")
      jvm.Given(
        tparams = List(T),
        name = DbLibTextSupport.textName,
        implicitParams = List(jvm.Param(textofT, PgText.of(T))),
        tpe = PgText.of(default.Defaulted.of(T)),
        body = code"""$PgText.instance($outerLambda)"""
      )
    }
    if (enableStreamingInserts) List(textInstance) else Nil
  }

  override def stringEnumInstances(wrapperType: jvm.Type, underlying: jvm.Type, sqlType: String, openEnum: Boolean): List[jvm.Given] = {
    val sqlTypeLit = jvm.StrLit(sqlType)
    val arrayWrapper = jvm.Type.ArrayOf(wrapperType)
    val create = jvm.MethodRef(wrapperType, jvm.Ident(if (openEnum) "apply" else "force"))
    val extract = jvm.FieldGetterRef(wrapperType, jvm.Ident("value"))
    List(
      Some(
        jvm.Given(
          tparams = Nil,
          name = pgTypeArrayName,
          implicitParams = Nil,
          tpe = PgType.of(arrayWrapper),
          body = {
            val underlyingCls = jvm.ClassOf(underlying)
            val xs = jvm.Ident("xs")
            val lambda1 = jvm.Lambda1(xs, lang.arrayMap(xs.code, create, jvm.ClassOf(wrapperType).code))
            val lambda2 = jvm.Lambda1(xs, lang.arrayMap(xs.code, extract, underlyingCls.code))
            val base =
              code"""|${lookupPgType(jvm.Type.ArrayOf(underlying))}
                     |  .bimap($lambda1, $lambda2)""".stripMargin
            if (openEnum) base
            else code"""|$base
                        |  .renamedDropPrecision($sqlTypeLit)""".stripMargin
          }
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = pgTypeName,
          implicitParams = Nil,
          tpe = PgType.of(wrapperType),
          body = {
            val base = code"${lookupPgType(underlying)}.bimap($create, $extract)"
            if (openEnum) base
            else code"""|$base
                        |  .renamedDropPrecision($sqlTypeLit)""".stripMargin

          }
        )
      )
    ).flatten
  }

  override def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, underlying: jvm.Type, overrideDbType: Option[String]): List[jvm.Given] = {
    val xs = jvm.Ident("xs")
    // When overrideDbType is provided (e.g., for domain types), we need to rename the pgType
    // to use the correct SQL type name for proper casting
    def maybeRename(baseCode: jvm.Code, sqlType: String): jvm.Code = {
      val sqlTypeLit = jvm.StrLit(sqlType)
      code"$baseCode.renamed($sqlTypeLit)"
    }
    List[Option[jvm.Given]](
      Some(
        jvm.Given(
          tparams = Nil,
          name = pgTypeArrayName,
          implicitParams = Nil,
          tpe = PgType.of(jvm.Type.ArrayOf(wrapperType)),
          body = {
            val lambda1 = jvm.Lambda1(xs, lang.arrayMap(xs.code, jvm.ConstructorMethodRef(wrapperType).code, jvm.ClassOf(wrapperType).code))
            val lambda2 = jvm.Lambda1(xs, lang.arrayMap(xs.code, jvm.FieldGetterRef(wrapperType, jvm.Ident("value")).code, jvm.ClassOf(underlying).code))
            val base = code"${lookupPgType(jvm.Type.ArrayOf(underlying))}.bimap($lambda1, $lambda2)"
            overrideDbType.fold(base)(t => maybeRename(base, t + "[]"))
          }
        )
      ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = pgTypeName,
          implicitParams = Nil,
          tpe = PgType.of(wrapperType),
          body = {
            val base = code"${lookupPgType(underlying)}.bimap(${jvm.ConstructorMethodRef(wrapperType)}, ${jvm.FieldGetterRef(wrapperType, jvm.Ident("value"))})"
            overrideDbType.fold(base)(maybeRename(base, _))
          }
        )
      )
    ).flatten
  }

  override val missingInstances: List[jvm.ClassMember] = Nil

  override def rowInstances(tpe: jvm.Type, cols: NonEmptyList[ComputedColumn], rowType: DbLib.RowType): List[jvm.ClassMember] = {
    def rowParser = {
      jvm.Value(
        Nil,
        rowParserName,
        RowParser.of(tpe),
        Some {
          val pgTypes = cols.toList.map(x => lookupPgType(x.tpe)).mkCode(code", ")
          val row = jvm.Ident("row")
          val newTuple = jvm.New(jvm.InferredTargs(Tuple(cols.length)), cols.toList.map(c => jvm.Arg.Pos(jvm.ApplyNullary(row, c.name))))
          val lambda = jvm.Lambda1(row, newTuple)
          code"$RowParsers.of($pgTypes, ${jvm.ConstructorMethodRef(tpe)}, $lambda)"
        }
      )
    }
    rowType match {
      case DbLib.RowType.Writable =>
        val text = if (enableStreamingInserts) {
          val row = jvm.Ident("row")
          val sb = jvm.Ident("sb")
          val textCols: NonEmptyList[jvm.Code] = cols.map { col =>
            val text = col.tpe match {
              case jvm.Type.TApply(default.Defaulted, List(targ)) =>
                val innerPgText = jvm.ApplyNullary(lookupPgType(targ), DbLibTextSupport.textName)
                val target = jvm.Select(default.Defaulted.code, DbLibTextSupport.textName)
                jvm.Call(target.code, List(jvm.Call.ArgGroup(List(jvm.Arg.Pos(innerPgText.code)), isImplicit = true))).code
              case other =>
                jvm.ApplyNullary(lookupPgType(other), DbLibTextSupport.textName).code
            }
            code"$text.unsafeEncode($row.${col.name}, $sb)"
          }
          // Interleave encode statements with delimiter appends
          val delimiter = code"$sb.append($PgText.DELIMETER)"
          val statements: NonEmptyList[jvm.Code] = NonEmptyList(textCols.head, textCols.tail.flatMap(stmt => List(delimiter, stmt)))
          val lambdaBody = code"""|{
                                  |  ${statements.map(s => code"$s;").mkCode("\n")}
                                  |}""".stripMargin
          val body = code"$PgText.instance(${jvm.Lambda2(row, sb, lambdaBody)})"
          Some(jvm.Given(tparams = Nil, name = DbLibTextSupport.textName, implicitParams = Nil, tpe = PgText.of(tpe), body = body))
        } else None

        text.toList
      case DbLib.RowType.ReadWriteable =>
        List(rowParser) ++ List(
          jvm.Given(tparams = Nil, name = DbLibTextSupport.textName, implicitParams = Nil, tpe = PgText.of(tpe), body = code"$PgText.from($rowParserName)")
        )
      case DbLib.RowType.Readable => List(rowParser)
    }
  }

  val PgRead = jvm.Type.Qualified("typo.runtime.PgRead")
  val PgWrite = jvm.Type.Qualified("typo.runtime.PgWrite")

  /** Map jdbc types to PgTypes for custom type bimap generation - only for simple types */
  def lookupPgTypeFromJdbc(jdbcType: jvm.Type): Option[jvm.Code] =
    jdbcType match {
      case TypesJava.BigDecimal                                                                     => Some(code"$PgTypes.numeric")
      case TypesJava.Boolean                                                                        => Some(code"$PgTypes.bool")
      case TypesJava.Double                                                                         => Some(code"$PgTypes.float8")
      case TypesJava.Float                                                                          => Some(code"$PgTypes.float4")
      case TypesJava.Short                                                                          => Some(code"$PgTypes.int2")
      case TypesJava.Integer                                                                        => Some(code"$PgTypes.int4")
      case TypesJava.Long                                                                           => Some(code"$PgTypes.int8")
      case TypesJava.String                                                                         => Some(code"$PgTypes.text")
      case TypesJava.UUID                                                                           => Some(code"$PgTypes.uuid")
      case jvm.Type.ArrayOf(TypesJava.Byte | TypesJava.BytePrimitive | TypesScala.Byte | lang.Byte) => Some(code"$PgTypes.bytea")
      case _                                                                                        => None
    }

  /** Map jdbc types to PgText instances for custom type text encoding */
  def lookupPgTextFromJdbc(textType: jvm.Type): Option[jvm.Code] =
    textType match {
      case TypesJava.String                                                                         => Some(code"$PgText.textString")
      case TypesJava.BigDecimal | lang.BigDecimal                                                   => Some(code"$PgText.textBigDecimal")
      case TypesJava.Boolean | lang.Boolean                                                         => Some(code"$PgText.textBoolean")
      case TypesJava.Double | lang.Double                                                           => Some(code"$PgText.textDouble")
      case TypesJava.Float | lang.Float                                                             => Some(code"$PgText.textFloat")
      case TypesJava.Integer | lang.Int                                                             => Some(code"$PgText.textInteger")
      case TypesJava.Long | lang.Long                                                               => Some(code"$PgText.textLong")
      case TypesJava.Short | lang.Short                                                             => Some(code"$PgText.textShort")
      case TypesJava.UUID                                                                           => Some(code"$PgText.textUuid")
      case jvm.Type.ArrayOf(TypesJava.Byte | TypesJava.BytePrimitive | TypesScala.Byte | lang.Byte) => Some(code"$PgText.textByteArray")
      case _                                                                                        => None
    }

  override def customTypeInstances(ct: CustomType): List[jvm.Given] = {
    val v = jvm.Ident("v")

    // pgType instance - either using bimap for simple types or full PgType.of for complex types
    val pgTypeBody: jvm.Code = lookupPgTypeFromJdbc(ct.toTypo.jdbcType) match {
      case Some(basePgType) =>
        // Simple type - use bimap, then rename to correct SQL type
        val sqlTypeLit = jvm.StrLit(ct.sqlType)
        code"$basePgType.bimap(${jvm.Lambda1(v, ct.toTypo0(v))}, ${jvm.Lambda1(v, ct.fromTypo0(v))}).renamed($sqlTypeLit)"
      case None =>
        // Complex type (e.g., PGbox, PGpath) - use PgType.of with custom read/write
        val sqlTypeLit = jvm.StrLit(ct.sqlType)
        val jdbcType = ct.toTypo.jdbcType
        val lambda1 = jvm.Lambda1(v, ct.toTypo0(v))
        // Use TypedLambda1 for Scala because passObjectToJdbc() returns PgWrite[Object]
        val lambda2 = jvm.TypedLambda1(ct.typoType, v, ct.fromTypo0(v))
        code"""|$PgType.of(
               |  $sqlTypeLit,
               |  $PgRead.castJdbcObjectTo(${jvm.ClassOf(jdbcType)}).map($lambda1),
               |  $PgWrite.passObjectToJdbc().contramap($lambda2),
               |  ${ct.typoType}.${DbLibTextSupport.textName}
               |)""".stripMargin
    }

    val pgTypeInstance = jvm.Given(
      tparams = Nil,
      name = pgTypeName,
      implicitParams = Nil,
      tpe = PgType.of(ct.typoType),
      body = pgTypeBody
    )

    // pgText instance for streaming inserts
    val baseText = lookupPgTextFromJdbc(ct.toText.textType).getOrElse(sys.error(s"Unsupported text type for custom type: ${ct.toText.textType}"))

    val pgTextInstance = jvm.Given(
      tparams = Nil,
      name = DbLibTextSupport.textName,
      implicitParams = Nil,
      tpe = PgText.of(ct.typoType),
      body = code"$baseText.contramap(${jvm.Lambda1(v, ct.toText.toTextType(v))})"
    )

    // pgTypeArray instance for array support
    // Uses PgType.array(read, write) which internally handles typename.array() and pgText.array()
    val pgTypeArrayInstance: Option[jvm.Given] =
      if (ct.forbidArray) None
      else {
        val toTypo = ct.toTypoInArray.getOrElse(ct.toTypo)
        val fromTypo = ct.fromTypoInArray.getOrElse(ct.fromTypo)
        val arrayType = jvm.Type.ArrayOf(ct.typoType)
        val xs = jvm.Ident("xs")

        // Determine the read method based on whether we need to cast JDBC array elements
        val innerMapper = jvm.Lambda1(v, toTypo.toTypo(v, ct.typoType))
        val arrayRead: jvm.Code = lookupPgTypeFromJdbc(toTypo.jdbcType) match {
          case Some(_) =>
            // Simple type - use massageJdbcArrayTo
            val jdbcArrayType = jvm.Type.ArrayOf(toTypo.jdbcType)
            code"$PgRead.massageJdbcArrayTo(${jvm.ClassOf(jdbcArrayType)}).map(${jvm.Lambda1(xs, lang.arrayMap(xs.code, innerMapper.code, jvm.ClassOf(ct.typoType).code))})"
          case None =>
            // Complex type - use castJdbcArrayTo
            code"$PgRead.castJdbcArrayTo(${jvm.ClassOf(toTypo.jdbcType)}).map(${jvm.Lambda1(xs, lang.arrayMap(xs.code, innerMapper.code, jvm.ClassOf(ct.typoType).code))})"
        }

        // Generate the array write using the fromTypo conversion
        // Use TypedLambda1 for cross-language compatibility
        val typedLambda = jvm.TypedLambda1(ct.typoType, v, fromTypo.fromTypo0(v))
        // Use GenericMethodCall for cross-language compatibility
        val passObjectToJdbc = jvm.GenericMethodCall(PgWrite, jvm.Ident("passObjectToJdbc"), List(fromTypo.jdbcType), Nil)
        val typenameAs = jvm.GenericMethodCall(code"${ct.typoType}.$pgTypeName.typename()", jvm.Ident("as"), List(fromTypo.jdbcType), Nil)
        val arrayWrite: jvm.Code =
          code"$passObjectToJdbc.array($typenameAs).contramap(${jvm.Lambda1(xs, lang.arrayMap(xs.code, typedLambda.code, jvm.ClassOf(fromTypo.jdbcType).code))})"

        Some(
          jvm.Given(
            tparams = Nil,
            name = pgTypeArrayName,
            implicitParams = Nil,
            tpe = PgType.of(arrayType),
            // Use PgType.array(read, write) which handles typename.array() and pgText.array()
            body = code"${ct.typoType}.$pgTypeName.array($arrayRead, $arrayWrite)"
          )
        )
      }

    // pgText must come before pgType since complex types reference it
    List(pgTextInstance, pgTypeInstance) ++ pgTypeArrayInstance.toList
  }
}
