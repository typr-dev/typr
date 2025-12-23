package typr
package internal

case class ComputedStringEnum(
    dbEnum: db.StringEnum,
    tpe: jvm.Type.Qualified,
    members: NonEmptyList[(jvm.Ident, String)]
)
object ComputedStringEnum {
  def apply(naming: Naming)(enm: db.StringEnum): ComputedStringEnum =
    new ComputedStringEnum(
      enm,
      jvm.Type.Qualified(naming.enumName(enm.name)),
      enm.values.map { value => naming.enumValue(value) -> value }
    )

}
