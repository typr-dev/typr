package typo

sealed abstract class Dialect extends Product with Serializable {
  def usingDefinition: sc.Code
  def usingCall: sc.Code
  def valDefinition: String
  def defDefinition: String
  def paramDefinition: String
  def implicitParamsPrefix: String
}

object Dialect {
  case object Scala2XSource3 extends Dialect {
    override def usingDefinition: sc.Code = sc.Code.Str("implicit")
    override def usingCall: sc.Code = sc.Code.Empty
    override def valDefinition: String = "implicit lazy val"
    override def defDefinition: String = "implicit def"
    override def paramDefinition: String = "implicit"
    override def implicitParamsPrefix: String = "implicit "
  }
  case object Scala3 extends Dialect {
    override def usingDefinition: sc.Code = sc.Code.Str("using")
    override def usingCall: sc.Code = sc.Code.Str("using ")
    override def valDefinition: String = "given"
    override def defDefinition: String = "given"
    override def paramDefinition: String = "using"
    override def implicitParamsPrefix: String = "using "
  }
}
