package typr
package internal

case class ComputedDomain(tpe: jvm.Type.Qualified, underlyingType: jvm.Type, underlying: db.Domain)

object ComputedDomain {
  def apply(naming: Naming, typeMapperScala: TypeMapperJvm)(dom: db.Domain): ComputedDomain =
    ComputedDomain(
      tpe = jvm.Type.Qualified(naming.domainName(dom.name)),
      underlyingType = typeMapperScala.domain(dom.tpe),
      underlying = dom
    )
}
