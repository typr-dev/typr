package typr
package internal

import scala.collection.immutable.SortedSet

case class ComputedMariaSet(
    sortedValues: SortedSet[String],
    tpe: jvm.Type.Qualified,
    members: NonEmptyList[(jvm.Ident, String)]
)

object ComputedMariaSet {
  def apply(naming: Naming)(set: db.MariaType.Set): ComputedMariaSet = {
    val sortedValues = SortedSet(set.values*)
    new ComputedMariaSet(
      sortedValues,
      jvm.Type.Qualified(naming.setTypeName(sortedValues)),
      NonEmptyList
        .fromList(sortedValues.toList.map(value => naming.enumValue(value) -> value))
        .getOrElse(sys.error("SET must have at least one value"))
    )
  }
}
