package typr.dsl.internal

import typr.dsl.{SortOrder, Structure}

object mocks {
  class RowOrdering[Fields, Row](structure: Structure[Fields, Row], sortOrderings: List[SortOrder[?]]) extends Ordering[Row] {
    override def compare(leftRow: Row, rightRow: Row): Int =
      sortOrderings.iterator
        .map { case so: SortOrder[t] =>
          val left = structure.untypedEval(so.expr, leftRow)
          val right = structure.untypedEval(so.expr, rightRow)
          val ordering: Ordering[Option[t]] = Ordering.Option(if (so.ascending) DummyOrdering.ord[t] else DummyOrdering.ord[t].reverse)
          ordering.compare(left, right)
        }
        .find(_ != 0)
        .getOrElse(0)
  }
}
