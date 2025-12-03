package typo
package internal
package codegen

import typo.internal.analysis.MaybeReturnsRows
import typo.jvm.Code.TypeOps

class DbLibTypo(
    pkg: jvm.QIdent,
    override val lang: Lang,
    default: ComputedDefault,
    enableStreamingInserts: Boolean,
    adapter: DbAdapter,
    naming: Naming
) extends DbLib {

  /** Quote a column name for use in SQL code */
  def quotedColName(col: db.ColName): jvm.Code = jvm.Code.Str(adapter.quoteIdent(col.value))

  /** Quote a column name for use in SQL code - using ComputedColumn */
  def quotedColName(col: ComputedColumn): jvm.Code = quotedColName(col.dbName)

  /** Quote a table/relation name for use in SQL code */
  def quotedRelName(name: db.RelationName): jvm.Code =
    jvm.Code.Str(quotedRelNameStr(name))

  /** Get quoted relation name as a plain string */
  def quotedRelNameStr(name: db.RelationName): String =
    name.schema.foldLeft(adapter.quoteIdent(name.name))((acc, s) => s"${adapter.quoteIdent(s)}.$acc")

  // Fragment type - unified for both PostgreSQL and MariaDB
  val Fragment = jvm.Type.Qualified("typo.runtime.Fragment")
  val FragmentLiteral = Fragment / jvm.Ident("Literal")

  val RowParser = jvm.Type.Qualified("typo.runtime.RowParser")
  val RowParsers = jvm.Type.Qualified("typo.runtime.RowParsers")
  val streamingInsert = jvm.Type.Qualified("typo.runtime.streamingInsert")

  // For Java, use Fragment.interpolate (static method)
  // For Scala, use FragmentInterpolator.interpolate (extension method)
  val SqlStringInterpolation: jvm.Type.Qualified = lang match {
    case _: LangScala          => jvm.Type.Qualified("typo.runtime.FragmentInterpolator")
    case LangJava | LangKotlin => jvm.Type.Qualified("typo.runtime.Fragment")
    case _                     => ???
  }

  def rowParserFor(rowType: jvm.Type) = code"$rowType.$rowParserName"
  def SQL(content: jvm.Code) = jvm.StringInterpolate(SqlStringInterpolation / jvm.Ident("interpolate"), jvm.Ident("interpolate"), content)
  def FR(content: jvm.Code) = SQL(content)

  val pgTypeArrayName = jvm.Ident("pgTypeArray")
  val rowParserName: jvm.Ident = jvm.Ident("_rowParser")

  import lang.prop

  /** Access ID from a row - uses method call for composite IDs, property access for unary IDs */
  def idAccessOnRow(on: jvm.Code, id: IdComputed): jvm.Code = id match {
    case _: IdComputed.Composite => lang.nullaryMethodCall(on, id.paramName)
    case _: IdComputed.Unary     => prop(on, id.paramName)
  }

  override val additionalFiles: List[typo.jvm.File] = Nil

  def runtimeInterpolateValue(name: jvm.Code, tpe: jvm.Type): jvm.Code =
    jvm.RuntimeInterpolation(code"${lookupPgType(tpe)}.encode($name)")

  def dbNames(cols: NonEmptyList[ComputedColumn], isRead: Boolean): jvm.Code =
    cols
      .map(c => quotedColName(c) ++ (if (isRead) adapter.columnReadCast(c) else jvm.Code.Empty))
      .mkCode(", ")

  /** Get column names as a plain string for MariaDB SQL literals (no type casts since MariaDB doesn't use PostgreSQL-style casts in SELECT) */
  def dbNamesString(cols: NonEmptyList[ComputedColumn]): String =
    cols.map(c => adapter.quoteIdent(c.dbName.value)).toList.mkString(", ")

  def matchId(id: IdComputed): jvm.Code =
    id match {
      case id: IdComputed.Unary =>
        code"${quotedColName(id.col)} = ${runtimeInterpolateValue(id.paramName, id.tpe)}"
      case composite: IdComputed.Composite =>
        composite.cols.map(cc => code"${quotedColName(cc)} = ${runtimeInterpolateValue(prop(composite.paramName.code, cc.name), cc.tpe)}").mkCode(" AND ")
    }

  /** Resolve known implicits at generation-time instead of at compile-time */
  def lookupPgType(tpe: jvm.Type): jvm.Code =
    adapter.lookupType(tpe, pkg, lang)

  /** Lookup database type instance based on the original database type. This is more accurate than lookupPgType because it preserves the exact database type (e.g., VARCHAR vs TEXT vs CHAR all map to
    * String but need different type instances).
    */
  def lookupDbTypeInstance(dbType: db.Type): jvm.Code =
    adapter.lookupTypeByDbType(dbType, adapter.Types, naming)

  /** Lookup database type instance for a computed column. For generated wrapper types (like ProductId), use the type's own pgType field. For primitive/standard types, look up by the database type.
    */
  def lookupDbTypeInstance(col: ComputedColumn): jvm.Code =
    lookupDbTypeInstance(col.tpe, col.dbCol.tpe)

  /** Lookup database type instance given both JVM type and database type. For generated wrapper types (like ProductId), use the type's own pgType field. For primitive/standard types, look up by the
    * database type. Handles Optional and Array wrappers recursively.
    */
  def lookupDbTypeInstance(jvmType: jvm.Type, dbType: db.Type): jvm.Code =
    jvmType match {
      // Handle Optional types by recursing and adding .opt()
      case lang.Optional(inner) =>
        code"${lookupDbTypeInstance(inner, dbType)}.opt()"
      // User-defined type (type override) within our package - use its own pgType field
      case jvm.Type.UserDefined(x: jvm.Type.Qualified) if x.value.idents.startsWith(pkg.idents) =>
        code"$x.${adapter.typeFieldName}"
      // User-defined type (type override) external to our package - fall back to database type
      case jvm.Type.UserDefined(_) =>
        lookupDbTypeInstance(dbType)
      // Generated type - use its own pgType field
      case x: jvm.Type.Qualified if x.value.idents.startsWith(pkg.idents) =>
        code"$x.${adapter.typeFieldName}"
      // User-defined array type within our package
      case jvm.Type.ArrayOf(jvm.Type.UserDefined(inner: jvm.Type.Qualified)) if inner.value.idents.startsWith(pkg.idents) =>
        code"$inner.${adapter.typeFieldName}Array"
      // Generated array type - use the element type's pgTypeArray field
      case jvm.Type.ArrayOf(inner: jvm.Type.Qualified) if inner.value.idents.startsWith(pkg.idents) =>
        code"$inner.${adapter.typeFieldName}Array"
      // Standard type - look up by database type
      case _ =>
        lookupDbTypeInstance(dbType)
    }

  /** Lookup database type instance for a unary ID. Uses the ID's wrapper type (e.g., AuditLogId) rather than the underlying column type.
    */
  def lookupDbTypeInstance(id: IdComputed.Unary): jvm.Code =
    lookupDbTypeInstance(id.tpe, id.col.dbCol.tpe)

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

  /** Generate selectByIds method body - different strategies per database */
  private def selectByIdsBody(
      relName: db.RelationName,
      cols: NonEmptyList[ComputedColumn],
      id: IdComputed,
      idsParam: jvm.Param[jvm.Type],
      rowType: jvm.Type
  ): jvm.Body = {
    val qRelName = quotedRelName(relName)
    val qRelNameStr = quotedRelNameStr(relName)
    val colNames = dbNames(cols, isRead = true)
    val colNamesStr = dbNamesString(cols)
    val rowParser = rowParserFor(rowType)

    adapter.dbType match {
      case DbType.PostgreSQL =>
        id match {
          case x: IdComputed.Unary =>
            // PostgreSQL: WHERE col = ANY(array)
            val sql = SQL {
              code"""|select $colNames
                     |from $qRelName
                     |where ${jvm.Code.Str(adapter.quoteIdent(x.col.dbName.value))} = ANY(${runtimeInterpolateValue(idsParam.name, idsParam.tpe)})""".stripMargin
            }
            jvm.Body.Expr(code"$sql.query($rowParser.all()).runUnchecked(c)")

          case x: IdComputed.Composite =>
            // PostgreSQL: WHERE (cols) IN (SELECT unnest(...), unnest(...))
            val vals = x.cols.map { col =>
              jvm
                .Value(
                  Nil,
                  col.name,
                  jvm.Type.ArrayOf(col.tpe),
                  Some(lang.arrayMap(idsParam.name.code, jvm.FieldGetterRef(x.tpe, col.name).code, jvm.ClassOf(col.tpe).code)),
                  isLazy = false,
                  isOverride = false
                )
                .code
            }

            def arrayCast(col: ComputedColumn): jvm.Code = {
              val baseCast = SqlCast.toPg(col.dbCol).map(_.typeName).getOrElse(col.dbCol.udtName.getOrElse(""))
              if (baseCast.nonEmpty) jvm.Code.Str(s"::${baseCast}[]") else jvm.Code.Empty
            }

            val sql = SQL {
              val colsStr = x.cols.map(_.dbCol.name.code).mkCode(", ")
              val selectClause = x.cols.toList match {
                case single :: Nil =>
                  code"select unnest(${runtimeInterpolateValue(single.name, jvm.Type.ArrayOf(single.tpe))}${arrayCast(single)})"
                case first :: rest =>
                  val firstUnnest = code"select unnest(${runtimeInterpolateValue(first.name, jvm.Type.ArrayOf(first.tpe))}${arrayCast(first)})"
                  rest.foldLeft(firstUnnest) { (acc, col) =>
                    code"$acc, unnest(${runtimeInterpolateValue(col.name, jvm.Type.ArrayOf(col.tpe))}${arrayCast(col)})"
                  }
                case Nil => sys.error("No columns")
              }
              code"""|select $colNames
                     |from $qRelName
                     |where ($colsStr)
                     |in ($selectClause)
                     |""".stripMargin
            }
            jvm.Body.Stmts(vals.toList :+ jvm.Return(code"$sql.query($rowParser.all()).runUnchecked(c)").code)
        }

      case DbType.MariaDB | DbType.MySQL =>
        id match {
          case x: IdComputed.Unary =>
            // MariaDB: Build IN clause with individual values
            val colName = adapter.quoteIdent(x.col.dbName.value)
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodeId = code"${lookupDbTypeInstance(x)}.encode($idIdent)"
            val addStmt = jvm.Body.Expr(code"$fragments.add($encodeId)")
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(TypesJava.ArrayList.of(Fragment)), jvm.New(TypesJava.ArrayList.of(Fragment), Nil)),
                lang.arrayForEach(idsParam.name.code, idIdent, addStmt),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"select $colNamesStr from $qRelNameStr where $colName in ("
                    )}), $Fragment.comma($fragments), $Fragment.lit(${jvm.StrLit(s")")})).query($rowParser.all()).runUnchecked(c)"""
                )
              )
            )

          case x: IdComputed.Composite =>
            // MariaDB: (col1, col2) IN ((val1, val2), (val3, val4), ...)
            val colNamesJoined = x.cols.toList.map(c => adapter.quoteIdent(c.dbCol.name.value)).mkString(", ")
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodedCols = x.cols.toList.map(c => code"${lookupDbTypeInstance(c)}.encode(${lang.prop(idIdent.code, c.name)})")
            val commaLit = code"$Fragment.lit(${jvm.StrLit(", ")})"
            val openParen = code"$Fragment.lit(${jvm.StrLit("(")})"
            val closeParen = code"$Fragment.lit(${jvm.StrLit(")")})"
            val tupleArgs = encodedCols.flatMap(e => List(commaLit, e)).drop(1)
            val tupleExpr = code"$Fragment.interpolate($openParen, ${tupleArgs.mkCode(", ")}, $closeParen)"
            val addStmt = jvm.Body.Expr(code"$fragments.add($tupleExpr)")
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(TypesJava.ArrayList.of(Fragment)), jvm.New(TypesJava.ArrayList.of(Fragment), Nil)),
                lang.arrayForEach(idsParam.name.code, idIdent, addStmt),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"select $colNamesStr from $qRelNameStr where ($colNamesJoined) in ("
                    )}), $Fragment.comma($fragments), $Fragment.lit(${jvm.StrLit(")")})).query($rowParser.all()).runUnchecked(c)"""
                )
              )
            )
        }
    }
  }

  /** Generate deleteByIds method body - different strategies per database */
  private def deleteByIdsBody(
      relName: db.RelationName,
      id: IdComputed,
      idsParam: jvm.Param[jvm.Type]
  ): jvm.Body = {
    val qRelName = quotedRelName(relName)
    val qRelNameStr = quotedRelNameStr(relName)

    adapter.dbType match {
      case DbType.PostgreSQL =>
        id match {
          case x: IdComputed.Unary =>
            // PostgreSQL: WHERE col = ANY(array)
            val sql = SQL {
              code"""|delete
                     |from $qRelName
                     |where ${jvm.Code.Str(adapter.quoteIdent(x.col.dbName.value))} = ANY(${runtimeInterpolateValue(idsParam.name, jvm.Type.ArrayOf(x.tpe))})""".stripMargin
            }
            jvm.Body.Expr(
              code"""|$sql
                     |  .update()
                     |  .runUnchecked(c)""".stripMargin
            )

          case x: IdComputed.Composite =>
            // PostgreSQL: WHERE (cols) IN (SELECT unnest(...), unnest(...))
            val vals = x.cols.map { col =>
              jvm
                .Value(
                  Nil,
                  col.name,
                  jvm.Type.ArrayOf(col.tpe),
                  Some(lang.arrayMap(idsParam.name.code, jvm.FieldGetterRef(x.tpe, col.name).code, jvm.ClassOf(col.tpe).code)),
                  isLazy = false,
                  isOverride = false
                )
                .code
            }

            def arrayCast(col: ComputedColumn): jvm.Code = {
              val baseCast = SqlCast.toPg(col.dbCol).map(_.typeName).getOrElse(col.dbCol.udtName.getOrElse(""))
              if (baseCast.nonEmpty) jvm.Code.Str(s"::${baseCast}[]") else jvm.Code.Empty
            }

            val sql = SQL {
              val colsStr = x.cols.map(_.dbCol.name.code).mkCode(", ")
              val selectClause = x.cols.toList match {
                case single :: Nil =>
                  code"select unnest(${runtimeInterpolateValue(single.name, jvm.Type.ArrayOf(single.tpe))}${arrayCast(single)})"
                case first :: rest =>
                  val firstUnnest = code"select unnest(${runtimeInterpolateValue(first.name, jvm.Type.ArrayOf(first.tpe))}${arrayCast(first)})"
                  rest.foldLeft(firstUnnest) { (acc, col) =>
                    code"$acc, unnest(${runtimeInterpolateValue(col.name, jvm.Type.ArrayOf(col.tpe))}${arrayCast(col)})"
                  }
                case Nil => sys.error("No columns")
              }
              code"""|delete
                     |from $qRelName
                     |where ($colsStr)
                     |in ($selectClause)
                     |""".stripMargin
            }
            jvm.Body.Stmts(vals.toList :+ jvm.Return(code"$sql.update().runUnchecked(c)").code)
        }

      case DbType.MariaDB | DbType.MySQL =>
        id match {
          case x: IdComputed.Unary =>
            // MariaDB: Build IN clause with individual values
            val colName = adapter.quoteIdent(x.col.dbName.value)
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodeId = code"${lookupDbTypeInstance(x)}.encode($idIdent)"
            val addStmt = jvm.Body.Expr(code"$fragments.add($encodeId)")
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(TypesJava.ArrayList.of(Fragment)), jvm.New(TypesJava.ArrayList.of(Fragment), Nil)),
                lang.arrayForEach(idsParam.name.code, idIdent, addStmt),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(s"delete from $qRelNameStr where $colName in (")}), $Fragment.comma($fragments), $Fragment.lit(${jvm
                      .StrLit(s")")})).update().runUnchecked(c)"""
                )
              )
            )

          case x: IdComputed.Composite =>
            // MariaDB: (col1, col2) IN ((val1, val2), (val3, val4), ...)
            val colNamesJoined = x.cols.toList.map(c => adapter.quoteIdent(c.dbCol.name.value)).mkString(", ")
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodedCols = x.cols.toList.map(c => code"${lookupDbTypeInstance(c)}.encode(${lang.prop(idIdent.code, c.name)})")
            val commaLit = code"$Fragment.lit(${jvm.StrLit(", ")})"
            val openParen = code"$Fragment.lit(${jvm.StrLit("(")})"
            val closeParen = code"$Fragment.lit(${jvm.StrLit(")")})"
            val tupleArgs = encodedCols.flatMap(e => List(commaLit, e)).drop(1)
            val tupleExpr = code"$Fragment.interpolate($openParen, ${tupleArgs.mkCode(", ")}, $closeParen)"
            val addStmt = jvm.Body.Expr(code"$fragments.add($tupleExpr)")
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(TypesJava.ArrayList.of(Fragment)), jvm.New(TypesJava.ArrayList.of(Fragment), Nil)),
                lang.arrayForEach(idsParam.name.code, idIdent, addStmt),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"delete from $qRelNameStr where ($colNamesJoined) in ("
                    )}), $Fragment.comma($fragments), $Fragment.lit(${jvm.StrLit(")")})).update().runUnchecked(c)"""
                )
              )
            )
        }
    }
  }

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

  override def repoImpl(repoMethod: RepoMethod): jvm.Body =
    repoMethod match {
      case RepoMethod.SelectBuilder(relName, fieldsType, rowType) =>
        val structure = prop(code"$fieldsType", "structure")
        jvm.Body.Expr(code"${jvm.Type.dsl.SelectBuilder}.of(${jvm.StrLit(quotedRelNameStr(relName))}, $structure, $rowType.$rowParserName, ${adapter.dialectRef})")
      case RepoMethod.SelectAll(relName, cols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(cols, isRead = true)}
                 |from ${quotedRelName(relName)}
                 |""".stripMargin
        }
        jvm.Body.Expr(code"""$sql.query(${rowParserFor(rowType)}.all()).runUnchecked(c)""")

      case RepoMethod.SelectById(relName, cols, id, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(cols, isRead = true)}
                 |from ${quotedRelName(relName)}
                 |where ${matchId(id)}""".stripMargin
        }
        jvm.Body.Expr(code"""$sql.query(${rowParserFor(rowType)}.first()).runUnchecked(c)""")

      case RepoMethod.SelectByIds(relName, cols, computedId, idsParam, rowType) =>
        selectByIdsBody(relName, cols, computedId, idsParam, rowType)

      case RepoMethod.SelectByIdsTracked(x) =>
        val ret = jvm.Ident("ret")
        val row = jvm.Ident("row")
        val selectByIdsCall = jvm.Call.withImplicits(
          code"selectByIds",
          List(jvm.Arg.Pos(x.idsParam.name.code)),
          List(jvm.Arg.Pos(code"c"))
        )
        // Use IgnoreResult to discard the return value of map.put() for Scala Consumer compatibility
        val putCall = jvm.Body.Expr(jvm.IgnoreResult(code"ret.put(${idAccessOnRow(row.code, x.idComputed)}, $row)"))
        val forEachLambda = jvm.Lambda(row, putCall)
        jvm.Body.Stmts(
          List(
            jvm.LocalVar(ret, Some(lang.MapOps.mutableImpl.of(x.idComputed.tpe, x.rowType)), lang.MapOps.newMutableMap(x.idComputed.tpe, x.rowType)),
            lang.ListType.forEach(selectByIdsCall, forEachLambda.code),
            jvm.Return(ret)
          )
        )

      case RepoMethod.UpdateBuilder(relName, fieldsType, rowType) =>
        val structure = prop(code"$fieldsType", "structure")
        jvm.Body.Expr(code"${jvm.Type.dsl.UpdateBuilder}.of(${jvm.StrLit(quotedRelNameStr(relName))}, $structure, $rowType.$rowParserName.all(), ${adapter.dialectRef})")

      case RepoMethod.SelectByUnique(relName, keyColumns, allCols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(allCols, isRead = true)}
                 |from ${quotedRelName(relName)}
                 |where ${keyColumns.map(c => code"${quotedColName(c)} = ${runtimeInterpolateValue(c.name, c.tpe)}").mkCode(" AND ")}
                 |""".stripMargin
        }

        jvm.Body.Expr(code"$sql.query(${rowParserFor(rowType)}.first()).runUnchecked(c)")

      case RepoMethod.SelectByFieldValues(relName, cols, fieldValue, fieldValueOrIdsParam, rowType) =>
        val where = {
          val x: jvm.Ident = jvm.Ident("x")
          val fv: jvm.Ident = jvm.Ident("fv")

          val typeSwitch = jvm.TypeSwitch(
            fv,
            cols.toList.map { col =>
              jvm.TypeSwitch.Case(fieldValue / col.name, x, FR(code"${quotedColName(col)} = ${runtimeInterpolateValue(prop(x.code, "value"), col.tpe)}"))
            }
          )
          val mappedList = lang.ListType.map(fieldValueOrIdsParam.name.code, jvm.Lambda(fv, jvm.Body.Expr(typeSwitch)))
          jvm.Value(
            Nil,
            jvm.Ident("where"),
            Fragment,
            Some(code"""|$Fragment.whereAnd(
                     |  $mappedList
                     |)""".stripMargin),
            isLazy = false,
            isOverride = false
          )
        }

        val sql = SQL(code"""select ${dbNames(cols, isRead = true)} from ${quotedRelName(relName)} ${jvm.RuntimeInterpolation(code"${where.name}")}""")
        jvm.Body.Stmts(
          List(
            where,
            jvm.Return(code"$sql.query(${rowParserFor(rowType)}.all()).runUnchecked(c)")
          )
        )

      case RepoMethod.UpdateFieldValues(relName, id, varargsParam, fieldValue, cases0, _) =>
        val updates = {
          val x: jvm.Ident = jvm.Ident("x")
          val fv: jvm.Ident = jvm.Ident("fv")

          val typeSwitch = jvm.TypeSwitch(
            fv,
            cases0.toList.map { col =>
              jvm.TypeSwitch.Case(fieldValue / col.name, x, FR(code"${quotedColName(col)} = ${runtimeInterpolateValue(prop(x.code, "value"), col.tpe)}"))
            }
          )
          val mapper = jvm.Lambda(fv, typeSwitch.code)
          val mappedList = lang.ListType.map(varargsParam.name.code, mapper.code)
          jvm.Value(Nil, jvm.Ident("updates"), lang.ListType.tpe.of(Fragment), Some(mappedList), isLazy = false, isOverride = false)
        }

        val sql = SQL {
          code"""|update ${quotedRelName(relName)}
                 |${jvm.RuntimeInterpolation(code"$Fragment.set(${updates.name})")}
                 |where ${matchId(id)}""".stripMargin
        }

        jvm.Body.Stmts(
          List(
            updates,
            jvm.Return(
              jvm.IfExpr(
                jvm.ApplyNullary(updates.name, jvm.Ident("isEmpty")),
                code"false",
                code"""|$sql
                     |  .update().runUnchecked(c) > 0""".stripMargin
              )
            )
          )
        )
      case RepoMethod.Update(relName, _, id, param, colsUnsaved) =>
        val sql = SQL {
          val setCols = colsUnsaved.map { col =>
            code"${quotedColName(col)} = ${runtimeInterpolateValue(prop(param.name.code, col.name), col.tpe)}${adapter.columnWriteCast(col)}"
          }
          code"""|update ${quotedRelName(relName)}
                 |set ${setCols.mkCode(",\n")}
                 |where ${matchId(id)}""".stripMargin
        }
        jvm.Body.Stmts(
          List(
            jvm.Value(Nil, id.paramName, id.tpe, Some(idAccessOnRow(param.name.code, id)), isLazy = false, isOverride = false),
            jvm.Return(code"$sql.update().runUnchecked(c) > 0")
          )
        )

      case RepoMethod.Insert(relName, cols, unsavedParam, rowType, writeableColumnsWithId) =>
        val values = writeableColumnsWithId.map { c =>
          runtimeInterpolateValue(prop(unsavedParam.name.code, c.name), c.tpe).code ++ adapter.columnWriteCast(c)
        }
        val sql = SQL {
          code"""|insert into ${quotedRelName(relName)}(${dbNames(writeableColumnsWithId, isRead = false)})
                 |values (${values.mkCode(", ")})
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        jvm.Body.Expr(
          code"""|$sql
                 |  .updateReturning(${rowParserFor(rowType)}.exactlyOne()).runUnchecked(c)"""
        )
      case RepoMethod.Upsert(relName, cols, id, unsavedParam, rowType, writeableColumnsWithId) =>
        val values = writeableColumnsWithId.map { c =>
          runtimeInterpolateValue(prop(unsavedParam.name.code, c.name), c.tpe).code ++ adapter.columnWriteCast(c)
        }

        // Generate the ON CONFLICT/ON DUPLICATE KEY UPDATE clause - delegated to adapter
        val nonPkCols = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name))
        val conflictUpdate = nonPkCols match {
          case Nil =>
            // When all columns are PK, use a no-op update to ensure RETURNING works
            adapter.conflictNoOpClause(id.cols.head, quotedColName)
          case cols =>
            adapter.conflictUpdateClause(cols, quotedColName)
        }

        val sql = SQL {
          adapter.upsertSql(
            tableName = quotedRelName(relName),
            columns = dbNames(writeableColumnsWithId, isRead = false),
            idColumns = dbNames(id.cols, isRead = false),
            values = values.mkCode(", "),
            conflictUpdate = conflictUpdate,
            returning = Some(dbNames(cols, isRead = true))
          )
        }

        jvm.Body.Expr(
          code"""|$sql
                 |  .updateReturning($rowType.$rowParserName.exactlyOne())
                 |  .runUnchecked(c)"""
        )
      case RepoMethod.UpsertBatch(relName, cols, id, rowType, writeableColumnsWithId) =>
        // For MariaDB, we need to include ALL columns (including AUTO_INCREMENT) in the INSERT
        // because upsertBatch operates on Row type (not RowUnsaved) which has the ID.
        // MariaDB allows explicit values in AUTO_INCREMENT columns.
        val insertCols = if (!adapter.supportsArrays) cols else writeableColumnsWithId
        val nonPkCols = insertCols.toList.filterNot(c => id.cols.exists(_.name == c.name))
        val conflictAction = nonPkCols match {
          case Nil =>
            adapter.conflictNoOpClause(id.cols.head, quotedColName)
          case updateCols =>
            adapter.conflictUpdateClause(updateCols, quotedColName)
        }

        val sql = SQL {
          adapter.upsertSql(
            tableName = quotedRelName(relName),
            columns = dbNames(insertCols, isRead = false),
            idColumns = dbNames(id.cols, isRead = false),
            values = insertCols.map(c => code"?${adapter.columnWriteCast(c)}").mkCode(", "),
            conflictUpdate = conflictAction,
            returning = Some(dbNames(cols, isRead = true))
          )
        }

        // MariaDB: RETURNING with batch doesn't work via getGeneratedKeys(), so we execute each row individually
        // PostgreSQL: Use updateManyReturning which works correctly with batch + RETURNING
        if (!adapter.supportsArrays) {
          jvm.Body.Expr(
            code"""|$sql
                   |  .updateReturningEach($rowType.$rowParserName, unsaved)
                   |  .runUnchecked(c)""".stripMargin
          )
        } else {
          jvm.Body.Expr(
            code"""|$sql
                   |  .updateManyReturning($rowType.$rowParserName, unsaved)
                   |  .runUnchecked(c)""".stripMargin
          )
        }

      case RepoMethod.UpsertStreaming(relName, id, rowType, writeableColumnsWithId) =>
        // UpsertStreaming uses PostgreSQL-specific COPY syntax - not supported on MariaDB
        if (!adapter.supportsCopyStreaming) {
          val msg = jvm.StrLit("UpsertStreaming not supported for this database")
          jvm.Body.Expr(code"throw new UnsupportedOperationException($msg)")
        } else {
          // PostgreSQL-specific conflict action syntax
          val conflictAction = writeableColumnsWithId.toList.filterNot(c => id.cols.exists(_.name == c.name)) match {
            case Nil => code"do nothing"
            case nonEmpty =>
              code"""|do update set
                     |  ${nonEmpty.map { c => code"${quotedColName(c)} = EXCLUDED.${quotedColName(c)}" }.mkCode(",\n")}""".stripMargin
          }
          val tempTablename = s"${relName.name}_TEMP"

          val copySql = lang.s(code"copy $tempTablename(${dbNames(writeableColumnsWithId, isRead = false)}) from stdin")

          val mergeSql = SQL {
            code"""|insert into ${quotedRelName(relName)}(${dbNames(writeableColumnsWithId, isRead = false)})
                   |select * from $tempTablename
                   |on conflict (${dbNames(id.cols, isRead = false)})
                   |$conflictAction
                   |;
                   |drop table $tempTablename;""".stripMargin
          }
          jvm.Body.Stmts(
            List(
              jvm.IgnoreResult(code"${SQL(code"create temporary table $tempTablename (like ${quotedRelName(relName)}) on commit drop")}.update().runUnchecked(c)"),
              jvm.IgnoreResult(code"$streamingInsert.insertUnchecked($copySql, batchSize, unsaved, c, $rowType.${adapter.textFieldName})"),
              jvm.Return(code"$mergeSql.update().runUnchecked(c)")
            )
          )
        }

      case RepoMethod.InsertUnsaved(relName, cols, unsaved, unsavedParam, _, rowType) =>
        // Use ArrayList as declared type for Kotlin compatibility (List is read-only in Kotlin)
        val columns = jvm.Value(
          Nil,
          jvm.Ident("columns"),
          TypesJava.ArrayList.of(FragmentLiteral),
          Some(jvm.New(TypesJava.ArrayList.of(FragmentLiteral), Nil)),
          isLazy = false,
          isOverride = false
        )
        val values = jvm.Value(Nil, jvm.Ident("values"), TypesJava.ArrayList.of(Fragment), Some(jvm.New(TypesJava.ArrayList.of(Fragment), Nil)), isLazy = false, isOverride = false)

        val cases0 = unsaved.normalColumns.flatMap { col =>
          val value = FR(code"${runtimeInterpolateValue(prop(unsavedParam.name.code, col.name), col.tpe)}${adapter.columnWriteCast(col)}")
          val quotedName = adapter.quoteIdent(col.dbName.value)
          List(
            jvm.IgnoreResult(code"${columns.name}.add($Fragment.lit(${jvm.StrLit(quotedName)}))").code,
            jvm.IgnoreResult(code"${values.name}.add($value)").code
          )
        }

        val cases1 = unsaved.defaultedCols.map { case ComputedRowUnsaved.DefaultedCol(col @ ComputedColumn(_, ident, _, _), origType) =>
          val value = FR(code"${runtimeInterpolateValue(code"value", origType)}${adapter.columnWriteCast(col)}")
          val quotedName = adapter.quoteIdent(col.dbName.value)
          val valueIdent = jvm.Ident("value")
          val byName0 = jvm.ByName(jvm.Body.Stmts(Nil))
          // Multi-statement lambda body
          val lambda1BodyStatements = List(
            jvm.IgnoreResult(code"${columns.name}.add($Fragment.lit(${jvm.StrLit(quotedName)}))").code,
            jvm.IgnoreResult(code"${values.name}.add($value)").code
          )
          val lambda1 = jvm.Lambda(List(jvm.LambdaParam(valueIdent)), jvm.Body.Stmts(lambda1BodyStatements))
          code"""|${prop(unsavedParam.name.code, ident)}.visit(
                 |  $byName0,
                 |  $lambda1
                 |);""".stripMargin
        }

        val sql = SQL {
          code"""|insert into ${quotedRelName(relName)}(${jvm.RuntimeInterpolation(code"$Fragment.comma(${columns.name})")})
                 |values (${jvm.RuntimeInterpolation(code"$Fragment.comma(${values.name})")})
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        val sqlEmpty = SQL {
          code"""|insert into ${quotedRelName(relName)} default values
                 |returning ${dbNames(cols, isRead = true)}
                 |""".stripMargin
        }
        val q = {
          val body = if (unsaved.normalColumns.isEmpty) jvm.IfExpr(jvm.ApplyNullary(columns.name, jvm.Ident("isEmpty")), sqlEmpty, sql) else sql
          jvm.Value(Nil, jvm.Ident("q"), Fragment, Some(body), isLazy = false, isOverride = false)
        }

        jvm.Body.Stmts(
          List[List[jvm.Code]](
            List(columns, values),
            cases0,
            cases1,
            List(
              q,
              jvm.Return(code"q.updateReturning($rowType.$rowParserName.exactlyOne()).runUnchecked(c)")
            )
          ).flatten
        )

      case RepoMethod.InsertStreaming(relName, rowType, writeableColumnsWithId) =>
        val sql = lang.s(code"COPY ${quotedRelName(relName)}(${dbNames(writeableColumnsWithId, isRead = false)}) FROM STDIN")
        jvm.Body.Expr(code"$streamingInsert.insertUnchecked($sql, batchSize, unsaved, c, $rowType.${adapter.textFieldName})")
      case RepoMethod.InsertUnsavedStreaming(relName, unsaved) =>
        val sql = lang.s(code"COPY ${quotedRelName(relName)}(${dbNames(unsaved.unsavedCols, isRead = false)}) FROM STDIN (DEFAULT '${DbLibTextSupport.DefaultValue}')")
        jvm.Body.Expr(code"$streamingInsert.insertUnchecked($sql, batchSize, unsaved, c, ${unsaved.tpe}.${adapter.textFieldName})")
      case RepoMethod.DeleteBuilder(relName, fieldsType, _) =>
        val structure = prop(code"$fieldsType", "structure")
        jvm.Body.Expr(code"${jvm.Type.dsl.DeleteBuilder}.of(${jvm.StrLit(quotedRelNameStr(relName))}, $structure, ${adapter.dialectRef})")
      case RepoMethod.Delete(relName, id) =>
        val sql = SQL {
          code"""delete from ${quotedRelName(relName)} where ${matchId(id)}"""
        }
        jvm.Body.Expr(code"$sql.update().runUnchecked(c) > 0")
      case RepoMethod.DeleteByIds(relName, computedId, idsParam) =>
        deleteByIdsBody(relName, computedId, idsParam)

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
                       |select ${cols.map(c => code"$row.${c.dbCol.parsedName.originalName.code}${adapter.columnReadCast(c)}").mkCode(", ")}
                       |from $row""".stripMargin
            }

          code"${SQL(renderedWithCasts)}.query(${rowParserFor(rowName)}.all()).runUnchecked(c)"
        }

        jvm.Body.Expr(ret.getOrElse(code"${SQL(renderedScript)}.update().runUnchecked(c)"))
    }

  override def mockRepoImpl(id: IdComputed, repoMethod: RepoMethod, maybeToRow: Option[jvm.Param[jvm.Type.Function1]]): jvm.Body = {
    val mapCode = jvm.Ident("map").code
    val MapOps = lang.MapOps
    val Opt = lang.Optional

    // Helper to generate proper ID access (method call for composite, property for unary)
    def idAccess(on: jvm.Code): jvm.Code = id match {
      case _: IdComputed.Composite => lang.nullaryMethodCall(on, id.paramName)
      case _: IdComputed.Unary     => prop(on, id.paramName)
    }

    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, _) =>
        // Java: new SelectBuilderMock<>(structure, () -> new ArrayList<>(map.values()), SelectParams.empty())
        val supplierLambda = jvm.Lambda(jvm.Body.Expr(MapOps.valuesToList(mapCode)))
        val structure = prop(code"$fieldsType", "structure")
        jvm.Body.Expr(
          jvm
            .New(
              jvm.InferredTargs(jvm.Type.dsl.SelectBuilderMock),
              List(jvm.Arg.Pos(structure), jvm.Arg.Pos(supplierLambda), jvm.Arg.Pos(code"${jvm.Type.dsl.SelectParams}.empty()"))
            )
            .code
        )
      case RepoMethod.SelectAll(_, _, _) =>
        jvm.Body.Expr(MapOps.valuesToList(mapCode))
      case RepoMethod.SelectById(_, _, id, _) =>
        jvm.Body.Expr(MapOps.get(mapCode, id.paramName.code))
      case RepoMethod.SelectByIds(_, _, _, idsParam, rowType) =>
        val idVar = jvm.Ident("id")
        val resultVar = jvm.Ident("result")
        jvm.Body.Stmts(
          List(
            jvm.LocalVar(resultVar, None, jvm.New(TypesJava.ArrayList.of(rowType), Nil).code),
            lang.arrayForEach(
              idsParam.name.code,
              idVar,
              jvm.Body.Stmts(
                List(
                  jvm.LocalVar(jvm.Ident("opt"), None, MapOps.get(mapCode, idVar.code)),
                  jvm.If(
                    lang.Optional.isDefined(jvm.Ident("opt").code),
                    jvm.IgnoreResult(code"$resultVar.add(${lang.Optional.get(jvm.Ident("opt").code)})").code
                  )
                )
              )
            ),
            jvm.Return(resultVar)
          )
        )
      case RepoMethod.SelectByIdsTracked(x) =>
        val rowVar = jvm.Ident("row")
        // Use typed Lambda to ensure proper type inference in Scala with Java collectors
        val keyExtractor = jvm.Lambda(List(jvm.LambdaParam.typed(rowVar, x.rowType)), jvm.Body.Expr(idAccessOnRow(rowVar.code, x.idComputed)))
        val methodCall = jvm.Call.withImplicits(
          x.methodName.code,
          List(jvm.Arg.Pos(x.idsParam.name.code)),
          List(jvm.Arg.Pos(code"c"))
        )
        jvm.Body.Expr(
          lang.ListType.collectToMap(
            methodCall,
            keyExtractor.code,
            x.idComputed.tpe,
            x.rowType
          )
        )
      case RepoMethod.SelectByUnique(_, keyColumns, _, _) =>
        val vVar = jvm.Ident("v")
        val predicateBody = keyColumns
          .map { c =>
            lang.equals(c.name.code, prop(vVar.code, c.name))
          }
          .mkCode(" && ")
        val predicateLambda = jvm.Lambda(vVar, predicateBody)
        jvm.Body.Expr(lang.ListType.findFirst(MapOps.valuesToList(mapCode), predicateLambda.code))

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
        // Java: new UpdateBuilderMock<>(structure, () -> new ArrayList<>(map.values()), UpdateParams.empty(), row -> row)
        val rowIdent = jvm.Ident("row")
        jvm.Body.Expr(
          jvm
            .New(
              jvm.InferredTargs(jvm.Type.dsl.UpdateBuilderMock),
              List(
                jvm.Arg.Pos(prop(code"$fieldsType", "structure")),
                jvm.Arg.Pos(jvm.Lambda(jvm.Body.Expr(MapOps.valuesToList(mapCode)))),
                jvm.Arg.Pos(code"${jvm.Type.dsl.UpdateParams}.empty()"),
                jvm.Arg.Pos(jvm.Lambda(rowIdent, rowIdent.code))
              )
            )
            .code
        )
      case RepoMethod.Update(_, _, _, param, _) =>
        val paramIdAccess = idAccess(param.name.code)
        val shouldUpdateVar = jvm.Ident("shouldUpdate")
        val oldRowVar = jvm.Ident("oldRow")

        // Build the predicate lambda: oldRow -> oldRow != param.name
        val predicateBody = lang.notEquals(oldRowVar.code, param.name.code)
        val predicateLambda = jvm.Lambda(oldRowVar, predicateBody)

        // shouldUpdate = map.get(id).filter(predicate).isDefined
        val filtered = Opt.filter(MapOps.get(mapCode, paramIdAccess), predicateLambda.code)
        val shouldUpdateExpr = Opt.isDefined(filtered)

        val localVar = jvm.LocalVar(shouldUpdateVar, None, shouldUpdateExpr)
        val ifStmt = jvm.If(shouldUpdateVar.code, code"${MapOps.putVoid(mapCode, paramIdAccess, param.name.code)}${lang.`;`}")

        jvm.Body.Stmts(
          List(
            localVar.code,
            ifStmt.code,
            jvm.Return(shouldUpdateVar)
          )
        )
      case RepoMethod.Insert(_, _, unsavedParam, _, _) =>
        val unsavedIdAccess = idAccess(unsavedParam.name.code)
        val throwStmt = jvm.Throw(TypesJava.RuntimeException.construct(lang.s(code"id $$${unsavedIdAccess} already exists")))
        val ifStmt = jvm.If(MapOps.contains(mapCode, unsavedIdAccess), throwStmt.code)
        jvm.Body.Stmts(
          List(
            ifStmt.code,
            MapOps.putVoid(mapCode, unsavedIdAccess, unsavedParam.name.code),
            jvm.Return(unsavedParam.name.code)
          )
        )
      case RepoMethod.Upsert(_, _, _, unsavedParam, _, _) =>
        val unsavedIdAccess = idAccess(unsavedParam.name.code)
        jvm.Body.Stmts(
          List(
            MapOps.putVoid(mapCode, unsavedIdAccess, unsavedParam.name.code),
            jvm.Return(unsavedParam.name.code)
          )
        )
      case RepoMethod.UpsertStreaming(_, localId, _, _) =>
        val rowVar = jvm.Ident("row")
        val countVar = jvm.Ident("count")
        val rowIdAccess = idAccessOnRow(rowVar.code, localId)
        val whileBody: List[jvm.Code] = List(
          jvm.LocalVar(rowVar, None, code"unsaved.next()").code,
          MapOps.putVoid(mapCode, rowIdAccess, rowVar.code),
          countVar.code ++ code" = " ++ countVar.code ++ code" + 1"
        )
        jvm.Body.Stmts(
          List(
            code"var $countVar = 0",
            jvm.While(code"unsaved.hasNext()", whileBody).code,
            jvm.Return(countVar.code)
          )
        )
      case RepoMethod.UpsertBatch(_, _, localId, rowType, _) =>
        val rowVar = jvm.Ident("row")
        val resultVar = jvm.Ident("result")
        val rowIdAccess = idAccessOnRow(rowVar.code, localId)
        val addCall = jvm.IgnoreResult(resultVar.code.invoke("add", rowVar.code))
        val whileBody: List[jvm.Code] = List(
          jvm.LocalVar(rowVar, None, code"unsaved.next()").code,
          MapOps.putVoid(mapCode, rowIdAccess, rowVar.code),
          addCall.code
        )
        jvm.Body.Stmts(
          List(
            jvm.LocalVar(resultVar, None, jvm.New(TypesJava.ArrayList.of(rowType), Nil).code).code,
            jvm.While(code"unsaved.hasNext()", whileBody).code,
            jvm.Return(resultVar.code)
          )
        )
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _) =>
        val insertCall = jvm.Call.withImplicits(
          code"insert",
          List(jvm.Arg.Pos(jvm.Apply1(maybeToRow.get, unsavedParam.name))),
          List(jvm.Arg.Pos(code"c"))
        )
        jvm.Body.Expr(insertCall)
      case RepoMethod.InsertStreaming(_, _, _) =>
        val rowVar = jvm.Ident("row")
        val countVar = jvm.Ident("count")
        val rowIdAccess = idAccess(rowVar.code)
        val whileBody: List[jvm.Code] = List(
          jvm.LocalVar(rowVar, None, code"unsaved.next()").code,
          MapOps.putVoid(mapCode, rowIdAccess, rowVar.code),
          countVar.code ++ code" = " ++ countVar.code ++ code" + 1L"
        )
        jvm.Body.Stmts(
          List(
            code"var $countVar = 0L",
            jvm.While(code"unsaved.hasNext()", whileBody).code,
            jvm.Return(countVar.code)
          )
        )
      case RepoMethod.InsertUnsavedStreaming(_, _) =>
        val unsavedRowVar = jvm.Ident("unsavedRow")
        val rowVar = jvm.Ident("row")
        val countVar = jvm.Ident("count")
        val rowIdAccess = idAccess(rowVar.code)
        val whileBody: List[jvm.Code] = List(
          jvm.LocalVar(unsavedRowVar, None, code"unsaved.next()").code,
          jvm.LocalVar(rowVar, None, jvm.Apply1(maybeToRow.get, unsavedRowVar)).code,
          MapOps.putVoid(mapCode, rowIdAccess, rowVar.code),
          countVar.code ++ code" = " ++ countVar.code ++ code" + 1L"
        )
        jvm.Body.Stmts(
          List(
            code"var $countVar = 0L",
            jvm.While(code"unsaved.hasNext()", whileBody).code,
            jvm.Return(countVar.code)
          )
        )
      case RepoMethod.DeleteBuilder(_, fieldsType, _) =>
        // Java: new DeleteBuilderMock<>(structure, () -> new ArrayList<>(map.values()), DeleteParams.empty(), row -> row.id(), id -> map.remove(id))
        val rowVar = jvm.Ident("row")
        val idVar = jvm.Ident("id")
        // Use removeVoid for Consumer - returns void instead of Optional
        jvm.Body.Expr(
          jvm
            .New(
              jvm.InferredTargs(jvm.Type.dsl.DeleteBuilderMock),
              List(
                jvm.Arg.Pos(prop(code"$fieldsType", "structure")),
                jvm.Arg.Pos(jvm.Lambda(jvm.Body.Expr(MapOps.valuesToList(mapCode)))),
                jvm.Arg.Pos(code"${jvm.Type.dsl.DeleteParams}.empty()"),
                jvm.Arg.Pos(jvm.Lambda(rowVar, idAccess(rowVar.code))),
                jvm.Arg.Pos(jvm.Lambda(idVar, MapOps.removeVoid(mapCode, idVar.code)))
              )
            )
            .code
        )

      case RepoMethod.Delete(_, id) =>
        jvm.Body.Expr(Opt.isDefined(MapOps.remove(mapCode, id.paramName.code)))
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        val idVar = jvm.Ident("id")
        val countVar = jvm.Ident("count")
        val forEachBody = jvm.Body.Stmts(
          List(
            jvm
              .If(
                Opt.isDefined(MapOps.remove(mapCode, idVar.code)),
                code"$countVar = $countVar + 1${lang.`;`}"
              )
              .code
          )
        )
        jvm.Body.Stmts(
          List(
            code"var $countVar = 0",
            lang.arrayForEach(idsParam.name.code, idVar, forEachBody),
            jvm.Return(countVar.code)
          )
        )
      case RepoMethod.SqlFile(_) =>
        // should not happen (tm)
        jvm.Body.Expr(code"???")
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
      body = jvm.Body.Expr(jvm.Call.withImplicits(code"($newRepo).insert", List(jvm.Arg.Pos(newRow)), List(jvm.Arg.Pos(c.name)))),
      isOverride = false,
      isDefault = false
    )
  }

  override val defaultedInstance: List[jvm.Given] = {
    def textInstance = {
      val T = jvm.Type.Abstract(jvm.Ident("T"))
      val textofT = jvm.Ident("t")
      val ot = jvm.Ident("ot")
      val sb = jvm.Ident("sb")
      val value = jvm.Ident("value")
      // Multi-statement body - renders with {} in Java, without in Kotlin
      val innerLambda0Body = jvm.Body.Stmts(List(jvm.IgnoreResult(code"""$sb.append("${DbLibTextSupport.DefaultValue}")""").code))
      val innerLambda0 = jvm.ByName(innerLambda0Body)
      val innerLambda1 = jvm.Lambda(value, code"$textofT.unsafeEncode($value, $sb)")
      val outerLambda = jvm.Lambda(ot, sb, code"$ot.visit($innerLambda0, $innerLambda1)")
      jvm.Given(
        tparams = List(T),
        name = adapter.textFieldName,
        implicitParams = List(jvm.Param(textofT, adapter.TextClass.of(T))),
        tpe = adapter.TextClass.of(default.Defaulted.of(T)),
        body = code"""${adapter.TextClass}.instance($outerLambda)"""
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
      // Skip array instance for databases that don't support arrays
      if (!adapter.supportsArrays) None
      else
        Some(
          jvm.Given(
            tparams = Nil,
            name = pgTypeArrayName,
            implicitParams = Nil,
            tpe = adapter.TypeClass.of(arrayWrapper),
            body = {
              val underlyingCls = jvm.ClassOf(underlying)
              val xs = jvm.Ident("xs")
              val lambda1 = jvm.Lambda(xs, lang.arrayMap(xs.code, create, jvm.ClassOf(wrapperType).code))
              val lambda2 = jvm.Lambda(xs, lang.arrayMap(xs.code, extract, underlyingCls.code))
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
          name = adapter.typeFieldName,
          implicitParams = Nil,
          tpe = adapter.TypeClass.of(wrapperType),
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
      // Skip array instance for databases that don't support arrays
      if (!adapter.supportsArrays) None
      else
        Some(
          jvm.Given(
            tparams = Nil,
            name = pgTypeArrayName,
            implicitParams = Nil,
            tpe = adapter.TypeClass.of(jvm.Type.ArrayOf(wrapperType)),
            body = {
              val lambda1 = jvm.Lambda(xs, lang.arrayMap(xs.code, jvm.ConstructorMethodRef(wrapperType).code, jvm.ClassOf(wrapperType).code))
              val lambda2 = jvm.Lambda(xs, lang.arrayMap(xs.code, jvm.FieldGetterRef(wrapperType, jvm.Ident("value")).code, jvm.ClassOf(underlying).code))
              val base = code"${lookupPgType(jvm.Type.ArrayOf(underlying))}.bimap($lambda1, $lambda2)"
              overrideDbType.fold(base)(t => maybeRename(base, t + "[]"))
            }
          )
        ),
      Some(
        jvm.Given(
          tparams = Nil,
          name = adapter.typeFieldName,
          implicitParams = Nil,
          tpe = adapter.TypeClass.of(wrapperType),
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
          val pgTypes = cols.toList.map(x => lookupDbTypeInstance(x)).mkCode(code", ")
          val row = jvm.Ident("row")
          val newArray = lang.arrayOf(cols.toList.map(c => lang.propertyGetterAccess(row.code, c.name)))
          val encodeLambda = jvm.Lambda(row, newArray)
          // For Kotlin, we need to use a lambda instead of constructor reference
          // because Kotlin's ::ClassName doesn't SAM-convert to Java functional interfaces
          // Also, we must NOT use typed lambda parameters because Kotlin won't SAM-convert
          // typed lambdas to Java functional interfaces with generic type parameters
          val decodeLambda: jvm.Code = lang match {
            case LangKotlin =>
              val params = cols.toList.zipWithIndex.map { case (col, i) =>
                jvm.Param(jvm.Ident(s"t$i"), col.tpe)
              }
              val args = params.zip(cols.toList).map { case (p, col) =>
                val notNull = jvm.NotNull(p.name).code
                // Use base() to unwrap Commented/UserDefined wrappers
                jvm.Type.base(col.tpe) match {
                  case TypesJava.String => code"$notNull as ${col.tpe}"
                  case _                => notNull
                }
              }
              // Use untyped params for SAM conversion compatibility
              jvm.Lambda(params.map(p => jvm.LambdaParam(p.name)), jvm.Body.Expr(tpe.construct(args: _*)))
            case _ =>
              jvm.ConstructorMethodRef(tpe)
          }
          code"$RowParsers.of($pgTypes, $decodeLambda, $encodeLambda)"
        },
        isLazy = false,
        isOverride = false
      )
    }
    rowType match {
      case DbLib.RowType.Writable =>
        val text = if (enableStreamingInserts) {
          val row = jvm.Ident("row")
          val sb = jvm.Ident("sb")
          val textCols: NonEmptyList[jvm.Code] = cols.map { col =>
            val text = col.tpe match {
              case jvm.Type.TApply(default.Defaulted, List(innerType)) =>
                // For Defaulted[T], we need to look up the type for T (the inner type)
                val innerPgText = jvm.ApplyNullary(lookupDbTypeInstance(innerType, col.dbCol.tpe), adapter.textFieldName)
                val target = jvm.Select(default.Defaulted.code, adapter.textFieldName)
                jvm.Call(target.code, List(jvm.Call.ArgGroup(List(jvm.Arg.Pos(innerPgText.code)), isImplicit = true))).code
              case _ =>
                jvm.ApplyNullary(lookupDbTypeInstance(col), adapter.textFieldName).code
            }
            code"$text.unsafeEncode($row.${col.name}, $sb)"
          }
          // Interleave encode statements with delimiter appends (delimiter BETWEEN columns, not before first)
          val delimiter = code"$sb.append(${adapter.TextClass}.DELIMETER)"
          val stmts = textCols.head :: textCols.tail.flatMap(stmt => List(delimiter, stmt))
          val lambdaBody = jvm.Body.Stmts(stmts)
          val body = code"${adapter.TextClass}.instance(${jvm.Lambda(row, sb, lambdaBody)})"
          Some(jvm.Given(tparams = Nil, name = adapter.textFieldName, implicitParams = Nil, tpe = adapter.TextClass.of(tpe), body = body))
        } else None

        text.toList
      case DbLib.RowType.ReadWriteable =>
        List(rowParser) ++ List(
          jvm.Given(tparams = Nil, name = adapter.textFieldName, implicitParams = Nil, tpe = adapter.TextClass.of(tpe), body = code"${adapter.TextClass}.from($rowParserName)")
        )
      case DbLib.RowType.Readable => List(rowParser)
    }
  }

  val PgRead = jvm.Type.Qualified("typo.runtime.PgRead")
  val PgWrite = jvm.Type.Qualified("typo.runtime.PgWrite")

  /** Map jdbc types to PgTypes for custom type bimap generation - only for simple types */
  def lookupPgTypeFromJdbc(jdbcType: jvm.Type): Option[jvm.Code] =
    jdbcType match {
      case TypesJava.BigDecimal                    => Some(code"${adapter.Types}.numeric")
      case TypesJava.Boolean | TypesKotlin.Boolean => Some(code"${adapter.Types}.bool")
      case TypesJava.Double | TypesKotlin.Double   => Some(code"${adapter.Types}.float8")
      case TypesJava.Float | TypesKotlin.Float     => Some(code"${adapter.Types}.float4")
      case TypesJava.Short | TypesKotlin.Short     => Some(code"${adapter.Types}.int2")
      case TypesJava.Integer | TypesKotlin.Int     => Some(code"${adapter.Types}.int4")
      case TypesJava.Long | TypesKotlin.Long       => Some(code"${adapter.Types}.int8")
      case TypesJava.String | TypesKotlin.String   => Some(code"${adapter.Types}.text")
      case TypesJava.UUID                          => Some(code"${adapter.Types}.uuid")
      case lang.ByteArrayType                      => Some(code"${adapter.Types}.bytea")
      case _                                       => None
    }

  /** Map jdbc types to PgText instances for custom type text encoding */
  def lookupPgTextFromJdbc(textType: jvm.Type): Option[jvm.Code] =
    textType match {
      case TypesJava.String                       => Some(code"${adapter.TextClass}.textString")
      case TypesJava.BigDecimal | lang.BigDecimal => Some(code"${adapter.TextClass}.textBigDecimal")
      case TypesJava.Boolean | lang.Boolean       => Some(code"${adapter.TextClass}.textBoolean")
      case TypesJava.Double | lang.Double         => Some(code"${adapter.TextClass}.textDouble")
      case TypesJava.Float | lang.Float           => Some(code"${adapter.TextClass}.textFloat")
      case TypesJava.Integer | lang.Int           => Some(code"${adapter.TextClass}.textInteger")
      case TypesJava.Long | lang.Long             => Some(code"${adapter.TextClass}.textLong")
      case TypesJava.Short | lang.Short           => Some(code"${adapter.TextClass}.textShort")
      case TypesJava.UUID                         => Some(code"${adapter.TextClass}.textUuid")
      case lang.ByteArrayType                     => Some(code"${adapter.TextClass}.textByteArray")
      case _                                      => None
    }

  override def customTypeInstances(ct: CustomType): List[jvm.Given] = {
    val v = jvm.Ident("v")

    // Convert Java types to Kotlin equivalents for lambda parameter types
    def kotlinifyType(tpe: jvm.Type): jvm.Type = lang match {
      case LangKotlin =>
        tpe match {
          case TypesJava.String  => lang.String
          case TypesJava.Integer => lang.Int
          case TypesJava.Long    => lang.Long
          case TypesJava.Short   => lang.Short
          case TypesJava.Boolean => lang.Boolean
          case TypesJava.Float   => lang.Float
          case TypesJava.Double  => lang.Double
          case other             => other
        }
      case _ => tpe
    }

    // pgType instance - either using bimap for simple types or full PgType.of for complex types
    val pgTypeBody: jvm.Code = lookupPgTypeFromJdbc(ct.toTypo.jdbcType) match {
      case Some(basePgType) =>
        // Simple type - use bimap, then rename to correct SQL type
        val sqlTypeLit = jvm.StrLit(ct.sqlType)
        // Use TypedLambda1 for Kotlin because type inference doesn't work for bimap lambdas
        // Convert Java types to Kotlin equivalents for lambda parameters
        val lambda1 = jvm.Lambda(List(jvm.LambdaParam.typed(v, kotlinifyType(ct.toTypo.jdbcType))), jvm.Body.Expr(ct.toTypo0(v)))
        val lambda2 = jvm.Lambda(List(jvm.LambdaParam.typed(v, ct.typoType)), jvm.Body.Expr(ct.fromTypo0(v)))
        code"$basePgType.bimap($lambda1, $lambda2).renamed($sqlTypeLit)"
      case None =>
        // Complex type (e.g., PGbox, PGpath) - use PgType.of with custom read/write
        val sqlTypeLit = jvm.StrLit(ct.sqlType)
        val readJdbcType = ct.toTypo.jdbcType
        val writeJdbcType = ct.fromTypo.jdbcType
        // Use TypedLambda1 for both lambdas - Kotlin needs explicit types, Scala needs them for passObjectToJdbc
        val lambda1 = jvm.Lambda(List(jvm.LambdaParam.typed(v, readJdbcType)), jvm.Body.Expr(ct.toTypo0(v)))
        val lambda2 = jvm.Lambda(List(jvm.LambdaParam.typed(v, ct.typoType)), jvm.Body.Expr(ct.fromTypo0(v)))
        // For Kotlin, add explicit type argument to PgType.of<T>()
        val pgTypeOf = lang match {
          case LangKotlin => code"${adapter.TypeClass}.of<${ct.typoType}>"
          case _          => code"${adapter.TypeClass}.of"
        }
        // Use GenericMethodCall for passObjectToJdbc to add type arg for Kotlin
        // Note: writeJdbcType is what the contramap produces (ct.fromTypo.jdbcType), not what we read
        val passObjectToJdbc = jvm.GenericMethodCall(PgWrite, jvm.Ident("passObjectToJdbc"), List(writeJdbcType), Nil)
        code"""$pgTypeOf(
              |  $sqlTypeLit,
              |  $PgRead.castJdbcObjectTo(${jvm.ClassOf(readJdbcType)}).map($lambda1),
              |  $passObjectToJdbc.contramap($lambda2),
              |  ${ct.typoType}.${adapter.textFieldName}
              |)""".stripMargin
    }

    val pgTypeInstance = jvm.Given(
      annotations = ct.pgTypeAnnotations,
      tparams = Nil,
      name = adapter.typeFieldName,
      implicitParams = Nil,
      tpe = adapter.TypeClass.of(ct.typoType),
      body = pgTypeBody
    )

    // pgText instance for streaming inserts
    val baseText = lookupPgTextFromJdbc(ct.toText.textType).getOrElse(sys.error(s"Unsupported text type for custom type: ${ct.toText.textType}"))

    val pgTextInstance = jvm.Given(
      tparams = Nil,
      name = adapter.textFieldName,
      implicitParams = Nil,
      tpe = adapter.TextClass.of(ct.typoType),
      body = code"$baseText.contramap(${jvm.Lambda(v, ct.toText.toTextType(v))})"
    )

    // pgTypeArray instance for array support
    // Uses PgType.array(read, write) which internally handles typename.array() and pgText.array()
    // Skip for databases that don't support arrays
    val pgTypeArrayInstance: Option[jvm.Given] =
      if (ct.forbidArray || !adapter.supportsArrays) None
      else {
        val toTypo = ct.toTypoInArray.getOrElse(ct.toTypo)
        val fromTypo = ct.fromTypoInArray.getOrElse(ct.fromTypo)
        val arrayType = jvm.Type.ArrayOf(ct.typoType)
        val xs = jvm.Ident("xs")

        // Determine the read method based on whether we need to cast JDBC array elements
        // Use TypedLambda1 for Kotlin to help with type inference
        // Convert Java types to Kotlin equivalents for lambda parameters
        val innerMapper = jvm.Lambda(List(jvm.LambdaParam.typed(v, kotlinifyType(toTypo.jdbcType))), jvm.Body.Expr(toTypo.toTypo(v, ct.typoType)))
        val arrayRead: jvm.Code = lookupPgTypeFromJdbc(toTypo.jdbcType) match {
          case Some(_) =>
            // Simple type - use massageJdbcArrayTo
            val jdbcArrayType = jvm.Type.ArrayOf(kotlinifyType(toTypo.jdbcType))
            val xsLambda = jvm.Lambda(List(jvm.LambdaParam.typed(xs, jdbcArrayType)), jvm.Body.Expr(lang.arrayMap(xs.code, innerMapper.code, jvm.ClassOf(ct.typoType).code)))
            code"$PgRead.massageJdbcArrayTo(${jvm.ClassOf(jdbcArrayType)}).map($xsLambda)"
          case None =>
            // Complex type - use castJdbcArrayTo
            val jdbcArrayType = jvm.Type.ArrayOf(toTypo.jdbcType)
            val xsLambda = jvm.Lambda(List(jvm.LambdaParam.typed(xs, jdbcArrayType)), jvm.Body.Expr(lang.arrayMap(xs.code, innerMapper.code, jvm.ClassOf(ct.typoType).code)))
            code"$PgRead.castJdbcArrayTo(${jvm.ClassOf(toTypo.jdbcType)}).map($xsLambda)"
        }

        // Generate the array write using the fromTypo conversion
        // Use TypedLambda1 for cross-language compatibility
        val typedLambda = jvm.Lambda(List(jvm.LambdaParam.typed(v, ct.typoType)), jvm.Body.Expr(fromTypo.fromTypo0(v)))
        // Use GenericMethodCall for cross-language compatibility
        val passObjectToJdbc = jvm.GenericMethodCall(PgWrite, jvm.Ident("passObjectToJdbc"), List(fromTypo.jdbcType), Nil)
        val typenameAs = jvm.GenericMethodCall(code"${ct.typoType}.${adapter.typeFieldName}.typename()", jvm.Ident("as"), List(fromTypo.jdbcType), Nil)
        val xsWriteLambda = jvm.Lambda(List(jvm.LambdaParam.typed(xs, arrayType)), jvm.Body.Expr(lang.arrayMap(xs.code, typedLambda.code, jvm.ClassOf(fromTypo.jdbcType).code)))
        val arrayWrite: jvm.Code =
          code"$passObjectToJdbc.array($typenameAs).contramap($xsWriteLambda)"

        Some(
          jvm.Given(
            tparams = Nil,
            name = pgTypeArrayName,
            implicitParams = Nil,
            tpe = adapter.TypeClass.of(arrayType),
            // Use PgType.array(read, write) which handles typename.array() and pgText.array()
            body = code"${ct.typoType}.${adapter.typeFieldName}.array($arrayRead, $arrayWrite)"
          )
        )
      }

    // pgText must come before pgType since complex types reference it
    List(pgTextInstance, pgTypeInstance) ++ pgTypeArrayInstance.toList
  }
}
