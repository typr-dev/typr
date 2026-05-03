package dev.typr.dslsc

import dev.typr.foundations.Tuple

import _root_.scala.jdk.OptionConverters.*

/** Top-level type alias for RelationStructure interface.
  *
  * Generated code must implement the Java interface methods:
  *   - columns(): java.util.List[dev.typr.dsl.SqlExpr.FieldLike[?, Row]] (from FieldsBase)
  *   - rowCodec(): dev.typr.foundations.RowCodec[Row] (from FieldsBase)
  *   - _path(): java.util.List[dev.typr.dsl.Path] (from RelationStructure)
  *   - withPaths(_path: java.util.List[dev.typr.dsl.Path]): RelationStructure[Fields, Row] (from RelationStructure)
  *
  * The Java RelationStructure interface provides a default allFields() implementation that delegates to FieldsBase.columns().
  */
type RelationStructure[Fields, Row] = dev.typr.dsl.RelationStructure[Fields, Row]

object Structure {

  object Tuple2 {
    def of[A, B](first: A, second: B): Tuple.Tuple2[A, B] = {
      Tuple.of(first, second)
    }

    // Special conversion for left join results: Tuple2[Row, Optional[Row2]] -> Tuple2[Row, Option[Row2]]
    def fromLeftJoin[Row, Row2](javaTuple: Tuple.Tuple2[Row, java.util.Optional[Row2]]): Tuple.Tuple2[Row, Option[Row2]] = {
      Tuple.of(javaTuple._1(), javaTuple._2().toScala)
    }
  }
}
