package typr.dsl

/** Expresses that (tuples of) `Fields` structures can be joined.
  *
  * This also serves as the type-level connection between `Fields` and `Row`.
  */
trait Structure[Fields, Row] {
  def fields: Fields
  def columns: List[SqlExpr.FieldLike[?, ?]]
  def _path: List[Path]
  def withPath(path: Path): Structure[Fields, Row]

  // It's up to you to ensure that the `Row` in `field` is the same type as `row`
  def untypedGet[T](field: SqlExpr.FieldLike[T, ?], row: Row): Option[T]

  // It's up to you to ensure that the `Row` in `field` is the same type as `row`
  def untypedEval[T](expr: SqlExpr[T], row: Row): Option[T] =
    (expr: @unchecked) match {
      case like: SqlExpr.FieldLike[T, row] =>
        untypedGet(like, row)
      case x: SqlExpr.ConstReq[T] =>
        Some(x.value)
      case x: SqlExpr.ConstOpt[T] =>
        x.value
      case x: SqlExpr.ArrayIndex[t] =>
        for {
          arr <- untypedEval(x.arr, row)
          idx <- untypedEval(x.idx, row)
          res <- if (idx < 0 || idx >= arr.length) None else Some(arr(idx))
        } yield res

      case SqlExpr.Apply1(f, arg1) =>
        untypedEval(arg1, row).map(f.eval)
      case SqlExpr.Apply2(f, arg1, arg2) =>
        for {
          _1 <- untypedEval(arg1, row)
          _2 <- untypedEval(arg2, row)
        } yield f.eval(_1, _2)
      case SqlExpr.Apply3(f, arg1, arg2, arg3) =>
        for {
          _1 <- untypedEval(arg1, row)
          _2 <- untypedEval(arg2, row)
          _3 <- untypedEval(arg3, row)
        } yield f.eval(_1, _2, _3)
      case SqlExpr.Binary(left, op, right) =>
        for {
          _1 <- untypedEval(left, row)
          _2 <- untypedEval(right, row)
        } yield op.eval(_1, _2)
      case SqlExpr.Underlying(expr, bijection) =>
        untypedEval(expr, row).map(bijection.underlying)
      case x: SqlExpr.Coalesce[t] =>
        untypedEval(x.expr, row).orElse(untypedEval(x.getOrElse, row))
      case SqlExpr.In(expr, values, _) =>
        untypedEval(expr, row).map(values.contains)
      case x: SqlExpr.IsNull[t] =>
        Some(untypedEval(x.expr, row).isEmpty)
      case x: SqlExpr.Not[T] =>
        untypedEval(x.expr, row).map(t => x.B.map(t)(b => !b))
      case x: SqlExpr.RowExpr =>
        Some(x.exprs.map(expr => untypedEval(expr, row)))
      case x: SqlExpr.CompositeIn[t, Row] @unchecked /* for 2.13 */ =>
        val thisRow: Seq[?] = x.parts.flatMap(part => untypedEval(part.field, row))
        Some(x.tuples.exists { tuple =>
          val thatRow: Seq[?] = x.parts.map(part => part.extract(tuple))
          thisRow == thatRow
        })
    }

  final def join[Fields2, Row2](other: Structure[Fields2, Row2]): Structure[Fields ~ Fields2, Row ~ Row2] =
    new Structure.Tupled(Structure.sharedPrefix(this._path, other._path), left = this, right = other)

  final def leftJoin[Fields2, Row2](other: Structure[Fields2, Row2]): Structure[Fields ~ Fields2, Row ~ Option[Row2]] =
    new Structure.LeftTupled(Structure.sharedPrefix(this._path, other._path), left = this, right = other)
}

object Structure {
  def sharedPrefix[T](left: List[T], right: List[T]): List[T] = {
    val prefix = left.zip(right).takeWhile { case (a, b) => a == b }
    prefix.map(_._1)
  }

  // Check if a field exists in a columns list by path and name
  // This is more robust than using contains() which relies on object equality
  private def containsField(columns: List[SqlExpr.FieldLike[?, ?]], field: SqlExpr.FieldLike[?, ?]): Boolean =
    columns.exists(col => col.path == field.path && col.name == field.name)

  // some of the row types are discarded, exchanging some type-safety for a cleaner API
  implicit class FieldOps[T, R0](private val field: SqlExpr.FieldLike[T, R0]) extends AnyVal {
    def castRow[R1]: SqlExpr.FieldLike[T, R1] = field.asInstanceOf[SqlExpr.FieldLike[T, R1]]
  }

  private class Tupled[Fields1, Fields2, Row1, Row2](val _path: List[Path], val left: Structure[Fields1, Row1], val right: Structure[Fields2, Row2]) extends Structure[Fields1 ~ Fields2, Row1 ~ Row2] {
    override val fields: Fields1 ~ Fields2 =
      (left.fields, right.fields)

    override val columns: List[SqlExpr.FieldLike[?, ?]] =
      left.columns ++ right.columns

    override def untypedGet[T](field: SqlExpr.FieldLike[T, ?], row: Row1 ~ Row2): Option[T] =
      if (containsField(left.columns, field)) left.untypedGet(field.castRow, row._1)
      else right.untypedGet(field.castRow, row._2)

    override def withPath(newPath: Path): Tupled[Fields1, Fields2, Row1, Row2] =
      new Tupled(newPath :: _path, left.withPath(newPath), right.withPath(newPath))
  }

  private class LeftTupled[Fields1, Fields2, Row1, Row2](val _path: List[Path], val left: Structure[Fields1, Row1], val right: Structure[Fields2, Row2])
      extends Structure[Fields1 ~ Fields2, Row1 ~ Option[Row2]] {

    override val fields: Fields1 ~ Fields2 =
      (left.fields, right.fields)

    override val columns: List[SqlExpr.FieldLike[?, ?]] =
      left.columns ++ right.columns

    override def untypedGet[T](field: SqlExpr.FieldLike[T, ?], row: Row1 ~ Option[Row2]): Option[T] =
      if (containsField(left.columns, field)) left.untypedGet(field, row._1)
      else row._2.flatMap(v => right.untypedGet(field, v))

    override def withPath(newPath: Path): LeftTupled[Fields1, Fields2, Row1, Row2] =
      new LeftTupled(newPath :: _path, left.withPath(newPath), right.withPath(newPath))
  }
}
