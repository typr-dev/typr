package typr
package internal

import typr.internal.rewriteDependentData.EvalMaybe
import typr.internal.compat.*

// we let types flow through constraints down to this column, the point is to reuse id types downstream
object findTypeFromFk {
  def apply(
      typoLogger: TypoLogger,
      source: Source,
      colName: db.ColName,
      pointsTo: List[(Source.Relation, db.ColName)],
      eval: EvalMaybe[db.RelationName, HasSource],
      lang: Lang
  )(inSameSource: db.ColName => Option[jvm.Type]): Option[jvm.Type] = {
    val all: List[Either[jvm.Type, jvm.Type]] =
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

    all.distinctByCompat { e => jvm.Type.base(e.merge) } match {
      case Nil      => None
      case e :: Nil => Some(e.merge)
      case all =>
        val fromSelf = all.collectFirst { case Left(tpe) => tpe }
        val fromOthers = all.collectFirst { case Right(tpe) => tpe }
        val renderedTypes = all.map { e => lang.renderTree(e.merge, lang.Ctx.Empty) }
        typoLogger.warn(s"Multiple distinct types inherited for column ${colName.value} in $source: ${renderedTypes.mkString(", ")}")
        fromOthers.orElse(fromSelf)
    }
  }
}
