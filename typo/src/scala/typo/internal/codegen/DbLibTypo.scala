package typo
package internal
package codegen

import typo.internal.analysis.MaybeReturnsRows
import typo.jvm.Code.TypeOps

class DbLibTypo(
    override val lang: Lang,
    default: ComputedDefault,
    enableStreamingInserts: Boolean,
    adapter: DbAdapter,
    naming: Naming
) extends DbLib {

  /** The database type (PostgreSQL, Oracle, MariaDB) */
  val dbType: DbType = adapter.dbType

  // Check if we're using Scala types (not Java types wrapped in Scala syntax)
  // This is true for LangScalaNew but false for LangScalaJava
  private val usesScalaTypes: Boolean = lang.typeSupport eq TypeSupportScala

  /** Check if we should use native string interpolation (only Scala with Scala DSL) */
  private val useNativeStringInterpolation: Boolean = lang match {
    case scala: LangScala => scala.dsl == DslQualifiedNames.Scala
    case _                => false
  }

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

  val streamingInsert = jvm.Type.Qualified("typo.runtime.streamingInsert")
  val Fragment = lang.dsl.Fragment
  // SqlStringInterpolation uses Fragment companion object for all languages now
  val SqlStringInterpolation: jvm.Type.Qualified = Fragment
  // Kotlin nullable extension function
  val KotlinNullableExtension = jvm.Type.Qualified("typo.kotlindsl.nullable")
  // Kotlin query extension function for Fragment
  val KotlinQueryExtension = jvm.Type.Qualified("typo.kotlindsl.query")
  // Scala DbTypeOps implicit class for nullable extension method - use specific type for each database
  val ScalaDbTypeOps = adapter.dbType match {
    case DbType.PostgreSQL => jvm.Type.Qualified("typo.scaladsl.PgTypeOps")
    case DbType.MariaDB    => jvm.Type.Qualified("typo.scaladsl.MariaTypeOps")
    case DbType.DuckDB     => jvm.Type.Qualified("typo.scaladsl.DuckDbTypeOps")
    case DbType.Oracle     => jvm.Type.Qualified("typo.scaladsl.OracleTypeOps")
  }

  def rowParserFor(rowType: jvm.Type) = code"$rowType.$rowParserName"

  /** Get ResultSetParser - no .underlying needed now that we use wrapped types */
  def resultSetParserFor(rowType: jvm.Type, method: String): jvm.Code =
    code"${rowParserFor(rowType)}.$method()"

  // Extension class imports for Scala result conversions
  val OperationListOps = jvm.Type.Qualified("typo.scaladsl.OperationListOps")
  val OperationOptionalToOptionOps = jvm.Type.Qualified("typo.scaladsl.OperationOptionalToOptionOps")
  val ScalaIteratorOps = jvm.Type.Qualified("typo.scaladsl.ScalaIteratorOps")
  val ScalaCollectionConverters = jvm.Type.Qualified("scala.jdk.CollectionConverters")

  /** Generate the appropriate runUnchecked call for methods returning List - converts to Scala List for scalaDsl only */
  def queryAllRunUnchecked: jvm.Code = code".runUnchecked(c)"

  /** Generate the appropriate runUnchecked call for methods returning Optional - converts to Scala Option for scalaDsl only */
  def queryFirstRunUnchecked: jvm.Code = code".runUnchecked(c)"

  /** Convert Iterator to Java Iterator for batch inserts - only needed for Java DSL, Scala DSL Fragment methods accept Scala Iterator */
  def iteratorToJava(iteratorCode: jvm.Code): jvm.Code =
    if (lang.dsl == DslQualifiedNames.Scala) iteratorCode
    else lang.typeSupport.IteratorOps.toJavaIterator(iteratorCode)

  /** Convert Iterator to Java Iterator - always needed for streamingInsert which is Java code */
  def iteratorToJavaAlways(iteratorCode: jvm.Code): jvm.Code =
    lang.typeSupport.IteratorOps.toJavaIterator(iteratorCode)

  /** Fragment.comma() expects java.util.List for Java DSL, but Iterable for Scala DSL */
  def collectionForComma(listCode: jvm.Code): jvm.Code =
    if (lang.dsl == DslQualifiedNames.Scala) listCode
    else lang.typeSupport.ListType.toJavaList(listCode, Fragment)

  /** Fragment.or() expects List[Fragment] for Scala, ArrayList for Java/Kotlin */
  def collectionForOr(listCode: jvm.Code): jvm.Code = lang match {
    case _: LangScala => code"$listCode.toList"
    case _            => listCode
  }

  def SQL(content: jvm.Code): jvm.Code = {
    if (useNativeStringInterpolation) {
      // For Scala with Scala DSL: use native string interpolation
      jvm.StringInterpolate(SqlStringInterpolation / jvm.Ident(lang.dsl.interpolatorName), jvm.Ident(lang.dsl.interpolatorName), content).code
    } else {
      // For everything else: directly generate Fragment.interpolate() calls
      val linearized = jvm.Code.linearize(content)
      val processedParts: List[jvm.Code] = linearized.map {
        case jvm.Code.Tree(jvm.RuntimeInterpolation(value)) =>
          // Runtime values are already Fragment instances, pass as-is
          value
        case jvm.Code.Str(str) =>
          // String parts: wrap in Fragment.lit() with proper string literal
          code"$Fragment.lit(${jvm.StrLit(str)})"
        case other =>
          // For other Code types, render them and wrap in Fragment.lit()
          val str = other.render(lang).asString
          code"$Fragment.lit(${jvm.StrLit(str)})"
      }

      // Ensure the interpolation ends with a lit() if needed
      val finalParts = linearized.lastOption match {
        case Some(jvm.Code.Tree(jvm.RuntimeInterpolation(_))) =>
          // If the last part is a runtime interpolation, add an empty string literal
          processedParts :+ code"$Fragment.lit(${jvm.StrLit("")})"
        case _ =>
          processedParts
      }

      // For Kotlin, use qualified name Fragment.interpolate() directly (static imports don't work with companion objects)
      // For Java, use static import and unqualified name
      lang match {
        case _: LangKotlin =>
          // Create qualified call: Fragment.interpolate(...)
          val selectInterpolate = jvm.Select(Fragment.code, jvm.Ident(lang.dsl.interpolatorName))
          val call = jvm.Call(selectInterpolate.code, List(jvm.Call.ArgGroup(finalParts.map(jvm.Arg.Pos.apply), isImplicit = false)))
          call.code
        case _ =>
          val staticImport = jvm.Import(Fragment / jvm.Ident(lang.dsl.interpolatorName), isStatic = true)
          val interpolateIdent = jvm.Ident(lang.dsl.interpolatorName)
          val call = jvm.Call(interpolateIdent, List(jvm.Call.ArgGroup(finalParts.map(jvm.Arg.Pos.apply), isImplicit = false)))
          code"$staticImport$call"
      }
    }
  }
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

  def runtimeInterpolateValue(name: jvm.Code, typoType: TypoType): jvm.Code =
    jvm.RuntimeInterpolation(code"$Fragment.encode(${lookupType(typoType)}, $name)")

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
        // Use id.typoType (the wrapper type like SalesreasonId), not id.col.typoType (the underlying column type like int4)
        code"${quotedColName(id.col)} = ${runtimeInterpolateValue(id.paramName, id.typoType)}"
      case composite: IdComputed.Composite =>
        composite.cols.map(cc => code"${quotedColName(cc)} = ${runtimeInterpolateValue(prop(composite.paramName.code, cc.name), cc.typoType)}").mkCode(" AND ")
    }

  /** Lookup runtime type instance using pre-computed TypoType. Delegates to adapter.lookupType which handles all TypoType cases.
    */
  def lookupType(typoType: TypoType): jvm.Code =
    adapter.lookupType(typoType, naming, lang.typeSupport)

  /** Lookup database type instance for a computed column. */
  def lookupType(col: ComputedColumn): jvm.Code =
    lookupType(col.typoType)

  /** Lookup database type instance for a unary ID. Uses the ID's typoType which represents the wrapper type (e.g., AuditLogId). */
  def lookupType(id: IdComputed.Unary): jvm.Code =
    lookupType(id.typoType)

  /** Lookup database type instance for a raw db.Type by wrapping in TypoType.Standard. */
  def lookupType(dbType: db.Type): jvm.Code =
    lookupType(TypoType.Standard(lang.String, dbType))

  override def resolveConstAs(typoType: TypoType): jvm.Code =
    typoType match {
      case TypoType.Nullable(_, inner) =>
        code"${lang.dsl.ConstAsAsOpt.of(inner.jvmType)}(${lookupType(inner)})"
      case _ =>
        code"${lang.dsl.ConstAsAs.of(typoType.jvmType)}(${lookupType(typoType)})"
    }

  /** Combine boolean expressions using SqlExpr.all(). Works uniformly for both Java and Scala. */
  def booleanAndChain(exprs: NonEmptyList[jvm.Code]): jvm.Code =
    code"${lang.dsl.SqlExpr}.all(${exprs.toList.mkCode(", ")})"

  /** Create CompositeInPart expression with explicit type arguments: Part<T, Tuple, Row> */
  def compositeInPart(fieldType: jvm.Type, compositeIdType: jvm.Type, rowType: jvm.Type, fieldExpr: jvm.Code, getterField: jvm.Ident, pgType: jvm.Code): jvm.Code = {
    val getterRef = jvm.FieldGetterRef(compositeIdType, getterField)
    lang match {
      case _: LangScala =>
        // For Scala, use factory method: CompositeIn.Part[T, Tuple, Row](field, extract, pgType)
        code"${lang.dsl.CompositeIn}.Part[$fieldType, $compositeIdType, $rowType]($fieldExpr, $getterRef, $pgType)"
      case _ =>
        // For Java/Kotlin, use constructor: new Part<T, Tuple, Row>(field, extract, pgType)
        val partType = lang.dsl.CompositeInPart.of(fieldType, compositeIdType, rowType)
        jvm.New(partType, List(jvm.Arg.Pos(fieldExpr), jvm.Arg.Pos(getterRef), jvm.Arg.Pos(pgType)))
    }
  }

  /** Create CompositeIn expression - uses factory method for Scala, constructor for Java/Kotlin */
  def compositeInConstruct(parts: jvm.Code, tuples: jvm.Code): jvm.Code = lang match {
    case _: LangScala =>
      // For Scala, use factory method: CompositeIn(parts, tuples)
      code"${lang.dsl.CompositeIn}($parts, $tuples)"
    case _ =>
      // For Java/Kotlin, use constructor: new CompositeIn(parts, tuples)
      lang.dsl.CompositeIn.construct(parts, tuples)
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

    adapter.dbType match {
      case DbType.PostgreSQL =>
        id match {
          case x: IdComputed.Unary =>
            // PostgreSQL: WHERE col = ANY(array)
            val arrayTypoType = TypoType.Array(idsParam.tpe, x.typoType)
            val sql = SQL {
              code"""|select $colNames
                     |from $qRelName
                     |where ${jvm.Code.Str(adapter.quoteIdent(x.col.dbName.value))} = ANY(${runtimeInterpolateValue(idsParam.name, arrayTypoType)})""".stripMargin
            }
            jvm.Body.Expr(code"$sql.query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked")

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
              val baseCast = adapter.writeCastTypeName(col).getOrElse(col.dbCol.udtName.getOrElse(""))
              if (baseCast.nonEmpty) jvm.Code.Str(s"::${baseCast}[]") else jvm.Code.Empty
            }

            val sql = SQL {
              val colsStr = x.cols.map(_.dbCol.name.code).mkCode(", ")
              def unnestCol(col: ComputedColumn): jvm.Code = {
                val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(col.tpe), col.typoType)
                code"unnest(${runtimeInterpolateValue(col.name, arrayTypoType)}${arrayCast(col)})"
              }
              val selectClause = x.cols.toList match {
                case single :: Nil =>
                  // Single column: use unnest directly
                  code"select ${unnestCol(single)}"
                case _ =>
                  // Multiple columns: SELECT * FROM unnest(arr1, arr2) works for both PostgreSQL and DuckDB
                  val unnestArgs = x.cols.toList
                    .map { col =>
                      val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(col.tpe), col.typoType)
                      runtimeInterpolateValue(col.name, arrayTypoType)
                    }
                    .mkCode(", ")
                  code"select * from unnest($unnestArgs)"
              }
              code"""|select $colNames
                     |from $qRelName
                     |where ($colsStr)
                     |in ($selectClause)
                     |""".stripMargin
            }
            jvm.Body.Stmts(vals.toList :+ jvm.Return(code"$sql.query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked").code)
        }

      case DbType.DuckDB =>
        id match {
          case x: IdComputed.Unary =>
            // DuckDB: WHERE col = ANY(array) - same as PostgreSQL
            val arrayTypoType = TypoType.Array(idsParam.tpe, x.typoType)
            val sql = SQL {
              code"""|select $colNames
                     |from $qRelName
                     |where ${jvm.Code.Str(adapter.quoteIdent(x.col.dbName.value))} = ANY(${runtimeInterpolateValue(idsParam.name, arrayTypoType)})""".stripMargin
            }
            jvm.Body.Expr(code"$sql.query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked")

          case x: IdComputed.Composite =>
            // DuckDB: Generate OR clauses since unnest(INTEGER[], VARCHAR[]) doesn't work with mixed types
            // WHERE (col1 = ? AND col2 = ?) OR (col1 = ? AND col2 = ?) OR ...
            val idIdent = jvm.Ident("id")
            val orClauses = jvm.Ident("orClauses")

            // Build: "(col1 = encode(id.col1) AND col2 = encode(id.col2))"
            val encodedCols = x.cols.toList.map { col =>
              val quotedCol = adapter.quoteIdent(col.dbCol.name.value)
              code"$Fragment.lit(${jvm.StrLit(s"$quotedCol = ")}), $Fragment.encode(${lookupType(col)}, ${lang.prop(idIdent.code, col.name)})"
            }
            val andLit = code"$Fragment.lit(${jvm.StrLit(" AND ")})"
            val andArgs = encodedCols.flatMap(e => List(andLit, e)).drop(1)
            val openParen = code"$Fragment.lit(${jvm.StrLit("(")})"
            val closeParen = code"$Fragment.lit(${jvm.StrLit(")")})"
            val orClauseForId = code"$Fragment.interpolate($openParen, ${andArgs.mkCode(", ")}, $closeParen)"
            val addOrClause = lang.typeSupport.MutableListOps.add(orClauses.code, orClauseForId)

            jvm.Body.Stmts(
              List(
                jvm.LocalVar(orClauses, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addOrClause)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"select $colNamesStr\nfrom $qRelNameStr\nwhere "
                    )}), $Fragment.or(${collectionForOr(orClauses.code)}), $Fragment.lit(${jvm.StrLit("\n")})).query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked"""
                )
              )
            )
        }

      case DbType.MariaDB =>
        id match {
          case x: IdComputed.Unary =>
            // MariaDB: Build IN clause with individual values
            val colName = adapter.quoteIdent(x.col.dbName.value)
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodeId = code"$Fragment.encode(${lookupType(x)}, $idIdent)"
            val addStmt = lang.typeSupport.MutableListOps.add(fragments.code, encodeId)
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addStmt)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"select $colNamesStr from $qRelNameStr where $colName in ("
                    )}), $Fragment.comma(${collectionForComma(fragments.code)}), $Fragment.lit(${jvm.StrLit(s")")})).query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked"""
                )
              )
            )

          case x: IdComputed.Composite =>
            // MariaDB: (col1, col2) IN ((val1, val2), (val3, val4), ...)
            val colNamesJoined = x.cols.toList.map(c => adapter.quoteIdent(c.dbCol.name.value)).mkString(", ")
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodedCols = x.cols.toList.map(c => code"$Fragment.encode(${lookupType(c)}, ${lang.prop(idIdent.code, c.name)})")
            val commaLit = code"$Fragment.lit(${jvm.StrLit(", ")})"
            val openParen = code"$Fragment.lit(${jvm.StrLit("(")})"
            val closeParen = code"$Fragment.lit(${jvm.StrLit(")")})"
            val tupleArgs = encodedCols.flatMap(e => List(commaLit, e)).drop(1)
            val tupleExpr = code"$Fragment.interpolate($openParen, ${tupleArgs.mkCode(", ")}, $closeParen)"
            val addStmt = lang.typeSupport.MutableListOps.add(fragments.code, tupleExpr)
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addStmt)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"select $colNamesStr from $qRelNameStr where ($colNamesJoined) in ("
                    )}), $Fragment.comma(${collectionForComma(fragments.code)}), $Fragment.lit(${jvm.StrLit(")")})).query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked"""
                )
              )
            )
        }

      case DbType.Oracle =>
        // Oracle: Use same approach as MariaDB (IN clause)
        id match {
          case x: IdComputed.Unary =>
            val colName = adapter.quoteIdent(x.col.dbName.value)
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodeId = code"$Fragment.encode(${lookupType(x)}, $idIdent)"
            val addStmt = lang.typeSupport.MutableListOps.add(fragments.code, encodeId)
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addStmt)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"select $colNamesStr from $qRelNameStr where $colName in ("
                    )}), $Fragment.comma(${collectionForComma(fragments.code)}), $Fragment.lit(${jvm.StrLit(s")")})).query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked"""
                )
              )
            )

          case x: IdComputed.Composite =>
            val colNamesJoined = x.cols.toList.map(c => adapter.quoteIdent(c.dbCol.name.value)).mkString(", ")
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodedCols = x.cols.toList.map(c => code"$Fragment.encode(${lookupType(c)}, ${lang.prop(idIdent.code, c.name)})")
            val commaLit = code"$Fragment.lit(${jvm.StrLit(", ")})"
            val openParen = code"$Fragment.lit(${jvm.StrLit("(")})"
            val closeParen = code"$Fragment.lit(${jvm.StrLit(")")})"
            val tupleArgs = encodedCols.flatMap(e => List(commaLit, e)).drop(1)
            val tupleExpr = code"$Fragment.interpolate($openParen, ${tupleArgs.mkCode(", ")}, $closeParen)"
            val addStmt = lang.typeSupport.MutableListOps.add(fragments.code, tupleExpr)
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addStmt)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"select $colNamesStr from $qRelNameStr where ($colNamesJoined) in ("
                    )}), $Fragment.comma(${collectionForComma(fragments.code)}), $Fragment.lit(${jvm.StrLit(")")})).query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked"""
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
            val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(x.tpe), x.typoType)
            val sql = SQL {
              code"""|delete
                     |from $qRelName
                     |where ${jvm.Code.Str(adapter.quoteIdent(x.col.dbName.value))} = ANY(${runtimeInterpolateValue(idsParam.name, arrayTypoType)})""".stripMargin
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
              val baseCast = adapter.writeCastTypeName(col).getOrElse(col.dbCol.udtName.getOrElse(""))
              if (baseCast.nonEmpty) jvm.Code.Str(s"::${baseCast}[]") else jvm.Code.Empty
            }

            val sql = SQL {
              val colsStr = x.cols.map(_.dbCol.name.code).mkCode(", ")
              def unnestCol(col: ComputedColumn): jvm.Code = {
                val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(col.tpe), col.typoType)
                code"unnest(${runtimeInterpolateValue(col.name, arrayTypoType)}${arrayCast(col)})"
              }
              val selectClause = x.cols.toList match {
                case single :: Nil =>
                  // Single column: use unnest directly
                  code"select ${unnestCol(single)}"
                case _ =>
                  // Multiple columns: SELECT * FROM unnest(arr1, arr2) works for both PostgreSQL and DuckDB
                  val unnestArgs = x.cols.toList
                    .map { col =>
                      val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(col.tpe), col.typoType)
                      runtimeInterpolateValue(col.name, arrayTypoType)
                    }
                    .mkCode(", ")
                  code"select * from unnest($unnestArgs)"
              }
              code"""|delete
                     |from $qRelName
                     |where ($colsStr)
                     |in ($selectClause)
                     |""".stripMargin
            }
            jvm.Body.Stmts(vals.toList :+ jvm.Return(code"$sql.update().runUnchecked(c)").code)
        }

      case DbType.DuckDB =>
        id match {
          case x: IdComputed.Unary =>
            // DuckDB: WHERE col = ANY(array) - same as PostgreSQL
            val arrayTypoType = TypoType.Array(jvm.Type.ArrayOf(x.tpe), x.typoType)
            val sql = SQL {
              code"""|delete
                     |from $qRelName
                     |where ${jvm.Code.Str(adapter.quoteIdent(x.col.dbName.value))} = ANY(${runtimeInterpolateValue(idsParam.name, arrayTypoType)})""".stripMargin
            }
            jvm.Body.Expr(
              code"""|$sql
                     |  .update()
                     |  .runUnchecked(c)""".stripMargin
            )

          case x: IdComputed.Composite =>
            // DuckDB: Generate OR clauses since unnest(INTEGER[], VARCHAR[]) doesn't work with mixed types
            val idIdent = jvm.Ident("id")
            val orClauses = jvm.Ident("orClauses")

            // Build: "(col1 = encode(id.col1) AND col2 = encode(id.col2))"
            val encodedCols = x.cols.toList.map { col =>
              val quotedCol = adapter.quoteIdent(col.dbCol.name.value)
              code"$Fragment.lit(${jvm.StrLit(s"$quotedCol = ")}), $Fragment.encode(${lookupType(col)}, ${lang.prop(idIdent.code, col.name)})"
            }
            val andLit = code"$Fragment.lit(${jvm.StrLit(" AND ")})"
            val andArgs = encodedCols.flatMap(e => List(andLit, e)).drop(1)
            val openParen = code"$Fragment.lit(${jvm.StrLit("(")})"
            val closeParen = code"$Fragment.lit(${jvm.StrLit(")")})"
            val orClauseForId = code"$Fragment.interpolate($openParen, ${andArgs.mkCode(", ")}, $closeParen)"
            val addOrClause = lang.typeSupport.MutableListOps.add(orClauses.code, orClauseForId)

            jvm.Body.Stmts(
              List(
                jvm.LocalVar(orClauses, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addOrClause)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"delete\nfrom $qRelNameStr\nwhere "
                    )}), $Fragment.or(${collectionForOr(orClauses.code)}), $Fragment.lit(${jvm.StrLit("\n")})).update().runUnchecked(c)"""
                )
              )
            )
        }

      case DbType.MariaDB =>
        id match {
          case x: IdComputed.Unary =>
            // MariaDB: Build IN clause with individual values
            val colName = adapter.quoteIdent(x.col.dbName.value)
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodeId = code"$Fragment.encode(${lookupType(x)}, $idIdent)"
            val addStmt = lang.typeSupport.MutableListOps.add(fragments.code, encodeId)
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addStmt)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(s"delete from $qRelNameStr where $colName in (")}), $Fragment.comma(${collectionForComma(
                      fragments.code
                    )}), $Fragment.lit(${jvm
                      .StrLit(s")")})).update().runUnchecked(c)"""
                )
              )
            )

          case x: IdComputed.Composite =>
            // MariaDB: (col1, col2) IN ((val1, val2), (val3, val4), ...)
            val colNamesJoined = x.cols.toList.map(c => adapter.quoteIdent(c.dbCol.name.value)).mkString(", ")
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodedCols = x.cols.toList.map(c => code"$Fragment.encode(${lookupType(c)}, ${lang.prop(idIdent.code, c.name)})")
            val commaLit = code"$Fragment.lit(${jvm.StrLit(", ")})"
            val openParen = code"$Fragment.lit(${jvm.StrLit("(")})"
            val closeParen = code"$Fragment.lit(${jvm.StrLit(")")})"
            val tupleArgs = encodedCols.flatMap(e => List(commaLit, e)).drop(1)
            val tupleExpr = code"$Fragment.interpolate($openParen, ${tupleArgs.mkCode(", ")}, $closeParen)"
            val addStmt = lang.typeSupport.MutableListOps.add(fragments.code, tupleExpr)
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addStmt)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"delete from $qRelNameStr where ($colNamesJoined) in ("
                    )}), $Fragment.comma(${collectionForComma(fragments.code)}), $Fragment.lit(${jvm.StrLit(")")})).update().runUnchecked(c)"""
                )
              )
            )
        }

      case DbType.Oracle =>
        // Oracle: Use same approach as MariaDB (IN clause)
        id match {
          case x: IdComputed.Unary =>
            val colName = adapter.quoteIdent(x.col.dbName.value)
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodeId = code"$Fragment.encode(${lookupType(x)}, $idIdent)"
            val addStmt = lang.typeSupport.MutableListOps.add(fragments.code, encodeId)
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addStmt)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(s"delete from $qRelNameStr where $colName in (")}), $Fragment.comma(${collectionForComma(
                      fragments.code
                    )}), $Fragment.lit(${jvm
                      .StrLit(s")")})).update().runUnchecked(c)"""
                )
              )
            )

          case x: IdComputed.Composite =>
            val colNamesJoined = x.cols.toList.map(c => adapter.quoteIdent(c.dbCol.name.value)).mkString(", ")
            val idIdent = jvm.Ident("id")
            val fragments = jvm.Ident("fragments")
            val encodedCols = x.cols.toList.map(c => code"$Fragment.encode(${lookupType(c)}, ${lang.prop(idIdent.code, c.name)})")
            val commaLit = code"$Fragment.lit(${jvm.StrLit(", ")})"
            val openParen = code"$Fragment.lit(${jvm.StrLit("(")})"
            val closeParen = code"$Fragment.lit(${jvm.StrLit(")")})"
            val tupleArgs = encodedCols.flatMap(e => List(commaLit, e)).drop(1)
            val tupleExpr = code"$Fragment.interpolate($openParen, ${tupleArgs.mkCode(", ")}, $closeParen)"
            val addStmt = lang.typeSupport.MutableListOps.add(fragments.code, tupleExpr)
            jvm.Body.Stmts(
              List(
                jvm.LocalVar(fragments, Some(lang.typeSupport.MutableListOps.tpe.of(Fragment)), lang.typeSupport.MutableListOps.empty),
                lang.arrayForEach(idsParam.name.code, idIdent, jvm.Body.Expr(addStmt)),
                jvm.Return(
                  code"""$Fragment.interpolate($Fragment.lit(${jvm.StrLit(
                      s"delete from $qRelNameStr where ($colNamesJoined) in ("
                    )}), $Fragment.comma(${collectionForComma(fragments.code)}), $Fragment.lit(${jvm.StrLit(")")})).update().runUnchecked(c)"""
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
        sig(params = Nil, implicitParams = Nil, returnType = lang.dsl.SelectBuilder.of(fieldsType, rowType))
      case RepoMethod.SelectAll(_, _, rowType) =>
        sig(params = Nil, implicitParams = List(c), returnType = lang.ListType.tpe.of(rowType))
      case RepoMethod.SelectById(_, _, id, rowType) =>
        sig(params = List(id.param), implicitParams = List(c), returnType = lang.Optional.tpe(rowType))
      case RepoMethod.SelectByIds(_, _, _, idsParam, rowType) =>
        sig(params = List(idsParam), implicitParams = List(c), returnType = lang.ListType.tpe.of(rowType))
      case RepoMethod.SelectByIdsTracked(x) =>
        sig(params = List(x.idsParam), implicitParams = List(c), returnType = lang.MapOps.tpe.of(x.idComputed.tpe, x.rowType))
      case RepoMethod.SelectByUnique(_, keyColumns, _, rowType) =>
        sig(params = keyColumns.toList.map(_.param), implicitParams = List(c), returnType = lang.Optional.tpe(rowType))
      case RepoMethod.SelectByFieldValues(_, _, _, fieldValueOrIdsParam, rowType) =>
        sig(params = List(fieldValueOrIdsParam), implicitParams = List(c), returnType = lang.ListType.tpe.of(rowType))
      case RepoMethod.UpdateBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, implicitParams = Nil, returnType = lang.dsl.UpdateBuilder.of(fieldsType, rowType))
      case RepoMethod.UpdateFieldValues(_, id, varargs, _, _, _) =>
        sig(params = List(id.param, varargs), implicitParams = List(c), returnType = lang.Boolean)
      case RepoMethod.Update(_, _, _, param, _) =>
        sig(params = List(param), implicitParams = List(c), returnType = lang.Boolean)
      case RepoMethod.Insert(_, _, _, unsavedParam, _, returningStrategy) =>
        sig(params = List(unsavedParam), implicitParams = List(c), returnType = returningStrategy.returnType)
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
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _, returningStrategy) =>
        sig(params = List(unsavedParam), implicitParams = List(c), returnType = returningStrategy.returnType)
      case RepoMethod.InsertUnsavedStreaming(_, unsaved) =>
        val unsavedParam = jvm.Param(jvm.Ident("unsaved"), lang.IteratorType.of(unsaved.tpe))
        val batchSize = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("batchSize"), lang.Int, Some(code"10000"))
        sig(params = List(unsavedParam, batchSize), implicitParams = List(c), returnType = lang.Long)
      case RepoMethod.DeleteBuilder(_, fieldsType, rowType) =>
        sig(params = Nil, implicitParams = Nil, returnType = lang.dsl.DeleteBuilder.of(fieldsType, rowType))
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
        jvm.Body.Expr(code"${lang.dsl.SelectBuilder}.of(${jvm.StrLit(quotedRelNameStr(relName))}, $structure, ${rowParserFor(rowType)}, ${adapter.dialectRef(lang)})")
      case RepoMethod.SelectAll(relName, cols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(cols, isRead = true)}
                 |from ${quotedRelName(relName)}
                 |""".stripMargin
        }
        jvm.Body.Expr(code"""$sql.query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked""")

      case RepoMethod.SelectById(relName, cols, id, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(cols, isRead = true)}
                 |from ${quotedRelName(relName)}
                 |where ${matchId(id)}""".stripMargin
        }
        jvm.Body.Expr(code"""$sql.query(${resultSetParserFor(rowType, "first")})$queryFirstRunUnchecked""")

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
        // Convert mutable map to immutable (noop for Java, .toMap for Scala)
        val returnExpr = lang.MapOps.toImmutable(ret.code, x.idComputed.tpe, x.rowType)
        jvm.Body.Stmts(
          List(
            jvm.LocalVar(ret, Some(lang.MapOps.mutableImpl.of(x.idComputed.tpe, x.rowType)), lang.MapOps.newMutableMap(x.idComputed.tpe, x.rowType)),
            lang.ListType.forEach(selectByIdsCall, forEachLambda.code),
            jvm.Return(returnExpr)
          )
        )

      case RepoMethod.UpdateBuilder(relName, fieldsType, rowType) =>
        jvm.Body.Expr(code"${lang.dsl.UpdateBuilder}.of(${jvm.StrLit(quotedRelNameStr(relName))}, ${prop(code"$fieldsType", "structure")}, $rowType.$rowParserName, ${adapter.dialectRef(lang)})")

      case RepoMethod.SelectByUnique(relName, keyColumns, allCols, rowType) =>
        val sql = SQL {
          code"""|select ${dbNames(allCols, isRead = true)}
                 |from ${quotedRelName(relName)}
                 |where ${keyColumns.map(c => code"${quotedColName(c)} = ${runtimeInterpolateValue(c.name, c.typoType)}").mkCode(" AND ")}
                 |""".stripMargin
        }
        jvm.Body.Expr(code"""$sql.query(${resultSetParserFor(rowType, "first")})$queryFirstRunUnchecked""")

      case RepoMethod.SelectByFieldValues(relName, cols, fieldValue, fieldValueOrIdsParam, rowType) =>
        val where = {
          val x: jvm.Ident = jvm.Ident("x")
          val fv: jvm.Ident = jvm.Ident("fv")

          val typeSwitch = jvm.TypeSwitch(
            fv,
            cols.toList.map { col =>
              jvm.TypeSwitch.Case(fieldValue / col.name, x, FR(code"${quotedColName(col)} = ${runtimeInterpolateValue(prop(x.code, "value"), col.typoType)}"))
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
            jvm.Return(code"$sql.query(${resultSetParserFor(rowType, "all")})$queryAllRunUnchecked")
          )
        )

      case RepoMethod.UpdateFieldValues(relName, id, varargsParam, fieldValue, cases0, _) =>
        val updates = {
          val x: jvm.Ident = jvm.Ident("x")
          val fv: jvm.Ident = jvm.Ident("fv")

          val typeSwitch = jvm.TypeSwitch(
            fv,
            cases0.toList.map { col =>
              jvm.TypeSwitch.Case(fieldValue / col.name, x, FR(code"${quotedColName(col)} = ${runtimeInterpolateValue(prop(x.code, "value"), col.typoType)}"))
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
            code"${quotedColName(col)} = ${runtimeInterpolateValue(prop(param.name.code, col.name), col.typoType)}${adapter.columnWriteCast(col)}"
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

      case RepoMethod.Insert(relName, cols, _, unsavedParam, writeableColumnsWithId, returningStrategy) =>
        val rowType = returningStrategy.returnType
        val values = writeableColumnsWithId.map { c =>
          runtimeInterpolateValue(prop(unsavedParam.name.code, c.name), c.typoType).code ++ adapter.columnWriteCast(c)
        }

        returningStrategy match {
          case ReturningStrategy.SqlReturning(_) =>
            // PostgreSQL/MariaDB: Use RETURNING clause
            val sql = SQL {
              code"""|insert into ${quotedRelName(relName)}(${dbNames(writeableColumnsWithId, isRead = false)})
                     |values (${values.mkCode(", ")})
                     |returning ${dbNames(cols, isRead = true)}
                     |""".stripMargin
            }
            val code = resultSetParserFor(rowType, "exactlyOne")
            jvm.Body.Expr(
              code"""|$sql
                     |  .updateReturning($code).runUnchecked(c)"""
            )

          case ReturningStrategy.GeneratedKeysAllColumns(_, allCols) =>
            // Oracle: Use getGeneratedKeys with all column names (no STRUCT/ARRAY in table)
            val columnNameCodes = allCols.toList.map(c => code""""${c.dbName.value}"""")
            val columnNamesArray = lang.typedArrayOf(lang.String, columnNameCodes)
            val insertSql = SQL {
              code"""|insert into ${quotedRelName(relName)}(${dbNames(writeableColumnsWithId, isRead = false)})
                     |values (${values.mkCode(", ")})
                     |""".stripMargin
            }
            val code = resultSetParserFor(rowType, "exactlyOne")
            jvm.Body.Expr(
              code"""|$insertSql
                     |  .updateReturningGeneratedKeys($columnNamesArray, $code).runUnchecked(c)"""
            )

          case ReturningStrategy.GeneratedKeysIdOnly(idComputed) =>
            // Oracle: Use getGeneratedKeys with ID column names only (table has STRUCT/ARRAY)
            val idCols = idComputed.cols.toList
            val columnNameCodes = idCols.map(c => code""""${c.dbName.value}"""")
            val columnNamesArray = lang.typedArrayOf(lang.String, columnNameCodes)
            val insertSql = SQL {
              code"""|insert into ${quotedRelName(relName)}(${dbNames(writeableColumnsWithId, isRead = false)})
                     |values (${values.mkCode(", ")})
                     |""".stripMargin
            }
            val code = resultSetParserFor(idComputed.tpe, "exactlyOne")
            jvm.Body.Expr(
              code"""|$insertSql
                     |  .updateReturningGeneratedKeys($columnNamesArray, $code).runUnchecked(c)"""
            )
        }
      case RepoMethod.Upsert(relName, cols, id, unsavedParam, rowType, writeableColumnsWithId) =>
        val values = writeableColumnsWithId.map { c =>
          runtimeInterpolateValue(prop(unsavedParam.name.code, c.name), c.typoType).code ++ adapter.columnWriteCast(c)
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

        val parser = code"$rowType.$rowParserName.exactlyOne()"
        jvm.Body.Expr(
          code"""|$sql
                 |  .updateReturning($parser)
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
        val rowParser = code"$rowType.$rowParserName"
        // MariaDB: RETURNING with batch doesn't work via getGeneratedKeys(), so we execute each row individually
        // DuckDB: JDBC driver doesn't support batch + RETURNING (SQLFeatureNotSupportedException)
        // PostgreSQL: Use updateManyReturning which works correctly with batch + RETURNING
        // Note: For Scala DSL, Fragment methods expect Scala iterators, not Java iterators
        val unsavedArg = iteratorToJava(code"unsaved")
        if (!adapter.supportsArrays || adapter.dbType == DbType.DuckDB) {
          jvm.Body.Expr(
            code"""|$sql
                   |  .updateReturningEach($rowParser, $unsavedArg)
                   |$queryAllRunUnchecked""".stripMargin
          )
        } else {
          jvm.Body.Expr(
            code"""|$sql
                   |  .updateManyReturning($rowParser, $unsavedArg)
                   |$queryAllRunUnchecked""".stripMargin
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
          val unsavedArg = iteratorToJavaAlways(code"unsaved")
          jvm.Body.Stmts(
            List(
              jvm.IgnoreResult(code"${SQL(code"create temporary table $tempTablename (like ${quotedRelName(relName)}) on commit drop")}.update().runUnchecked(c)"),
              jvm.IgnoreResult(code"$streamingInsert.insertUnchecked($copySql, batchSize, $unsavedArg, c, $rowType.${adapter.textFieldName})"),
              jvm.Return(code"$mergeSql.update().runUnchecked(c)")
            )
          )
        }

      case RepoMethod.InsertUnsaved(relName, cols, unsaved, unsavedParam, _, _, returningStrategy) =>
        val rowType = returningStrategy.returnType
        // Use mutable list for building columns and values
        val columns = jvm.Value(
          Nil,
          jvm.Ident("columns"),
          lang.typeSupport.MutableListOps.tpe.of(Fragment),
          Some(lang.typeSupport.MutableListOps.empty),
          isLazy = false,
          isOverride = false
        )
        val values = jvm.Value(Nil, jvm.Ident("values"), lang.typeSupport.MutableListOps.tpe.of(Fragment), Some(lang.typeSupport.MutableListOps.empty), isLazy = false, isOverride = false)

        val cases0 = unsaved.normalColumns.flatMap { col =>
          val value = FR(code"${runtimeInterpolateValue(prop(unsavedParam.name.code, col.name), col.typoType)}${adapter.columnWriteCast(col)}")
          val quotedName = adapter.quoteIdent(col.dbName.value)
          List(
            lang.typeSupport.MutableListOps.add(columns.name.code, code"$Fragment.lit(${jvm.StrLit(quotedName)})"),
            lang.typeSupport.MutableListOps.add(values.name.code, value)
          )
        }

        val cases1 = unsaved.defaultedCols.map { case ComputedRowUnsaved.DefaultedCol(col @ ComputedColumn(_, ident, _, _), _, origTypoType) =>
          val value = FR(code"${runtimeInterpolateValue(code"value", origTypoType)}${adapter.columnWriteCast(col)}")
          val quotedName = adapter.quoteIdent(col.dbName.value)
          val valueIdent = jvm.Ident("value")
          val byName0 = jvm.ByName(jvm.Body.Stmts(Nil))
          // Multi-statement lambda body
          val lambda1BodyStatements = List(
            lang.typeSupport.MutableListOps.add(columns.name.code, code"$Fragment.lit(${jvm.StrLit(quotedName)})"),
            lang.typeSupport.MutableListOps.add(values.name.code, value)
          )
          val lambda1 = jvm.Lambda(List(jvm.LambdaParam(valueIdent)), jvm.Body.Stmts(lambda1BodyStatements))
          code"""|${prop(unsavedParam.name.code, ident)}.visit(
                 |  $byName0,
                 |  $lambda1
                 |);""".stripMargin
        }

        returningStrategy match {
          case ReturningStrategy.SqlReturning(_) =>
            // PostgreSQL/MariaDB: Use RETURNING clause
            val sql = SQL {
              code"""|insert into ${quotedRelName(relName)}(${jvm.RuntimeInterpolation(code"$Fragment.comma(${collectionForComma(columns.name.code)})")})
                     |values (${jvm.RuntimeInterpolation(code"$Fragment.comma(${collectionForComma(values.name.code)})")})
                     |returning ${dbNames(cols, isRead = true)}
                     |""".stripMargin
            }
            val sqlEmpty = SQL {
              code"""|insert into ${quotedRelName(relName)} default values
                     |returning ${dbNames(cols, isRead = true)}
                     |""".stripMargin
            }
            val q = {
              val body = if (unsaved.normalColumns.isEmpty) jvm.IfExpr(jvm.ApplyNullary(columns.name, jvm.Ident("isEmpty")), sqlEmpty, sql).code else sql
              jvm.Value(Nil, jvm.Ident("q"), Fragment, Some(body), isLazy = false, isOverride = false)
            }

            val parser = code"$rowType.$rowParserName.exactlyOne()"
            jvm.Body.Stmts(
              List[List[jvm.Code]](
                List(columns, values),
                cases0,
                cases1,
                List(
                  q,
                  jvm.Return(code"q.updateReturning($parser).runUnchecked(c)")
                )
              ).flatten
            )

          case ReturningStrategy.GeneratedKeysAllColumns(_, allCols) =>
            // Oracle: Use getGeneratedKeys with all column names (no STRUCT/ARRAY in table)
            val sql = SQL {
              code"""|insert into ${quotedRelName(relName)}(${jvm.RuntimeInterpolation(code"$Fragment.comma(${collectionForComma(columns.name.code)})")})
                     |values (${jvm.RuntimeInterpolation(code"$Fragment.comma(${collectionForComma(values.name.code)})")})
                     |""".stripMargin
            }
            val sqlEmpty = SQL {
              code"""|insert into ${quotedRelName(relName)} default values
                     |""".stripMargin
            }
            val q = {
              val body = if (unsaved.normalColumns.isEmpty) jvm.IfExpr(jvm.ApplyNullary(columns.name, jvm.Ident("isEmpty")), sqlEmpty, sql).code else sql
              jvm.Value(Nil, jvm.Ident("q"), Fragment, Some(body), isLazy = false, isOverride = false)
            }

            val columnNameCodes = allCols.toList.map(c => code""""${c.dbName.value}"""")
            val columnNamesArray = lang.typedArrayOf(lang.String, columnNameCodes)
            val parser = code"$rowType.$rowParserName.exactlyOne()"
            jvm.Body.Stmts(
              List[List[jvm.Code]](
                List(columns, values),
                cases0,
                cases1,
                List(
                  q,
                  jvm.Return(code"q.updateReturningGeneratedKeys($columnNamesArray, $parser).runUnchecked(c)")
                )
              ).flatten
            )

          case ReturningStrategy.GeneratedKeysIdOnly(idComputed) =>
            // Oracle: Use getGeneratedKeys with ID column names only (table has STRUCT/ARRAY)
            val sql = SQL {
              code"""|insert into ${quotedRelName(relName)}(${jvm.RuntimeInterpolation(code"$Fragment.comma(${collectionForComma(columns.name.code)})")})
                     |values (${jvm.RuntimeInterpolation(code"$Fragment.comma(${collectionForComma(values.name.code)})")})
                     |""".stripMargin
            }
            val sqlEmpty = SQL {
              code"""|insert into ${quotedRelName(relName)} default values
                     |""".stripMargin
            }
            val q = {
              val body = if (unsaved.normalColumns.isEmpty) jvm.IfExpr(jvm.ApplyNullary(columns.name, jvm.Ident("isEmpty")), sqlEmpty, sql).code else sql
              jvm.Value(Nil, jvm.Ident("q"), Fragment, Some(body), isLazy = false, isOverride = false)
            }

            val idCols = idComputed.cols.toList
            val columnNameCodes = idCols.map(c => code""""${c.dbName.value}"""")
            val columnNamesArray = lang.typedArrayOf(lang.String, columnNameCodes)
            val parser = code"$rowType.$rowParserName.exactlyOne()"
            jvm.Body.Stmts(
              List[List[jvm.Code]](
                List(columns, values),
                cases0,
                cases1,
                List(
                  q,
                  jvm.Return(code"q.updateReturningGeneratedKeys($columnNamesArray, $parser).runUnchecked(c)")
                )
              ).flatten
            )
        }

      case RepoMethod.InsertStreaming(relName, rowType, writeableColumnsWithId) =>
        val sql = lang.s(code"COPY ${quotedRelName(relName)}(${dbNames(writeableColumnsWithId, isRead = false)}) FROM STDIN")
        val unsavedArg = iteratorToJavaAlways(code"unsaved")
        jvm.Body.Expr(code"$streamingInsert.insertUnchecked($sql, batchSize, $unsavedArg, c, $rowType.${adapter.textFieldName})")
      case RepoMethod.InsertUnsavedStreaming(relName, unsaved) =>
        val sql = lang.s(code"COPY ${quotedRelName(relName)}(${dbNames(unsaved.unsavedCols, isRead = false)}) FROM STDIN (DEFAULT '${DbLibTextSupport.DefaultValue}')")
        val unsavedArg = iteratorToJavaAlways(code"unsaved")
        jvm.Body.Expr(code"$streamingInsert.insertUnchecked($sql, batchSize, $unsavedArg, c, ${unsaved.tpe}.${adapter.textFieldName})")
      case RepoMethod.DeleteBuilder(relName, fieldsType, _) =>
        val structure = prop(code"$fieldsType", "structure")
        jvm.Body.Expr(code"${lang.dsl.DeleteBuilder}.of(${jvm.StrLit(quotedRelNameStr(relName))}, $structure, ${adapter.dialectRef(lang)})")
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
          val cast = adapter.writeCast(param.dbType, Some(param.udtName)).fold("")(_.withColons)
          code"${runtimeInterpolateValue(param.name, param.typoType)}$cast"
        }
        val ret = for {
          cols <- sqlScript.maybeCols.toOption
          rowName <- sqlScript.maybeRowName.toOption
        } yield {
          // this is necessary to make custom types work with sql scripts, unfortunately.
          val renderedWithCasts: jvm.Code =
            cols.toList.flatMap(c => adapter.readCast(c.dbCol.tpe)) match {
              case Nil => renderedScript.code
              case _ =>
                val row = jvm.Ident("row")

                code"""|with $row as (
                       |  $renderedScript
                       |)
                       |select ${cols.map(c => code"$row.${c.dbCol.parsedName.originalName.code}${adapter.columnReadCast(c)}").mkCode(", ")}
                       |from $row""".stripMargin
            }

          code"${SQL(renderedWithCasts)}.query(${resultSetParserFor(rowName, "all")})$queryAllRunUnchecked"
        }

        jvm.Body.Expr(ret.getOrElse(code"${SQL(renderedScript)}.update().runUnchecked(c)"))
    }

  override def mockRepoImpl(id: IdComputed, repoMethod: RepoMethod, maybeToRow: Option[jvm.Param[jvm.Type.Function1]]): jvm.Body = {
    val mapCode = jvm.Ident("map").code
    // Helper to generate proper ID access (method call for composite, property for unary)
    def idAccess(on: jvm.Code): jvm.Code = id match {
      case _: IdComputed.Composite => lang.nullaryMethodCall(on, id.paramName)
      case _: IdComputed.Unary     => prop(on, id.paramName)
    }

    repoMethod match {
      case RepoMethod.SelectBuilder(_, fieldsType, _) =>
        val supplierLambda = jvm.Lambda(jvm.Body.Expr(lang.MapOps.valuesToList(mapCode)))
        val structure = prop(code"$fieldsType", "structure")
        // For Scala, use factory method that converts Scala List to Java List
        // For Java/Kotlin, use new SelectBuilderMock<>() directly
        lang match {
          case _: LangScala =>
            jvm.Body.Expr(
              code"${lang.dsl.SelectBuilderMock}($structure, $supplierLambda, ${lang.dsl.SelectParams}.empty())"
            )
          case _ =>
            jvm.Body.Expr(
              jvm
                .New(
                  jvm.InferredTargs(lang.dsl.SelectBuilderMock),
                  List(jvm.Arg.Pos(structure), jvm.Arg.Pos(supplierLambda), jvm.Arg.Pos(code"${lang.dsl.SelectParams}.empty()"))
                )
                .code
            )
        }
      case RepoMethod.SelectAll(_, _, _) =>
        jvm.Body.Expr(lang.MapOps.valuesToList(mapCode))
      case RepoMethod.SelectById(_, _, id, _) =>
        jvm.Body.Expr(lang.MapOps.get(mapCode, id.paramName.code))
      case RepoMethod.SelectByIds(_, _, _, idsParam, rowType) =>
        // Use Scala idiom for Scala types, imperative style for Java types
        if (usesScalaTypes) {
          jvm.Body.Expr(code"${idsParam.name}.flatMap($mapCode.get(_)).toList")
        } else {
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
                    jvm.LocalVar(jvm.Ident("opt"), None, lang.MapOps.get(mapCode, idVar.code)),
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
        }
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
        jvm.Body.Expr(lang.ListType.findFirst(lang.MapOps.valuesToList(mapCode), predicateLambda.code))

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
        val rowIdent = jvm.Ident("row")
        val supplierLambda = jvm.Lambda(jvm.Body.Expr(lang.MapOps.valuesToList(mapCode)))
        val structure = prop(code"$fieldsType", "structure")
        val copyRow = jvm.Lambda(rowIdent, rowIdent.code)
        // For Scala, use factory method that converts Scala List to Java List
        // For Java/Kotlin, use new UpdateBuilderMock<>() directly
        lang match {
          case _: LangScala =>
            jvm.Body.Expr(
              code"${lang.dsl.UpdateBuilderMock}($structure, $supplierLambda, ${lang.dsl.UpdateParams}.empty(), $copyRow)"
            )
          case _ =>
            jvm.Body.Expr(
              jvm
                .New(
                  jvm.InferredTargs(lang.dsl.UpdateBuilderMock),
                  List(
                    jvm.Arg.Pos(structure),
                    jvm.Arg.Pos(supplierLambda),
                    jvm.Arg.Pos(code"${lang.dsl.UpdateParams}.empty()"),
                    jvm.Arg.Pos(copyRow)
                  )
                )
                .code
            )
        }
      case RepoMethod.Update(_, _, _, param, _) =>
        val paramIdAccess = idAccess(param.name.code)
        val shouldUpdateVar = jvm.Ident("shouldUpdate")
        val oldRowVar = jvm.Ident("oldRow")

        jvm.Body.Stmts(
          List(
            jvm
              .LocalVar(
                shouldUpdateVar,
                None,
                lang.Optional.isDefined(
                  lang.Optional.filter(
                    lang.MapOps.get(mapCode, paramIdAccess),
                    // Build the predicate lambda: oldRow -> oldRow != param.name
                    jvm.Lambda(oldRowVar, lang.notEquals(oldRowVar.code, param.name.code)).code
                  )
                )
              )
              .code,
            // shouldUpdate = map.get(id).filter(predicate).isDefined
            jvm.If(shouldUpdateVar.code, code"${lang.MapOps.putVoid(mapCode, paramIdAccess, param.name.code)}${lang.`;`}").code,
            jvm.Return(shouldUpdateVar)
          )
        )
      case RepoMethod.Insert(_, _, _, unsavedParam, _, returningStrategy) =>
        val unsavedIdAccess = idAccess(unsavedParam.name.code)
        val throwStmt = jvm.Throw(TypesJava.RuntimeException.construct(lang.s(code"id ${rt(unsavedIdAccess)} already exists")))
        val ifStmt = jvm.If(lang.MapOps.contains(mapCode, unsavedIdAccess), throwStmt.code)
        // Return ID for Oracle with STRUCT/ARRAY, full row otherwise
        val returnValue = returningStrategy match {
          case _: ReturningStrategy.GeneratedKeysIdOnly => unsavedIdAccess
          case _                                        => unsavedParam.name.code
        }
        jvm.Body.Stmts(
          List(
            ifStmt.code,
            lang.MapOps.putVoid(mapCode, unsavedIdAccess, unsavedParam.name.code),
            jvm.Return(returnValue)
          )
        )
      case RepoMethod.Upsert(_, _, _, unsavedParam, _, _) =>
        val unsavedIdAccess = idAccess(unsavedParam.name.code)
        jvm.Body.Stmts(
          List(
            lang.MapOps.putVoid(mapCode, unsavedIdAccess, unsavedParam.name.code),
            jvm.Return(unsavedParam.name.code)
          )
        )
      case RepoMethod.UpsertStreaming(_, localId, _, _) =>
        val rowVar = jvm.Ident("row")
        val rowIdAccess = idAccessOnRow(rowVar.code, localId)
        // Use Scala idiom for Scala types, imperative style for Java types
        if (usesScalaTypes) {
          jvm.Body.Expr(
            code"""|unsaved.map { $rowVar =>
                   |  ${lang.MapOps.putVoid(mapCode, rowIdAccess, rowVar.code)}
                   |  1
                   |}.sum""".stripMargin
          )
        } else {
          val countVar = jvm.Ident("count")
          val whileBody: List[jvm.Code] = List(
            jvm.LocalVar(rowVar, None, code"unsaved.next()").code,
            lang.MapOps.putVoid(mapCode, rowIdAccess, rowVar.code),
            countVar.code ++ code" = " ++ countVar.code ++ code" + 1"
          )
          jvm.Body.Stmts(
            List(
              code"var $countVar = 0",
              jvm.While(code"unsaved.hasNext()", whileBody).code,
              jvm.Return(countVar.code)
            )
          )
        }
      case RepoMethod.UpsertBatch(_, _, localId, rowType, _) =>
        val rowVar = jvm.Ident("row")
        val rowIdAccess = idAccessOnRow(rowVar.code, localId)
        // Use Scala idiom for Scala types, imperative style for Java types
        if (usesScalaTypes) {
          jvm.Body.Expr(
            code"""|unsaved.map { $rowVar =>
                   |  ${lang.MapOps.putVoid(mapCode, rowIdAccess, rowVar.code)}
                   |  $rowVar
                   |}.toList""".stripMargin
          )
        } else {
          val resultVar = jvm.Ident("result")
          jvm.Body.Stmts(
            List(
              jvm.LocalVar(resultVar, None, jvm.New(TypesJava.ArrayList.of(rowType), Nil).code).code,
              jvm
                .While(
                  code"unsaved.hasNext()",
                  List(
                    jvm.LocalVar(rowVar, None, code"unsaved.next()").code,
                    lang.MapOps.putVoid(mapCode, rowIdAccess, rowVar.code),
                    jvm.IgnoreResult(resultVar.code.invoke("add", rowVar.code)).code
                  )
                )
                .code,
              jvm.Return(resultVar.code)
            )
          )
        }
      case RepoMethod.InsertUnsaved(_, _, _, unsavedParam, _, _, _) =>
        val insertCall = jvm.Call.withImplicits(
          code"insert",
          List(jvm.Arg.Pos(jvm.Apply1(maybeToRow.get, unsavedParam.name))),
          List(jvm.Arg.Pos(code"c"))
        )
        jvm.Body.Expr(insertCall)
      case RepoMethod.InsertStreaming(_, _, _) =>
        val rowVar = jvm.Ident("row")
        val rowIdAccess = idAccess(rowVar.code)
        // Use Scala idiom for Scala types, imperative style for Java types
        if (usesScalaTypes) {
          jvm.Body.Expr(
            code"""|unsaved.map { $rowVar =>
                   |  ${lang.MapOps.putVoid(mapCode, rowIdAccess, rowVar.code)}
                   |  1L
                   |}.sum""".stripMargin
          )
        } else {
          val countVar = jvm.Ident("count")
          val whileBody: List[jvm.Code] = List(
            jvm.LocalVar(rowVar, None, code"unsaved.next()").code,
            lang.MapOps.putVoid(mapCode, rowIdAccess, rowVar.code),
            countVar.code ++ code" = " ++ countVar.code ++ code" + 1L"
          )
          jvm.Body.Stmts(
            List(
              code"var $countVar = 0L",
              jvm.While(code"unsaved.hasNext()", whileBody).code,
              jvm.Return(countVar.code)
            )
          )
        }
      case RepoMethod.InsertUnsavedStreaming(_, _) =>
        val unsavedRowVar = jvm.Ident("unsavedRow")
        val rowVar = jvm.Ident("row")
        val rowIdAccess = idAccess(rowVar.code)
        val toRowFn = maybeToRow.get.name
        // Use Scala idiom for Scala types, imperative style for Java types
        if (usesScalaTypes) {
          jvm.Body.Expr(
            code"""|unsaved.map { $unsavedRowVar =>
                   |  val $rowVar = $toRowFn($unsavedRowVar)
                   |  ${lang.MapOps.putVoid(mapCode, rowIdAccess, rowVar.code)}
                   |  1L
                   |}.sum""".stripMargin
          )
        } else {
          val countVar = jvm.Ident("count")
          val whileBody: List[jvm.Code] = List(
            jvm.LocalVar(unsavedRowVar, None, code"unsaved.next()").code,
            jvm.LocalVar(rowVar, None, jvm.Apply1(maybeToRow.get, unsavedRowVar)).code,
            lang.MapOps.putVoid(mapCode, rowIdAccess, rowVar.code),
            countVar.code ++ code" = " ++ countVar.code ++ code" + 1L"
          )
          jvm.Body.Stmts(
            List(
              code"var $countVar = 0L",
              jvm.While(code"unsaved.hasNext()", whileBody).code,
              jvm.Return(countVar.code)
            )
          )
        }
      case RepoMethod.DeleteBuilder(_, fieldsType, _) =>
        val rowVar = jvm.Ident("row")
        val idVar = jvm.Ident("id")
        val supplierLambda = jvm.Lambda(jvm.Body.Expr(lang.MapOps.valuesToList(mapCode)))
        val structure = prop(code"$fieldsType", "structure")
        val idExtractor = jvm.Lambda(rowVar, idAccess(rowVar.code))
        // Use removeVoid for Consumer - returns void instead of Optional
        val deleteById = jvm.Lambda(idVar, lang.MapOps.removeVoid(mapCode, idVar.code))
        // For Scala, use factory method that converts Scala List to Java List
        // For Java/Kotlin, use new DeleteBuilderMock<>() directly
        lang match {
          case _: LangScala =>
            jvm.Body.Expr(
              code"${lang.dsl.DeleteBuilderMock}($structure, $supplierLambda, ${lang.dsl.DeleteParams}.empty(), $idExtractor, $deleteById)"
            )
          case _ =>
            jvm.Body.Expr(
              jvm
                .New(
                  jvm.InferredTargs(lang.dsl.DeleteBuilderMock),
                  List(
                    jvm.Arg.Pos(structure),
                    jvm.Arg.Pos(supplierLambda),
                    jvm.Arg.Pos(code"${lang.dsl.DeleteParams}.empty()"),
                    jvm.Arg.Pos(idExtractor),
                    jvm.Arg.Pos(deleteById)
                  )
                )
                .code
            )
        }

      case RepoMethod.Delete(_, id) =>
        jvm.Body.Expr(lang.Optional.isDefined(lang.MapOps.remove(mapCode, id.paramName.code)))
      case RepoMethod.DeleteByIds(_, _, idsParam) =>
        val idVar = jvm.Ident("id")
        val countVar = jvm.Ident("count")
        val forEachBody = jvm.Body.Stmts(
          List(
            jvm
              .If(
                lang.Optional.isDefined(lang.MapOps.remove(mapCode, idVar.code)),
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

    // Determine return type based on insert method's returning strategy
    // Oracle tables with STRUCT/ARRAY columns return ID only, others return full row
    val returnType: jvm.Type = x.table.repoMethods
      .flatMap(_.toList.collectFirst {
        case RepoMethod.InsertUnsaved(_, _, _, _, _, _, returningStrategy) => returningStrategy.returnType
        case RepoMethod.Insert(_, _, _, _, _, returningStrategy)           => returningStrategy.returnType
      })
      .getOrElse(x.table.names.RowName)

    jvm.Method(
      Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = x.name,
      params = x.params,
      implicitParams = List(c),
      tpe = returnType,
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
    if (enableStreamingInserts && adapter.supportsCopyStreaming) List(textInstance) else Nil
  }

  override def stringEnumInstances(wrapperType: jvm.Type, underlyingTypoType: TypoType, sqlType: String, openEnum: Boolean): List[jvm.Given] = {
    val underlyingDbType = underlyingTypoType.underlyingDbType
    val sqlTypeLit = jvm.StrLit(sqlType)
    val arrayWrapper = jvm.Type.ArrayOf(wrapperType)
    val create = jvm.MethodRef(wrapperType, jvm.Ident(if (openEnum) "apply" else "force"))
    val extract = jvm.FieldGetterRef(wrapperType, jvm.Ident("value"))
    val underlyingJvmCls = jvm.ClassOf(underlyingTypoType.jvmType)
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
              val xs = jvm.Ident("xs")
              val lambda1 = jvm.Lambda(xs, lang.arrayMap(xs.code, create, jvm.ClassOf(wrapperType).code))
              val lambda2 = jvm.Lambda(xs, lang.arrayMap(xs.code, extract, underlyingJvmCls.code))
              val arrayDbType = underlyingDbType match {
                case pgType: db.PgType         => db.PgType.Array(pgType)
                case duckDbType: db.DuckDbType => db.DuckDbType.ArrayType(duckDbType, None)
                case other                     => other
              }
              val base =
                code"""|${lookupType(arrayDbType)}
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
            val base = code"${lookupType(underlyingDbType)}.bimap($create, $extract)"
            if (openEnum) base
            else code"""|$base
                        |  .renamedDropPrecision($sqlTypeLit)""".stripMargin

          }
        )
      )
    ).flatten
  }

  override def wrapperTypeInstances(wrapperType: jvm.Type.Qualified, underlyingJvmType: jvm.Type, underlyingDbType: db.Type, overrideDbType: Option[String]): List[jvm.Given] = {
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
              val lambda2 = jvm.Lambda(xs, lang.arrayMap(xs.code, jvm.FieldGetterRef(wrapperType, jvm.Ident("value")).code, jvm.ClassOf(underlyingJvmType).code))
              val arrayDbType = underlyingDbType match {
                case pgType: db.PgType         => db.PgType.Array(pgType)
                case duckDbType: db.DuckDbType => db.DuckDbType.ArrayType(duckDbType, None)
                case other                     => other
              }
              val base = code"${lookupType(arrayDbType)}.bimap($lambda1, $lambda2)"
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
            val base = code"${lookupType(underlyingDbType)}.bimap(${jvm.ConstructorMethodRef(wrapperType)}, ${jvm.FieldGetterRef(wrapperType, jvm.Ident("value"))})"
            overrideDbType.fold(base)(maybeRename(base, _))
          }
        )
      ),
      // For Oracle with GeneratedKeysIdOnly, we need a RowParser for the ID type
      // This is used to parse the generated key(s) from the ResultSet returned by getGeneratedKeys()
      // Note: We inline the type lookup instead of referencing the oracleType field to avoid
      // forward reference issues in Java (static fields are sorted alphabetically)
      if (adapter.dbType != DbType.Oracle) None
      else
        Some(
          jvm.Given(
            tparams = Nil,
            name = rowParserName,
            implicitParams = Nil,
            tpe = lang.dsl.RowParser.of(wrapperType),
            body = {
              val x = jvm.Ident("x")
              val id = jvm.Ident("id")
              val encodeLambda = jvm.Lambda(id, lang.arrayOf(List(id.code)))
              // Inline the type with bimap to avoid forward reference to oracleType field
              val inlineType = code"${lookupType(underlyingDbType)}.bimap(${jvm.ConstructorMethodRef(wrapperType)}, ${jvm.FieldGetterRef(wrapperType, jvm.Ident("value"))})"
              // For single-column ID where T0 = Row, decode is identity
              // (the DbType.bimap already handles converting the underlying type to the wrapper type)
              val decodeLambda = jvm.Lambda(x, x.code)
              lang.dsl match {
                case DslQualifiedNames.Scala =>
                  // Scala RowParsers.of: def of[T0, Row](t0: DbType[T0])(decode: T0 => Row)(encode: Row => Array[Any])
                  code"${lang.dsl.RowParsers}.of($inlineType)($decodeLambda)($encodeLambda)"
                case _ =>
                  // Java/Kotlin RowParsers.of: def of[T0, Row](t0: DbType[T0], decode: Function1[T0, Row], encode: Function[Row, Object[]])
                  code"${lang.dsl.RowParsers}.of($inlineType, $decodeLambda, $encodeLambda)"
              }
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
        lang.dsl.RowParser.of(tpe),
        Some {
          val pgTypes = cols.toList.map(x => lookupType(x)).mkCode(code", ")
          val row = jvm.Ident("row")
          val newArray = lang.arrayOf(cols.toList.map(c => lang.propertyGetterAccess(row.code, c.name)))
          val encodeLambda = jvm.Lambda(row, newArray)
          // For Kotlin, we need to use a lambda instead of constructor reference
          // because Kotlin's ::ClassName doesn't SAM-convert to Java functional interfaces
          // Also, we must NOT use typed lambda parameters because Kotlin won't SAM-convert
          // typed lambdas to Java functional interfaces with generic type parameters

          val decodeLambda: jvm.Code = lang match {
            case LangScala(_, TypeSupportJava, _) if cols.length > 22 =>
              // For Scala with Java types and arity > 22, directly instantiate Function type
              // to work around Scala compiler bug with SAM conversion
              val params = cols.toList.zipWithIndex.map { case (col, i) =>
                jvm.Param(jvm.Ident(s"t$i"), col.tpe)
              }
              val args = params.map(_.name.code)
              val applyMethod = jvm.Method(
                annotations = Nil,
                comments = jvm.Comments.Empty,
                tparams = Nil,
                name = jvm.Ident("apply"),
                params = params,
                implicitParams = Nil,
                tpe = tpe,
                throws = Nil,
                body = jvm.Body.Expr(tpe.construct(args: _*)),
                isOverride = true,
                isDefault = false
              )
              val functionTypeName = jvm.Type.Qualified(s"typo.runtime.RowParsers.Function${cols.length}")
              val typeParams = cols.toList.map(_.tpe) :+ tpe
              val functionType = functionTypeName.of(typeParams: _*)
              jvm
                .NewWithBody(
                  extendsClass = None,
                  implementsInterface = Some(functionType),
                  members = List(applyMethod)
                )
                .code
            case _: LangKotlin =>
              val params = cols.toList.zipWithIndex.map { case (col, i) =>
                jvm.Param(jvm.Ident(s"t$i"), col.tpe)
              }
              val args = params.zip(cols.toList).map { case (p, col) =>
                // Use base() to unwrap Commented/UserDefined wrappers
                val paramRef = p.name.code
                jvm.Type.base(col.tpe) match {
                  case TypesJava.String => code"$paramRef as ${col.tpe}"
                  case _                => paramRef
                }
              }
              // Use untyped params for SAM conversion compatibility
              jvm.Lambda(params.map(p => jvm.LambdaParam(p.name)), jvm.Body.Expr(tpe.construct(args: _*)))
            case _ =>
              jvm.ConstructorMethodRef(tpe)
          }
          // For Scala DSL, use curried calls; for Java/Kotlin, use single parameter list
          lang.dsl match {
            case DslQualifiedNames.Scala =>
              code"${lang.dsl.RowParsers}.of($pgTypes)($decodeLambda)($encodeLambda)"
            case _ =>
              code"${lang.dsl.RowParsers}.of($pgTypes, $decodeLambda, $encodeLambda)"
          }
        },
        isLazy = false,
        isOverride = false
      )
    }
    rowType match {
      case DbLib.RowType.Writable =>
        val text = if (enableStreamingInserts && adapter.supportsCopyStreaming) {
          val row = jvm.Ident("row")
          val sb = jvm.Ident("sb")
          val textCols: NonEmptyList[jvm.Code] = cols.map { col =>
            val text = col.tpe match {
              case jvm.Type.TApply(default.Defaulted, List(_)) =>
                // For Defaulted[T], col.typoType preserves the inner type structure (e.g., Generated for DepartmentId)
                val innerPgText = jvm.ApplyNullary(lookupType(col), adapter.textFieldName)
                val target = jvm.Select(default.Defaulted.code, adapter.textFieldName)
                jvm.Call(target.code, List(jvm.Call.ArgGroup(List(jvm.Arg.Pos(innerPgText.code)), isImplicit = true))).code
              case _ =>
                jvm.ApplyNullary(lookupType(col), adapter.textFieldName).code
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
        val rowParserArg = lang.dsl match {
          case DslQualifiedNames.Java => rowParserName.code
          case _                      => code"$rowParserName.underlying"
        }
        val textInstance =
          if (enableStreamingInserts && adapter.supportsCopyStreaming)
            List(jvm.Given(tparams = Nil, name = adapter.textFieldName, implicitParams = Nil, tpe = adapter.TextClass.of(tpe), body = code"${adapter.TextClass}.from($rowParserArg)"))
          else Nil
        List(rowParser) ++ textInstance
      case DbLib.RowType.Readable => List(rowParser)
    }
  }

  override def customTypeInstances(ct: CustomType): List[jvm.Given] =
    Nil

  override def structInstances(computed: ComputedOracleObjectType): List[jvm.ClassMember] = {
    if (computed.attributes.isEmpty) {
      Nil
    } else {
      val oracleType = jvm.Type.Qualified("typo.runtime.OracleType")
      val oracleObject = jvm.Type.Qualified("typo.runtime.OracleObject")

      // Get the Oracle type name (e.g., "ADDRESS_T")
      val oracleTypeName = computed.underlying.name.name

      // Build the chain: OracleObject.<StructType>builder("TYPE_NAME") (Java/Kotlin) or OracleObject.builder[StructType]("TYPE_NAME") (Scala)
      val builderStart = jvm.GenericMethodCall(
        target = oracleObject,
        methodName = jvm.Ident("builder"),
        typeArgs = List(computed.tpe),
        args = List(jvm.Arg.Pos(jvm.StrLit(oracleTypeName)))
      )

      // Add all .addAttribute(...) calls
      val addAttributeCalls = computed.attributes
        .map { attr =>
          val attrName = jvm.StrLit(attr.dbAttribute.name)
          val oracleTypeCall = lookupType(attr.dbAttribute.tpe)
          // Use FieldGetterRef for language-specific getter syntax: Java: ClassName::field, Scala: _.field
          val methodRef = jvm.FieldGetterRef(computed.tpe, attr.name)
          code".addAttribute($attrName, $oracleTypeCall, $methodRef)"
        }
        .mkCode(code"")

      // Build the constructor lambda: attrs -> new StructType((Type)attrs[0], ...)
      // Create individual arguments for jvm.New (handles Kotlin's lack of 'new' keyword)
      val attrsIdent = jvm.Ident("attrs")
      val constructorArgs: List[jvm.Arg] = computed.attributes.zipWithIndex.map { case (attr, idx) =>
        // Use ArrayIndex for language-agnostic array access: Java/Kotlin: attrs[idx], Scala: attrs(idx)
        val arrayAccess = jvm.ArrayIndex(attrsIdent, idx)
        jvm.Arg.Pos(lang.castFromObject(attr.tpe, arrayAccess))
      }
      // Use jvm.New for language-specific instance creation and jvm.Lambda for lambda syntax
      val constructorBody = jvm.New(computed.tpe, constructorArgs)
      val constructorLambda = jvm.Lambda("attrs", jvm.Body(List(constructorBody)))
      val buildCall = code".build($constructorLambda)"

      // Complete chain with .asType()
      val fullChain = code"$builderStart$addAttributeCalls$buildCall.asType()"

      // Create static field
      List(
        jvm.Value(
          annotations = Nil,
          name = jvm.Ident("oracleType"),
          tpe = oracleType.of(computed.tpe),
          body = Some(fullChain),
          isLazy = false,
          isOverride = false
        )
      )
    }
  }

  override def collectionInstances(computed: ComputedOracleCollectionType): List[jvm.ClassMember] = {
    val oracleType = jvm.Type.Qualified("typo.runtime.OracleType")
    val elementTypeInstance = lookupType((computed.underlying: @unchecked) match {
      case v: db.OracleType.VArray      => v.elementType
      case n: db.OracleType.NestedTable => n.elementType
    })

    // Generate base factory call for List<ElementType>
    val baseFactoryCall = (computed.underlying: @unchecked) match {
      case v: db.OracleType.VArray =>
        val oracleVArray = jvm.Type.Qualified("typo.runtime.OracleVArray")
        val typeName = jvm.StrLit(v.name.name)
        code"$oracleVArray.of($typeName, ${v.maxSize}, $elementTypeInstance)"
      case n: db.OracleType.NestedTable =>
        val oracleNestedTable = jvm.Type.Qualified("typo.runtime.OracleNestedTable")
        val typeName = jvm.StrLit(n.name.name)
        code"$oracleNestedTable.of($typeName, $elementTypeInstance)"
    }

    // Convert from List<ElementType> to WrapperType using bimap
    val listType = jvm.Type.Qualified("java.util.List")
    val listIdent = jvm.Ident("list")
    val wrapperIdent = jvm.Ident("wrapper")

    // Create language-specific bimap lambdas
    val (toWrapperLambda, fromWrapperLambda) = lang match {
      case LangJava =>
        val newArray = lang.newArray(computed.elementType, Some(code"0"))
        val toArray = code"$listIdent.toArray($newArray)"
        val toWrapper = jvm.Lambda(
          "list",
          jvm.Body(List(jvm.New(computed.tpe, List(jvm.Arg.Pos(toArray)))))
        )
        val valueAccess = lang.prop(wrapperIdent, "value")
        val listOf = code"$listType.of($valueAccess)"
        val fromWrapper = jvm.Lambda("wrapper", jvm.Body(List(listOf)))
        (toWrapper, fromWrapper)

      case _: LangKotlin =>
        // Kotlin: Use asList() to convert array to java.util.List
        val toArray = code"$listIdent.toTypedArray()"
        val toWrapper = jvm.Lambda(
          "list",
          jvm.Body(List(jvm.New(computed.tpe, List(jvm.Arg.Pos(toArray)))))
        )
        val valueAccess = lang.prop(wrapperIdent, "value")
        // Use asList() which returns java.util.List backed by the array
        val listOf = code"$valueAccess.asList()"
        val fromWrapper = jvm.Lambda("wrapper", jvm.Body(List(listOf)))
        (toWrapper, fromWrapper)

      case scala: LangScala if scala.typeSupport == TypeSupportJava =>
        // For Scala with Java DSL (TypeSupportJava), use the same logic as Java/Kotlin
        val newArray = lang.newArray(computed.elementType, Some(code"0"))
        val toArray = code"$listIdent.toArray($newArray)"
        val toWrapper = jvm.Lambda(
          "list",
          jvm.Body(List(jvm.New(computed.tpe, List(jvm.Arg.Pos(toArray)))))
        )
        val valueAccess = lang.prop(wrapperIdent, "value")
        // Use spread operator : _* to pass array as varargs to List.of()
        val listOf = code"$listType.of($valueAccess: _*)"
        val fromWrapper = jvm.Lambda("wrapper", jvm.Body(List(listOf)))
        (toWrapper, fromWrapper)

      case scala: LangScala =>
        // Convert from java.util.List to Scala List, then to Array
        val scalaList = scala.ListType.fromJavaList(code"$listIdent", computed.elementType)
        val toArray = code"$scalaList.toArray"
        val toWrapper = jvm.Lambda(
          "list",
          jvm.Body(List(jvm.New(computed.tpe, List(jvm.Arg.Pos(toArray)))))
        )
        // Convert from Array to Scala List, then to java.util.List
        val valueAccess = lang.prop(wrapperIdent, "value")
        val scalaListFromArray = code"$valueAccess.toList"
        val javaList = scala.ListType.toJavaList(scalaListFromArray, computed.elementType)
        val fromWrapper = jvm.Lambda("wrapper", jvm.Body(List(javaList)))
        (toWrapper, fromWrapper)

      case _ => ???
    }

    val factoryCall = code"$baseFactoryCall.bimap($toWrapperLambda, $fromWrapperLambda)"

    List(
      jvm.Value(
        annotations = Nil,
        name = jvm.Ident("oracleType"),
        tpe = oracleType.of(computed.tpe),
        body = Some(factoryCall),
        isLazy = false,
        isOverride = false
      )
    )
  }
}
