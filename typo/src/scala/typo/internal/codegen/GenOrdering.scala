package typo
package internal
package codegen

class GenOrdering(customTypes: CustomTypes, pkg: sc.QIdent) {
  val orderingName = new sc.Ident("ordering")

  def ordering(tpe: sc.Type, constituents0: NonEmptyList[sc.Param]): sc.Given = {
    val ordering = TypesScala.Ordering.of(tpe)
    val constituents = constituents0.toList.take(9) // max number supported by `Ordering`
    val impl = constituents match {
      case List(col) =>
        code"${TypesScala.Ordering}.by(_.${col.name.code})"
      case more =>
        code"${TypesScala.Ordering}.by(x => (${more.map(col => code"x.${col.name.code}").mkCode(", ")}))"
    }
    // don't demand that parts of the id are ordered, for instance if they are custom types or user-provided
    val needsImplicits = constituents.filterNot { x =>
      val baseType = sc.Type.base(x.tpe)
      val hasOrdering = TypesScala.HasOrdering(baseType)

      def isInternal = baseType match {
        case sc.Type.Qualified(qident)
            if qident.idents.startsWith(pkg.idents)
            // custom types do not have ordering
              && !customTypes.All.contains(baseType) =>
          true
        case _ => false
      }

      hasOrdering || isInternal
    }

    needsImplicits match {
      case Nil =>
        sc.Given(Nil, orderingName, Nil, ordering, impl)
      case nonEmpty =>
        val orderingParams = nonEmpty.map(_.tpe).distinct.zipWithIndex.map { case (colTpe, idx) => sc.Param(sc.Ident(s"O$idx"), TypesScala.Ordering.of(colTpe), None) }
        sc.Given(Nil, orderingName, orderingParams, ordering, impl)
    }
  }
}
