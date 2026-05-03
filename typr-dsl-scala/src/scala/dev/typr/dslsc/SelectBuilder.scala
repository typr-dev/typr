package dev.typr.dslsc

import dev.typr.foundations.Tuple
import dev.typr.foundations.Tuple.Tuple2
import dev.typr.foundations.Fragment
import dev.typr.{dsl}

import dev.typr.foundations.Connection
import _root_.scala.jdk.CollectionConverters.*
import _root_.scala.jdk.OptionConverters.*

/** Type-safe query builder with Scala/Java type conversions handled internally via bijections.
  *
  * The public API uses Scala types (Fields, Row), while internal conversions to Java types are handled by bijections that are hidden from the user.
  */
sealed trait SelectBuilder[Fields, Row] {
  private[dslsc] def javaBuilder: dsl.SelectBuilder[?, ?]

  def renderCtx(): dsl.RenderCtx
  def structure(): dsl.Structure[?, ?]

  def where(predicate: Fields => SqlExpr[Boolean]): SelectBuilder[Fields, Row]
  def maybeWhere[T](value: Option[T], predicate: (Fields, T) => SqlExpr[Boolean]): SelectBuilder[Fields, Row]
  def orderBy[T](orderFunc: Fields => dsl.SortOrder[T]): SelectBuilder[Fields, Row]
  def seek[T](orderFunc: Fields => dsl.SortOrder[T], value: SqlExpr.Const[T]): SelectBuilder[Fields, Row]
  def maybeSeek[T](orderFunc: Fields => dsl.SortOrder[T], maybeValue: Option[T], asConst: T => SqlExpr.Const[T]): SelectBuilder[Fields, Row]
  def offset(offset: Int): SelectBuilder[Fields, Row]
  def limit(limit: Int): SelectBuilder[Fields, Row]

  def toList(using connection: Connection): List[Row]
  def count(using connection: Connection): Int
  def sql(): Option[Fragment]

  def joinFk[Fields2, Row2](fkFunc: Fields => ForeignKey[Fields2, Row2], other: SelectBuilder[Fields2, Row2]): SelectBuilder[Tuple2[Fields, Fields2], Tuple2[Row, Row2]]
  def join[Fields2, Row2](other: SelectBuilder[Fields2, Row2]): SelectBuilder.PartialJoin[Fields, Fields2, Row, Row2]
  def joinOn[Fields2, Row2](other: SelectBuilder[Fields2, Row2], pred: Tuple2[Fields, Fields2] => SqlExpr[Boolean]): SelectBuilder[Tuple2[Fields, Fields2], Tuple2[Row, Row2]]
  def leftJoinOn[Fields2, Row2](other: SelectBuilder[Fields2, Row2], pred: Tuple2[Fields, Fields2] => SqlExpr[Boolean]): SelectBuilder[Tuple2[Fields, Fields2], (Row, Option[Row2])]
  def multisetOn[Fields2, Row2](other: SelectBuilder[Fields2, Row2], pred: Tuple2[Fields, Fields2] => SqlExpr[Boolean]): SelectBuilder[Tuple2[Fields, Fields2], (Row, List[Row2])]

  def groupBy[G](groupKey: Fields => SqlExpr[G]): dsl.GroupedBuilder[?, ?]
  def groupBy[G1, G2](key1: Fields => SqlExpr[G1], key2: Fields => SqlExpr[G2]): dsl.GroupedBuilder[?, ?]
  def groupBy[G1, G2, G3](key1: Fields => SqlExpr[G1], key2: Fields => SqlExpr[G2], key3: Fields => SqlExpr[G3]): dsl.GroupedBuilder[?, ?]
  def groupBy[G1, G2, G3, G4](key1: Fields => SqlExpr[G1], key2: Fields => SqlExpr[G2], key3: Fields => SqlExpr[G3], key4: Fields => SqlExpr[G4]): dsl.GroupedBuilder[?, ?]
  def groupBy[G1, G2, G3, G4, G5](
      key1: Fields => SqlExpr[G1],
      key2: Fields => SqlExpr[G2],
      key3: Fields => SqlExpr[G3],
      key4: Fields => SqlExpr[G4],
      key5: Fields => SqlExpr[G5]
  ): dsl.GroupedBuilder[?, ?]
  def groupByExpr(groupKeys: Fields => List[SqlExpr[?]]): dsl.GroupedBuilder[?, ?]

  /** Project to a new tuple expression.
    *
    * Use `tupleWith()` methods on SqlExpr or `Tuple.of()` to create tuple expressions:
    * {{{
    * // Using tupleWith():
    * builder.map(p => p.name.tupleWith(p.age))
    *
    * // Using Tuple.of():
    * builder.map(p => Tuple.of(p.name, p.age))
    * }}}
    */
  def map[NewFields, JavaNewFields <: dsl.TupleExpr[NewRow], NewRow <: Tuple](f: Fields => NewFields)(using bij: Bijection[NewFields, JavaNewFields]): SelectBuilder[NewFields, NewRow]

  /** Convert this SelectBuilder to a Subquery expression for use in IN clauses. */
  def subquery: SqlExpr.Subquery[Fields, Row]
}

object SelectBuilder {

  /** Private implementation with 4 type parameters for Scala/Java type conversion.
    *
    * @tparam ScalaFields
    *   The Scala-side Fields type exposed in the public API
    * @tparam JavaFields
    *   The Java-side Fields type used by the underlying builder
    * @tparam ScalaRow
    *   The Scala-side Row type exposed in the public API
    * @tparam JavaRow
    *   The Java-side Row type used by the underlying builder
    */
  private[dslsc] class Impl[ScalaFields, JavaFields, ScalaRow, JavaRow](
      private[dslsc] val javaBuilder: dsl.SelectBuilder[JavaFields, JavaRow],
      private[dslsc] val fieldsBij: Bijection[ScalaFields, JavaFields],
      private[dslsc] val rowBij: Bijection[ScalaRow, JavaRow]
  ) extends SelectBuilder[ScalaFields, ScalaRow] {

    /** Accept a FK join from a parent builder. For FK joins, this table must have identity bijections (ScalaFields = JavaFields, ScalaRow = JavaRow). The FK's types match ScalaFields/ScalaRow, which
      * equal JavaFields/JavaRow for base tables.
      */
    private[dslsc] def acceptJoinFk[PJF, PJR, PSF](
        parentJavaBuilder: dsl.SelectBuilder[PJF, PJR],
        parentFieldsBij: Bijection[PSF, PJF],
        fkFunc: PSF => dsl.ForeignKey[ScalaFields, ScalaRow]
    ): dsl.SelectBuilder[Tuple2[PJF, JavaFields], Tuple2[PJR, JavaRow]] = {
      // Convert the FK function to use Java types using inverse bijections
      val fkFuncJava: java.util.function.Function[PJF, dsl.ForeignKey[JavaFields, JavaRow]] =
        pjf => fkFunc(parentFieldsBij.from(pjf)).withBijections(fieldsBij.inverse(), rowBij.inverse())
      parentJavaBuilder.joinFk(fkFuncJava, javaBuilder)
    }

    def renderCtx(): dsl.RenderCtx = javaBuilder.renderCtx()
    def structure(): dsl.Structure[?, ?] = javaBuilder.structure()

    def where(predicate: ScalaFields => SqlExpr[Boolean]): SelectBuilder[ScalaFields, ScalaRow] = {
      new Impl(
        javaBuilder.where(jf => SqlExpr.toJavaBool(predicate(fieldsBij.from(jf)))),
        fieldsBij,
        rowBij
      )
    }

    def maybeWhere[T](value: Option[T], predicate: (ScalaFields, T) => SqlExpr[Boolean]): SelectBuilder[ScalaFields, ScalaRow] = {
      new Impl(
        javaBuilder.maybeWhere(value.toJava, (jf: JavaFields, t: T) => SqlExpr.toJavaBool(predicate(fieldsBij.from(jf), t))),
        fieldsBij,
        rowBij
      )
    }

    def orderBy[T](orderFunc: ScalaFields => dsl.SortOrder[T]): SelectBuilder[ScalaFields, ScalaRow] = {
      new Impl(
        javaBuilder.orderBy(jf => orderFunc(fieldsBij.from(jf))),
        fieldsBij,
        rowBij
      )
    }

    def seek[T](orderFunc: ScalaFields => dsl.SortOrder[T], value: SqlExpr.Const[T]): SelectBuilder[ScalaFields, ScalaRow] = {
      new Impl(
        javaBuilder.seek(jf => orderFunc(fieldsBij.from(jf)), value.underlying),
        fieldsBij,
        rowBij
      )
    }

    def maybeSeek[T](
        orderFunc: ScalaFields => dsl.SortOrder[T],
        maybeValue: Option[T],
        asConst: T => SqlExpr.Const[T]
    ): SelectBuilder[ScalaFields, ScalaRow] = {
      new Impl(
        javaBuilder.maybeSeek(jf => orderFunc(fieldsBij.from(jf)), maybeValue.toJava, t => asConst(t).underlying),
        fieldsBij,
        rowBij
      )
    }

    def offset(offset: Int): SelectBuilder[ScalaFields, ScalaRow] = {
      new Impl(javaBuilder.offset(offset), fieldsBij, rowBij)
    }

    def limit(limit: Int): SelectBuilder[ScalaFields, ScalaRow] = {
      new Impl(javaBuilder.limit(limit), fieldsBij, rowBij)
    }

    def toList(using connection: Connection): List[ScalaRow] = {
      javaBuilder.toList(connection).asScala.toList.map(rowBij.from)
    }

    def count(using connection: Connection): Int = {
      javaBuilder.count(connection)
    }

    def sql(): Option[Fragment] = {
      javaBuilder.sql().toScala
    }

    def joinFk[Fields2, Row2](fkFunc: ScalaFields => ForeignKey[Fields2, Row2], other: SelectBuilder[Fields2, Row2]): SelectBuilder[Tuple2[ScalaFields, Fields2], Tuple2[ScalaRow, Row2]] = {
      other match {
        case o: Impl[Fields2, jf2, Row2, jr2] =>
          // Use acceptJoinFk to let 'other' handle the FK type conversion
          val newJavaBuilder: dsl.SelectBuilder[Tuple2[JavaFields, jf2], Tuple2[JavaRow, jr2]] =
            o.acceptJoinFk(javaBuilder, fieldsBij, sf => fkFunc(sf).underlying)

          val newFieldsBij: Bijection[Tuple2[ScalaFields, Fields2], Tuple2[JavaFields, jf2]] =
            Bijection.of(
              t => Tuple.of(fieldsBij.underlying(t._1()), o.fieldsBij.underlying(t._2())),
              t => Tuple.of(fieldsBij.from(t._1()), o.fieldsBij.from(t._2()))
            )
          val newRowBij: Bijection[Tuple2[ScalaRow, Row2], Tuple2[JavaRow, jr2]] =
            Bijection.of(
              t => Tuple.of(rowBij.underlying(t._1()), o.rowBij.underlying(t._2())),
              t => Tuple.of(rowBij.from(t._1()), o.rowBij.from(t._2()))
            )
          new Impl(newJavaBuilder, newFieldsBij, newRowBij)
      }
    }

    def join[Fields2, Row2](other: SelectBuilder[Fields2, Row2]): PartialJoin[ScalaFields, Fields2, ScalaRow, Row2] = {
      new PartialJoin(this, other)
    }

    def joinOn[Fields2, Row2](other: SelectBuilder[Fields2, Row2], pred: Tuple2[ScalaFields, Fields2] => SqlExpr[Boolean]): SelectBuilder[Tuple2[ScalaFields, Fields2], Tuple2[ScalaRow, Row2]] = {
      other match {
        case o: Impl[Fields2, jf2, Row2, jr2] =>
          val newJavaBuilder: dsl.SelectBuilder[Tuple2[JavaFields, jf2], Tuple2[JavaRow, jr2]] =
            javaBuilder.joinOn(
              o.javaBuilder,
              (tuple: Tuple2[JavaFields, jf2]) => pred(Tuple.of(fieldsBij.from(tuple._1()), o.fieldsBij.from(tuple._2()))).underlying.underlying(SqlExpr.ScalaToJavaBool)
            )

          val newFieldsBij: Bijection[Tuple2[ScalaFields, Fields2], Tuple2[JavaFields, jf2]] =
            Bijection.of(
              t => Tuple.of(fieldsBij.underlying(t._1()), o.fieldsBij.underlying(t._2())),
              t => Tuple.of(fieldsBij.from(t._1()), o.fieldsBij.from(t._2()))
            )
          val newRowBij: Bijection[Tuple2[ScalaRow, Row2], Tuple2[JavaRow, jr2]] =
            Bijection.of(
              t => Tuple.of(rowBij.underlying(t._1()), o.rowBij.underlying(t._2())),
              t => Tuple.of(rowBij.from(t._1()), o.rowBij.from(t._2()))
            )
          new Impl(newJavaBuilder, newFieldsBij, newRowBij)
      }
    }

    def leftJoinOn[Fields2, Row2](
        other: SelectBuilder[Fields2, Row2],
        pred: Tuple2[ScalaFields, Fields2] => SqlExpr[Boolean]
    ): SelectBuilder[Tuple2[ScalaFields, Fields2], (ScalaRow, Option[Row2])] = {
      other match {
        case o: Impl[Fields2, jf2, Row2, jr2] =>
          val newJavaBuilder: dsl.SelectBuilder[Tuple2[JavaFields, jf2], Tuple2[JavaRow, java.util.Optional[jr2]]] =
            javaBuilder.leftJoinOn(
              o.javaBuilder,
              (tuple: Tuple2[JavaFields, jf2]) => pred(Tuple.of(fieldsBij.from(tuple._1()), o.fieldsBij.from(tuple._2()))).underlying.underlying(SqlExpr.ScalaToJavaBool)
            )

          val newFieldsBij: Bijection[Tuple2[ScalaFields, Fields2], Tuple2[JavaFields, jf2]] =
            Bijection.of(
              t => Tuple.of(fieldsBij.underlying(t._1()), o.fieldsBij.underlying(t._2())),
              t => Tuple.of(fieldsBij.from(t._1()), o.fieldsBij.from(t._2()))
            )
          val newRowBij: Bijection[(ScalaRow, Option[Row2]), Tuple2[JavaRow, java.util.Optional[jr2]]] =
            Bijection.of(
              t => Tuple.of(rowBij.underlying(t._1), t._2.map(o.rowBij.underlying).toJava),
              t => (rowBij.from(t._1()), t._2().toScala.map(o.rowBij.from))
            )
          new Impl(newJavaBuilder, newFieldsBij, newRowBij)
      }
    }

    def multisetOn[Fields2, Row2](
        other: SelectBuilder[Fields2, Row2],
        pred: Tuple2[ScalaFields, Fields2] => SqlExpr[Boolean]
    ): SelectBuilder[Tuple2[ScalaFields, Fields2], (ScalaRow, List[Row2])] = {
      other match {
        case o: Impl[Fields2, jf2, Row2, jr2] =>
          val newJavaBuilder: dsl.SelectBuilder[Tuple2[JavaFields, jf2], Tuple2[JavaRow, java.util.List[jr2]]] =
            javaBuilder.multisetOn(
              o.javaBuilder,
              (tuple: Tuple2[JavaFields, jf2]) => pred(Tuple.of(fieldsBij.from(tuple._1()), o.fieldsBij.from(tuple._2()))).underlying.underlying(SqlExpr.ScalaToJavaBool)
            )

          val newFieldsBij: Bijection[Tuple2[ScalaFields, Fields2], Tuple2[JavaFields, jf2]] =
            Bijection.of(
              t => Tuple.of(fieldsBij.underlying(t._1()), o.fieldsBij.underlying(t._2())),
              t => Tuple.of(fieldsBij.from(t._1()), o.fieldsBij.from(t._2()))
            )
          val newRowBij: Bijection[(ScalaRow, List[Row2]), Tuple2[JavaRow, java.util.List[jr2]]] =
            Bijection.of(
              t => Tuple.of(rowBij.underlying(t._1), t._2.map(o.rowBij.underlying).asJava),
              t => (rowBij.from(t._1()), t._2().asScala.toList.map(o.rowBij.from))
            )
          new Impl(newJavaBuilder, newFieldsBij, newRowBij)
      }
    }

    def groupBy[G](groupKey: ScalaFields => SqlExpr[G]): dsl.GroupedBuilder[?, ?] = {
      javaBuilder.groupBy(jf => groupKey(fieldsBij.from(jf)).underlying)
    }

    def groupBy[G1, G2](
        key1: ScalaFields => SqlExpr[G1],
        key2: ScalaFields => SqlExpr[G2]
    ): dsl.GroupedBuilder[?, ?] = {
      javaBuilder.groupBy(
        jf => key1(fieldsBij.from(jf)).underlying,
        jf => key2(fieldsBij.from(jf)).underlying
      )
    }

    def groupBy[G1, G2, G3](
        key1: ScalaFields => SqlExpr[G1],
        key2: ScalaFields => SqlExpr[G2],
        key3: ScalaFields => SqlExpr[G3]
    ): dsl.GroupedBuilder[?, ?] = {
      javaBuilder.groupBy(
        jf => key1(fieldsBij.from(jf)).underlying,
        jf => key2(fieldsBij.from(jf)).underlying,
        jf => key3(fieldsBij.from(jf)).underlying
      )
    }

    def groupBy[G1, G2, G3, G4](
        key1: ScalaFields => SqlExpr[G1],
        key2: ScalaFields => SqlExpr[G2],
        key3: ScalaFields => SqlExpr[G3],
        key4: ScalaFields => SqlExpr[G4]
    ): dsl.GroupedBuilder[?, ?] = {
      javaBuilder.groupBy(
        jf => key1(fieldsBij.from(jf)).underlying,
        jf => key2(fieldsBij.from(jf)).underlying,
        jf => key3(fieldsBij.from(jf)).underlying,
        jf => key4(fieldsBij.from(jf)).underlying
      )
    }

    def groupBy[G1, G2, G3, G4, G5](
        key1: ScalaFields => SqlExpr[G1],
        key2: ScalaFields => SqlExpr[G2],
        key3: ScalaFields => SqlExpr[G3],
        key4: ScalaFields => SqlExpr[G4],
        key5: ScalaFields => SqlExpr[G5]
    ): dsl.GroupedBuilder[?, ?] = {
      javaBuilder.groupBy(
        jf => key1(fieldsBij.from(jf)).underlying,
        jf => key2(fieldsBij.from(jf)).underlying,
        jf => key3(fieldsBij.from(jf)).underlying,
        jf => key4(fieldsBij.from(jf)).underlying,
        jf => key5(fieldsBij.from(jf)).underlying
      )
    }

    def groupByExpr(groupKeys: ScalaFields => List[SqlExpr[?]]): dsl.GroupedBuilder[?, ?] = {
      javaBuilder.groupByExpr(jf => groupKeys(fieldsBij.from(jf)).map(_.underlying).asJava)
    }

    def map[NewFields, JavaNewFields <: dsl.TupleExpr[NewRow], NewRow <: Tuple](
        f: ScalaFields => NewFields
    )(using bij: Bijection[NewFields, JavaNewFields]): SelectBuilder[NewFields, NewRow] = {
      val newJavaBuilder: dsl.SelectBuilder[JavaNewFields, NewRow] =
        javaBuilder.map(jf => bij.underlying(f(fieldsBij.from(jf))))
      new Impl(newJavaBuilder, bij, Bijection.identity())
    }

    def subquery: SqlExpr.Subquery[ScalaFields, ScalaRow] = {
      SqlExpr.Subquery.fromSelectBuilder(this)
    }
  }

  /** Create a SelectBuilder from a Java SelectBuilder with identity bijections. Used for normal tables where Scala and Java types are the same.
    */
  def apply[Fields, Row](jb: dsl.SelectBuilder[Fields, Row]): SelectBuilder[Fields, Row] =
    new Impl(jb, Bijection.identity(), Bijection.identity())

  /** Create a SelectBuilder with explicit bijections (for internal use). */
  private[dslsc] def withBijections[ScalaFields, JavaFields, ScalaRow, JavaRow](
      jb: dsl.SelectBuilder[JavaFields, JavaRow],
      fieldsBij: Bijection[ScalaFields, JavaFields],
      rowBij: Bijection[ScalaRow, JavaRow]
  ): SelectBuilder[ScalaFields, ScalaRow] =
    new Impl(jb, fieldsBij, rowBij)

  def of[Fields, Row](
      name: String,
      structure: dsl.RelationStructure[Fields, Row],
      rowCodec: dev.typr.foundationssc.RowCodec[Row],
      dialect: dsl.Dialect
  ): SelectBuilder[Fields, Row] = {
    new Impl(dsl.SelectBuilder.of(name, structure, rowCodec.underlying, dialect), Bijection.identity(), Bijection.identity())
  }

  /** Partial join builder for fluent join syntax. */
  class PartialJoin[Fields1, Fields2, Row1, Row2](
      private val parent: SelectBuilder[Fields1, Row1],
      private val other: SelectBuilder[Fields2, Row2]
  ) {
    def onFk(fkFunc: Fields1 => ForeignKey[Fields2, Row2]): SelectBuilder[Tuple2[Fields1, Fields2], Tuple2[Row1, Row2]] = {
      parent.joinFk(fields => fkFunc(fields), other)
    }

    def on(pred: Tuple2[Fields1, Fields2] => SqlExpr[Boolean]): SelectBuilder[Tuple2[Fields1, Fields2], Tuple2[Row1, Row2]] = {
      parent.joinOn(other, tuple => pred(tuple))
    }

    def leftOn(pred: Tuple2[Fields1, Fields2] => SqlExpr[Boolean]): SelectBuilder[Tuple2[Fields1, Fields2], (Row1, Option[Row2])] = {
      parent.leftJoinOn(other, tuple => pred(tuple))
    }

    def multisetOn(pred: Tuple2[Fields1, Fields2] => SqlExpr[Boolean]): SelectBuilder[Tuple2[Fields1, Fields2], (Row1, List[Row2])] = {
      parent.multisetOn(other, tuple => pred(tuple))
    }
  }
}
