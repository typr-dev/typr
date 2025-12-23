package typr.dsl

import typr.dsl.Structure.FieldOps

trait RelationStructure[Fields, Row] extends Structure[Fields, Row] {
  outer =>
  def copy(path: List[Path]): RelationStructure[Fields, Row]

  override def withPath(newPath: Path): RelationStructure[Fields, Row] =
    copy(path = newPath :: _path)

  override final def untypedGet[T](field: SqlExpr.FieldLike[T, ?], row: Row): Option[T] =
    field.castRow[Row].get(row)
}
