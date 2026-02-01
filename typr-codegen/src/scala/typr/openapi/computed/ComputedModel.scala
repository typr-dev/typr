package typr.openapi.computed

import typr.jvm
import typr.openapi.{ModelClass, SumType}

/** Computed representation of an OpenAPI model for code generation.
  *
  * Separates computation (type mapping, naming) from file generation.
  */
sealed trait ComputedModel {

  /** The computed JVM type for this model */
  def tpe: jvm.Type.Qualified

  /** Documentation */
  def doc: Option[String]
}

object ComputedModel {

  /** Computed object type with properties */
  case class ObjectType(
      proto: ModelClass.ObjectType,
      tpe: jvm.Type.Qualified,
      properties: List[ComputedProperty],
      implements: List[jvm.Type.Qualified],
      discriminatorValues: Map[String, String]
  ) extends ComputedModel {
    def doc: Option[String] = proto.description
    def name: String = proto.name
  }

  /** Computed enum type */
  case class EnumType(
      proto: ModelClass.EnumType,
      tpe: jvm.Type.Qualified
  ) extends ComputedModel {
    def doc: Option[String] = proto.description
    def name: String = proto.name
    def values: List[String] = proto.values
  }

  /** Computed wrapper type (newtype) */
  case class WrapperType(
      proto: ModelClass.WrapperType,
      tpe: jvm.Type.Qualified,
      underlyingType: jvm.Type,
      implements: List[jvm.Type.Qualified]
  ) extends ComputedModel {
    def doc: Option[String] = proto.description
    def name: String = proto.name
  }

  /** Computed type alias */
  case class AliasType(
      proto: ModelClass.AliasType,
      tpe: jvm.Type.Qualified,
      targetType: jvm.Type
  ) extends ComputedModel {
    def doc: Option[String] = proto.description
    def name: String = proto.name
  }

  /** Computed sum type (discriminated union) */
  case class SumType(
      proto: typr.openapi.SumType,
      tpe: jvm.Type.Qualified,
      commonProperties: List[ComputedProperty],
      permittedSubtypes: List[jvm.Type.Qualified]
  ) extends ComputedModel {
    def doc: Option[String] = proto.description
    def name: String = proto.name
    def discriminator: typr.openapi.Discriminator = proto.discriminator
    def usesTypeInference: Boolean = proto.usesTypeInference
  }
}
