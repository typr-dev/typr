package typo

import typo.internal.codegen.ToCode

sealed abstract class ImplicitOrUsing extends Product with Serializable {
  def callImplicitOrUsing: sc.Code
}

object ImplicitOrUsing {
  case object Implicit extends ImplicitOrUsing {
    override def toString: String = "implicit"
    override def callImplicitOrUsing: sc.Code = sc.Code.Empty
  }
  case object Using extends ImplicitOrUsing {
    override def toString: String = "using"
    override def callImplicitOrUsing: sc.Code = sc.Code.Str("using ")
  }

  implicit def toCode: ToCode[ImplicitOrUsing] = {
    case Implicit => sc.Code.Str("implicit")
    case Using    => sc.Code.Str("using")
  }

}
