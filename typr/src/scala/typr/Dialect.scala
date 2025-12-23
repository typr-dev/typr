package typr

sealed abstract class Dialect extends Product with Serializable {
  def usingDefinition: jvm.Code
  def usingCall: jvm.Code
  def valDefinition: String
  def defDefinition: String
  def paramDefinition: String
  def implicitParamsPrefix: String
  def implicitlyCall: jvm.Code
}

object Dialect {
  case object Scala2XSource3 extends Dialect {
    override def usingDefinition: jvm.Code = jvm.Code.Str("implicit")
    override def usingCall: jvm.Code = jvm.Code.Empty
    override def valDefinition: String = "implicit lazy val"
    override def defDefinition: String = "implicit def"
    override def paramDefinition: String = "implicit"
    override def implicitParamsPrefix: String = "implicit "
    override def implicitlyCall: jvm.Code = jvm.Code.Str("implicitly")
  }
  case object Scala3 extends Dialect {
    override def usingDefinition: jvm.Code = jvm.Code.Str("using")
    override def usingCall: jvm.Code = jvm.Code.Str("using ")
    override def valDefinition: String = "given"
    override def defDefinition: String = "given"
    override def paramDefinition: String = "using"
    override def implicitParamsPrefix: String = "using "
    override def implicitlyCall: jvm.Code = jvm.Code.Str("summon")
  }
}
