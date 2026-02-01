package typr
package internal

trait HasSource {
  def source: Source.Relation
  def cols: NonEmptyList[ComputedColumn]
}
