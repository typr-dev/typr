package typr
package internal

object ComputedRowUnsaved {
  sealed trait CategorizedColumn {
    def col: ComputedColumn
  }
  case class DefaultedCol(col: ComputedColumn, originalType: jvm.Type, originalTypoType: TypoType) extends CategorizedColumn
  case class AlwaysGeneratedCol(col: ComputedColumn) extends CategorizedColumn
  case class NormalCol(col: ComputedColumn) extends CategorizedColumn

  def apply(source: Source, cols: NonEmptyList[ComputedColumn], default: ComputedDefault, naming: Naming): Option[ComputedRowUnsaved] = {
    val categorizedColumns: NonEmptyList[CategorizedColumn] =
      cols.map {
        case col if col.dbCol.maybeGenerated.exists(_.ALWAYS) => AlwaysGeneratedCol(col)
        case col if col.dbCol.isDefaulted =>
          val wrappedType = jvm.Type.TApply(default.Defaulted, List(col.tpe))
          DefaultedCol(
            col = col.copy(typoType = col.typoType.withJvmType(wrappedType)),
            originalType = col.tpe,
            originalTypoType = col.typoType
          )
        case col => NormalCol(col)
      }

    // note: this changes order of columns
    val unsavedCols: List[CategorizedColumn] =
      categorizedColumns.toList.collect { case x: NormalCol => x } ++
        categorizedColumns.toList.collect { case x: DefaultedCol => x }

    val shouldOutputUnsavedRow = categorizedColumns.toList.exists {
      case _: NormalCol => false
      case _            => true
    }

    if (shouldOutputUnsavedRow)
      NonEmptyList.fromList(unsavedCols).map { unsavedCols =>
        new ComputedRowUnsaved(
          categorizedUnsavedCols = unsavedCols,
          categorizedColumnsOriginalOrder = categorizedColumns,
          tpe = jvm.Type.Qualified(naming.rowUnsaved(source))
        )
      }
    else None
  }
}

case class ComputedRowUnsaved(
    categorizedUnsavedCols: NonEmptyList[ComputedRowUnsaved.CategorizedColumn],
    categorizedColumnsOriginalOrder: NonEmptyList[ComputedRowUnsaved.CategorizedColumn],
    tpe: jvm.Type.Qualified
) {
  // all columns which goes into an `UnsavedRow` type
  def unsavedCols: NonEmptyList[ComputedColumn] =
    categorizedUnsavedCols.map(_.col)

  def normalColumns: List[ComputedColumn] =
    categorizedUnsavedCols.toList.collect { case n: ComputedRowUnsaved.NormalCol => n.col }
  def defaultedCols: List[ComputedRowUnsaved.DefaultedCol] =
    categorizedUnsavedCols.toList.collect { case d: ComputedRowUnsaved.DefaultedCol => d }
  def alwaysGeneratedCols: List[ComputedColumn] =
    categorizedColumnsOriginalOrder.toList.collect { case n: ComputedRowUnsaved.AlwaysGeneratedCol => n.col }
}
