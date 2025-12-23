package typr.scaladsl

import typr.dsl

import scala.jdk.OptionConverters.*

/** Top-level type alias for RelationStructure interface.
  *
  * Generated code must implement the Java interface methods:
  *   - columns(): java.util.List[typr.dsl.SqlExpr.FieldLike[?, Row]] (from FieldsExpr)
  *   - _path(): java.util.List[typr.dsl.Path] (from RelationStructure)
  *   - copy(_path: java.util.List[typr.dsl.Path]): RelationStructure[Fields, Row] (from RelationStructure)
  *
  * The Java RelationStructure interface provides a default allFields() implementation that delegates to columns() after casting to FieldsExpr. No override needed here.
  */
type RelationStructure[Fields, Row] = typr.dsl.RelationStructure[Fields, Row]

object Structure {

  case class Tuple2[A, B](underlying: dsl.Tuple2[A, B]) {
    def _1: A = underlying._1()
    def _2: B = underlying._2()
  }

  object Tuple2 {
    def of[A, B](first: A, second: B): Tuple2[A, B] = {
      Tuple2(dsl.Tuple2.of(first, second))
    }

    // Special conversion for left join results: Tuple2[Row, Optional[Row2]] -> Tuple2[Row, Option[Row2]]
    def fromLeftJoin[Row, Row2](javaTuple: dsl.Tuple2[Row, java.util.Optional[Row2]]): Tuple2[Row, Option[Row2]] = {
      Tuple2(dsl.Tuple2.of(javaTuple._1(), javaTuple._2().toScala))
    }
  }
}
