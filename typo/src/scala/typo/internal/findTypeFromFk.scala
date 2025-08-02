package typo
package internal

import typo.internal.rewriteDependentData.EvalMaybe
import typo.internal.compat.*

// we let types flow through constraints down to this column, the point is to reuse id types downstream
object findTypeFromFk {
  def apply(
      typoLogger: TypoLogger,
      source: Source,
      colName: db.ColName,
      pointsTo: List[(Source.Relation, db.ColName)],
      eval: EvalMaybe[db.RelationName, HasSource],
      dialect: Dialect
  )(inSameSource: db.ColName => Option[sc.Type]): Option[sc.Type] = {
    val all: List[Either[sc.Type, sc.Type]] =
      pointsTo.flatMap { case (otherTableSource, otherColName) =>
        if (otherTableSource == source)
          if (colName == otherColName) None
          else inSameSource(otherColName).map(Left.apply)
        else
          for {
            existingTable <- eval(otherTableSource.name)
            nonCircular <- existingTable.get
            otherCol <- nonCircular.cols.find(_.dbName == otherColName)
          } yield Right(otherCol.tpe)
      }

    all.distinctByCompat { e => sc.Type.base(e.merge) } match {
      case Nil      => None
      case e :: Nil => Some(e.merge)
      case all =>
        val fromSelf = all.collectFirst { case Left(tpe) => tpe }
        val fromOthers = all.collectFirst { case Right(tpe) => tpe }
        val renderedTypes = all.map { e => sc.renderTree(e.merge, dialect) }
        typoLogger.warn(s"Multiple distinct types inherited for column ${colName.value} in $source: ${renderedTypes.mkString(", ")}")
        fromOthers.orElse(fromSelf)
    }
  }
}
