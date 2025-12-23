package typr
package internal

import typr.internal.pg.OpenEnum

sealed trait IdComputed {
  def paramName: jvm.Ident
  def cols: NonEmptyList[ComputedColumn]
  def tpe: jvm.Type
  final def param: jvm.Param[jvm.Type] = jvm.Param(paramName, tpe)
}

object IdComputed {

  sealed trait Unary extends IdComputed {
    def col: ComputedColumn
    override def cols: NonEmptyList[ComputedColumn] = NonEmptyList(col, Nil)
    override def paramName: jvm.Ident = col.name

    /** TypoType for the ID type itself (not the underlying column) */
    def typoType: TypoType = this match {
      case x: UnaryNormal => TypoType.Generated(x.tpe, col.dbCol.tpe, x.tpe)
      case x: UnaryInherited =>
        x.tpe match {
          case q: jvm.Type.Qualified => TypoType.Generated(q, col.dbCol.tpe, q)
          case _                     => col.typoType
        }
      case _: UnaryNoIdType => col.typoType
      case x: UnaryOpenEnum => TypoType.Generated(x.tpe, col.dbCol.tpe, x.tpe)
      case x: UnaryUserSpecified =>
        x.tpe match {
          case q: jvm.Type.Qualified => TypoType.UserDefined(q, col.dbCol.tpe, Left(q))
          case _                     => col.typoType
        }
    }
  }

  // normal generated code for a normal single-column id
  case class UnaryNormal(col: ComputedColumn, tpe: jvm.Type.Qualified) extends Unary {
    def underlying: jvm.Type = col.tpe
  }

  // normal generated code for a normal single-column id, we won't generate extra code for this usage of it
  case class UnaryInherited(col: ComputedColumn, tpe: jvm.Type) extends Unary {
    def underlying: jvm.Type = col.tpe
  }

  // // user specified they don't want a primary key type for this
  case class UnaryNoIdType(col: ComputedColumn, tpe: jvm.Type) extends Unary {
    def underlying: jvm.Type = col.tpe
  }

  case class UnaryOpenEnum(
      col: ComputedColumn,
      tpe: jvm.Type.Qualified,
      underlying: jvm.Type,
      openEnum: OpenEnum
  ) extends Unary

  // if user supplied a type override for an id column
  case class UnaryUserSpecified(col: ComputedColumn, tpe: jvm.Type) extends Unary {
    override def paramName: jvm.Ident = col.name
  }

  case class Composite(cols: NonEmptyList[ComputedColumn], tpe: jvm.Type.Qualified, paramName: jvm.Ident) extends IdComputed
}
